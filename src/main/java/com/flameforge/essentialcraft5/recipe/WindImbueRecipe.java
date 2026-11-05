package com.flameforge.essentialcraft5.recipe;

import com.flameforge.essentialcraft5.item.WindHornItem;
import com.flameforge.essentialcraft5.registry.ECWind;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 注风配方（原作 api/WindImbueRecipe，RecipesCore L205~209 写死三条）：在风之符文上把手里的一个原料换成产物，花祭坛水晶柱里的 ESPE。
 * 数据包 JSON，放 data/essentialcraft5/recipes/wind/：
 * <pre>
 * { "type": "essentialcraft5:wind_imbue",
 *   "ingredient": { "item": "minecraft:diamond" },
 *   "result": { "item": "essentialcraft5:wind_gem" },
 *   "espe": 10000,                 // 注一次要多少 ESPE（原作 enderEnergy）
 *   "espe_config": "gem_espe",     // 可选：花费改读 SERVER 配置；gem_espe = general.wind.gem_espe，windbound_espe = general.wind.windbound_espe，
 *                                  //       horn_espe = general.wind_horn.espe；配置没加载时用上面的 espe
 *   "grants_windbound": false,     // 可选：true 时不换物品，原料原样留着，玩家受到风之眷顾（原作灵魂石那条；result 只给手册显示）
 *   "requires_windbound": false }  // 可选：true 时只有已受风之眷顾的玩家能注（风之号角，第十批 10b）；没受眷顾的直接拒绝，什么都不扣
 * </pre>
 * 原作判「是不是灵魂石那条」靠拿灵魂石去试原料（{@code rec.input.apply(new ItemStack(soulStone))}），这里改成配方自己声明，数据包可以另加别的眷顾原料。
 * 水瓶那条原料用 {@code forge:partial_nbt}（原作 IngredientUtils.getIngredientNBT，只认 Potion=minecraft:water 的药水瓶）。
 * {@link #matches} 只看输入格第一格是不是这种原料（原作 getRecipeByInput）；查找用 {@link #find}。
 */
public class WindImbueRecipe implements Recipe<Container> {

    /** espe_config 认的名字：风之水晶（general.wind.gem_espe） */
    public static final String CONFIG_GEM_ESPE = "gem_espe";
    /** espe_config 认的名字：风之号角（general.wind_horn.espe，第十批 10b） */
    public static final String CONFIG_HORN_ESPE = "horn_espe";
    /** espe_config 认的名字：受风之眷顾（general.wind.windbound_espe） */
    public static final String CONFIG_WINDBOUND_ESPE = "windbound_espe";

    private final ResourceLocation id;
    private final Ingredient input;
    private final ItemStack result;
    private final double espe;
    @Nullable
    private final String espeConfig;
    private final boolean grantsWindbound;
    private final boolean requiresWindbound;

    public WindImbueRecipe(ResourceLocation id, Ingredient input, ItemStack result, double espe, @Nullable String espeConfig, boolean grantsWindbound) {
        this(id, input, result, espe, espeConfig, grantsWindbound, false);
    }

    public WindImbueRecipe(ResourceLocation id, Ingredient input, ItemStack result, double espe, @Nullable String espeConfig, boolean grantsWindbound,
                           boolean requiresWindbound) {
        this.id = id;
        this.input = input;
        this.result = result;
        this.espe = espe;
        this.espeConfig = espeConfig;
        this.grantsWindbound = grantsWindbound;
        this.requiresWindbound = requiresWindbound;
    }

    /** 手里这个物品能注风的第一条配方，没有返回 null（两端都能调：客户端有同步来的配方表） */
    @Nullable
    public static WindImbueRecipe find(Level level, ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return level.getRecipeManager().getRecipeFor(ECWind.WIND_IMBUE.get(), new SimpleContainer(stack), level).orElse(null);
    }

    public Ingredient getInput() {
        return input;
    }

    /** 注一次要多少 ESPE：写了 espe_config 的读配置（SERVER 配置会同步给客户端，手册显示的也是服务器的值），否则读 JSON 的 espe */
    public double getEspe() {
        if (CONFIG_GEM_ESPE.equals(espeConfig)) {
            return ECWind.Config.gemESPE();
        }
        if (CONFIG_HORN_ESPE.equals(espeConfig)) {
            return WindHornItem.Config.espe();
        }
        if (CONFIG_WINDBOUND_ESPE.equals(espeConfig)) {
            return ECWind.Config.windboundESPE();
        }
        return espe;
    }

    /** JSON 里写的 espe（不看配置） */
    public double getBaseEspe() {
        return espe;
    }

    @Nullable
    public String getEspeConfig() {
        return espeConfig;
    }

    /** 是「授予风之眷顾」的配方（不换物品） */
    public boolean grantsWindbound() {
        return grantsWindbound;
    }

    /** 只有已受风之眷顾的玩家能注（风之号角） */
    public boolean requiresWindbound() {
        return requiresWindbound;
    }

    /** 产物（副本） */
    public ItemStack assemble(ItemStack input) {
        return result.copy();
    }

    @Override
    public boolean matches(Container container, Level level) {
        return container.getContainerSize() > 0 && input.test(container.getItem(0));
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, input);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ECWind.WIND_IMBUE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ECWind.WIND_IMBUE.get();
    }

    /** 不进原版配方书 */
    @Override
    public boolean isSpecial() {
        return true;
    }

    public static class Serializer implements RecipeSerializer<WindImbueRecipe> {

        @Override
        public WindImbueRecipe fromJson(ResourceLocation id, JsonObject json) {
            Ingredient input = Ingredient.fromJson(GsonHelper.getNonNull(json, "ingredient"));
            double espe = GsonHelper.getAsDouble(json, "espe");
            // NaN、无穷、负数写进来，比较「够不够」时永远成立或永远不成立
            if (!Double.isFinite(espe) || espe < 0) {
                throw new JsonParseException("espe 必须是非负的有限数，实际 " + espe);
            }
            String config = json.has("espe_config") ? GsonHelper.getAsString(json, "espe_config").toLowerCase(Locale.ROOT) : null;
            if (config != null && !CONFIG_GEM_ESPE.equals(config) && !CONFIG_HORN_ESPE.equals(config) && !CONFIG_WINDBOUND_ESPE.equals(config)) {
                throw new JsonParseException("espe_config 只能是 " + CONFIG_GEM_ESPE + "、" + CONFIG_HORN_ESPE + " 或 " + CONFIG_WINDBOUND_ESPE + "，实际 " + config);
            }
            ItemStack result = RecipeJson.result(json);
            if (result.isEmpty()) {
                throw new JsonParseException("result 不能为空");
            }
            boolean windbound = GsonHelper.getAsBoolean(json, "grants_windbound", false);
            boolean requires = GsonHelper.getAsBoolean(json, "requires_windbound", false);
            return new WindImbueRecipe(id, input, result, espe, config, windbound, requires);
        }

        @Override
        public WindImbueRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            Ingredient input = Ingredient.fromNetwork(buf);
            ItemStack result = buf.readItem();
            double espe = buf.readDouble();
            String config = buf.readBoolean() ? buf.readUtf(32) : null;
            boolean windbound = buf.readBoolean();
            boolean requires = buf.readBoolean();
            return new WindImbueRecipe(id, input, result, espe, config, windbound, requires);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, WindImbueRecipe recipe) {
            recipe.input.toNetwork(buf);
            buf.writeItem(recipe.result);
            buf.writeDouble(recipe.espe);
            buf.writeBoolean(recipe.espeConfig != null);
            if (recipe.espeConfig != null) {
                buf.writeUtf(recipe.espeConfig, 32);
            }
            buf.writeBoolean(recipe.grantsWindbound);
            buf.writeBoolean(recipe.requiresWindbound);
        }
    }
}
