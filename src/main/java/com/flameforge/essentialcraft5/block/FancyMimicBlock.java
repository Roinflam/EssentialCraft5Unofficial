package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.FancyMimicBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 花纹拟态（原作 BlockFancyMimic，一个注册名 × meta 0~15 → 这里每种花纹一个注册名 {@code fancy_mimic_<花纹>}）。
 * 就是拟态方块（{@link MimicBlock}）加两样：
 * <ul>
 *   <li><b>有碰撞箱</b>（原作 L63~66 {@code getCollisionBoundingBox} 返回选框）：碰撞 = 选框 = 被模仿方块的形状，没伪装、形状为空时整格。
 *       直接返回 {@link MimicBlock#getShape}——那里已经是「取 {@link MimicBlock#camoAt} 的形状（再挡一次伪装成拟态的存档）、出错整格」并带防重入。</li>
 *   <li>伪装以后花纹画在被模仿方块上面（原作 ModelFancyMimic 在 CUTOUT_MIPPED 层追加花纹）：框架 F5-1，注册类 {@code init()} 里
 *       {@code Camo.registerCamoBlock(方块, 花纹模型)}；没伪装时画自己的模型（拟态方块贴图 + 花纹，datagen 生成）。</li>
 * </ul>
 * 右键伪装 / 潜行空手还原、冒险 / 旁观改不了、不能伪装成拟态方块和花纹拟态（{@link MimicBlock#acceptsCamo} 按 {@code instanceof MimicBlock} 挡，
 * 另外注册时进 {@code mimic_blacklist}）、恒挡光 15、六面结实、寻路当墙、里面和上面不刷怪、液体流不进来，都继承拟态方块。
 * <p>
 * <b>不要加 {@code dynamicShape()}</b>：加了以后方块状态不缓存碰撞形状，{@code getShadeBrightness} 这类区块渲染线程上的调用会经由碰撞形状去读方块实体。
 * 现在状态缓存里的碰撞形状按「没有方块实体」算（整格），{@code isCollisionShapeFullBlock} 恒真，是偏保守的方向；
 * 实体移动（{@code BlockCollisions}）走带上下文的三参数版本，不用缓存，拿到的是真实形状。服务端在别的线程上读不到方块实体时按没伪装算（整格）。
 */
public class FancyMimicBlock extends MimicBlock {

    private final FancyPattern pattern;

    public FancyMimicBlock(Properties properties, FancyPattern pattern) {
        super(properties);
        this.pattern = pattern;
    }

    public FancyPattern pattern() {
        return pattern;
    }

    /** 碰撞 = 选框（被模仿方块的形状，没伪装整格）；拟态方块本身恒为空 */
    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FancyMimicBlockEntity(pos, state);
    }
}
