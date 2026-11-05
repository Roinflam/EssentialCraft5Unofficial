package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.MatrixTunerBlock;
import com.flameforge.essentialcraft5.blockentity.MatrixTunerBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.MatrixTunerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 矩阵调律器模块（第十二批，batch12.md §4.6；policy §4.15）：EC5 新机器，原作没有。
 * 玩家站到它上面，它花 ESPE 和 MRU 一点点抹掉这个人的矩阵损伤；只减损伤，从不治腐化效果（治效果仍然只有灵魂五芒星）。
 * 和附魔升华台成对：升华台往上加损伤，调律器让损伤可以花钱慢慢消掉。
 * <p>
 * 注册名：{@code matrix_tuner}（方块、方块实体、菜单同名；命名表里是 EC5 新增的一条，old_id 为 null）。
 * 方块属性照附魔升华台（硬度 1、抗爆按 1.12 规则换算）；模型 {@code models/block/matrix_tuner.json} 手写，
 * 13/16 高，顶 / 侧 / 底三张贴图（{@code block/matrix_tuner_*}）。
 * <p>
 * 文字在 {@code tools/lang/keys/ecmatrixtuner.json}，配方 {@code data/essentialcraft5/recipes/matrixtuner/}，
 * 手册支线 {@code guide/<语言>/corruption.json} 的 matrix_tuner 一条（进度 {@code guide/corruption/matrix_tuner}）。
 * 客户端部分（界面）见 {@code client/MatrixTunerClient}。
 */
public final class ECMatrixTuner {

    public static final RegistryObject<MatrixTunerBlock> MATRIX_TUNER = ECBlocks.register("matrix_tuner",
            () -> new MatrixTunerBlock(ECBlocks.machine(MapColor.COLOR_RED, 1F, ECBlocks.legacyResistance(1F, 1F, true))),
            BlockSpec.of(BlockSpec.existing("matrix_tuner")));

    public static final RegistryObject<BlockEntityType<MatrixTunerBlockEntity>> MATRIX_TUNER_BE =
            ECBlockEntities.type("matrix_tuner", MatrixTunerBlockEntity::new, MATRIX_TUNER);

    public static final RegistryObject<MenuType<MatrixTunerMenu>> MATRIX_TUNER_MENU =
            ECMenus.register("matrix_tuner", MatrixTunerMenu::new);

    private ECMatrixTuner() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 machines.matrix_tuner */
    public static void init() {
        ECConfig.machine(MatrixTunerBlockEntity.Config::define);
    }
}
