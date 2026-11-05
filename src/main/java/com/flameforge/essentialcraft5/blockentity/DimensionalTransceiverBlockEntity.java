package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.menu.DimensionalTransceiverMenu;
import com.flameforge.essentialcraft5.mru.MRULink;
import com.flameforge.essentialcraft5.registry.ECVoidMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * MRU 跨维度收发器（第六批 6b M2，batch6b.md §4.2）。对应原作 TileMRUDimensionalTransciever（+ MRUTileCrossDimStorage / MRUTileRangelessStorage）。
 * <p>
 * 一格绑定宝石，从宝石指向的 MRU 源拉满自己（容量 {@code max_mru}，默认 5000）；别的机器再用绑定宝石从它这里拉。
 * 和普通机器的区别只有距离：同一维度不限距离（{@link MRULink#UNLIMITED}），{@code allow_dimensional_transfer} 开着时还能连到别的维度。
 * 拉取规则全在框架的 {@link MRULink}：跨维度用 {@code server.getLevel}（不初始化维度）+ 只读已完全加载的区块（没加载返回 UNLOADED，绝不同步加载）；
 * 不许跨维度时，绑到别的维度返回 WRONG_DIMENSION；源头区块已加载但不 tick 时，被抽走的 MRU 照样立即标记存盘（{@code MRUBlockEntity.onStorageChanged}）。
 * <p>
 * 只放创造栏（原作全仓没有配方、战利品、交易；policy §4.7）。以后要给配方时必须再加「源头主人 = 收发器主人」和同维度距离上限（batch6b.md §5.2）。
 *
 * <h2>和原作不同</h2>
 * 原作跨维度每刻初始化维度、同步加载区块；两边都是 0 时算出 NaN；关掉跨维度后连到本维度同坐标；宝石有 NBT 没有 pos 时越界；
 * 界面在客户端现算宝石状态，跨维度时什么都不显示。这些框架都已修：状态由服务端算好随菜单同步（跨维度也显示）。
 * 更新包不带物品栏：宝石里记着源头的维度和坐标，不发给附近的人（渲染也用不到）。
 */
public class DimensionalTransceiverBlockEntity extends MRUBlockEntity {

    /** 服务端配置 machines.dimensional_transceiver（原作 tileentities.mrudimensionaltransciever，默认值照原作） */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.BooleanValue allowDimensionalTransfer;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("dimensional_transceiver");
            maxMRU = builder.comment("MRU capacity.", "MRU 容量")
                    .defineInRange("max_mru", ECConfig.DEVICE_MAX_MRU, 1, Integer.MAX_VALUE);
            allowDimensionalTransfer = builder.comment("Allow the bound gem to point at an MRU source in another dimension (same dimension: unlimited range either way).",
                            "允许绑定宝石连到别的维度的 MRU 源（同一维度不管开不开都不限距离）")
                    .define("allow_dimensional_transfer", true);
            builder.pop();
        }
    }

    public static final int SLOTS = 1;

    /** 只给 GameTest 的这一台自己的覆盖（null = 用配置） */
    @Nullable
    private Boolean testAllowDimensional;

    public DimensionalTransceiverBlockEntity(BlockPos pos, BlockState state) {
        super(ECVoidMachines.DIMENSIONAL_TRANSCEIVER_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), SLOTS);
    }

    /** 同一维度不限距离（原作 MRUTileRangelessStorage / MRUTileCrossDimStorage 都不查距离） */
    @Override
    protected int linkRange() {
        return MRULink.UNLIMITED;
    }

    @Override
    protected boolean linkCrossDimension() {
        return testAllowDimensional != null ? testAllowDimensional : ECConfig.get(Config.allowDimensionalTransfer);
    }

    /**
     * 只给 GameTest：这一台允不允许跨维度（null = 用配置）。只改这一台，不碰配置文件（服务端配置自动存盘，文件监视器会冲掉测试里设的值）。
     */
    public void setTestAllowDimensional(@Nullable Boolean allow) {
        testAllowDimensional = allow;
    }

    /** 槽位规则（两端共用）：唯一的格子只放绑定宝石 */
    public static boolean slotRule(int slot, ItemStack stack) {
        return stack.getItem() instanceof BoundGemItem;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 宝石里记着源头（可能在别的维度）的坐标，不发给附近的人；渲染用不到物品栏 */
    @Override
    protected boolean syncInventoryToClient() {
        return false;
    }

    @Override
    protected boolean syncBalanceToClient() {
        return false;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new DimensionalTransceiverMenu(id, inventory, this);
    }
}
