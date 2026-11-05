package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.effect.ArmorShredEffect;
import com.flameforge.essentialcraft5.item.module.ArmorModuleItem;
import com.flameforge.essentialcraft5.item.module.ArmorModules;
import com.flameforge.essentialcraft5.item.module.ModuleConfig;
import com.flameforge.essentialcraft5.item.module.ModuleType;
import com.flameforge.essentialcraft5.item.module.combat.AccumulatorEvents;
import com.flameforge.essentialcraft5.item.module.combat.PhaseAnchorEvents;
import com.flameforge.essentialcraft5.item.module.combat.ShredEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

/**
 * 宅男套装的三块战斗模块（第十批 10c-M3，tools/plan/batch10.md §5.12；policy §4.13），接在模块平台 {@link ECModules} 后面 init。
 * <ul>
 *   <li>{@code accumulator_module} 累加器（胸甲）：打进对方受击无敌、被整下吞掉的伤害记账，下一下真打实时并进那一下，默认最多翻一倍（{@code max_ratio}）
 *       （{@code item/module/combat/AccumulatorEvents}）。</li>
 *   <li>{@code shred_module} 削减（胸甲）：打实的每一下给目标叠一层破甲（10b 的效果 {@code armor_shred}，每级扣多少由这里的配置定）
 *       （{@code item/module/combat/ShredEvents}）。</li>
 *   <li>{@code phase_anchor_module} 相位锚（护腿或靴子，每秒耗电）：周围的敌对生物、10 秒内和你互相交过手的玩家挂上「锚定」，瞬移和传送道具用不了
 *       （{@code item/module/combat/PhaseAnchorEvents}）。</li>
 * </ul>
 * 命名表里三条都是 EC5 新增；配方在 {@code data/essentialcraft5/recipes/terminal/<注册名>.json}，贴图 {@code textures/item/module/<注册名>.png}，
 * 文字 {@code tools/lang/keys/eccombatmodules.json}，相位锚的豁免标签 {@code data/essentialcraft5/tags/entity_types/anchor_immune.json}（默认空）。
 * 配置在 {@code general.computer_modules.accumulator / shred / phase_anchor}（经 {@link ModuleConfig#section} 挂进模块平台那一节）。
 */
public final class ECCombatModules {

    /** 累加器：只装胸甲，不按秒耗电（记账、结算都不花 MRU） */
    public static final ModuleType ACCUMULATOR = ArmorModules.register(ModuleType.builder(EssentialCraft5.id("accumulator_module"))
            .parts(ArmorItem.Type.CHESTPLATE)
            .build());
    /** 削减：只装胸甲，不耗电 */
    public static final ModuleType SHRED = ArmorModules.register(ModuleType.builder(EssentialCraft5.id("shred_module"))
            .parts(ArmorItem.Type.CHESTPLATE)
            .build());
    /** 相位锚：护腿或靴子，开着时每秒 {@code mru_per_second}（由模块平台每秒统一扣） */
    public static final ModuleType PHASE_ANCHOR = ArmorModules.register(ModuleType.builder(EssentialCraft5.id("phase_anchor_module"))
            .parts(ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS)
            .mruPerSecond(() -> PhaseAnchorEvents.Config.values().mruPerSecond())
            .build());

    public static final RegistryObject<ArmorModuleItem> ACCUMULATOR_MODULE = ECItems.register("accumulator_module",
            () -> new ArmorModuleItem(ACCUMULATOR, new Item.Properties()), ItemSpec.generated("item/module/accumulator_module"));
    public static final RegistryObject<ArmorModuleItem> SHRED_MODULE = ECItems.register("shred_module",
            () -> new ArmorModuleItem(SHRED, new Item.Properties()), ItemSpec.generated("item/module/shred_module"));
    public static final RegistryObject<ArmorModuleItem> PHASE_ANCHOR_MODULE = ECItems.register("phase_anchor_module",
            () -> new ArmorModuleItem(PHASE_ANCHOR, new Item.Properties()), ItemSpec.generated("item/module/phase_anchor_module"));

    /** 相位锚不锚的敌对生物（默认空）：给整合包作者留口子，例如靠瞬移放招的模组首领 */
    public static final TagKey<EntityType<?>> ANCHOR_IMMUNE = TagKey.create(Registries.ENTITY_TYPE, EssentialCraft5.id("anchor_immune"));

    private static boolean initialized;

    private ECCombatModules() {
    }

    /**
     * 由 ECRegistries.register() 调用（排在 ECModules.init() 之后）：模块和物品在类加载时登记，这里登记三个配置小节，
     * 并让破甲效果每级扣的量改读削减的配置（谁加的破甲都按它算）。
     */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ModuleConfig.section(AccumulatorEvents.Config::define);
        ModuleConfig.section(ShredEvents.Config::define);
        ModuleConfig.section(PhaseAnchorEvents.Config::define);
        ArmorShredEffect.setPerLevel(() -> ShredEvents.Config.values().armorPerLevel(), () -> ShredEvents.Config.values().toughnessPerLevel());
    }
}
