package com.flameforge.essentialcraft5.block.hoanna;

import com.flameforge.essentialcraft5.hoanna.HoannaTide;
import com.flameforge.essentialcraft5.loot.condition.HoannaLootConditions;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaMaterials;
import com.flameforge.essentialcraft5.registry.ECHoannaResources;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 潮晶芽 {@code tide_bud}（第十三批 13d，batch13.md §6.1 魔力腐化之地、§6.2 魔力潮汐；HS §2.1.2）：{@link #STAGE} 0~3，只往上长，
 * 脚下要有能撑住的顶面，没了就碎。形状随阶段变大，照原版紫水晶芽的小 / 中 / 大 / 簇四档（{@link #HEIGHTS}、{@link #INSETS}）。
 * <ul>
 *   <li>生长（{@link #canGrow}，五个条件缺一不可）：在海纳尔、所在群系是魔力腐化之地、大潮（{@link HoannaTide#isSpringTide}）、
 *       夜里（一天的 13 000~23 000 刻）、头顶露天（和战利品条件 {@code sky_visible} 同一口径）；满足时每次随机刻
 *       {@code tide_bud.growth_chance}（0.5）几率长一阶。只改这一格自己的状态，O(1)。</li>
 *   <li>潮汐关掉（{@link HoannaTide#active} 为假）时没有大潮：改成每个夜里都长，几率 × {@link #FLAT_TIDE_GROWTH_SCALE}（3/8）。
 *       大潮是八天里的三天（月相 7、0、1），平潮每夜都长、几率乘 3/8，平均产量和开着潮汐时一样；潮晶是宅男 2 阶的主线材料，
 *       关掉潮汐不能让它只剩世界生成时就长好的那些。</li>
 *   <li>海纳尔是数据包维度，昼夜读的是主世界的（DerivedLevelData）：主世界有人睡觉跳过夜晚时，海纳尔的夜也一起跳过，这一夜就少长（只在手册里写明，不另补）。</li>
 *   <li>收取（{@link #use}）：真玩家右键第 3 阶 → 潮晶 1~2（{@link ECItemEntities#giveOrDrop}），芽回 0 留在原地；没长满、假玩家都 PASS。</li>
 *   <li>挖掉（表在 {@code ECHoannaResources#tideBudLoot}）：精准采集掉芽本体（放下是 stage 0，可以移栽，但只在魔力腐化之地长）；
 *       否则第 3 阶掉潮晶 1，其余阶段什么都不掉。</li>
 * </ul>
 * 世界生成时 stage 随机 0~3。
 */
public class TideBudBlock extends Block {

    public static final int MAX_STAGE = 3;
    /** 潮汐关掉时每夜生长几率的倍数：大潮占八天里的三天 */
    public static final float FLAT_TIDE_GROWTH_SCALE = 3F / 8F;
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, MAX_STAGE);
    /** 各阶段碰撞箱的高度（像素），照原版小 / 中 / 大紫水晶芽和紫水晶簇，矮到走得过去 */
    public static final int[] HEIGHTS = {3, 4, 5, 7};
    /** 各阶段选中框的高度（像素），贴着贴图里株子的实际高度：顶上那颗潮晶要点得中，不然右键收取会点到后面的方块上 */
    public static final int[] OUTLINE_HEIGHTS = {5, 9, 12, 15};
    /** 各阶段四周往里缩的像素 */
    public static final int[] INSETS = {4, 3, 3, 3};

    private static final VoxelShape[] SHAPES = new VoxelShape[MAX_STAGE + 1];
    private static final VoxelShape[] COLLISION = new VoxelShape[MAX_STAGE + 1];

    static {
        for (int stage = 0; stage <= MAX_STAGE; stage++) {
            int inset = INSETS[stage];
            SHAPES[stage] = Block.box(inset, 0D, inset, 16 - inset, OUTLINE_HEIGHTS[stage], 16 - inset);
            COLLISION[stage] = Block.box(inset, 0D, inset, 16 - inset, HEIGHTS[stage], 16 - inset);
        }
    }

    public TideBudBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
    }

    /** 五个生长条件都满足（不含几率） */
    public static boolean canGrow(ServerLevel level, BlockPos pos) {
        return ECHoanna.isHoanna(level)
                && ECHoannaResources.inBiome(level, pos, ECHoanna.MAGIC_CORRUPTION)
                && (!HoannaTide.active() || HoannaTide.isSpringTide(level))
                && HoannaLootConditions.isNight(level.getDayTime())
                && HoannaLootConditions.skyVisibleAt(level, Vec3.atCenterOf(pos));
    }

    /** 这一次随机刻长一阶的几率：{@code growth_chance}，潮汐关掉时再 × {@link #FLAT_TIDE_GROWTH_SCALE} */
    public static double growthChance() {
        double chance = ECHoannaResources.Config.tideGrowthChance();
        return HoannaTide.active() ? chance : chance * FLAT_TIDE_GROWTH_SCALE;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        tryGrow(state, level, pos, random.nextFloat());
    }

    /**
     * 随机刻的判定（roll 是这次掷出的 [0, 1) 随机数，GameTest 直接传）：没长满、条件都满足、roll &lt; {@link #growthChance} 时长一阶。
     *
     * @return 长了没有
     */
    public static boolean tryGrow(BlockState state, ServerLevel level, BlockPos pos, float roll) {
        int stage = state.getValue(STAGE);
        if (stage >= MAX_STAGE || roll >= growthChance() || !canGrow(level, pos)) {
            return false;
        }
        level.setBlock(pos, state.setValue(STAGE, stage + 1), Block.UPDATE_CLIENTS);
        return true;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(STAGE) < MAX_STAGE) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!ECProtection.isRealPlayer(player)) {
            return InteractionResult.PASS;
        }
        level.setBlock(pos, state.setValue(STAGE, 0), Block.UPDATE_CLIENTS);
        ECItemEntities.giveOrDrop((ServerPlayer) player, new ItemStack(ECHoannaMaterials.TIDE_CRYSTAL.get(), 1 + level.random.nextInt(2)));
        level.playSound(null, pos, ECHoannaResources.TIDE_BUD_HARVEST.get(), SoundSource.BLOCKS, 0.8F, 1F);
        return InteractionResult.CONSUME;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(STAGE)];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION[state.getValue(STAGE)];
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }
}
