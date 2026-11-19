package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CBuilderFlightPacket;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 创世神之杖施工的「先计划、后执行」：飞行特效要提前 {@code vfx_lead_ticks}（D）刻告诉客户端哪些格子要放，
 * 所以放置类任务（填充、全部替换）在刻 t 只读方块挑出这一刻要做的格子、发 {@link S2CBuilderFlightPacket}，到刻 t + D 才照常逐格复核、真放。
 * <p>
 * 计划不取料、不扣 MRU、不改世界，也不替执行做任何判断：执行时权限、材料、MRU、立不住、方块变了照第一次那样全部重查，
 * 计划里的格子没放成就是没放成。D = 0 时没有计划这一步，任务和以前一样当刻就做，也不发包。
 * <p>
 * 计划按格子序号记：一次计划是「序号 [上一批末尾, toIndex) 这一段，第 due 刻起可以执行」。执行只做到已经到期的最后一段的末尾；
 * 耗时预算不够时剩下的留到下一刻接着做，不另发包。每个任务最多攒 D + 1 段，攒满（执行落后）就先不计划。
 */
public final class BuilderFlightPlan {

    /** 一个任务的计划队列：D + 1 段的环形缓冲 */
    static final class Ring {
        final int lead;
        private final long[] due;
        private final int[] toIndex;
        private int head;
        private int size;
        /** 下一次计划从这一格序号起 */
        int planNext;
        /** 这一单发过飞行包（收尾时要不要发收尾包） */
        boolean sent;

        Ring(int lead) {
            this.lead = lead;
            this.due = new long[lead + 1];
            this.toIndex = new int[lead + 1];
        }

        boolean full() {
            return size == due.length;
        }

        int size() {
            return size;
        }

        void push(long dueTick, int to) {
            int slot = (head + size) % due.length;
            due[slot] = dueTick;
            toIndex[slot] = to;
            size++;
        }

        /** 这一刻最多可以执行到哪一格序号（不含）；没有到期的段就是 next 本身 */
        int executableUntil(long now, int next) {
            int until = next;
            for (int k = 0; k < size; k++) {
                int slot = (head + k) % due.length;
                if (due[slot] > now) {
                    break;
                }
                until = Math.max(until, toIndex[slot]);
            }
            return until;
        }

        /** 执行到了 next：做完的段移出 */
        void retire(int next) {
            while (size > 0 && toIndex[head] <= next) {
                head = (head + 1) % due.length;
                size--;
            }
        }

        /** 丢掉全部计划（暂停、结束）：计划了没执行的格子不再执行，下一次计划从 next 重新起 */
        void reset(int next) {
            head = 0;
            size = 0;
            planNext = next;
        }
    }

    /** GameTest 用：记下发出去的每个包和收包人 */
    public record Sent(ServerPlayer to, S2CBuilderFlightPacket packet) {
    }

    @Nullable
    private static List<Sent> recorder;

    private BuilderFlightPlan() {
    }

    /** GameTest 用：开始记录发出去的飞行包（放在单独的 batch 里，@BeforeBatch 开、@AfterBatch 关） */
    public static void startRecording() {
        recorder = Collections.synchronizedList(new ArrayList<>());
    }

    public static void stopRecording() {
        recorder = null;
    }

    /** 记下的包（没在记录时为空表） */
    public static List<Sent> recorded() {
        List<Sent> list = recorder;
        return list == null ? List.of() : List.copyOf(list);
    }

