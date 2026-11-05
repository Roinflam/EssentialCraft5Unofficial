package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 可染色的方块（魔力强化石、魔力强化玻璃；以后的魔力护板、虚空石等也用它）。对应原作 BlockDyeable。
 * <p>
 * 颜色是方块状态 {@link #COLOR}（16 色），不拆注册名；物品只有一种，放下是白色，挖下来也总是白色（原作没有 damageDropped）。
 * 贴图是灰白底色，模型 cube_colored 的六个面都带 tintindex 0，客户端按颜色染（见 MaterialsClient），色值同原作 COLOR_VALUES，
 * 也就是原版的烟花色 {@link DyeColor#getFireworkColor()}。
 * 底图很暗的（虚空石、虚空玻璃）整张乘色会发黑、16 色分不清，改用两层模型：底图不染色，只有上面那层纹路带 tintindex 0
 * （见 ECMaterials.veinedDyeableModel）。
 * <p>
 * 染色：手持染料（DyeItem 或 forge:dyes/* 标签里的物品，方块物品除外）右键改成该颜色，<b>不消耗染料</b>（原作如此）；
 * 潜行右键把周围 5×5×5 内的同种方块一起染。原作的潜行分支其实走不到：1.12 潜行且手里有东西时不会调用 onBlockActivated。
 * 这里用 {@link SneakDyeing} 在潜行拿染料时放行方块交互，让它按原作的本意生效。
 * 启用时补了领地保护（原作走不到这个分支，没有对应的问题）：连带的格子逐格查出生点保护 / 世界边界，
 * 再以这名玩家发一次放置事件，被拦下的格子不染（见 {@link #recolorGuarded}）；点中的那格由交互事件管着，照旧直接染。
 * 范围染色只给真玩家（{@link ECProtection#isRealPlayer}，第三批审查 RB-5）：混合端不把假玩家（机械手、方块激活器）的放置事件转给领地插件，
 * 假玩家潜行拿染料也只染点中的那一格。
 * <p>
 * {@code glass}：原作按 Material.GLASS 区分的那几处——不是完整立方体、透光、半透明层（贴图只有全透和不透两种 alpha，用 cutout）。
 * 同种方块相邻的面不画（原作 shouldSideBeRendered），玻璃连成一片时看不到内部的边。
 */
public class DyeableBlock extends Block {

    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);

    /** 潜行染色的半径：原作 getAllInBoxMutable(pos ± 2) */
    private static final int AREA_RADIUS = 2;

    private final boolean glass;

    public DyeableBlock(Properties properties, boolean glass) {
        super(properties);
        this.glass = glass;
        registerDefaultState(stateDefinition.any().setValue(COLOR, DyeColor.WHITE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR);
    }

    /** 方块染色用的色值（客户端颜色处理器调） */
    public static int tintColor(BlockState state) {
        return state.getValue(COLOR).getFireworkColor();
    }

    /**
     * 物品对应的染料颜色，不是染料返回 null。
     * 不用 Forge 的 DyeColor.getColor：它按标签找时循环到 BLACK 之前就停了，标签里的黑色染料认不出来。
     */
    @Nullable
    public static DyeColor dyeColor(ItemStack stack) {
        // 原作排除 ItemBlock：染色的羊毛、陶瓦之类如果带染料矿辞，不该被当成染料
        if (stack.isEmpty() || stack.getItem() instanceof BlockItem) {
            return null;
        }
        if (stack.getItem() instanceof DyeItem dye) {
            return dye.getDyeColor();
        }
        for (DyeColor color : DyeColor.values()) {
            if (stack.is(color.getTag())) {
                return color;
            }
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        DyeColor color = dyeColor(player.getItemInHand(hand));
        if (color == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            // 假玩家的放置事件混合端不转给领地插件（policy §2.1），连带的格子没人管，只给真玩家范围染色
            if (player.isShiftKeyDown() && ECProtection.isRealPlayer(player)) {
                for (BlockPos cursor : BlockPos.betweenClosed(pos.offset(-AREA_RADIUS, -AREA_RADIUS, -AREA_RADIUS),
                        pos.offset(AREA_RADIUS, AREA_RADIUS, AREA_RADIUS))) {
                    BlockPos target = cursor.immutable();
                    // 逐格查交互权限（出生点保护、世界边界）：范围染色能伸到玩家点不到的格子
                    if (!level.mayInteract(player, target)) {
                        continue;
                    }
                    if (target.equals(pos)) {
                        recolor(level, target, color);
                    } else {
                        recolorGuarded(level, target, color, player, hit.getDirection());
                    }
                }
            } else {
                recolor(level, pos, color);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 把 pos 处的本方块改成 color；不是本方块或颜色已相同时什么也不做。原作只通知客户端（flag 2），不触发邻居更新 */
    private boolean recolor(Level level, BlockPos pos, DyeColor color) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this) || state.getValue(COLOR) == color) {
            return false;
        }
        level.setBlock(pos, state.setValue(COLOR, color), Block.UPDATE_CLIENTS);
        return true;
    }

    /**
     * 范围染色里被连带的一格（不是玩家点中的那格）：先换色，再以这名玩家发一次放置事件，被拦下就原样还原这一格。
     * 只对真玩家调用（调用处已查 {@link ECProtection#isRealPlayer}）：假玩家发的放置事件混合端不转，等于放行。
     * <p>
     * {@code mayInteract} 只看出生点保护和世界边界、不发任何事件；点中的那格有 RightClickBlock 管着（混合端转成领地插件看得到的交互事件），
     * 连带的格子没有，站在自己领地边上就能改掉边界另一侧别人的方块颜色。所以照 {@code MagicalLanternItem.placePermanent} 的写法
     * 补一次 {@link BlockEvent.EntityPlaceEvent}：混合端会转给领地插件，别人领地里的格子被拦下就跳过。换颜色在语义上接近「放置」，
     * 不用 BreakEvent 作探测，免得 CoreProtect 之类记下假的破坏日志。
     *
     * @param face 点中的面，放置事件按它找「贴着放的方块」
     */
    private void recolorGuarded(Level level, BlockPos pos, DyeColor color, Player player, Direction face) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this) || state.getValue(COLOR) == color) {
            return;
        }
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos, Block.UPDATE_CLIENTS);
        level.setBlock(pos, state.setValue(COLOR, color), Block.UPDATE_CLIENTS);
        if (ForgeEventFactory.onBlockPlace(player, snapshot, face)) {
            // 同 recolor 只通知客户端、不触发邻居更新
            snapshot.restore(true, false);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }

    // ---- 玻璃（同原版 AbstractGlassBlock）----

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return glass ? Shapes.empty() : super.getVisualShape(state, level, pos, context);
    }

    @SuppressWarnings("deprecation")
    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return glass ? 1F : super.getShadeBrightness(state, level, pos);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return glass || super.propagatesSkylightDown(state, level, pos);
    }

    /**
     * 潜行时原版不调用方块的 use（手里有东西就直接走物品的 useOn），这里在「潜行 + 染料 + 可染色方块」时放行，
     * 交给 {@link #use} 做范围染色。两端都会触发这个事件，两端判断一致。
     * <p>
     * 只把 DEFAULT 升成 ALLOW：别的模组（领地等）在更高优先级只设了 useBlock DENY、没取消事件时不改回来——
     * 点中的那一格不另发放置事件（由交互事件管着），改回 ALLOW 就绕过了它们（6a 审查 M7-5，VoidTests.voidStoneSneakDyeKeepsDeny）。
     */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class SneakDyeing {

        private SneakDyeing() {
        }

        @SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            if (event.getEntity().isShiftKeyDown() && dyeColor(event.getItemStack()) != null
                    && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof DyeableBlock
                    && event.getUseBlock() != Event.Result.DENY) {
                event.setUseBlock(Event.Result.ALLOW);
            }
        }
    }
}
