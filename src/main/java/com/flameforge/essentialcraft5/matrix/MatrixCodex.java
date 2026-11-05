package com.flameforge.essentialcraft5.matrix;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.entity.HologramEntity;
import com.flameforge.essentialcraft5.entity.boss.BossDamageBudget;
import com.flameforge.essentialcraft5.entity.boss.ECBoss;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CMatrixCodexPacket;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.util.ECLandedHit;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 矩阵图谱（第十三批 13a，batch13.md §3.1.1，限制见 policy §4.16）：玩家亲手杀过的敌对生物种类，每种只记第一次。
 * 两份存在玩家数据里：总图谱 {@link IPlayerData#getMatrixCodex()}、在海纳尔杀的另记一份 {@link IPlayerData#getMatrixCodexHoanna()}。
 * <ul>
 *   <li><b>记什么</b>：{@link #countable} = 敌对（{@link Enemy} 或 {@link MobCategory#MONSTER}）或首领（{@code forge:bosses}，首领一律按敌对算，同同调的口径），
 *       不是玩家、不在 {@code essentialcraft5:growth_excluded} 里。全息投影、审判庭哨兵是 MISC 类、故意不实现 {@link Enemy}（免得线圈、恶魔这些「只打敌对」的东西去打它们），
 *       靠首领标签进图谱。</li>
 *   <li><b>谁记</b>（{@link #handKiller}）：致命一击的来源实体是这名真玩家（不是假玩家；箭算射手）；或者致命一击没有来源实体（烧死、摔死），
 *       而最后打它的是这名真玩家、在 {@value #HAND_WINDOW} 刻以内。宠物、别人补刀不算（它们打过以后自己就成了最后打它的）。
 *       <b>首领</b>（{@code forge:bosses}）另算：本场打出不少于最大生命 {@value #BOSS_SHARE_PERCENT}% 有效伤害的真玩家各记一次（{@link #bossParticipants}），
 *       伤害在 {@link LivingDamageEvent} LOWEST 记进弱引用表（打实照 {@link ECLandedHit#landed}，打掉的伤害吸收也算有效伤害）；没人够数时（比如开服前打的那部分没记上）退回亲手的那名。
 *       全息投影（含哨兵）每一下按 {@code general.hologram.max_damage_per_hit} 夹过再记，和它实际掉的血一致。</li>
 *   <li><b>不记</b>：死亡事件被取消（图腾以外的复活）的——监听器不收已取消的事件。</li>
 *   <li><b>矩阵阶关掉</b>（{@code general.matrix_rank.enabled = false}）：关掉是暂停、不毁进度，图谱照记（同样发增量包，不发动作栏那句），
 *       首杀 UBMRU 不给（这是矩阵阶这套系统的奖励）。</li>
 *   <li><b>上限</b>：每份最多 {@code codex_max_entries}（默认 1 024）种，记满了不再记。</li>
 *   <li><b>同步</b>：新记了才发 {@code S2CMatrixCodexPacket.added}（只发那一条），动作栏一句「矩阵图谱 +1」。</li>
 * </ul>
 * 首杀 UBMRU 由 {@code player/UBMRUEvents} 结算：它在同一个死亡事件里先调 {@link #record}，按返回的「这次新记了的玩家」给击杀者另加一份。
 * <p>
 * 分组（{@link Group}）不存，按需从图谱里算（{@link #counts}）：标签组按实体类型标签算，两端都能算（实体标签同步到客户端）。
 */
public final class MatrixCodex {

    /** 致命一击没有来源实体时，往回看多少刻内是不是这名玩家打的（同原版击杀归属的 100 刻、风之号角的口径） */
    public static final int HAND_WINDOW = 100;
    /** 首领：打出不少于最大生命这个比例的有效伤害才算参战（同首领掉落的口径，D25） */
    public static final float BOSS_SHARE = 0.1F;
    static final int BOSS_SHARE_PERCENT = 10;

    /** 首领 → 本场各真玩家打出的有效伤害（弱引用：首领被移除后自动丢掉；只在服务端主线程用） */
    private static final Map<LivingEntity, Ledger> BOSS_DAMAGE = new WeakHashMap<>();

    private MatrixCodex() {
    }

    /** 图谱分组（batch13.md §3.1.1）。按标签算的组带标签，其余按图谱本身算 */
    public enum Group {
        /** 总数 */
        TOTAL("total", null),
        /** 下界本地的种类（{@code essentialcraft5:codex_nether}） */
        NETHER("nether", ECTags.CODEX_NETHER),
        /** 在海纳尔杀过的种类（海纳尔那份图谱） */
        HOANNA("hoanna", null),
        /** 海纳尔原生（{@code essentialcraft5:hoanna_natives}，13d 起有成员） */
        HOANNA_NATIVES("hoanna_natives", ECTags.HOANNA_NATIVES),
        /** 首领（{@code forge:bosses}） */
        BOSSES("bosses", Tags.EntityTypes.BOSSES),
        /** 本模组的生物（命名空间 essentialcraft5） */
        MOD("mod", null),
        /** 末地（{@code essentialcraft5:codex_end}） */
        END("end", ECTags.CODEX_END),
        /** 远征（{@code essentialcraft5:codex_expedition}：袭击、海底神殿、远古城市的怪） */
        EXPEDITION("expedition", ECTags.CODEX_EXPEDITION);

        private final String key;
        @Nullable
        private final TagKey<EntityType<?>> tag;

        Group(String key, @Nullable TagKey<EntityType<?>> tag) {
            this.key = key;
            this.tag = tag;
        }

        /** 数据文件、状态包里的键 */
        public String key() {
            return key;
        }

        /** 组名的翻译键 matrix_codex.essentialcraft5.group.&lt;key&gt; */
        public String nameKey() {
            return "matrix_codex." + EssentialCraft5.MODID + ".group." + key;
        }

        @Nullable
        public TagKey<EntityType<?>> tag() {
            return tag;
        }

        /** 按标签算的组：标签里有几种实体（标签还没绑定时 0）；不按标签算的组返回 −1 */
        public int tagSize() {
            if (tag == null) {
                return -1;
            }
            return BuiltInRegistries.ENTITY_TYPE.getTag(tag).map(set -> set.size()).orElse(0);
        }

        /** 这种实体算不算这一组（总数、海纳尔两组不看种类，恒为真） */
        public boolean contains(EntityType<?> type) {
            if (tag != null) {
                return type.is(tag);
            }
            if (this == MOD) {
                ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
                return id != null && EssentialCraft5.MODID.equals(id.getNamespace());
            }
            return true;
        }

        @Nullable
        public static Group byKey(String key) {
            for (Group group : values()) {
                if (group.key.equals(key)) {
                    return group;
                }
            }
            return null;
        }
    }

    /** 各组的种类数 */
    public record Counts(Map<Group, Integer> values) {

        public int get(Group group) {
            return values.getOrDefault(group, 0);
        }
    }

    // ------------------------------------------------------------------ 查询

    /** 这只生物死了能不能进图谱：敌对或首领、不是玩家、不在 growth_excluded 里（growth_excluded 优先） */
    public static boolean countable(LivingEntity entity) {
        if (entity instanceof Player) {
            return false;
        }
        EntityType<?> type = entity.getType();
        if (type.is(ECTags.GROWTH_EXCLUDED)) {
            return false;
        }
        return entity instanceof Enemy || type.getCategory() == MobCategory.MONSTER || type.is(Tags.EntityTypes.BOSSES);
    }

    /** 各组的种类数。图谱里的 id 已经不存在（删了模组）的只算进总数 */
    public static Counts counts(IPlayerData data) {
        Map<Group, Integer> values = new EnumMap<>(Group.class);
        values.put(Group.TOTAL, data.getMatrixCodex().size());
        values.put(Group.HOANNA, data.getMatrixCodexHoanna().size());
        for (String raw : data.getMatrixCodex()) {
            EntityType<?> type = type(raw);
            if (type == null) {
                continue;
            }
            for (Group group : Group.values()) {
                if (group != Group.TOTAL && group != Group.HOANNA && group.contains(type)) {
                    values.merge(group, 1, Integer::sum);
                }
            }
        }
        return new Counts(values);
    }

    /** 按 id 取实体类型；没注册返回 null（实体注册表有默认值，不先查会拿到猪） */
    @Nullable
    public static EntityType<?> type(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        return id != null && ForgeRegistries.ENTITY_TYPES.containsKey(id) ? ForgeRegistries.ENTITY_TYPES.getValue(id) : null;
    }

    /** 实体类型的 id 字符串（记进图谱的就是它） */
    public static String id(EntityType<?> type) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        return id == null ? "" : id.toString();
    }

    /**
     * 亲手击杀者：致命一击的来源实体是真玩家（箭算射手）；没有来源实体时，最后打它的是真玩家且在 {@value #HAND_WINDOW} 刻内。
     * 来源实体是别的东西（宠物、铁傀儡、别的怪）时返回 null。
     */
    @Nullable
    public static Player handKiller(LivingEntity victim, DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker != null) {
            return attacker instanceof Player player && !(player instanceof FakePlayer) ? player : null;
        }
        LivingEntity last = victim.getLastHurtByMob();
        if (last instanceof Player player && !(player instanceof FakePlayer)
                && victim.tickCount - victim.getLastHurtByMobTimestamp() <= HAND_WINDOW) {
            return player;
        }
        return null;
    }

    // ------------------------------------------------------------------ 记录

    /**
     * 结算一次死亡：给该记的玩家记上这一种，返回<b>总图谱</b>这次新记了的玩家（给首杀 UBMRU 用）。
     * 矩阵阶关掉时照记、不发动作栏，返回空表（不给首杀加成）。
     * {@code player/UBMRUEvents.handleDeath} 在死亡事件 LOWEST 里调（GameTest 也直接调它）。只在服务端调。
     */
    public static List<Player> record(LivingEntity victim, DamageSource source) {
        Ledger ledger = BOSS_DAMAGE.remove(victim);
        if (!countable(victim)) {
            return List.of();
        }
        boolean enabled = MatrixRank.enabled();
        List<Player> credited = new ArrayList<>();
        if (victim.getType().is(Tags.EntityTypes.BOSSES)) {
            credited.addAll(bossParticipants(victim, ledger));
        }
        if (credited.isEmpty()) {
            Player killer = handKiller(victim, source);
            if (killer != null) {
                credited.add(killer);
            }
        }
        if (credited.isEmpty()) {
            return List.of();
        }
        EntityType<?> type = victim.getType();
        boolean inHoanna = ECHoanna.isHoanna(victim.level());
        List<Player> fresh = new ArrayList<>(credited.size());
        for (Player player : credited) {
            if (add(player, type, inHoanna, !enabled) && enabled) {
                fresh.add(player);
            }
        }
        return fresh;
    }

    /**
     * 给一名玩家记上一种（指令和死亡结算共用）：没记过的记上、发增量包、动作栏提示。
     *
     * @param inHoanna 同时记进海纳尔那份
     * @return 总图谱这次新记上了
     */
    public static boolean add(Player player, EntityType<?> type, boolean inHoanna) {
        return add(player, type, inHoanna, false);
    }

    /**
     * 同 {@link #add(Player, EntityType, boolean)}；quiet = true 时不发动作栏那句（矩阵阶关掉时照记图谱用），增量包照发，客户端的图谱保持一致。
     */
    public static boolean add(Player player, EntityType<?> type, boolean inHoanna, boolean quiet) {
        IPlayerData data = PlayerDataEvents.get(player);
        String id = id(type);
        if (data == null || id.isEmpty()) {
            return false;
        }
        int max = MatrixConfig.codexMaxEntries();
        boolean newTotal = data.getMatrixCodex().size() < max && data.addMatrixCodex(id);
        boolean newHoanna = inHoanna && data.getMatrixCodexHoanna().size() < max && data.addMatrixCodexHoanna(id);
        if (!newTotal && !newHoanna) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            ECNetwork.sendTo(serverPlayer, S2CMatrixCodexPacket.added(newTotal ? id : null, newHoanna ? id : null));
            if (!quiet) {
                serverPlayer.displayClientMessage(addedMessage(data, type, newTotal), true);
            }
        }
        return newTotal;
    }

    /**
     * 动作栏那句：总图谱新记的写「矩阵图谱 +1：僵尸（9 / 13，三阶要 13）」，已经到九阶或下一阶没有数据时只写总数；
     * 只是海纳尔那份新记的写「海纳尔图谱 +1」。
     */
    public static Component addedMessage(IPlayerData data, EntityType<?> type, boolean newTotal) {
        Component name = type.getDescription().copy().withStyle(ChatFormatting.WHITE);
        if (!newTotal) {
            return Component.translatable("message.essentialcraft5.matrix_codex.added_hoanna", name, data.getMatrixCodexHoanna().size())
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        int have = data.getMatrixCodex().size();
        int next = data.getMatrixRank() + 1;
        MatrixRankData.Entry entry = next <= MatrixRank.MAX ? MatrixRankData.get(next) : null;
        int need = entry == null ? 0 : entry.codex().required(Group.TOTAL);
        if (need <= 0) {
            return Component.translatable("message.essentialcraft5.matrix_codex.added_total", name, have)
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        return Component.translatable("message.essentialcraft5.matrix_codex.added", name, have, need, MatrixRank.rankName(next))
                .withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    /** 清空一名玩家的两份图谱（指令），发整份包；返回清掉了几条 */
    public static int clear(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            return 0;
        }
        int removed = data.clearMatrixCodex();
        resend(player);
        return removed;
    }

    /** 把整份图谱重发给本人（指令改过以后） */
    public static void resend(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data != null && player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            ECNetwork.sendTo(serverPlayer, S2CMatrixCodexPacket.full(data));
        }
    }

    // ------------------------------------------------------------------ 首领伤害

    /** 首领本场的参战者：打出不少于最大生命 {@link #BOSS_SHARE} 有效伤害的真玩家（在线的那个对象优先，下线又上线的照样认） */
    public static List<Player> bossParticipants(LivingEntity boss, @Nullable Ledger ledger) {
        if (ledger == null) {
            return List.of();
        }
        float threshold = boss.getMaxHealth() * BOSS_SHARE;
        List<Player> players = new ArrayList<>();
        ledger.dealt.forEach((id, dealt) -> {
            if (dealt + 1.0E-4F < threshold) {
                return;
            }
            Player player = boss.getServer() == null ? null : boss.getServer().getPlayerList().getPlayer(id);
            if (player == null) {
                WeakReference<Player> ref = ledger.who.get(id);
                player = ref == null ? null : ref.get();
            }
            if (player != null && !player.isRemoved()) {
                players.add(player);
            }
        });
        return players;
    }

    /** 记一下首领挨的有效伤害，没吃伤害吸收（GameTest 直接调） */
    public static void noteBossDamage(LivingEntity boss, DamageSource source, float amount) {
        noteBossDamage(boss, source, amount, 0F);
    }

    /**
     * 记一下首领挨的有效伤害（{@link MatrixCodexEvents#matrixCodexBossDamage} 调）：扣到生命上的（不超过它剩下的生命）加上打掉的伤害吸收，
     * 「打实」照 {@link ECLandedHit#landed}，整下被伤害吸收吃掉的一下也算参战。
     *
     * @param absorbed 这一下打掉的伤害吸收（{@link ECLandedHit#absorbed}）
     */
    public static void noteBossDamage(LivingEntity boss, DamageSource source, float amount, float absorbed) {
        float dealt = Math.max(0F, amount);
        float eaten = absorbed > 0F && Float.isFinite(absorbed) ? absorbed : 0F;
        if (!Float.isFinite(dealt) || !(dealt > 0F || eaten > 0F) || !boss.getType().is(Tags.EntityTypes.BOSSES)) {
            return;
        }
        if (!(source.getEntity() instanceof Player player) || player instanceof FakePlayer) {
            return;
        }
        float effective = Math.min(dealt, Math.max(0F, boss.getHealth())) + eaten;
        // 全息投影（含哨兵）的单次封顶在 HologramEvents 里，和这里同是 LivingDamageEvent LOWEST，谁先跑看注册顺序；
        // 这里自己再夹一次，免得别的模组乘过的一大下在封顶之前就被记成参战份额
        if (boss instanceof HologramEntity hologram && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            float cap = hologram.damageCap();
            if (cap > 0F) {
                effective = Math.min(effective, cap);
            }
        }
        if (effective <= 0F) {
            return;
        }
        Ledger ledger = BOSS_DAMAGE.computeIfAbsent(boss, key -> new Ledger());
        ledger.dealt.merge(player.getUUID(), effective, Float::sum);
        ledger.who.put(player.getUUID(), new WeakReference<>(player));
    }

    /** GameTest 用：这只首领本场记下的伤害（UUID → 有效伤害，没有返回空表） */
    public static Map<UUID, Float> bossDamage(LivingEntity boss) {
        Ledger ledger = BOSS_DAMAGE.get(boss);
        return ledger == null ? Map.of() : Map.copyOf(ledger.dealt);
    }

    /** 首领本场的伤害记录（只在服务端主线程用） */
    public static final class Ledger {
        private final Map<UUID, Float> dealt = new HashMap<>();
        private final Map<UUID, WeakReference<Player>> who = new HashMap<>();
    }

    // ------------------------------------------------------------------ 指令、显示用

    /** 图谱按记下的先后排的实体名（指令 list 用） */
    public static List<String> describe(Collection<String> ids) {
        List<String> out = new ArrayList<>(ids.size());
        for (String raw : ids) {
            EntityType<?> type = type(raw);
            out.add(type == null ? raw + "?" : raw);
        }
        return out;
    }

    /** 各组种类数写成「total=21 nether=4 …」（日志、指令用） */
    public static String summary(Counts counts) {
        Map<String, Integer> ordered = new LinkedHashMap<>();
        for (Group group : Group.values()) {
            ordered.put(group.key(), counts.get(group));
        }
        StringBuilder builder = new StringBuilder();
        ordered.forEach((key, value) -> builder.append(builder.isEmpty() ? "" : " ").append(key).append('=').append(value));
        return builder.toString();
    }

    // ------------------------------------------------------------------ 事件

    /** Forge 总线（类名带模块前缀，见 tools/check_event_handlers.py）。死亡结算在 player/UBMRUEvents 里调 {@link #record} */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class MatrixCodexEvents {

        private MatrixCodexEvents() {
        }

        /** 首领挨的有效伤害：LOWEST、不收已取消的、打实了才记（{@link ECLandedHit#landed}；混合端插件拦下的、护盾整下吃掉的不算） */
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void matrixCodexBossDamage(LivingDamageEvent event) {
            LivingEntity boss = event.getEntity();
            if (!boss.level().isClientSide && boss.getType().is(Tags.EntityTypes.BOSSES) && ECLandedHit.landed(event)) {
                float amount = event.getAmount();
                // 本模组首领的单次封顶 / 每秒桶和这里同在 LOWEST，谁先跑看注册顺序：按同一个事件的结算数记（只算一次、只扣一次桶）
                if (boss instanceof ECBoss) {
                    amount = Math.min(amount, BossDamageBudget.resolve(event));
                }
                noteBossDamage(boss, event.getSource(), amount, ECLandedHit.absorbed(event));
            }
        }

        @SubscribeEvent
        public static void matrixCodexServerStopped(ServerStoppedEvent event) {
            BOSS_DAMAGE.clear();
        }
    }
}
