package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.module.ArmorModuleItem;
import com.flameforge.essentialcraft5.item.module.ArmorModules;
import com.flameforge.essentialcraft5.item.module.ModuleConfig;
import com.flameforge.essentialcraft5.item.module.ModuleType;
import com.flameforge.essentialcraft5.item.module.SurvivalModuleConfig;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

/**
 * 宅男套装的三块生存模块（第十批 10c-M2，tools/plan/batch10.md §5.11），接在模块平台 {@link ECModules} 后面：
 * <ul>
 *   <li>{@code rewind_module} 回溯（头盔）：致命一击退回约 5 秒前站过的地方，事件在 {@code item/module/RewindEvents}；</li>
 *   <li>{@code matrix_filter_module} 矩阵过滤（头盔）：有害的状态效果加不上身、改记矩阵损伤，{@code item/module/MatrixFilterEvents}；</li>
 *   <li>{@code matrix_shunt_module} 矩阵分流（胸甲）：魔法伤害的一半不扣血、改记矩阵损伤，{@code item/module/MatrixShuntEvents}。</li>
 * </ul>
 * 三块都只作用于穿戴者自己、不按秒耗电（回溯每次、矩阵过滤每拦一个新效果自己先算后扣）；加矩阵损伤一律走 {@code CorruptionEffects.addMatrixDamage}，
 * 过滤 / 分流先看 {@code matrixRoom}（batch10.md §14.7）。配置 {@code general.computer_modules.{rewind,matrix_filter,matrix_shunt}.*}（{@link SurvivalModuleConfig}）。
 * <p>
 * 新注册名（命名表 tools/id_map_items.json，EC5 新增）：{@code rewind_module}、{@code matrix_filter_module}、{@code matrix_shunt_module}；
 * 贴图 {@code textures/item/module/<注册名>.png}；配方 {@code data/essentialcraft5/recipes/terminal/<注册名>.json}；文字 tools/lang/keys/ecsurvivalmodules.json。
 */
public final class ECSurvivalModules {

    /** 回溯：头盔，每次触发花 {@code rewind.mru_cost} */
    public static final ModuleType REWIND = ArmorModules.register(ModuleType.builder(EssentialCraft5.id("rewind_module"))
            .parts(ArmorItem.Type.HELMET)
            .mruPerUse(() -> SurvivalModuleConfig.rewind().mruCost())
            .build());
    /** 矩阵过滤：头盔，每拦一个新效果花 {@code matrix_filter.mru_per_effect} */
    public static final ModuleType MATRIX_FILTER = ArmorModules.register(ModuleType.builder(EssentialCraft5.id("matrix_filter_module"))
            .parts(ArmorItem.Type.HELMET)
            .mruPerUse(() -> SurvivalModuleConfig.filter().mruPerEffect())
            .build());
    /** 矩阵分流：胸甲，不花 MRU（代价是矩阵损伤） */
    public static final ModuleType MATRIX_SHUNT = ArmorModules.register(ModuleType.builder(EssentialCraft5.id("matrix_shunt_module"))
            .parts(ArmorItem.Type.CHESTPLATE)
            .build());

    public static final RegistryObject<ArmorModuleItem> REWIND_MODULE = ECItems.register("rewind_module",
            () -> new ArmorModuleItem(REWIND, new Item.Properties()), ItemSpec.generated("item/module/rewind_module"));
    public static final RegistryObject<ArmorModuleItem> MATRIX_FILTER_MODULE = ECItems.register("matrix_filter_module",
            () -> new ArmorModuleItem(MATRIX_FILTER, new Item.Properties()), ItemSpec.generated("item/module/matrix_filter_module"));
    public static final RegistryObject<ArmorModuleItem> MATRIX_SHUNT_MODULE = ECItems.register("matrix_shunt_module",
            () -> new ArmorModuleItem(MATRIX_SHUNT, new Item.Properties()), ItemSpec.generated("item/module/matrix_shunt_module"));

    private static boolean initialized;

    private ECSurvivalModules() {
    }

    /** 由 ECRegistries.register() 在 ECModules.init() 之后调用：模块和物品在类加载时登记，这里把配置小节挂进 general.computer_modules */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ModuleConfig.section(SurvivalModuleConfig::define);
    }
}
