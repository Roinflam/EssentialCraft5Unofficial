package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.recipe.CrafterGrid;
import com.flameforge.essentialcraft5.recipe.CraftingPattern;
import com.flameforge.essentialcraft5.recipe.CraftingRecipeIndex;
import com.flameforge.essentialcraft5.registry.ECCrafter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 合成框架的样板界面（原作 ContainerCraftingFrame + GuiCraftingFrame），手持物品菜单（{@link HeldItemMenu}）：
 * 9 个样本幽灵格（按行排，坐标照原作 (30 + x·18, 17 + y·18)）+ 1 个产物格 (124, 35) + 玩家背包 (8, 84)。
 *
 * <h2>操作</h2>
 * <ul>
 *   <li>样本格：照 {@link GhostSlots#handleClick}——拿着东西左键 = 记一份样本（不消耗），空手或右键 = 清掉；数字键 = 记那格快捷栏的物品；
 *       从背包 Shift 点 = 复制进第一个空格。每次改完立刻写回框架（{@link #saveTo}），配方跟着样本重算（记下的配方还配得上就留着）。</li>
 *   <li>产物格（{@link #SLOT_RESULT}）：拿着东西点 = <b>反查</b>（{@link CraftingRecipeIndex#next}）：找产出它的配方、把样本摆好；
 *       同一样东西再点一次换下一条配方。空手点、Shift 点、丢弃键 = 清空整个样板。数字键 = 用那格快捷栏的物品反查。只在服务端算，结果由菜单同步回去。</li>
 *   <li>按钮 {@link #BUTTON_MODE}（左上角 (6, 6)）：切换「按配方原料 / 精确」（{@code clickMenuButton}，porting_guide §9）。</li>
 * </ul>
 * 模式用一个 {@link DataSlot} 同步给界面：从副手打开时，客户端背包里那件框架的 NBT 在界面开着期间不会更新（原版只同步当前菜单的格子），不能靠读它。
 */
public class CraftingFrameMenu extends HeldItemMenu {

    public static final int GRID_X = 30;
    public static final int GRID_Y = 17;
    public static final int RESULT_X = 124;
    public static final int RESULT_Y = 35;
    /** 产物格在菜单里的槽号（样本格是 0~8） */
    public static final int SLOT_RESULT = CraftingPattern.SIZE;
    /** 模式按钮 */
    public static final int BUTTON_MODE = 0;

    private final SimpleContainer samples = new SimpleContainer(CraftingPattern.SIZE);
    private final SimpleContainer result = new SimpleContainer(1);
    private final Level level;
    private CraftingPattern.Mode mode;
    /** 反查选定的配方：下一次 {@link #saveTo} 直接写它（不按样本重查），用完清掉 */
    @Nullable
    private ResourceLocation forcedRecipe;

    /** 客户端（ECMenus 工厂） */
    public CraftingFrameMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, HeldItemMenu.readHand(buf));
    }

    public CraftingFrameMenu(int id, Inventory inventory, InteractionHand hand) {
        super(ECCrafter.CRAFTING_FRAME_MENU.get(), id, inventory, hand);
        this.level = inventory.player.level();
        ItemStack held = held();
        ItemStack[] saved = CraftingPattern.samples(held);
        for (int i = 0; i < CraftingPattern.SIZE; i++) {
            samples.setItem(i, saved[i]);
            addSlot(new GhostSlot(samples, i, GRID_X + i % CrafterGrid.WIDTH * 18, GRID_Y + i / CrafterGrid.WIDTH * 18));
        }
        // 产物格：只显示，不收任何样本（从背包 Shift 复制样本时不会落到这里），点击由 clicked 单独处理
        addSlot(new GhostSlot(result, 0, RESULT_X, RESULT_Y, stack -> false));
        this.mode = CraftingPattern.mode(held);
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return mode.ordinal();
            }

            @Override
            public void set(int value) {
                mode = CraftingPattern.Mode.byOrdinal(value);
            }
        });
        addPlayerInventory(inventory, 8, 84);
        refreshResult();
    }

    /** 当前模式（客户端是同步来的） */
    public CraftingPattern.Mode mode() {
        return mode;
    }

    /** 第 i 格样本（界面用） */
    public ItemStack sample(int i) {
        return samples.getItem(i);
    }

    @Override
    public void clicked(int slotId, int button, ClickType type, Player player) {
        if (slotId == SLOT_RESULT) {
            clickResult(button, type, player);
            return;
        }
        super.clicked(slotId, button, type, player);
        refreshResult();
    }

    /** 产物格的点击（规则见类注释）；只在服务端做 */
    private void clickResult(int button, ClickType type, Player player) {
        if (player.level().isClientSide || !stillValid(player)) {
            return;
        }
        ItemStack product;
        switch (type) {
            case PICKUP -> product = getCarried();
            case QUICK_MOVE, THROW -> product = ItemStack.EMPTY;
            case SWAP -> {
                // 数字键换手持那格一律不理（同 HeldItemMenu）；只认快捷栏和副手
                if (button == heldSlot() || !(Inventory.isHotbarSlot(button) || button == Inventory.SLOT_OFFHAND)) {
                    return;
                }
                product = player.getInventory().getItem(button);
            }
            default -> {
                return;
            }
        }
        ItemStack[] before = currentSamples();
        ItemStack[] after;
        ResourceLocation chosen = null;
        if (product.isEmpty()) {
            after = CraftingPattern.emptySamples();
        } else {
            CraftingRecipeIndex.Choice choice = CraftingRecipeIndex.next(level, product, CraftingPattern.recipeId(held()));
            if (choice == null) {
                return;   // 没有能摆出来的工作台配方：什么都不变
            }
            after = choice.samples();
            chosen = choice.recipe().getId();
        }
        setSamples(after);
        forcedRecipe = chosen;
        boolean saved;
        try {
            saved = commit();
        } finally {
            forcedRecipe = null;
        }
        if (!saved) {
            setSamples(before);
        }
        refreshResult();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().isClientSide || id != BUTTON_MODE) {
            return false;
        }
        CraftingPattern.Mode old = mode;
        mode = mode.next();
        if (!commit()) {
            mode = old;
            return false;
        }
        return true;
    }

    /**
     * 写回框架：样本、模式、配方。配方 = 反查选定的那条；否则记下的那条还配得上样本就留着，不然按样本现查（和原版工作台每改一格重算一样）。
     */
    @Override
    protected boolean saveTo(ItemStack held) {
        ItemStack[] current = currentSamples();
        ResourceLocation recipe = forcedRecipe != null && !CraftingPattern.allEmpty(current)
                ? forcedRecipe
                : CraftingPattern.matchRecipe(current, CraftingPattern.recipeId(held), level);
        CraftingPattern.write(held, current, mode, recipe);
        return true;
    }

    /** 产物格显示：按框架记下的配方现算（不信存档），配不上样本就空着 */
    private void refreshResult() {
        ItemStack held = held();
        ItemStack shown = ItemStack.EMPTY;
        ItemStack[] current = currentSamples();
        CraftingRecipe recipe = CraftingPattern.craftingRecipe(level.getRecipeManager(), CraftingPattern.recipeId(held));
        if (recipe != null && !CraftingPattern.allEmpty(current)) {
            try {
                CrafterGrid grid = new CrafterGrid(current);
                if (recipe.matches(grid, level)) {
                    shown = recipe.assemble(grid, level.registryAccess());
                }
            } catch (RuntimeException ignored) {
                // 别的模组的配方抛异常：产物格空着
            }
        }
        if (!ItemStack.matches(result.getItem(0), shown)) {
            result.setItem(0, shown);
        }
    }

    private ItemStack[] currentSamples() {
        ItemStack[] out = new ItemStack[CraftingPattern.SIZE];
        for (int i = 0; i < out.length; i++) {
            out[i] = samples.getItem(i).copy();
        }
        return out;
    }

    private void setSamples(ItemStack[] values) {
        for (int i = 0; i < CraftingPattern.SIZE; i++) {
            samples.setItem(i, values[i]);
        }
    }
}
