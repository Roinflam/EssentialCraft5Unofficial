package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.client.EssenceClient;
import com.flameforge.essentialcraft5.item.MagicalLanternItem;
import com.flameforge.essentialcraft5.registry.ECEssence;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Locale;

/**
 * 魔力光源（原作 BlockMagicLight，注册名 torch → magic_light）：看不见、没有碰撞、亮度 15、什么都不掉的光源方块，只由魔力灯笼放出来。
 * <ul>
 *   <li>{@link LightType#PLACED}（原作 meta 0）：灯笼右键方块放的永久光源。原作选取框 0.2~0.8，挖掉它要对准这个小框（硬度 0，一下就掉），这里照做；
 *       原作每次随机显示刻冒 5 个 MRU 粒子，只有能看见 MRU 的玩家看得到（原作 ParticleMRU 只在 canPlayerSeeMRU 时才画）。
 *       框架还没有 MRU 粒子类型，先用品红的原版尘埃粒子顶上，同样只给 {@code MRUVision.canSee} 的玩家看。</li>
 *   <li>{@link LightType#SPAWNED}（原作 meta 1）：手持灯笼时脚下的临时光源，{@value #LIFETIME} tick 后自己消失。
 *       脚下已经够亮或者正在鞘翅滑翔时灯笼不放（见 {@link MagicalLanternItem#BRIGHT_ENOUGH}）。</li>
 * </ul>
 * 移植改动：
 * <ul>
 *   <li>临时光源<b>没有选取框</b>、可以被直接替换（原作两种都有 0.2~0.8 的选取框：拿着灯笼低头时，脚下那格的光源会挡住视线，
 *       点不到下面的方块）。所以低头拿灯笼右键地面时，射线点中的是地面、目标格正是脚下的临时光源：
 *       灯笼放永久光源用 {@link #canPlacePermanentAt}，临时光源也算可放，直接顶替它（原作点中的是临时光源本身，永久光源放在头部那格）。</li>
 *   <li>临时光源到期时，如果还有拿着灯笼的玩家站在这一格，就续 {@value #LIFETIME} tick，不删了再放。原作站着不动时每 21 tick
 *       删一次再放一次，每次都要重算光照、还会闪一下。</li>
 *   <li>临时光源放置和删除都不通知邻居（flag 2|16）：原作 flag 2 在 1.12 不触发侦测器，1.20 要再加 UPDATE_KNOWN_SHAPE 才不触发。
 *       否则拿着灯笼从侦测器前走过每一步都会触发它。</li>
 *   <li>透天空光（原作不透光度 1），隐形的光源不该投影子。</li>
 * </ul>
 * 没有物品形态（原作的方块物品只是个占位图标，拿不到），战利品表是空表。
 */
@SuppressWarnings("deprecation")
public class MagicLightBlock extends Block {

    /** 临时光源的寿命（原作 scheduleUpdate 20） */
    public static final int LIFETIME = 20;
    /** 临时光源放置、删除用的标记：只发给客户端，不通知邻居、不更新邻居形状 */
    public static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    public static final EnumProperty<LightType> TYPE = EnumProperty.create("type", LightType.class);

    /** 原作 BLOCK_AABB（0.2~0.8） */
    private static final VoxelShape PLACED_SHAPE = Block.box(3.2D, 3.2D, 3.2D, 12.8D, 12.8D, 12.8D);

    public MagicLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TYPE, LightType.PLACED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TYPE);
    }

    public static boolean isTemporary(BlockState state) {
        return state.getValue(TYPE) == LightType.SPAWNED;
    }

    /** 能放光源的位置：空气（含洞穴空气），不是世界外的虚空空气，在世界边界和高度范围内。临时光源只放在这种地方 */
    public static boolean canPlaceAt(Level level, BlockPos pos) {
        if (!level.isInWorldBounds(pos)) {
            return false;
        }
        return isPlaceableAir(level.getBlockState(pos));
    }

    /**
     * 能放永久光源的位置：{@link #canPlaceAt} 之外，临时光源也可以顶替。拿着灯笼时脚下那格一直是临时光源，
     * 低头右键脚下的地面（或者贴墙右键墙面脚高处）时目标格就是它；只认空气的话，玩家最常做的这个动作什么都不发生。
     * 顶替以后临时光源残留的定时刻落到永久光源上，{@code expire} 看到不是临时光源直接返回；随机刻只对临时光源开着。
     */
    public static boolean canPlacePermanentAt(Level level, BlockPos pos) {
        if (!level.isInWorldBounds(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return isPlaceableAir(state) || (state.getBlock() instanceof MagicLightBlock && isTemporary(state));
    }

    private static boolean isPlaceableAir(BlockState state) {
        return state.isAir() && !state.is(Blocks.VOID_AIR);
    }

    /**
     * 在 pos 放一个临时光源（手持灯笼时调用）。只替换空气，不发放置事件（1 秒就没，和原作一样）。
     *
     * @return 放了没有
     */
    public static boolean placeTemporary(Level level, BlockPos pos) {
        if (level.isClientSide || !canPlaceAt(level, pos)) {
            return false;
        }
        MagicLightBlock block = ECEssence.MAGIC_LIGHT.get();
        if (!level.setBlock(pos, block.defaultBlockState().setValue(TYPE, LightType.SPAWNED), QUIET)) {
            return false;
        }
        level.scheduleTick(pos, block, LIFETIME);
        return true;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        expire(state, level, pos);
    }

    /** 兜底：定时刻丢了（比如被别的模组清掉）也能靠随机刻消失。原作 setTickRandomly(true) 同理 */
    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return isTemporary(state);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        expire(state, level, pos);
    }

    private void expire(BlockState state, ServerLevel level, BlockPos pos) {
        if (!isTemporary(state)) {
            return;
        }
        if (heldHere(level, pos)) {
            level.scheduleTick(pos, this, LIFETIME);
            return;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
    }

    /** 还有拿着灯笼（主手或副手）的玩家站在这一格（脚所在的格子）吗 */
    private static boolean heldHere(Level level, BlockPos pos) {
        return !level.getEntitiesOfClass(Player.class, new AABB(pos),
                player -> !player.isSpectator() && player.blockPosition().equals(pos) && MagicalLanternItem.isHolding(player)).isEmpty();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!isTemporary(state)) {
            EssenceClient.magicLightParticles(level, pos, random);
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return isTemporary(state) ? Shapes.empty() : PLACED_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** 永久光源照原作不能被直接替换（要先挖掉）；临时光源可以，免得挡住在脚下放方块 */
    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return isTemporary(state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1F;
    }

    /** 原作 BlockMagicLight.LightType */
    public enum LightType implements StringRepresentable {
        /** 永久（灯笼右键放的） */
        PLACED,
        /** 临时（手持灯笼时脚下的） */
        SPAWNED;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
