package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.RadiatingChamberBlock;
import com.flameforge.essentialcraft5.blockentity.RadiatingChamberBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.RadiatingChamberMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 辐射仓模块：radiatingChamber → radiating_chamber（方块、方块实体、菜单同名）。
 * <p>
 * 配方类型 essentialcraft5:radiating_chamber 在 ECRecipes（框架），配方是
 * data/essentialcraft5/recipes/radiating_chamber/ 下的 JSON（原作 RecipesCore.registerRadiatingChamber 的 14 条 + 机器本身的工作台配方）。
 * 客户端部分（界面、BER）见 client/RadiatingChamberClient。
 */
public final class ECRadiatingChamber {

    /**
     * 原作 radiatingChamber：registerBlockSimple(…, 1, 1, 0)（先抗爆后硬度，不透光度 0 → 透天空光），
     * Material.ROCK + MapColor.GOLD，OBJ 模型 → 硬度 1、抗爆 1.0
     */
    public static final RegistryObject<RadiatingChamberBlock> RADIATING_CHAMBER = ECBlocks.register("radiating_chamber",
            () -> new RadiatingChamberBlock(ECBlocks.machine(MapColor.GOLD, 1F, ECBlocks.legacyResistance(1F, 1F, true)).noOcclusion()),
            BlockSpec.of(BlockSpec.obj("radiatingchamber", "block/model/radiatingchamber")));

    public static final RegistryObject<BlockEntityType<RadiatingChamberBlockEntity>> RADIATING_CHAMBER_BE =
            ECBlockEntities.type("radiating_chamber", RadiatingChamberBlockEntity::new, RADIATING_CHAMBER);

    public static final RegistryObject<MenuType<RadiatingChamberMenu>> RADIATING_CHAMBER_MENU =
            ECMenus.register("radiating_chamber", RadiatingChamberMenu::new);

    private ECRadiatingChamber() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 */
    public static void init() {
        ECConfig.machine(RadiatingChamberBlockEntity.Config::define);
    }
}
