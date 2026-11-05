package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.enrichment.EnrichmentStorage;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 富集仓红石控制器：仓里的 MRU 达到设定的百分比时向四周输出 15 级红石信号。对应原作 TileMRUCUECRedstoneController。
 * <p>
 * 设定 0~10：0 = 始终输出，1~9 = 存量达到 10%~90%，10 = 存满（原作 {@code MRU / 上限 × 10 ≥ 设定}，这里用整数比较，没有浮点误差）。
 * 空手潜行右键切换设定，普通右键查看。只有连着一座已成形的仓时才输出。
 * <p>
 * 与原作不同：
 * <ul>
 *   <li>不再要求仓里有 MRU 聚集体（原作 outputRedstone 先查 getMRUCU() != null，研究书没写，D10 定为去掉）。</li>
 *   <li>原作每 20 tick 无条件通知一次邻居；这里每 20 tick 算一次，输出变了才通知。输出存盘，重进世界后不会先断一下。</li>
 *   <li>提示消息由服务端发（原作只在客户端拼字符串，服务端的设定和客户端显示的可能对不上）。</li>
 * </ul>
 */
public class EnrichmentRedstoneControllerBlockEntity extends EnrichmentPartBlockEntity {

    public static final int MAX_SETTING = 10;
    /** 原作每 20 tick 更新一次 */
    public static final int UPDATE_INTERVAL = 20;

    private static final String TAG_SETTING = "setting";
    private static final String TAG_POWERED = "powered";

    private int setting;
    private boolean powered;
    private int timer;

    public EnrichmentRedstoneControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ECEnrichment.EC_REDSTONE_CONTROLLER_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EnrichmentRedstoneControllerBlockEntity be) {
        if (++be.timer >= UPDATE_INTERVAL) {
            be.timer = 0;
            be.update();
        }
    }

    public int getSetting() {
        return setting;
    }

    public boolean isPowered() {
        return powered;
    }

    /** 潜行右键：0 → 1 → … → 10 → 0，下一 tick 立即按新设定重算 */
    public void cycleSetting() {
        setting = (setting + 1) % (MAX_SETTING + 1);
        timer = UPDATE_INTERVAL;
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    public void setSetting(int value) {
        setting = Mth.clamp(value, 0, MAX_SETTING);
        timer = UPDATE_INTERVAL;
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    /** 按现在的设定和仓里的存量该不该出信号 */
    public boolean computeOutput() {
        EnrichmentControllerBlockEntity controller = link.controller();
        if (controller == null) {
            return false;
        }
        EnrichmentStorage storage = controller.storage();
        long max = storage.getMaxMRU();
        return max > 0 && storage.getMRU() * (long) MAX_SETTING >= setting * max;
    }

    /** 重算输出，变了就通知周围的方块 */
    public void update() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean now = computeOutput();
        if (now != powered) {
            powered = now;
            level.blockEntityChanged(worldPosition);
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    /** 当前设定的说明（「红石输出：MRU 达到 50% 时开启」） */
    public Component settingMessage() {
        if (setting == 0) {
            return Component.translatable("message.essentialcraft5.ec_redstone_controller.always");
        }
        if (setting >= MAX_SETTING) {
            return Component.translatable("message.essentialcraft5.ec_redstone_controller.full");
        }
        return Component.translatable("message.essentialcraft5.ec_redstone_controller.above", setting * 10);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_SETTING, setting);
        tag.putBoolean(TAG_POWERED, powered);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        setting = Mth.clamp(tag.getInt(TAG_SETTING), 0, MAX_SETTING);
        powered = tag.getBoolean(TAG_POWERED);
    }
}
