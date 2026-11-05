package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.blockentity.OreProcessing.Status;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.MagmaticAlloyItem;
import com.flameforge.essentialcraft5.menu.MagmaticSmelteryMenu;
import com.flameforge.essentialcraft5.mru.ECCorruption;
import com.flameforge.essentialcraft5.recipe.OreSmeltingRecipe;
import com.flameforge.essentialcraft5.registry.ECMagmatic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 岩浆冶炼炉。对应原作 TileMagmaticSmelter（magmaticSmeltery → magmatic_smeltery）。
 * <p>
 * 两道工序同时跑，互不等待：
 * <ol>
 *   <li><b>熔矿</b>：3 号格的矿石（或粗矿）每 tick 耗 {@code mru_usage_ores}（50）MRU、有 10% 的几率耗 1 mB 岩浆，
 *       做满 {@code ticks_required_ores}（400）tick 出 掉落数 × 2 个岩浆合金到 4 号格（粗矿减半，见 {@link OreSmeltingRecipe#yieldOf}）；
 *       罐里没有岩浆不动工。</li>
 *   <li><b>炼合金</b>：5 号格的岩浆合金每 tick 耗 {@code mru_usage_alloys}（30）MRU，做满 {@code ticks_required_alloys}（40）tick
 *       出 2 个成品到 6 号格。不要岩浆。</li>
 * </ol>
 * 每完成一次（熔一块矿、炼一个合金）出一个魔力炉渣到 7 号格。合金要自己从 4 号格挪到 5 号格（或者用漏斗接）。
 * 岩浆从 1 号格的岩浆桶（任何装着岩浆的容器）倒进罐里，空容器进 2 号格；管道也能往里灌。收到红石信号时整台停机，进度保留。
 *
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li><b>输出放不下就不做</b>：原作只看「+4 / +2 放得下」，实际加 掉落数 × 2 个再截到 64，青金石、红石矿一次能吞掉几十个合金。</li>
 *   <li><b>桶倒得进去才倒</b>：原作罐里不满 8000 就吃一整桶，装不下的岩浆没了；现在罐里放得下整桶（1000 mB）才倒（容器能分次倒的照分次倒）。
 *       空容器要 2 号格放得下才倒（原作要求 2 号格空着，空桶能叠 16 个以后没必要）。</li>
 *   <li><b>罐子只收岩浆、不许抽出</b>：原作把裸 FluidTank 当能力暴露，管道能灌任何流体（灌进水以后永远停机），也能把岩浆抽走。</li>
 *   <li><b>进度存盘</b>：原作两道进度都不存，重进世界从头做。这里连同所属配方一起存，换了矿种（配方）进度清零。</li>
 *   <li>认矿石看「在不在配方的矿石 / 粗矿里」，原作只认物品的第一个矿辞。</li>
 *   <li>冒火粒子在客户端放（原作写在服务端分支里，从来没显示过）；只在工作时冒。</li>
 *   <li>漏斗只能往 3 号格塞有配方的矿石（玩家手放不限，放错了界面上写「没有对应的配方」）。</li>
 * </ul>
 */
public class MagmaticSmelteryBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.magmaticsmeltery */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.IntValue oreUsage;
        public static ForgeConfigSpec.IntValue alloyUsage;
        public static ForgeConfigSpec.IntValue oreTicks;
        public static ForgeConfigSpec.IntValue alloyTicks;
        public static ForgeConfigSpec.BooleanValue generateCorruption;
        public static ForgeConfigSpec.IntValue maxCorruptionGen;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("magmatic_smeltery");
            maxMRU = builder.defineInRange("max_mru", ECConfig.DEVICE_MAX_MRU, 1, Integer.MAX_VALUE);
            oreUsage = builder.comment("MRU used per tick while melting ore into Magmatic Alloy.", "熔矿时每 tick 耗多少 MRU")
                    .defineInRange("mru_usage_ores", 50, 0, Integer.MAX_VALUE);
            alloyUsage = builder.comment("MRU used per tick while smelting Magmatic Alloy into products.", "炼合金时每 tick 耗多少 MRU")
                    .defineInRange("mru_usage_alloys", 30, 0, Integer.MAX_VALUE);
            oreTicks = builder.comment("Ticks to melt one ore.", "熔一块矿要多少 tick")
                    .defineInRange("ticks_required_ores", 400, 1, 1_000_000);
            alloyTicks = builder.comment("Ticks to smelt one Magmatic Alloy.", "炼一个岩浆合金要多少 tick")
                    .defineInRange("ticks_required_alloys", 40, 1, 1_000_000);
            generateCorruption = builder.comment("Whether the smeltery leaks MRU into nearby MRUCUs while working.",
                            "工作时是否向附近的 MRU 聚集体泄漏 MRU")
                    .define("generate_corruption", false);
            maxCorruptionGen = builder.comment("Max MRU leaked per working tick (a random amount from 0 to this).",
                            "每个工作 tick 最多泄漏多少 MRU（在 0 到这个值之间随机）")
                    .defineInRange("max_corruption_gen", 3, 0, 10000);
            builder.pop();
        }
    }

    public static final int SLOT_GEM = 0;
    public static final int SLOT_LAVA_IN = 1;
    public static final int SLOT_LAVA_OUT = 2;
    public static final int SLOT_ORE = 3;
    public static final int SLOT_ALLOY_OUT = 4;
    public static final int SLOT_ALLOY_IN = 5;
    public static final int SLOT_PRODUCT = 6;
    public static final int SLOT_SLAG = 7;
    public static final int SLOTS = 8;

    /** 原作 new FluidTank(8000) */
    public static final int TANK_CAPACITY = 8000;
    /** 熔矿的每个工作 tick 耗 1 mB 岩浆的几率（原作 rand.nextFloat() &lt;= 0.1F，平均一块矿约 40 mB） */
    public static final float LAVA_USE_CHANCE = 0.1F;
    /** 多久没干活算停工（给客户端的冒火粒子用；断断续续缺 MRU 时不来回切） */
    static final int WORKING_LINGER = 20;

    private static final String TAG_TANK = "tank";
    public static final String TAG_ORE_PROGRESS = "ore_progress";
    private static final String TAG_ORE_RECIPE = "ore_recipe";
    public static final String TAG_ALLOY_PROGRESS = "alloy_progress";
    private static final String TAG_ALLOY_RECIPE = "alloy_recipe";
    private static final String TAG_WORKING = "working";

    private final FluidTank tank = new FluidTank(TANK_CAPACITY, MagmaticSmelteryBlockEntity::isLava) {
        @Override
        protected void onContentsChanged() {
            // 只标记区块要存盘（岩浆量走菜单同步，不用发方块更新包）；不像 setChanged 那样通知比较器
            if (level != null && !level.isClientSide) {
                level.blockEntityChanged(worldPosition);
            }
        }
    };
    private final IFluidHandler external = new InsertOnlyLava(tank);
    @Nullable
    private LazyOptional<IFluidHandler> fluidCap;

    private int oreProgress;
    @Nullable
    private ResourceLocation oreRecipe;
    private int alloyProgress;
    @Nullable
    private ResourceLocation alloyRecipe;
    private Status oreStatus = Status.IDLE;
    private Status alloyStatus = Status.IDLE;
    private final OreSmeltingRecipe.Cache oreCache = new OreSmeltingRecipe.Cache();
    private final MagmaticAlloyItem.Cache alloyCache = new MagmaticAlloyItem.Cache();
    private final OreProcessing.Results results = new OreProcessing.Results();

    /**
     * 容器倒不进去时记下当时的容器、罐里的量、2 号格，三样都没变就不再试：
     * 每试一次都要复制容器、新建它的流体能力，罐子满着时桶一直放在格子里会每 tick 分配一堆对象。
     */
    private ItemStack failedContainer = ItemStack.EMPTY;
    private int failedContainerCount = -1;
    private int failedAmount = -1;
    private ItemStack failedOutput = ItemStack.EMPTY;
    private int failedOutputCount = -1;

    private int idleTicks = WORKING_LINGER;
    /** 服务端：上次发给客户端的；客户端：收到的 */
    private boolean working;

    public MagmaticSmelteryBlockEntity(BlockPos pos, BlockState state) {
        super(ECMagmatic.MAGMATIC_SMELTERY_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), SLOTS);
    }

    // ------------------------------------------------------------------
    // 槽位规则（两端共用）
    // ------------------------------------------------------------------

    public static boolean isLava(FluidStack stack) {
        return !stack.isEmpty() && stack.getFluid().isSame(Fluids.LAVA);
    }

    /** 装着岩浆的容器（岩浆桶，或者别的模组装岩浆的物品） */
    public static boolean containsLava(ItemStack stack) {
        return !stack.isEmpty() && FluidUtil.getFluidContained(stack).map(MagmaticSmelteryBlockEntity::isLava).orElse(false);
    }

    /**
     * 0 号绑定宝石；1 号装岩浆的容器；3 号矿石（除了岩浆合金和岩浆桶什么都收，同原作；有没有配方由机器判断）；5 号岩浆合金；
     * 2、4、6、7 号是输出槽，什么都不能放。
     */
    public static boolean slotRule(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_GEM -> defaultSlotRule(slot, stack);
            case SLOT_LAVA_IN -> containsLava(stack);
            case SLOT_ORE -> !stack.is(ECMagmatic.MAGMATIC_ALLOY.get()) && !containsLava(stack);
            case SLOT_ALLOY_IN -> stack.is(ECMagmatic.MAGMATIC_ALLOY.get());
            default -> false;
        };
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    /** 原作 getOutputSlots {2, 4, 6, 7} */
    @Override
    protected boolean isOutputSlot(int slot) {
        return slot == SLOT_LAVA_OUT || slot == SLOT_ALLOY_OUT || slot == SLOT_PRODUCT || slot == SLOT_SLAG;
    }

    /** 漏斗往矿石格只能塞有配方的东西，免得一格杂物把后面的矿石全堵住 */
    @Override
    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        if (slot == SLOT_ORE) {
            return level != null && slotRule(slot, stack) && OreSmeltingRecipe.find(level.getRecipeManager(), stack) != null;
        }
        return super.canAutomationInsert(slot, stack);
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        if (level == null) {
            return;
        }
        // 原作整段逻辑都在 getRedstonePowerFromNeighbors == 0 里：有信号时连桶都不倒，进度保留
        if (neighborPowered()) {
            oreStatus = Status.POWERED;
            alloyStatus = Status.POWERED;
            updateWorking(false);
            return;
        }
        drainContainer();
        boolean worked = meltOre();
        worked |= smeltAlloy();
        updateWorking(worked);
    }

    /** 1 号格的容器往罐里倒：整份倒得进去、空容器 2 号格放得下才倒 */
    private void drainContainer() {
        ItemStack container = inventory.getStackInSlot(SLOT_LAVA_IN);
        if (container.isEmpty() || tank.getSpace() <= 0) {
            return;
        }
        ItemStack output = inventory.getStackInSlot(SLOT_LAVA_OUT);
        if (container == failedContainer && container.getCount() == failedContainerCount && tank.getFluidAmount() == failedAmount
                && output == failedOutput && output.getCount() == failedOutputCount) {
            return;
        }
        // 模拟时 Forge 会真的倒一个副本，拿到的就是倒完以后的容器
        FluidActionResult simulated = FluidUtil.tryEmptyContainer(container, tank, Integer.MAX_VALUE, null, false);
        ItemStack remainder = simulated.getResult();
        boolean ok = simulated.isSuccess() && (remainder.isEmpty() || OreProcessing.fits(inventory, SLOT_LAVA_OUT, remainder));
        // 桶只能整桶倒：罐里放不下 1000 mB 时这一步会失败，不会像原作那样吞掉装不下的部分
        FluidActionResult result = ok ? FluidUtil.tryEmptyContainer(container, tank, Integer.MAX_VALUE, null, true) : FluidActionResult.FAILURE;
        if (!result.isSuccess()) {
            failedContainer = container;
            failedContainerCount = container.getCount();
            failedAmount = tank.getFluidAmount();
            failedOutput = output;
            failedOutputCount = output.getCount();
            return;
        }
        failedContainer = ItemStack.EMPTY;
        inventory.extractItem(SLOT_LAVA_IN, 1, false);
        if (!result.getResult().isEmpty()) {
            OreProcessing.put(inventory, SLOT_LAVA_OUT, result.getResult());
        }
    }

    /** 熔矿，返回这个 tick 有没有干活 */
    private boolean meltOre() {
        ItemStack input = inventory.getStackInSlot(SLOT_ORE);
        if (input.isEmpty()) {
            setOreProgress(0, null);
            oreStatus = Status.IDLE;
            return false;
        }
        OreSmeltingRecipe recipe = oreCache.get(level, input);
        OreSmeltingRecipe.Yield yield = recipe == null ? null : recipe.yieldFor(input, OreSmeltingRecipe.SMELTERY_MULTIPLIER);
        if (recipe == null || yield == null) {
            setOreProgress(0, null);
            oreStatus = Status.NO_RECIPE;
            return false;
        }
        if (!recipe.getId().equals(oreRecipe)) {
            // 换了矿种：旧矿种的进度不能带过来
            setOreProgress(0, recipe.getId());
        }
        if (input.getCount() < yield.consumed()) {
            oreStatus = Status.NEED_MORE;
            return false;
        }
        ItemStack alloys = results.alloys(recipe, yield.alloys());
        if (!OreProcessing.fits(inventory, SLOT_ALLOY_OUT, alloys)) {
            oreStatus = Status.OUTPUT_FULL;
            return false;
        }
        if (tank.getFluidAmount() <= 0) {
            oreStatus = Status.NO_LAVA;
            return false;
        }
        int cost = ECConfig.get(Config.oreUsage);
        if (mru.getMRU() < cost) {
            oreStatus = Status.NO_MRU;
            return false;
        }
        oreStatus = Status.WORKING;
        mru.extractMRU(cost, true);
        if (level.random.nextFloat() <= LAVA_USE_CHANCE) {
            tank.drain(1, IFluidHandler.FluidAction.EXECUTE);
        }
        leak();
        if (oreProgress + 1 >= ECConfig.get(Config.oreTicks)) {
            inventory.extractItem(SLOT_ORE, yield.consumed(), false);
            OreProcessing.put(inventory, SLOT_ALLOY_OUT, alloys);
            OreProcessing.addSlag(inventory, SLOT_SLAG);
            setOreProgress(0, null);
        } else {
            setOreProgress(oreProgress + 1, recipe.getId());
        }
        return true;
    }

    /** 炼合金，返回这个 tick 有没有干活 */
    private boolean smeltAlloy() {
        ItemStack alloy = inventory.getStackInSlot(SLOT_ALLOY_IN);
        if (alloy.isEmpty()) {
            setAlloyProgress(0, null);
            alloyStatus = Status.IDLE;
            return false;
        }
        OreSmeltingRecipe recipe = alloyCache.get(level, alloy);
        ItemStack products = recipe == null ? ItemStack.EMPTY : results.products(recipe);
        if (recipe == null || products.isEmpty()) {
            // 配方被数据包删了（或产物标签是空的）：合金留着，等配方回来
            setAlloyProgress(0, null);
            alloyStatus = Status.NO_RECIPE;
            return false;
        }
        if (!recipe.getId().equals(alloyRecipe)) {
            setAlloyProgress(0, recipe.getId());
        }
        if (!OreProcessing.fits(inventory, SLOT_PRODUCT, products)) {
            alloyStatus = Status.OUTPUT_FULL;
            return false;
        }
        int cost = ECConfig.get(Config.alloyUsage);
        if (mru.getMRU() < cost) {
            alloyStatus = Status.NO_MRU;
            return false;
        }
        alloyStatus = Status.WORKING;
        mru.extractMRU(cost, true);
        leak();
        if (alloyProgress + 1 >= ECConfig.get(Config.alloyTicks)) {
            inventory.extractItem(SLOT_ALLOY_IN, 1, false);
            OreProcessing.put(inventory, SLOT_PRODUCT, products);
            OreProcessing.addSlag(inventory, SLOT_SLAG);
            setAlloyProgress(0, null);
        } else {
            setAlloyProgress(alloyProgress + 1, recipe.getId());
        }
        return true;
    }

    private void leak() {
        if (ECConfig.get(Config.generateCorruption)) {
            ECCorruption.randomIncreaseAt(level, worldPosition, level.random, ECConfig.get(Config.maxCorruptionGen));
        }
    }

    private void setOreProgress(int value, @Nullable ResourceLocation recipe) {
        if (value != oreProgress || !Objects.equals(recipe, oreRecipe)) {
            oreProgress = value;
            oreRecipe = recipe;
            // 耗能为 0 时 MRU 不变，基类不会标记存盘，这里自己标
            level.blockEntityChanged(worldPosition);
        }
    }

    private void setAlloyProgress(int value, @Nullable ResourceLocation recipe) {
        if (value != alloyProgress || !Objects.equals(recipe, alloyRecipe)) {
            alloyProgress = value;
            alloyRecipe = recipe;
            level.blockEntityChanged(worldPosition);
        }
    }

    /** 工作状态变了才发一次方块更新包（客户端据此冒火）；停工要连续 {@link #WORKING_LINGER} tick 才算 */
    private void updateWorking(boolean worked) {
        idleTicks = worked ? 0 : Math.min(idleTicks + 1, WORKING_LINGER);
        boolean now = idleTicks < WORKING_LINGER;
        if (now != working) {
            working = now;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** 工作时从底座周围往外冒火（原作的粒子位置：中心 ± 0.45 格，贴着底面） */
    @Override
    protected void clientTick() {
        if (level == null || !working) {
            return;
        }
        RandomSource random = level.random;
        if (random.nextInt(3) != 0) {
            return;
        }
        double x = worldPosition.getX() + 0.5 + (random.nextDouble() * 2 - 1) / 2.2;
        double z = worldPosition.getZ() + 0.5 + (random.nextDouble() * 2 - 1) / 2.2;
        level.addParticle(ParticleTypes.FLAME, x, worldPosition.getY() + 0.05, z, 0, 0.01, 0);
    }

    // ------------------------------------------------------------------
    // 访问（界面数值、GameTest）
    // ------------------------------------------------------------------

    /** 内部的罐子（机器自己和 GameTest 用；对外的能力是只进不出的包装） */
    public FluidTank getTank() {
        return tank;
    }

    public int getLavaAmount() {
        return tank.getFluidAmount();
    }

    public int getOreProgress() {
        return oreProgress;
    }

    public int getAlloyProgress() {
        return alloyProgress;
    }

    public Status getOreStatus() {
        return oreStatus;
    }

    public Status getAlloyStatus() {
        return alloyStatus;
    }

    public boolean isWorking() {
        return working;
    }

    public static int oreTicks() {
        return ECConfig.get(Config.oreTicks);
    }

    public static int alloyTicks() {
        return ECConfig.get(Config.alloyTicks);
    }

    public static int oreCost() {
        return ECConfig.get(Config.oreUsage);
    }

    public static int alloyCost() {
        return ECConfig.get(Config.alloyUsage);
    }

    // ------------------------------------------------------------------
    // 能力
    // ------------------------------------------------------------------

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER && !isRemoved()) {
            // 用到才建；失效后清空、下次再建（方块实体被移走又放回时还能用）
            if (fluidCap == null) {
                fluidCap = LazyOptional.of(() -> external);
            }
            return fluidCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        if (fluidCap != null) {
            fluidCap.invalidate();
            fluidCap = null;
        }
    }

    /** 对外的流体能力：只收岩浆（罐子本身的校验）、什么都抽不出来 */
    private record InsertOnlyLava(FluidTank tank) implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int index) {
            return tank.getFluid().copy();
        }

        @Override
        public int getTankCapacity(int index) {
            return tank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int index, @NotNull FluidStack stack) {
            return tank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return tank.fill(resource, action);
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    // ------------------------------------------------------------------
    // 存盘与同步
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_TANK, tank.writeToNBT(new CompoundTag()));
        if (oreRecipe != null && oreProgress > 0) {
            tag.putInt(TAG_ORE_PROGRESS, oreProgress);
            tag.putString(TAG_ORE_RECIPE, oreRecipe.toString());
        }
        if (alloyRecipe != null && alloyProgress > 0) {
            tag.putInt(TAG_ALLOY_PROGRESS, alloyProgress);
            tag.putString(TAG_ALLOY_RECIPE, alloyRecipe.toString());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tank.readFromNBT(tag.getCompound(TAG_TANK));
        if (!tank.isEmpty() && !isLava(tank.getFluid())) {
            tank.setFluid(FluidStack.EMPTY);
        }
        // 配方 ID 读回来先不解析：第一次 tick 选出的配方还是它，进度就接着算；换了（数据包删改）就清零
        oreRecipe = readId(tag, TAG_ORE_RECIPE);
        oreProgress = oreRecipe != null ? Math.max(0, tag.getInt(TAG_ORE_PROGRESS)) : 0;
        alloyRecipe = readId(tag, TAG_ALLOY_RECIPE);
        alloyProgress = alloyRecipe != null ? Math.max(0, tag.getInt(TAG_ALLOY_PROGRESS)) : 0;
        // 只有发给客户端的更新包里有这个键（不存盘）
        if (tag.contains(TAG_WORKING, Tag.TAG_BYTE)) {
            working = tag.getBoolean(TAG_WORKING);
        }
    }

    @Nullable
    private static ResourceLocation readId(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString(key)) : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean(TAG_WORKING, working);
        return tag;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MagmaticSmelteryMenu(id, inventory, this);
    }

    @Override
    protected boolean ambientWorking() {
        return working;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return MachineAmbiences.MAGMATIC_SMELTERY;
    }
}
