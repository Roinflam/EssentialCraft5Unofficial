package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.api.WindAffinity;
import com.flameforge.essentialcraft5.client.WindTrialClient;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.entity.WindMageSummons;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvents;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECWind;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * 风之号角（第十批 10b-M1「风之试炼」，batch10.md §5.5.2~§5.5.3，D3 A）：原版山羊角（哪种曲调都行）在风之符文上注风得到，
 * 注风配方要已受风之眷顾（{@code requires_windbound}，没受眷顾的人注风直接拒绝、什么都不扣），花费读 {@code general.wind_horn.espe}（默认 20 000）。
 * <p>
 * <b>吹</b>：夜里（{@link #night}，同原版的口径：雷雨天的白天也算；或者海纳尔的黑暗天灾中）在主世界或海纳尔头顶露天的地方，像原版山羊角一样按住使用 {@value #USE_TICKS} 刻；
 * 吹完以后 {@value #SUMMON_DELAY} 刻，风把一只风之大法师和 {@code escorts} 只随机的学徒 / 法师带到 {@code summon_min}~{@code summon_max} 格外。
 * 召来的法师的规矩（只盯召唤者、打旁人取消、离开 / 下线 / 死掉 / 没人打就随风散去、只有召唤者亲手打死才掉落）见 {@link WindMageSummons}。
 * <p>
 * 判断顺序（{@link #refusal}，不满足就在快捷栏上方说原因、号角不消耗）：受眷顾 → 不是和平难度 → 主世界或海纳尔 → 头顶露天 → 夜里 / 黑暗天灾 →
 * 不在冷却（{@code cooldown_ticks}，存在玩家数据里，重进游戏不会清掉；同时给原版的物品冷却，号角图标上看得见）→ 没有正在场上（或正在赶来）的一批。
 * 吹完再找生成点（{@link WindMageSummons#plan}：最多 16 个点，逐个以召唤者身份 {@code ECProtection.check}，还要求生成处无碰撞、头顶露天、脚下能站）；
 * 一个都不行就不召、号角不消耗。找到了才消耗号角（创造模式不消耗）、开始冷却、排进 {@link WindMageSummons} 的待召唤表。
 * 开始吹时两端照原版山羊角放一声「召唤」曲调（音量 {@value #SOUND_VOLUME}，约 {@value #SOUND_RANGE} 格内听得到；原版 16、256 格）。
 * <p>
 * 客户端在开始吹时用能拿到的信息先判一遍（{@link #clientRefusal}：受眷顾、维度、露天、夜里或海纳尔的黑暗天灾、和平、同步来的冷却），判不过就不举号角；
 * 服务端是准的，原因由服务端说。「夜里」两端都按时间和天气现算（{@link #night}）：客户端的 {@code Level.isNight()} 读的 skyDarken 只在进服时算一次，
 * 之后就冻住了。原版的物品冷却只存在内存里，死亡重生、换维度时客户端会丢，所以登录、重生、换维度三处都按玩家数据里记的补一次
 * （{@link WindHornEvents}），图标上照样转圈。
 * <p>
 * 防刷：每次吹掉一支号角 = 2 万 ESPE + 一支山羊角；只有召唤者亲手打死才掉落；同一人同时只有一批、冷却 10 分钟（policy 防刷怪塔）。
 */
public class WindHornItem extends Item {

    /** 按住吹多久（刻） */
    public static final int USE_TICKS = 60;
    /** 吹完到法师赶来（刻） */
    public static final int SUMMON_DELAY = 100;
    /** 原版山羊角的「召唤」曲调（{@code SoundEvents.GOAT_HORN_SOUND_VARIANTS} 第 5 个，call_goat_horn） */
    public static final int SOUND_VARIANT = 5;
    public static final float SOUND_VOLUME = 4F;
    public static final int SOUND_RANGE = 64;

    /** 吹不响的原因（GameTest 断言用） */
    public enum Refusal {
        NONE,
        NOT_WINDBOUND,
        PEACEFUL,
        WRONG_DIMENSION,
        NO_SKY,
        NOT_NIGHT,
        COOLDOWN,
        /** 上一批还在场上（或正在赶来） */
        BUSY,
        /** 附近没有能落脚、又有权限的地方 */
        NO_SPACE;

        String key() {
            return "message.essentialcraft5.wind_horn." + name().toLowerCase(Locale.ROOT);
        }
    }

    public WindHornItem(Properties properties) {
        super(properties);
    }

    // ================================================================ 吹

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            if (clientRefusal(player) != Refusal.NONE) {
                return InteractionResultHolder.fail(stack);
            }
        } else {
            if (!(player instanceof ServerPlayer serverPlayer) || !ECProtection.isRealPlayer(serverPlayer)) {
                return InteractionResultHolder.fail(stack);
            }
            Refusal refusal = refusal(serverPlayer);
            if (refusal != Refusal.NONE) {
                tell(serverPlayer, refusal);
                return InteractionResultHolder.fail(stack);
            }
        }
        player.startUsingItem(hand);
        SoundEvent sound = SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(SOUND_VARIANT).value();
        level.playSound(player, player, sound, SoundSource.RECORDS, SOUND_VOLUME, 1.0F);
        level.gameEvent(GameEvent.INSTRUMENT_PLAY, player.position(), GameEvent.Context.of(player));
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player && ECProtection.isRealPlayer(player)) {
            blow(player, stack, null);
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.TOOT_HORN;
    }

    /**
     * 吹响以后（服务端）：再判一遍条件，找生成点，找到了消耗号角、开始冷却、排进待召唤表。
     *
     * @param candidates 生成点候选；null 时按配置的距离在召唤者周围随机取（GameTest 传测试场地里的点）
     * @return {@link Refusal#NONE} = 召唤成功（法师 {@value #SUMMON_DELAY} 刻后到）
     */
    public static Refusal blow(ServerPlayer player, ItemStack stack, @Nullable List<BlockPos> candidates) {
        Refusal refusal = refusal(player);
        if (refusal != Refusal.NONE) {
            tell(player, refusal);
            return refusal;
        }
        ServerLevel level = player.serverLevel();
        WindMageSummons.Plan plan = WindMageSummons.plan(player, candidates != null ? candidates : WindMageSummons.candidates(player));
        if (plan == null) {
            tell(player, Refusal.NO_SPACE);
            return Refusal.NO_SPACE;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        startCooldown(player);
        WindMageSummons.schedule(player, plan, level.getGameTime() + SUMMON_DELAY);
        player.displayClientMessage(Component.translatable("message.essentialcraft5.wind_horn.blown").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC), true);
        return Refusal.NONE;
    }

    /** 服务端的判断（见类注释的顺序） */
    public static Refusal refusal(ServerPlayer player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null || player instanceof FakePlayer || !data.isWindbound()) {
            return Refusal.NOT_WINDBOUND;
        }
        ServerLevel level = player.serverLevel();
        if (!Config.ignoreEnvironment()) {
            if (level.getDifficulty() == Difficulty.PEACEFUL) {
                return Refusal.PEACEFUL;
            }
            boolean hoanna = ECHoanna.isHoanna(level);
            if (!hoanna && level.dimension() != Level.OVERWORLD) {
                return Refusal.WRONG_DIMENSION;
            }
            if (!skyAbove(player)) {
                return Refusal.NO_SKY;
            }
            if (!night(level) && !(hoanna && HoannaEvents.isActive(level, HoannaEvent.DARKNESS))) {
                return Refusal.NOT_NIGHT;
            }
        }
        if (level.getGameTime() < data.getWindHornCooldown()) {
            return Refusal.COOLDOWN;
        }
        if (WindMageSummons.hasBatch(player.getUUID())) {
            return Refusal.BUSY;
        }
        return Refusal.NONE;
    }

    /**
     * 客户端先判一遍（见类注释）：服务端那几项里客户端拿得到的，顺序同 {@link #refusal}（批还在不在客户端不知道，不判）。
     * GameTest 也拿服务端玩家调：海纳尔黑暗天灾那一项只有客户端知道，服务端玩家调时按没有天灾算。
     */
    public static Refusal clientRefusal(Player player) {
        if (!WindAffinity.isWindbound(player)) {
            return Refusal.NOT_WINDBOUND;
        }
        Level level = player.level();
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return Refusal.PEACEFUL;
        }
        boolean hoanna = ECHoanna.isHoanna(level);
        if (!hoanna && level.dimension() != Level.OVERWORLD) {
            return Refusal.WRONG_DIMENSION;
        }
        if (!skyAbove(player)) {
            return Refusal.NO_SKY;
        }
        if (!night(level) && !(hoanna && Boolean.TRUE.equals(DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> WindTrialClient::hoannaDarkness)))) {
            return Refusal.NOT_NIGHT;
        }
        // 原版物品冷却在客户端可能已经丢了（重生、换维度），玩家数据里同步来的冷却兜底
        IPlayerData data = PlayerDataEvents.get(player);
        if (data != null && level.getGameTime() < data.getWindHornCooldown()) {
            return Refusal.COOLDOWN;
        }
        return Refusal.NONE;
    }

    /**
     * 夜里：照原版 {@code Level.updateSkyBrightness} 的式子按时间和天气现算 skyDarken，≥ 4 算夜里（同 {@code Level.isNight}：雷雨天的白天也算，
     * 只下雨不算；没有昼夜的维度永远不算）。两端都用它：客户端的 skyDarken 只在 ClientLevel 建好时算一次，{@code isNight()} 在客户端是冻住的。
     */
    public static boolean night(Level level) {
        if (level.dimensionType().hasFixedTime()) {
            return false;
        }
        double rain = 1.0D - (double) (level.getRainLevel(1.0F) * 5.0F) / 16.0D;
        double thunder = 1.0D - (double) (level.getThunderLevel(1.0F) * 5.0F) / 16.0D;
        double sun = 0.5D + 2.0D * Mth.clamp((double) Mth.cos(level.getTimeOfDay(1.0F) * ((float) Math.PI * 2F)), -0.25D, 0.25D);
        return (int) ((1.0D - sun * rain * thunder) * 11.0D) >= 4;
    }

    /** 头顶露天：眼睛所在那一格看得见天 */
    private static boolean skyAbove(Player player) {
        return player.level().canSeeSky(BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()));
    }

    /** 开始冷却：玩家数据里记到哪一刻（存盘），再给原版的物品冷却（图标上的转圈，原版自己同步给客户端） */
    public static void startCooldown(ServerPlayer player) {
        IPlayerData data = PlayerDataEvents.get(player);
        int ticks = Config.cooldownTicks();
        if (data != null) {
            data.setWindHornCooldown(player.level().getGameTime() + ticks);
        }
        if (ticks > 0) {
            player.getCooldowns().addCooldown(ECWind.WIND_HORN.get(), ticks);
        }
    }

    /** 召唤没成（法师赶来时召唤者已经不在了、生成点全被堵上）：号角还回去、冷却清掉 */
    public static void refund(ServerPlayer player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data != null) {
            data.setWindHornCooldown(0L);
        }
        player.getCooldowns().removeCooldown(ECWind.WIND_HORN.get());
        ECItemEntities.giveOrDrop(player, new ItemStack(ECWind.WIND_HORN.get()));
        player.displayClientMessage(Component.translatable("message.essentialcraft5.wind_horn.fizzled").withStyle(ChatFormatting.DARK_AQUA), true);
    }

    private static void tell(ServerPlayer player, Refusal refusal) {
        Component text = refusal == Refusal.COOLDOWN
                ? Component.translatable(refusal.key(), cooldownSeconds(player))
                : Component.translatable(refusal.key());
        player.displayClientMessage(text.copy().withStyle(ChatFormatting.DARK_AQUA), true);
    }

    private static int cooldownSeconds(ServerPlayer player) {
        IPlayerData data = PlayerDataEvents.get(player);
        long left = data == null ? 0L : data.getWindHornCooldown() - player.level().getGameTime();
        return Math.max(1, Mth.ceil(left / 20D));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.essentialcraft5.wind_horn", Config.escorts()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.wind_horn.rules").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ================================================================ 事件

    /** Forge 总线（类名带模块前缀，见 tools/check_event_handlers.py） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class WindHornEvents {

        private WindHornEvents() {
        }

        /** 物品冷却不存盘：登录时按玩家数据里记的补回去（图标上照样转圈） */
        @SubscribeEvent
        public static void windHornLogin(PlayerEvent.PlayerLoggedInEvent event) {
            restoreCooldown(event.getEntity());
        }

        /** 死亡重生、从末地回来是新的 ServerPlayer，物品冷却跟着丢了：照登录补一次 */
        @SubscribeEvent
        public static void windHornRespawn(PlayerEvent.PlayerRespawnEvent event) {
            restoreCooldown(event.getEntity());
        }

        /** 换维度时客户端新建本地玩家，物品冷却丢了：再给一次，服务端会重发冷却包 */
        @SubscribeEvent
        public static void windHornChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            restoreCooldown(event.getEntity());
        }
    }

    /** 按玩家数据里记的冷却补回原版的物品冷却（还剩多少给多少；没在冷却什么都不做）。登录、重生、换维度时调，GameTest 走上面的事件方法 */
    private static void restoreCooldown(Player entity) {
        if (entity instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            IPlayerData data = PlayerDataEvents.get(player);
            long left = data == null ? 0L : data.getWindHornCooldown() - player.level().getGameTime();
            if (left > 0L) {
                player.getCooldowns().addCooldown(ECWind.WIND_HORN.get(), (int) Math.min(Integer.MAX_VALUE, left));
            }
        }
    }

    // ================================================================ 配置

    /** SERVER 配置 {@code general.wind_horn}（ECWind.init 登记） */
    public static final class Config {
        public static final double DEFAULT_ESPE = 20_000D;
        public static final int DEFAULT_COOLDOWN_TICKS = 12_000;
        public static final int DEFAULT_DESPAWN_TICKS = 6_000;
        public static final int DEFAULT_ESCORTS = 2;
        public static final int DEFAULT_LEASH_RANGE = 64;
        public static final int DEFAULT_SUMMON_MIN = 12;
        public static final int DEFAULT_SUMMON_MAX = 20;

        @Nullable
        public static ForgeConfigSpec.DoubleValue espeValue;
        @Nullable
        public static ForgeConfigSpec.IntValue cooldownValue;
        @Nullable
        public static ForgeConfigSpec.IntValue despawnValue;
        @Nullable
        public static ForgeConfigSpec.IntValue escortsValue;
        @Nullable
        public static ForgeConfigSpec.IntValue leashValue;
        @Nullable
        public static ForgeConfigSpec.IntValue summonMinValue;
        @Nullable
        public static ForgeConfigSpec.IntValue summonMaxValue;

        /** GameTest：不看维度、露天、夜里、和平（null / false = 照常判）；只给测试用，不改配置文件 */
        private static boolean testIgnoreEnvironment;
        @Nullable
        private static Integer testCooldown;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Wind Horn: summons a Wind Archmage with escorts for its summoner to fight.", "风之号角：召来风之大法师和随从，给召唤者一个人打")
                    .push("wind_horn");
            espeValue = builder.comment("ESPE cost of imbuing a goat horn into a Wind Horn (recipes marked \"espe_config\": \"horn_espe\").",
                            "注风做一支风之号角要多少 ESPE（只对 JSON 写了 \"espe_config\": \"horn_espe\" 的配方生效）")
                    .defineInRange("espe", DEFAULT_ESPE, 0D, 1.0E7D);
            cooldownValue = builder.comment("Ticks before the same player can blow a Wind Horn again (12000 = 10 minutes).",
                            "同一名玩家吹过号角以后多少刻才能再吹（12000 = 10 分钟）")
                    .defineInRange("cooldown_ticks", DEFAULT_COOLDOWN_TICKS, 0, 720_000);
            despawnValue = builder.comment("Summoned mages blow away after this many ticks without anyone hitting them (6000 = 5 minutes).",
                            "召来的法师这么多刻没人打就随风散去（6000 = 5 分钟）")
                    .defineInRange("despawn_ticks", DEFAULT_DESPAWN_TICKS, 200, 72_000);
            escortsValue = builder.comment("Apprentices / mages that come with the Archmage.",
                            "跟大法师一起来的学徒 / 法师有几只")
                    .defineInRange("escorts", DEFAULT_ESCORTS, 0, 4);
            leashValue = builder.comment("Summoned mages blow away once their summoner is farther than this many blocks.",
                            "召唤者离开这么多格，召来的法师就随风散去")
                    .defineInRange("leash_range", DEFAULT_LEASH_RANGE, 16, 256);
            summonMinValue = builder.comment("Closest distance (blocks) the mages are brought to.",
                            "法师被带到离召唤者最近多少格")
                    .defineInRange("summon_min", DEFAULT_SUMMON_MIN, 4, 64);
            summonMaxValue = builder.comment("Farthest distance (blocks) the mages are brought to (at least summon_min).",
                            "法师被带到离召唤者最远多少格（不小于 summon_min）")
                    .defineInRange("summon_max", DEFAULT_SUMMON_MAX, 4, 64);
            builder.pop();
        }

        public static double espe() {
            return espeValue == null ? DEFAULT_ESPE : ECConfig.get(espeValue);
        }

        public static int cooldownTicks() {
            Integer override = testCooldown;
            return override != null ? override : cooldownValue == null ? DEFAULT_COOLDOWN_TICKS : ECConfig.get(cooldownValue);
        }

        public static int despawnTicks() {
            return despawnValue == null ? DEFAULT_DESPAWN_TICKS : ECConfig.get(despawnValue);
        }

        public static int escorts() {
            return escortsValue == null ? DEFAULT_ESCORTS : ECConfig.get(escortsValue);
        }

        public static int leashRange() {
            return leashValue == null ? DEFAULT_LEASH_RANGE : ECConfig.get(leashValue);
        }

        public static int summonMin() {
            return summonMinValue == null ? DEFAULT_SUMMON_MIN : ECConfig.get(summonMinValue);
        }

        public static int summonMax() {
            int max = summonMaxValue == null ? DEFAULT_SUMMON_MAX : ECConfig.get(summonMaxValue);
            return Math.max(max, summonMin());
        }

        static boolean ignoreEnvironment() {
            return testIgnoreEnvironment;
        }

        /** GameTest / 混合端自检：不看维度、露天、夜里、和平；冷却刻数（null = 读真配置） */
        public static void setTestOverrides(boolean ignoreEnvironment, @Nullable Integer cooldownTicks) {
            testIgnoreEnvironment = ignoreEnvironment;
            testCooldown = cooldownTicks;
        }

        public static void clearTestOverrides() {
            setTestOverrides(false, null);
        }
    }
}
