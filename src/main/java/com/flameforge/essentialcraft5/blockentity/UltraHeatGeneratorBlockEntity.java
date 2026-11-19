package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.AmbienceParticles;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.UltraHeatGeneratorMenu;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECUltraGenerators;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.Nullable;

/**
 * 终极热力产能器。对应原作 TileUltraHeatGenerator（方块 BlockUltraHeatGenerator，注册名 ultraHeatGen；界面数值见原作 GuiMRUGenerated 的
 * "ultraHeatGenerator" 分支）。
 * <p>
 * 像热力产能器一样烧原版燃料，但多了一个只升不降的<b>温度</b>：燃烧期间每刻
 * <ol>
 *   <li>按东南西北相隔一格的四个方块算热源系数 mf（规则和热力产能器同一份：{@link HeatGeneratorBlockEntity#heatFactor}）；</li>
 *   <li>按当前温度算升温倍率 s 和这一刻烧掉的燃烧时间（{@link #step}）：
 *       温度 &lt; 1000 时 s = 0.1 + 0.0009 × 温度、烧掉 25 + (s − 0.1) × 80 / 3；1000 ~ 10000 时 s = 1、烧掉 1；
 *       &gt; 10000 时 s = 0.001 + 9990 / 温度、烧掉 1；</li>
 *   <li>温度 += mf × s；</li>
 *   <li>按升温后的温度出 MRU：&lt; 1000 时 温度 × 0.01，1000 ~ 10000 时 2 + 温度 × 0.008，&gt; 10000 时 72 + 温度 × 0.001，
 *       不到 1 不出、取整（产量里没有 mf：热源只管升温快慢，热起来以后四周拆空了也照样产，只是不再升温）。</li>
 * </ol>
 * 刚点火的一段时间几乎不出 MRU、燃料烧得很快（四面岩浆烧煤：第 876 刻开始出 1 MRU/t，约 65 块煤热到 1000 度）。
 * 温度存在方块实体里，拆掉就归零。每开始烧一个燃料，副产物槽多 1 个魔力炉渣（满了就不出，燃料照烧）；
 * 存满了不再点新燃料，正在烧的照常烧完（温度照升，多出来的 MRU 丢掉）；有红石信号时整台停住（温度、燃烧时间都不变，也不冒火）。
 * <p>
 * 温度照原作不封顶（原作研究书把「热度没有上限」当卖点写出来了）；配置 {@code max_heat} 大于 0 时按它封顶
 * （只截参与计算的温度，见 {@link #getEffectiveHeat}，存着的不削，上限调回去温度原样回来）。
 * 只读四格方块状态、只在自己格子里烧燃料，不碰世界里的任何东西，所以不用主人规则。
 * <p>
 * 与原作不同之处：
 * <ul>
 *   <li>燃烧时间扣成负数时（原作 int 截断后可能小于 0，界面火苗溢出）归 0，再按原作的条件续燃料。</li>
 *   <li>岩浆桶这类有容器的燃料还一个空桶（原作判断写在扣数量之前、永远不成立，桶直接没了）；燃料槽只收能烧的东西，
 *       剩在里面的空桶对外算输出槽（同热力产能器）。</li>
 *   <li>容量 10 万做成真生效的配置（原作构造器写死通用产能器的 10 倍，配置项 MaxMRU 读了没用）；平衡值范围 [−1, 2]
 *       （原作把 setMaxValue 笔误成第二次 setMinValue）；平衡值放下时随机一次并存盘（见 {@link RandomBalanceBlockEntity}）。</li>
 *   <li>读档时温度不是有限数（NaN、无穷）或小于 0 的归 0（原作不校验：小于 −1041 时燃料越烧越多，NaN 时卡死；只有改 NBT 才会出现）。</li>
 *   <li>界面上的产量和温度由服务端算好随菜单同步（原作界面自己按另一套式子算：中段按温度 ÷ 124 少报约 2，高段按 80 + 温度 ÷ 1000 多报 8，
 *       还每帧读四格方块）。</li>
 *   <li>粒子只在燃烧时冒、换成原版粒子、每刻 1~2 团（原作不管烧不烧每刻 13 团）。</li>
 * </ul>
 */
public class UltraHeatGeneratorBlockEntity extends RandomBalanceBlockEntity {

