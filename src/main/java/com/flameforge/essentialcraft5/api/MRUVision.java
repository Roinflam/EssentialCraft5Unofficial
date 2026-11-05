package com.flameforge.essentialcraft5.api;

import com.flameforge.essentialcraft5.item.SoulStoneItem;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.registry.ECTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 玩家能不能「看见 MRU」。对应原作 {@code ECUtils.canPlayerSeeMRU}：主手、副手、头盔槽里有 MRU 可见物品。
 * <p>
 * 原作的可见物品有两种来源：{@code ApiCore.MRU_VISIBLE_LIST}（二阶 MRUCU 移动魔杖、各套防护头盔）和实现 {@code IMRUVisibilityHandler} 的物品（单片镜）。
 * 这里统一成物品标签 {@link ECTags#MRU_VISION}（{@code essentialcraft5:mru_vision}）：框架建空标签（tools/tags/framework.json），
 * 物品所在的模块在自己的 tools/tags/&lt;模块&gt;.json 里加成员，例如 ECGear：
 * <pre>{@code
 * { "items": { "essentialcraft5:mru_vision": [ "essentialcraft5:magic_monocle", "essentialcraft5:mru_mover_t2", "essentialcraft5:mhs_helmet" ] } }
 * }</pre>
 * 一阶 MRUCU 移动魔杖（一批的 mru_mover）原作不在列表里，不加。
 * <p>
 * 效果（batch2 D12）：聚集体所有人都看得见；能看见 MRU 的玩家看到的光线不做深度测试，隔着方块也能看见
 * （client/render/MRUPresenceRenderer 的 canSeeMRU 默认就是 {@link #canSee}）。以后 MRU 连线粒子同理。
 * <p>
 * 两端都能调（标签双端同步）；客户端用本地玩家调。
 * <p>
 * 第十三批起另有一条来源：矩阵一阶起拿着绑定自己的灵魂石（{@link #matrixVision}）。
 */
public final class MRUVision {

    private MRUVision() {
    }

    public static boolean canSee(Player player) {
        return grantsVision(player.getMainHandItem())
                || grantsVision(player.getOffhandItem())
                || grantsVision(player.getItemBySlot(EquipmentSlot.HEAD))
                || matrixVision(player);
    }

    /**
     * 矩阵视觉（第十三批 13a，batch13.md §3.1.4）：矩阵一阶起，主手或副手拿着绑定自己的灵魂石就看得见 MRU（同单片镜那套）。
     * 阶随普通同步包发给本人，客户端用本地玩家调也对。
     */
    public static boolean matrixVision(Player player) {
        return MatrixRanks.get(player) >= MatrixRank.VISION_RANK
                && (ownSoulStone(player, player.getMainHandItem()) || ownSoulStone(player, player.getOffhandItem()));
    }

    private static boolean ownSoulStone(Player player, ItemStack stack) {
        return stack.getItem() instanceof SoulStoneItem && player.getUUID().equals(SoulStoneItem.getOwner(stack));
    }

    /** 这个物品在主手、副手或头盔槽时能不能让玩家看见 MRU */
    public static boolean grantsVision(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ECTags.MRU_VISION);
    }
}
