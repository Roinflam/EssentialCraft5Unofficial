package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.AnimalSeparatorBlock;
import com.flameforge.essentialcraft5.blockentity.AnimalSeparatorBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.AnimalSeparatorMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 动物分离器模块（第四批 4a，batch4.md §3.2）：魔力动物分离器（幼年 / 成年）。
 * <p>
 * 新旧注册名：原作一个方块 {@code essentialcraft:device} 的 meta 6（childSeparator）→ {@code child_separator}、
 * meta 7（adultSeparator）→ {@code adult_separator}；两种方块共用方块实体类型和菜单类型 {@code animal_separator}（原作 TileAnimalSeparator，
 * 界面借射线塔的那套，这里单独做了一个同样布局、加了主人状态的界面）。
 * 原作 BlocksCore L168 先 setHardness(1) 再 setResistance(1) → 硬度 1、抗爆 0.6；Material.ROCK；整块不透明；六面同贴图。
 * <p>
 * 工作台配方在 data/essentialcraft5/recipes/separators/，界面与手册文字在 tools/lang/keys/ecseparators.json，
 * 手册条目是「农牧自动化」章的部件 tools/guide/parts/ECSeparators.json（由集成的人拼进 farming.json）。客户端部分见 client/SeparatorsClient。
 */
public final class ECSeparators {

    public static final RegistryObject<AnimalSeparatorBlock> CHILD_SEPARATOR = ECBlocks.register("child_separator",
            () -> new AnimalSeparatorBlock(ECBlocks.machine(MapColor.STONE, 1F, ECBlocks.legacyResistance(1F, 1F, false)), true),
            BlockSpec.of(BlockSpec.cubeAll("block/childseparator")));

    public static final RegistryObject<AnimalSeparatorBlock> ADULT_SEPARATOR = ECBlocks.register("adult_separator",
            () -> new AnimalSeparatorBlock(ECBlocks.machine(MapColor.STONE, 1F, ECBlocks.legacyResistance(1F, 1F, false)), false),
            BlockSpec.of(BlockSpec.cubeAll("block/adultseparator")));

    public static final RegistryObject<BlockEntityType<AnimalSeparatorBlockEntity>> ANIMAL_SEPARATOR_BE =
            ECBlockEntities.type("animal_separator", AnimalSeparatorBlockEntity::new, CHILD_SEPARATOR, ADULT_SEPARATOR);

    public static final RegistryObject<MenuType<AnimalSeparatorMenu>> ANIMAL_SEPARATOR_MENU =
            ECMenus.register("animal_separator", AnimalSeparatorMenu::new);

    private ECSeparators() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 */
    public static void init() {
        ECConfig.machine(AnimalSeparatorBlockEntity.Config::define);
    }
}
