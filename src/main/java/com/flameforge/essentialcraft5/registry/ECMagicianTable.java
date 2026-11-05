package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.MagicianTableBlock;
import com.flameforge.essentialcraft5.blockentity.MagicianTableBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.MagicianTableMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 魔力工作台模块（原作 magicianTable → magician_table）：方块、方块实体、菜单。
 * 升级表见 api/MagicianTableUpgrades，配方是 data/essentialcraft5/recipes/magician_table/ 下的 JSON
 * （机器配方类型 essentialcraft5:magician_table，加上工作台本身的合成配方）。
 * 客户端部分（界面、升级方块渲染、升级物品提示）在 client/MagicianTableClient。
 */
public final class ECMagicianTable {

    /**
     * 原作 magicianTable：registerBlockSimple(…, 1, 1, 0)（先抗爆后硬度，不透光度 0），Material.ROCK、MapColor.PURPLE，
     * OBJ 模型 → 硬度 1、抗爆 1.0，天空光穿透
     */
    public static final RegistryObject<MagicianTableBlock> MAGICIAN_TABLE = ECBlocks.register("magician_table",
            () -> new MagicianTableBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 1F, true))
                    .noOcclusion()),
            BlockSpec.of(BlockSpec.obj("magiciantable", "block/model/magiciantable")));

    public static final RegistryObject<BlockEntityType<MagicianTableBlockEntity>> MAGICIAN_TABLE_BE =
            ECBlockEntities.type("magician_table", MagicianTableBlockEntity::new, MAGICIAN_TABLE);

    public static final RegistryObject<MenuType<MagicianTableMenu>> MAGICIAN_TABLE_MENU =
            ECMenus.register("magician_table", MagicianTableMenu::new);

    private ECMagicianTable() {
    }

    public static void init() {
        ECConfig.machine(MagicianTableBlockEntity.Config::define);
    }
}
