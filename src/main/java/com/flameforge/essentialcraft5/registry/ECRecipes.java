package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.recipe.KeepNbtRecipes;
import com.flameforge.essentialcraft5.recipe.MagicianTableRecipe;
import com.flameforge.essentialcraft5.recipe.RadiatingChamberRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * 机器配方类型与序列化器。原作写死在 RecipesCore 的机器配方都改成数据包 JSON。
 * <p>
 * 这里只放一批的两种机器配方，和几个模块共用的工作台配方序列化器（第六批 6b：保留 NBT 的有序 / 无序配方，见 {@link KeepNbtRecipes}）。
 * 模块自己的配方类型在模块注册类里用 {@link #type} / {@link #serializer} 声明，不改这个文件：
 * <pre>{@code
 * public final class ECMagmatic {
 *     public static final RegistryObject<RecipeType<OreSmeltingRecipe>> ORE_SMELTING = ECRecipes.type("ore_smelting");
 *     public static final RegistryObject<RecipeSerializer<OreSmeltingRecipe>> ORE_SMELTING_SERIALIZER =
 *             ECRecipes.serializer("ore_smelting", OreSmeltingRecipe.Serializer::new);
 * }
 * }</pre>
 * 配方 JSON 的 {@code "type"} 写 {@code essentialcraft5:ore_smelting}（序列化器的注册名），
 * 配方类 {@code getType()} 返回 {@code ECMagmatic.ORE_SMELTING.get()}、{@code getSerializer()} 返回序列化器，
 * 查配方用 {@code level.getRecipeManager().getAllRecipesFor(ECMagmatic.ORE_SMELTING.get())}。
 * 静态字段在模块类加载（{@code EC<模块>.init()} 被调用）时登记，早于注册事件，不用额外接线。
 */
public final class ECRecipes {

    public static final RegistryObject<RecipeType<MagicianTableRecipe>> MAGICIAN_TABLE = type("magician_table");
    public static final RegistryObject<RecipeSerializer<MagicianTableRecipe>> MAGICIAN_TABLE_SERIALIZER =
            serializer("magician_table", MagicianTableRecipe.Serializer::new);

    public static final RegistryObject<RecipeType<RadiatingChamberRecipe>> RADIATING_CHAMBER = type("radiating_chamber");
    public static final RegistryObject<RecipeSerializer<RadiatingChamberRecipe>> RADIATING_CHAMBER_SERIALIZER =
            serializer("radiating_chamber", RadiatingChamberRecipe.Serializer::new);

    /**
     * 产物带上某件原料 NBT 的工作台配方（类型仍是 {@code minecraft:crafting}），JSON 的 {@code "type"} 写这两个名字，
     * 其余字段同原版 crafting_shaped / crafting_shapeless，另加 {@code "nbt_source"}。建筑魔杖换模式用无序的，过滤器升级用有序的。
     */
    public static final RegistryObject<RecipeSerializer<KeepNbtRecipes.ShapedKeepNbt>> CRAFTING_SHAPED_KEEP_NBT =
            serializer("crafting_shaped_keep_nbt", KeepNbtRecipes.ShapedSerializer::new);
    public static final RegistryObject<RecipeSerializer<KeepNbtRecipes.ShapelessKeepNbt>> CRAFTING_SHAPELESS_KEEP_NBT =
            serializer("crafting_shapeless_keep_nbt", KeepNbtRecipes.ShapelessSerializer::new);

    private ECRecipes() {
    }

    static void init() {
    }

    /** 登记一个配方类型 {@code essentialcraft5:<name>}（原版 RecipeType.simple，toString 就是注册名） */
    public static <T extends Recipe<?>> RegistryObject<RecipeType<T>> type(String name) {
        return ECRegistries.RECIPE_TYPES.register(name, () -> RecipeType.simple(EssentialCraft5.id(name)));
    }

    /** 登记一个配方序列化器 {@code essentialcraft5:<name>}，配方 JSON 的 "type" 写这个名字 */
    public static <T extends Recipe<?>> RegistryObject<RecipeSerializer<T>> serializer(String name, Supplier<RecipeSerializer<T>> factory) {
        return ECRegistries.RECIPE_SERIALIZERS.register(name, factory);
    }
}
