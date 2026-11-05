package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECComputer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 宅男套装的材料总账（第八批 M2，batch8.md §2.2 / §6.4）：从游戏里实际载入的配方（RecipeManager）逐级展开，
 * 和 {@code tools/gen/computer_bom.py} 从配方 JSON 算出来的数一致（脚本的输出就是下面的期望值，手册 computer/costs 的表也是它）。
 * 服主用数据包改了配方产量，这里第一时间挂，提醒手册的总账过时了。
 * <p>
 * 展开规则同脚本：只展开 {@code computer/*} 21 条（力场穹顶除外）和恶魔核心（demonic/demonic_core），几件东西要的中间件先合在一起、每种按整次合成向上取整。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class ComputerCostTests {

    private static final String EMPTY = "empty";

    private ComputerCostTests() {
    }

    /** 产出物 → (每次几个, 原料 → 个数)；原料按 Ingredient 的第一个候选认（标签原料只有逻辑核心的红石 / 青金石 / 石英） */
    private record Recipe(int count, Map<Item, Integer> ingredients) {
    }

    private static Map<Item, Recipe> load(ServerLevel level) {
        Map<Item, Recipe> recipes = new HashMap<>();
        for (CraftingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ResourceLocation id = recipe.getId();
            // 力场穹顶（第十批 10c）的配方也在 computer/ 下，但它不是套装的零件，不进总账
            boolean computer = id.getNamespace().equals(EssentialCraft5.MODID) && id.getPath().startsWith("computer/")
                    && !id.getPath().equals("computer/force_dome");
            if (!computer && !id.equals(EssentialCraft5.id("demonic/demonic_core"))) {
                continue;
            }
            Map<Item, Integer> ingredients = new LinkedHashMap<>();
            for (Ingredient ingredient : recipe.getIngredients()) {
                ItemStack[] options = ingredient.getItems();
                if (options.length > 0 && !options[0].isEmpty()) {
                    ingredients.merge(options[0].getItem(), 1, Integer::sum);
                }
            }
            ItemStack result = recipe.getResultItem(level.registryAccess());
            recipes.put(result.getItem(), new Recipe(result.getCount(), ingredients));
        }
        return recipes;
    }

    /** 用的在前、被用的在后 */
    private static List<Item> order(Map<Item, Recipe> recipes) {
        List<Item> order = new ArrayList<>();
        Set<Item> seen = new HashSet<>();
        List<Item> roots = new ArrayList<>(recipes.keySet());
        roots.sort((a, b) -> String.valueOf(ForgeRegistries.ITEMS.getKey(a)).compareTo(String.valueOf(ForgeRegistries.ITEMS.getKey(b))));
        for (Item item : roots) {
            visit(item, recipes, seen, order);
        }
        java.util.Collections.reverse(order);
        return order;
    }

    private static void visit(Item item, Map<Item, Recipe> recipes, Set<Item> seen, List<Item> order) {
        if (!recipes.containsKey(item) || !seen.add(item)) {
            return;
        }
        for (Item ingredient : recipes.get(item).ingredients().keySet()) {
            visit(ingredient, recipes, seen, order);
        }
        order.add(item);
    }

    /** targets 合起来要多少原料（不展开的东西） */
    private static Map<Item, Integer> expand(Map<Item, Recipe> recipes, List<Item> targets) {
        Map<Item, Integer> demand = new HashMap<>();
        for (Item target : targets) {
            demand.merge(target, 1, Integer::sum);
        }
        for (Item item : order(recipes)) {
            int need = demand.getOrDefault(item, 0);
            if (need <= 0) {
                continue;
            }
            Recipe recipe = recipes.get(item);
            int times = (need + recipe.count() - 1) / recipe.count();
            recipe.ingredients().forEach((ingredient, n) -> demand.merge(ingredient, times * n, Integer::sum));
        }
        Map<Item, Integer> base = new HashMap<>();
        demand.forEach((item, n) -> {
            if (!recipes.containsKey(item) && n > 0) {
                base.put(item, n);
            }
        });
        return base;
    }

    private static Item item(String name) {
        return ForgeRegistries.ITEMS.getValue(EssentialCraft5.id(name));
    }

    private static void check(GameTestHelper helper, String label, Map<Item, Integer> base, String name, int expected) {
        int actual = base.getOrDefault(item(name), 0);
        helper.assertTrue(actual == expected, label + "：" + name + " 应是 " + expected + "（tools/gen/computer_bom.py），实际 " + actual);
    }

    private static List<Item> armor() {
        return List.of(ECComputer.COMPUTER_HELMET.get(), ECComputer.COMPUTER_CHESTPLATE.get(), ECComputer.COMPUTER_LEGGINGS.get(),
                ECComputer.COMPUTER_BOOTS.get());
    }

    /** 全套 + 反重力板（合起来做）：恶魔板 329、风之水晶 55、MRU 反应核 5、永恒物质 3、元素魔晶 96；只做四件：287 / 48 / 4 */
    @GameTest(template = EMPTY)
    public static void computerCostFullSet(GameTestHelper helper) {
        Map<Item, Recipe> recipes = load(helper.getLevel());
        helper.assertTrue(recipes.size() == 22, "应载入 21 条宅男配方和恶魔核心，实际 " + recipes.size());
        List<Item> all = new ArrayList<>(armor());
        all.add(ECComputer.COMPUTER_BOARD.get());
        Map<Item, Integer> full = expand(recipes, all);
        check(helper, "全套 + 反重力板", full, "demonic_plate", 329);
        check(helper, "全套 + 反重力板", full, "wind_gem", 55);
        check(helper, "全套 + 反重力板", full, "void_mru_reactor", 5);
        check(helper, "全套 + 反重力板", full, "matter_of_eternity", 3);
        check(helper, "全套 + 反重力板", full, "elemental_gem", 96);
        check(helper, "全套 + 反重力板", full, "mru_resonating_crystal", 56);
        check(helper, "全套 + 反重力板", full, "fading_crystal", 56);
        check(helper, "全套 + 反重力板", full, "mithriline_crystal", 48);
        check(helper, "全套 + 反重力板", full, "pale_plate", 20);
        check(helper, "全套 + 反重力板", full, "combined_magical_alloys", 34);
        for (String core : List.of("addition_core", "subtraction_core", "multiplication_core", "division_core")) {
            check(helper, "全套 + 反重力板", full, core, 1);
        }
        Map<Item, Integer> four = expand(recipes, armor());
        check(helper, "只做四件", four, "demonic_plate", 287);
        check(helper, "只做四件", four, "wind_gem", 48);
        check(helper, "只做四件", four, "void_mru_reactor", 4);
        helper.succeed();
    }

    /** 逐件单算的恶魔板：头盔 126、胸甲 105、护腿 96、靴子 83、反重力板 68（单算的和比合起来做多，多出的零件能互相匀） */
    @GameTest(template = EMPTY)
    public static void computerCostPerPiece(GameTestHelper helper) {
        Map<Item, Recipe> recipes = load(helper.getLevel());
        String[] names = {"computer_helmet", "computer_chestplate", "computer_leggings", "computer_boots", "computer_board"};
        int[] plates = {126, 105, 96, 83, 68};
        int[] gems = {20, 17, 15, 11, 14};
        int sum = 0;
        for (int i = 0; i < names.length; i++) {
            Map<Item, Integer> base = expand(recipes, List.of(item(names[i])));
            check(helper, names[i], base, "demonic_plate", plates[i]);
            check(helper, names[i], base, "wind_gem", gems[i]);
            sum += plates[i];
        }
        helper.assertTrue(sum > 329, "逐件单算的和应比合起来做多");
        helper.succeed();
    }
}
