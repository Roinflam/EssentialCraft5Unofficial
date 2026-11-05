package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.module.BlankModuleItem;
import com.flameforge.essentialcraft5.item.module.ModuleConfig;
import com.flameforge.essentialcraft5.item.module.ModuleTicker;
import com.flameforge.essentialcraft5.recipe.ArmorModuleRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.RegistryObject;

/**
 * 宅男套装的模块平台（第十批 10c-F，tools/plan/batch10.md §4.3）：空白模块、装模块的特殊配方、配置 {@code general.computer_modules}。
 * 机制在 {@code item/module/}：登记表与盔甲 NBT {@code ArmorModules}、模块种类 {@code ModuleType}、模块物品 {@code ArmorModuleItem}、
 * 能装模块的盔甲 {@code IModularArmor}（宅男套装实现）、每人快照 {@code ModuleCache}、每秒电费 {@code ModuleTicker}、冷却 {@code ModuleCooldowns}、
 * 终端「模块」页的服务端 {@code ModuleTerminal}；装的配方 {@code recipe/ArmorModuleRecipe}。
 * <p>
 * 新注册名（命名表 tools/id_map_items.json，EC5 新增）：{@code blank_module}（空白模块）。六块模块（回溯、矩阵过滤、矩阵分流、累加器、削减、相位锚）
 * 由各自的注册类登记（{@code ArmorModules.register} + {@code ArmorModuleItem}），接在本类后面 init。
 * 空白模块的配方要审判庭芯片，写在 {@code data/essentialcraft5/recipes/terminal/blank_module.json}；装模块的配方 {@code recipes/terminal/armor_module_install.json}。
 * 手册打勾进度 {@code advancements/guide/terminal/blank_module.json}（拿到空白模块）、{@code guide/terminal/modules.json}（身上两块模块生效，代码发）。
 * 文字 tools/lang/keys/ecmodules.json。
 */
public final class ECModules {

    /** 空白模块：六块模块的底料，本身没有效果（贴图 item/module/blank_module） */
    public static final RegistryObject<BlankModuleItem> BLANK_MODULE = ECItems.register("blank_module",
            () -> new BlankModuleItem(new Item.Properties()), ItemSpec.generated("item/module/blank_module"));

    /** 装模块的工作台特殊配方（JSON {@code "type": "essentialcraft5:armor_module_install"}） */
    public static final RegistryObject<RecipeSerializer<ArmorModuleRecipe>> ARMOR_MODULE_INSTALL =
            ECRecipes.serializer("armor_module_install", () -> new SimpleCraftingRecipeSerializer<>(ArmorModuleRecipe::new));

    /** 手册步骤「做出空白模块」（拿到就打勾，inventory_changed） */
    public static final ResourceLocation GUIDE_BLANK_MODULE = EssentialCraft5.id("guide/terminal/blank_module");
    /** 手册步骤「装上两块模块」（身上两块模块生效时代码发，见 ModuleTicker） */
    public static final ResourceLocation GUIDE_MODULES = ModuleTicker.GUIDE_MODULES;

    private static boolean initialized;

    private ECModules() {
    }

    /** 由 ECRegistries.register() 调用：物品、序列化器在类加载时登记，这里登记配置 general.computer_modules（各模块的小节经 ModuleConfig.section 挂进来） */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ECConfig.general(ModuleConfig::define);
    }
}
