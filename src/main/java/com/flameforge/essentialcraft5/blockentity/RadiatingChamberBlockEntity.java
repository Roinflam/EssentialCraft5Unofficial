package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.RadiatingChamberMenu;
import com.flameforge.essentialcraft5.mru.ECCorruption;
import com.flameforge.essentialcraft5.recipe.PositionalInsertRule;
import com.flameforge.essentialcraft5.recipe.RadiatingChamberRecipe;
import com.flameforge.essentialcraft5.registry.ECRadiatingChamber;
import com.flameforge.essentialcraft5.registry.ECRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 辐射仓。对应原作 TileRadiatingChamber。
 * <p>
 * 0 号槽绑定宝石，1、2 号槽是按位置对应的两个原料（上、下），3 号槽输出。没有红石信号时，
 * 找一条原料对得上、且机器里 MRU 的平衡值落在配方区间（闭区间）内的配方，每 tick 推进 1、
 * 消耗 {@code 基础耗能 × 配方耗能倍率} MRU（向下取整，和原作一样），推进到配方的 mru 值时出一份产物，两个原料各扣 1。
 * 工作的每一 tick 都向周围泄漏一点 MRU（原作 genCorruption，见 {@link ECCorruption}）。
 *
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li><b>换配方重置进度</b>（原作 bug）：原作只在「一开始」挑配方，做到一半平衡值变了、匹配到另一条配方时进度照常累积，
 *       最后产出的仍是旧配方的东西（比如平衡值已经拉到混沌区，出来的还是冰冻投影）。这里每 tick 按当前平衡值选配方，
 *       选中的配方和进度所属的配方不是同一条就从 0 开始。</li>
 *   <li><b>配方查找缓存</b>：原作每 tick 遍历全部配方。这里只在两个原料槽的物品变了、或者数据包重载后才重新筛一遍
 *       「原料对得上的配方」（候选），每 tick 只在候选里按平衡值挑（通常只有 1~3 条）。</li>
 *   <li><b>重叠区间</b>：原作按注册顺序取第一条，1.20.1 的 RecipeManager 没有稳定顺序。自带配方的区间已经写成不重叠
 *       （见 data/essentialcraft5/recipes/radiating_chamber/magic_matrix_projection.json），
 *       数据包再加重叠的配方时按配方 ID 字典序取第一条，至少每次结果一样。</li>
 *   <li><b>输出放不下时停在原处</b>：原作清零。输出槽不能放入东西以后，做到一半被堵住只剩「数据包重载改了产物」一种情况，
 *       实际玩法里两者没有区别；停在原处写起来更简单，也不会白扔进度。</li>
 *   <li><b>产物合并要求 NBT 一致</b>：原作只比物品和 meta，合并时新建一个不带 NBT 的堆叠，输出槽里原有物品的 NBT 会丢。</li>
 *   <li><b>进度存盘</b>：原作 progressLevel 不存盘，区块卸载后投影（1 万 tick）要从头做。这里连同所属配方的 ID 一起存。</li>
 *   <li>输出槽不能放入物品（原作是普通槽，放进别的东西会把机器堵死）。</li>
 *   <li><b>漏斗按配方放位置</b>：原作漏斗什么都往里塞，这里照 {@link PositionalInsertRule}，还记下上一次出产物用的配方（存盘），
 *       两格都空、同一件东西两格都能放时照它放（见 {@link #canAutomationInsert}）。</li>
 * </ul>
 *
 * <h2>解码故障核心（第十批 10c，batch10.md §5.10.2）</h2>
 * 上、下原料格里只有一个带目标平衡值的故障核心、另一格空着时，不找配方，改走 {@link GlitchyCoreDecoder}：平衡值对准核心上的目标才推进，
 * 满了在输出槽出一份审判庭坐标。状态多三种（{@link Status#DECODING}、{@link Status#DECODE_BALANCE}、{@link Status#LOCATING}，只往末尾加），
 * 界面的进度条、平衡值窗口、耗能照样用（{@link #decodeView}）。
 */
public class RadiatingChamberBlockEntity extends MRUBlockEntity {

    /** 原作 tileentities.radiatingchamber */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxMRU;
        public static ForgeConfigSpec.DoubleValue mruUsage;
        public static ForgeConfigSpec.BooleanValue generateCorruption;
        public static ForgeConfigSpec.IntValue maxCorruptionGen;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("radiating_chamber");
            maxMRU = builder.defineInRange("max_mru", ECConfig.DEVICE_MAX_MRU, 1, Integer.MAX_VALUE);
            mruUsage = builder.comment("MRU used per working tick = this × the recipe's cost modifier (rounded down).",
                            "工作时每 tick 耗能 = 这个值 × 配方的耗能倍率（向下取整）")
                    .defineInRange("mru_usage_modifier", 1D, 0D, 1_000_000D);
            generateCorruption = builder.comment("Whether the chamber leaks MRU into nearby MRUCUs while working.",
                            "工作时是否向附近的 MRU 聚集体泄漏 MRU")
                    .define("generate_corruption", true);
            maxCorruptionGen = builder.comment("Max MRU leaked per working tick (a random amount from 0 to this).",
                            "每个工作 tick 最多泄漏多少 MRU（在 0 到这个值之间随机）")
                    .defineInRange("max_corruption_gen", 1, 0, 10000);
            builder.pop();
        }
    }

    /** 界面上显示的机器状态（服务端算好，随菜单同步） */
    public enum Status {
        /** 原料槽空着，或者没有配方对得上 */
        IDLE(0xFFFFFF),
        WORKING(0x00FF00),
        /** 原料对得上配方，但平衡值不在任何一条的区间里 */
        WRONG_BALANCE(0xFFFF00),
        NO_MRU(0xFFFF00),
        OUTPUT_FULL(0xFF0000),
        /** 有红石信号，停机 */
        POWERED(0xFF0000),
        /** 解码故障核心中（第十批 10c） */
        DECODING(0x00FF00),
        /** 解码中，仓里的平衡值不在核心上目标的窗口里：停住，进度保留 */
        DECODE_BALANCE(0xFFFF00),
        /** 解码进度满了，正在海纳尔里找坐标（几刻） */
        LOCATING(0x00FF00);

        private static final Status[] VALUES = values();

        /** 字色：红色要玩家处理，黄色是暂时条件不够，绿色正常（与绑定宝石状态的配色一致） */
        public final int color;

        Status(int color) {
            this.color = color;
        }

        public MutableComponent label() {
            return Component.translatable("gui.essentialcraft5.radiating_chamber." + name().toLowerCase(Locale.ROOT));
        }

        public static Status byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : IDLE;
        }
    }

    public static final int SLOT_GEM = 0;
    public static final int SLOT_TOP = 1;
    public static final int SLOT_BOTTOM = 2;
    public static final int SLOT_OUTPUT = 3;
    public static final int SLOTS = 4;

    public static final String TAG_PROGRESS = "progress";
    public static final String TAG_RECIPE = "recipe";
    public static final String TAG_LAST_RECIPE = "last_recipe";

    /** 已推进的 tick 数（原作 progressLevel） */
    private int progress;
    /** 进度属于哪条配方；选中的配方换了就清零 */
    @Nullable
    private ResourceLocation progressRecipe;
    /** 上一次出产物用的配方（存盘）：漏斗平手时的提示，见 {@link #canAutomationInsert}；没做过为 null */
    @Nullable
    private ResourceLocation lastRecipe;

    /** 界面显示用：正在做的配方，没有时是候选里的第一条（平衡值不符时告诉玩家要多少） */
    @Nullable
    private RadiatingChamberRecipe displayRecipe;
    private Status status = Status.IDLE;

    // ---- 候选配方缓存：两个原料槽的物品（引用、NBT、是否为空）或配方表换了才重算 ----
    private List<RadiatingChamberRecipe> candidates = List.of();
    @Nullable
    private RecipeManager seenRecipes;
    private final ItemStack[] seenStacks = {ItemStack.EMPTY, ItemStack.EMPTY};
    private final CompoundTag[] seenTags = new CompoundTag[2];
    private final boolean[] seenEmpty = {true, true};
    /** 解码故障核心的进度（第十批 10c） */
    private final GlitchyCoreDecoder decoder = new GlitchyCoreDecoder();

    public RadiatingChamberBlockEntity(BlockPos pos, BlockState state) {
        super(ECRadiatingChamber.RADIATING_CHAMBER_BE.get(), pos, state, () -> ECConfig.get(Config.maxMRU), SLOTS);
    }

    /** 槽位规则（两端共用）：0 号只放绑定宝石，1、2 号随意，3 号输出槽什么都不能放 */
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
     * 漏斗、管道往两个原料格插入时按配方表判断位置（{@link PositionalInsertRule}）。原作什么都收：漏斗里有两个空白投影时，
     * 第二个会进下格，配方要求下格为空，辐射仓就停在「空闲」直到手动掏出来。玩家手放不受这条限制。
     * <p>
     * 两格都空、同一件东西上下都能放时，先照上一次出产物用的配方（{@link #lastRecipe}）放：铁锭在强化石、强化玻璃里放下格，
     * 在 MRU 磁体里放上格，没做过东西时进下格；手放一块铁锭在上格做出一个磁体以后，两格同时吃空、铁锭先到也照样进上格，
     * 漏斗供料的磁体线不会每轮卡一次。
     */
    @Override
    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        if (slot != SLOT_TOP && slot != SLOT_BOTTOM) {
            return super.canAutomationInsert(slot, stack);
        }
        if (level == null) {
            return false;
        }
        ItemStack[] grid = {inventory.getStackInSlot(SLOT_TOP), inventory.getStackInSlot(SLOT_BOTTOM)};
        return PositionalInsertRule.accepts(level.getRecipeManager().getAllRecipesFor(ECRecipes.RADIATING_CHAMBER.get()),
                grid, slot - SLOT_TOP, stack, lastRecipe);
    }

    // ------------------------------------------------------------------
    // 配方选择（静态，GameTest 直接调）
    // ------------------------------------------------------------------

    /**
     * 原料对得上的全部配方（不看平衡值），按配方 ID 排序。
     * 原作 getRecipeByInputAndBalance 按注册顺序取第一条；1.20.1 的配方表没有稳定顺序，按 ID 排是为了结果可重复。
     */
    public static List<RadiatingChamberRecipe> findCandidates(RecipeManager recipes, Level level, ItemStack top, ItemStack bottom) {
        if (top.isEmpty() && bottom.isEmpty()) {
            return List.of();
        }
        Container view = new SimpleContainer(top, bottom);
        return recipes.getAllRecipesFor(ECRecipes.RADIATING_CHAMBER.get()).stream()
                .filter(recipe -> recipe.matches(view, level))
                .sorted(Comparator.comparing(RadiatingChamberRecipe::getId))
                .toList();
    }

    /** 候选里第一条平衡值区间（闭区间）包含 balance 的，没有返回 null */
    @Nullable
    public static RadiatingChamberRecipe pick(List<RadiatingChamberRecipe> candidates, float balance) {
        for (RadiatingChamberRecipe recipe : candidates) {
            if (recipe.acceptsBalance(balance)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * 某条配方每个工作 tick 的耗能（原作 (int)(mruUsage × costModifier)，向下取整）。
     * 倍率是 float（JSON 里的 0.7 读进来是 0.699999988），先还原成写的十进制值；再按相对容差取整，
     * 免得 0.57 × 100 = 56.999… 取整成 56。默认配置下结果和原作一样。
     */
    public static int costPerTick(RadiatingChamberRecipe recipe) {
        double raw = ECConfig.get(Config.mruUsage) * Double.parseDouble(Float.toString(recipe.getCostModifier()));
        return Math.max(0, (int) Math.floor(raw * (1 + 1e-9)));
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        if (level == null) {
            return;
        }
        GlitchyCoreDecoder.Input decode = GlitchyCoreDecoder.input(inventory.getStackInSlot(SLOT_TOP), inventory.getStackInSlot(SLOT_BOTTOM));
        if (decode == null) {
            decoder.reset();
        } else {
            // 解码分支：不找配方，配方进度清掉（故障核心不是任何配方的原料，普通分支也会清）
            setProgress(0, null);
            displayRecipe = null;
            if (neighborPowered()) {
                decoder.track(decode);
                status = Status.POWERED;
            } else if (level instanceof ServerLevel server) {
                status = decoder.tick(this, server, decode);
            }
            return;
        }
        // 原作 getRedstonePowerFromNeighbors(pos) == 0 才工作；有信号时什么都不动，进度保留
        if (neighborPowered()) {
            status = Status.POWERED;
            // 停机时界面照样显示正在做的配方和进度（读档后第一 tick 也要填上）
            refreshCandidates();
            RadiatingChamberRecipe shown = null;
            for (RadiatingChamberRecipe candidate : candidates) {
                if (candidate.getId().equals(progressRecipe)) {
                    shown = candidate;
                    break;
                }
            }
            if (shown == null) {
                shown = pick(candidates, mru.getBalance());
            }
            displayRecipe = shown != null ? shown : candidates.isEmpty() ? null : candidates.get(0);
            return;
        }
        refreshCandidates();
        RadiatingChamberRecipe recipe = pick(candidates, mru.getBalance());
        if (recipe == null) {
            // 原作：原料拿走、或平衡值出了所有区间，进度清零
            setProgress(0, null);
            displayRecipe = candidates.isEmpty() ? null : candidates.get(0);
            status = candidates.isEmpty() ? Status.IDLE : Status.WRONG_BALANCE;
            return;
        }
        displayRecipe = recipe;
        if (!recipe.getId().equals(progressRecipe)) {
            // 修原作 bug：平衡值变了选中另一条配方时，旧配方的进度不能带过去
            setProgress(0, recipe.getId());
        }
        if (!canOutput(recipe.getResult())) {
            status = Status.OUTPUT_FULL;
            return;
        }
        int cost = costPerTick(recipe);
        if (mru.getMRU() < cost) {
            status = Status.NO_MRU;
            return;
        }
        status = Status.WORKING;
        mru.extractMRU(cost, true);
        if (ECConfig.get(Config.generateCorruption)) {
            ECCorruption.randomIncreaseAt(level, worldPosition, level.random, ECConfig.get(Config.maxCorruptionGen));
        }
        if (progress + 1 >= recipe.getMRU()) {
            craft(recipe);
            setProgress(0, null);
        } else {
            setProgress(progress + 1, recipe.getId());
        }
    }

    /** 原料槽的物品换了（或配方表重载了）才重新筛候选 */
    private void refreshCandidates() {
        RecipeManager recipes = level.getRecipeManager();
        boolean changed = recipes != seenRecipes;
        for (int i = 0; i < 2; i++) {
            ItemStack stack = inventory.getStackInSlot(SLOT_TOP + i);
            // 引用变了是换了一堆；NBT 引用变了是物品被改了；原地 shrink 到 0 引用不变，要单独看是否为空
            if (stack != seenStacks[i] || stack.getTag() != seenTags[i] || stack.isEmpty() != seenEmpty[i]) {
                seenStacks[i] = stack;
                seenTags[i] = stack.getTag();
                seenEmpty[i] = stack.isEmpty();
                changed = true;
            }
        }
        if (changed) {
            seenRecipes = recipes;
            candidates = findCandidates(recipes, level, seenStacks[0], seenStacks[1]);
        }
    }

    /** 原作 canFunction：输出槽空着，或者是同一种物品（这里还要求 NBT 一致）且放得下 */
    private boolean canOutput(ItemStack result) {
        if (result.isEmpty()) {
            return false;
        }
        ItemStack out = inventory.getStackInSlot(SLOT_OUTPUT);
        if (out.isEmpty()) {
            return true;
        }
        int limit = Math.min(inventory.getSlotLimit(SLOT_OUTPUT), out.getMaxStackSize());
        return ItemStack.isSameItemSameTags(out, result) && out.getCount() + result.getCount() <= limit;
    }

    private void craft(RadiatingChamberRecipe recipe) {
        ItemStack top = inventory.getStackInSlot(SLOT_TOP);
        ItemStack bottom = inventory.getStackInSlot(SLOT_BOTTOM);
        ItemStack result = recipe.assemble(new SimpleContainer(top, bottom), level.registryAccess());
        ItemStack out = inventory.getStackInSlot(SLOT_OUTPUT);
        // 用 setStackInSlot 写回（不原地 grow），ItemStackHandler 才知道变了：存盘、客户端同步都靠它
        inventory.setStackInSlot(SLOT_OUTPUT, out.isEmpty() ? result : out.copyWithCount(out.getCount() + result.getCount()));
        // 原作两个原料槽各扣 1（空槽扣了等于没扣）
        inventory.extractItem(SLOT_TOP, 1, false);
        inventory.extractItem(SLOT_BOTTOM, 1, false);
        // 物品栏变了，上面已经标记存盘
        lastRecipe = recipe.getId();
    }

    private void setProgress(int value, @Nullable ResourceLocation recipe) {
        if (value != progress || (recipe == null ? progressRecipe != null : !recipe.equals(progressRecipe))) {
            progress = value;
            progressRecipe = recipe;
            // 耗能为 0 时 MRU 不变，基类不会标记存盘，这里自己标
            level.blockEntityChanged(worldPosition);
        }
    }

    // ------------------------------------------------------------------
    // 访问（界面数值、GameTest）
    // ------------------------------------------------------------------

    public int getProgress() {
        return progress;
    }

    /** 进度所属的配方 ID（没在做时为 null） */
    @Nullable
    public ResourceLocation getProgressRecipe() {
        return progressRecipe;
    }

    /** 上一次出产物用的配方 ID（漏斗平手时的提示；没做过为 null） */
    @Nullable
    public ResourceLocation getLastRecipe() {
        return lastRecipe;
    }

    /** 界面显示的配方：正在做的，或平衡值不符时候选里的第一条 */
    @Nullable
    public RadiatingChamberRecipe getDisplayRecipe() {
        return displayRecipe;
    }

    public Status getStatus() {
        return status;
    }

    /** 正在解码故障核心时界面上显示的数（进度、所需刻数、平衡值窗口、每刻耗能）；没在解码为 null */
    @Nullable
    public GlitchyCoreDecoder.View decodeView() {
        return decoder.view();
    }

    /** 解码进度（GameTest 用） */
    public int getDecodeProgress() {
        return decoder.progress();
    }

    /** 这一次找坐标已经看了几个点（GameTest 用） */
    public int getLocateSamples() {
        return decoder.locateSamples();
    }

    // ------------------------------------------------------------------
    // 存盘
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (progressRecipe != null && progress > 0) {
            tag.putInt(TAG_PROGRESS, progress);
            tag.putString(TAG_RECIPE, progressRecipe.toString());
        }
        if (lastRecipe != null) {
            tag.putString(TAG_LAST_RECIPE, lastRecipe.toString());
        }
        decoder.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // 配方 ID 读回来先不解析：第一次 tick 选出的配方还是它，进度就接着算；换了（数据包删改）就清零
        ResourceLocation recipe = tag.contains(TAG_RECIPE, Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString(TAG_RECIPE)) : null;
        progress = recipe != null ? Math.max(0, tag.getInt(TAG_PROGRESS)) : 0;
        progressRecipe = recipe;
        // 只是漏斗的提示：配方被数据包删了也照样读回，用的时候在配方表里找不到就当没有
        lastRecipe = tag.contains(TAG_LAST_RECIPE, Tag.TAG_STRING) ? ResourceLocation.tryParse(tag.getString(TAG_LAST_RECIPE)) : null;
        // 解码进度连同当时的输入（格子、目标平衡值）读回；第一刻输入对得上就接着解
        decoder.load(tag);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RadiatingChamberMenu(id, inventory, this);
    }

    // ------------------------------------------------------------------ 声音和粒子

    @Override
    protected boolean ambientWorking() {
        return status == Status.WORKING;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return MachineAmbiences.RADIATING;
    }
}