    /**
     * 把一批格子按包的上限拆成几个包：同一包里每一维的跨度不超过 {@value S2CBuilderFlightPacket#MAX_OFFSET}、
     * 方块状态不超过 {@value S2CBuilderFlightPacket#MAX_PALETTE} 种、格数不超过 {@value S2CBuilderFlightPacket#MAX_CELLS}；
     * 顺序照传进来的顺序（客户端按包里的顺序错开落位时刻），超了哪一项就从这一格起开新包。
     */
    public static List<S2CBuilderFlightPacket> pack(int kind, int casterId, int jobId, int lead, LongArrayList positions, IntArrayList stateIds) {
        List<S2CBuilderFlightPacket> packets = new ArrayList<>();
        int n = Math.min(positions.size(), stateIds.size());
        int start = 0;
        while (start < n) {
            BlockPos first = BlockPos.of(positions.getLong(start));
            int minX = first.getX(), minY = first.getY(), minZ = first.getZ();
            int maxX = minX, maxY = minY, maxZ = minZ;
            IntArrayList palette = new IntArrayList();
            palette.add(stateIds.getInt(start));
            int end = start + 1;
            while (end < n && end - start < S2CBuilderFlightPacket.MAX_CELLS) {
                BlockPos pos = BlockPos.of(positions.getLong(end));
                int nMinX = Math.min(minX, pos.getX()), nMaxX = Math.max(maxX, pos.getX());
                int nMinY = Math.min(minY, pos.getY()), nMaxY = Math.max(maxY, pos.getY());
                int nMinZ = Math.min(minZ, pos.getZ()), nMaxZ = Math.max(maxZ, pos.getZ());
                if (nMaxX - nMinX > S2CBuilderFlightPacket.MAX_OFFSET || nMaxY - nMinY > S2CBuilderFlightPacket.MAX_OFFSET
                        || nMaxZ - nMinZ > S2CBuilderFlightPacket.MAX_OFFSET) {
                    break;
                }
                int state = stateIds.getInt(end);
                if (!palette.contains(state)) {
                    if (palette.size() >= S2CBuilderFlightPacket.MAX_PALETTE) {
                        break;
                    }
                    palette.add(state);
                }
                minX = nMinX;
                maxX = nMaxX;
                minY = nMinY;
                maxY = nMaxY;
                minZ = nMinZ;
                maxZ = nMaxZ;
                end++;
            }
            int[] cells = new int[end - start];
            for (int i = start; i < end; i++) {
                BlockPos pos = BlockPos.of(positions.getLong(i));
                cells[i - start] = S2CBuilderFlightPacket.packCell(pos.getX() - minX, pos.getY() - minY, pos.getZ() - minZ,
                        palette.indexOf(stateIds.getInt(i)));
            }
            packets.add(new S2CBuilderFlightPacket(kind, casterId, jobId, lead, BlockPos.asLong(minX, minY, minZ), palette.toIntArray(), cells));
            start = end;
        }
        return packets;
    }

    /** 收尾包：这一单不会再放任何已经发过的格子 */
    static S2CBuilderFlightPacket endPacket(int casterId, int jobId, int lead) {
        return new S2CBuilderFlightPacket(S2CBuilderFlightPacket.KIND_END, casterId, jobId, lead, 0L, new int[0], new int[0]);
    }

    /**
     * 发给离施工外框 {@code vfx_send_radius} 格内、在施工那个维度里的玩家；施法者本人总收到（已经下线的不发）。
     *
     * @param level 施工所在的维度（施法者换了维度以后照旧按原来的维度找旁观者）
     */
    static void send(@Nullable ServerLevel level, ServerPlayer caster, BoundingBox box, List<S2CBuilderFlightPacket> packets) {
        if (packets.isEmpty()) {
            return;
        }
        List<ServerPlayer> targets = recipients(level, caster, box, caster.server.getPlayerList().getPlayers());
        List<Sent> list = recorder;
        for (ServerPlayer target : targets) {
            for (S2CBuilderFlightPacket packet : packets) {
                if (list != null) {
                    list.add(new Sent(target, packet));
                }
                ECNetwork.sendTo(target, packet);
            }
        }
    }

    /**
     * 收包的人：施法者本人（没下线就算，不管在哪个维度），加上 candidates 里在 level 这个维度、离施工外框最近一点 {@code vfx_send_radius} 格以内的其他人（大范围施工时边上的人也收得到）。
     * level 为 null 时只有施法者。
     */
    public static List<ServerPlayer> recipients(@Nullable ServerLevel level, ServerPlayer caster, BoundingBox box, Iterable<ServerPlayer> candidates) {
        List<ServerPlayer> targets = new ArrayList<>();
        if (!caster.hasDisconnected()) {
            targets.add(caster);
        }
        if (level == null) {
            return targets;
        }
        double radius = ECBuilder.Config.vfxSendRadius();
        for (ServerPlayer other : candidates) {
            if (other != caster && !other.getUUID().equals(caster.getUUID()) && !other.hasDisconnected() && other.level() == level
                    && BuilderSelection.distanceSqToBox(other.position(), box) <= radius * radius) {
                targets.add(other);
            }
        }
        return targets;
    }
}
