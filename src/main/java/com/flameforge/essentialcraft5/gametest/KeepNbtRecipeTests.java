package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.recipe.CrafterGrid;
import com.flameforge.essentialcraft5.recipe.CraftingRecipeIndex;
import com.flameforge.essentialcraft5.recipe.KeepNbtRecipes;
import com.flameforge.essentialcraft5.registry.ECRecipes;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 保留 NBT 的工作台配方（{@link KeepNbtRecipes}，第六批 6b 框架）：产物带上 {@code nbt_source} 那件原料的 tag，
 * 配方产物自己写的 nbt 合并在上面，别的格子的 tag 不带；JSON / 网络读写；仍是工作台配方，合成器能摆样本。
 * 配方对象直接构造（用原版物品），不靠数据包——建筑魔杖、高级过滤器的配方 JSON 由各自模块的测试守着。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class KeepNbtRecipeTests {

    private static final String EMPTY = "empty";

    private KeepNbtRecipeTests() {
    }

    /**
     * 无序：带名字、损耗和自定义 tag 的铁剑 + 木棍 → 金剑，金剑的 tag 和铁剑一模一样（金剑新建时自带的 {@code Damage:0} 不把损耗 12 清零）；数量照配方
     */
    @GameTest(template = EMPTY)
    public static void keepNbtShapelessCopiesTag(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        KeepNbtRecipes.ShapelessKeepNbt recipe = shapeless(new ItemStack(Items.GOLDEN_SWORD, 2));
        ItemStack source = taggedSword();
        CrafterGrid grid = new CrafterGrid(new ItemStack[] {
                new ItemStack(Items.STICK), ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, source, ItemStack.EMPTY});
        helper.assertTrue(recipe.matches(grid, level), "铁剑 + 木棍应匹配无序配方");
        ItemStack out = recipe.assemble(grid, level.registryAccess());
        helper.assertTrue(out.is(Items.GOLDEN_SWORD) && out.getCount() == 2, "产物应是 2 把金剑（数量照配方），实际 " + out);
        helper.assertTrue(Objects.equals(out.getTag(), source.getTag()), "产物 tag 应与铁剑相同：" + out.getTag() + " / " + source.getTag());
        helper.assertTrue("Keep Me".equals(out.getHoverName().getString()), "名字应带过来，实际 " + out.getHoverName().getString());
        helper.assertTrue(Objects.equals(recipe.getResultItem(level.registryAccess()).getTag(), new ItemStack(Items.GOLDEN_SWORD).getTag()),
                "配方自己的产物不应被改，实际 " + recipe.getResultItem(level.registryAccess()).getTag());
        helper.succeed();
    }

    /**
     * 有序（过滤器升级那种 " P / PGP / P "）：中心铁剑的 tag 带到钻石剑上；配方产物自己写的 nbt 合并上去、同名键以配方为准
     * （配方写了和默认不一样的耐久也以配方为准）；中心换成新铁剑时产物只剩配方自己的 nbt
     */
    @GameTest(template = EMPTY)
    public static void keepNbtShapedCopiesAndMerges(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack result = new ItemStack(Items.DIAMOND_SWORD);
        CompoundTag own = new CompoundTag();
        own.putBoolean("ec5_own", true);
        own.putInt("ec5_shared", 1);
        result.setTag(own);
        KeepNbtRecipes.ShapedKeepNbt recipe = shaped(result);

        ItemStack source = taggedSword();
        source.getOrCreateTag().putInt("ec5_shared", 99);
        CrafterGrid grid = upgradeGrid(source);
        helper.assertTrue(recipe.matches(grid, level), "纸 ×4 + 铁剑应匹配有序配方");
        ItemStack out = recipe.assemble(grid, level.registryAccess());
        CompoundTag tag = out.getTag();
        helper.assertTrue(out.is(Items.DIAMOND_SWORD) && out.getCount() == 1 && tag != null, "产物应是 1 把带 tag 的钻石剑，实际 " + out);
        helper.assertTrue(tag.getCompound("ec5_test").getInt("samples") == 7 && tag.getInt("Damage") == 12
                        && "Keep Me".equals(out.getHoverName().getString()),
                "铁剑的自定义 tag、损耗、名字都应带过来：" + tag);
        helper.assertTrue(tag.getBoolean("ec5_own") && tag.getInt("ec5_shared") == 1,
                "配方产物自己的 nbt 应合并上去，同名键以配方为准：" + tag);

        CompoundTag recipeTag = recipe.getResultItem(level.registryAccess()).getTag();
        ItemStack plain = new ItemStack(Items.IRON_SWORD);
        ItemStack plainOut = recipe.assemble(upgradeGrid(plain), level.registryAccess());
        helper.assertTrue(Objects.equals(plainOut.getTag(), recipeTag), "中心是新铁剑时产物只有配方自己的 nbt，实际 " + plainOut.getTag() + " / " + recipeTag);
        helper.assertTrue(recipeTag != null && recipeTag.getBoolean("ec5_own") && !recipeTag.contains("ec5_test"), "配方自己的产物不应被改：" + recipeTag);

        ItemStack repaired = new ItemStack(Items.DIAMOND_SWORD);
        repaired.setDamageValue(5);
        KeepNbtRecipes.carryNbt(repaired, grid, Ingredient.of(Items.IRON_SWORD));
        helper.assertTrue(repaired.getDamageValue() == 5 && repaired.getTag().contains("ec5_test"),
                "配方产物写了和默认不一样的耐久时以配方为准、其余照带，实际 " + repaired.getTag());
        helper.succeed();
    }

    /** 只认 nbt_source（这里用没耐久的纸）：别的格子（带名字的木棍）的 tag 不带；符合的那件没有 tag 时产物没有 tag；两件都符合时取格号小的 */
    @GameTest(template = EMPTY)
    public static void keepNbtIgnoresOtherSlots(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        NonNullList<Ingredient> ingredients = NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.PAPER), Ingredient.of(Items.STICK));
        KeepNbtRecipes.ShapelessKeepNbt recipe = new KeepNbtRecipes.ShapelessKeepNbt(new ShapelessRecipe(EssentialCraft5.id("test/keep_nbt_paper"), "",
                CraftingBookCategory.MISC, new ItemStack(Items.GOLD_INGOT), ingredients), Ingredient.of(Items.PAPER));
        ItemStack namedStick = new ItemStack(Items.STICK);
        namedStick.setHoverName(Component.literal("Not Me"));
        namedStick.getOrCreateTag().putInt("ec5_stick", 3);
        CrafterGrid grid = new CrafterGrid(new ItemStack[] {namedStick, new ItemStack(Items.PAPER)});
        helper.assertTrue(recipe.matches(grid, level), "纸 + 木棍应匹配");
        ItemStack out = recipe.assemble(grid, level.registryAccess());
        helper.assertTrue(out.is(Items.GOLD_INGOT) && out.getTag() == null, "木棍的 tag 不应带到产物上，实际 " + out.getTag());

        ItemStack first = new ItemStack(Items.PAPER);
        first.getOrCreateTag().putInt("ec5_first", 1);
        ItemStack second = new ItemStack(Items.PAPER);
        second.getOrCreateTag().putInt("ec5_second", 2);
        ItemStack picked = KeepNbtRecipes.carryNbt(new ItemStack(Items.GOLD_INGOT), new CrafterGrid(new ItemStack[] {ItemStack.EMPTY, first, second}),
                Ingredient.of(Items.PAPER));
        helper.assertTrue(Objects.equals(picked.getTag(), first.getTag()), "应取第一件符合的，实际 " + picked.getTag());
        helper.succeed();
    }

    /**
     * 序列化器：JSON 读出来是保留 NBT 的子类、类型仍是工作台、不是特殊配方、序列化器是自己的；网络往返后原料、产物、nbt_source 都在；
     * 缺 nbt_source 报错；合成器的样本摆法（{@link CraftingRecipeIndex#layout}）认得两种
     */
    @GameTest(template = EMPTY)
    public static void keepNbtSerializers(GameTestHelper helper) {
        ResourceLocation shapedId = EssentialCraft5.id("test/keep_nbt_shaped");
        JsonObject shapedJson = JsonParser.parseString("""
                {"type": "essentialcraft5:crafting_shaped_keep_nbt",
                 "pattern": [" P ", "PGP", " P "],
                 "key": {"P": {"item": "minecraft:paper"}, "G": {"item": "minecraft:iron_sword"}},
                 "result": {"item": "minecraft:diamond_sword", "nbt": "{ec5_own:1b}"},
                 "nbt_source": {"item": "minecraft:iron_sword"}}
                """).getAsJsonObject();
        KeepNbtRecipes.ShapedKeepNbt shaped = ECRecipes.CRAFTING_SHAPED_KEEP_NBT.get().fromJson(shapedId, shapedJson);
        helper.assertTrue(shaped.getType() == RecipeType.CRAFTING && !shaped.isSpecial()
                        && shaped.getSerializer() == ECRecipes.CRAFTING_SHAPED_KEEP_NBT.get() && shaped instanceof IShapedRecipe<?>,
                "有序：类型应是工作台、不是特殊配方、序列化器是 crafting_shaped_keep_nbt");
        helper.assertTrue(shaped.getWidth() == 3 && shaped.getHeight() == 3 && shaped.nbtSource().test(new ItemStack(Items.IRON_SWORD))
                        && !shaped.nbtSource().test(new ItemStack(Items.PAPER)),
                "有序：3×3、nbt_source 是铁剑");
        helper.assertTrue(shaped.getResultItem(RegistryAccess.EMPTY).getTag() != null
                        && shaped.getResultItem(RegistryAccess.EMPTY).getTag().getBoolean("ec5_own"),
                "有序：产物自己的 nbt 应读进来");
        KeepNbtRecipes.ShapedKeepNbt shapedBack = roundTrip(buf -> ECRecipes.CRAFTING_SHAPED_KEEP_NBT.get().toNetwork(buf, shaped),
                buf -> ECRecipes.CRAFTING_SHAPED_KEEP_NBT.get().fromNetwork(shapedId, buf));
        helper.assertTrue(shapedBack.getWidth() == 3 && shapedBack.getIngredients().size() == 9
                        && shapedBack.nbtSource().test(new ItemStack(Items.IRON_SWORD)) && !shapedBack.nbtSource().test(new ItemStack(Items.PAPER))
                        && ItemStack.isSameItemSameTags(shapedBack.getResultItem(RegistryAccess.EMPTY), shaped.getResultItem(RegistryAccess.EMPTY)),
                "有序：网络往返后应一致");

        ResourceLocation shapelessId = EssentialCraft5.id("test/keep_nbt_shapeless");
        JsonObject shapelessJson = JsonParser.parseString("""
                {"type": "essentialcraft5:crafting_shapeless_keep_nbt",
                 "ingredients": [{"item": "minecraft:iron_sword"}],
                 "result": {"item": "minecraft:golden_sword"},
                 "nbt_source": {"item": "minecraft:iron_sword"}}
                """).getAsJsonObject();
        KeepNbtRecipes.ShapelessKeepNbt shapeless = ECRecipes.CRAFTING_SHAPELESS_KEEP_NBT.get().fromJson(shapelessId, shapelessJson);
        helper.assertTrue(shapeless.getType() == RecipeType.CRAFTING && !shapeless.isSpecial()
                        && shapeless.getSerializer() == ECRecipes.CRAFTING_SHAPELESS_KEEP_NBT.get() && !(shapeless instanceof IShapedRecipe<?>),
                "无序：类型应是工作台、不是特殊配方、序列化器是 crafting_shapeless_keep_nbt");
        KeepNbtRecipes.ShapelessKeepNbt shapelessBack = roundTrip(
                buf -> ECRecipes.CRAFTING_SHAPELESS_KEEP_NBT.get().toNetwork(buf, shapeless),
                buf -> ECRecipes.CRAFTING_SHAPELESS_KEEP_NBT.get().fromNetwork(shapelessId, buf));
        helper.assertTrue(shapelessBack.getIngredients().size() == 1 && shapelessBack.nbtSource().test(new ItemStack(Items.IRON_SWORD))
                        && shapelessBack.getResultItem(RegistryAccess.EMPTY).is(Items.GOLDEN_SWORD),
                "无序：网络往返后应一致");

        // 单放一件换模式的配方，合成格里放的就是一件，产物照样带 tag
        ItemStack source = taggedSword();
        ItemStack cycled = shapeless.assemble(new CrafterGrid(new ItemStack[] {ItemStack.EMPTY, ItemStack.EMPTY, source}), RegistryAccess.EMPTY);
        helper.assertTrue(Objects.equals(cycled.getTag(), source.getTag()), "从 JSON 读出来的无序配方也应带 tag");

        shapelessJson.remove(KeepNbtRecipes.NBT_SOURCE);
        boolean threw = false;
        try {
            ECRecipes.CRAFTING_SHAPELESS_KEEP_NBT.get().fromJson(shapelessId, shapelessJson);
        } catch (JsonParseException e) {
            threw = true;
        }
        helper.assertTrue(threw, "缺 nbt_source 应报错");

        helper.assertTrue(CraftingRecipeIndex.layout(shaped) != null && CraftingRecipeIndex.layout(shapeless) != null,
                "魔力合成器应能把两种配方摆成样本");
        helper.succeed();
    }

    // ------------------------------------------------------------------

    /** 带名字、损耗和一段自定义 tag 的铁剑 */
    private static ItemStack taggedSword() {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setHoverName(Component.literal("Keep Me"));
        sword.setDamageValue(12);
        CompoundTag custom = new CompoundTag();
        custom.putInt("samples", 7);
        custom.putString("mode", "fill");
        sword.getOrCreateTag().put("ec5_test", custom);
        return sword;
    }

    /** 铁剑 + 木棍 → result，nbt_source = 铁剑 */
    private static KeepNbtRecipes.ShapelessKeepNbt shapeless(ItemStack result) {
        NonNullList<Ingredient> ingredients = NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.IRON_SWORD), Ingredient.of(Items.STICK));
        ShapelessRecipe base = new ShapelessRecipe(EssentialCraft5.id("test/keep_nbt_shapeless_direct"), "", CraftingBookCategory.MISC, result, ingredients);
        return new KeepNbtRecipes.ShapelessKeepNbt(base, Ingredient.of(Items.IRON_SWORD));
    }

    /** " P / PGP / P "：P = 纸、G = 铁剑 → result，nbt_source = 铁剑 */
    private static KeepNbtRecipes.ShapedKeepNbt shaped(ItemStack result) {
        Ingredient p = Ingredient.of(Items.PAPER);
        Ingredient g = Ingredient.of(Items.IRON_SWORD);
        NonNullList<Ingredient> ingredients = NonNullList.of(Ingredient.EMPTY,
                Ingredient.EMPTY, p, Ingredient.EMPTY,
                p, g, p,
                Ingredient.EMPTY, p, Ingredient.EMPTY);
        ShapedRecipe base = new ShapedRecipe(EssentialCraft5.id("test/keep_nbt_shaped_direct"), "", CraftingBookCategory.MISC, 3, 3, ingredients, result);
        return new KeepNbtRecipes.ShapedKeepNbt(base, g);
    }

    private static CrafterGrid upgradeGrid(ItemStack center) {
        ItemStack paper = new ItemStack(Items.PAPER);
        return new CrafterGrid(new ItemStack[] {
                ItemStack.EMPTY, paper, ItemStack.EMPTY,
                paper, center, paper,
                ItemStack.EMPTY, paper, ItemStack.EMPTY});
    }

    private static <T> T roundTrip(Consumer<FriendlyByteBuf> write, Function<FriendlyByteBuf, T> read) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            write.accept(buf);
            return read.apply(buf);
        } finally {
            buf.release();
        }
    }
}
