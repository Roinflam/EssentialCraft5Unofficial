package com.flameforge.essentialcraft5.player;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.api.WindAffinity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.WindTabletItem;
import com.flameforge.essentialcraft5.registry.ECWind;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 风之心愿（第十批 10b-M1「风之试炼」，batch10.md §5.5.1）：受到风之眷顾的玩家潜行、两手空着右键完整的风之符文，风说出一个心愿；
 * 替它做到，风道谢、给好感、心愿数 +1。风之真名除了好感，还要攒够心愿数（{@link #required()}，见 {@link WindTabletItem#attuned}）。
 *
 * <h2>怎么要、怎么换</h2>
 * <ul>
 *   <li>要：{@link #ask}（由 {@code WindRuneBlockEntity.imbue} 在潜行、主手空着时调；原版潜行时只要任一只手拿着东西就不调方块的 use，
 *       所以到得了这里就是两手都空）。同时只有一个心愿；期限 {@code deadline_ticks}（默认 3 个游戏日）；过期不罚，过 {@code cooldown_ticks} 再问。</li>
 *   <li>换：手持风之药水潜行右键符文（{@link #swap}）——药水用掉一瓶，换一个别的心愿。原版潜行拿着东西不调方块的 use、会改走物品的使用（药水被喝掉），
 *       所以 {@link WindWishEvents#windWishSwapClick} 在 {@code RightClickBlock} 里对「受眷顾、潜行、主手是风之药水、点的是风之符文」放行方块交互，
 *       两端判断一致（客户端读同步来的玩家数据），客户端就不会开始喝。</li>
 * </ul>
 *
 * <h2>八种心愿（只给现在做得到的，{@link #options}）</h2>
 * 服务端每秒判一次（同 {@link WindRelations} 的节奏），落羽每刻判、月下跟着起跳事件走：
 * <table>
 *   <tr><th>种类</th><th>要做的</th><th>什么时候给</th></tr>
 *   <tr><td>{@code climb} 登高</td><td>Y ≥ {@value #CLIMB_Y}、头顶露天累计 {@value #CLIMB_SECONDS} 秒</td><td>当前维度有天空、没有顶</td></tr>
 *   <tr><td>{@code chase} 追风</td><td>疾跑累计 {@value #CHASE_BLOCKS} 格（每秒按水平位移算，一秒最多记 {@value #CHASE_MAX_STEP} 格，传送不算）</td><td>总是</td></tr>
 *   <tr><td>{@code travel} 远行</td><td>走进点名的一类群系（{@link TravelTarget}），挑这次不在的</td><td>数据包里这类群系还在</td></tr>
 *   <tr><td>{@code feather} 落羽</td><td>从 {@value #FEATHER_HEIGHT} 格以上落下、落地 / 入水时没有摔落伤害（轻盈、水、缓降都算；
 *       高度差按离地后自己记的最高点算——入水那刻原版把 fallDistance 清零，缓降每刻清零，不能只看它）</td><td>总是</td></tr>
 *   <tr><td>{@code moon} 月下</td><td>夜里头顶露天连跳 {@value #MOON_JUMPS} 次（两次之间隔超过 {@value #MOON_GAP_TICKS} 刻重数）</td><td>当前维度有昼夜</td></tr>
 *   <tr><td>{@code abroad} 他乡</td><td>到给心愿时所在维度以外的维度累计待满 {@value #ABROAD_SECONDS} 秒</td><td>总是</td></tr>
 *   <tr><td>{@code glide} 乘风</td><td>鞘翅连续滑翔 {@value #GLIDE_BLOCKS} 格（落地或收起就重数）</td><td>胸甲格是能飞的鞘翅</td></tr>
 *   <tr><td>{@code thunder} 听雷</td><td>雷雨里头顶露天累计 {@value #THUNDER_SECONDS} 秒</td><td>正在打雷</td></tr>
 * </table>
 * 先等概率挑种类，远行再等概率挑群系；换的时候排除当前这种。
 *
 * <h2>完成</h2>
 * 风道谢（聊天栏一句）、{@link WindRelations#grant}（原始值 {@code points}，系数 0.1 时实得 100；捕风挂坠照样 15% 给受困之风）、心愿数 +1、
 * 发 {@link #WISH_ADVANCEMENT}；再查一次风之真名（{@link #checkTrueName}）。真名以后照常能问、好感照算（通往风之契约）。
 * 聊天栏只在给出 / 完成 / 过期时各一条；问的时候已有心愿、还在冷却、换不了，都在快捷栏上方说一句。
 * <p>
 * <b>真名之前只给 {@link #required()} 个</b>：心愿数攒够、真名还没显全时，风不再托付新的心愿（{@link AskResult#RESTING}）。不然心愿次数不限、
 * 做完能马上再问，等于给风之好感 3500 这道门槛加了一条不受限的来源；真名前的好感只认这几个心愿，其余照旧靠药水和受眷顾后的风事件。
 * 服主中途调低 {@code required}、手上还有一个心愿时，做完照样道谢、记数，只是不给好感（{@link #complete} 里按完成前的心愿数判）。
 * 完成后不另设冷却：真名以后照常给，每个 10~20 分钟，和风之契约靠药水估的节奏同一量级。
 *
 * <h2>存哪</h2>
 * 玩家数据的 {@code wishes}（心愿数）和 {@code wish}（{@link Wish#toTag}：{@code type}、{@code param}、{@code deadline}（游戏刻）、{@code progress}；
 * 过期后只剩 {@code cooldown}）。给出 / 完成 / 过期时同步给本人（石板提示要心愿数），每秒的进度只存不发（{@link IPlayerData#setWindWish} 的 sync = false）。
 * 疾跑 / 滑翔上一秒的位置、落羽的离地最高点这些不存盘，放在 {@link #TRACKERS}（只有手上有心愿的在线玩家才有一份），重进游戏从头记。
 *
 * <h2>风之真名的进度与老存档</h2>
 * {@link #checkTrueName}：好感增加（{@code WindAffinity.add} 里调）、心愿完成、登录三处查一次 {@link WindTabletItem#attuned}，第一次成立时发
 * {@link #TRUE_NAME_ADVANCEMENT}。老存档（玩家数据里还没有 {@code wishes} 键）登录时由 {@link #migrateLegacy} 处理：好感已到门槛的，心愿数直接记成
 * {@code general.wind_wish.required}（他们开门的时候还没有心愿这回事），登录时再补 {@code true_name}；没到门槛的照常要做心愿。
 * 已经开过门、好感却低于（服主调高后的）门槛的老玩家，由手册迁移表补 {@code true_name} 的那一条顺带记满心愿数（动作 {@link #LEGACY_ACTION}）。
 * <p>
 * 登录时的顺序：{@link #migrateLegacy} 在 NORMAL，手册迁移（{@code GuideMigration}）在 LOW，登录时补 {@code true_name} 的
 * {@link WindWishEvents#windWishLoginLate} 在 LOWEST。{@code true_name} 是第十批 10b 才有的进度，老存档里只可能是登录这一刻代码刚发的，
 * 不能让迁移把它当成「走过后面章节」的证据（v1 里它是第十四章「产线」的证明进度之一）。
 * <p>
 * 开销：每名手上有心愿的玩家每秒一次判定（群系查当前格、天空查头顶一格）、每刻一次哈希表查找；没有心愿的玩家只有一次查找。
 * 只作用于玩家自己，不碰方块、不碰别的实体。
 */
public final class WindWishes {

    /** 第一次完成心愿（手册「风之心愿」一步，代码发） */
    public static final ResourceLocation WISH_ADVANCEMENT = EssentialCraft5.id("guide/wind/wish");
    /** 风之真名第一次显全且心愿够数（手册第十六章「风的真名」一步，代码发） */
    public static final ResourceLocation TRUE_NAME_ADVANCEMENT = EssentialCraft5.id("guide/hoanna/true_name");
    /**
     * 手册迁移的动作名（{@code GuideMigration.registerAction}，ECWind.init 登记）：迁移表补上 {@code true_name} 以后，把心愿数记满（{@link #fillLegacyWishes}）。
     * 名字写进了迁移表 v1，上线后不许改。
     */
    public static final String LEGACY_ACTION = EssentialCraft5.MODID + ":wind_wishes_legacy";

    public static final int CLIMB_Y = 200;
    public static final int CLIMB_SECONDS = 30;
    public static final int CHASE_BLOCKS = 400;
    /** 追风一秒最多记几格（疾跑约 5.6 格 / 秒，加速药水、风推一把也到不了 20；再多就是传送或坐东西） */
    public static final int CHASE_MAX_STEP = 20;
    public static final int FEATHER_HEIGHT = 25;
    public static final int MOON_JUMPS = 32;
    /** 月下：两次起跳隔超过这么多刻就从头数（「连跳」） */
    public static final int MOON_GAP_TICKS = 100;
    public static final int ABROAD_SECONDS = 120;
    public static final int GLIDE_BLOCKS = 200;
    /** 乘风一秒最多记几格（烟花加速约 30~35 格 / 秒） */
    public static final int GLIDE_MAX_STEP = 80;
    public static final int THUNDER_SECONDS = 60;

    private static final String KEY_TYPE = "type";
    private static final String KEY_PARAM = "param";
    private static final String KEY_DEADLINE = "deadline";
    private static final String KEY_PROGRESS = "progress";
    private static final String KEY_COOLDOWN = "cooldown";

    /** 手上有心愿的在线玩家 → 不存盘的追踪状态（只在服务端线程用） */
    private static final Map<UUID, Tracker> TRACKERS = new HashMap<>();

    private WindWishes() {
    }

    // ================================================================ 种类

    /** 心愿的种类；{@link #id} 存盘、做语言键后缀，上线后不许改 */
    public enum Type {
        CLIMB("climb", CLIMB_SECONDS),
        CHASE("chase", CHASE_BLOCKS),
        TRAVEL("travel", 0),
        FEATHER("feather", 0),
        MOON("moon", MOON_JUMPS),
        ABROAD("abroad", ABROAD_SECONDS),
        GLIDE("glide", GLIDE_BLOCKS),
        THUNDER("thunder", THUNDER_SECONDS);

        private final String id;
        /** 要攒到多少（秒 / 格 / 次）；0 = 一下子就成（远行、落羽） */
        private final int goal;

        Type(String id, int goal) {
            this.id = id;
            this.goal = goal;
        }

        public String id() {
            return id;
        }

        public int goal() {
            return goal;
        }

        @Nullable
        public static Type byId(String id) {
            for (Type type : values()) {
                if (type.id.equals(id)) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * 远行的群系类别。原版有标签的用标签；原版没有雪原标签，用 Forge 的 {@code forge:is_snowy}；樱花林按群系 id。
     * 数据包把某类全删了（标签空了、群系没了）就不再点它。
     */
    public enum TravelTarget {
        BADLANDS("badlands", BiomeTags.IS_BADLANDS, null),
        JUNGLE("jungle", BiomeTags.IS_JUNGLE, null),
        DEEP_OCEAN("deep_ocean", BiomeTags.IS_DEEP_OCEAN, null),
        MOUNTAIN("mountain", BiomeTags.IS_MOUNTAIN, null),
        TAIGA("taiga", BiomeTags.IS_TAIGA, null),
        SAVANNA("savanna", BiomeTags.IS_SAVANNA, null),
        SNOWY("snowy", Tags.Biomes.IS_SNOWY, null),
        CHERRY_GROVE("cherry_grove", null, Biomes.CHERRY_GROVE);

        private final String id;
        @Nullable
        private final TagKey<Biome> tag;
        @Nullable
        private final ResourceKey<Biome> biome;

        TravelTarget(String id, @Nullable TagKey<Biome> tag, @Nullable ResourceKey<Biome> biome) {
            this.id = id;
            this.tag = tag;
            this.biome = biome;
        }

        public String id() {
            return id;
        }

        public boolean matches(Holder<Biome> holder) {
            return tag != null ? holder.is(tag) : biome != null && holder.is(biome);
        }

        /** 这个世界的群系注册表里还有这类群系 */
        public boolean exists(RegistryAccess access) {
            Registry<Biome> registry = access.registryOrThrow(Registries.BIOME);
            if (tag != null) {
                return registry.getTag(tag).map(set -> set.size() > 0).orElse(false);
            }
            return biome != null && registry.getHolder(biome).isPresent();
        }

        /** 群系类别的名字（语言键） */
        public Component displayName() {
            return Component.translatable("message.essentialcraft5.wind_wish.biome." + id);
        }

        @Nullable
        public static TravelTarget byId(String id) {
            for (TravelTarget target : values()) {
                if (target.id.equals(id)) {
                    return target;
                }
            }
            return null;
        }
    }

    /**
     * 一个心愿：种类、参数（远行 = 群系类别 id，他乡 = 给心愿时所在维度的 id，其余空）、期限（游戏刻）、进度（秒 / 格 / 次）。
     */
    public record Wish(Type type, String param, long deadline, double progress) {

        public CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString(KEY_TYPE, type.id());
            tag.putString(KEY_PARAM, param);
            tag.putLong(KEY_DEADLINE, deadline);
            tag.putDouble(KEY_PROGRESS, progress);
            return tag;
        }

        /** 读不出种类（空标签、只剩冷却、未知的 id）时返回 null */
        @Nullable
        public static Wish fromTag(CompoundTag tag) {
            Type type = tag.contains(KEY_TYPE, Tag.TAG_STRING) ? Type.byId(tag.getString(KEY_TYPE)) : null;
            if (type == null) {
                return null;
            }
            return new Wish(type, tag.getString(KEY_PARAM), tag.getLong(KEY_DEADLINE), Math.max(0D, tag.getDouble(KEY_PROGRESS)));
        }

        public Wish withProgress(double value) {
            return new Wish(type, param, deadline, value);
        }

        /** 风说的那句话（不含「风说出了一个心愿」的前缀） */
        public Component description() {
            String key = "message.essentialcraft5.wind_wish.desc." + type.id();
            return switch (type) {
                case CLIMB -> Component.translatable(key, CLIMB_Y, CLIMB_SECONDS);
                case CHASE -> Component.translatable(key, CHASE_BLOCKS);
                case TRAVEL -> {
                    TravelTarget target = TravelTarget.byId(param);
                    yield Component.translatable(key, target == null ? Component.literal(param) : target.displayName());
                }
                case FEATHER -> Component.translatable(key, FEATHER_HEIGHT);
                case MOON -> Component.translatable(key, MOON_JUMPS);
                case ABROAD -> Component.translatable(key, ABROAD_SECONDS / 60);
                case GLIDE -> Component.translatable(key, GLIDE_BLOCKS);
                case THUNDER -> Component.translatable(key, THUNDER_SECONDS);
            };
        }
    }

    /**
     * 现在能给哪些心愿的依据（{@link #of} 从玩家身上取；GameTest 可以换掉其中一项，例如打不打雷，不动世界的天气）。
     *
     * @param skyDimension 当前维度有天空、没有顶（登高、听雷要露天）
     * @param dayNight     当前维度有昼夜（月下）
     * @param climbable    当前维度的建筑高度够得着 Y {@value #CLIMB_Y}
     * @param elytra       胸甲格是能飞的鞘翅（乘风）
     * @param thundering   正在打雷，而且是有天空的维度（听雷）
     */
    public record Feasibility(boolean skyDimension, boolean dayNight, boolean climbable, boolean elytra, boolean thundering,
                              ResourceKey<Level> dimension, Holder<Biome> biome, RegistryAccess registries) {

        public static Feasibility of(Player player) {
            Level level = player.level();
            boolean sky = level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling();
            boolean dayNight = sky && !level.dimensionType().hasFixedTime();
            boolean climbable = sky && level.getMaxBuildHeight() > CLIMB_Y + 2;
            boolean elytra = player.getItemBySlot(EquipmentSlot.CHEST).canElytraFly(player);
            boolean thundering = sky && WindWishes.thundering(level);
            return new Feasibility(sky, dayNight, climbable, elytra, thundering, level.dimension(), biomeAt(player), level.registryAccess());
        }

        public Feasibility withThundering(boolean value) {
            return new Feasibility(skyDimension, dayNight, climbable, elytra, value, dimension, biome, registries);
        }
    }

    /** 不存盘的追踪状态（见类注释） */
    private static final class Tracker {
        final Type type;
        @Nullable
        Vec3 lastPos;
        @Nullable
        ResourceKey<Level> lastDimension;
        boolean airborne;
        double peakY;
        boolean fallHurt;
        long lastJump = Long.MIN_VALUE / 2;

        Tracker(Type type) {
            this.type = type;
        }
    }

    // ================================================================ 读

    @Nullable
    private static IPlayerData data(Player player) {
        return player instanceof FakePlayer ? null : PlayerDataEvents.get(player);
    }

    /** 完成过几个心愿（两端都能调：客户端读同步来的本人数据） */
    public static int count(Player player) {
        IPlayerData data = data(player);
        return data == null ? 0 : data.getWindWishes();
    }

    /** 当前的心愿；没有返回 null */
    @Nullable
    public static Wish current(Player player) {
        IPlayerData data = data(player);
        return data == null ? null : Wish.fromTag(data.getWindWish());
    }

    /** 过期后要等到哪一刻才能再问（没在冷却时 ≤ 现在） */
    public static long cooldownUntil(Player player) {
        IPlayerData data = data(player);
        return data == null ? 0L : data.getWindWish().getLong(KEY_COOLDOWN);
    }

    /** 风之真名要几个心愿：功能关掉时 0（否则谁都凑不齐），否则读 {@code required} */
    public static int required() {
        return Config.enabled() ? Config.required() : 0;
    }

    /** 心愿能不能换：功能开着、玩家受眷顾（两端一致：客户端读同步来的数据和配置） */
    public static boolean swapApplies(Player player) {
        return Config.enabled() && WindAffinity.isWindbound(player);
    }

    /** 现在能给哪些种类（见类注释的表）；exclude 非空时排除它 */
    public static List<Type> options(Feasibility f, @Nullable Type exclude) {
        List<Type> types = new ArrayList<>(Type.values().length);
        for (Type type : Type.values()) {
            if (type == exclude) {
                continue;
            }
            boolean ok = switch (type) {
                case CLIMB -> f.climbable();
                case CHASE, FEATHER, ABROAD -> true;
                case TRAVEL -> !travelTargets(f).isEmpty();
                case MOON -> f.dayNight();
                case GLIDE -> f.elytra();
                case THUNDER -> f.thundering();
            };
            if (ok) {
                types.add(type);
            }
        }
        return types;
    }

    /** 远行能点的群系类别：这个世界还有、而且玩家现在不在里面 */
    public static List<TravelTarget> travelTargets(Feasibility f) {
        List<TravelTarget> targets = new ArrayList<>();
        for (TravelTarget target : TravelTarget.values()) {
            if (target.exists(f.registries()) && !target.matches(f.biome())) {
                targets.add(target);
            }
        }
        return targets;
    }

    /** 挑一个心愿（先等概率挑种类，远行再挑群系）；一个都给不了返回 null */
    @Nullable
    public static Wish pick(Feasibility f, @Nullable Type exclude, RandomSource random, long now) {
        List<Type> types = options(f, exclude);
        if (types.isEmpty()) {
            return null;
        }
        Type type = types.get(random.nextInt(types.size()));
        String param = "";
        if (type == Type.TRAVEL) {
            List<TravelTarget> targets = travelTargets(f);
            param = targets.get(random.nextInt(targets.size())).id();
        } else if (type == Type.ABROAD) {
            param = f.dimension().location().toString();
        }
        return new Wish(type, param, now + Config.deadlineTicks(), 0D);
    }

    // ================================================================ 要、换

    /** {@link #ask} 的结果（GameTest 断言用） */
    public enum AskResult {
        /** 心愿功能关着 */
        DISABLED,
        /** 没受风之眷顾：风不回应 */
        NOT_WINDBOUND,
        /** 已经有一个心愿：提醒一遍，不换 */
        ACTIVE,
        /** 上一个心愿过期了，还在冷却 */
        COOLDOWN,
        /** 一个都给不了（正常不会出现：追风总能给） */
        NONE_AVAILABLE,
        /** 心愿数已经够了、风之真名还没显全：风不再托付新的心愿（见类注释「完成」） */
        RESTING,
        /** 给出了一个心愿 */
        GIVEN
    }

    /** 问风要一个心愿（只在服务端、只给真玩家；见类注释） */
    public static AskResult ask(ServerPlayer player) {
        if (!Config.enabled()) {
            return AskResult.DISABLED;
        }
        IPlayerData data = data(player);
        if (data == null || !ECProtection.isRealPlayer(player) || !data.isWindbound()) {
            hint(player, Component.translatable("message.essentialcraft5.wind_wish.not_windbound"));
            return AskResult.NOT_WINDBOUND;
        }
        long now = player.level().getGameTime();
        Wish wish = Wish.fromTag(data.getWindWish());
        if (wish != null) {
            remind(player, wish, now);
            return AskResult.ACTIVE;
        }
        long cooldown = clampCooldown(data, now);
        if (now < cooldown) {
            hint(player, Component.translatable("message.essentialcraft5.wind_wish.cooldown", Mth.ceil((cooldown - now) / 20D)));
            return AskResult.COOLDOWN;
        }
        if (data.getWindWishes() >= required() && !WindTabletItem.nameRevealed(player)) {
            hint(player, Component.translatable("message.essentialcraft5.wind_wish.resting"));
            return AskResult.RESTING;
        }
        Wish next = pick(Feasibility.of(player), null, player.getRandom(), now);
        if (next == null) {
            hint(player, Component.translatable("message.essentialcraft5.wind_wish.no_other"));
            return AskResult.NONE_AVAILABLE;
        }
        give(player, data, next);
        return AskResult.GIVEN;
    }

    /** {@link #swap} 的结果 */
    public enum SwapResult {
        NOT_WINDBOUND,
        /** 手上没有心愿：药水不动 */
        NO_WISH,
        /** 想不出别的心愿：药水不动 */
        NO_OTHER,
        /** 换了，药水用掉一瓶 */
        SWAPPED
    }

    /** 用一瓶风之药水换一个别的心愿（只在服务端；potion 是主手那组，换成了才扣一瓶，创造模式也扣，同喝药水） */
    public static SwapResult swap(ServerPlayer player, ItemStack potion) {
        IPlayerData data = data(player);
        if (!Config.enabled() || data == null || !ECProtection.isRealPlayer(player) || !data.isWindbound()) {
            return SwapResult.NOT_WINDBOUND;
        }
        Wish wish = Wish.fromTag(data.getWindWish());
        if (wish == null) {
            hint(player, Component.translatable("message.essentialcraft5.wind_wish.no_wish"));
            return SwapResult.NO_WISH;
        }
        Wish next = pick(Feasibility.of(player), wish.type(), player.getRandom(), player.level().getGameTime());
        if (next == null) {
            hint(player, Component.translatable("message.essentialcraft5.wind_wish.no_other"));
            return SwapResult.NO_OTHER;
        }
        potion.shrink(1);
        say(player, Component.translatable("message.essentialcraft5.wind_wish.swapped"));
        give(player, data, next);
        return SwapResult.SWAPPED;
    }

    private static void give(ServerPlayer player, IPlayerData data, Wish wish) {
        data.setWindWish(wish.toTag(), true);
        TRACKERS.put(player.getUUID(), new Tracker(wish.type()));
        say(player, Component.translatable("message.essentialcraft5.wind_wish.give", wish.description()));
    }

    /** 已有心愿时的提醒：心愿、进度、还剩多久（快捷栏上方） */
    private static void remind(ServerPlayer player, Wish wish, long now) {
        int minutes = Math.max(1, Mth.ceil(Math.min(wish.deadline() - now, Config.deadlineTicks()) / 1200D));
        Component text = wish.type().goal() > 0
                ? Component.translatable("message.essentialcraft5.wind_wish.active", wish.description(),
                (int) Math.min(wish.progress(), wish.type().goal()), wish.type().goal(), minutes)
                : Component.translatable("message.essentialcraft5.wind_wish.active_simple", wish.description(), minutes);
        hint(player, text);
    }

    // ================================================================ 推进

    /**
     * 每秒一次（{@link WindWishEvents#windWishTick}）：过期、累计型心愿的进度、远行的群系。GameTest 直接调它推进计时。
     */
    public static void secondTick(ServerPlayer player) {
        IPlayerData data = data(player);
        if (data == null) {
            return;
        }
        Wish wish = Wish.fromTag(data.getWindWish());
        long now = player.level().getGameTime();
        if (wish == null) {
            TRACKERS.remove(player.getUUID());
            clampCooldown(data, now);
            return;
        }
        Tracker tracker = tracker(player, wish.type());
        if (wish.deadline() - now > Config.deadlineTicks()) {
            wish = new Wish(wish.type(), wish.param(), now + Config.deadlineTicks(), wish.progress());
            data.setWindWish(wish.toTag(), true);
        }
        if (now >= wish.deadline()) {
            expire(player, data, now);
            return;
        }
        double progress = wish.progress();
        Vec3 pos = player.position();
        ResourceKey<Level> dimension = player.level().dimension();
        boolean sameDimension = tracker.lastPos != null && dimension.equals(tracker.lastDimension);
        switch (wish.type()) {
            case CLIMB -> {
                if (player.getY() >= CLIMB_Y && skyAbove(player)) {
                    progress += 1D;
                }
            }
            case CHASE -> {
                if (player.isSprinting() && sameDimension && !player.isPassenger()) {
                    double dx = pos.x - tracker.lastPos.x;
                    double dz = pos.z - tracker.lastPos.z;
                    progress += Math.min(Math.sqrt(dx * dx + dz * dz), CHASE_MAX_STEP);
                }
            }
            case TRAVEL -> {
                TravelTarget target = TravelTarget.byId(wish.param());
                if (target != null && target.matches(biomeAt(player))) {
                    complete(player, data);
                    return;
                }
            }
            case ABROAD -> {
                if (!dimension.location().toString().equals(wish.param())) {
                    progress += 1D;
                }
            }
            case GLIDE -> {
                if (player.isFallFlying() && sameDimension) {
                    progress += Math.min(pos.distanceTo(tracker.lastPos), GLIDE_MAX_STEP);
                } else if (!player.isFallFlying()) {
                    progress = 0D;
                }
            }
            case THUNDER -> {
                if (thundering(player.level()) && skyAbove(player)) {
                    progress += 1D;
                }
            }
            case FEATHER, MOON -> {
                // 每刻 / 起跳事件里判
            }
        }
        tracker.lastPos = pos;
        tracker.lastDimension = dimension;
        if (wish.type().goal() > 0 && progress >= wish.type().goal()) {
            complete(player, data);
            return;
        }
        if (progress != wish.progress()) {
            data.setWindWish(wish.withProgress(progress).toTag(), false);
        }
    }

    /**
     * 落羽：每刻一次，只对手上是落羽的玩家。离地时记最高点，落地 / 入水那刻比高度差；飞行、鞘翅、骑乘、爬梯子都算「没在落」，从头记。
     * 摔落伤害由 {@link #onFallDamage} 记下（同一次落下里先于落地判断到达：服务端的摔落伤害和「着地」是同一个移动包里算的）。
     */
    public static void featherTick(ServerPlayer player) {
        Tracker tracker = TRACKERS.get(player.getUUID());
        if (tracker == null || tracker.type != Type.FEATHER) {
            return;
        }
        if (player.getAbilities().flying || player.isFallFlying() || player.isPassenger() || player.isSpectator() || player.onClimbable()) {
            tracker.airborne = false;
            return;
        }
        boolean grounded = player.onGround() || player.isInWater() || player.isInLava();
        double y = player.getY();
        if (!grounded) {
            if (!tracker.airborne) {
                tracker.airborne = true;
                tracker.peakY = y;
                tracker.fallHurt = false;
            } else if (y > tracker.peakY) {
                tracker.peakY = y;
            }
            return;
        }
        if (tracker.airborne) {
            tracker.airborne = false;
            if (tracker.peakY - y >= FEATHER_HEIGHT && !tracker.fallHurt) {
                IPlayerData data = data(player);
                Wish wish = data == null ? null : Wish.fromTag(data.getWindWish());
                if (wish != null && wish.type() == Type.FEATHER) {
                    complete(player, data);
                }
            }
        }
    }

    /** 摔落伤害真的落下了（LivingDamageEvent）：这一次落下不算落羽 */
    public static void onFallDamage(ServerPlayer player) {
        Tracker tracker = TRACKERS.get(player.getUUID());
        if (tracker != null && tracker.type == Type.FEATHER) {
            tracker.fallHurt = true;
        }
    }

    /** 月下：服务端的起跳（原版在移动包里认出起跳时调 jumpFromGround，发 LivingJumpEvent） */
    public static void onJump(ServerPlayer player) {
        Tracker tracker = TRACKERS.get(player.getUUID());
        if (tracker == null || tracker.type != Type.MOON) {
            return;
        }
        IPlayerData data = data(player);
        Wish wish = data == null ? null : Wish.fromTag(data.getWindWish());
        if (wish == null || wish.type() != Type.MOON || !night(player.level()) || !skyAbove(player)) {
            return;
        }
        long now = player.level().getGameTime();
        double progress = now - tracker.lastJump > MOON_GAP_TICKS ? 1D : wish.progress() + 1D;
        tracker.lastJump = now;
        if (progress >= MOON_JUMPS) {
            complete(player, data);
        } else {
            data.setWindWish(wish.withProgress(progress).toTag(), false);
        }
    }

    private static void complete(ServerPlayer player, IPlayerData data) {
        int before = data.getWindWishes();
        data.setWindWish(new CompoundTag(), true);
        data.setWindWishes(before + 1);
        TRACKERS.remove(player.getUUID());
        say(player, Component.translatable("message.essentialcraft5.wind_wish.done"));
        // 真名之前只有前 required 个心愿给好感（平时 ask 已经不给第 required + 1 个，这里兜服主中途调低 required 的情形）
        if (before < required() || WindTabletItem.nameRevealed(player)) {
            WindRelations.grant(player, Config.points(), player.getRandom());
        }
        award(player, WISH_ADVANCEMENT);
        checkTrueName(player);
    }

    /**
     * 读冷却的截止刻。截止刻比「现在 + cooldown_ticks」还晚，说明存的时候和现在的游戏刻对不上
     * （玩家数据在两个游戏刻差很多的存档 / 服之间搬过，或世界时间被回退），照原样等下去会冷却几天；
     * 这时按现在重新起算，并写回，之后照常倒数
     */
    private static long clampCooldown(IPlayerData data, long now) {
        long cooldown = data.getWindWish().getLong(KEY_COOLDOWN);
        long latest = now + Config.cooldownTicks();
        if (cooldown <= latest) {
            return cooldown;
        }
        CompoundTag tag = new CompoundTag();
        tag.putLong(KEY_COOLDOWN, latest);
        data.setWindWish(tag, true);
        return latest;
    }

    private static void expire(ServerPlayer player, IPlayerData data, long now) {
        CompoundTag tag = new CompoundTag();
        tag.putLong(KEY_COOLDOWN, now + Config.cooldownTicks());
        data.setWindWish(tag, true);
        TRACKERS.remove(player.getUUID());
        say(player, Component.translatable("message.essentialcraft5.wind_wish.expired"));
    }

    private static Tracker tracker(ServerPlayer player, Type type) {
        Tracker tracker = TRACKERS.get(player.getUUID());
        if (tracker == null || tracker.type != type) {
            tracker = new Tracker(type);
            TRACKERS.put(player.getUUID(), tracker);
        }
        return tracker;
    }

    /** 按玩家数据重建 / 去掉追踪状态（登录、重生时） */
    public static void refreshTracker(ServerPlayer player) {
        Wish wish = current(player);
        if (wish == null) {
            TRACKERS.remove(player.getUUID());
        } else {
            TRACKERS.put(player.getUUID(), new Tracker(wish.type()));
        }
    }

    // ================================================================ 风之真名、老存档

    /** 风之真名成立（好感够、心愿够）就发 {@link #TRUE_NAME_ADVANCEMENT}；已经发过什么都不做 */
    public static void checkTrueName(Player player) {
        if (player instanceof ServerPlayer serverPlayer && ECProtection.isRealPlayer(serverPlayer) && WindTabletItem.attuned(serverPlayer)) {
            award(serverPlayer, TRUE_NAME_ADVANCEMENT);
        }
    }

    /**
     * 老存档（见类注释）：读档时没有 {@code wishes} 键、好感已到门槛的，心愿数记成 {@code general.wind_wish.required}。返回改没改。
     * 只认 {@code PlayerData} 读档时记下的标记，标记取一次就清掉；GameTest 直接调。
     * <p>
     * 比的是配置值本身（{@link Config#required()}），不是 {@link #required()}：第一次登录时心愿功能刚好关着，{@link #required()} 是 0，
     * 标记却已经取走了；以后服主打开心愿，好感早就够的老玩家就得回头补做。
     */
    public static boolean migrateLegacy(ServerPlayer player) {
        if (!(PlayerDataEvents.get(player) instanceof PlayerData data) || !data.takeLegacyWishes()) {
            return false;
        }
        int required = Config.required();
        if (data.getWindPoints() < ECWind.Config.tabletPoints() || data.getWindWishes() >= required) {
            return false;
        }
        data.setWindWishes(required);
        EssentialCraft5.LOGGER.info("[EC5] 风之心愿：老存档里 {} 的好感已到门槛（{}），心愿数直接记成 {}",
                player.getGameProfile().getName(), data.getWindPoints(), required);
        return true;
    }

    /**
     * 手册迁移的动作 {@link #LEGACY_ACTION}：迁移表因为玩家走过后面的章节（开过门、进过海纳尔……）补上 {@code true_name} 以后，心愿数不到
     * {@code general.wind_wish.required} 的记满。管的是 {@link #migrateLegacy} 漏掉的那批：服主调高了 {@code tablet_points}，老玩家的好感低于新门槛，
     * 可在旧规则下早就过了这道门。记满不给他们任何新东西，石板和契约照样要好感够新门槛。用配置值，理由同 {@link #migrateLegacy}。
     */
    public static void fillLegacyWishes(ServerPlayer player) {
        IPlayerData data = data(player);
        int required = Config.required();
        if (data == null || data.getWindWishes() >= required) {
            return;
        }
        data.setWindWishes(required);
        EssentialCraft5.LOGGER.info("[EC5] 风之心愿：{} 在旧规则下已经过了风之真名这一步，心愿数记成 {}", player.getGameProfile().getName(), required);
    }

    // ================================================================ 小工具

    /** 头顶露天：眼睛所在那一格看得见天 */
    public static boolean skyAbove(Player player) {
        return player.level().canSeeSky(BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()));
    }

    /** 夜里（GameTest 可覆盖，不动世界的时间） */
    public static boolean night(Level level) {
        Boolean override = Config.testNight;
        return override != null ? override : level.isNight();
    }

    /** 正在打雷（GameTest 可覆盖，不动世界的天气） */
    public static boolean thundering(Level level) {
        Boolean override = Config.testThundering;
        return override != null ? override : level.isThundering();
    }

    /** 玩家所在格的群系（GameTest 可覆盖） */
    public static Holder<Biome> biomeAt(Player player) {
        Holder<Biome> override = Config.testBiome;
        return override != null ? override : player.level().getBiome(player.blockPosition());
    }

    /** 给手册进度：把剩下的条件全部授予（进度文件被数据包删掉时什么都不做） */
    public static void award(ServerPlayer player, ResourceLocation id) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        if (advancement == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) {
            return;
        }
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    /** 风的话：聊天栏，深青色斜体（同 {@link WindRelations}） */
    private static void say(ServerPlayer player, MutableComponent text) {
        player.sendSystemMessage(text.withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
    }

    /** 快捷栏上方的一句 */
    private static void hint(ServerPlayer player, Component text) {
        player.displayClientMessage(text.copy().withStyle(ChatFormatting.DARK_AQUA), true);
    }

    // ================================================================ 事件

    /** Forge 总线（类名带模块前缀，见 tools/check_event_handlers.py） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class WindWishEvents {

        private WindWishEvents() {
        }

        @SubscribeEvent
        public static void windWishTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER
                    || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) {
                return;
            }
            Tracker tracker = TRACKERS.get(player.getUUID());
            if (tracker == null) {
                return;
            }
            if (tracker.type == Type.FEATHER) {
                featherTick(player);
            }
            if (player.tickCount % 20 == 0) {
                secondTick(player);
            }
        }

        @SubscribeEvent
        public static void windWishJump(LivingEvent.LivingJumpEvent event) {
            if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
                onJump(player);
            }
        }

        /** 摔落伤害真的落下了才算（被别的模组 / 插件拦下的不算），见 {@link #onFallDamage} */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void windWishFallDamage(LivingDamageEvent event) {
            if (event.getAmount() > 0F && event.getSource().is(DamageTypeTags.IS_FALL)
                    && event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
                onFallDamage(player);
            }
        }

        /**
         * 手持风之药水潜行右键风之符文：放行方块交互，交给 {@code WindRuneBlock.use} 去换心愿（见类注释）。两端都走：
         * 客户端放行后方块的 use 返回 SUCCESS，就不会再开始喝药水。
         */
        @SubscribeEvent
        public static void windWishSwapClick(PlayerInteractEvent.RightClickBlock event) {
            Player player = event.getEntity();
            if (event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
                    || !player.getMainHandItem().is(ECWind.AIR_POTION.get())
                    || !event.getLevel().getBlockState(event.getPos()).is(ECWind.WIND_RUNE.get())
                    || !swapApplies(player)) {
                return;
            }
            event.setUseBlock(Event.Result.ALLOW);
        }

        /** 登录（NORMAL，排在手册迁移的 LOW 之前）：老存档记满心愿数、重建追踪状态。补 true_name 放在 {@link #windWishLoginLate} */
        @SubscribeEvent
        public static void windWishLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
                migrateLegacy(player);
                refreshTracker(player);
            }
        }

        /**
         * 登录时查一次风之真名（LOWEST，排在手册迁移的 LOW 之后）：这一刻发的 true_name 不能被迁移当成证明进度（见类注释「风之真名的进度与老存档」）
         */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void windWishLoginLate(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
                checkTrueName(player);
            }
        }

        @SubscribeEvent
        public static void windWishRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
                refreshTracker(player);
            }
        }

        @SubscribeEvent
        public static void windWishLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            TRACKERS.remove(event.getEntity().getUUID());
        }

        @SubscribeEvent
        public static void windWishServerStopped(ServerStoppedEvent event) {
            TRACKERS.clear();
        }
    }

    // ================================================================ 配置

    /** SERVER 配置 {@code general.wind_wish}（ECWind.init 登记） */
    public static final class Config {
        public static final boolean DEFAULT_ENABLED = true;
        public static final int DEFAULT_REQUIRED = 7;
        public static final int DEFAULT_POINTS = 1000;
        public static final int DEFAULT_DEADLINE_TICKS = 72_000;
        public static final int DEFAULT_COOLDOWN_TICKS = 2_400;

        @Nullable
        public static ForgeConfigSpec.BooleanValue enabledValue;
        @Nullable
        public static ForgeConfigSpec.IntValue requiredValue;
        @Nullable
        public static ForgeConfigSpec.IntValue pointsValue;
        @Nullable
        public static ForgeConfigSpec.IntValue deadlineValue;
        @Nullable
        public static ForgeConfigSpec.IntValue cooldownValue;

        // GameTest 的测试覆盖值（null = 读真配置；不许在测试里 ConfigValue.set）
        @Nullable
        private static Boolean testEnabled;
        @Nullable
        private static Integer testRequired;
        @Nullable
        private static Integer testCooldown;
        @Nullable
        static Boolean testNight;
        @Nullable
        static Boolean testThundering;
        @Nullable
        static Holder<Biome> testBiome;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Wind wishes: the wind asks favours of Windbound players at a Wind Rune.", "风之心愿：受眷顾的玩家在风之符文上问风要心愿")
                    .push("wind_wish");
            enabledValue = builder.comment("Whether the wind gives wishes at all. When off, the Wind Tablet needs no wishes.",
                            "风会不会给心愿；关掉时风之石板不要心愿")
                    .define("enabled", DEFAULT_ENABLED);
            requiredValue = builder.comment("Wishes the Wind Tablet needs, on top of wind relation, before it answers (0 = none).",
                            "风之石板回应之前要完成几个心愿（另外还要够好感；0 = 不要心愿）")
                    .defineInRange("required", DEFAULT_REQUIRED, 0, 20);
            pointsValue = builder.comment("Raw wind relation for each fulfilled wish (multiplied by the wind relation coefficient, 0.1 by default).",
                            "完成一个心愿给的风之好感原始值（再乘风之好感系数，默认 0.1）")
                    .defineInRange("points", DEFAULT_POINTS, 0, 100_000);
            deadlineValue = builder.comment("Ticks a wish lasts before it fades (72000 = 3 in-game days).",
                            "一个心愿多少刻后过期（72000 = 3 个游戏日）")
                    .defineInRange("deadline_ticks", DEFAULT_DEADLINE_TICKS, 1_200, 2_400_000);
            cooldownValue = builder.comment("Ticks to wait after a wish fades before asking again.",
                            "心愿过期以后多少刻才能再问")
                    .defineInRange("cooldown_ticks", DEFAULT_COOLDOWN_TICKS, 0, 240_000);
            builder.pop();
        }

        public static boolean enabled() {
            Boolean override = testEnabled;
            return override != null ? override : enabledValue == null ? DEFAULT_ENABLED : ECConfig.get(enabledValue);
        }

        public static int required() {
            Integer override = testRequired;
            return override != null ? override : requiredValue == null ? DEFAULT_REQUIRED : ECConfig.get(requiredValue);
        }

        public static int points() {
            return pointsValue == null ? DEFAULT_POINTS : ECConfig.get(pointsValue);
        }

        public static int deadlineTicks() {
            return deadlineValue == null ? DEFAULT_DEADLINE_TICKS : ECConfig.get(deadlineValue);
        }

        public static int cooldownTicks() {
            Integer override = testCooldown;
            return override != null ? override : cooldownValue == null ? DEFAULT_COOLDOWN_TICKS : ECConfig.get(cooldownValue);
        }

        /** GameTest：功能开关、要几个心愿、冷却刻数（null = 读真配置） */
        public static void setTestOverrides(@Nullable Boolean enabled, @Nullable Integer required, @Nullable Integer cooldown) {
            testEnabled = enabled;
            testRequired = required;
            testCooldown = cooldown;
        }

        /** GameTest：夜里、打雷、所在群系（null = 读世界；只影响心愿的判定，不动世界） */
        public static void setTestEnvironment(@Nullable Boolean night, @Nullable Boolean thundering, @Nullable Holder<Biome> biome) {
            testNight = night;
            testThundering = thundering;
            testBiome = biome;
        }

        public static void clearTestOverrides() {
            setTestOverrides(null, null, null);
            setTestEnvironment(null, null, null);
        }
    }

    /** 给 GameTest：这名玩家现在有没有追踪状态 */
    public static boolean isTracked(Player player) {
        return TRACKERS.containsKey(player.getUUID());
    }
}
