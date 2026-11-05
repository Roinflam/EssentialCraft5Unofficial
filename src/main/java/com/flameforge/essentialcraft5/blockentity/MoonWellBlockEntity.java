package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.MoonWellMenu;
import com.flameforge.essentialcraft5.registry.ECBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * 月亮井。对应原作 TileMoonWell。
 * <p>
 * 夜里、不是新月、正上方看得到天空、没有红石信号时，每 tick 产出 {@code 满月产量 × 月相系数 × 高度系数}：
 * 月相系数 = |1 − 月相 × 0.25|（满月 1、新月 0），高度系数 = 1 − y / 最大高度。
 * <p>
 * 移植改动（都属于「原作 bug 顺便修」）：
 * <ul>
 *   <li>高度系数夹在 [0, 1]，达到最大高度（默认 80）就不产出。原作 y &gt; 最大高度时只把系数置 0、却没乘进产量，
 *       结果 80 格以上反而按满产发电（原作界面也显示满产）；y 正好等于最大高度时才是 0。</li>
 *   <li>1.12 的世界最低 y=0，系数不会超过 1；1.20.1 可以到 y=-64，不夹的话深坑底部会超过原作的上限。</li>
 * </ul>
 * 平衡值固定为配置值（原作在构造时设一次，这里每 tick 设，改配置即时生效）。
 */
public class MoonWellBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.moonwell */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.DoubleValue balance;
        public static ForgeConfigSpec.DoubleValue mruGenerated;
        public static ForgeConfigSpec.IntValue maxHeight;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("moon_well");
            maxMRU = builder.defineInRange("max_mru", ECConfig.GENERATOR_MAX_MRU, 1, Integer.MAX_VALUE);
            balance = builder.defineInRange("balance", 1D, 0D, 2D);
            mruGenerated = builder.comment("MRU per tick at full moon and y=0; scaled down by moon phase and height.",
                            "满月、y=0 时每 tick 的产量，按月相和高度衰减")
                    .defineInRange("max_mru_generated", 60D, 0D, 1_000_000D);
            maxHeight = builder.comment("No MRU at or above this height.", "达到这个高度就不再产出")
                    .defineInRange("max_height", 80, 1, 4096);
            builder.pop();
        }
    }

    /** 这一刻真的产出了 MRU，存满了不算（工作声音和粒子看它，见 {@link #ambientWorking}） */
    private boolean generatedThisTick;

    public MoonWellBlockEntity(BlockPos pos, BlockState state) {
        super(ECBlockEntities.MOON_WELL.get(), pos, state, () -> ECConfig.get(Config.maxMRU), 0);
    }

    @Override
    protected boolean hasBoundGemSlot() {
        return false;
    }

    @Override
    protected void serverTick() {
        mru.setBalance((float) ECConfig.get(Config.balance));
        generatedThisTick = false;
        if (level == null || neighborPowered()) {
            return;
        }
        int generated = (int) potentialGeneration(level, worldPosition);
        if (generated > 0 && canGenerate(level, worldPosition)) {
            generatedThisTick = mru.addMRU(generated, true) > 0;
        }
    }

    /** 不看昼夜和天空、只按月相和高度算的每 tick 产量。界面显示的就是这个（与原作 GuiMRUGenerated 一致）。 */
    public static double potentialGeneration(Level level, BlockPos pos) {
        int moonPhase = level.getMoonPhase();
        double moonFactor = Math.abs(1D - moonPhase * 0.25D);
        int maxHeight = ECConfig.get(Config.maxHeight);
        double heightFactor = pos.getY() >= maxHeight ? 0D : Math.min(1D, 1D - (double) pos.getY() / maxHeight);
        return ECConfig.get(Config.mruGenerated) * moonFactor * heightFactor;
    }

    public static boolean canGenerate(Level level, BlockPos pos) {
        return level.getMoonPhase() != 4 && !level.isDay() && level.canSeeSky(pos.above());
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MoonWellMenu(id, inventory, this);
    }

    @Override
    protected boolean ambientWorking() {
        return generatedThisTick;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return MachineAmbiences.MOON_WELL;
    }
}
