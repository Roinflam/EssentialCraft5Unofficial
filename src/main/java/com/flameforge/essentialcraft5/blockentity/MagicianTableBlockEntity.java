package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.api.MagicianTableUpgrades;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.MagicianTableMenu;
import com.flameforge.essentialcraft5.mru.ECCorruption;
import com.flameforge.essentialcraft5.recipe.MagicianTableRecipe;
import com.flameforge.essentialcraft5.recipe.PositionalInsertRule;
import com.flameforge.essentialcraft5.registry.ECMagicianTable;
import com.flameforge.essentialcraft5.registry.ECRecipes;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * 魔力工作台（原作 magicianTable，任务文档里叫「魔术师台」）。对应原作 TileMagicianTable。
 * <p>
 * 槽位：0 绑定宝石；1 中心、2 左上、3 右上、4 左下、5 右下（五格按位置对应配方原料，见 {@link MagicianTableRecipe}）；6 输出。
 * 没有红石信号时：输入匹配到配方、产物放得进输出槽，就每 tick 进度 += 速度系数、耗 {@code mru_usage_modifier × 速度系数} MRU，
 * 进度达到配方的 MRU 数就出一份产物，五个输入槽各扣 1（同调升阶的四角按配方要的数量扣，见 {@link #craft}）。速度系数来自装着的升级（{@link MagicianTableUpgrades}，没装为 1）。
 * 工作的每一 tick 都向周围泄漏 MRU（{@link ECCorruption}）。
 * <p>
 * 移植改动（原作 bug 顺便修）：
 * <ul>
 *   <li>配方只在输入变化（物品栏变动，见 {@link #setChanged}）或数据包重载后才重新查。原作每 tick 把全部配方挨个比一遍。</li>
 *   <li>做到一半输入换成了另一个配方：进度清零、从头做新配方。原作不清进度，满了按旧配方出货、却扣新配方的原料，
 *       可以先放贵原料起个头、再换成便宜原料白拿贵产物。</li>
 *   <li>正在做的配方记注册名存盘，读档后原样接着做。原作只存进度和总量，读档后靠「总量相同」猜是哪个配方，
 *       猜不中时（换了输入、数据包改了数值）进度既不清零也不前进，工作台卡死。</li>
 *   <li>MRU 消耗的小数部分累计着扣（{@link #mruDebt}）。原作每 tick 扣 (int)(系数)，元素核心（×1.2）实际每 tick 只扣 1，
 *       末影鳞合金（×2.5）只扣 2，等于白打折；配置的消耗倍率小于 1 时干脆不耗 MRU。</li>
 *   <li>输出槽不能放东西（漏斗、玩家都不行），输出合并按物品 + NBT 比较。原作输出槽能塞任意物品把工作台堵住，
 *       合并时只比物品和耐久、合并后丢掉 NBT。</li>
 *   <li>升级存物品本身（原作存升级列表的下标），方块被挖掉时和物品栏一起掉出来。</li>
 *   <li>漏斗、管道按配方表把原料放到对的位置、几格之间均摊（见 {@link #canAutomationInsert}），不会再把原料塞错格把工作台卡死。
 *       还记下上一次出产物用的配方（存盘），同一件东西在不同配方里位置不同时照它放。</li>
 * </ul>
 */
public class MagicianTableBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.magiciantable */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.DoubleValue mruUsage;
        public static ForgeConfigSpec.BooleanValue generatesCorruption;
        public static ForgeConfigSpec.IntValue maxCorruptionGen;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("magician_table");
            maxMRU = builder.defineInRange("max_mru", ECConfig.DEVICE_MAX_MRU, 1, Integer.MAX_VALUE);
            mruUsage = builder.comment("MRU used per tick = this x the upgrade's speed factor (fractions are carried over).",
                            "每 tick 耗的 MRU = 这个值 × 升级的速度系数（小数部分累计着扣）")
                    .defineInRange("mru_usage_modifier", 1D, 0D, 1000D);
            generatesCorruption = builder.comment("Whether the table leaks MRU into nearby MRUCUs while working.",
                            "工作时是否向周围泄漏 MRU")
                    .define("generate_corruption", true);
            maxCorruptionGen = builder.comment("Max MRU leaked per working tick (a random amount from 0 to this).",
                            "工作时每 tick 最多泄漏多少 MRU（在 0 到这个值之间随机）")
                    .defineInRange("max_corruption_gen", 1, 0, 10000);
            builder.pop();
        }
    }

    public static final int SLOT_GEM = 0;
    public static final int SLOT_CENTER = 1;
    public static final int SLOT_TOP_LEFT = 2;
    public static final int SLOT_TOP_RIGHT = 3;
    public static final int SLOT_BOTTOM_LEFT = 4;
    public static final int SLOT_BOTTOM_RIGHT = 5;
    public static final int SLOT_OUTPUT = 6;
    public static final int SLOTS = 7;
    /** 第一个输入槽，之后 {@link MagicianTableRecipe#INPUTS} 个按配方原料的顺序排 */
    public static final int FIRST_INPUT = SLOT_CENTER;

    private static final String TAG_PROGRESS = "progress";
    private static final String TAG_DEBT = "mru_debt";
    private static final String TAG_RECIPE = "recipe";
    private static final String TAG_UPGRADE = "upgrade";
    private static final String TAG_LAST_RECIPE = "last_recipe";

    /** 当前配方的进度（原作 progressLevel），单位同配方的 MRU 数 */
    private double progress;
    /** 还没扣的小数部分 MRU（0 ≤ 值 &lt; 1） */
    private double mruDebt;
    /** 这一个 serverTick 里推进了进度（给声音用，不存盘） */
    private boolean workedThisTick;
    /** 正在做的配方（存盘）；null 表示没在做 */
    @Nullable
    private ResourceLocation activeRecipeId;
    /** 上一次出产物用的配方（存盘）：漏斗平手时的提示，见 {@link #canAutomationInsert}；没做过为 null */
    @Nullable
    private ResourceLocation lastRecipeId;
    /** 装着的升级物品（数量 1），没装为空 */
    private ItemStack upgrade = ItemStack.EMPTY;

    // 配方查找缓存：输入变了或数据包重载了才重查
    private boolean inputsDirty = true;
    @Nullable
    private RecipeManager cachedManager;
    @Nullable
    private MagicianTableRecipe cachedRecipe;

    public MagicianTableBlockEntity(BlockPos pos, BlockState state) {
        super(ECMagicianTable.MAGICIAN_TABLE_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), SLOTS);
    }

    /** 槽位规则（两端共用）：0 号绑定宝石，1~5 号任意物品，6 号输出槽不能放 */
    public static boolean slotRule(int slot, ItemStack stack) {
        return slot != SLOT_OUTPUT && defaultSlotRule(slot, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    @Override
    protected boolean isOutputSlot(int slot) {
        return slot == SLOT_OUTPUT;
    }

    /**
     * 漏斗、管道往原料格插入时按配方表判断位置、并在几格之间均摊（{@link PositionalInsertRule}）。
     * 原作漏斗按槽号顺序塞，单原料配方的中心格满 64 以后就塞进四角、多原料配方的角料全堆进左上格，配方随即对不上，工作台卡死。
     * 玩家手放不受这条限制。
     * <p>
     * 同一件东西在不同配方里位置不同、几个空格都能收时，先照上一次出产物用的配方（{@link #lastRecipeId}）放。
     * 例：MRU 碎片在 MRU 水晶里放中心、在灵魂碎片里放四角，两边各一条配方，没做过东西时按槽号进中心；
     * 手摆一份做出灵魂碎片以后，五格吃空、碎片先到也进四角，漏斗供料的灵魂碎片线不会每轮卡一次。
     */
    @Override
    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        if (slot < FIRST_INPUT || slot >= FIRST_INPUT + MagicianTableRecipe.INPUTS) {
            return super.canAutomationInsert(slot, stack);
        }
        if (level == null) {
            return false;
        }
        ItemStack[] grid = new ItemStack[MagicianTableRecipe.INPUTS];
        for (int i = 0; i < grid.length; i++) {
            grid[i] = inventory.getStackInSlot(FIRST_INPUT + i);
        }
        return PositionalInsertRule.accepts(level.getRecipeManager().getAllRecipesFor(ECRecipes.MAGICIAN_TABLE.get()),
                grid, slot - FIRST_INPUT, stack, lastRecipeId);
    }

    /** 物品栏的每次变动（ItemStackHandler.onContentsChanged）都会走到这里，借此标记「要重查配方」 */
    @Override
    public void setChanged() {
        inputsDirty = true;
        super.setChanged();
    }

    // ------------------------------------------------------------------
    // 工作
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        workedThisTick = false;
        if (level == null) {
            return;
        }
        // 先查配方再看红石：界面要靠 cachedRecipe 显示停机时存着的进度（读档后第一 tick 也要填上）
        MagicianTableRecipe recipe = matchedRecipe();
        // 原作：有红石信号时什么都不做（进度保留）
        if (neighborPowered()) {
            return;
        }
        // 原作两种情况都把进度清零：输入不再匹配任何配方；产物放不进输出槽
        if (recipe == null || !canOutput(recipe)) {
            clearProgress();
            return;
        }
        if (!recipe.getId().equals(activeRecipeId)) {
            // 开始做新配方（包括做到一半换了配方，见类注释）
            clearProgress();
            activeRecipeId = recipe.getId();
            markSaved();
        }
        double speed = speedFactor();
        double cost = speed * ECConfig.get(Config.mruUsage) + mruDebt;
        int whole = (int) cost;
        // MRU 不够就等着，进度保留（原作同）
        if (mru.getMRU() < whole) {
            return;
        }
        progress += speed;
        workedThisTick = true;
        mruDebt = cost - whole;
        mru.extractMRU(whole, true);
        if (ECConfig.get(Config.generatesCorruption)) {
            ECCorruption.randomIncreaseAt(level, worldPosition, level.random, ECConfig.get(Config.maxCorruptionGen));
        }
        if (progress >= recipe.getMRU()) {
            craft(recipe);
            clearProgress();
        }
        markSaved();
    }

    /** 当前输入匹配到的配方（带缓存） */
    @Nullable
    private MagicianTableRecipe matchedRecipe() {
        RecipeManager manager = level.getRecipeManager();
        // /reload 会换一个新的 RecipeManager，旧的配方对象随之作废
        if (inputsDirty || manager != cachedManager) {
            inputsDirty = false;
            cachedManager = manager;
            cachedRecipe = lookup(manager);
        }
        return cachedRecipe;
    }

    @Nullable
    private MagicianTableRecipe lookup(RecipeManager manager) {
        ItemStack[] grid = new ItemStack[MagicianTableRecipe.INPUTS];
        boolean any = false;
        for (int i = 0; i < grid.length; i++) {
            grid[i] = inventory.getStackInSlot(FIRST_INPUT + i);
            any |= !grid[i].isEmpty();
        }
        // 全空不查：数据包里要是有五格全空的配方，工作台会凭空出货
        if (!any) {
            return null;
        }
        // SimpleContainer(ItemStack...) 直接包住这几堆，不会改动它们；正在做的配方先比，命中就不用扫全表
        Container container = new SimpleContainer(grid);
        return manager.getRecipeFor(ECRecipes.MAGICIAN_TABLE.get(), container, level, activeRecipeId)
                .map(Pair::getSecond)
                .orElse(null);
    }

    /** 五个输入格现在的物品，包成配方查找、拼产物用的容器（SimpleContainer 直接包住这几堆，不复制） */
    private Container inputContainer() {
        ItemStack[] grid = new ItemStack[MagicianTableRecipe.INPUTS];
        for (int i = 0; i < grid.length; i++) {
            grid[i] = inventory.getStackInSlot(FIRST_INPUT + i);
        }
        return new SimpleContainer(grid);
    }

    /**
     * 产物放得进输出槽（原作 canFunction）。产物按输入算（{@code assemble}）：普通配方就是固定产物的副本，
     * 同调升阶 / 转移的产物带着中心那件的 NBT（batch13.md §3.3.5）；算出来是空的（例：同调家族被关掉）就不开工。
     */
    private boolean canOutput(MagicianTableRecipe recipe) {
        ItemStack result = recipe.assemble(inputContainer(), level.registryAccess());
        if (result.isEmpty()) {
            return false;
        }
        ItemStack output = inventory.getStackInSlot(SLOT_OUTPUT);
        if (output.isEmpty()) {
            return true;
        }
        int limit = Math.min(output.getMaxStackSize(), inventory.getSlotLimit(SLOT_OUTPUT));
        return ItemStack.isSameItemSameTags(output, result) && output.getCount() + result.getCount() <= limit;
    }

    /**
     * 出一份产物（原作 craft）。产物先按扣料之前的输入拼好（{@code assemble}），再扣料：每格扣配方要的数量（普通配方每格 1，
     * 同调升阶的四角可以一格好几个）；配方要某一格留下东西时（同调转移的旧件留在角格、只清同调）换成它、不扣。
     */
    private void craft(MagicianTableRecipe recipe) {
        Container inputs = inputContainer();
        ItemStack result = recipe.assemble(inputs, level.registryAccess());
        ItemStack output = inventory.getStackInSlot(SLOT_OUTPUT);
        ItemStack placed;
        if (output.isEmpty()) {
            placed = result;
        } else {
            placed = output.copy();
            placed.grow(result.getCount());
        }
        // 扣料之前留一份五格的快照给 crafted 钩子（扣料会改格子里的物品）
        ItemStack[] before = new ItemStack[MagicianTableRecipe.INPUTS];
        for (int i = 0; i < before.length; i++) {
            before[i] = inputs.getItem(i).copy();
        }
        inventory.setStackInSlot(SLOT_OUTPUT, placed);
        for (int i = 0; i < MagicianTableRecipe.INPUTS; i++) {
            ItemStack stack = inventory.getStackInSlot(FIRST_INPUT + i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack keep = recipe.remainderAt(i, stack);
            if (keep != null) {
                inventory.setStackInSlot(FIRST_INPUT + i, keep);
            } else {
                inventory.extractItem(FIRST_INPUT + i, recipe.countAt(i), false);
            }
        }
        // 物品栏变了，上面已经标记存盘
        lastRecipeId = recipe.getId();
        recipe.crafted(new SimpleContainer(before), inventory.getStackInSlot(SLOT_OUTPUT));
    }

    private void clearProgress() {
        if (progress != 0D || activeRecipeId != null) {
            progress = 0D;
            activeRecipeId = null;
            markSaved();
        }
    }

    /** 只标记区块要存盘，不像 setChanged 那样通知邻居（进度每 tick 都在变） */
    private void markSaved() {
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    // ------------------------------------------------------------------
    // 升级
    // ------------------------------------------------------------------

    public ItemStack getUpgrade() {
        return upgrade;
    }

    public boolean hasUpgrade() {
        return !upgrade.isEmpty();
    }

    /** 当前速度系数（原作 speedFactor） */
    public double speedFactor() {
        return MagicianTableUpgrades.efficiency(upgrade);
    }

    /**
     * 装上升级（原作 BlockMagicianTable.onBlockActivated）。只在服务端调用。
     *
     * @return 是否装上了（已经装着一个、或不是升级物品时为 false）；调用方负责扣手里的物品
     */
    public boolean installUpgrade(ItemStack stack) {
        if (hasUpgrade() || MagicianTableUpgrades.get(stack) == null) {
            return false;
        }
        upgrade = stack.copyWithCount(1);
        upgradeChanged();
        return true;
    }

    /** 卸下升级，返回卸下的物品（没装时为空）。只在服务端调用。 */
    public ItemStack removeUpgrade() {
        ItemStack removed = upgrade;
        if (!removed.isEmpty()) {
            upgrade = ItemStack.EMPTY;
            upgradeChanged();
        }
        return removed;
    }

    private void upgradeChanged() {
        setChanged();
        // 客户端要靠它画工作台里的升级方块，立即同步（装卸是玩家操作，频率很低）
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** 挖掉方块时连升级一起掉（原作 breakBlock 里单独掉升级物品） */
    @Override
    public void dropContents() {
        super.dropContents();
        if (level != null && !upgrade.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), upgrade);
            upgrade = ItemStack.EMPTY;
        }
    }

    // ------------------------------------------------------------------
    // 界面数值（服务端菜单现取）
    // ------------------------------------------------------------------

    public double getProgress() {
        return progress;
    }

    /** 上一次出产物用的配方 ID（漏斗平手时的提示；没做过为 null） */
    @Nullable
    public ResourceLocation getLastRecipe() {
        return lastRecipeId;
    }

    /** 正在做的配方要多少进度；没在做为 0 */
    public int requiredProgress() {
        return activeRecipeId != null && cachedRecipe != null && activeRecipeId.equals(cachedRecipe.getId())
                ? cachedRecipe.getMRU() : 0;
    }

    /** 速度系数 ×100，界面按 short 同步 */
    public int speedPercent() {
        return (int) Math.round(speedFactor() * 100D);
    }

    // ------------------------------------------------------------------
    // 存盘
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble(TAG_PROGRESS, progress);
        tag.putDouble(TAG_DEBT, mruDebt);
        if (activeRecipeId != null) {
            tag.putString(TAG_RECIPE, activeRecipeId.toString());
        }
        if (lastRecipeId != null) {
            tag.putString(TAG_LAST_RECIPE, lastRecipeId.toString());
        }
        if (!upgrade.isEmpty()) {
            tag.put(TAG_UPGRADE, upgrade.save(new CompoundTag()));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        progress = Math.max(0D, tag.getDouble(TAG_PROGRESS));
        mruDebt = Math.max(0D, Math.min(tag.getDouble(TAG_DEBT), 1D));
        activeRecipeId = tag.contains(TAG_RECIPE) ? ResourceLocation.tryParse(tag.getString(TAG_RECIPE)) : null;
        // 只是漏斗的提示：配方被数据包删了也照样读回，用的时候在配方表里找不到就当没有
        lastRecipeId = tag.contains(TAG_LAST_RECIPE, Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString(TAG_LAST_RECIPE)) : null;
        upgrade = tag.contains(TAG_UPGRADE) ? ItemStack.of(tag.getCompound(TAG_UPGRADE)) : ItemStack.EMPTY;
        inputsDirty = true;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MagicianTableMenu(id, inventory, this);
    }

    // ------------------------------------------------------------------ 声音和粒子

    @Override
    protected boolean ambientWorking() {
        return workedThisTick;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return MachineAmbiences.MAGICIAN;
    }
}
