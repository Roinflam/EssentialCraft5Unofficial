package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.MagicalEnchanterBlock;
import com.flameforge.essentialcraft5.block.MagicalJukeboxBlock;
import com.flameforge.essentialcraft5.block.MagicalRepairerBlock;
import com.flameforge.essentialcraft5.block.PotionSpreaderBlock;
import com.flameforge.essentialcraft5.blockentity.MagicalEnchanterBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalJukeboxBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalRepairerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.PotionSpreaderBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.MagicalEnchanterMenu;
import com.flameforge.essentialcraft5.menu.MagicalJukeboxMenu;
import com.flameforge.essentialcraft5.menu.MagicalRepairerMenu;
import com.flameforge.essentialcraft5.menu.PotionSpreaderMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 魔力器械模块（第二批）：魔力修复器、魔力附魔台、药水扩散器、魔力唱片机（方块、方块实体、菜单同名）。
 * <p>
 * 新旧注册名：magicalRepairer → magical_repairer；magicalEnchanter → magical_enchanter；
 * potionSpreader → potion_spreader；magicalJukebox → magical_jukebox。
 * 四个方块原作都是 registerBlockSimple(…, 1, 1, 0)（先抗爆后硬度 → 硬度 1、抗爆 1.0，不透光度 0 → 透天空光），
 * Material.ROCK；修复器、附魔台、扩散器 MapColor.PURPLE，唱片机 MapColor.WOOD；都是 OBJ 模型。
 * <p>
 * 工作台配方在 data/essentialcraft5/recipes/devices/，界面与手册文字在 tools/lang/keys/ecdevices.json，
 * 手册章节 assets/essentialcraft5/guide/devices.json。客户端部分（界面、三个 BER）见 client/DevicesClient。
 */
public final class ECDevices {

    // ---------------- 魔力修复器 ----------------

    public static final RegistryObject<MagicalRepairerBlock> MAGICAL_REPAIRER = ECBlocks.register("magical_repairer",
            () -> new MagicalRepairerBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 1F, true))
                    .noOcclusion()),
            BlockSpec.of(BlockSpec.obj("magicalrepairer", "block/model/magicalrepairer")));

    public static final RegistryObject<BlockEntityType<MagicalRepairerBlockEntity>> MAGICAL_REPAIRER_BE =
            ECBlockEntities.type("magical_repairer", MagicalRepairerBlockEntity::new, MAGICAL_REPAIRER);

    public static final RegistryObject<MenuType<MagicalRepairerMenu>> MAGICAL_REPAIRER_MENU =
            ECMenus.register("magical_repairer", MagicalRepairerMenu::new);

    // ---------------- 魔力附魔台 ----------------

    public static final RegistryObject<MagicalEnchanterBlock> MAGICAL_ENCHANTER = ECBlocks.register("magical_enchanter",
            () -> new MagicalEnchanterBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 1F, true))
                    .noOcclusion()),
            BlockSpec.of(BlockSpec.obj("magicalenchanter", "block/model/magicalenchanter")));

    public static final RegistryObject<BlockEntityType<MagicalEnchanterBlockEntity>> MAGICAL_ENCHANTER_BE =
            ECBlockEntities.type("magical_enchanter", MagicalEnchanterBlockEntity::new, MAGICAL_ENCHANTER);

    public static final RegistryObject<MenuType<MagicalEnchanterMenu>> MAGICAL_ENCHANTER_MENU =
            ECMenus.register("magical_enchanter", MagicalEnchanterMenu::new);

    // ---------------- 药水扩散器 ----------------

    public static final RegistryObject<PotionSpreaderBlock> POTION_SPREADER = ECBlocks.register("potion_spreader",
            () -> new PotionSpreaderBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 1F, true))
                    .noOcclusion()),
            BlockSpec.of(BlockSpec.obj("potionspreader", "block/model/potionspreader")));

    public static final RegistryObject<BlockEntityType<PotionSpreaderBlockEntity>> POTION_SPREADER_BE =
            ECBlockEntities.type("potion_spreader", PotionSpreaderBlockEntity::new, POTION_SPREADER);

    public static final RegistryObject<MenuType<PotionSpreaderMenu>> POTION_SPREADER_MENU =
            ECMenus.register("potion_spreader", PotionSpreaderMenu::new);

    // ---------------- 魔力唱片机 ----------------

    public static final RegistryObject<MagicalJukeboxBlock> MAGICAL_JUKEBOX = ECBlocks.register("magical_jukebox",
            () -> new MagicalJukeboxBlock(ECBlocks.machine(MapColor.WOOD, 1F, ECBlocks.legacyResistance(1F, 1F, true))
                    .noOcclusion()),
            BlockSpec.of(BlockSpec.obj("magicaljukebox", "block/model/magicaljukebox")));

    public static final RegistryObject<BlockEntityType<MagicalJukeboxBlockEntity>> MAGICAL_JUKEBOX_BE =
            ECBlockEntities.type("magical_jukebox", MagicalJukeboxBlockEntity::new, MAGICAL_JUKEBOX);

    public static final RegistryObject<MenuType<MagicalJukeboxMenu>> MAGICAL_JUKEBOX_MENU =
            ECMenus.register("magical_jukebox", MagicalJukeboxMenu::new);

    private ECDevices() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 */
    public static void init() {
        ECConfig.machine(MagicalRepairerBlockEntity.Config::define);
        ECConfig.machine(MagicalEnchanterBlockEntity.Config::define);
        ECConfig.machine(PotionSpreaderBlockEntity.Config::define);
        ECConfig.machine(MagicalJukeboxBlockEntity.Config::define);
    }
}
