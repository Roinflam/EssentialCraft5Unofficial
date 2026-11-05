package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.RayTowerMenu;
import com.flameforge.essentialcraft5.registry.ECGenerators;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * 射线塔（两格高，方块实体只在下半格）。对应原作 TileRayTower。
 * <p>
 * 就是个 MRU 中继站：0 号槽放绑定宝石，从宝石指向的源头拉 MRU（距离同所有机器，三个轴各 16 格），
 * 别的机器再把宝石绑到射线塔上，这样一串射线塔就能把 MRU 送到远处。容量 5000，平衡值按拉进来的量加权平均。
 * 拉 MRU、界面状态都由 MRUBlockEntity 的绑定宝石逻辑处理，这里没有自己的逻辑。
 * <p>
 * 原作两个半格各有一个 TileRayTower（上半格那个有自己的存储和物品栏，但界面只开下半格的，是个没用的空壳）；
 * 这里上半格不建方块实体。绑定宝石点上半格时绑到下半格，由方块实现 IBoundGemTarget 声明。
 * 原作连线粒子（MRUFX，射线塔顶端 y+1.85 处发出）等框架的 MRU 连线粒子一起做。
 */
public class RayTowerBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.raytower */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("ray_tower");
            maxMRU = builder.defineInRange("max_mru", ECConfig.DEVICE_MAX_MRU, 1, Integer.MAX_VALUE);
            builder.pop();
        }
    }

    public static final int SLOT_GEM = 0;

    public RayTowerBlockEntity(BlockPos pos, BlockState state) {
        super(ECGenerators.RAY_TOWER_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), 1);
    }

    /** 槽位规则（两端共用）：0 号只放绑定宝石（原作 isItemValidForSlot） */
    public static boolean slotRule(int slot, ItemStack stack) {
        return defaultSlotRule(slot, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 顶上的水晶浮在 y+2 左右，渲染范围要把上面两格包进来，否则抬头看时水晶会被裁掉 */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0D, 2D, 0D);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RayTowerMenu(id, inventory, this);
    }
}
