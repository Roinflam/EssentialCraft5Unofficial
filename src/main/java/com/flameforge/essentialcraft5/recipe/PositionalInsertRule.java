package com.flameforge.essentialcraft5.recipe;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 按位置匹配原料的机器（魔力工作台 5 格、辐射仓 2 格）给漏斗、管道用的插入规则。玩家手放不走这里。
 * <p>
 * 原作这类机器的原料格对自动化什么都收：漏斗按槽号顺序塞，东西进了配方里不该放的位置，配方对不上，机器卡死，只能手动掏出来。
 * 这里改成按配方表判断每一格要不要这件物品：
 * <ul>
 *   <li>格子里已经有东西：只收同种物品（补货）。</li>
 *   <li>格子是空的：要有一条配方在这个位置接受它，而且其余已经有东西的格子也和这条配方对得上。
 *       所以五格全空时第一件原料就能自动放对位置，不用先手动摆一份。</li>
 *   <li>均摊：好几格都要这件物品时，只让数量最少的那格收。漏斗按槽号挨个试，结果就是轮流补，
 *       不会把四个角的料全堆进第一格、等其它角吃空了停机。</li>
 *   <li>平手（几格数量一样，实际有差别的只有「几个空格都能收」）依次比：
 *       <ol>
 *         <li>机器<b>上一次出产物用的配方</b>（机器存盘，调用方传进来）在哪一格用这件东西、又和其余格对得上，先进哪格；</li>
 *         <li>「在更多配方里用这个位置」的格子先进；</li>
 *         <li>槽号小的先进。</li>
 *       </ol>
 *       例：辐射仓里铁锭在强化石、强化玻璃里放下格，在 MRU 磁体（第四批）里放上格。没做过东西、或者上一次做的是强化石的辐射仓，
 *       漏斗送来的铁锭进下格，做强化石的自动化线不会被先到的铁锭占住上格卡死。要用漏斗做磁体，第一次先手放一块铁锭在上格
 *       （或者让精华先到：精华只有磁体用、只能进下格，之后的铁锭就只能进上格）；做出一个磁体以后机器记住了这条配方，
 *       之后上下两格同时吃空、铁锭又先到，也照样进上格，磁体线能一直自己跑。换回做强化石时同理，先手放一件定下位置，做出一份就记住了。</li>
 * </ul>
 * 第十三批（batch13.md §3.3.5）：配方实现 {@link SlotTest} 时按它判断一格收不收（同调升阶的中心只收这个家族、这一阶、同调度攒满的装备），
 * 不只看原料本身；四角要好几个的格子照样一件件补到够，不另限上限（材料都能叠到 64，够任何一格的数量）。
 * {@link SlotTest#automatable} 为假的格子漏斗一律不往里放（同调转移的新件、旧件：新旧两件配不配得成对，逐格问是问不出来的，
 * 漏斗照逐格的规则会把一件攒了点的宅男胸甲塞进转移的左上格、把它当旧件，工作台就此卡住；转移本来就要玩家手动取回旧件，两件都只许手放）。
 * 这些格子已经放好东西时，别的格子照常按这条配方补料（手放好新旧两件以后，漏斗照样能送水晶粉、合金）。
 */
public final class PositionalInsertRule {

    private PositionalInsertRule() {
    }

    /**
     * 配方对某一格的要求比 {@code getIngredients()} 里的原料更细时实现它（例：要这个家族里几阶、攒满了的装备）。
     * 漏斗判断「这条配方在这一格要不要这件」时用它代替原料的 test。
     */
    public interface SlotTest {
        /** 第 index 格（和 getIngredients 的下标一致）收不收 stack；空格要求（原料为空）照样返回 stack.isEmpty() */
        boolean acceptsAt(int index, ItemStack stack);

        /** 漏斗能不能往第 index 格放东西（只许手放的格子返回 false）；格子里已经有的东西照样按 {@link #acceptsAt} 对 */
        default boolean automatable(int index) {
            return true;
        }
    }

    /** 配方第 index 格收不收这件：实现了 {@link SlotTest} 的按它，否则按原料 */
    private static boolean test(Recipe<?> recipe, NonNullList<Ingredient> ingredients, int index, ItemStack stack) {
        if (recipe instanceof SlotTest slots) {
            return slots.acceptsAt(index, stack);
        }
        return ingredients.get(index).test(stack);
    }

    /** 漏斗往配方第 index 格放这件行不行：这一格许自动放，而且收这件 */
    private static boolean testInsert(Recipe<?> recipe, NonNullList<Ingredient> ingredients, int index, ItemStack stack) {
        if (recipe instanceof SlotTest slots && !slots.automatable(index)) {
            return false;
        }
        return test(recipe, ingredients, index, stack);
    }

    /** 同 {@link #accepts(List, ItemStack[], int, ItemStack, ResourceLocation)}，不带「上一次的配方」 */
    public static boolean accepts(List<? extends Recipe<?>> recipes, ItemStack[] grid, int index, ItemStack stack) {
        return accepts(recipes, grid, index, stack, null);
    }

    /**
     * @param recipes    这台机器的全部配方（原料按位置排，和 grid 一一对应）
     * @param grid       各原料格现在的物品
     * @param index      要插入的原料格下标（0 起，对应 grid）
     * @param lastRecipe 机器上一次出产物用的配方 ID，平手时先照它放（见类注释）；没有时为 null，
     *                   不在 recipes 里（数据包删了）时当没有
     */
    public static boolean accepts(List<? extends Recipe<?>> recipes, ItemStack[] grid, int index, ItemStack stack,
                                  @Nullable ResourceLocation lastRecipe) {
        if (stack.isEmpty() || !wants(recipes, grid, index, stack)) {
            return false;
        }
        Recipe<?> hint = find(recipes, lastRecipe);
        int count = grid[index].getCount();
        for (int j = 0; j < grid.length; j++) {
            if (j == index) {
                continue;
            }
            int other = grid[j].getCount();
            if (other > count || !wants(recipes, grid, j, stack)) {
                continue;
            }
            if (other < count || prefers(recipes, hint, grid, stack, j, index)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static Recipe<?> find(List<? extends Recipe<?>> recipes, @Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        for (Recipe<?> recipe : recipes) {
            if (id.equals(recipe.getId())) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * 两格数量一样、都要这件物品时 j 是否比 index 优先（数量相同时都走这里，不只两格都空）：
     * 先看上一次的配方（hint）是不是只在其中一格用它、又和其余格对得上；再比「多少条配方在这个位置用它」，多的优先；
     * 最后槽号小的优先。两格都有东西时它们是同种物品，hint 对两格的判断总是一样，实际只影响几个空格之间的取舍。
     */
    private static boolean prefers(List<? extends Recipe<?>> recipes, @Nullable Recipe<?> hint, ItemStack[] grid, ItemStack stack,
                                   int j, int index) {
        if (hint != null) {
            boolean hintJ = fitsAt(hint, grid, j, stack);
            boolean hintIndex = fitsAt(hint, grid, index, stack);
            if (hintJ != hintIndex) {
                return hintJ;
            }
        }
        int a = usesAt(recipes, j, stack);
        int b = usesAt(recipes, index, stack);
        return a != b ? a > b : j < index;
    }

    /** 多少条配方在 index 位置接受这件物品（不看别的格） */
    private static int usesAt(List<? extends Recipe<?>> recipes, int index, ItemStack stack) {
        int n = 0;
        for (Recipe<?> recipe : recipes) {
            NonNullList<Ingredient> ingredients = recipe.getIngredients();
            if (index < ingredients.size() && testInsert(recipe, ingredients, index, stack)) {
                n++;
            }
        }
        return n;
    }

    /** 不考虑均摊时，这一格要不要这件物品 */
    private static boolean wants(List<? extends Recipe<?>> recipes, ItemStack[] grid, int index, ItemStack stack) {
        ItemStack current = grid[index];
        if (!current.isEmpty()) {
            return current.getCount() < current.getMaxStackSize() && ItemStack.isSameItemSameTags(current, stack);
        }
        for (Recipe<?> recipe : recipes) {
            if (fitsAt(recipe, grid, index, stack)) {
                return true;
            }
        }
        return false;
    }

    /** 这条配方在 index 位置许漏斗放 stack，且其余非空格都和配方对应位置对得上（空格不管） */
    private static boolean fitsAt(Recipe<?> recipe, ItemStack[] grid, int index, ItemStack stack) {
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.size() != grid.length || !testInsert(recipe, ingredients, index, stack)) {
            return false;
        }
        for (int j = 0; j < grid.length; j++) {
            if (j != index && !grid[j].isEmpty() && !test(recipe, ingredients, j, grid[j])) {
                return false;
            }
        }
        return true;
    }
}
