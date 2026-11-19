package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.AmbienceParticles;
import com.flameforge.essentialcraft5.api.IHotBlock;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.HeatGeneratorMenu;
import com.flameforge.essentialcraft5.registry.ECGenerators;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 热力产能器。对应原作 TileHeatGenerator（界面数值见原作 GuiMRUGenerated 的 "heatGenerator" 分支）。
 * <p>
 * 烧原版燃料，燃烧期间每 tick 产出 {@code 基础产量 × 东南西北相隔一格的四个方块的系数连乘}：
 * 空气 0、下界岩 0.75、岩浆 0.95、火 0.7、实现了 {@link IHotBlock} 的按接口、其它 0.5。
 * 所以四面都得放东西，任何一面是空气就不产出。每开始烧一个燃料，副产物槽多 1 个魔力炉渣（满 64 个就不再加，燃料照烧）。
 * 满了就不再续燃料，正在烧的那个照常烧完；有红石信号时整台停住（燃烧时间也不减）。
 * <p>
 * 移植改动：
 * <ul>
 *   <li>平衡值：放下时随机一次并存盘（原作每次加载都重新随机，见 {@link RandomBalanceBlockEntity}）。</li>
 *   <li>岩浆桶这类有容器的燃料：原作判断写在扣数量之前、永远不成立，桶直接没了；这里照原版熔炉还一个空桶。
 *       空桶留在燃料槽里，漏斗、管道可以把它抽走（燃料槽里放的不是燃料时对外算输出槽，同原版熔炉）。</li>
 *   <li>燃料槽只收能烧的东西（原作什么都能放，放了不能烧的就卡住）。</li>
 *   <li>1.12 → 1.20：空气含洞穴空气；岩浆不分流动与否（1.12 的 Blocks.LAVA 也包括停下来的流动岩浆）；火含灵魂火。</li>
 *   <li>界面上的产量改由服务端算好随菜单同步（原作在客户端现读周围方块）。</li>
 * </ul>
 */
public class HeatGeneratorBlockEntity extends RandomBalanceBlockEntity {

