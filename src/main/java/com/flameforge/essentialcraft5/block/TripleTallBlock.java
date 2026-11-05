package com.flameforge.essentialcraft5.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 三格高的方块（下 / 中 / 上三段是同一个方块的三个状态 {@link #PART}），照原版门、高花的写法：
 * <ul>
 *   <li>上面两格都放得下才让放；玩家放下时在 setPlacedBy 里补上两段，/setblock、GameTest、结构这类不经过玩家的在 onPlace 里补
 *       （只往放得下的格子里补，<b>绝不覆盖</b>已有的方块）。</li>
 *   <li>任何一段没了，相邻的段在 updateShape 里跟着变成空气，一段接一段传下去，所以拆哪一段都只清掉这一根。</li>
 *   <li>只有底座有方块实体、有掉落（战利品表按 part=bottom 写）：拆哪一段都只掉一份。</li>
 *   <li>拆中、上段时如果是创造模式、或者工具不对（底座要求正确工具才掉），先把底座不掉落地清掉，免得它被连带拆掉时掉出东西（同原版门）。</li>
 *   <li>活塞推不动（三段会被推散），子类在方块属性里写 {@code pushReaction(PushReaction.BLOCK)}。</li>
 * </ul>
 * 原作 BlockMithrilineCrystal 的写法（onBlockAdded 把上两格直接设成中段、breakBlock 按层号把上下几格直接设成空气）有两个问题：
 * 放下时会覆盖上方原有的方块；上两格都被设成「中段」，敲最上面那格时它按中段处理，把它<b>上方</b>那格（可能是基岩、别人的方块）也删掉，
 * 而且底座还留着能再挖一份。这里都按上面的规则重写。
 * <p>
 * 用法：继承它，写 newBlockEntity（通常只给底座，{@code isBottom(state) ? new … : null}）、形状、战利品表（只在 part=bottom 掉自己），
 * 方块属性里写 {@code pushReaction(PushReaction.BLOCK)}。
 * 水晶柱不用再继承：五种档位（米斯莱 / 淡青 / 虚空 / 恶魔 / 暗渎）都直接用 {@link MithrilineCrystalPillarBlock}，构造时传档位和方块实体类型；
 * 反应堆支架这类别的三格高方块继承这个类。
 */
public abstract class TripleTallBlock extends BaseEntityBlock {

    public enum Part implements StringRepresentable {
        BOTTOM,
        MIDDLE,
        TOP;

        private static final Part[] VALUES = values();

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** 离底座几格 */
        public int height() {
            return ordinal();
        }

        @Nullable
        public Part above() {
            return this == TOP ? null : VALUES[ordinal() + 1];
        }

        @Nullable
        public Part below() {
            return this == BOTTOM ? null : VALUES[ordinal() - 1];
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    protected TripleTallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, Part.BOTTOM));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    public static boolean isBottom(BlockState state) {
        return state.getValue(PART) == Part.BOTTOM;
    }

    /** 这一段所属的底座坐标 */
    public static BlockPos bottomOf(BlockPos pos, BlockState state) {
        return pos.below(state.getValue(PART).height());
    }

    /** BaseEntityBlock 默认不渲染方块模型（INVISIBLE），改回 MODEL：破坏粒子、子类的方块模型要靠它 */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // ---- 放置 ----

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() < level.getMaxBuildHeight() - 2
                && level.getBlockState(pos.above()).canBeReplaced(context)
                && level.getBlockState(pos.above(2)).canBeReplaced(context)) {
            return defaultBlockState();
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && isBottom(state)) {
            fillUpperParts(level, pos, state);
        }
    }

    /** 不经过玩家放下的（/setblock、GameTest、结构）在这里补上两段；玩家放的此时也会先走到这里 */
    @SuppressWarnings("deprecation")
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && isBottom(state) && !oldState.is(this)) {
            fillUpperParts(level, pos, state);
        }
    }

    /** 两段都放得下（空的、可替换的，或者已经是对应的段）才补，放不下就一格都不动 */
    private void fillUpperParts(Level level, BlockPos pos, BlockState bottom) {
        BlockPos middle = pos.above();
        BlockPos top = pos.above(2);
        if (!canHold(level, middle, Part.MIDDLE) || !canHold(level, top, Part.TOP)) {
            return;
        }
        setPart(level, middle, bottom, Part.MIDDLE);
        setPart(level, top, bottom, Part.TOP);
    }

    private boolean canHold(Level level, BlockPos pos, Part part) {
        if (level.isOutsideBuildHeight(pos)) {
            return false;
        }
        BlockState existing = level.getBlockState(pos);
        return existing.is(this) && existing.getValue(PART) == part || existing.canBeReplaced();
    }

    private void setPart(Level level, BlockPos pos, BlockState bottom, Part part) {
        BlockState wanted = bottom.setValue(PART, part);
        if (level.getBlockState(pos) != wanted) {
            level.setBlock(pos, wanted, Block.UPDATE_ALL);
        }
    }

    // ---- 拆除 ----

    /** 朝向相邻段的那一面：相邻的段不在了（或不是该有的那一段），自己也没了 */
    @SuppressWarnings("deprecation")
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        Part part = state.getValue(PART);
        Part expected = direction == Direction.UP ? part.above() : direction == Direction.DOWN ? part.below() : null;
        if (expected != null) {
            return neighbor.is(this) && neighbor.getValue(PART) == expected ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !isBottom(state)) {
            BlockPos bottom = bottomOf(pos, state);
            BlockState bottomState = level.getBlockState(bottom);
            // 底座会被 updateShape 连带拆掉并掉落；创造模式、或者工具不对时不该掉，先悄悄清掉（原版门、高花同样处理）
            if (bottomState.is(this) && isBottom(bottomState)
                    && (player.isCreative() || !player.hasCorrectToolForDrops(bottomState))) {
                level.setBlock(bottom, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, bottom, Block.getId(bottomState));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }
}
