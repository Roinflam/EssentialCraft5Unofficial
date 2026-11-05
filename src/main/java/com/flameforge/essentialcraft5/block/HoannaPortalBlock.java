package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.client.hoanna.HoannaPortalOverlay;
import com.flameforge.essentialcraft5.hoanna.portal.HoannaPortalShape;
import com.flameforge.essentialcraft5.hoanna.portal.HoannaPortalTracker;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.joml.Vector3f;

/**
 * 海纳尔传送门（原作 BlockHoannaPortal，注册名 portal → {@code hoanna_portal}；第六批 6a M2，batch6a.md §5.2）：拿风之石板右键点燃的下界门换出来，
 * 玩家拿不到物品（没有方块物品、硬度 −1、没有掉落）。形状、朝向、「框坏了就塌」都照原版 {@code NetherPortalBlock}，判定改用 {@link HoannaPortalShape}
 * （两种门格混着也算完整，见那边的类注释）。
 * <p>
 * 和原作 / 原版下界门不同的地方：
 * <ul>
 *   <li><b>只传真玩家</b>：{@link #entityInside} 在服务端只把 {@code ECProtection.isRealPlayer} 的玩家交给 {@link HoannaPortalTracker} 计时，
 *       掉落物、生物、矿车、假玩家一律不管（原作任何实体都传，能跨维度搬东西、拖怪）。<b>不调 {@code entity.handleInsidePortal}</b>（那是下界门的计时，
 *       会把人送去下界），也不在这里换维度（碰撞遍历中途移除实体会出事），传送放在 {@code PlayerTickEvent} 的 END。</li>
 *   <li>骑着东西不能过门（D9 A），由 {@link HoannaPortalTracker#inside} 判断、提示。方块在 {@code #minecraft:portals} 里
 *       （tools/tags/echoannaportal.json）：在门里从坐骑上下来时原地下来、不被挪出门外，同原版下界门（{@code LivingEntity.dismountVehicle}）。</li>
 *   <li>不开随机刻：原作 {@code updateTick} 是空的，不刷僵尸猪灵。</li>
 *   <li>{@code forceSolidOn}：流水冲不掉它（原版下界门由 {@code FlowingFluid.canHoldFluid} 写死挡住水；我们的方块不在那张名单里，
 *       不强制实心的话流水、倒桶都能把门格冲成水）。只改 {@code isSolid / blocksMotion}，没有碰撞箱、照样能走进去（同风之符文、原版告示牌的写法）。</li>
 * </ul>
 * <b>{@link #STABLE}</b>：换门 / 建门的方块改动整段走 {@code ECProtection.captureGuarded}，被领地拒绝时按快照还原。捕获期间放下的是 {@code stable=false}
 * （没登记兴趣点的状态），放行以后再逐格改成 {@code stable=true}（兴趣点只认这个状态）。这样被拒还原时不会去删一个从没登记过的兴趣点——
 * 否则每格都会在日志里报一行 ERROR「POI data mismatch: never registered」（Forge 自己的放置捕获被拒时，床、钟也会这样）。
 * 两种状态都照常能过门；默认状态是 {@code stable=true}（{@code /setblock} 放的直接能被找到）。
 */
public class HoannaPortalBlock extends Block {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    /** 兴趣点只登记 stable=true 的状态（见类注释） */
    public static final BooleanProperty STABLE = BooleanProperty.create("stable");

    protected static final VoxelShape X_AXIS_AABB = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    protected static final VoxelShape Z_AXIS_AABB = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 16.0D, 16.0D);

    /** 稀疏的绿色微粒：门贴图的绿 #00C02A */
    private static final DustParticleOptions GREEN_DUST = new DustParticleOptions(new Vector3f(0F, 0xC0 / 255F, 0x2A / 255F), 1.0F);

    public HoannaPortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(STABLE, true));
    }

    /** 给定朝向、stable 的门格状态 */
    public BlockState portalState(Direction.Axis axis, boolean stable) {
        return defaultBlockState().setValue(AXIS, axis.isHorizontal() ? axis : Direction.Axis.X).setValue(STABLE, stable);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_AXIS_AABB : X_AXIS_AABB;
    }

    /** 同原版 NetherPortalBlock L64~68：同一平面上的邻格变了、又不是门格时，框不完整就变成空气（不掉落） */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction.Axis updateAxis = direction.getAxis();
        Direction.Axis portalAxis = state.getValue(AXIS);
        boolean perpendicular = portalAxis != updateAxis && updateAxis.isHorizontal();
        if (!perpendicular && !HoannaPortalShape.isPortal(neighbor) && !new HoannaPortalShape(level, pos, portalAxis).isComplete()) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) {
            // 本地玩家的进门暗角：客户端自己计时，不发包（HoannaPortalOverlay 自己判断是不是本地玩家）
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HoannaPortalOverlay.inside(entity));
            return;
        }
        if (entity instanceof ServerPlayer player && ECProtection.isRealPlayer(player)) {
            HoannaPortalTracker.inside(player, pos);
        }
    }

    /** 环境音同原版（1/100、音量 0.5），外加 1/4 概率一粒绿色微粒（原版下界门每次 4 粒紫色传送粒子） */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(100) == 0) {
            level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS,
                    0.5F, random.nextFloat() * 0.4F + 0.8F, false);
        }
        if (random.nextInt(4) == 0) {
            boolean alongX = state.getValue(AXIS) == Direction.Axis.X;
            double along = random.nextDouble();
            double across = 0.5D + (random.nextDouble() - 0.5D) * 0.3D;
            double x = pos.getX() + (alongX ? along : across);
            double z = pos.getZ() + (alongX ? across : along);
            level.addParticle(GREEN_DUST, x, pos.getY() + random.nextDouble(), z, 0.0D, 0.02D, 0.0D);
        }
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        return state;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, STABLE);
    }
}