    /** 原作 tileentities.ultraheatgenerator */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.DoubleValue balance;
        public static ForgeConfigSpec.IntValue maxHeat;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("ultra_heat_generator");
            maxMRU = builder.comment("MRU capacity (the original hard-coded 10 times the generic generator capacity).",
                            "MRU 容量（原作写死通用产能器的 10 倍）")
                    .defineInRange("max_mru", ECConfig.GENERATOR_MAX_MRU * 10, 1, Integer.MAX_VALUE);
            balance = builder.comment("Balance of the MRU produced. -1 = random in [0, 2), rolled once when the generator is placed.",
                            "产出 MRU 的平衡值。-1 表示放下时在 [0, 2) 里随机一次（冰冻、混沌 MRU 的来源，别轻易改）")
                    .defineInRange("balance", RANDOM, -1D, 2D);
            maxHeat = builder.comment("Highest heat (degrees) the generator can reach. 0 = no limit, as in the original.",
                            "Lowering it caps output at once; stored heat is kept and comes back if the limit is raised.",
                            "温度上限（度）；0 表示不封顶（同原作）。调低后产量立刻按上限算，存着的温度不丢，上限调回去就恢复")
                    .defineInRange("max_heat", 0, 0, Integer.MAX_VALUE);
            builder.pop();
        }
    }

    public static final int SLOT_FUEL = 0;
    public static final int SLOT_SLAG = 1;

    private static final String TAG_BURN = "burn";
    private static final String TAG_BURN_MAX = "burn_max";
    private static final String TAG_HEAT = "heat";

    private int burnTime;
    private int maxBurnTime;
    private double heat;
    /** 当前温度下每刻的产量（不管在不在烧），每刻重算，给界面看 */
    private int generation;
    /** 上次告诉客户端的「是否在烧」（客户端靠它决定冒不冒火） */
    private boolean syncedBurning;
    /** GameTest 用的温度上限，不存盘；null 时读配置 */
    @Nullable
    private Integer maxHeatOverride;

    public UltraHeatGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ECUltraGenerators.ULTRA_HEAT_GENERATOR_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), 2);
    }

    // ---- 公式（纯函数，GameTest 直接测） ----

    /**
     * 燃烧中的一刻算出来的结果。
     *
     * @param heat       升温后的温度
     * @param burnCost   这一刻要烧掉的燃烧时间（温度 &lt; 1000 时带小数）
     * @param generation 这一刻的产量（不到 1 为 0）
     */
    public record Step(double heat, double burnCost, int generation) {

        /**
         * 扣掉这一刻的燃烧时间。原作写的是 int 字段的复合赋值 {@code currentBurnTime -= cost}，
         * 等于 {@code (int) (burnTime - cost)}：向零截断，带小数时实际多扣一刻（1600 − 48.976 → 1551）。
         */
        public int burnAfter(int burnTime) {
            return (int) (burnTime - burnCost);
        }
    }

    /**
     * 燃烧中的一刻（原作 update 里 currentBurnTime &gt; 0 那一段，运算顺序照抄）。
     *
     * @param heat       这一刻开始时的温度
     * @param heatFactor 四周热源系数连乘
     * @param maxHeat    温度上限，≤ 0 表示不封顶
     */
    public static Step step(double heat, double heatFactor, double maxHeat) {
        double scale;
        double cost;
        if (heat < 1000D) {
            scale = 0.1D + heat * 0.0009D;
            cost = (scale - 0.1D) * 80 / 3 + 25;
        } else if (heat > 10000D) {
            scale = 0.001D + 9990 / heat;
            cost = 1D;
        } else {
            scale = 1D;
            cost = 1D;
        }
        double next = heat + heatFactor * scale;
        if (maxHeat > 0D && next > maxHeat) {
            next = maxHeat;
        }
        return new Step(next, cost, generation(next));
    }

    /** 这个温度下每刻的产量：算出来不到 1 的为 0，其余向下取整 */
    public static int generation(double heat) {
        double generated;
        if (heat < 1000D) {
            generated = heat * 0.01D;
        } else if (heat > 10000D) {
            generated = 72D + heat * 0.001D;
        } else {
            generated = 2D + heat * 0.008D;
        }
        return generated >= 1D ? (int) generated : 0;
    }

    // ---- 槽位 ----

    /** 槽位规则（两端共用）：0 号只收燃料，1 号是副产物输出，不能放 */
    public static boolean slotRule(int slot, ItemStack stack) {
        return slot == SLOT_FUEL && HeatGeneratorBlockEntity.isFuel(stack);
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
        return slot == SLOT_FUEL && !fuel.isEmpty() && !HeatGeneratorBlockEntity.isFuel(fuel);
    }

    // ---- tick ----

    /** 现在的温度上限，≤ 0 表示不封顶 */
    public int maxHeat() {
        return maxHeatOverride != null ? maxHeatOverride : ECConfig.get(Config.maxHeat);
    }

    /**
     * 参与计算的温度（升温、耗燃料、产量、界面显示都用它）：配置了上限时按上限截住，存着的值不改。
     * 上限调低再调回去，温度原样回来，不会因为服主试配置就丢掉玩家烧了几天的积累。
     */
    public double getEffectiveHeat() {
        return effectiveHeat(maxHeat());
    }

    private double effectiveHeat(int cap) {
        return cap > 0 ? Math.min(heat, cap) : heat;
    }

    @Override
    protected void serverTick() {
        applyBalance(ECConfig.get(Config.balance));
        if (level == null) {
            return;
        }
        int cap = maxHeat();
        if (!neighborPowered()) {
            if (burnTime > 0) {
                // 上限调低以后存值可能高过上限：按上限算这一刻，存值不动（上限调回去温度原样回来）
                Step step = step(effectiveHeat(cap), HeatGeneratorBlockEntity.heatFactor(level, worldPosition), cap);
                if (cap <= 0 || heat <= cap) {
                    heat = step.heat();
                }
                burnTime = Math.max(0, step.burnAfter(burnTime));
                if (step.generation() >= 1) {
                    mru.addMRU(step.generation(), true);
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
        generation = generation(effectiveHeat(cap));
        boolean burning = burnTime > 0;
        if (burning != syncedBurning) {
            syncedBurning = burning;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** 每烧一个燃料出一个魔力炉渣；槽满了（或者被别的东西占着）就不出，燃料照烧（同原作，写法同热力产能器） */
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

    /** 扣一个燃料；有容器的（岩浆桶）把容器还回燃料槽，放不下就掉在机器上面（同热力产能器） */
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

    // ---- 界面数值与测试入口 ----

    public int getBurnTime() {
        return burnTime;
    }

    public int getMaxBurnTime() {
        return maxBurnTime;
    }

    /** 存着的温度（上限调低时可能高过上限，见 {@link #getEffectiveHeat}） */
    public double getHeat() {
        return heat;
    }

    /** 界面上显示的温度：参与计算的那个（向下取整，超出 int 的按 int 上限显示） */
    public int getDisplayHeat() {
        return (int) getEffectiveHeat();
    }

    public int getGeneration() {
        return generation;
    }

    /** 直接设温度（GameTest、展示厅用）：非有限数、负数按 0 */
    public void setHeatForTest(double value) {
        heat = sanitizeHeat(value);
        generation = generation(getEffectiveHeat());
        setChanged();
    }

    /** 这一台的温度上限覆盖（GameTest 用，不存盘）；null 恢复读配置 */
    public void setMaxHeatForTest(@Nullable Integer value) {
        maxHeatOverride = value;
    }

    private static double sanitizeHeat(double value) {
        return Double.isFinite(value) && value > 0D ? value : 0D;
    }

    // ---- 客户端 ----

    /** 该不该冒火：在烧、而且没有红石信号（两端都能算，GameTest 直接测） */
    public boolean showsFlames() {
        return level != null && burnTime > 0 && !neighborPowered();
    }

    /**
     * 燃烧时冒火（有红石信号时停机，不冒）：每刻在机器四个上角之一冒一团往上飘的火（原作四个角每刻各一团，位置照抄），
     * 四分之一的机会再在中间冒一团。原作不管烧不烧，每刻还在四个侧面各放两团 EC 自己的火焰粒子，这里去掉。
     * <p>
     * 红石要在客户端自己判断：服务端有信号时把燃烧时间冻住，「在烧」这个状态不翻转就不发更新，客户端的 burnTime 会一直大于 0
     * （原作的粒子同样写在没有红石的分支里）。比较器供电时客户端拿不到输出强度，看不出有信号，这点同终极自然熔炉和原作。
     */
    @Override
    protected void clientTick() {
        if (!showsFlames()) {
            return;
        }
        RandomSource random = level.random;
        double x = worldPosition.getX();
        double y = worldPosition.getY();
        double z = worldPosition.getZ();
        double cornerX = random.nextBoolean() ? 0.05D : 0.95D;
        double cornerZ = random.nextBoolean() ? 0.05D : 0.95D;
        AmbienceParticles.add(level, ParticleTypes.FLAME, x + cornerX, y + 1.2D, z + cornerZ, 0, 0.01D, 0);
        if (random.nextInt(4) == 0) {
            AmbienceParticles.add(level, ParticleTypes.FLAME, x + 0.5D, y + 0.5D, z + 0.5D, 0, 0.1D, 0);
        }
    }

    // ---- 存盘 ----

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_BURN, burnTime);
        tag.putInt(TAG_BURN_MAX, maxBurnTime);
        tag.putDouble(TAG_HEAT, heat);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        burnTime = Math.max(0, tag.getInt(TAG_BURN));
        maxBurnTime = Math.max(0, tag.getInt(TAG_BURN_MAX));
        // 高过上限的照原样读回，不削（只在计算时截，见 getEffectiveHeat）
        heat = sanitizeHeat(tag.getDouble(TAG_HEAT));
        generation = generation(getEffectiveHeat());
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new UltraHeatGeneratorMenu(id, inventory, this);
    }
}
