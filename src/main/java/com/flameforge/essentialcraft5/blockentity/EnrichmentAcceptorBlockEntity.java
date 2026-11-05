package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandler;
import com.flameforge.essentialcraft5.enrichment.ControllerMRUView;
import com.flameforge.essentialcraft5.enrichment.EnrichmentLink;
import com.flameforge.essentialcraft5.enrichment.EnrichmentPart;
import com.flameforge.essentialcraft5.enrichment.EnrichmentStorage;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.menu.EnrichmentAcceptorMenu;
import com.flameforge.essentialcraft5.mru.MRULink;
import com.flameforge.essentialcraft5.mru.MRUStorage;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 富集仓 MRU 接收器：放一颗绑定宝石，从宝石指向的源头把 MRU 直接拉进整座仓（控制器的存储）。对应原作 TileMRUCUECAcceptor。
 * <p>
 * 自己不存 MRU（基类的存储上限为 0，也不用基类的拉取逻辑），拉取照 MRULink 的规则：距离三个轴各 16 格、只读已加载的区块、
 * 平衡值按拉进来的量加权平均。仓存满（或超出上限）时每 20 tick 才核对一次宝石状态。
 * 仓没成形、没连上控制器时不拉，界面上写「未连接富集仓」。
 * <p>
 * 对外的 MRU（别的东西往这里塞）只进不出，存量读数恒为 0、上限是整座仓的：机器的宝石误绑到这里时显示「目标没有 MRU」
 * （原作报整座仓的存量，宝石显示「工作中」却拉不到，见 {@link ControllerMRUView}）；原作还每 10 tick 把整个 NBT 发给 16 格内所有人，这里不发。
 * 界面：宝石槽 (80, 32)、宝石状态 (48, 50)，照原作 ContainerMRUAcceptor / GuiMRUAcceptor。
 */
public class EnrichmentAcceptorBlockEntity extends MRUBlockEntity implements EnrichmentPart {

    public static final int SLOT_GEM = 0;
    private static final int FULL_REFRESH = 20;

    private final EnrichmentLink link = new EnrichmentLink(this);
    private final LazyOptional<IMRUHandler> mruCap = LazyOptional.of(() -> new ControllerMRUView(link, ControllerMRUView.Mode.INPUT));

    private MRULink.Status status = MRULink.Status.NO_GEM;
    private int refresh;
    private ItemStack cachedGem = ItemStack.EMPTY;
    @Nullable
    private CompoundTag cachedGemTag;
    @Nullable
    private GlobalPos cachedTarget;

    public EnrichmentAcceptorBlockEntity(BlockPos pos, BlockState state) {
        super(ECEnrichment.EC_ACCEPTOR_BE.get(), pos, state, () -> 0, 1);
    }

    /** 槽位规则（两端共用）：只放绑定宝石（原作 isItemValidForSlot） */
    public static boolean slotRule(int slot, ItemStack stack) {
        return stack.getItem() instanceof BoundGemItem;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 不用基类往自己存储里拉的逻辑，改成往控制器里拉（见 serverTick） */
    @Override
    protected boolean hasBoundGemSlot() {
        return false;
    }

    @Override
    public EnrichmentLink enrichmentLink() {
        return link;
    }

    public boolean isLinked() {
        return link.isLinked();
    }

    @Override
    protected void serverTick() {
        ItemStack gem = inventory.getStackInSlot(SLOT_GEM);
        if (!(gem.getItem() instanceof BoundGemItem)) {
            status = MRULink.Status.NO_GEM;
            return;
        }
        EnrichmentControllerBlockEntity controller = link.controller();
        if (controller == null) {
            // 界面上显示「未连接富集仓」，这个状态不会被看到
            status = MRULink.Status.NO_TILE;
            return;
        }
        EnrichmentStorage storage = controller.storage();
        if (storage.getMRU() >= storage.getMaxMRU() && refresh-- > 0) {
            return;
        }
        refresh = FULL_REFRESH;
        if (gem != cachedGem || gem.getTag() != cachedGemTag) {
            cachedGem = gem;
            cachedGemTag = gem.getTag();
            cachedTarget = BoundGemItem.getTarget(gem);
        }
        status = MRULink.pull(level, worldPosition, storage, cachedTarget, linkRange(), linkCrossDimension());
    }

    /** 界面上的宝石状态是往仓里拉的状态 */
    @Override
    public MRULink.Status getLinkStatus() {
        return status;
    }

    /** 界面（MachineData）读到的是整座仓的存量；没连上时是自己那个空存储 */
    @Override
    public MRUStorage getMRUStorage() {
        EnrichmentControllerBlockEntity controller = link.controller();
        return controller != null ? controller.storage() : super.getMRUStorage();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ECCapabilities.MRU) {
            return mruCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        mruCap.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        link.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        link.load(tag);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new EnrichmentAcceptorMenu(id, inventory, this);
    }
}
