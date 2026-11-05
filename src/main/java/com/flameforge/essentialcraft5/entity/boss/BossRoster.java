package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.registry.ECBossFramework;
import com.flameforge.essentialcraft5.teleport.ECTeleport;
import com.flameforge.essentialcraft5.util.ECLevels;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 首领的参战名单与有效伤害账（第十三批 13c-Fb，batch13.md §5.3「共同」、policy §4.18「首领通用件」）。
 * 照全息投影的名单另写一份（全息投影、哨兵的代码不动），做成组件：首领（{@link ECBoss}）持有一份，服务端每刻调 {@link #tick}。
 *
 * <h2>谁在名单里</h2>
 * 召唤者 / 触发者（首领自己调 {@link #add}），加上对它打实了一下（{@code ECLandedHit.landed}）的生存 / 冒险真玩家（{@link BossDamageBudget} 的
 * {@code LivingDamageEvent} LOWEST 监听器调 {@link #noteDamage}）。旁观的、创造模式的、假玩家不进名单、不被技能打、不被拉回、不锁飞行；
 * 混合端插件拦下的攻击不发 {@code LivingDamageEvent}，也就不进名单。
 *
 * <h2>每 {@value #CHECK_INTERVAL} 刻查一次（{@link #check}）</h2>
 * 只遍历名单（按 UUID 从在线玩家里取）。距离从场地中心量（{@link ECBoss#arenaFloor} 的顶面中心；没有场地时从首领自己量）：
 * <ul>
 *   <li>下线、死了、换了维度、改成创造 / 旁观、离场地超过 {@code escapeRange}（{@link ECBoss#beyondEscape}，默认直线距离）→ 脱战
 *       （移出名单，不拽、不扣血；离太远的动作栏说一句，只对之前某次检查时在范围里的人说：在范围外打实一下被加进来的，下次检查静默移出）。
 *       死亡另由 {@link BossRules} 在 {@code LivingDeathEvent} 里当场移出（{@link #forgetFighter}）：开了立即重生时从死到重生只隔 1~3 刻，
 *       10 刻一次的检查看到的往往已经是重生出来的新对象；</li>
 *   <li>{@code pullRange}~{@code escapeRange} 格 → 拽回场地地面：{@code ECTeleport.check(被拉的人, …)} 先看落点安全、再按<b>被拉的这个人</b>探测落点的权限
 *       （场地是召唤者布置的，可能是他领地里一间封死的屋子；在那里不能动的人不拽进去，不困人，policy §1.1），再 {@code ECTeleport.teleport}
 *       发可取消的传送事件（混合端插件拦得住）。传成了扣 {@code pull_damage}（{@code mobAttack(首领)}）、放拽回音效、动作栏说一句；
 *       落点不安全、没有权限（含这一刻探测不出结论）或传送被取消时<b>不拉、不扣血，人留在名单里</b>（batch13.md §12.4 C65），
 *       但这样拉不回来的时间连着累计，满 {@code general.mobs.boss.abandon_ticks}（默认 600 刻）按脱战移出：堵住拉回点换不来免费脱战，
 *       也不能借它无限期挂着一场战斗（和跑出 {@code escapeRange} 一个价）。回到 {@code pullRange} 以内或被拽回来就清零。{@code pullRange} 为 0 的首领不拉；</li>
 *   <li>开了飞行压制的：名单里的人每次检查关一次飞行并同步，本模组给的飞行由 {@code ECFlight} 的压制器暂停（{@link #isEngaged}）；</li>
 *   <li>移出名单（以上各种、死亡）时调首领的 {@link ECBoss#onFighterLeft}，只发给名单的整屏画面在那里收掉；</li>
 *   <li>血条只给名单里在线、同世界的人看（「按名单显示」）：路过的人看不到别人的首领战。</li>
 * </ul>
 * 和平难度：每刻都查，交给 {@link ECBoss#endBoss}（{@link ECBoss.BossEnd#PEACEFUL}）。开了「散去」的首领名单空了每刻累计，到
 * {@code general.mobs.boss.abandon_ticks}（默认 600）交给 {@link ECBoss#endBoss}（{@link ECBoss.BossEnd#ABANDONED}）；有人进名单就清零。
 * 区块没加载时实体不 tick，计时也不走。
 *
 * <h2>有效伤害账</h2>
 * 每一下打实的有效伤害（扣到生命上的、不超过它剩下的生命，加上打掉的伤害吸收；封顶以后的数）记两份：所有来源的合计 {@link #totalDealt}，
 * 和每名生存 / 冒险真玩家各自的 {@link #dealt}（最多记 {@value #MAX_LEDGER} 人，存盘）。分掉落（{@link BossLoot}）按后者对最大生命的比例；
 * {@link #playerShare} 是这份账里真玩家的占比（宠物不算）；{@code fair_kill} 读的是 {@code MobDamageShare}（宠物算主人，首领按同一个
 * 结算数记，和这里一致）。下线、脱战不清账：打过的那份伤害照样算。
 * <p>
 * 只在服务端主线程读写。存读档：名单、伤害账、放弃计时、拉不回来的累计；读档不叠加（{@link #load} 先清空再读）。
 */
public final class BossRoster {

    /** 名单检查间隔（刻；同全息投影） */
    public static final int CHECK_INTERVAL = 10;
    /** 没有场地时，拉回落点从首领脚下往下找几格 */
    public static final int PULL_FLOOR_SEARCH = 8;
    /** 伤害账最多记几名玩家（防存档被刷大） */
    public static final int MAX_LEDGER = 64;
    /** 浮点比较的余量 */
    private static final float EPS = 1.0E-4F;

    private static final String TAG_PARTICIPANTS = "participants";
    private static final String TAG_DEALT = "dealt";
    private static final String TAG_ID = "id";
    private static final String TAG_AMOUNT = "amount";
    private static final String TAG_TOTAL = "total_dealt";
    private static final String TAG_ABANDON = "abandon_ticks";
    private static final String TAG_STRANDED = "stranded";
    private static final String TAG_TICKS = "ticks";

    /**
     * 名单的距离与开关。
     *
     * @param engageRange    Boss 档：离首领多远以内的真玩家打得动它（创造模式不限；小 Boss 不用这一项）
     * @param pullRange      离场地超过这么远就拽回（0 = 不拽；拽不回来的连着累计，满 abandon_ticks 脱战）
     * @param escapeRange    离场地超过这么远就脱战
     * @param abandons       名单空了到点散去（结构守卫这类关掉）
     * @param suppressFlight 参战者不许飞
     */
    public record Settings(double engageRange, double pullRange, double escapeRange, boolean abandons, boolean suppressFlight) {

        public Settings {
            engageRange = finiteNonNegative(engageRange);
            pullRange = finiteNonNegative(pullRange);
            escapeRange = Math.max(finiteNonNegative(escapeRange), pullRange);
        }

        /**
         * 召唤出来、有场地的 Boss：在 pullRange 内打得动它，pullRange~escapeRange 拽回，名单空了散去，压制飞行。
         * pullRange 填 0（不拽）时打得动它的距离按 escapeRange 算，不会变成谁都打不动。
         */
        public static Settings arena(double pullRange, double escapeRange) {
            double pull = finiteNonNegative(pullRange);
            return new Settings(pull > 0D ? pull : escapeRange, pull, escapeRange, true, true);
        }

        /** 召唤者离场地超过这么远算「离开了」：拉回距离；不拽的首领按脱战距离 */
        public double awayRange() {
            return pullRange > 0D ? pullRange : escapeRange;
        }

        /** 守着一个地方的小 Boss：不拽、不散去、不压飞行，离开 escapeRange 就脱战 */
        public static Settings guardian(double escapeRange) {
            return new Settings(escapeRange, 0D, escapeRange, false, false);
        }

        private static double finiteNonNegative(double value) {
            return Double.isFinite(value) && value > 0D ? value : 0D;
        }
    }

    /** 服务端在世界里的名单（飞行压制器、死亡移出用；只有 attach 过的进来） */
    private static final Set<BossRoster> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());

    private final ECBoss boss;
    private final ServerBossEvent bar;
    private final Set<UUID> participants = new LinkedHashSet<>();
    /** 名单检查时人在脱战距离以内的（不存盘）：只有他们离开时才说「退出了战斗」 */
    private final Set<UUID> seenInside = new HashSet<>();
    private final Map<UUID, Float> dealt = new LinkedHashMap<>();
    /** 在拉回距离以外、拉不回来的连续刻数（按名单检查累计） */
    private final Map<UUID, Integer> stranded = new LinkedHashMap<>();
    private float totalDealt;
    private int abandonTicks;
    private int clock;
    private boolean ended;

    public BossRoster(ECBoss boss, BossEvent.BossBarColor color) {
        this.boss = boss;
        this.bar = new ServerBossEvent(boss.bossEntity().getDisplayName(), color, BossEvent.BossBarOverlay.PROGRESS);
    }

    // ------------------------------------------------------------------
    // 名单
    // ------------------------------------------------------------------

    /** 把这名玩家加进名单（召唤者、触发者；打实了的由 {@link #noteDamage} 加）。只收生存 / 冒险的真玩家；加上了返回 true */
    public boolean add(ServerPlayer player) {
        if (!ECProtection.isRealPlayer(player) || !BossRules.fighting(player)) {
            return false;
        }
        participants.add(player.getUUID());
        abandonTicks = 0;
        return true;
    }

    public void remove(UUID player) {
        LivingEntity owner = boss.bossEntity();
        drop(player, owner.level() instanceof ServerLevel server ? ECProtection.onlinePlayer(server.getServer(), player) : null);
    }

    /** 移出名单（连同拉不回来的累计），告诉首领（{@link ECBoss#onFighterLeft}）；本来就不在名单里时什么都不做 */
    private void drop(UUID player, @Nullable ServerPlayer online) {
        stranded.remove(player);
        if (participants.remove(player)) {
            boss.onFighterLeft(player, online);
        }
    }

    public boolean contains(UUID player) {
        return participants.contains(player);
    }

    /** 名单（只读，按进名单的先后） */
    public Set<UUID> participants() {
        return Collections.unmodifiableSet(participants);
    }

    /** 名单里此刻在线、活着、和首领同一个世界、生存 / 冒险的人（技能、整屏画面、发包都只对他们） */
    public List<ServerPlayer> online() {
        LivingEntity owner = boss.bossEntity();
        if (!(owner.level() instanceof ServerLevel server) || participants.isEmpty()) {
            return List.of();
        }
        List<ServerPlayer> out = new ArrayList<>(participants.size());
        for (UUID id : participants) {
            ServerPlayer player = ECProtection.onlinePlayer(server.getServer(), id);
            if (player != null && player.isAlive() && !player.isRemoved() && player.level() == server && BossRules.fighting(player)) {
                out.add(player);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 有效伤害账
    // ------------------------------------------------------------------

    /**
     * 记一下打实的有效伤害（{@link BossDamageBudget} 的 LOWEST 监听器调；GameTest 也直接调）：所有来源都进合计；
     * 攻击者是生存 / 冒险真玩家的，进他自己那份并加进名单。effective 不是正的有限数就不记。
     */
    public void noteDamage(DamageSource source, float effective) {
        if (!(effective > 0F) || !Float.isFinite(effective)) {
            return;
        }
        totalDealt += effective;
        if (source.getEntity() instanceof ServerPlayer player && ECProtection.isRealPlayer(player) && BossRules.fighting(player)) {
            UUID id = player.getUUID();
            if (dealt.containsKey(id) || dealt.size() < MAX_LEDGER) {
                dealt.merge(id, effective, Float::sum);
            }
            add(player);
        }
    }

    /** 这名玩家打出的有效伤害（没打过是 0） */
    public float dealt(UUID player) {
        Float value = dealt.get(player);
        return value == null ? 0F : value;
    }

    /** 伤害账（只读，按第一次打实的先后） */
    public Map<UUID, Float> ledger() {
        return Collections.unmodifiableMap(dealt);
    }

    /** 所有来源的有效伤害合计 */
    public float totalDealt() {
        return totalDealt;
    }

    /** 真玩家（生存 / 冒险）打出的有效伤害合计 */
    public float playerDealt() {
        float sum = 0F;
        for (float value : dealt.values()) {
            sum += value;
        }
        return sum;
    }

    /** 生存 / 冒险真玩家打出的有效伤害占所有来源的比例（没挨过打是 0；公平击杀不读它，读 {@code MobDamageShare}） */
    public float playerShare() {
        return totalDealt > 0F ? Math.min(1F, playerDealt() / totalDealt) : 0F;
    }

    /**
     * 打出不少于最大生命 × share 有效伤害的玩家（按伤害从多到少；同样多的按先后）。share 不是 [0, 1] 里的数时按 [0, 1] 夹。
     */
    public List<UUID> qualifiers(double share) {
        double clamped = Double.isFinite(share) ? Math.max(0D, Math.min(1D, share)) : 0D;
        float threshold = (float) (boss.bossEntity().getMaxHealth() * clamped);
        List<Map.Entry<UUID, Float>> entries = new ArrayList<>(dealt.entrySet());
        entries.removeIf(entry -> entry.getValue() + EPS < threshold || !(entry.getValue() > 0F));
        entries.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));
        List<UUID> out = new ArrayList<>(entries.size());
        for (Map.Entry<UUID, Float> entry : entries) {
            out.add(entry.getKey());
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 每刻
    // ------------------------------------------------------------------

    /**
     * 服务端每刻调一次（{@link ECBossMonster#customServerAiStep}）：和平难度结束、更新血条进度、每 {@value #CHECK_INTERVAL} 刻 {@link #check}、
     * 名单空了累计放弃时间。首领死了、被移除、已经结束过就什么都不做。
     */
    public void tick() {
        LivingEntity owner = boss.bossEntity();
        if (ended || !(owner.level() instanceof ServerLevel server) || owner.isRemoved() || owner.isDeadOrDying()) {
            return;
        }
        if (server.getDifficulty() == Difficulty.PEACEFUL) {
            end(ECBoss.BossEnd.PEACEFUL);
            return;
        }
        bar.setProgress(Math.max(0F, Math.min(1F, owner.getHealth() / Math.max(1.0E-3F, owner.getMaxHealth()))));
        if (++clock >= CHECK_INTERVAL) {
            clock = 0;
            check(server);
        }
        if (!boss.rosterSettings().abandons()) {
            abandonTicks = 0;
            return;
        }
        if (participants.isEmpty()) {
            if (++abandonTicks >= ECBossFramework.Config.values().abandonTicks()) {
                end(ECBoss.BossEnd.ABANDONED);
            }
        } else {
            abandonTicks = 0;
        }
    }

    private void end(ECBoss.BossEnd reason) {
        ended = true;
        bar.removeAllPlayers();
        boss.endBoss(reason);
    }

    /** 名单检查一次（见类注释；GameTest 直接调）。只在服务端调 */
    public void check(ServerLevel server) {
        Settings settings = boss.rosterSettings();
        Vec3 center = center();
        seenInside.retainAll(participants);
        for (UUID id : List.copyOf(participants)) {
            ServerPlayer player = ECProtection.onlinePlayer(server.getServer(), id);
            if (player == null || !player.isAlive() || player.isRemoved() || player.level() != server || !BossRules.fighting(player)) {
                seenInside.remove(id);
                drop(id, player);
                continue;
            }
            double distance = player.position().distanceTo(center);
            if (boss.beyondEscape(player, center, settings.escapeRange())) {
                // 进名单时就在范围外的（远处放箭、打实一下被加进来的）静默移出，只对在范围里待过的人说「退出了战斗」
                boolean wasInside = seenInside.remove(id);
                drop(id, player);
                if (wasInside) {
                    player.displayClientMessage(Component.translatable("message.essentialcraft5.boss.left_fight"), true);
                }
                continue;
            }
            seenInside.add(id);
            if (settings.pullRange() > 0D && distance > settings.pullRange()) {
                if (pullBack(server, player)) {
                    stranded.remove(id);
                } else if (stranded.merge(id, CHECK_INTERVAL, Integer::sum) >= ECBossFramework.Config.values().abandonTicks()) {
                    drop(id, player);
                    player.displayClientMessage(Component.translatable("message.essentialcraft5.boss.left_fight_stranded"), true);
                    continue;
                }
            } else {
                stranded.remove(id);
            }
            if (settings.suppressFlight() && player.getAbilities().flying) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }
        syncBar(server);
    }

    /**
     * 把一名参战者拽回场地地面（见类注释）：落点安全、他在落点能动、传送没被取消才算拽回来了（扣血、放音效、动作栏）；
     * 否则返回 false，什么都不做，人留在名单里。权限按被拉的人探测（缓存 + 每刻预算，预算按这只首领算）；探测不出结论（PENDING）这一轮也不拉。
     */
    public boolean pullBack(ServerLevel server, ServerPlayer player) {
        BlockPos floor = pullFloor(server);
        if (floor == null) {
            return false;
        }
        LivingEntity owner = boss.bossEntity();
        ECTeleport.Check check = ECTeleport.check(player, server, floor, owner);
        Vec3 landing = check.landing();
        if (!check.ok() || landing == null) {
            return false;
        }
        if (!ECTeleport.teleport(player, server, landing, player.getYRot(), player.getXRot())) {
            return false;
        }
        player.resetFallDistance();
        float damage = (float) ECBossFramework.Config.values().pullDamage();
        if (damage > 0F) {
            player.hurt(owner.damageSources().mobAttack(owner), damage);
        }
        server.playSound(null, landing.x, landing.y, landing.z, ECBossFramework.SOUND_PULL.get(), SoundSource.HOSTILE, 1F, 1F);
        player.displayClientMessage(Component.translatable("message.essentialcraft5.boss.pulled_back"), true);
        return true;
    }

    /**
     * 拉回落点的「脚下那格」（{@code ECTeleport.check} 要的就是它）：有场地时是 {@link ECBoss#arenaFloor}（区块没加载返回 null）；
     * 没有时从首领脚下往下 {@value #PULL_FLOOR_SEARCH} 格内第一个有碰撞的方块；找不到返回 null（不拉）
     */
    @Nullable
    public BlockPos pullFloor(ServerLevel server) {
        BlockPos arena = boss.arenaFloor();
        if (arena != null) {
            return ECLevels.isFullyLoaded(server, arena) ? arena : null;
        }
        BlockPos start = boss.bossEntity().blockPosition();
        for (int i = 0; i <= PULL_FLOOR_SEARCH; i++) {
            BlockPos pos = start.below(i);
            if (!ECLevels.isFullyLoaded(server, pos)) {
                return null;
            }
            if (!server.getBlockState(pos).getCollisionShape(server, pos, CollisionContext.empty()).isEmpty()) {
                return pos;
            }
        }
        return null;
    }

    /** 量拉回 / 脱战距离的中心：场地地面的顶面中心，没有场地时是首领自己的位置 */
    public Vec3 center() {
        BlockPos arena = boss.arenaFloor();
        return arena != null ? Vec3.atBottomCenterOf(arena.above()) : boss.bossEntity().position();
    }

    /** 血条只给名单里在线、同世界的人 */
    private void syncBar(ServerLevel server) {
        Set<ServerPlayer> want = new HashSet<>(online());
        for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
            if (!want.remove(player)) {
                bar.removePlayer(player);
            }
        }
        for (ServerPlayer player : want) {
            bar.addPlayer(player);
        }
    }

    // ------------------------------------------------------------------
    // 世界登记、血条
    // ------------------------------------------------------------------

    /** 首领加进世界时调（服务端才登记） */
    public void attach() {
        if (!boss.bossEntity().level().isClientSide) {
            ACTIVE.add(this);
        }
    }

    /** 首领移出世界时调：不再压制飞行、血条撤掉 */
    public void detach() {
        ACTIVE.remove(this);
        bar.removeAllPlayers();
    }

    /** 首领的血条（名字变了由首领调 {@code bar().setName}） */
    public ServerBossEvent bar() {
        return bar;
    }

    /** 已经交给过 {@link ECBoss#endBoss}（之后 tick 什么都不做） */
    public boolean ended() {
        return ended;
    }

    public int abandonTicks() {
        return abandonTicks;
    }

    /** 这名参战者在拉回距离以外、连着拉不回来多少刻了（没有就是 0） */
    public int strandedTicks(UUID player) {
        return stranded.getOrDefault(player, 0);
    }

    /**
     * ECFlight 的压制器：这名玩家在某只首领的名单里（同一个世界、首领还活着、这只首领开了飞行压制）就暂停本模组给的飞行。
     */
    public static boolean isEngaged(ServerPlayer player) {
        if (ACTIVE.isEmpty()) {
            return false;
        }
        UUID id = player.getUUID();
        for (BossRoster roster : List.copyOf(ACTIVE)) {
            LivingEntity owner = roster.boss.bossEntity();
            if (owner.isAlive() && !owner.isRemoved() && owner.level() == player.level() && roster.participants.contains(id)
                    && roster.boss.rosterSettings().suppressFlight()) {
                return true;
            }
        }
        return false;
    }

    /** 这名玩家死了：从所有首领的名单里移出（伤害账不动） */
    public static void forgetFighter(UUID player) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        for (BossRoster roster : List.copyOf(ACTIVE)) {
            if (roster.participants.contains(player)) {
                roster.remove(player);
            }
        }
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    public void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (UUID id : participants) {
            list.add(NbtUtils.createUUID(id));
        }
        tag.put(TAG_PARTICIPANTS, list);
        ListTag ledger = new ListTag();
        for (Map.Entry<UUID, Float> entry : dealt.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putUUID(TAG_ID, entry.getKey());
            row.putFloat(TAG_AMOUNT, entry.getValue());
            ledger.add(row);
        }
        tag.put(TAG_DEALT, ledger);
        tag.putFloat(TAG_TOTAL, totalDealt);
        tag.putInt(TAG_ABANDON, abandonTicks);
        ListTag strandedList = new ListTag();
        for (Map.Entry<UUID, Integer> entry : stranded.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putUUID(TAG_ID, entry.getKey());
            row.putInt(TAG_TICKS, entry.getValue());
            strandedList.add(row);
        }
        tag.put(TAG_STRANDED, strandedList);
    }

    /** 读档：先清空再读（同一只读两次不会叠加）；坏条目跳过 */
    public void load(CompoundTag tag) {
        participants.clear();
        for (Tag entry : tag.getList(TAG_PARTICIPANTS, Tag.TAG_INT_ARRAY)) {
            try {
                participants.add(NbtUtils.loadUUID(entry));
            } catch (IllegalArgumentException ignored) {
                // 坏掉的 UUID 跳过
            }
        }
        dealt.clear();
        for (Tag entry : tag.getList(TAG_DEALT, Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) entry;
            float amount = row.getFloat(TAG_AMOUNT);
            if (row.hasUUID(TAG_ID) && amount > 0F && Float.isFinite(amount) && dealt.size() < MAX_LEDGER) {
                dealt.put(row.getUUID(TAG_ID), amount);
            }
        }
        float total = tag.getFloat(TAG_TOTAL);
        totalDealt = Float.isFinite(total) && total > 0F ? Math.max(total, playerDealt()) : playerDealt();
        abandonTicks = Math.max(0, tag.getInt(TAG_ABANDON));
        stranded.clear();
        for (Tag entry : tag.getList(TAG_STRANDED, Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) entry;
            int ticks = row.getInt(TAG_TICKS);
            if (row.hasUUID(TAG_ID) && ticks > 0 && participants.contains(row.getUUID(TAG_ID))) {
                stranded.put(row.getUUID(TAG_ID), ticks);
            }
        }
    }
}