    /** 原作 tileentities.heatgenerator */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.DoubleValue balance;
        public static ForgeConfigSpec.IntValue mruGenerated;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("heat_generator");
            maxMRU = builder.defineInRange("max_mru", ECConfig.GENERATOR_MAX_MRU, 1, Integer.MAX_VALUE);
            balance = builder.comment("Balance of the MRU produced. -1 = random in [0, 2), rolled once when the generator is placed.",
                            "产出 MRU 的平衡值。-1 表示放下时在 [0, 2) 里随机一次（冰冻、混沌 MRU 的来源，别轻易改）")
                    .defineInRange("balance", RANDOM, -1D, 2D);
            mruGenerated = builder.comment("MRU per tick while burning, before the heat factors of the four surrounding blocks.",
                            "燃烧时每 tick 的基础产量，再乘四周方块的热力系数")
                    .defineInRange("mru_generated", 20, 0, 1_000_000);
            builder.pop();
        }
    }

    public static final int SLOT_FUEL = 0;
    public static final int SLOT_SLAG = 1;

    private static final String TAG_BURN = "burn";
    private static final String TAG_BURN_MAX = "burn_max";

    private int burnTime;
    private int maxBurnTime;
    /** 当前四周方块下每 tick 的产量（不管在不在烧），每 tick 重算，给界面看 */
    private int generation;
    /** 上次告诉客户端的「是否在烧」（客户端靠它决定冒不冒火） */
    private boolean syncedBurning;

    public HeatGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ECGenerators.HEAT_GENERATOR_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), 2);
    }

    /** 槽位规则（两端共用）：0 号只收燃料，1 号是副产物输出，不能放 */
    public static boolean slotRule(int slot, ItemStack stack) {
        return slot == SLOT_FUEL && isFuel(stack);
    }

    public static boolean isFuel(ItemStack stack) {
        return ForgeHooks.getBurnTime(stack, null) > 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    @Override
    protected boolean hasBoundGemSlot() {
        return false;
    }

    /** 副产物槽对外可取；燃料槽里剩下的空桶之类（不是燃料）也可取 */
    @Override
    protected boolean isOutputSlot(int slot) {
        if (slot == SLOT_SLAG) {
            return true;
        }
        ItemStack fuel = inventory.getStackInSlot(SLOT_FUEL);
        return slot == SLOT_FUEL && !fuel.isEmpty() && !isFuel(fuel);
    }

    @Override
    protected void serverTick() {
        applyBalance(ECConfig.get(Config.balance));
        if (level == null) {
            return;
        }
        // 系数连乘是 0.95、0.7 这种二进制除不尽的小数，改了基础产量时会差一点点（160 × 0.5 × 0.5 × 0.7 × 0.75 算出 20.999…），取整前补一个极小量
        generation = (int) (ECConfig.get(Config.mruGenerated) * heatFactor(level, worldPosition) + 1e-6);
        if (!neighborPowered()) {
            if (burnTime > 0) {
                burnTime--;
                if (generation >= 1) {
                    mru.addMRU(generation, true);
                }
                level.blockEntityChanged(worldPosition);
            }
            ItemStack fuel = inventory.getStackInSlot(SLOT_FUEL);
            if (burnTime == 0 && !fuel.isEmpty() && mru.getMRU() < mru.getMaxMRU()) {
                int time = ForgeHooks.getBurnTime(fuel, null);
                if (time > 0) {
                    burnTime = time;
                    maxBurnTime = time;
                    addSlag();
                    consumeFuel(fuel);
                }
            }
        }
        boolean burning = burnTime > 0;
        if (burning != syncedBurning) {
            syncedBurning = burning;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** 每烧一个燃料出一个魔力炉渣；槽满了（或者被别的东西占着）就不出，燃料照烧（同原作） */
    private void addSlag() {
        Item slag = ECMaterials.MAGICAL_SLAG.get();
        ItemStack out = inventory.getStackInSlot(SLOT_SLAG);
        if (out.isEmpty()) {
            inventory.setStackInSlot(SLOT_SLAG, new ItemStack(slag));
        } else if (out.is(slag) && out.getCount() < Math.min(out.getMaxStackSize(), inventory.getSlotLimit(SLOT_SLAG))) {
            // 不能用 insertItem：它按槽位规则判断，而 1 号槽不接受任何东西
            inventory.setStackInSlot(SLOT_SLAG, out.copyWithCount(out.getCount() + 1));
        }
    }

    /** 扣一个燃料；有容器的（岩浆桶）把容器还回燃料槽，放不下就掉在机器上面 */
    private void consumeFuel(ItemStack fuel) {
        ItemStack remainder = fuel.getCraftingRemainingItem();
        inventory.extractItem(SLOT_FUEL, 1, false);
        if (remainder.isEmpty() || level == null) {
            return;
        }
        if (inventory.getStackInSlot(SLOT_FUEL).isEmpty()) {
            inventory.setStackInSlot(SLOT_FUEL, remainder);
        } else {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, remainder);
        }
    }

    /** 东南西北相隔一格的四个方块的热力系数连乘（原作 TileHeatGenerator.update 与 GuiMRUGenerated 里的同一段） */
    public static double heatFactor(Level level, BlockPos pos) {
        double factor = 1D;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos at = pos.relative(direction, 2);
            factor *= heatModifier(level, at, level.getBlockState(at));
            if (factor == 0D) {
                break;
            }
        }
        return factor;
    }

    public static double heatModifier(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return 0D;
        }
        if (state.is(Blocks.NETHERRACK)) {
            return 0.75D;
        }
        if (state.is(Blocks.LAVA)) {
            return 0.95D;
        }
        if (state.is(BlockTags.FIRE)) {
            return 0.7D;
        }
        if (state.getBlock() instanceof IHotBlock hot) {
            return hot.getHeatModifier(level, pos, state);
        }
        return 0.5D;
    }

    // ---- 界面数值 ----

    public int getBurnTime() {
        return burnTime;
    }

    public int getMaxBurnTime() {
        return maxBurnTime;
    }

    public int getGeneration() {
        return generation;
    }

    // ---- 客户端 ----

    /**
     * 燃烧时冒火。原作四个侧面中间各一团 EC 自己的火焰粒子、中间一团往上飘的火、顶上一团烟，每 tick 都放，
     * 而且不管在不在烧；这里只在烧的时候放，换成原版粒子，量减到每 tick 一团火。
     */
    @Override
    protected void clientTick() {
        if (level == null || burnTime <= 0) {
            return;
        }
        RandomSource random = level.random;
        double x = worldPosition.getX();
        double y = worldPosition.getY();
        double z = worldPosition.getZ();
        Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        // 位置照抄原作：x + 0.725 + (偏移 - 0.5) / 2.2，落在各侧面中间
        AmbienceParticles.add(level, ParticleTypes.FLAME, x + 0.725 + (side.getStepX() - 0.5) / 2.2, y + 0.4,
                z + 0.725 + (side.getStepZ() - 0.5) / 2.2, 0, 0, 0);
        if (random.nextInt(4) == 0) {
            AmbienceParticles.add(level, ParticleTypes.FLAME, x + 0.5 + (random.nextFloat() - random.nextFloat()) * 0.2, y + 0.65,
                    z + 0.5 + (random.nextFloat() - random.nextFloat()) * 0.2, 0, 0.01, 0);
        }
        if (random.nextInt(4) == 0) {
            AmbienceParticles.add(level, ParticleTypes.SMOKE, x + 0.5 + (random.nextFloat() - random.nextFloat()) * 0.05, y + 0.8,
                    z + 0.5 + (random.nextFloat() - random.nextFloat()) * 0.05, 0, 0, 0);
        }
    }

    // ---- 存盘 ----

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_BURN, burnTime);
        tag.putInt(TAG_BURN_MAX, maxBurnTime);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        burnTime = Math.max(0, tag.getInt(TAG_BURN));
        maxBurnTime = Math.max(0, tag.getInt(TAG_BURN_MAX));
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new HeatGeneratorMenu(id, inventory, this);
    }
}
