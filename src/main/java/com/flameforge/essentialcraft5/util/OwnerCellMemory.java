package com.flameforge.essentialcraft5.util;

import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * 替主人对「某一格里的生物」下手之前的那道门：这一格主人自己能不能动（领地）。特斯拉线圈打怪、恶魔清怪用它（第十批权限修补 A6），
 * 线圈打闯入者时也要过它（A1：闯入者站的那格得是线圈主人能动的，射程伸进第三个人的领地、公共保护区时那里的人不打）。每台线圈、每只恶魔各带一个实例。
 * <p>
 * 为什么要有：线圈（半径最多 32）、恶魔（12）的射程会伸进邻居的领地，把邻居刷怪塔里的怪清掉（被它们打死的还不掉东西）。
 * 伤害不带攻击者（线圈）或攻击者是恶魔，PvP / 领地插件都管不到，只能由本模组自己问：以主人的身份对那一格发一次探测
 * （{@link ECProtection#check}，走它的缓存和每刻预算），能动才打。
 * <p>
 * <b>少探测</b>：混合端上每次探测都是一次真的破坏事件，领地插件被拒时会给主人发一句「没有权限」（人在别处、别的维度也收得到），
 * 能动的那次也会被 AFK、挖掘统计之类的插件当成主人挖了一格。所以探测的结论两种都记下来，记着的时候不再探测：
 * <ul>
 *   <li>不能动的格子记 {@value #DENY_TICKS} 刻（5 分钟），这段时间里主人在不在线都按不能动算；</li>
 *   <li>能动的格子主人在线时 {@value #RECHECK_TICKS} 刻（5 分钟）内直接用记录，再早的重新探测一次；主人离线时认 {@value #REMEMBER_TICKS} 刻（1 小时）。</li>
 * </ul>
 * 只往「先不打」的方向用：没记过、记录过期的格子主人在线才探测，主人离线时<b>不打</b>（返回 {@link ECProtection.Verdict#UNKNOWN}）。
 * 记录只在内存里（机器 / 实体卸载、重启就没了），每张表最多 {@value #MAX_CELLS} 格（满了先删过期的，还满就删掉最早记下的那一半）；
 * 主人换了人、换了维度两张表一起作废。
 * 服主关掉 {@code general.protection.require_owner_online}（没装领地插件的服）时主人离线放行（记着不能动的格子除外），和 {@link ECProtection#checkAsOwner} 一致。
 * <p>
 * 用法：{@link #peek} 只看记录（不探测、不花预算），{@link #check} 记录给不出结论、主人又在线时才探测。一次要问很多格的调用方
 * （线圈一次放电、恶魔一次清怪）先 {@code peek}，已知不能动的直接跳过，再自己限制每次新探测几格。
 * <p>
 * 主人认法：机器用 {@link ECProtection#onlineOwner}（单人里没记主人、或者没开局域网时记下的主人不在线，算房主的）；
 * 按 UUID 记主人的（恶魔的召唤者）用 {@link ECProtection#onlineActing}，同一套规则。多人服里没有主人的一律 {@link ECProtection.Verdict#DENY}。
 * <p>
 * 只在服务端线程上用。
 */
public final class OwnerCellMemory {

    /** 主人在线时探测「能动」的格子，主人离线后还认多久（1 小时） */
    public static final int REMEMBER_TICKS = 72_000;
    /** 主人在线时，「能动」的记录多久以内直接用、不再探测（5 分钟） */
    public static final int RECHECK_TICKS = 6_000;
    /** 探测「不能动」的格子记多久（5 分钟）：这段时间里不再以主人身份探测它，主人在不在线都按不能动算 */
    public static final int DENY_TICKS = 6_000;
    /** 每张表最多记多少格：满了先删过期的，还满就删掉最早记下的那一半 */
    public static final int MAX_CELLS = 4096;

    /** 格子（{@link BlockPos#asLong}）→ 最近一次探测成「能动」的那一刻（服务器刻） */
    private final Long2IntOpenHashMap allowedAt = new Long2IntOpenHashMap();
    /** 格子 → 最近一次探测成「不能动」的那一刻 */
    private final Long2IntOpenHashMap deniedAt = new Long2IntOpenHashMap();
    @Nullable
    private ResourceKey<Level> dimension;
    @Nullable
    private UUID memoryOwner;

    /**
     * 机器替主人对 pos 里的生物下手前问一句：记录给得出结论就用记录；给不出时主人在线就用主人真身探测（预算按机器算），结论记下来；
     * 主人不在线 {@link ECProtection.Verdict#UNKNOWN}；没有主人 {@link ECProtection.Verdict#DENY}。只有 {@link ECProtection.Verdict#ALLOW} 才下手。
     */
    public ECProtection.Verdict check(MRUBlockEntity machine, BlockPos pos) {
        if (!(machine.getLevel() instanceof ServerLevel level)) {
            return ECProtection.Verdict.DENY;
        }
        return resolve(level, ECProtection.onlineOwner(machine), machine.getOwner(), pos, machine, true);
    }

    /**
     * 同上，主人按 UUID 记（恶魔的召唤者）。{@code caller} 分探测预算（一般传实体自己）。
     */
    public ECProtection.Verdict check(Level level, @Nullable UUID owner, BlockPos pos, Object caller) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return ECProtection.Verdict.DENY;
        }
        return resolve(serverLevel, ECProtection.onlineActing(serverLevel.getServer(), owner), owner, pos, caller, true);
    }

    /**
     * 只看记录、不探测、不花预算：{@link #check} 不探测时会给的结论。{@link ECProtection.Verdict#UNKNOWN} = 主人在线时要探测一次才知道，
     * 主人离线时就是不打。
     */
    public ECProtection.Verdict peek(MRUBlockEntity machine, BlockPos pos) {
        if (!(machine.getLevel() instanceof ServerLevel level)) {
            return ECProtection.Verdict.DENY;
        }
        return resolve(level, ECProtection.onlineOwner(machine), machine.getOwner(), pos, null, false);
    }

    /** 同上，主人按 UUID 记（恶魔的召唤者） */
    public ECProtection.Verdict peek(Level level, @Nullable UUID owner, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return ECProtection.Verdict.DENY;
        }
        return resolve(serverLevel, ECProtection.onlineActing(serverLevel.getServer(), owner), owner, pos, null, false);
    }

    /**
     * @param online   在线、实际替谁干活的玩家（单人里可能是房主）；null = 不在线
     * @param recorded 记下的主人；online 和它都是 null = 没有主人
     * @param probe    记录给不出结论、主人在线时要不要探测
     */
    private ECProtection.Verdict resolve(ServerLevel level, @Nullable ServerPlayer online, @Nullable UUID recorded, BlockPos pos,
                                         @Nullable Object caller, boolean probe) {
        if (online == null && recorded == null) {
            return ECProtection.Verdict.DENY;
        }
        UUID owner = online != null ? online.getUUID() : recorded;
        long key = pos.asLong();
        int now = now(level);
        boolean scoped = level.dimension() == dimension && owner.equals(memoryOwner);
        if (scoped && fresh(deniedAt, key, now, DENY_TICKS)) {
            return ECProtection.Verdict.DENY;
        }
        if (online == null) {
            if (!ECProtection.Config.requireOwnerOnline()) {
                return ECProtection.Verdict.ALLOW;
            }
            return scoped && fresh(allowedAt, key, now, REMEMBER_TICKS) ? ECProtection.Verdict.ALLOW : ECProtection.Verdict.UNKNOWN;
        }
        if (scoped && fresh(allowedAt, key, now, RECHECK_TICKS)) {
            return ECProtection.Verdict.ALLOW;
        }
        return probe ? probe(online, level, pos, caller) : ECProtection.Verdict.UNKNOWN;
    }

    private ECProtection.Verdict probe(ServerPlayer player, ServerLevel level, BlockPos pos, @Nullable Object caller) {
        ECProtection.Verdict verdict = ECProtection.check(player, level, pos, caller);
        if (verdict != ECProtection.Verdict.UNKNOWN) {
            record(level, pos, player.getUUID(), verdict == ECProtection.Verdict.ALLOW);
        }
        return verdict;
    }

    /** 记一格的结论（换了主人、换了维度先把两张表清掉）；同一格只留一种结论 */
    private void record(ServerLevel level, BlockPos pos, UUID owner, boolean allowed) {
        if (level.dimension() != dimension || !Objects.equals(owner, memoryOwner)) {
            allowedAt.clear();
            deniedAt.clear();
            dimension = level.dimension();
            memoryOwner = owner;
        }
        long key = pos.asLong();
        int now = now(level);
        if (allowed) {
            deniedAt.remove(key);
            put(allowedAt, key, now, REMEMBER_TICKS);
        } else {
            allowedAt.remove(key);
            put(deniedAt, key, now, DENY_TICKS);
        }
    }

    private static boolean fresh(Long2IntOpenHashMap table, long key, int now, int lifetime) {
        if (!table.containsKey(key)) {
            return false;
        }
        int age = now - table.get(key);
        return age >= 0 && age < lifetime;
    }

    /**
     * 满了（这格又不在表里）：先删过期的（含比现在还晚的：单人换存档时服务器刻从 0 重新数），还满就删掉最早记下的那一半——
     * 不整份清空，否则射程大、怪多的地方一小时里攒满一次，主人刚好这时下线，离线时就只剩清空以后这几分钟记下的格子。
     */
    private static void put(Long2IntOpenHashMap table, long key, int now, int lifetime) {
        if (table.size() >= MAX_CELLS && !table.containsKey(key)) {
            table.long2IntEntrySet().removeIf(entry -> {
                int age = now - entry.getIntValue();
                return age < 0 || age >= lifetime;
            });
            if (table.size() >= MAX_CELLS) {
                int[] ages = new int[table.size()];
                int i = 0;
                for (int at : table.values()) {
                    ages[i++] = now - at;
                }
                Arrays.sort(ages);
                // 年龄从小到大：年龄 ≥ 中位数的删掉，留下最新的一半；最新的那一刻就占了一半以上时只删比它旧的
                int median = ages[ages.length / 2];
                int cut = median == ages[0] ? median + 1 : median;
                table.long2IntEntrySet().removeIf(entry -> now - entry.getIntValue() >= cut);
                if (table.size() >= MAX_CELLS) {
                    // 全是同一刻记下的（正常用法到不了：每刻探测有预算）
                    table.clear();
                }
            }
        }
        table.put(key, now);
    }

    private static int now(ServerLevel level) {
        return level.getServer().getTickCount();
    }

    // ---- 调试与 GameTest ----

    /** 记着几格「能动」 */
    public int size() {
        return allowedAt.size();
    }

    /** 记着几格「不能动」 */
    public int deniedSize() {
        return deniedAt.size();
    }

    /** 这一格现在记着「能动」（按主人离线时认的 1 小时算，不管主人在不在线） */
    public boolean remembers(Level level, BlockPos pos) {
        return level instanceof ServerLevel serverLevel && level.dimension() == dimension
                && fresh(allowedAt, pos.asLong(), now(serverLevel), REMEMBER_TICKS);
    }

    /** 这一格现在记着「不能动」 */
    public boolean remembersDenied(Level level, BlockPos pos) {
        return level instanceof ServerLevel serverLevel && level.dimension() == dimension
                && fresh(deniedAt, pos.asLong(), now(serverLevel), DENY_TICKS);
    }

    /** 全部忘掉 */
    public void clear() {
        allowedAt.clear();
        deniedAt.clear();
        dimension = null;
        memoryOwner = null;
    }

    /** GameTest 用：所有记录都当成早记了 ticks 刻（测「过期以后不认」，不用真等） */
    public void ageForTest(int ticks) {
        for (Long2IntOpenHashMap table : Arrays.asList(allowedAt, deniedAt)) {
            for (Long2IntMap.Entry entry : table.long2IntEntrySet()) {
                entry.setValue(entry.getIntValue() - ticks);
            }
        }
    }

    /** GameTest 用：不探测，直接替 owner 记一格的结论（测表满了怎么淘汰，不用真探测几千格） */
    public void recordForTest(ServerLevel level, BlockPos pos, UUID owner, boolean allowed) {
        record(level, pos, owner, allowed);
    }
}
