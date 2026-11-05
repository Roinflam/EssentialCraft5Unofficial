package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.EmberForgeBlockEntity;
import com.flameforge.essentialcraft5.registry.ECEmberForge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 余烬锻炉（第七批 M2）。对应原作 BlockEmberForge（Material.ROCK、整块 cube_top：顶面 ember_anvil_top、侧面强化石，不透光度 0）。
 * 没有界面、没有物品栏，逻辑在 {@link EmberForgeBlockEntity}。
 * <p>
 * 空手右键（主手空着时接管，拿着东西照旧能对着它放方块、用物品）：
 * <ul>
 *   <li>不潜行：动作栏报状态（结构缺哪格、还缺几样、要等天黑、有红石、锻造进度）。</li>
 *   <li>潜行：放开托着的所有物品（拾取延迟立即清零，{@value EmberForgeBlockEntity#RELEASE_TICKS} 刻内不再托住），东西放错了、改主意了用它取回。
 *       副手拿着东西也照样放开（{@link EmberForgeSneakRelease}）。</li>
 * </ul>
 * 正下方那块虚空石变了时让结构缓存标脏（别的格子离得远，靠 40 刻复查）；方块被拆掉时放开托着的物品。
 */
public class EmberForgeBlock extends MachineBlock<EmberForgeBlockEntity> {

    public EmberForgeBlock(Properties properties) {
        // 原作 setLightOpacity(0)：天空光直接穿过
        super(properties, true);
    }

    @Override
    protected BlockEntityType<EmberForgeBlockEntity> blockEntityType() {
        return ECEmberForge.EMBER_FORGE_BE.get();
    }

    @Override
    protected boolean opensMenu() {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof EmberForgeBlockEntity forge)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(forge.releaseHeld(server, true), true);
            } else {
                player.displayClientMessage(forge.statusMessage(server), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof EmberForgeBlockEntity forge) {
            forge.structure().markDirty();
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof EmberForgeBlockEntity forge) {
            forge.releaseHeld(server, false);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * 潜行时只要副手拿着东西（盾、图腾、火把、食物……），原版就不调方块的 use：服务端 {@code ServerPlayerGameMode.useItemOn}
     * 要求两只手的物品都 {@code doesSneakBypassUse}，空手为真、盾和火把为假；客户端判断相同，主手跳过后接着用副手（火把贴到锻炉上、盾举起来）。
     * 这里在「主手、潜行、主手空着、点的是余烬锻炉」时放行，交给 {@link #use} 放开托着的东西。两端都会触发这个事件，两端判断一致。
     * <p>
     * 只把 DEFAULT 升成 ALLOW，同 {@code DyeableBlock.SneakDyeing}：别的模组（领地等）在更高优先级只设了 useBlock DENY、没取消事件时不改回来，
     * 改回 ALLOW 就绕过了它们。
     */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class EmberForgeSneakRelease {

        private EmberForgeSneakRelease() {
        }

        @SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            Player player = event.getEntity();
            if (event.getHand() == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && player.getMainHandItem().isEmpty()
                    && event.getUseBlock() == Event.Result.DEFAULT
                    && event.getLevel().getBlockState(event.getPos()).is(ECEmberForge.EMBER_FORGE.get())) {
                event.setUseBlock(Event.Result.ALLOW);
            }
        }
    }
}
