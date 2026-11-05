package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.ComputerArmorItem;
import com.flameforge.essentialcraft5.item.ComputerBoardItem;
import com.flameforge.essentialcraft5.recipe.AttunementAscensionRecipe;
import com.flameforge.essentialcraft5.recipe.AttunementTransferRecipe;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECLeyCondenser;
import com.flameforge.essentialcraft5.registry.ECTags;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.FrameType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 电脑一系的零件、配方与进度（第八批 M2，batch8.md §4.2；产量按 D4 = A）：
 * <ul>
 *   <li>15 种零件、四件套装、反重力板都注册了；零件是普通物品，不在本模组的任何标签里（不进 cores/magic、plates/magic 这些代用标签）；
 *       反重力板一组 1 个、在 curios:belt。</li>
 *   <li>21 条配方逐条按 1.10.2 的形状摆原料，RecipeManager 认得出是那一条、产量对（基础件和富神经元每次 4、中间件 2、其余 1）；
 *       原作 1.12 JSON 抄错的三条（视觉透镜、胸甲、反重力板）照 1.12 的摆法摆不出东西。</li>
 *   <li>数学核心没有任何配方产出；零件和套装不当别的配方的原料（没有拆回原料的配方）。</li>
 *   <li>手册打勾进度 7 个（含可选的反重力板）；进度树终点 main/computer_armor：父 main/inquisitorium（第十批起主线是全息 → 审判庭 → 宅男套装）、challenge 框，
 *       四件同时在物品栏里才完成（只拿到三件不算）。</li>
 * </ul>
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class ComputerPartsTests {

    private static final String EMPTY = "empty";

    private ComputerPartsTests() {
    }

    // ================================================================ 工具

    private static Item item(String name) {
        ResourceLocation id = name.contains(":") ? ResourceLocation.tryParse(name) : EssentialCraft5.id(name);
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            throw new IllegalStateException("没有注册 " + id);
        }
        return item;
    }

    /** 一条配方的期望：九格（行优先，空格 = 空）与字符 → 物品、产量 */
    private record Expected(String name, String[] pattern, Map<Character, String> key, int count) {

        Item[] grid() {
            Item[] grid = new Item[9];
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    char ch = pattern[row].charAt(col);
                    grid[row * 3 + col] = ch == ' ' ? Items.AIR : item(key.get(ch));
                }
            }
            return grid;
        }
    }

    private static Map<Character, String> key(Object... pairs) {
        Map<Character, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Character) pairs[i], (String) pairs[i + 1]);
        }
        return map;
    }

    /** 1.10.2 代码配方（b62e0f5^ RecipeRegistry L488~703）的形状，产量按 D4 = A */
    private static List<Expected> expected() {
        String plate = "demonic_plate";
        String nep = "neuronic_enriched_plating";
        return List.of(
                new Expected("neuronic_enriched_plating", new String[]{"#C#", "@#@", "#C#"},
                        key('#', plate, 'C', "wind_gem", '@', "combined_magical_alloys"), 4),
                new Expected("force_absorber", new String[]{"#@#", "@C@", "#@#"}, key('#', plate, '@', "mithriline_crystal", 'C', "wind_gem"), 4),
                new Expected("force_emitter", new String[]{"#@#", "@C@", "#@#"}, key('#', plate, '@', "fading_crystal", 'C', "wind_gem"), 4),
                new Expected("particle_catcher", new String[]{"#@#", "@C@", "#@#"}, key('#', plate, '@', "fading_crystal", 'C', "force_absorber"), 4),
                new Expected("particle_emitter", new String[]{"#@#", "@C@", "#@#"},
                        key('#', plate, '@', "mru_resonating_crystal", 'C', "force_emitter"), 4),
                new Expected("force_field_core", new String[]{"#@#", "@C@", "#@#"},
                        key('#', "pale_plate", '@', "demonic_core", 'C', "void_mru_reactor"), 4),
                new Expected("logic_core", new String[]{"#R#", "LQL", "#R#"},
                        key('#', plate, 'R', "minecraft:redstone", 'L', "minecraft:lapis_lazuli", 'Q', "minecraft:quartz"), 1),
                new Expected("force_field_generator", new String[]{"#@#", "@C@", "#@#"},
                        key('#', "force_emitter", '@', "force_field_core", 'C', "matter_of_eternity"), 2),
                new Expected("force_field_plating", new String[]{"#@#", "@C@", "#@#"},
                        key('#', "particle_catcher", '@', "particle_emitter", 'C', "force_field_core"), 2),
                new Expected("pressure_stabilizer", new String[]{"#@#", "@@@", "#@#"}, key('#', "force_absorber", '@', nep), 2),
                new Expected("repulsion_generator", new String[]{"#@#", "@C@", "#@#"},
                        key('#', "particle_emitter", '@', "force_emitter", 'C', nep), 2),
                new Expected("thruster_engine", new String[]{"#@#", "$#$", "P#P"},
                        key('#', "force_emitter", '@', "particle_emitter", '$', "pressure_stabilizer", 'P', nep), 2),
                new Expected("sound_manager", new String[]{"#C#", "767", "#C#"},
                        key('#', plate, 'C', "demonic_core", '7', "force_absorber", '6', "pressure_stabilizer"), 1),
                new Expected("speaker_plate", new String[]{"#@#", "@C@", "#@#"}, key('#', "force_absorber", '@', nep, 'C', "sound_manager"), 1),
                new Expected("vision_lens", new String[]{"#@#", "ACA", "#@#"},
                        key('#', nep, '@', "force_absorber", 'A', "sun_imbued_glass", 'C', "magic_purified_glass_alloy"), 1),
                new Expected("computer_engine", new String[]{"#+#", "/C*", "#-#"},
                        key('#', nep, '+', "addition_core", '-', "subtraction_core", '*', "multiplication_core", '/', "division_core", 'C', "logic_core"), 1),
                new Expected("computer_helmet", new String[]{"FCF", "SLS", "EOE"},
                        key('F', "force_field_plating", 'C', "force_field_generator", 'S', "speaker_plate", 'L', "vision_lens", 'E', nep,
                                'O', "pressure_stabilizer"), 1),
                new Expected("computer_chestplate", new String[]{"R*S", "FCF", "ETE"},
                        key('R', "repulsion_generator", '*', "computer_engine", 'S', "pressure_stabilizer", 'F', "force_field_plating",
                                'C', "force_field_generator", 'E', nep, 'T', "thruster_engine"), 1),
                new Expected("computer_leggings", new String[]{"FRF", "TCT", "ESE"},
                        key('F', "force_field_plating", 'R', "repulsion_generator", 'T', "thruster_engine", 'C', "force_field_generator", 'E', nep,
                                'S', "pressure_stabilizer"), 1),
                new Expected("computer_boots", new String[]{"FSF", "ECE", "SFS"},
                        key('F', "force_field_plating", 'S', "pressure_stabilizer", 'E', nep, 'C', "force_field_generator"), 1),
                new Expected("computer_board", new String[]{"SCS", "EEE", "RTR"},
                        key('S', "pressure_stabilizer", 'C', "force_field_generator", 'E', nep, 'R', "repulsion_generator", 'T', "thruster_engine"), 1));
    }

    private static Optional<CraftingRecipe> craft(GameTestHelper helper, Item[] grid, CraftingContainer[] out) {
        ServerLevel level = helper.getLevel();
        CraftingContainer container = new TransientCraftingContainer(new DummyMenu(), 3, 3);
        for (int i = 0; i < 9; i++) {
            container.setItem(i, grid[i] == Items.AIR ? ItemStack.EMPTY : new ItemStack(grid[i]));
        }
        out[0] = container;
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, container, level);
    }

    /** 本模块的物品：15 种零件、四件套装、反重力板 */
    private static List<Item> moduleItems() {
        List<Item> items = new java.util.ArrayList<>();
        for (RegistryObject<Item> part : ECComputer.parts()) {
            items.add(part.get());
        }
        for (RegistryObject<ComputerArmorItem> armor : ECComputer.armor()) {
            items.add(armor.get());
        }
        items.add(ECComputer.COMPUTER_BOARD.get());
        return items;
    }

    // ================================================================ 注册

    /** 20 个注册名都在；零件是普通物品、一组 64、不在本模组的任何标签里；反重力板一组 1、在 curios:belt；套装四件按部位 */
    @GameTest(template = EMPTY)
    public static void computerPartsRegistered(GameTestHelper helper) {
        List<String> parts = List.of("force_absorber", "force_emitter", "particle_catcher", "particle_emitter", "force_field_core", "logic_core",
                "force_field_generator", "force_field_plating", "pressure_stabilizer", "repulsion_generator", "thruster_engine", "sound_manager",
                "speaker_plate", "vision_lens", "computer_engine");
        helper.assertTrue(ECComputer.parts().size() == parts.size(), "应有 15 种零件");
        for (int i = 0; i < parts.size(); i++) {
            Item part = item(parts.get(i));
            helper.assertTrue(ECComputer.parts().get(i).get() == part, parts.get(i) + " 的注册名对不上");
            helper.assertTrue(part.getClass() == Item.class, parts.get(i) + " 应是普通物品，实际 " + part.getClass().getSimpleName());
            ItemStack stack = new ItemStack(part);
            helper.assertTrue(stack.getMaxStackSize() == 64, parts.get(i) + " 一组 64");
            helper.assertTrue(stack.getTags().noneMatch(tag -> tag.location().getNamespace().equals(EssentialCraft5.MODID)),
                    parts.get(i) + " 不应在本模组的标签里（代用标签之类）");
        }
        // 四种数学核心另在 #essentialcraft5:math_cores 里（第九批地脉凝聚器的配方任选一种）；电脑引擎仍要指定的核心，
        // 由 LeyCondenserTests.condenserRecipeAndTag 守着
        for (String core : List.of("neuronic_enriched_plating", "addition_core", "subtraction_core", "multiplication_core", "division_core")) {
            helper.assertTrue(new ItemStack(item(core)).getTags().noneMatch(tag -> tag.location().getNamespace().equals(EssentialCraft5.MODID)
                            && !tag.equals(ECLeyCondenser.MATH_CORES)),
                    core + " 不应在本模组的标签里（#math_cores 除外）");
        }
        String[] armor = {"computer_helmet", "computer_chestplate", "computer_leggings", "computer_boots"};
        for (int i = 0; i < armor.length; i++) {
            Item piece = item(armor[i]);
            helper.assertTrue(piece instanceof ComputerArmorItem computer && computer.getType().ordinal() == i, armor[i] + " 应是这个部位的宅男盔甲");
        }
        Item board = item("computer_board");
        helper.assertTrue(board instanceof ComputerBoardItem, "反重力板的类");
        ItemStack boardStack = new ItemStack(board);
        helper.assertTrue(boardStack.getMaxStackSize() == 1, "反重力板一组 1 个");
        helper.assertTrue(boardStack.is(ECTags.CURIOS_BELT), "反重力板应在 curios:belt（要先跑 runData）");
        helper.succeed();
    }

    // ================================================================ 配方

    /** 21 条配方：按 1.10.2 的形状摆，认得出是 computer/<名字> 那一条，产量对，原料全被消耗 */
    @GameTest(template = EMPTY)
    public static void computerRecipesShapesAndYields(GameTestHelper helper) {
        List<Expected> all = expected();
        helper.assertTrue(all.size() == 21, "应有 21 条配方");
        ServerLevel level = helper.getLevel();
        for (Expected expected : all) {
            CraftingContainer[] container = new CraftingContainer[1];
            Optional<CraftingRecipe> recipe = craft(helper, expected.grid(), container);
            ResourceLocation id = EssentialCraft5.id("computer/" + expected.name());
            helper.assertTrue(recipe.isPresent() && recipe.get().getId().equals(id),
                    expected.name() + " 照 1.10.2 摆应匹配 " + id + "，实际 " + recipe.map(r -> r.getId().toString()).orElse("没有"));
            ItemStack out = recipe.get().assemble(container[0], level.registryAccess());
            helper.assertTrue(out.is(item(expected.name())) && out.getCount() == expected.count(),
                    expected.name() + " 应出 " + expected.count() + " 个，实际 " + out.getCount() + " × " + out.getItem());
            helper.assertTrue(recipe.get().getRemainingItems(container[0]).stream().allMatch(ItemStack::isEmpty), expected.name() + "：原料都应被消耗");
        }
        helper.succeed();
    }

    /**
     * 原作 1.12 JSON 抄错的三条照 1.12 的摆法摆不出东西：视觉透镜（照抄了力场核心的原料：淡青板 / 恶魔核心 / 反应核）、
     * 胸甲（F / S 对调：右上力场护板、中排两边压力稳定器）、反重力板（S 写成力场护板）
     */
    @GameTest(template = EMPTY)
    public static void computerRecipesFixedFrom1102(GameTestHelper helper) {
        String nep = "neuronic_enriched_plating";
        List<Expected> wrong = List.of(
                new Expected("vision_lens", new String[]{"#@#", "ACA", "#@#"},
                        key('#', "pale_plate", '@', "demonic_core", 'A', "void_mru_reactor", 'C', "void_mru_reactor"), 1),
                new Expected("computer_chestplate", new String[]{"R*S", "FCF", "ETE"},
                        key('R', "repulsion_generator", '*', "computer_engine", 'S', "force_field_plating", 'F', "pressure_stabilizer",
                                'C', "force_field_generator", 'E', nep, 'T', "thruster_engine"), 1),
                new Expected("computer_board", new String[]{"SCS", "EEE", "RTR"},
                        key('S', "force_field_plating", 'C', "force_field_generator", 'E', nep, 'R', "repulsion_generator", 'T', "thruster_engine"), 1));
        for (Expected expected : wrong) {
            Optional<CraftingRecipe> recipe = craft(helper, expected.grid(), new CraftingContainer[1]);
            helper.assertTrue(recipe.isEmpty(), expected.name() + " 照 1.12 JSON 的摆法不应做得出东西，实际 " + recipe.map(r -> r.getId().toString()).orElse(""));
        }
        helper.succeed();
    }

    /**
     * 本模块以外拿零件当原料、但做的是新东西（不是拆回原料）的配方：全息诱饵投影仪吃视觉透镜和富神经元护板（第十批 10b，batch10 §5.8.2）；
     * 审判庭信标和审判庭头盔 / 胸甲 / 护腿吃富神经元护板（第十批 10c，batch10 §5.10.4、D22）；
     * 空白模块吃富神经元护板和逻辑核心、相位锚模块吃力场核心（第十批 10c，batch10 §3、§5.12.3）；
     * 稳手模块吃压力稳定器、隐匿模块吃视觉透镜（第十二批，batch12.md §3.2）
     */
    private static final Set<String> PART_CONSUMERS = Set.of("hologram/holo_decoy_projector", "inquisitorium/inquisitorium_beacon",
            "inquisitorium/inquisitorium_helmet", "inquisitorium/inquisitorium_chestplate", "inquisitorium/inquisitorium_leggings",
            "terminal/blank_module", "terminal/phase_anchor_module", "terminal/steady_hand_module", "terminal/veil_module");

    /** 数学核心没有任何配方产出；零件、套装、反重力板不当本模块以外任何配方的原料（没有拆回原料的配方；{@link #PART_CONSUMERS} 除外） */
    @GameTest(template = EMPTY)
    public static void computerNoCoreRecipeNoDisassembly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Set<Item> cores = Set.of(ECComputer.ADDITION_CORE.get(), ECComputer.SUBTRACTION_CORE.get(), ECComputer.MULTIPLICATION_CORE.get(),
                ECComputer.DIVISION_CORE.get());
        Set<Item> module = new HashSet<>(moduleItems());
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            ItemStack result = recipe.getResultItem(level.registryAccess());
            helper.assertFalse(!result.isEmpty() && cores.contains(result.getItem()), "数学核心不应有配方：" + recipe.getId());
            if (recipe.getId().getNamespace().equals(EssentialCraft5.MODID)
                    && (recipe.getId().getPath().startsWith("computer/") || PART_CONSUMERS.contains(recipe.getId().getPath()))) {
                continue;
            }
            // 同调升阶 / 转移：中心那件做完原样出来（只改同调），不算拆
            if (recipe instanceof AttunementAscensionRecipe || recipe instanceof AttunementTransferRecipe) {
                continue;
            }
            for (Ingredient ingredient : recipe.getIngredients()) {
                for (ItemStack option : ingredient.getItems()) {
                    helper.assertFalse(module.contains(option.getItem()), recipe.getId() + " 不应拿 " + option.getItem() + " 当原料");
                }
            }
        }
        helper.succeed();
    }

    // ================================================================ 进度

    /** 手册打勾进度 7 个（含可选的反重力板）；main/computer_armor 父 main/inquisitorium、challenge 框；四件同时在物品栏里才完成 */
    @GameTest(template = EMPTY)
    public static void computerAdvancements(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (String name : List.of("guide/hologram/neuronic_plating", "guide/computer/force_parts", "guide/computer/force_field",
                "guide/computer/drives", "guide/computer/senses", "guide/computer/computer_engine", "guide/computer/antigravity_board")) {
            helper.assertTrue(level.getServer().getAdvancements().getAdvancement(EssentialCraft5.id(name)) != null, "应有手册打勾进度 " + name);
        }
        Advancement advancement = level.getServer().getAdvancements().getAdvancement(ECComputer.ADVANCEMENT_COMPUTER_ARMOR);
        helper.assertTrue(advancement != null, "应有进度 main/computer_armor");
        helper.assertTrue(advancement.getParent() != null && advancement.getParent().getId().equals(EssentialCraft5.id("main/inquisitorium")),
                "父进度应是 main/inquisitorium");
        helper.assertTrue(advancement.getDisplay() != null && advancement.getDisplay().getFrame() == FrameType.CHALLENGE, "应是 challenge 框（进度树终点）");

        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        try {
            player.getInventory().setItem(0, new ItemStack(ECComputer.COMPUTER_HELMET.get()));
            player.getInventory().setItem(1, new ItemStack(ECComputer.COMPUTER_CHESTPLATE.get()));
            ItemStack legs = new ItemStack(ECComputer.COMPUTER_LEGGINGS.get());
            player.getInventory().setItem(2, legs);
            CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), legs);
            helper.assertFalse(player.getAdvancements().getOrStartProgress(advancement).isDone(), "只有三件时不应完成");
            ItemStack boots = new ItemStack(ECComputer.COMPUTER_BOOTS.get());
            player.getInventory().setItem(3, boots);
            CriteriaTriggers.INVENTORY_CHANGED.trigger(player, player.getInventory(), boots);
            helper.assertTrue(player.getAdvancements().getOrStartProgress(advancement).isDone(), "四件都在物品栏里应完成");
        } finally {
            player.getAdvancements().stopListening();
        }
        helper.succeed();
    }

    /** TransientCraftingContainer 要一个菜单通知格子变化，测试里用个空的 */
    private static final class DummyMenu extends AbstractContainerMenu {
        DummyMenu() {
            super(null, 0);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }
}
