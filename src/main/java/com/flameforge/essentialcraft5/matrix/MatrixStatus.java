package com.flameforge.essentialcraft5.matrix;

import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CMatrixStatusPacket;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 矩阵状态包（{@link S2CMatrixStatusPacket}）的内容（第十三批 13a，batch13.md §3.1.5）：淬炼台界面要的「九阶表、下一阶要什么、哪几样满足了」
 * 由服务端算好发给本人，客户端不自己算（站点、祭品、能量、数据包改过的九阶表都只有服务端知道）。客户端放在 {@code MatrixClient.lastStatus()}。
 *
 * <h2>字段</h2>
 * <pre>
 * rank: int                    现在几阶
 * enabled: bool                矩阵阶开着没有（关着时淬炼台开始不了）
 * peaceful: bool               和平难度（图谱条件按满足算）
 * codex: {total, nether, hoanna, hoanna_natives, bosses, mod, end, expedition}   各组种类数
 * pool: int, pool_cap: int     矩阵容量
 * reserve: int                 试炼储备
 * cooldown: int                试炼冷却剩几刻
 * ranks: [ {rank, available, title_key, codex_total, pentacle_tier, ubmru, mru, espe} × 1~9 ]   九阶表（available = 这一阶的数据读到了）
 * next: {                      下一阶（已经九阶或下一阶读不到时没有这一项）
 *   rank, codex_ok: bool, pentacle_tier, ubmru, mru, espe,
 *   conditions: [ {kind, key, have, need, ok} ],               图谱、属性条件逐条（{@link MatrixRank#conditions}）
 *   offerings: [ {items: [物品 id…], count} ],                 祭品（每项的候选物品，界面轮换着画）
 *   echoes: {count, alive, health, damage, time_seconds}
 * }
 * crucible: { … }              淬炼台自己的部分（站点、祭品格、台子 MRU、柱子 ESPE 满足没有），由淬炼台填，框架原样带上
 * </pre>
 */
public final class MatrixStatus {

    /** 每项祭品最多带几个候选物品（标签很大时截断，界面只轮换着画） */
    public static final int MAX_CANDIDATES = 16;

    private MatrixStatus() {
    }

    /** 按这名玩家现在的数据算一份；crucible 是淬炼台那部分（没有传 null） */
    public static CompoundTag build(ServerPlayer player, @Nullable CompoundTag crucible) {
        CompoundTag tag = new CompoundTag();
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            return tag;
        }
        int rank = data.getMatrixRank();
        boolean peaceful = MatrixRank.peaceful(player.level());
        tag.putInt("rank", rank);
        tag.putBoolean("enabled", MatrixRank.enabled());
        tag.putBoolean("peaceful", peaceful);
        CompoundTag codex = new CompoundTag();
        MatrixCodex.Counts counts = MatrixCodex.counts(data);
        for (MatrixCodex.Group group : MatrixCodex.Group.values()) {
            codex.putInt(group.key(), counts.get(group));
        }
        tag.put("codex", codex);
        tag.putInt("pool", data.getMatrixPool());
        tag.putInt("pool_cap", MatrixPool.capacity(rank));
        tag.putInt("reserve", data.getMatrixTrialReserve());
        tag.putInt("cooldown", data.getMatrixTrialCooldown());

        Map<Integer, MatrixRankData.Entry> table = MatrixRankData.all();
        ListTag ranks = new ListTag();
        for (int n = 1; n <= MatrixRank.MAX; n++) {
            MatrixRankData.Entry entry = table.get(n);
            CompoundTag row = new CompoundTag();
            row.putInt("rank", n);
            row.putBoolean("available", entry != null);
            row.putString("title_key", "matrix_rank.essentialcraft5.title." + n);
            if (entry != null) {
                row.putInt("codex_total", entry.codex().required(MatrixCodex.Group.TOTAL));
                row.putInt("pentacle_tier", entry.pentacleTier());
                row.putInt("ubmru", entry.ubmru());
                row.putInt("mru", entry.mru());
                row.putInt("espe", entry.espe());
            }
            ranks.add(row);
        }
        tag.put("ranks", ranks);

        MatrixRankData.Entry next = rank < MatrixRank.MAX ? table.get(rank + 1) : null;
        if (next != null) {
            tag.put("next", next(data, next, peaceful));
        }
        if (crucible != null) {
            tag.put("crucible", crucible.copy());
        }
        return tag;
    }

    private static CompoundTag next(IPlayerData data, MatrixRankData.Entry entry, boolean peaceful) {
        CompoundTag next = new CompoundTag();
        next.putInt("rank", entry.rank());
        next.putBoolean("codex_ok", MatrixRank.codexSatisfied(data, entry, peaceful));
        next.putInt("pentacle_tier", entry.pentacleTier());
        next.putInt("ubmru", entry.ubmru());
        next.putInt("mru", entry.mru());
        next.putInt("espe", entry.espe());
        ListTag conditions = new ListTag();
        for (MatrixRank.Condition condition : MatrixRank.conditions(data, entry, peaceful)) {
            CompoundTag row = new CompoundTag();
            row.putString("kind", condition.kind());
            row.putString("key", condition.key());
            row.putInt("have", condition.have());
            row.putInt("need", condition.need());
            row.putBoolean("ok", condition.ok());
            conditions.add(row);
        }
        next.put("conditions", conditions);
        ListTag offerings = new ListTag();
        for (MatrixRankData.Offering offering : entry.offerings()) {
            CompoundTag row = new CompoundTag();
            ListTag items = new ListTag();
            Set<Item> seen = new LinkedHashSet<>();
            for (ItemStack stack : offering.ingredient().getItems()) {
                if (seen.size() >= MAX_CANDIDATES) {
                    break;
                }
                if (seen.add(stack.getItem())) {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (id != null) {
                        items.add(StringTag.valueOf(id.toString()));
                    }
                }
            }
            row.put("items", items);
            row.putInt("count", offering.count());
            offerings.add(row);
        }
        next.put("offerings", offerings);
        CompoundTag echoes = new CompoundTag();
        echoes.putInt("count", entry.echoes().count());
        echoes.putInt("alive", entry.echoes().alive());
        echoes.putInt("health", entry.echoes().health());
        echoes.putInt("damage", entry.echoes().damage());
        echoes.putInt("time_seconds", entry.echoes().timeSeconds());
        next.put("echoes", echoes);
        return next;
    }

    /** 算一份发给本人（淬炼台打开界面、条件变了时调） */
    public static void send(ServerPlayer player, @Nullable CompoundTag crucible) {
        if (player.connection != null) {
            ECNetwork.sendTo(player, new S2CMatrixStatusPacket(build(player, crucible)));
        }
    }
}
