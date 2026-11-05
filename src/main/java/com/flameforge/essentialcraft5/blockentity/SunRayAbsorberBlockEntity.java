package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.SunRayAbsorberMenu;
import com.flameforge.essentialcraft5.registry.ECSolar;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * 日光吸收器。对应原作 TileSunRayAbsorber。
 * <p>
 * 被太阳棱镜的光束照到的那些刻里（一道光束 20 刻，见 {@link SolarPrismBlockEntity}），没有红石信号时每刻产出 500 MRU。
 * 平衡值固定为配置值（默认 2.0，混沌），容量 10 万（原作 GENERATOR_MAX_MRU_GENERIC × 10）。没有物品格。
 * <p>
 * 移植改动：
 * <ul>
 *   <li>原作每刻在自己周围 3×3×3 里查有没有光束实体；这里由棱镜直接调 {@link #illuminate}，吸收器只记「还要亮几刻」。
 *       几道光束重叠时取较长的那段，不叠加（同原作：只看范围里有没有光束）。</li>
 *   <li>配置 Balance 原作用 getInt() 读（1.5 会变成 1），这里是 double。平衡值每刻按配置设，改配置即时生效（同月亮井）。</li>
 *   <li>界面上的产量从服务端配置同步（原作读客户端自己的配置）。</li>
 * </ul>
 */
public class SunRayAbsorberBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.sunrayabsorber */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.DoubleValue balance;
        public static ForgeConfigSpec.IntValue mruGenerated;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("sun_ray_absorber");
            maxMRU = builder.defineInRange("max_mru", ECConfig.GENERATOR_MAX_MRU * 10, 1, Integer.MAX_VALUE);
            balance = builder.comment("Balance of the MRU produced (2.0 = chaos).", "产出 MRU 的平衡值（2.0 为混沌）")
                    .defineInRange("balance", 2D, 0D, 2D);
            mruGenerated = builder.comment("MRU per tick while a solar beam hits the absorber.", "被太阳光束照到时每刻的产量")
                    .defineInRange("mru_generated", 500, 0, 1_000_000);
            builder.pop();
        }
    }

    private static final String TAG_LIT = "lit_ticks";

    /** 还会被光照多少刻 */
    private int litTicks;

    /** 这一刻真的产出了 MRU，存满了不算（工作声音和粒子看它，见 {@link #ambientWorking}） */
    private boolean generatedThisTick;

    public SunRayAbsorberBlockEntity(BlockPos pos, BlockState state) {
        super(ECSolar.SUN_RAY_ABSORBER_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), 0);
    }

    @Override
    protected boolean hasBoundGemSlot() {
        return false;
    }

    @Override
    protected void serverTick() {
        mru.setBalance((float) ECConfig.get(Config.balance));
        generatedThisTick = false;
        if (level == null || litTicks <= 0) {
            return;
        }
        litTicks--;
        if (!neighborPowered()) {
            generatedThisTick = mru.addMRU(ECConfig.get(Config.mruGenerated), true) > 0;
        }
        if (litTicks == 0) {
            level.blockEntityChanged(worldPosition);
        }
    }

    /**
     * 太阳棱镜的光束落到这里：接下来 ticks 刻都算被照着。已经被照着时取较长的那段（不叠加）。
     * 由棱镜在服务端调用（棱镜只通过 ECLevels.loadedBlockEntity 找到已加载的吸收器）。
     */
    public void illuminate(int ticks) {
        if (ticks > litTicks) {
            litTicks = ticks;
            if (level != null && !level.isClientSide) {
                level.blockEntityChanged(worldPosition);
            }
        }
    }

    public boolean isLit() {
        return litTicks > 0;
    }

    public int getLitTicks() {
        return litTicks;
    }

    /** 被照到时每刻的产量（界面显示用，同原作 GuiMRUGenerated 的 "sunray" 分支） */
    public int getGeneration() {
        return ECConfig.get(Config.mruGenerated);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_LIT, litTicks);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // 一道光束最多 20 刻，坏数据不能让它一直亮着
        litTicks = Math.max(0, Math.min(SolarPrismBlockEntity.BEAM_DURATION, tag.getInt(TAG_LIT)));
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SunRayAbsorberMenu(id, inventory, this);
    }

    @Override
    protected boolean ambientWorking() {
        return generatedThisTick;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return MachineAmbiences.SUN_RAY;
    }
}
