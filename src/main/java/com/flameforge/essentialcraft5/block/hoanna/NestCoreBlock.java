package com.flameforge.essentialcraft5.block.hoanna;

import com.flameforge.essentialcraft5.blockentity.NestCoreBlockEntity;
import com.flameforge.essentialcraft5.registry.ECHoannaFramework;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * 巢穴核心 {@code nest_core}（第十三批 13d-HF，batch13.md §6.4「巢穴核心」；HS §2.4.4）：中景观（冰封营地、混沌裂隙、辐射陨坑）、
 * 13e 的前哨营地和沉城卫兵里各摆一个，走近了放出一波怪，清掉给出过力的人各一份奖励，回充 3 个游戏日。规则都在 {@link NestCoreBlockEntity}。
 * <p>
 * 方块本身：硬度 30、抗爆 1 200、挖掉什么都不掉（领地插件管得到挖：走普通的破坏流程）；状态 {@link #PHASE}（{@code armed / active / dormant}）
 * 只由方块实体改（方块改自己的状态，不碰别的格子），三种状态三张贴图，亮度 4 / 7 / 0；等着的时候偶尔冒一颗暗红尘（客户端 {@code animateTick}，
 * 本来就是按距离抽样、越远越稀，policy §1.2），休眠时不冒。
 */
public class NestCoreBlock extends BaseEntityBlock {

    /** 巢穴的状态 */
    public enum Phase implements StringRepresentable {
        /** 等着有人走近 */
        ARMED,
        /** 这一波还没清完 */
        ACTIVE,
        /** 清完了，回充中 */
        DORMANT;

        private final String id = name().toLowerCase(Locale.ROOT);

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    public static final EnumProperty<Phase> PHASE = EnumProperty.create("phase", Phase.class);

    /** 暗红尘的颜色（只在等着的时候冒） */
    private static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(0.45F, 0.06F, 0.05F), 1.0F);

    public NestCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PHASE, Phase.ARMED));
    }

    /** 亮度：等着 4、战斗中 7、休眠 0 */
    public static int lightLevel(BlockState state) {
        return switch (state.getValue(PHASE)) {
            case ARMED -> 4;
            case ACTIVE -> 7;
            case DORMANT -> 0;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PHASE);
    }

    /** BaseEntityBlock 默认不画方块模型 */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NestCoreBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ECHoannaFramework.NEST_CORE_BE.get(), NestCoreBlockEntity::serverTick);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PHASE) != Phase.ARMED || random.nextInt(3) != 0) {
            return;
        }
        level.addParticle(EMBER, pos.getX() + 0.2D + random.nextDouble() * 0.6D, pos.getY() + 1.05D, pos.getZ() + 0.2D + random.nextDouble() * 0.6D,
                0D, 0.02D, 0D);
    }
}
