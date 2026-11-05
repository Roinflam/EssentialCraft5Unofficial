package com.flameforge.essentialcraft5.matrix;

import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.blockentity.MatrixCrucibleBlockEntity;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.item.SoulStoneItem;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 试炼开始前的核对（第十三批 13a，batch13.md §3.1.2，限制见 policy §4.16）：开始按钮、界面上的打勾、GameTest 共用同一份 {@link #evaluate}，
 * 界面看到「全打勾」和服务端真开始用的是同一套判断。只在服务端用。
 *
 * <h2>核对什么（顺序就是界面上提示的先后）</h2>
 * <ol>
 *   <li>矩阵阶开着（{@code general.matrix_rank.enabled}）、还没到九阶、下一阶的数据读到了；</li>
 *   <li>自己没有别的试炼在跑、这台台子上没有别人在试炼、不在失败冷却里；</li>
 *   <li>本人：主手拿着<b>绑定自己</b>的灵魂石、离台子中心不超过 {@value #START_RADIUS} 格、生命不少于上限的一半；</li>
 *   <li>图谱和属性条件（{@link MatrixRank#conditions}，和平难度下图谱类按满足算）；</li>
 *   <li>站点（{@link #findSite}）：四阶起 {@value #SITE_RADIUS} 格内要有一座成形、档位够的灵魂五芒星，<b>只认自己的或自己能动的</b>，ESPE 从它的柱子扣；</li>
 *   <li>祭品：四个祭品格里能一一对上这一阶的每一项（种类、数量）；</li>
 *   <li>能量：UBMRU（试炼储备 + 身上）、台子里的 MRU、五芒星的有效 ESPE 都够；这一阶要的 MRU 比台子的容量还大时单独说
 *       （{@link Blocker#MRU_CAPACITY}，数据包或 {@code machines.matrix_crucible.max_mru} 配错了，玩家怎么充都凑不够）。</li>
 * </ol>
 * 和平难度不是挡住的理由：那时试炼改走引导（不放回响，{@link MatrixTrials}）。
 *
 * <h2>站点为什么只认自己的或能动的五芒星</h2>
 * 同 {@code CorruptionGate} 记档位的口径（policy §1.1 第 3 条：不拿别人机器里的能量）：五芒星记的主人是你（单人按 {@link ECProtection#actingOwner}），
 * 或者以你的身份问五芒星那一格能不能动（{@link ECProtection#check}，有缓存）得到「能」。邻居家的五芒星不算站点、不扣它的 ESPE，
 * 界面写「这座五芒星不归你」。问不出结论（探测预算用完、区块没加载）时这一次当作还不知道，下一秒再问。
 */
public final class MatrixTrialChecks {

    /** 开始时离台子中心最多几格（「台子 3 格内」：站在台子三格外的那一格脚下离中心约 3.04） */
    public static final double START_RADIUS = 3.5D;
    /** 站点：五芒星离台子最多几格（中心到中心的直线距离） */
    public static final int SITE_RADIUS = 8;

    private MatrixTrialChecks() {
    }

    /** 挡住开始的原因（顺序 = 界面提示的先后） */
    public enum Blocker {
        DISABLED("disabled"),
        MAX_RANK("max_rank"),
        NO_DATA("no_data"),
        RUNNING("running"),
        BUSY("busy"),
        COOLDOWN("cooldown"),
        SOUL_STONE("soul_stone"),
        TOO_FAR("too_far"),
        HEALTH("health"),
        CODEX("codex"),
        SITE_NONE("site_none"),
        SITE_TIER("site_tier"),
        SITE_NOT_YOURS("site_not_yours"),
        SITE_UNKNOWN("site_unknown"),
        SITE_ESPE("site_espe"),
        OFFERINGS("offerings"),
        UBMRU("ubmru"),
        /** 这一阶要的 MRU 超过了台子的容量，怎么充都凑不够 */
        MRU_CAPACITY("mru_capacity"),
        MRU("mru");

        private final String key;

        Blocker(String key) {
            this.key = key;
        }

        /** 状态包、语言键里的名字：gui.essentialcraft5.matrix_crucible.blocker.&lt;key&gt; */
        public String key() {
            return key;
        }
    }

    /** 站点的情形 */
    public enum SiteState {
        /** 这一阶只要台子 */
        NOT_NEEDED("not_needed"),
        OK("ok"),
        /** 范围内一座成形的五芒星都没有 */
        NONE("none"),
        /** 有，但档位都不够 */
        TIER("tier"),
        /** 档位够的都不归你（不是你的、你也动不了） */
        NOT_YOURS("not_yours"),
        /** 档位够、归你的那座还问不出结论（探测预算、区块） */
        UNKNOWN("unknown"),
        /** 归你的那座柱子里有效 ESPE 不够 */
        ESPE("espe");

        private final String key;

        SiteState(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    /**
     * 站点核对的结果。
     *
     * @param state     情形
     * @param needTier  要的档位（−1 = 只要台子）
     * @param foundTier 范围内看到的最高档位（−1 = 没有）
     * @param espe      选中的那座（或归你的里最多的那座）的有效 ESPE
     * @param pentacle  选中的五芒星（OK、ESPE 时有）
     */
    public record Site(SiteState state, int needTier, int foundTier, int espe, @Nullable PlayerPentacleBlockEntity pentacle) {

        public boolean ok() {
            return state == SiteState.OK || state == SiteState.NOT_NEEDED;
        }
    }

    /**
     * 一次核对的全部结果。
     *
     * @param entry       下一阶的数据（没有时 null）
     * @param blockers    挡住开始的原因（空 = 能开始）
     * @param site        站点
     * @param offerings   每项祭品对上的祭品格（0~3），对不上整组为 null
     * @param offeringOk  每项祭品单看有没有一格满足（界面打勾用）
     * @param ubmruNeed   要的 UBMRU
     * @param reserve     试炼储备
     * @param ubmru       身上的 UBMRU
     * @param mru         台子里的 MRU
     * @param channel     这次走引导（和平难度或服主关了回响）
     */
    public record Report(@Nullable MatrixRankData.Entry entry, List<Blocker> blockers, Site site, int @Nullable [] offerings,
                         boolean[] offeringOk, int ubmruNeed, int reserve, int ubmru, int mru, boolean channel) {

        public boolean ready() {
            return blockers.isEmpty();
        }

        @Nullable
        public Blocker first() {
            return blockers.isEmpty() ? null : blockers.get(0);
        }
    }

    // ------------------------------------------------------------------ 核对

    /**
     * 核对 player 现在能不能在 crucible 上开始下一阶的试炼。只读，不扣任何东西；站点会以玩家身份问一次五芒星那一格（有缓存）。
     */
    public static Report evaluate(ServerPlayer player, MatrixCrucibleBlockEntity crucible) {
        List<Blocker> blockers = new ArrayList<>();
        IPlayerData data = PlayerDataEvents.get(player);
        int rank = data == null ? 0 : data.getMatrixRank();
        MatrixRankData.Entry entry = rank < MatrixRank.MAX ? MatrixRankData.get(rank + 1) : null;
        if (!MatrixRank.enabled()) {
            blockers.add(Blocker.DISABLED);
        }
        if (rank >= MatrixRank.MAX) {
            blockers.add(Blocker.MAX_RANK);
        } else if (entry == null || data == null) {
            blockers.add(Blocker.NO_DATA);
        }
        MatrixTrials.Trial running = MatrixTrials.trialOf(player.getUUID());
        if (running != null) {
            blockers.add(Blocker.RUNNING);
        }
        UUID locked = crucible.lockedBy();
        if (locked != null && !locked.equals(player.getUUID())) {
            blockers.add(Blocker.BUSY);
        }
        if (data != null && data.getMatrixTrialCooldown() > 0) {
            blockers.add(Blocker.COOLDOWN);
        }
        if (!holdsOwnSoulStone(player)) {
            blockers.add(Blocker.SOUL_STONE);
        }
        if (!near(player, crucible.getBlockPos(), START_RADIUS)) {
            blockers.add(Blocker.TOO_FAR);
        }
        if (player.getHealth() < player.getMaxHealth() * 0.5F) {
            blockers.add(Blocker.HEALTH);
        }
        if (entry == null || data == null) {
            Site none = new Site(SiteState.NOT_NEEDED, -1, -1, 0, null);
            return new Report(entry, List.copyOf(blockers), none, null, new boolean[0], 0, data == null ? 0 : data.getMatrixTrialReserve(),
                    data == null ? 0 : data.getUBMRU(), crucible.getMRUStorage().getMRU(), false);
        }
        if (!MatrixRank.codexSatisfied(data, entry, MatrixRank.peaceful(player.level()))) {
            blockers.add(Blocker.CODEX);
        }
        int needTier = needTier(entry);
        Site site = findSite(player, crucible, needTier, entry.espe());
        switch (site.state()) {
            case NONE -> blockers.add(Blocker.SITE_NONE);
            case TIER -> blockers.add(Blocker.SITE_TIER);
            case NOT_YOURS -> blockers.add(Blocker.SITE_NOT_YOURS);
            case UNKNOWN -> blockers.add(Blocker.SITE_UNKNOWN);
            case ESPE -> blockers.add(Blocker.SITE_ESPE);
            default -> {
            }
        }
        List<ItemStack> slots = crucible.offerings();
        int[] match = matchOfferings(entry.offerings(), slots);
        boolean[] eachOk = new boolean[entry.offerings().size()];
        for (int i = 0; i < eachOk.length; i++) {
            MatrixRankData.Offering offering = entry.offerings().get(i);
            for (ItemStack stack : slots) {
                if (offering.test(stack)) {
                    eachOk[i] = true;
                    break;
                }
            }
        }
        if (match == null) {
            blockers.add(Blocker.OFFERINGS);
        }
        int reserve = data.getMatrixTrialReserve();
        int ubmru = data.getUBMRU();
        if ((long) reserve + Math.max(0, ubmru) < entry.ubmru()) {
            blockers.add(Blocker.UBMRU);
        }
        int mru = crucible.getMRUStorage().getMRU();
        if (entry.mru() > crucible.getMRUStorage().getMaxMRU()) {
            blockers.add(Blocker.MRU_CAPACITY);
        } else if (mru < entry.mru()) {
            blockers.add(Blocker.MRU);
        }
        return new Report(entry, List.copyOf(blockers), site, match, eachOk, entry.ubmru(), reserve, ubmru, mru,
                MatrixTrials.channelMode(player.level()));
    }

    /** 这一阶要的五芒星档位（−1 = 只要台子）；数据包只写了 ESPE、没写档位时按 0 档算：ESPE 总得有座五芒星可扣 */
    public static int needTier(MatrixRankData.Entry entry) {
        return entry.pentacleTier() < 0 && entry.espe() > 0 ? 0 : entry.pentacleTier();
    }

    /** 主手拿着绑定自己的灵魂石 */
    public static boolean holdsOwnSoulStone(Player player) {
        ItemStack stack = player.getMainHandItem();
        return stack.getItem() instanceof SoulStoneItem && player.getUUID().equals(SoulStoneItem.getOwner(stack));
    }

    /** 玩家（脚）离这一格方块中心不超过 radius 格 */
    public static boolean near(Player player, BlockPos pos, double radius) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= radius * radius;
    }

    // ------------------------------------------------------------------ 祭品

    /**
     * 给每项祭品找一个不重复的祭品格（种类对、数量够）。四项四格，回溯就够。
     *
     * @return 第 i 项对上的格子（0~3）；对不上返回 null；没有祭品时是空数组
     */
    public static int @Nullable [] matchOfferings(List<MatrixRankData.Offering> offerings, List<ItemStack> slots) {
        int[] assigned = new int[offerings.size()];
        boolean[] used = new boolean[slots.size()];
        return assign(offerings, slots, 0, assigned, used) ? assigned : null;
    }

    private static boolean assign(List<MatrixRankData.Offering> offerings, List<ItemStack> slots, int index, int[] assigned, boolean[] used) {
        if (index >= offerings.size()) {
            return true;
        }
        for (int slot = 0; slot < slots.size(); slot++) {
            if (!used[slot] && offerings.get(index).test(slots.get(slot))) {
                used[slot] = true;
                assigned[index] = slot;
                if (assign(offerings, slots, index + 1, assigned, used)) {
                    return true;
                }
                used[slot] = false;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 站点

    /**
     * 找站点：台子 {@value #SITE_RADIUS} 格内、同一个世界、已加载区块里的灵魂五芒星，档位读它自己记的（{@link PlayerPentacleBlockEntity#getTier()}，
     * 它每 40 刻按结构缓存重判一次）。档位够的里面只认自己的或能动的，取有效 ESPE 最多的那座。
     *
     * @param needTier 要的档位（−1 = 不要五芒星）
     * @param espeNeed 要的有效 ESPE
     */
    public static Site findSite(ServerPlayer player, MatrixCrucibleBlockEntity crucible, int needTier, int espeNeed) {
        if (needTier < 0) {
            return new Site(SiteState.NOT_NEEDED, -1, -1, 0, null);
        }
        if (!(crucible.getLevel() instanceof ServerLevel level)) {
            return new Site(SiteState.NONE, needTier, -1, 0, null);
        }
        int foundTier = -1;
        boolean anyEligible = false;
        boolean anyUnknown = false;
        PlayerPentacleBlockEntity best = null;
        int bestEspe = -1;
        for (PlayerPentacleBlockEntity pentacle : pentaclesNear(level, crucible.getBlockPos())) {
            int tier = pentacle.getTier();
            if (tier < 0) {
                continue;
            }
            foundTier = Math.max(foundTier, tier);
            if (tier < needTier) {
                continue;
            }
            anyEligible = true;
            ECProtection.Verdict verdict = ownsOrMayEdit(player, pentacle, crucible);
            if (verdict == ECProtection.Verdict.UNKNOWN) {
                anyUnknown = true;
                continue;
            }
            if (verdict == ECProtection.Verdict.DENY) {
                continue;
            }
            int espe = pentacle.effectiveESPE();
            if (espe > bestEspe) {
                best = pentacle;
                bestEspe = espe;
            }
        }
        if (best != null) {
            return new Site(bestEspe >= espeNeed ? SiteState.OK : SiteState.ESPE, needTier, foundTier, bestEspe, best);
        }
        if (anyUnknown) {
            return new Site(SiteState.UNKNOWN, needTier, foundTier, 0, null);
        }
        if (anyEligible) {
            return new Site(SiteState.NOT_YOURS, needTier, foundTier, 0, null);
        }
        return new Site(foundTier < 0 ? SiteState.NONE : SiteState.TIER, needTier, foundTier, 0, null);
    }

    /** 这座五芒星归不归这名玩家：记的主人是他（单人按房主），或者以他的身份问那一格能动 */
    public static ECProtection.Verdict ownsOrMayEdit(ServerPlayer player, PlayerPentacleBlockEntity pentacle, Object caller) {
        UUID owner = ECProtection.actingOwner(pentacle);
        if (owner != null && owner.equals(player.getUUID())) {
            return ECProtection.Verdict.ALLOW;
        }
        if (pentacle.getLevel() == null) {
            return ECProtection.Verdict.DENY;
        }
        return ECProtection.check(player, pentacle.getLevel(), pentacle.getBlockPos(), caller);
    }

    /** 台子 {@value #SITE_RADIUS} 格内（中心到中心）已加载区块里的灵魂五芒星；不加载区块 */
    public static List<PlayerPentacleBlockEntity> pentaclesNear(ServerLevel level, BlockPos center) {
        List<PlayerPentacleBlockEntity> out = new ArrayList<>();
        int minX = SectionPos.blockToSectionCoord(center.getX() - SITE_RADIUS);
        int maxX = SectionPos.blockToSectionCoord(center.getX() + SITE_RADIUS);
        int minZ = SectionPos.blockToSectionCoord(center.getZ() - SITE_RADIUS);
        int maxZ = SectionPos.blockToSectionCoord(center.getZ() + SITE_RADIUS);
        double max = (double) SITE_RADIUS * SITE_RADIUS;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof PlayerPentacleBlockEntity pentacle && !be.isRemoved()
                            && be.getBlockPos().distSqr(center) <= max) {
                        out.add(pentacle);
                    }
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 状态包

    /**
     * 状态包里淬炼台那一份（{@code MatrixStatus} 的 {@code crucible} 子标签），界面按它画打勾：
     * <pre>
     * ready: bool, blockers: [key…], channel: bool, running: bool（自己正在这台上试炼）, locked: bool（台子被占着）
     * cooldown: int（刻）, health_ok, hand_ok, near_ok: bool
     * site: {state, need_tier, found_tier, espe, espe_need}
     * offerings: [bool…]（每项单看有没有一格满足）, offerings_ok: bool（能一一对上）
     * ubmru_need, reserve, ubmru, mru_need, mru, mru_cap（台子的 MRU 容量）, reserve_cap（下一阶要的 UBMRU = 储备上限）
     * channel_seconds（引导要站多久）
     * </pre>
     */
    public static CompoundTag statusTag(ServerPlayer player, MatrixCrucibleBlockEntity crucible) {
        Report report = evaluate(player, crucible);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("ready", report.ready());
        ListTag blockers = new ListTag();
        for (Blocker blocker : report.blockers()) {
            blockers.add(net.minecraft.nbt.StringTag.valueOf(blocker.key()));
        }
        tag.put("blockers", blockers);
        tag.putBoolean("channel", report.channel());
        MatrixTrials.Trial trial = MatrixTrials.trialOf(player.getUUID());
        tag.putBoolean("running", trial != null && trial.at(crucible));
        tag.putBoolean("locked", crucible.isLocked());
        IPlayerData data = PlayerDataEvents.get(player);
        tag.putInt("cooldown", data == null ? 0 : data.getMatrixTrialCooldown());
        tag.putBoolean("health_ok", !report.blockers().contains(Blocker.HEALTH));
        tag.putBoolean("hand_ok", !report.blockers().contains(Blocker.SOUL_STONE));
        tag.putBoolean("near_ok", !report.blockers().contains(Blocker.TOO_FAR));
        CompoundTag site = new CompoundTag();
        site.putString("state", report.site().state().key());
        site.putInt("need_tier", report.site().needTier());
        site.putInt("found_tier", report.site().foundTier());
        site.putInt("espe", Math.max(0, report.site().espe()));
        site.putInt("espe_need", report.entry() == null ? 0 : report.entry().espe());
        tag.put("site", site);
        ListTag offerings = new ListTag();
        for (boolean ok : report.offeringOk()) {
            offerings.add(net.minecraft.nbt.ByteTag.valueOf(ok));
        }
        tag.put("offerings", offerings);
        tag.putBoolean("offerings_ok", report.offerings() != null);
        tag.putInt("ubmru_need", report.ubmruNeed());
        tag.putInt("reserve", report.reserve());
        tag.putInt("ubmru", report.ubmru());
        tag.putInt("mru_need", report.entry() == null ? 0 : report.entry().mru());
        tag.putInt("mru", report.mru());
        tag.putInt("mru_cap", crucible.getMRUStorage().getMaxMRU());
        tag.putInt("reserve_cap", MatrixTrials.reserveCap(player));
        tag.putInt("channel_seconds", report.entry() == null ? 0 : MatrixTrials.channelTicks(report.entry().rank()) / 20);
        return tag;
    }
}
