package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.MatrixAbsorberBlock;
import com.flameforge.essentialcraft5.blockentity.MatrixAbsorberBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.SoulStoneItem;
import com.flameforge.essentialcraft5.menu.MatrixAbsorberMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 模块 ECBootstrap：MRU 的起点（进度链见 tools/plan/batch1.md §1.1）。
 * <ul>
 *   <li>地面物品仪式（ritual.GroundRituals）：四种元素凝聚块、钻石给 MRU 聚集体 +5000、绿宝石 → 灵魂石</li>
 *   <li>UBMRU：杀怪获得、杀玩家夺取、死亡清零（player.UBMRUEvents）</li>
 *   <li>灵魂石、矩阵换能器（把 UBMRU 按 10:1 换成 MRU），以及第一批的进度（data/essentialcraft5/advancements/main）</li>
 * </ul>
 * 新旧注册名：soulStone:0 → soul_stone；matrixAbsorber → matrix_absorber（英文显示名 Matrix Destructor）。
 */
public final class ECBootstrap {

    /** 原作 soulStone:0（maxStackSize 1）；meta 1 血魔法灌注版不移植 */
    public static final RegistryObject<SoulStoneItem> SOUL_STONE = ECItems.register("soul_stone",
            () -> new SoulStoneItem(new Item.Properties().stacksTo(1)),
            ItemSpec.generated("item/matrix/soulstone"));

    /** 原作 matrixAbsorber：registerBlockSimple(…, 1, 1, 0)（先抗爆后硬度，不透光度 0），MapColor.PURPLE，OBJ 模型 → 抗爆 1.0 */
    public static final RegistryObject<MatrixAbsorberBlock> MATRIX_ABSORBER = ECBlocks.register("matrix_absorber",
            () -> new MatrixAbsorberBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 1F, true))
                    .noOcclusion()),
            BlockSpec.of(BlockSpec.obj("matrixabsorber", "block/model/matrixabsorber")));
    public static final RegistryObject<BlockEntityType<MatrixAbsorberBlockEntity>> MATRIX_ABSORBER_BE =
            ECBlockEntities.type("matrix_absorber", MatrixAbsorberBlockEntity::new, MATRIX_ABSORBER);
    public static final RegistryObject<MenuType<MatrixAbsorberMenu>> MATRIX_ABSORBER_MENU =
            ECMenus.register("matrix_absorber", MatrixAbsorberMenu::new);

    private ECBootstrap() {
    }

    public static void init() {
        ECConfig.machine(MatrixAbsorberBlockEntity.Config::define);
    }
}
