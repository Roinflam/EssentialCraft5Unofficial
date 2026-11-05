package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.MatrixCrucibleBlock;
import com.flameforge.essentialcraft5.blockentity.MatrixCrucibleBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.entity.MatrixEchoEntity;
import com.flameforge.essentialcraft5.menu.MatrixCrucibleMenu;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 矩阵淬炼台模块（第十三批 13a，batch13.md §3.1.2）：淬炼台方块、方块实体、菜单，矩阵回响实体。
 * <p>
 * 注册名：方块 / 方块实体 / 菜单 {@code matrix_crucible}，实体 {@code matrix_echo}（命名表里是 EC5 新增的条目）。
 * 贴图 {@code block/matrix_crucible_top}、{@code block/matrix_crucible_side}（底面复用魔力护板 {@code block/magicplatingblock}），
 * 模型 {@code models/block/matrix_crucible.json} 手写（12/16 高的台座），台座上方转的灵魂石由 {@code client/render/MatrixCrucibleRenderer} 画；
 * 阶徽 {@code gui/matrix_rank/rank_<n>}，相性图标 {@code gui/matrix_affinity/<属性>}。
 * 矩阵阶本身的框架（数据、门、配置 {@code general.matrix_rank}，含试炼那一节 {@code trial}）在 {@code matrix/}，由 {@code matrix/MatrixSystems} 登记；
 * 试炼的状态机在 {@code matrix/MatrixTrials}，开始前的核对在 {@code matrix/MatrixTrialChecks}。
 * <p>
 * 文字在 {@code tools/lang/keys/ecmatrix.json}，配方 {@code data/essentialcraft5/recipes/matrix/}；客户端部分（界面、方块渲染、回响渲染）见
 * {@code client/MatrixCrucibleClient}。
 */
public final class ECMatrix {

    /** 硬度、抗爆照矩阵换能器（1.12 规则换算）；台座 12/16 高，不挡光 */
    public static final RegistryObject<MatrixCrucibleBlock> MATRIX_CRUCIBLE = ECBlocks.register("matrix_crucible",
            () -> new MatrixCrucibleBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 1F, true)).noOcclusion()),
            BlockSpec.of(BlockSpec.existing("matrix_crucible")));

    public static final RegistryObject<BlockEntityType<MatrixCrucibleBlockEntity>> MATRIX_CRUCIBLE_BE =
            ECBlockEntities.type("matrix_crucible", MatrixCrucibleBlockEntity::new, MATRIX_CRUCIBLE);

    public static final RegistryObject<MenuType<MatrixCrucibleMenu>> MATRIX_CRUCIBLE_MENU =
            ECMenus.register("matrix_crucible", MatrixCrucibleMenu::new);

    /**
     * 矩阵回响：玩家模型大小（0.6 × 1.8），不存盘、不能 /summon（没有试炼就不知道该打谁）；同步照玩家克隆体。
     * 不注册成 fireImmune：那样连试炼者自己打出的火焰类伤害（反弹的火球、带 is_fire 的法术）也一起免了；火、岩浆、着火这类没有攻击者的伤害
     * 由回响自己的 isInvulnerableTo 挡掉，身上也不会着火（见 MatrixEchoEntity）。
     * 放在 MONSTER 类别里，原版的「敌对生物」判断才认它（和平难度下会被清掉，那时试炼改走引导）；它不进任何刷怪表，也没有刷怪蛋。
     */
    public static final RegistryObject<EntityType<MatrixEchoEntity>> MATRIX_ECHO = ECEntities.register("matrix_echo",
            EntityType.Builder.<MatrixEchoEntity>of(MatrixEchoEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F).noSave().noSummon().clientTrackingRange(8).updateInterval(2));

    private ECMatrix() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记机器配置和回响的属性 */
    public static void init() {
        ECConfig.machine(MatrixCrucibleBlockEntity.Config::define);
        ECEntities.attributes(MATRIX_ECHO, MatrixEchoEntity::createAttributes);
    }
}
