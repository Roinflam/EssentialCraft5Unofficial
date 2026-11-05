package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.EnchantUpgraderBlock;
import com.flameforge.essentialcraft5.blockentity.EnchantUpgraderBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.enchant.EnchantUpgrades;
import com.flameforge.essentialcraft5.enchant.EnchantUpgrades.Tier;
import com.flameforge.essentialcraft5.menu.EnchantUpgraderMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 附魔升级模块：附魔增幅台（中期，附魔书 1 → 25 级）和附魔升华台（超大后期，25 → 50 级），EC5 新机器，原作没有。
 * 规则、价格、配置见 {@link EnchantUpgrades}，机器见 {@link EnchantUpgraderBlockEntity}。原版铁砧不改。
 * <p>
 * 注册名：{@code enchant_amplifier}、{@code enchant_ascender}（方块、方块实体、菜单同名；命名表里是 EC5 新增的两条，old_id 为 null）。
 * 方块属性照魔力附魔台（硬度 1、抗爆按 1.12 规则换算、透天空光）；模型是手写的 JSON 方块模型（贴图自绘），
 * 增幅台是紫底青纹的讲台，升华台是恶魔护板色的黑曜石台加四根红晶。
 * <p>
 * 文字在 {@code tools/lang/keys/ecenchantupgrade.json}，配方 {@code data/essentialcraft5/recipes/enchantupgrade/}，
 * 手册支线 {@code guide/<语言>/devices.json} 的 enchant_amplifier、enchant_ascender 两条。客户端部分（界面、悬浮的书）见
 * {@code client/EnchantUpgradeClient}。
 */
public final class ECEnchantUpgrade {

    public static final RegistryObject<EnchantUpgraderBlock> ENCHANT_AMPLIFIER = ECBlocks.register("enchant_amplifier",
            () -> new EnchantUpgraderBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 1F, true))
                    .noOcclusion(), Tier.AMPLIFIER, () -> ECEnchantUpgrade.AMPLIFIER_BE.get()),
            BlockSpec.of(BlockSpec.existing("enchant_amplifier")));

    public static final RegistryObject<BlockEntityType<EnchantUpgraderBlockEntity>> AMPLIFIER_BE =
            ECBlockEntities.type("enchant_amplifier", EnchantUpgraderBlockEntity::create, ENCHANT_AMPLIFIER);

    public static final RegistryObject<MenuType<EnchantUpgraderMenu>> AMPLIFIER_MENU =
            ECMenus.register("enchant_amplifier", (id, inventory, buf) -> new EnchantUpgraderMenu(id, inventory, buf, Tier.AMPLIFIER));

    public static final RegistryObject<EnchantUpgraderBlock> ENCHANT_ASCENDER = ECBlocks.register("enchant_ascender",
            () -> new EnchantUpgraderBlock(ECBlocks.machine(MapColor.COLOR_BLACK, 1F, ECBlocks.legacyResistance(1F, 1F, true))
                    .noOcclusion(), Tier.ASCENDER, () -> ECEnchantUpgrade.ASCENDER_BE.get()),
            BlockSpec.of(BlockSpec.existing("enchant_ascender")));

    public static final RegistryObject<BlockEntityType<EnchantUpgraderBlockEntity>> ASCENDER_BE =
            ECBlockEntities.type("enchant_ascender", EnchantUpgraderBlockEntity::create, ENCHANT_ASCENDER);

    public static final RegistryObject<MenuType<EnchantUpgraderMenu>> ASCENDER_MENU =
            ECMenus.register("enchant_ascender", (id, inventory, buf) -> new EnchantUpgraderMenu(id, inventory, buf, Tier.ASCENDER));

    private ECEnchantUpgrade() {
    }

    public static RegistryObject<EnchantUpgraderBlock> block(Tier tier) {
        return tier == Tier.AMPLIFIER ? ENCHANT_AMPLIFIER : ENCHANT_ASCENDER;
    }

    public static RegistryObject<MenuType<EnchantUpgraderMenu>> menu(Tier tier) {
        return tier == Tier.AMPLIFIER ? AMPLIFIER_MENU : ASCENDER_MENU;
    }

    public static RegistryObject<BlockEntityType<EnchantUpgraderBlockEntity>> blockEntity(Tier tier) {
        return tier == Tier.AMPLIFIER ? AMPLIFIER_BE : ASCENDER_BE;
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 */
    public static void init() {
        ECConfig.machine(EnchantUpgrades::define);
    }
}
