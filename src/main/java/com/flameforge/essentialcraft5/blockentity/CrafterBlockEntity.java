package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.CraftingFrameItem;
import com.flameforge.essentialcraft5.menu.CrafterMenu;
import com.flameforge.essentialcraft5.recipe.CrafterGrid;
import com.flameforge.essentialcraft5.recipe.CraftingPattern;
import com.flameforge.essentialcraft5.registry.ECCrafter;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 魔力合成器（原作 TileCrafter，batch4 §3.3）：3×3 合成格（0~8，按行排）、产物格 9、合成框架格 10，没有 MRU、没有绑定宝石槽。
 * 红石信号<b>从无到有</b>合成一次（{@link RedstonePulseReceiver}，方块 {@code CrafterBlock} 不 tick），两次至少隔 {@code pulse_interval}（默认 4）刻。
 * 只动自己的物品栏，不碰世界（policy §1.1「不算触发」），所以<b>不需要主人</b>：/setblock 放的、主人不在线的照样合成。
 *
 * <h2>一次合成（{@link #craftOnce}，只在服务端）</h2>
 * <ol>
 *   <li>合成格的副本拼成 {@link CrafterGrid}（不用原版 TransientCraftingContainer，它要菜单）。</li>
 *   <li>取配方：装了有效框架 → 框架的配方 {@code matches} 一次，不扫全表；没装（或空框架）→ 原版熔炉那种 {@code RecipeManager.createCheck} 缓存，
 *       上次没配上以后合成格没变过就跳过扫描；框架失效 → 不合成。</li>
 *   <li>{@code assemble}，产物为空不合成；产物格空着、或同物品同 NBT 且放得下（≤ 叠加上限）才继续——<b>放不下就不合成，也不扣原料</b>。</li>
 *   <li>每个非空的格子扣 1（原作条件写反，格子里还有东西时整格清空）。</li>
 *   <li>剩余物（空桶、空瓶）照原版 ResultSlot：这格空了放回这格 → 同物品就叠上 → 并进产物格 → 从机器顶上掉出来（每次最多 9 个）。</li>
 * </ol>
 * 不传玩家、不发 {@code ItemCraftedEvent}（和别的模组的自动合成台一样）。
 *
 * <h2>槽位规则</h2>
 * <ul>
 *   <li>玩家（{@link #slotRule}，客户端菜单用 {@link #clientHandler} 套同一份规则）：9 号不能放；10 号只收合成框架；
 *       合成格：没装框架时除了合成框架什么都能放（框架不是任何配方的原料，Shift 点它才会进 10 号格），有效框架只收这一格样板要的，失效框架不收。</li>
 *   <li>漏斗、管道（{@link #canAutomationInsert}）：10 号空着时收框架；合成格按格<b>均摊</b>——只往「要这件东西、放得下」的格子里数量最少的那格放（同样多取槽号小的），
 *       漏斗逐格试的结果就是轮流补；<b>没装框架时只往已经有同种物品的格子补</b>，空格不收（原作什么都收，漏斗会全塞进 0 号格，本来就用不了）。</li>
 *   <li>能抽走的（{@link #isOutputSlot}）：9 号；装了有效框架时，再加上内容和样板不符的合成格（放回原格的空桶，让下面的漏斗抽走）。</li>
 * </ul>
 *
 * <h2>产线（研究手册第十四章，第十批）</h2>
 * 连续 {@code assembly_line_crafts}（默认 64）次合成，每次用到的原料全部是漏斗、管道、魔力镜经物品能力送进来的（{@link #onAutomationInserted}），
 * 就给主人打「产线」的勾（{@link #GUIDE_ASSEMBLY_LINE}；主人不在线时记着，下一次合成时补发）。界面的槽位直接改内部物品栏、不经过物品能力，
 * 而且原版的 Shift 点击会原地往已有堆叠里并数量、不触发任何回调，所以每格按「上次核对时的物品和数量」比：多出来、又不是这次自动化送来的算手放
 * （{@link #reconcile}）；合成时先算用掉的是手放的那些，用到手放原料的那次不算、计数清零。计数、每格手放的个数存盘。
 *
 * <h2>修的原作问题</h2>
 * 生存里一次都合不出来（输出格谁都放不进 L48，{@code makeRecipe} 的条件又写反 L207）；空框架时三处空指针（每 tick L96 崩服、漏斗插入 L58、打开界面 L67）；
 * 两端每 tick 全表查配方（L31~40）；精确模式连数量一起比（L81）；矿辞模式把两件都没矿辞的物品当成相同。
 */
public class CrafterBlockEntity extends MRUBlockEntity implements RedstonePulseReceiver {

    /** 服务端配置 machines.crafter（新加的限制，原作没有） */
    public static final class Config {
        public static ForgeConfigSpec.IntValue pulseInterval;
        public static ForgeConfigSpec.IntValue assemblyLineCrafts;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("crafter");
            pulseInterval = builder.comment("Minimum ticks between two crafts (one craft per rising redstone edge).",
                            "两次合成之间至少隔多少刻（红石每从无到有一次合成一次）")
                    .defineInRange("pulse_interval", DEFAULT_PULSE_INTERVAL, 1, 200);
            assemblyLineCrafts = builder.comment("Research book step \"assembly line\": crafts in a row whose ingredients all came in through hoppers, pipes or Magical Mirrors (none put in by hand).",
                            "研究手册「产线」一步：连续多少次合成的原料全部由漏斗、管道或魔力镜送进来（没有一件是手放的）")
                    .defineInRange("assembly_line_crafts", DEFAULT_ASSEMBLY_LINE_CRAFTS, 1, 10_000);
            builder.pop();
        }
    }

    public static final int DEFAULT_PULSE_INTERVAL = 4;
    public static final int DEFAULT_ASSEMBLY_LINE_CRAFTS = 64;
    /** 研究手册第十四章「产线」一步，只由代码发（进度文件被数据包删掉时什么都不做） */
    public static final ResourceLocation GUIDE_ASSEMBLY_LINE = EssentialCraft5.id("guide/automation/assembly_line");
    public static final String GUIDE_CRITERION = "awarded_by_code";
    private static final String TAG_ASSEMBLY = "assembly_line";
    private static final String TAG_ASSEMBLY_CRAFTS = "crafts";
    private static final String TAG_ASSEMBLY_PENDING = "pending";
    private static final String TAG_ASSEMBLY_MANUAL = "manual";
    public static final int GRID_SLOTS = CraftingPattern.SIZE;
    public static final int SLOT_OUTPUT = 9;
    public static final int SLOT_FRAME = 10;
    public static final int SLOTS = 11;
    /** 产物格上限（同原作 getInventoryStackLimit） */
    public static final int OUTPUT_LIMIT = 64;

    private final PulseGate gate = new PulseGate(CrafterBlockEntity::pulseInterval);
    private final CraftingPattern.Cache patternCache = new CraftingPattern.Cache();
    /** 没装框架时用：先试上一次的配方再扫全表（原版熔炉的做法） */
    private final RecipeManager.CachedCheck<CraftingContainer, CraftingRecipe> recipeCheck = RecipeManager.createCheck(RecipeType.CRAFTING);
    /** 没装框架时上一次没配上的合成格（只比物品和 NBT，不比数量）；格子没变就不再扫全表。不存盘 */
    @Nullable
    private ItemStack[] missedGrid;
    @Nullable
    private RecipeManager missedManager;

    // ---- 产线计数（见类注释） ----
    /** 连续几次「原料全经自动化送来」的合成；到门槛后停在门槛（存盘） */
    private int assemblyCrafts;
    /** 到了门槛、还没发给主人（主人不在线，等下一次合成补发；存盘） */
    private boolean assemblyPending;
    /** 每个合成格里有几个是手放的（存盘） */
    private final int[] manualCount = new int[GRID_SLOTS];
    /** 每个合成格上次核对时的那件物品和数量（不存盘：读档时取当时的内容） */
    private final ItemStack[] knownStack = new ItemStack[GRID_SLOTS];
    private final int[] knownCount = new int[GRID_SLOTS];

    public CrafterBlockEntity(BlockPos pos, BlockState state) {
        super(ECCrafter.CRAFTER_BE.get(), pos, state, () -> 0, SLOTS);
        Arrays.fill(knownStack, ItemStack.EMPTY);
    }

    private static int pulseInterval() {
        // 注册类的 init() 登记配置之前（理论上不会发生）按默认值
        return Config.pulseInterval == null ? DEFAULT_PULSE_INTERVAL : ECConfig.get(Config.pulseInterval);
    }

    /** 「产线」要连续几次（配置没加载时用默认值） */
    public static int assemblyLineCrafts() {
        return Config.assemblyLineCrafts == null ? DEFAULT_ASSEMBLY_LINE_CRAFTS : ECConfig.get(Config.assemblyLineCrafts);
    }

    @Override
    protected boolean hasBoundGemSlot() {
        return false;
    }

    /** 渲染用不到物品栏（F4-15）：物品栏只走菜单同步给开着界面的人 */
    @Override
    protected boolean syncInventoryToClient() {
        return false;
    }

    // ------------------------------------------------------------------
    // 框架
    // ------------------------------------------------------------------

    /** 框架格里的样板（解析结果有缓存）；没装 / 空框架是 {@link CraftingPattern.State#NONE} */
    public CraftingPattern pattern() {
        return patternCache.get(inventory.getStackInSlot(SLOT_FRAME), level);
    }

    public CraftingPattern.State frameState() {
        return pattern().state();
    }

    // ------------------------------------------------------------------
    // 槽位规则
    // ------------------------------------------------------------------

    /** 玩家手放的规则（两端共用；pattern 是框架格的解析结果） */
    public static boolean slotRule(int slot, ItemStack stack, CraftingPattern pattern) {
        if (stack.isEmpty()) {
            return false;
        }
        if (slot == SLOT_FRAME) {
            return stack.getItem() instanceof CraftingFrameItem;
        }
        if (slot < 0 || slot >= GRID_SLOTS) {
            return false;
        }
        return switch (pattern.state()) {
            // 没有配方拿合成框架当原料；拒掉它，从背包 Shift 点框架时才会进 10 号框架格，而不是落进空着的 0 号合成格（4a 审查 CRAFT-2）
            case NONE -> !(stack.getItem() instanceof CraftingFrameItem);
            case VALID -> pattern.accepts(slot, stack);
            case INVALID -> false;
        };
    }

    /**
     * 客户端菜单的物品栏：内容由原版菜单同步填；槽位规则和服务端一样依赖框架格，所以用自己框架格里那件、按客户端的配方表解析
     * （{@code MachineMenu.clientHandler} 只接受和物品栏内容无关的静态规则，这里用不上）。
     */
    public static ItemStackHandler clientHandler(Level level) {
        CraftingPattern.Cache cache = new CraftingPattern.Cache();
        return new ItemStackHandler(SLOTS) {
            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return slotRule(slot, stack, cache.get(getStackInSlot(SLOT_FRAME), level));
            }
        };
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack, pattern());
    }

    @Override
    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (slot == SLOT_FRAME) {
            return inventory.getStackInSlot(SLOT_FRAME).isEmpty() && stack.getItem() instanceof CraftingFrameItem;
        }
        if (slot < 0 || slot >= GRID_SLOTS) {
            return false;
        }
        CraftingPattern pattern = pattern();
        return pattern.state() != CraftingPattern.State.INVALID && cachedBalancedCell(pattern, stack) == slot;
    }

    /**
     * {@link #balancedCell} 的上一次结果（4a 审查 CRAFT-1）。Forge 的漏斗（{@code VanillaInventoryCodeHooks.putStackInInventoryAllSlots}）
     * 拿<b>同一个</b>物品对象对 11 个槽逐个 {@code insertItem}，0~8 号每问一次都要把 9 格的样板试一遍；精确 / 特殊配方的哈希样本每试一次都要算候选物品的哈希。
     * 这里记下「那个物品对象、它的 NBT 对象、样板对象、当时的游戏刻、9 格各自的物品对象和数量」，全都没变就直接用上次的结果，一轮 11 问只算一次。
     * 9 格按对象 + 数量比，而不是在 onContentsChanged 里记版本号：MRUBlockEntity 没给子类开这个钩子，而且原地改数量的代码
     * （原版 {@code AbstractContainerMenu.moveItemStackTo} 往已有堆叠里并、拿 {@code getStackInSlot} 的结果直接改的模组）根本不经过它。
     * 不存盘；跨刻一律重算（漏斗每刻都拿新拷出来的物品来问，跨刻本来也命中不了）。
     */
    @Nullable
    private ItemStack balancedStack;
    @Nullable
    private CompoundTag balancedTag;
    @Nullable
    private CraftingPattern balancedPattern;
    private long balancedTime = Long.MIN_VALUE;
    private int balancedResult = -1;
    private final ItemStack[] balancedCells = new ItemStack[GRID_SLOTS];
    private final int[] balancedCounts = new int[GRID_SLOTS];

    private int cachedBalancedCell(CraftingPattern pattern, ItemStack stack) {
        long now = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (stack == balancedStack && stack.getTag() == balancedTag && pattern == balancedPattern && now == balancedTime && sameCellsAsBalanced()) {
            return balancedResult;
        }
        int result = balancedCell(pattern, stack);
        balancedStack = stack;
        balancedTag = stack.getTag();
        balancedPattern = pattern;
        balancedTime = now;
        balancedResult = result;
        for (int i = 0; i < GRID_SLOTS; i++) {
            ItemStack cell = inventory.getStackInSlot(i);
            balancedCells[i] = cell;
            balancedCounts[i] = cell.getCount();
        }
        return result;
    }

    private boolean sameCellsAsBalanced() {
        for (int i = 0; i < GRID_SLOTS; i++) {
            ItemStack cell = inventory.getStackInSlot(i);
            if (cell != balancedCells[i] || cell.getCount() != balancedCounts[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 漏斗该往哪一格放：所有「要它、放得下」的格子里数量最少的（同样多取槽号小的）；没有返回 -1。
     * 要它 = 有效框架时这一格的样板收它；没框架时这一格已经有同种物品（空格不收）。
     * 漏斗走 {@link #canAutomationInsert} 时经过 {@link #cachedBalancedCell}。
     */
    int balancedCell(CraftingPattern pattern, ItemStack stack) {
        int best = -1;
        int bestCount = Integer.MAX_VALUE;
        for (int i = 0; i < GRID_SLOTS; i++) {
            ItemStack cell = inventory.getStackInSlot(i);
            boolean wants;
            if (pattern.isValid()) {
                wants = pattern.accepts(i, stack) && (cell.isEmpty() || canStack(cell, stack));
            } else {
                wants = !cell.isEmpty() && canStack(cell, stack);
            }
            if (wants && cell.getCount() < bestCount) {
                best = i;
                bestCount = cell.getCount();
            }
        }
        return best;
    }

    private boolean canStack(ItemStack cell, ItemStack stack) {
        return ItemStack.isSameItemSameTags(cell, stack)
                && cell.getCount() < Math.min(cell.getMaxStackSize(), inventory.getSlotLimit(0));
    }

    /** 下方漏斗每刻都从 0 号往后问一遍；哈希样本的比较靠 GhostSlots 按 NBT 内容缓存的哈希，格子里那件不会每刻重算（CRAFT-1） */
    @Override
    protected boolean isOutputSlot(int slot) {
        if (slot == SLOT_OUTPUT) {
            return true;
        }
        if (slot < 0 || slot >= GRID_SLOTS) {
            return false;
        }
        CraftingPattern pattern = pattern();
        ItemStack cell = inventory.getStackInSlot(slot);
        return pattern.isValid() && !cell.isEmpty() && !pattern.accepts(slot, cell);
    }

    // ------------------------------------------------------------------
    // 合成
    // ------------------------------------------------------------------

    @Override
    public PulseGate pulseGate() {
        return gate;
    }

    @Override
    public boolean onRedstonePulse(ServerLevel level, BlockState state) {
        craftOnce();
        return false;
    }

    /** 合成一次（规则见类注释）；合成了返回 true。只在服务端有效 */
    public boolean craftOnce() {
        if (level == null || level.isClientSide) {
            return false;
        }
        CraftingPattern pattern = pattern();
        if (pattern.state() == CraftingPattern.State.INVALID) {
            return false;
        }
        ItemStack[] cells = new ItemStack[GRID_SLOTS];
        for (int i = 0; i < GRID_SLOTS; i++) {
            cells[i] = inventory.getStackInSlot(i);
        }
        CrafterGrid grid = new CrafterGrid(cells);
        if (grid.isEmpty()) {
            return false;
        }
        CraftingRecipe recipe = findRecipe(pattern, grid, cells);
        if (recipe == null) {
            return false;
        }
        ItemStack result = recipe.assemble(grid, level.registryAccess());
        if (result.isEmpty()) {
            return false;
        }
        ItemStack output = inventory.getStackInSlot(SLOT_OUTPUT);
        if (!fits(output, result)) {
            return false;
        }
        // 剩余物照原版在扣料之前按原样算（格子里还是合成前的东西）
        NonNullList<ItemStack> remaining = recipe.getRemainingItems(grid);
        boolean automatedOnly = reconcileGrid();

        for (int i = 0; i < GRID_SLOTS; i++) {
            ItemStack cell = inventory.getStackInSlot(i);
            if (!cell.isEmpty()) {
                ItemStack left = cell.copy();
                left.shrink(1);
                inventory.setStackInSlot(i, left.isEmpty() ? ItemStack.EMPTY : left);
                // 先算用掉的是手放的那件
                if (manualCount[i] > 0) {
                    manualCount[i]--;
                }
            }
        }
        if (output.isEmpty()) {
            inventory.setStackInSlot(SLOT_OUTPUT, result.copy());
        } else {
            ItemStack merged = output.copy();
            merged.grow(result.getCount());
            inventory.setStackInSlot(SLOT_OUTPUT, merged);
        }

        List<ItemStack> drops = new ArrayList<>();
        for (int i = 0; i < GRID_SLOTS && i < remaining.size(); i++) {
            ItemStack rest = remaining.get(i);
            if (!rest.isEmpty()) {
                placeRemainder(i, rest.copy(), drops);
            }
        }
        dropOnTop(drops);
        // 剩余物是机器自己放回去的，不算手放：合成完照现在的内容重记
        for (int i = 0; i < GRID_SLOTS; i++) {
            remember(i);
        }
        countAssemblyCraft(automatedOnly);
        return true;
    }

    // ------------------------------------------------------------------
    // 产线计数
    // ------------------------------------------------------------------

    @Override
    protected void onAutomationInserted(int slot, int count) {
        if (slot >= 0 && slot < GRID_SLOTS) {
            reconcile(slot, count);
        }
    }

    /**
     * 核对一格：和上次记下的物品、数量比，多出来的（减去 automated 这次刚经物品能力送来的）算手放，这时产线计数清零；少了就是被拿走了，
     * 手放的个数不超过剩下的。核对完记下现在的内容。
     *
     * @param automated 这次刚经物品能力送进来的个数（合成前核对时为 0）
     */
    private void reconcile(int slot, int automated) {
        ItemStack cell = inventory.getStackInSlot(slot);
        // 自动化这次送来之前格子里有几个
        int before = cell.getCount() - automated;
        ItemStack known = knownStack[slot];
        if (cell.isEmpty() || before <= 0) {
            manualCount[slot] = 0;
        } else if (known.isEmpty() || !ItemStack.isSameItemSameTags(known, cell)) {
            // 换了东西（或者原来是空的）：送来之前就在的那些都是别的途径放进来的
            manualCount[slot] = before;
            assemblyCrafts = 0;
        } else if (before > knownCount[slot]) {
            manualCount[slot] = Math.min(before, manualCount[slot] + before - knownCount[slot]);
            assemblyCrafts = 0;
        } else {
            manualCount[slot] = Math.min(manualCount[slot], before);
        }
        remember(slot);
    }

    /** 合成前核对全部合成格；返回这次要用到的原料是不是全经自动化送来（有一格里还有手放的就不是） */
    private boolean reconcileGrid() {
        boolean automatedOnly = true;
        for (int i = 0; i < GRID_SLOTS; i++) {
            reconcile(i, 0);
            if (!inventory.getStackInSlot(i).isEmpty() && manualCount[i] > 0) {
                automatedOnly = false;
            }
        }
        return automatedOnly;
    }

    private void remember(int slot) {
        ItemStack cell = inventory.getStackInSlot(slot);
        knownStack[slot] = cell;
        knownCount[slot] = cell.getCount();
        manualCount[slot] = Math.min(manualCount[slot], cell.getCount());
    }

    /** 记一次合成：全经自动化就 +1（到门槛停住），否则清零；到了门槛就发给在线的主人，不在线等下一次合成 */
    private void countAssemblyCraft(boolean automatedOnly) {
        int target = assemblyLineCrafts();
        if (!automatedOnly) {
            assemblyCrafts = 0;
        } else if (assemblyCrafts < target) {
            assemblyCrafts++;
        }
        if (assemblyCrafts >= target) {
            assemblyPending = true;
        }
        if (assemblyPending) {
            ServerPlayer owner = ECProtection.onlineOwner(this);
            if (owner != null && !(owner instanceof FakePlayer)) {
                PlayerPentacleBlockEntity.award(owner, GUIDE_ASSEMBLY_LINE, GUIDE_CRITERION);
                assemblyPending = false;
            }
        }
        setChanged();
    }

    /** 现在连续了几次全经自动化的合成（到门槛后停在门槛） */
    public int assemblyLineProgress() {
        return assemblyCrafts;
    }

    /** 到了门槛、还在等主人上线 */
    public boolean assemblyLinePending() {
        return assemblyPending;
    }

    /** 这一合成格里有几个是手放的（GameTest 用） */
    public int manualCount(int slot) {
        return slot >= 0 && slot < GRID_SLOTS ? manualCount[slot] : 0;
    }

    @Nullable
    private CraftingRecipe findRecipe(CraftingPattern pattern, CrafterGrid grid, ItemStack[] cells) {
        if (pattern.isValid()) {
            CraftingRecipe recipe = pattern.recipe();
            return recipe != null && recipe.matches(grid, level) ? recipe : null;
        }
        RecipeManager manager = level.getRecipeManager();
        if (missedGrid != null && missedManager == manager && sameItems(missedGrid, cells)) {
            return null;
        }
        Optional<CraftingRecipe> found = recipeCheck.getRecipeFor(grid, level);
        if (found.isEmpty()) {
            missedGrid = snapshot(cells);
            missedManager = manager;
            return null;
        }
        missedGrid = null;
        missedManager = null;
        return found.get();
    }

    /** 产物格能不能放下这次的产物（空着，或同物品同 NBT 且加起来不超过叠加上限） */
    private boolean fits(ItemStack output, ItemStack result) {
        if (output.isEmpty()) {
            return result.getCount() <= Math.min(result.getMaxStackSize(), OUTPUT_LIMIT);
        }
        return ItemStack.isSameItemSameTags(output, result)
                && output.getCount() + result.getCount() <= Math.min(output.getMaxStackSize(), OUTPUT_LIMIT);
    }

    /** 一件剩余物：这格空了放回这格 → 同物品叠上 → 并进产物格 → 记下来从顶上掉出 */
    private void placeRemainder(int cell, ItemStack rest, List<ItemStack> drops) {
        ItemStack current = inventory.getStackInSlot(cell);
        if (current.isEmpty()) {
            inventory.setStackInSlot(cell, rest);
            return;
        }
        if (ItemStack.isSameItemSameTags(current, rest) && current.getCount() + rest.getCount() <= current.getMaxStackSize()) {
            ItemStack merged = current.copy();
            merged.grow(rest.getCount());
            inventory.setStackInSlot(cell, merged);
            return;
        }
        ItemStack output = inventory.getStackInSlot(SLOT_OUTPUT);
        if (ItemStack.isSameItemSameTags(output, rest)
                && output.getCount() + rest.getCount() <= Math.min(output.getMaxStackSize(), OUTPUT_LIMIT)) {
            ItemStack merged = output.copy();
            merged.grow(rest.getCount());
            inventory.setStackInSlot(SLOT_OUTPUT, merged);
            return;
        }
        drops.add(rest);
    }

    /** 放不回去的剩余物从机器顶上弹出来（每次合成最多 9 个，照原版掉落物的初速度） */
    private void dropOnTop(List<ItemStack> drops) {
        if (drops.isEmpty() || level == null) {
            return;
        }
        double x = worldPosition.getX() + 0.5D;
        double y = worldPosition.getY() + 1.0D;
        double z = worldPosition.getZ() + 0.5D;
        for (ItemStack stack : drops) {
            ItemEntity entity = new ItemEntity(level, x, y, z, stack);
            entity.setDefaultPickUpDelay();
            level.addFreshEntity(entity);
        }
    }

    private static ItemStack[] snapshot(ItemStack[] cells) {
        ItemStack[] out = new ItemStack[cells.length];
        for (int i = 0; i < cells.length; i++) {
            out[i] = cells[i].copyWithCount(1);
        }
        return out;
    }

    /** 两组格子的物品和 NBT 是否都一样（空对空；不比数量——数量不影响配方匹配） */
    private static boolean sameItems(ItemStack[] a, ItemStack[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i].isEmpty() != b[i].isEmpty() || (!a[i].isEmpty() && !ItemStack.isSameItemSameTags(a[i], b[i]))) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 存盘与界面
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        gate.save(tag, "pulse");
        if (level != null && !level.isClientSide) {
            // 手放的要在存盘前认出来：读档时按当时的内容重记，没核对过的手放原料会被当成自动化送来的
            for (int i = 0; i < GRID_SLOTS; i++) {
                reconcile(i, 0);
            }
        }
        CompoundTag line = new CompoundTag();
        line.putInt(TAG_ASSEMBLY_CRAFTS, assemblyCrafts);
        if (assemblyPending) {
            line.putBoolean(TAG_ASSEMBLY_PENDING, true);
        }
        line.putIntArray(TAG_ASSEMBLY_MANUAL, manualCount);
        tag.put(TAG_ASSEMBLY, line);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        gate.load(tag, "pulse");
        patternCache.invalidate();
        missedGrid = null;
        missedManager = null;
        // 第十批以前的存档没有这一段：格子里原有的东西不知道怎么来的，按自动化送来的算（少等一轮），计数从 0 开始
        CompoundTag line = tag.getCompound(TAG_ASSEMBLY);
        assemblyCrafts = Math.max(0, line.getInt(TAG_ASSEMBLY_CRAFTS));
        assemblyPending = line.getBoolean(TAG_ASSEMBLY_PENDING);
        int[] manual = line.getIntArray(TAG_ASSEMBLY_MANUAL);
        for (int i = 0; i < GRID_SLOTS; i++) {
            manualCount[i] = i < manual.length ? Math.max(0, manual[i]) : 0;
            remember(i);
        }
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CrafterMenu(id, inventory, this);
    }
}
