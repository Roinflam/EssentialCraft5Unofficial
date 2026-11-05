package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.menu.IntersectorMenu;
import com.flameforge.essentialcraft5.mru.MRULink;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * MRU 汇流器：两个绑定宝石槽，同时从两个源头拉 MRU，存 5000，本身又能被别的机器绑定当源——两路汇成一路的中继。
 * 对应原作 TileMRUIntersector（mruStorage.update 两个槽各调一次）。
 * <p>
 * 0 号槽由 MRUBlockEntity 的绑定宝石逻辑处理；1 号槽在 {@link #serverTick} 里用 {@link MRULink#pull} 自己拉，
 * 规则相同（三个轴各 16 格、只读已加载区块、平衡值加权平均、存满时每 20 tick 才核对状态），状态随菜单同步给界面。
 * 原作每 20 tick 比对整份 NBT、变了就发给 32 格内所有人，这里界面走菜单数据。
 * 界面：MRU 条 (7,4)、平衡值 (25,4)、两个宝石状态 (25,22)/(25,40)、存量 (25,58)，宝石槽 (108,23)/(108,41)，照原作 GuiMRUIntersector。
 */
public class IntersectorBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.mruintersector */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("intersector");
            maxMRU = builder.defineInRange("max_mru", ECConfig.DEVICE_MAX_MRU, 1, Integer.MAX_VALUE);
            builder.pop();
        }
    }

    public static final int SLOT_GEM = 0;
    public static final int SLOT_GEM_2 = 1;
    private static final int FULL_REFRESH = 20;

    private MRULink.Status secondStatus = MRULink.Status.NO_GEM;
    private int secondRefresh;
    private ItemStack cachedGem = ItemStack.EMPTY;
    @Nullable
    private CompoundTag cachedGemTag;
    @Nullable
    private GlobalPos cachedTarget;

    public IntersectorBlockEntity(BlockPos pos, BlockState state) {
        super(ECEnrichment.INTERSECTOR_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), 2);
    }

    /** 槽位规则（两端共用）：两个槽都只放绑定宝石（原作 isItemValidForSlot） */
    public static boolean slotRule(int slot, ItemStack stack) {
        return stack.getItem() instanceof BoundGemItem;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 第二个宝石的状态（第一个见 {@link #getLinkStatus()}） */
    public MRULink.Status getSecondLinkStatus() {
        return secondStatus;
    }

    @Override
    protected void serverTick() {
        ItemStack gem = inventory.getStackInSlot(SLOT_GEM_2);
        if (!(gem.getItem() instanceof BoundGemItem)) {
            secondStatus = MRULink.Status.NO_GEM;
            return;
        }
        if (mru.getMRU() >= mru.getMaxMRU() && secondRefresh-- > 0) {
            return;
        }
        secondRefresh = FULL_REFRESH;
        if (gem != cachedGem || gem.getTag() != cachedGemTag) {
            cachedGem = gem;
            cachedGemTag = gem.getTag();
            cachedTarget = BoundGemItem.getTarget(gem);
        }
        secondStatus = MRULink.pull(level, worldPosition, mru, cachedTarget, linkRange(), linkCrossDimension());
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new IntersectorMenu(id, inventory, this);
    }
}
