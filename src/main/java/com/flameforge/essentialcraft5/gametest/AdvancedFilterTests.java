package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ItemFilters;
import com.flameforge.essentialcraft5.block.FacingMachineBlock;
import com.flameforge.essentialcraft5.blockentity.MagicalHopperBlockEntity;
import com.flameforge.essentialcraft5.menu.FilterMenu;
import com.flameforge.essentialcraft5.recipe.CrafterGrid;
import com.flameforge.essentialcraft5.recipe.KeepNbtRecipes;
import com.flameforge.essentialcraft5.registry.ECLogistics;
import com.flameforge.essentialcraft5.registry.ECVoid;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 高级过滤器（第六批 6b，batch6b §4.4，D1 = B「每格选一个标签」）的 GameTest：注册、升级配方带样本和开关、三个开关的判定、
 * 每格的标签（默认选法、手动选、不多收、和样本对不上时退回按物品）、界面按钮的服务端校验、界面同步（DataSlot 编解码、标签重载后重算）、
 * 套娃各用各的开关、哈希样本、8 KB 上限、魔力漏斗里实际分拣。
 * <p>
 * 标签用原版和 Forge 自带的（GameTest 服里都有），成员数以这两家为准；整合包里成员数会变，默认选中的标签可能不同（选中后存进样本 NBT，不跟着变）。
 * 界面按钮在客户端直接返回 false 这一条测不了（GameTest 服里没有客户端世界），只测服务端的校验。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class AdvancedFilterTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final BlockPos HOPPER = new BlockPos(5, 2, 5);
    private static final BlockPos FRONT = HOPPER.east();

    private static final TagKey<Item> ORES_IRON = itemTag("forge", "ores/iron");
    private static final TagKey<Item> ORES_IN_GROUND_STONE = itemTag("forge", "ores_in_ground/stone");
    private static final TagKey<Item> ORE_RATES_SINGULAR = itemTag("forge", "ore_rates/singular");
    private static final TagKey<Item> INGOTS_IRON = itemTag("forge", "ingots/iron");
    private static final TagKey<Item> GLASS_WHITE = itemTag("forge", "glass/white");
    private static final TagKey<Item> GLASS_SILICA = itemTag("forge", "glass/silica");
    private static final TagKey<Item> ARMORS_HELMETS = itemTag("forge", "armors/helmets");
    private static final TagKey<Item> TOOLS_BOWS = itemTag("forge", "tools/bows");
    private static final TagKey<Item> ORES_GOLD = itemTag("forge", "ores/gold");
    private static final TagKey<Item> ORE_RATES_SPARSE = itemTag("forge", "ore_rates/sparse");
    private static final TagKey<Item> ORE_BEARING_GROUND_STONE = itemTag("forge", "ore_bearing_ground/stone");
    private static final TagKey<Item> COBBLESTONE_NORMAL = itemTag("forge", "cobblestone/normal");
    private static final TagKey<Item> LOGS = ItemTags.LOGS;
    private static final TagKey<Item> LECTERN_BOOKS = ItemTags.LECTERN_BOOKS;

    private AdvancedFilterTests() {
    }

    private static TagKey<Item> itemTag(String namespace, String path) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    /** 高级过滤器，依次放进样本 */
    private static ItemStack advanced(boolean blacklist, ItemStack... samples) {
        return filterOf(blacklist ? ECLogistics.ADVANCED_BLACKLIST_FILTER.get().getDefaultInstance()
                : ECLogistics.ADVANCED_WHITELIST_FILTER.get().getDefaultInstance(), samples);
    }

    private static ItemStack normal(boolean blacklist, ItemStack... samples) {
        return filterOf(blacklist ? ECLogistics.BLACKLIST_FILTER.get().getDefaultInstance()
                : ECLogistics.WHITELIST_FILTER.get().getDefaultInstance(), samples);
    }

    private static ItemStack filterOf(ItemStack filter, ItemStack... samples) {
        for (int i = 0; i < samples.length; i++) {
            if (!samples[i].isEmpty() && !ItemFilters.setSample(filter, i, samples[i])) {
                throw new IllegalStateException("AdvancedFilterTests：样本放不进过滤器");
            }
        }
        return filter;
    }

    private static ItemStack damagedSword(int damage) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(damage);
        return sword;
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.setHoverName(Component.literal(name));
        return stack;
    }

    /** 一本约 200 KB（按 sizeInBytes）的书：100 页、每页 1000 个 fill 字符 */
    private static ItemStack bigBook(char fill) {
        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        ListTag pages = new ListTag();
        for (int i = 0; i < 100; i++) {
            pages.add(StringTag.valueOf(String.valueOf(fill).repeat(1000)));
        }
        book.getOrCreateTag().put("pages", pages);
        return book;
    }

    // ------------------------------------------------------------------
    // 注册与升级
    // ------------------------------------------------------------------

    /** 两件已注册、实现接口、是高级、黑白名单对；进 essentialcraft5:filters（标签由 datagen 生成，先跑 runData）；堆叠 1。普通过滤器仍不是高级 */
    @GameTest(template = EMPTY)
    public static void advancedFilterRegistered(GameTestHelper helper) {
        ItemStack white = ECLogistics.ADVANCED_WHITELIST_FILTER.get().getDefaultInstance();
        ItemStack black = ECLogistics.ADVANCED_BLACKLIST_FILTER.get().getDefaultInstance();
        helper.assertTrue(ItemFilters.isFilter(white) && ItemFilters.isAdvanced(white) && !ItemFilters.isBlacklist(white), "高级白名单");
        helper.assertTrue(ItemFilters.isFilter(black) && ItemFilters.isAdvanced(black) && ItemFilters.isBlacklist(black), "高级黑名单");
        helper.assertTrue(white.is(ItemFilters.FILTERS) && black.is(ItemFilters.FILTERS), "两种高级过滤器都应在 essentialcraft5:filters 标签里");
        helper.assertTrue(white.getMaxStackSize() == 1 && black.getMaxStackSize() == 1, "高级过滤器堆叠 1");
        helper.assertFalse(ItemFilters.isAdvanced(ECLogistics.WHITELIST_FILTER.get().getDefaultInstance())
                || ItemFilters.isAdvanced(ECLogistics.BLACKLIST_FILTER.get().getDefaultInstance()), "普通过滤器不是高级");
        helper.succeed();
    }

    /**
     * 升级配方（保留 NBT 的有序配方）：普通白名单 + 4 块虚空板 → 高级白名单，样本、标签选择和开关原样带过来；
     * 拿白名单当中心做不出高级黑名单；配方仍是工作台配方
     */
    @GameTest(template = EMPTY)
    public static void advancedFilterUpgradeKeepsSamples(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingRecipe white = craftingRecipe(helper, "logistics/advanced_whitelist_filter");
        CraftingRecipe black = craftingRecipe(helper, "logistics/advanced_blacklist_filter");
        if (white == null || black == null) {
            return;
        }
        helper.assertTrue(white instanceof KeepNbtRecipes.ShapedKeepNbt && white.getType() == RecipeType.CRAFTING && !white.isSpecial(),
                "升级配方应是保留 NBT 的有序工作台配方");

        ItemStack source = normal(false, new ItemStack(Items.COBBLESTONE), named(new ItemStack(Items.DIAMOND), "kept"));
        ItemFilters.setFlag(source, ItemFilters.Flag.IGNORE_NBT, true);
        helper.assertTrue(ItemFilters.setSampleTag(source, 0, COBBLESTONE_NORMAL), "普通过滤器上也能存标签选择（升级后才生效）");
        CrafterGrid grid = upgradeGrid(source);
        helper.assertTrue(white.matches(grid, level), "普通白名单 + 4 块虚空板应匹配高级白名单配方");
        helper.assertFalse(black.matches(grid, level), "白名单当中心不应做出高级黑名单");
        ItemStack out = white.assemble(grid, level.registryAccess());
        helper.assertTrue(out.is(ECLogistics.ADVANCED_WHITELIST_FILTER.get()) && out.getCount() == 1, "产物应是 1 个高级白名单，实际 " + out);
        helper.assertTrue(ItemFilters.samples(out).get(0).is(Items.COBBLESTONE) && "kept".equals(ItemFilters.samples(out).get(1).getHoverName().getString()),
                "样本应原样带过来");
        helper.assertTrue(ItemFilters.flag(out, ItemFilters.Flag.IGNORE_NBT), "开关应带过来");
        helper.assertTrue(COBBLESTONE_NORMAL.equals(ItemFilters.sampleTag(out, 0)), "标签选择应带过来");
        helper.assertTrue(ItemFilters.accepts(out, new ItemStack(Items.DIAMOND)), "带过来的忽略 NBT 在高级过滤器上生效：普通钻石也收");
        helper.assertFalse(ItemFilters.accepts(source, new ItemStack(Items.DIAMOND)), "同样的开关在普通过滤器上不起作用");

        ItemStack blackSource = normal(true, new ItemStack(Items.DIRT));
        ItemStack blackOut = black.assemble(upgradeGrid(blackSource), level.registryAccess());
        helper.assertTrue(blackOut.is(ECLogistics.ADVANCED_BLACKLIST_FILTER.get()) && ItemFilters.samples(blackOut).get(0).is(Items.DIRT),
                "高级黑名单同样带上样本");
        helper.succeed();
    }

    @Nullable
    private static CraftingRecipe craftingRecipe(GameTestHelper helper, String path) {
        Recipe<?> recipe = helper.getLevel().getRecipeManager().byKey(EssentialCraft5.id(path)).orElse(null);
        if (recipe instanceof CraftingRecipe crafting) {
            return crafting;
        }
        helper.fail("找不到工作台配方 essentialcraft5:" + path);
        return null;
    }

    /** " P / PGP / P "：P = 虚空板，G = 中心的过滤器 */
    private static CrafterGrid upgradeGrid(ItemStack center) {
        ItemStack[] cells = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            cells[i] = i % 2 == 1 ? new ItemStack(ECVoid.VOID_PLATING.get()) : ItemStack.EMPTY;
        }
        cells[4] = center;
        return new CrafterGrid(cells);
    }

    // ------------------------------------------------------------------
    // 忽略耐久 / 忽略 NBT
    // ------------------------------------------------------------------

    /** 忽略耐久：损坏铁剑的样本，开着收新铁剑、关着拒；不管附魔；普通过滤器上写了这个开关也不起作用 */
    @GameTest(template = EMPTY)
    public static void advancedFilterIgnoreDamage(GameTestHelper helper) {
        ItemStack fresh = new ItemStack(Items.IRON_SWORD);
        ItemStack filter = advanced(false, damagedSword(50));
        helper.assertFalse(ItemFilters.accepts(filter, fresh), "开关关着：耐久不同不收");
        ItemFilters.setFlag(filter, ItemFilters.Flag.IGNORE_DAMAGE, true);
        helper.assertTrue(ItemFilters.accepts(filter, fresh), "忽略耐久：新铁剑也收");
        ItemStack enchanted = new ItemStack(Items.IRON_SWORD);
        enchanted.enchant(Enchantments.SHARPNESS, 1);
        helper.assertFalse(ItemFilters.accepts(filter, enchanted), "忽略耐久不忽略附魔");

        ItemStack plain = normal(false, damagedSword(50));
        ItemFilters.setFlag(plain, ItemFilters.Flag.IGNORE_DAMAGE, true);
        helper.assertTrue(ItemFilters.flag(plain, ItemFilters.Flag.IGNORE_DAMAGE), "普通过滤器的数据里写得进开关");
        helper.assertFalse(ItemFilters.accepts(plain, fresh), "普通过滤器上的忽略耐久不起作用");
        helper.succeed();
    }

    /** 忽略 NBT：改过名的钻石样本，开着收普通钻石、关着拒；不同物品照样拒 */
    @GameTest(template = EMPTY)
    public static void advancedFilterIgnoreNbt(GameTestHelper helper) {
        ItemStack filter = advanced(false, named(new ItemStack(Items.DIAMOND), "shiny"));
        helper.assertFalse(ItemFilters.accepts(filter, new ItemStack(Items.DIAMOND)), "开关关着：名字不同不收");
        ItemFilters.setFlag(filter, ItemFilters.Flag.IGNORE_NBT, true);
        helper.assertTrue(ItemFilters.accepts(filter, new ItemStack(Items.DIAMOND, 64)), "忽略 NBT：普通钻石也收（不比数量）");
        helper.assertFalse(ItemFilters.accepts(filter, new ItemStack(Items.EMERALD)), "忽略 NBT 不等于什么都收");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 按标签
    // ------------------------------------------------------------------

    /**
     * 铁矿石样本打开「按标签」：自动选 forge:ores/iron，收深板岩铁矿石、拒金矿石——两种矿石都在 ores_in_ground/stone、ore_rates/singular 里，
     * 「共有任意一个标签就算」的写法会把金矿石也收进来；同样设置放进黑名单语义反过来；关掉开关回到按物品比
     */
    @GameTest(template = EMPTY)
    public static void advancedFilterTagOres(GameTestHelper helper) {
        ItemStack ironOre = new ItemStack(Items.IRON_ORE);
        ItemStack deepslate = new ItemStack(Items.DEEPSLATE_IRON_ORE);
        ItemStack goldOre = new ItemStack(Items.GOLD_ORE);
        helper.assertTrue(ironOre.is(ORES_IN_GROUND_STONE) && goldOre.is(ORES_IN_GROUND_STONE)
                && ironOre.is(ORE_RATES_SINGULAR) && goldOre.is(ORE_RATES_SINGULAR), "前提：铁矿石和金矿石都在两组横切标签里");

        ItemStack white = advanced(false, ironOre);
        helper.assertFalse(ItemFilters.accepts(white, deepslate), "开关关着：只收铁矿石本身");
        ItemFilters.setFlag(white, ItemFilters.Flag.MATCH_TAGS, true);
        helper.assertTrue(ORES_IRON.equals(ItemFilters.sampleTag(white, 0)), "打开按标签应自动选 forge:ores/iron，实际 " + ItemFilters.sampleTag(white, 0));
        helper.assertTrue(ItemFilters.accepts(white, deepslate) && ItemFilters.accepts(white, ironOre), "按标签：深板岩铁矿石也收");
        helper.assertFalse(ItemFilters.accepts(white, goldOre), "按标签：金矿石不收（不多收）");

        ItemStack black = advanced(true, ironOre);
        ItemFilters.setFlag(black, ItemFilters.Flag.MATCH_TAGS, true);
        helper.assertTrue(!ItemFilters.accepts(black, deepslate) && ItemFilters.accepts(black, goldOre), "黑名单按标签：拒深板岩铁矿石、收金矿石");

        ItemFilters.setFlag(white, ItemFilters.Flag.MATCH_TAGS, false);
        helper.assertFalse(ItemFilters.accepts(white, deepslate), "关掉按标签：回到只收铁矿石本身");
        helper.assertTrue(ORES_IRON.equals(ItemFilters.sampleTag(white, 0)), "关掉开关不丢标签选择");
        helper.succeed();
    }

    /**
     * 橡木原木没有默认标签：开着按标签也按物品比（拒白桦原木）；手动选 minecraft:logs 后收白桦原木、拒石头；
     * 选一个橡木原木不在的标签被拒、数据不变；明确选回「按物品」后关开再开不会被默认标签覆盖
     */
    @GameTest(template = EMPTY)
    public static void advancedFilterTagManual(GameTestHelper helper) {
        ItemStack oak = new ItemStack(Items.OAK_LOG);
        ItemStack birch = new ItemStack(Items.BIRCH_LOG);
        ItemStack filter = advanced(false, oak);
        ItemFilters.setFlag(filter, ItemFilters.Flag.MATCH_TAGS, true);
        helper.assertTrue(ItemFilters.sampleTag(filter, 0) == null, "橡木原木不应有默认标签");
        helper.assertFalse(ItemFilters.accepts(filter, birch), "开着但没选标签：按物品比");

        helper.assertTrue(ItemFilters.setSampleTag(filter, 0, LOGS), "应能选橡木原木自己的标签 minecraft:logs");
        helper.assertTrue(ItemFilters.accepts(filter, birch) && !ItemFilters.accepts(filter, new ItemStack(Items.STONE)), "选 minecraft:logs：收白桦原木、拒石头");
        CompoundTag before = filter.getTag().copy();
        helper.assertFalse(ItemFilters.setSampleTag(filter, 0, INGOTS_IRON), "不属于这件样本的标签应被拒");
        helper.assertFalse(ItemFilters.setSampleTag(filter, 1, LOGS), "空格子不能选标签");
        helper.assertFalse(ItemFilters.setSampleTag(filter, 9, LOGS), "越界的格子不能选标签");
        helper.assertTrue(before.equals(filter.getTag()), "被拒时数据不应变");

        helper.assertTrue(ItemFilters.setSampleTag(filter, 0, null), "应能选回按物品");
        helper.assertFalse(ItemFilters.accepts(filter, birch), "选回按物品后拒白桦原木");
        ItemStack iron = advanced(false, new ItemStack(Items.IRON_INGOT));
        helper.assertTrue(ItemFilters.setSampleTag(iron, 0, null), "铁锭明确选按物品");
        ItemFilters.setFlag(iron, ItemFilters.Flag.MATCH_TAGS, true);
        helper.assertTrue(ItemFilters.sampleTag(iron, 0) == null, "明确选了按物品的格子，打开开关时不应换成默认标签");
        helper.succeed();
    }

    /**
     * 默认标签：铁锭 → forge:ingots/iron；白色染色玻璃 → forge:glass/white（它也在收了全部玻璃、按 id 排在前面的 forge:glass/silica 里，取成员最少的才对）；
     * 橡木原木、铁头盔（只在横切的 forge:armors/helmets 里）、弓（只在横切的 forge:tools/bows 里）、石头（只在横切的 forge:ore_bearing_ground/stone 里）→ 不选；
     * 下界金矿石 → forge:ores/gold（不是只有它一件的横切 forge:ore_rates/sparse）。五组横切前缀另外直接用 isMaterialTag 守：
     * forge:ores_in_ground/* 在测试环境里每组都比对应的 forge:ores/* 大（EC5 自己往里加了矿），借 defaultTag 测不出
     */
    @GameTest(template = EMPTY)
    public static void advancedFilterDefaultTags(GameTestHelper helper) {
        for (TagKey<Item> crossCutting : List.of(ORES_IN_GROUND_STONE, ORE_BEARING_GROUND_STONE, ORE_RATES_SINGULAR, ARMORS_HELMETS, TOOLS_BOWS)) {
            helper.assertFalse(ItemFilters.isMaterialTag(crossCutting), crossCutting.location() + " 是横切分组，不应当默认标签");
        }
        helper.assertTrue(ItemFilters.isMaterialTag(INGOTS_IRON) && ItemFilters.isMaterialTag(itemTag("c", "ingots/iron")), "forge: / c: 的类别/材质标签能当默认标签");
        helper.assertFalse(ItemFilters.isMaterialTag(LOGS) || ItemFilters.isMaterialTag(itemTag("forge", "ores")), "别的命名空间、路径不带 / 的不能当默认标签");
        ItemStack bow = new ItemStack(Items.BOW);
        helper.assertTrue(bow.is(TOOLS_BOWS), "前提：弓在 forge:tools/bows 里");
        helper.assertTrue(ItemFilters.defaultTag(bow) == null, "弓不选默认标签（横切分组），实际 " + ItemFilters.defaultTag(bow));
        ItemStack netherGold = new ItemStack(Items.NETHER_GOLD_ORE);
        helper.assertTrue(netherGold.is(ORE_RATES_SPARSE) && netherGold.is(ORES_GOLD) && ItemFilters.tagSize(ORE_RATES_SPARSE) < ItemFilters.tagSize(ORES_GOLD),
                "前提：下界金矿石在成员更少的 forge:ore_rates/sparse 里");
        helper.assertTrue(ORES_GOLD.equals(ItemFilters.defaultTag(netherGold)), "下界金矿石默认 forge:ores/gold，实际 " + ItemFilters.defaultTag(netherGold));
        ItemStack stone = new ItemStack(Items.STONE);
        helper.assertTrue(stone.is(ORE_BEARING_GROUND_STONE), "前提：石头在 forge:ore_bearing_ground/stone 里");
        helper.assertTrue(ItemFilters.defaultTag(stone) == null, "石头不选默认标签（横切分组），实际 " + ItemFilters.defaultTag(stone));

        helper.assertTrue(INGOTS_IRON.equals(ItemFilters.defaultTag(new ItemStack(Items.IRON_INGOT))), "铁锭默认 forge:ingots/iron，实际 "
                + ItemFilters.defaultTag(new ItemStack(Items.IRON_INGOT)));
        ItemStack whiteGlass = new ItemStack(Items.WHITE_STAINED_GLASS);
        helper.assertTrue(whiteGlass.is(GLASS_SILICA) && ItemFilters.tagSize(GLASS_SILICA) > ItemFilters.tagSize(GLASS_WHITE),
                "前提：白色染色玻璃也在成员更多的 forge:glass/silica 里");
        helper.assertTrue(GLASS_WHITE.equals(ItemFilters.defaultTag(whiteGlass)), "白色染色玻璃默认 forge:glass/white，实际 " + ItemFilters.defaultTag(whiteGlass));
        helper.assertTrue(ItemFilters.defaultTag(new ItemStack(Items.OAK_LOG)) == null, "橡木原木不选默认标签");
        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        helper.assertTrue(helmet.is(ARMORS_HELMETS), "前提：铁头盔在 forge:armors/helmets 里");
        helper.assertTrue(ItemFilters.defaultTag(helmet) == null, "铁头盔不选默认标签（横切分组）");
        helper.assertTrue(ItemFilters.tagChoices(ECLogistics.WHITELIST_FILTER.get().getDefaultInstance()).isEmpty(), "过滤器样本没有可选标签");

        // 手册里的例子：羊毛没有默认标签，手动选 minecraft:wool 收所有颜色
        ItemStack whiteWool = new ItemStack(Items.WHITE_WOOL);
        helper.assertTrue(ItemFilters.defaultTag(whiteWool) == null, "白色羊毛不选默认标签");
        ItemStack wool = advanced(false, whiteWool);
        ItemFilters.setFlag(wool, ItemFilters.Flag.MATCH_TAGS, true);
        helper.assertTrue(ItemFilters.setSampleTag(wool, 0, ItemTags.WOOL), "白色羊毛能选 minecraft:wool");
        helper.assertTrue(ItemFilters.accepts(wool, new ItemStack(Items.RED_WOOL)) && !ItemFilters.accepts(wool, new ItemStack(Items.WHITE_CARPET)),
                "选 minecraft:wool：收红色羊毛、不收地毯");

        // 换了样本清掉选择；开着按标签时换上来的样本自动选默认标签；样本没变的格子保留选择
        ItemStack filter = advanced(false, new ItemStack(Items.OAK_LOG), new ItemStack(Items.IRON_ORE));
        ItemFilters.setFlag(filter, ItemFilters.Flag.MATCH_TAGS, true);
        helper.assertTrue(ItemFilters.setSampleTag(filter, 0, LOGS), "选 minecraft:logs");
        helper.assertTrue(ItemFilters.setSamples(filter, List.of(new ItemStack(Items.OAK_LOG), new ItemStack(Items.IRON_INGOT))), "写回样本");
        helper.assertTrue(LOGS.equals(ItemFilters.sampleTag(filter, 0)), "样本没变的格子应保留标签选择");
        helper.assertTrue(INGOTS_IRON.equals(ItemFilters.sampleTag(filter, 1)), "换上铁锭的格子应自动选 forge:ingots/iron，实际 " + ItemFilters.sampleTag(filter, 1));
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 界面按钮
    // ------------------------------------------------------------------

    private static FilterMenu openHeld(ServerPlayer player, ItemStack filter) {
        Inventory inventory = player.getInventory();
        inventory.selected = 0;
        inventory.setItem(0, filter);
        return new FilterMenu(0, inventory, InteractionHand.MAIN_HAND);
    }

    /**
     * 高级过滤器：按钮 0~2 切开关并写回手上那件；打开按标签时已有样本自动选默认标签；16 + 格号 / 32 + 格号在「按物品 + 这件样本的全部标签」里前后循环；
     * 发给客户端的 DataSlot（开关位、每格标签序号）按 {@link FilterMenu#decodeTag} 解得回服务端的选择。
     * 伪造的按钮号（越界、空格子、开关没开、样本没有标签）返回 false、数据不变；普通过滤器上任何按钮都返回 false
     */
    @GameTest(template = EMPTY)
    public static void advancedFilterMenuButtons(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ItemStack held = advanced(false, new ItemStack(Items.IRON_INGOT));
        FilterMenu menu = openHeld(player, held);
        SyncRecorder sync = new SyncRecorder();
        menu.setSynchronizer(sync);
        helper.assertTrue(sync.initial != null && sync.initial.length == 1 + ItemFilters.SLOTS && sync.initial[0] == 0 && sync.initial[1] == 0,
                "开界面时同步的初值：开关全关、第 0 格按物品");
        helper.assertTrue(menu.isAdvanced(), "高级过滤器的界面");
        helper.assertTrue(menu.clickMenuButton(player, FilterMenu.BUTTON_FLAG), "按钮 0 应切换忽略耐久");
        helper.assertTrue(ItemFilters.flag(held, ItemFilters.Flag.IGNORE_DAMAGE) && menu.flagOn(ItemFilters.Flag.IGNORE_DAMAGE), "忽略耐久应写进手上那件");
        helper.assertTrue(menu.clickMenuButton(player, FilterMenu.BUTTON_FLAG) && !ItemFilters.flag(held, ItemFilters.Flag.IGNORE_DAMAGE), "再按一次关掉");
        helper.assertTrue(menu.clickMenuButton(player, FilterMenu.BUTTON_FLAG + 1) && ItemFilters.flag(held, ItemFilters.Flag.IGNORE_NBT), "按钮 1 切换忽略 NBT");

        helper.assertFalse(menu.clickMenuButton(player, FilterMenu.BUTTON_NEXT_TAG), "开关没开时不能换标签");
        helper.assertTrue(menu.clickMenuButton(player, FilterMenu.BUTTON_FLAG + 2) && ItemFilters.flag(held, ItemFilters.Flag.MATCH_TAGS), "按钮 2 切换按标签");
        helper.assertTrue(INGOTS_IRON.equals(ItemFilters.sampleTag(held, 0)) && INGOTS_IRON.equals(menu.selectedTag(0)),
                "打开按标签时铁锭应自动选 forge:ingots/iron");
        // 发给客户端的同步值（数据位按 addDataSlot 的顺序：0 = 开关位，1~9 = 第 0~8 格）：客户端的角标、提示、滚轮起点全靠它
        menu.broadcastChanges();
        int expectedFlags = 1 << ItemFilters.Flag.IGNORE_NBT.ordinal() | 1 << ItemFilters.Flag.MATCH_TAGS.ordinal();
        helper.assertTrue(Integer.valueOf(expectedFlags).equals(sync.changes.get(0)), "同步的开关位应是忽略 NBT + 按标签，实际 " + sync.changes.get(0));
        Integer synced = sync.changes.get(1);
        helper.assertTrue(synced != null && INGOTS_IRON.equals(FilterMenu.decodeTag(new ItemStack(Items.IRON_INGOT), synced)),
                "第 0 格的同步值应在客户端解出 forge:ingots/iron，实际 " + synced);

        List<TagKey<Item>> choices = ItemFilters.tagChoices(new ItemStack(Items.IRON_INGOT));
        int start = choices.indexOf(INGOTS_IRON) + 1;
        helper.assertTrue(start > 0, "forge:ingots/iron 应在铁锭的候选里");
        helper.assertTrue(menu.clickMenuButton(player, FilterMenu.BUTTON_NEXT_TAG), "滚轮下一个");
        TagKey<Item> expectedNext = start == choices.size() ? null : choices.get(start);
        helper.assertTrue(Objects.equals(expectedNext, ItemFilters.sampleTag(held, 0)),
                "下一个应是 " + expectedNext + "，实际 " + ItemFilters.sampleTag(held, 0));
        sync.changes.clear();
        menu.broadcastChanges();
        synced = sync.changes.get(1);
        helper.assertTrue(synced != null && Objects.equals(ItemFilters.sampleTag(held, 0), FilterMenu.decodeTag(new ItemStack(Items.IRON_INGOT), synced)),
                "换标签后第 0 格的同步值应在客户端解出 " + ItemFilters.sampleTag(held, 0) + "，实际 " + synced);
        helper.assertTrue(menu.clickMenuButton(player, FilterMenu.BUTTON_PREV_TAG) && INGOTS_IRON.equals(ItemFilters.sampleTag(held, 0)), "滚轮上一个回到 forge:ingots/iron");
        // 转一整圈（候选 + 按物品）回到原处，途中经过一次「按物品」
        boolean sawItem = false;
        for (int i = 0; i <= choices.size(); i++) {
            helper.assertTrue(menu.clickMenuButton(player, FilterMenu.BUTTON_NEXT_TAG), "第 " + i + " 次下一个");
            sawItem |= ItemFilters.sampleTag(held, 0) == null;
            TagKey<Item> now = ItemFilters.sampleTag(held, 0);
            helper.assertTrue(now == null || choices.contains(now), "选中的只能是铁锭自己的标签，实际 " + now);
        }
        helper.assertTrue(sawItem && INGOTS_IRON.equals(ItemFilters.sampleTag(held, 0)), "转一圈应经过按物品并回到原处");

        CompoundTag before = held.getTag().copy();
        int[] forged = {-1, 3, 15, FilterMenu.BUTTON_NEXT_TAG + 1, FilterMenu.BUTTON_NEXT_TAG + 9, FilterMenu.BUTTON_PREV_TAG + 9, 48, 999};
        for (int id : forged) {
            helper.assertFalse(menu.clickMenuButton(player, id), "按钮号 " + id + " 应被拒");
        }
        Item untagged = untaggedItem();
        if (untagged != null) {
            helper.assertTrue(ItemFilters.setSample(held, 2, new ItemStack(untagged)), "放一件没有标签的样本");
            before = held.getTag().copy();
            helper.assertFalse(menu.clickMenuButton(player, FilterMenu.BUTTON_NEXT_TAG + 2), "没有标签的样本不能换标签");
        }
        helper.assertTrue(before.equals(held.getTag()), "被拒的按钮不应改数据");
        menu.removed(player);

        ItemStack plain = normal(false, new ItemStack(Items.IRON_INGOT));
        FilterMenu plainMenu = openHeld(player, plain);
        CompoundTag plainBefore = plain.getTag().copy();
        for (int id = 0; id < 48; id++) {
            helper.assertFalse(plainMenu.clickMenuButton(player, id), "普通过滤器上按钮 " + id + " 应返回 false");
        }
        helper.assertTrue(plainBefore.equals(plain.getTag()) && !plainMenu.isAdvanced(), "普通过滤器的数据不应变");
        plainMenu.removed(player);
        helper.succeed();
    }

    /** 原版、Forge 都没有给它们挂标签的几件物品里找一件真没有标签的（找不到就跳过那一条） */
    @Nullable
    private static Item untaggedItem() {
        for (Item item : new Item[] {Items.BEDROCK, Items.BARRIER, Items.STRUCTURE_VOID, Items.JIGSAW, Items.DEBUG_STICK, Items.KNOWLEDGE_BOOK}) {
            if (new ItemStack(item).getTags().findAny().isEmpty()) {
                return item;
            }
        }
        return null;
    }

    /** 界面里换样本：幽灵格点击写回时，同一格换了物品就清掉原来的标签、开着按标签时换上默认标签 */
    @GameTest(template = EMPTY)
    public static void advancedFilterMenuSampleChange(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ItemStack held = advanced(false, new ItemStack(Items.OAK_LOG));
        FilterMenu menu = openHeld(player, held);
        helper.assertTrue(menu.clickMenuButton(player, FilterMenu.BUTTON_FLAG + 2), "打开按标签");
        helper.assertTrue(ItemFilters.setSampleTag(held, 0, LOGS), "选 minecraft:logs");
        menu.setCarried(new ItemStack(Items.IRON_ORE));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.sample(0).is(Items.IRON_ORE), "样本应换成铁矿石");
        helper.assertTrue(ORES_IRON.equals(ItemFilters.sampleTag(held, 0)), "换了物品应换成铁矿石的默认标签，实际 " + ItemFilters.sampleTag(held, 0));
        menu.setCarried(new ItemStack(Items.DIAMOND));
        menu.clicked(1, 0, ClickType.PICKUP, player);
        helper.assertTrue(ORES_IRON.equals(ItemFilters.sampleTag(held, 0)), "改别的格子不动这一格的标签");
        menu.setCarried(ItemStack.EMPTY);
        menu.removed(player);
        helper.succeed();
    }

    /**
     * 存着的标签和样本对不上（数据包把样本移出了标签，或者改过数据）：sampleTag 照样读出来，effectiveTag 和服务端菜单的 selectedTag 为 null，判定按物品比。
     * 去掉 effectiveTag 里「样本还在标签里」的检查，按住 Shift 看过滤器时物品提示会显示一个和样本无关的标签
     */
    @GameTest(template = EMPTY)
    public static void advancedFilterStaleSampleTag(GameTestHelper helper) {
        ItemStack stone = new ItemStack(Items.STONE);
        List<TagKey<Item>> stoneTags = ItemFilters.tagChoices(stone);
        helper.assertFalse(stoneTags.isEmpty() || stone.is(ItemTags.PLANKS), "前提：石头有自己的标签、不在 minecraft:planks 里");
        ItemStack filter = advanced(false, stone);
        ItemFilters.setFlag(filter, ItemFilters.Flag.MATCH_TAGS, true);
        helper.assertTrue(ItemFilters.setSampleTag(filter, 0, stoneTags.get(0)), "先选一个石头自己的标签");
        // 直接改数据（格式见 ItemFilters 类注释：samples[i].tag）
        ListTag samples = filter.getTag().getCompound(ItemFilters.TAG).getList("samples", Tag.TAG_COMPOUND);
        samples.getCompound(0).putString("tag", ItemTags.PLANKS.location().toString());
        helper.assertTrue(ItemTags.PLANKS.equals(ItemFilters.sampleTag(filter, 0)), "存着的选择读出来是 minecraft:planks，实际 " + ItemFilters.sampleTag(filter, 0));
        helper.assertTrue(ItemFilters.effectiveTag(filter, 0) == null, "石头不在 minecraft:planks 里：判定用的标签应为 null");
        helper.assertTrue(ItemFilters.accepts(filter, new ItemStack(Items.STONE)) && !ItemFilters.accepts(filter, new ItemStack(Items.OAK_PLANKS)),
                "退回按物品比：收石头、不收木板");

        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        FilterMenu menu = openHeld(player, filter);
        helper.assertTrue(menu.selectedTag(0) == null, "服务端菜单给的标签也应为 null，实际 " + menu.selectedTag(0));
        menu.removed(player);
        helper.succeed();
    }

    /**
     * 标签重载（{@code /reload}）后界面的同步值要重算：同步值取决于标签成员，NBT 对象却没换。
     * 这里用原地改 NBT（对象身份不变）代替「标签成员变了」，再直接调标签重载的处理器
     */
    @GameTest(template = EMPTY)
    public static void advancedFilterMenuSyncAfterTagReload(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ItemStack held = advanced(false, new ItemStack(Items.IRON_INGOT));
        FilterMenu menu = openHeld(player, held);
        SyncRecorder sync = new SyncRecorder();
        menu.setSynchronizer(sync);
        helper.assertTrue(sync.initial != null && sync.initial[0] == 0, "初值：开关全关");
        held.getTag().getCompound(ItemFilters.TAG).putBoolean(ItemFilters.Flag.IGNORE_DAMAGE.key(), true);
        menu.broadcastChanges();
        helper.assertFalse(sync.changes.containsKey(0), "前提：NBT 对象没换、标签也没重载时不重算");
        FilterMenu.FilterTagEvents.onFilterTagsUpdated(new TagsUpdatedEvent(helper.getLevel().registryAccess(), false, false));
        menu.broadcastChanges();
        helper.assertTrue(Integer.valueOf(1 << ItemFilters.Flag.IGNORE_DAMAGE.ordinal()).equals(sync.changes.get(0)),
                "标签重载后应重算并发出新的开关位，实际 " + sync.changes.get(0));
        menu.removed(player);
        helper.succeed();
    }

    /** 记下菜单发给客户端的同步数据（代替真连接；只关心 DataSlot：初值和之后每个数据位最后一次的新值） */
    private static final class SyncRecorder implements ContainerSynchronizer {
        @Nullable
        int[] initial;
        final Map<Integer, Integer> changes = new HashMap<>();

        @Override
        public void sendInitialData(AbstractContainerMenu menu, NonNullList<ItemStack> items, ItemStack carried, int[] data) {
            initial = data.clone();
        }

        @Override
        public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) {
        }

        @Override
        public void sendCarriedChange(AbstractContainerMenu menu, ItemStack stack) {
        }

        @Override
        public void sendDataChange(AbstractContainerMenu menu, int id, int value) {
            changes.put(id, value);
        }
    }

    // ------------------------------------------------------------------
    // 套娃、哈希样本、体积
    // ------------------------------------------------------------------

    /** 套娃各用各的开关：开着忽略 NBT 的高级过滤器放进普通白名单仍收普通钻石；外层高级的忽略耐久不传给里面的普通过滤器 */
    @GameTest(template = EMPTY)
    public static void advancedFilterNestedOwnFlags(GameTestHelper helper) {
        ItemStack inner = advanced(false, named(new ItemStack(Items.DIAMOND), "inner"));
        ItemFilters.setFlag(inner, ItemFilters.Flag.IGNORE_NBT, true);
        ItemStack outer = normal(false, inner);
        helper.assertTrue(ItemFilters.accepts(outer, new ItemStack(Items.DIAMOND)), "里面的高级过滤器按自己的忽略 NBT：普通钻石也收");
        helper.assertFalse(ItemFilters.accepts(outer, new ItemStack(Items.EMERALD)), "绿宝石不收");

        ItemStack exact = normal(false, damagedSword(30));
        ItemStack advancedOuter = advanced(false, exact);
        ItemFilters.setFlag(advancedOuter, ItemFilters.Flag.IGNORE_DAMAGE, true);
        helper.assertFalse(ItemFilters.accepts(advancedOuter, new ItemStack(Items.IRON_SWORD)), "外层的忽略耐久不应传给里面的普通过滤器");
        helper.assertTrue(ItemFilters.accepts(advancedOuter, damagedSword(30)), "同样损耗的铁剑照收");
        helper.succeed();
    }

    /**
     * 大书（哈希样本）配合按标签：选 minecraft:lectern_books 时同内容的书收、改了一个字的拒（NBT 照样比）；再开忽略 NBT 时成书也收；
     * 标签字符串算进 8 KB：过滤器本身接近上限时选不上
     */
    @GameTest(template = EMPTY)
    public static void advancedFilterHashedSampleTags(GameTestHelper helper) {
        ItemStack book = bigBook('a');
        helper.assertTrue(book.is(LECTERN_BOOKS) && new ItemStack(Items.WRITTEN_BOOK).is(LECTERN_BOOKS), "前提：书与笔、成书都在 minecraft:lectern_books 里");
        ItemStack filter = advanced(false, book);
        ItemFilters.setFlag(filter, ItemFilters.Flag.MATCH_TAGS, true);
        helper.assertTrue(ItemFilters.setSampleTag(filter, 0, LECTERN_BOOKS), "哈希样本也能选标签");
        helper.assertTrue(ItemFilters.accepts(filter, bigBook('a')), "同内容的书收");
        helper.assertFalse(ItemFilters.accepts(filter, bigBook('b')), "改了一个字的书拒（按标签也比 NBT）");
        helper.assertFalse(ItemFilters.accepts(filter, new ItemStack(Items.WRITTEN_BOOK)), "没开忽略 NBT：成书拒");
        ItemFilters.setFlag(filter, ItemFilters.Flag.IGNORE_NBT, true);
        helper.assertTrue(ItemFilters.accepts(filter, new ItemStack(Items.WRITTEN_BOOK)), "开了忽略 NBT：同标签的成书也收");

        // 8 KB：过滤器自己带一大段别的 NBT，剩下的地方放得下样本、放不下一个长标签名
        ItemStack padded = advanced(false, new ItemStack(Items.OAK_LOG));
        ItemFilters.setFlag(padded, ItemFilters.Flag.MATCH_TAGS, true);
        CompoundTag tag = padded.getOrCreateTag();
        tag.putString("pad", "");
        int chars = (ItemFilters.MAX_FILTER_BYTES - tag.sizeInBytes() - 10) / 2;
        tag.putString("pad", "x".repeat(chars));
        helper.assertTrue(padded.getTag().sizeInBytes() <= ItemFilters.MAX_FILTER_BYTES, "前提：垫好后仍在上限内");
        CompoundTag before = padded.getTag().copy();
        helper.assertFalse(ItemFilters.setSampleTag(padded, 0, LOGS), "标签字符串放不下时应选不上");
        helper.assertTrue(before.equals(padded.getTag()), "选不上时数据不变");
        tag.putString("pad", "x".repeat(Math.max(0, chars - 100)));
        helper.assertTrue(ItemFilters.setSampleTag(padded, 0, LOGS), "腾出地方后应能选上");
        helper.succeed();
    }

    /** 打开按标签时自动选的默认标签放不下 8 KB：只开开关、不选标签，整件仍在上限内（地方够时照常选上） */
    @GameTest(template = EMPTY)
    public static void advancedFilterDefaultTagOverLimit(GameTestHelper helper) {
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        // 先量「只开开关」和「开关 + 默认标签」各多占多少字节
        ItemStack flagOnly = advanced(false, iron);
        int base = flagOnly.getTag().sizeInBytes();
        flagOnly.getTag().getCompound(ItemFilters.TAG).putBoolean(ItemFilters.Flag.MATCH_TAGS.key(), true);
        int flagBytes = flagOnly.getTag().sizeInBytes() - base;
        ItemStack both = advanced(false, iron);
        ItemFilters.setFlag(both, ItemFilters.Flag.MATCH_TAGS, true);
        helper.assertTrue(INGOTS_IRON.equals(ItemFilters.sampleTag(both, 0)), "前提：地方够时自动选 forge:ingots/iron");
        int bothBytes = both.getTag().sizeInBytes() - base;
        helper.assertTrue(bothBytes > flagBytes + 8, "前提：标签字符串要占地方（开关 " + flagBytes + "、开关 + 标签 " + bothBytes + " 字节）");

        // 垫到剩下的地方放得下开关、放不下标签
        ItemStack padded = advanced(false, iron);
        CompoundTag tag = padded.getOrCreateTag();
        tag.putString("pad", "");
        int room = flagBytes + (bothBytes - flagBytes) / 2;
        tag.putString("pad", "x".repeat((ItemFilters.MAX_FILTER_BYTES - tag.sizeInBytes() - room) / 2));
        int left = ItemFilters.MAX_FILTER_BYTES - padded.getTag().sizeInBytes();
        helper.assertTrue(left >= flagBytes && left < bothBytes, "前提：剩 " + left + " 字节，开关要 " + flagBytes + "、开关 + 标签要 " + bothBytes);
        ItemFilters.setFlag(padded, ItemFilters.Flag.MATCH_TAGS, true);
        helper.assertTrue(ItemFilters.flag(padded, ItemFilters.Flag.MATCH_TAGS), "开关照样打开");
        helper.assertTrue(ItemFilters.sampleTag(padded, 0) == null, "默认标签放不下：不选，实际 " + ItemFilters.sampleTag(padded, 0));
        helper.assertTrue(padded.getTag().sizeInBytes() <= ItemFilters.MAX_FILTER_BYTES, "整件仍在 8 KB 以内，实际 " + padded.getTag().sizeInBytes());
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // 魔力漏斗
    // ------------------------------------------------------------------

    /** 魔力漏斗装高级白名单（橡木原木样本、按 minecraft:logs）：搬白桦原木、不搬石头 */
    @GameTest(template = EMPTY_LARGE)
    public static void advancedFilterInHopper(GameTestHelper helper) {
        helper.setBlock(HOPPER, ECLogistics.MAGICAL_HOPPER.get().defaultBlockState().setValue(FacingMachineBlock.FACING, Direction.EAST));
        MagicalHopperBlockEntity hopper = (MagicalHopperBlockEntity) helper.getBlockEntity(HOPPER);
        UUID owner = UUID.randomUUID();
        hopper.setOwner(owner, "adv-filter");
        ECProtection.setOnlineOverride(ProtectionFrameworkTests.realPlayer(helper, owner, "adv-filter"));
        try {
            ItemStack filter = advanced(false, new ItemStack(Items.OAK_LOG));
            ItemFilters.setFlag(filter, ItemFilters.Flag.MATCH_TAGS, true);
            helper.assertTrue(ItemFilters.setSampleTag(filter, 0, LOGS), "选 minecraft:logs");
            hopper.getInventory().setStackInSlot(MagicalHopperBlockEntity.SLOT_FILTER, filter);
            ItemEntity birch = drop(helper, 3.5D, 2.1D, 5.5D, new ItemStack(Items.BIRCH_LOG, 3));
            ItemEntity stone = drop(helper, 3.5D, 2.1D, 4.5D, new ItemStack(Items.STONE));
            int moved = hopper.transferOnce();
            helper.assertTrue(moved == 1, "应只搬 1 个掉落物，实际 " + moved);
            helper.assertTrue(birch.blockPosition().equals(helper.absolutePos(FRONT)), "白桦原木应被搬到前方格");
            helper.assertTrue(stone.blockPosition().equals(helper.absolutePos(new BlockPos(3, 2, 4))), "石头不应被搬");
        } finally {
            ECProtection.clearOnlineOverride(owner);
        }
        helper.succeed();
    }

    /** 在相对坐标 (x, y, z) 放一个不动的掉落物 */
    private static ItemEntity drop(GameTestHelper helper, double x, double y, double z, ItemStack stack) {
        Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack);
        item.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(item);
        return item;
    }
}
