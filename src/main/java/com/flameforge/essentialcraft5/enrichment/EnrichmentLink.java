package com.flameforge.essentialcraft5.enrichment;

import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity;
import com.flameforge.essentialcraft5.util.ECLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 部件记着的控制器（放在部件方块实体里）。
 * <p>
 * 原作部件直接存控制器 Tile 的引用，而且从不清除：控制器拆了，输出器还能从那个死对象里抽 MRU；
 * 两座仓共用一面墙时，后扫描的那个抢走部件。这里：
 * <ul>
 *   <li>只存控制器坐标（存盘），用的时候走 {@link ECLevels#loadedBlockEntity} 取（不触发区块加载），缓存到方块实体被移除为止；</li>
 *   <li>{@link #controller()} 只在控制器<b>已成形、并且它的部件表里有我</b>时才返回它，所以记着的坐标过时了也无害；</li>
 *   <li>控制器拆掉时把部件解绑、部件拆掉时通知控制器（双向解除），见 EnrichmentControllerBlockEntity。</li>
 * </ul>
 * 一个部件同时只属于一座仓：扫描时发现部件已被另一座已成形的仓认领，就不算它（共用墙上的存储方块不会给两座仓都加容量）。
 */
public final class EnrichmentLink {

    public static final String NBT_KEY = "enrichment_controller";

    private final BlockEntity owner;
    @Nullable
    private BlockPos controllerPos;
    @Nullable
    private EnrichmentControllerBlockEntity cached;

    public EnrichmentLink(BlockEntity owner) {
        this.owner = owner;
    }

    /** 记着的控制器坐标（不代表连接有效） */
    @Nullable
    public BlockPos controllerPos() {
        return controllerPos;
    }

    /** 控制器扫描成形时调用 */
    public void bind(BlockPos controller) {
        if (!controller.equals(controllerPos)) {
            controllerPos = controller.immutable();
            cached = null;
            markSaved();
        }
    }

    /** 控制器不再认领这个部件时调用；只解除指向 {@code controller} 的连接（部件可能已经被别的控制器认领了） */
    public void unbind(BlockPos controller) {
        if (controller.equals(controllerPos)) {
            controllerPos = null;
            cached = null;
            markSaved();
        }
    }

    /**
     * 有效的控制器：已加载、已成形、认领了这个部件。只在服务端有意义（客户端恒为 null）。
     */
    @Nullable
    public EnrichmentControllerBlockEntity controller() {
        EnrichmentControllerBlockEntity c = resolve();
        return c != null && c.isFormed() && c.hasPart(owner.getBlockPos()) ? c : null;
    }

    public boolean isLinked() {
        return controller() != null;
    }

    /** 这个部件是否已经属于 {@code me} 以外的一座有效的仓 */
    public boolean ownedByOther(BlockPos me) {
        return controllerPos != null && !controllerPos.equals(me) && controller() != null;
    }

    /** 部件被拆掉时调用：通知控制器（不管连接是否有效，控制器自己判断） */
    public void onPartRemoved() {
        EnrichmentControllerBlockEntity c = resolve();
        if (c != null) {
            c.onPartRemoved(owner.getBlockPos(), owner instanceof EnrichmentPart part && part.countsAsHoldingChamber());
        }
        controllerPos = null;
        cached = null;
    }

    /** 记着的坐标上已加载的控制器，不检查是否成形 */
    @Nullable
    private EnrichmentControllerBlockEntity resolve() {
        Level level = owner.getLevel();
        if (level == null || level.isClientSide || controllerPos == null) {
            return null;
        }
        EnrichmentControllerBlockEntity c = cached;
        if (c == null || c.isRemoved() || c.getLevel() != level) {
            c = ECLevels.loadedBlockEntity(level, controllerPos) instanceof EnrichmentControllerBlockEntity found ? found : null;
            cached = c;
        }
        return c;
    }

    private void markSaved() {
        Level level = owner.getLevel();
        if (level != null && !level.isClientSide) {
            level.blockEntityChanged(owner.getBlockPos());
        }
    }

    public void save(CompoundTag tag) {
        if (controllerPos != null) {
            tag.putLong(NBT_KEY, controllerPos.asLong());
        }
    }

    public void load(CompoundTag tag) {
        controllerPos = tag.contains(NBT_KEY, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(NBT_KEY)) : null;
        cached = null;
    }
}
