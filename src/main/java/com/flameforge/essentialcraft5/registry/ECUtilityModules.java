package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.module.ArmorModuleItem;
import com.flameforge.essentialcraft5.item.module.ArmorModules;
import com.flameforge.essentialcraft5.item.module.ModuleConfig;
import com.flameforge.essentialcraft5.item.module.ModuleType;
import com.flameforge.essentialcraft5.item.module.UtilityModuleConfig;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

/**
 * 宅男套装的四块生活模块（第十二批，batch12.md §4.7；policy §4.15），接在战斗模块 {@link ECCombatModules} 后面 init。
 * 不和套装自带的能力（补氧气、饱食度、夜视、免摔、着火抗火）重复，和已有六块抢槽位，让「装哪块」成为选择：
 * <ul>
 *   <li>{@code attraction_module} 吸附（护腿，每秒耗电）：每隔一会儿把身边没人扔的掉落物和经验球往身上推一把，潜行时不吸；
 *       推法和所有保护（看得见才推、别人的掉落物不推、领地探测、每次件数上限）照磁化法杖。</li>
 *   <li>{@code stride_module} 攀行（靴子，每秒耗电）：步高加 {@code step_bonus}，和靴子自带的一起正好 1.6 格。</li>
 *   <li>{@code steady_hand_module} 稳手（护腿或靴子，不耗电）：悬空、头在水里时挖掘不再减慢。</li>
 *   <li>{@code veil_module} 隐匿（头盔，每秒耗电）：怪物发现你的距离缩短，潜行时缩得更多；对玩家没有效果。</li>
 * </ul>
 * 效果都在 {@code item/module/UtilityModuleEvents}，配置 {@code general.computer_modules.attraction / stride / veil}（{@link UtilityModuleConfig}）。
 * <p>
 * 新注册名（命名表 tools/id_map_items.json，EC5 新增）：{@code attraction_module}、{@code stride_module}、{@code steady_hand_module}、{@code veil_module}；
 * 贴图 {@code textures/item/module/<注册名>.png}；配方 {@code data/essentialcraft5/recipes/terminal/<注册名>.json}（空白模块 + 各自的料）；
 * 文字 tools/lang/keys/ecutilitymodules.json；手册第二十一章 {@code terminal} 的四篇说明条目。
 */
public final class ECUtilityModules {

    /** 吸附：只装护腿，开着时每秒 {@code attraction.mru_per_second} */
    public static final ModuleType ATTRACTION = ArmorModules.register(ModuleType.builder(EssentialCraft5.id("attraction_module"))
            .parts(ArmorItem.Type.LEGGINGS)
            .mruPerSecond(() -> UtilityModuleConfig.attraction().mruPerSecond())
            .build());
    /** 攀行：只装靴子，开着时每秒 {@code stride.mru_per_second} */
    public static final ModuleType STRIDE = ArmorModules.register(ModuleType.builder(EssentialCraft5.id("stride_module"))
            .parts(ArmorItem.Type.BOOTS)
            .mruPerSecond(() -> UtilityModuleConfig.stride().mruPerSecond())
            .build());
    /**
     * 稳手：护腿或靴子，不耗电。挖掘速度两端各算一次，客户端不知道这一秒的电付没付上，收电就可能两边一快一慢、方块挖完又弹回来（batch12.md §2）
     */
    public static final ModuleType STEADY_HAND = ArmorModules.register(ModuleType.builder(EssentialCraft5.id("steady_hand_module"))
            .parts(ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS)
            .build());
    /** 隐匿：只装头盔，开着时每秒 {@code veil.mru_per_second} */
    public static final ModuleType VEIL = ArmorModules.register(ModuleType.builder(EssentialCraft5.id("veil_module"))
            .parts(ArmorItem.Type.HELMET)
            .mruPerSecond(() -> UtilityModuleConfig.veil().mruPerSecond())
            .build());

    public static final RegistryObject<ArmorModuleItem> ATTRACTION_MODULE = ECItems.register("attraction_module",
            () -> new ArmorModuleItem(ATTRACTION, new Item.Properties()), ItemSpec.generated("item/module/attraction_module"));
    public static final RegistryObject<ArmorModuleItem> STRIDE_MODULE = ECItems.register("stride_module",
            () -> new ArmorModuleItem(STRIDE, new Item.Properties()), ItemSpec.generated("item/module/stride_module"));
    public static final RegistryObject<ArmorModuleItem> STEADY_HAND_MODULE = ECItems.register("steady_hand_module",
            () -> new ArmorModuleItem(STEADY_HAND, new Item.Properties()), ItemSpec.generated("item/module/steady_hand_module"));
    public static final RegistryObject<ArmorModuleItem> VEIL_MODULE = ECItems.register("veil_module",
            () -> new ArmorModuleItem(VEIL, new Item.Properties()), ItemSpec.generated("item/module/veil_module"));

    private static boolean initialized;

    private ECUtilityModules() {
    }

    /** 由 ECRegistries.register() 在 ECCombatModules.init() 之后调用：模块和物品在类加载时登记，这里把三个配置小节挂进 general.computer_modules */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ModuleConfig.section(UtilityModuleConfig::define);
    }
}
