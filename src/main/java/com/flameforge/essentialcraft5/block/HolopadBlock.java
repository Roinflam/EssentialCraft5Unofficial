package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.entity.HologramEntity;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.registry.ECHologram;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 全息投影台（原作 {@code BlockHologramSpawner}，49 行）：半格高的石英色台子。放在海纳尔的露天处右键，台子消失，头顶 5 格出现全息投影（batch8.md §4.1）。
 * <p>
 * 右键只在服务端判，下面都满足才召唤，否则快捷栏写原因、什么都不扣（{@link #summon}，顺序即判定顺序）：
 * 真玩家（假玩家、机械手不行）、生存 / 冒险模式、不是和平难度、维度是海纳尔（或开了 {@code general.hologram.other_dimensions}）、
 * 召唤者的矩阵阶够（第十三批 13a 的门，{@code general.matrix_rank.gates.holopad} 默认六阶；已经召出来的不受影响）、
 * 台子上方露天（这一列往上没有挡路的方块；原作研究书写着「必须放在海纳尔的露天处」，代码没查）、
 * 生成点 (x + 0.5, y + 5, z + 0.5) 站得下 0.6 × 1.8 的投影、64 格内没有别的全息投影、{@code ECProtection.canEdit}（召唤 = 拿掉这块方块）。
 * <b>先生成后扣</b>：投影加进世界成功（别的模组、混合端插件没取消）才删方块，不掉落；然后触发「召唤实体」进度条件。
 * <p>
 * 原作右键不看游戏模式、难度、头顶、领地，直接删掉自己、生成 Boss（可能生成在方块里），客户端也跟着删一次。
 */
public class HolopadBlock extends Block {

    /** 原作 BLOCK_AABB (0, 0, 0, 1, 0.5, 1) */
    private static final VoxelShape SHAPE = Block.box(0D, 0D, 0D, 16D, 8D, 16D);

    /** 召唤的结果（按判定顺序） */
    public enum Result {
        OK,
        NOT_REAL_PLAYER,
        GAME_MODE,
        PEACEFUL,
        DIMENSION,
        NO_SKY,
        BLOCKED,
        OCCUPIED,
        DENIED,
        SPAWN_FAILED,
        /** 召唤者的矩阵阶不够（第十三批 13a 的门，默认六阶；{@code MatrixRank.mayUseHolopad}） */
        RANK_TOO_LOW;

        /** 快捷栏提示的键：message.essentialcraft5.holopad.&lt;小写名&gt;（OK、NOT_REAL_PLAYER 不提示） */
        public String messageKey() {
            return "message.essentialcraft5.holopad." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** 召唤结论：result 是 OK 时 hologram 是召出来的投影 */
    public record Outcome(Result result, @Nullable HologramEntity hologram) {
        public boolean ok() {
            return result == Result.OK;
        }
    }

    public HolopadBlock(Properties properties) {
        super(properties);
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer) || !ECProtection.isRealPlayer(serverPlayer)) {
            return InteractionResult.PASS;
        }
        Outcome outcome = summon(serverPlayer, server, pos);
        if (outcome.result() == Result.RANK_TOO_LOW) {
            serverPlayer.displayClientMessage(MatrixRank.gateMessage(serverPlayer, MatrixRank.Gate.HOLOPAD), true);
        } else if (!outcome.ok()) {
            serverPlayer.displayClientMessage(Component.translatable(outcome.result().messageKey()).withStyle(ChatFormatting.RED), true);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * 用 pos 上的投影台召唤一只全息投影（见类注释）。只在服务端调；pos 上不是投影台时按生成失败算。
     */
    public static Outcome summon(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!ECProtection.isRealPlayer(player)) {
            return fail(Result.NOT_REAL_PLAYER);
        }
        if (player.isCreative() || player.isSpectator()) {
            return fail(Result.GAME_MODE);
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return fail(Result.PEACEFUL);
        }
        if (!HologramEntity.dimensionAllowed(level)) {
            return fail(Result.DIMENSION);
        }
        if (!MatrixRank.mayUseHolopad(player)) {
            return fail(Result.RANK_TOO_LOW);
        }
        if (!level.getBlockState(pos).is(ECHologram.HOLOPAD.get())) {
            return fail(Result.SPAWN_FAILED);
        }
        if (!openSky(level, pos)) {
            return fail(Result.NO_SKY);
        }
        Vec3 spawn = spawnPoint(pos);
        AABB box = ECHologram.HOLOGRAM.get().getDimensions().makeBoundingBox(spawn);
        if (!level.noCollision(box)) {
            return fail(Result.BLOCKED);
        }
        if (HologramEntity.anyNearby(level, Vec3.atCenterOf(pos))) {
            return fail(Result.OCCUPIED);
        }
        if (!ECProtection.canEdit(player, level, pos)) {
            return fail(Result.DENIED);
        }
        HologramEntity hologram = ECHologram.HOLOGRAM.get().create(level);
        if (hologram == null) {
            return fail(Result.SPAWN_FAILED);
        }
        // 面朝召唤者
        float yaw = (float) (Mth.atan2(player.getZ() - spawn.z, player.getX() - spawn.x) * Mth.RAD_TO_DEG) - 90F;
        hologram.moveTo(spawn.x, spawn.y, spawn.z, yaw, 0F);
        hologram.setYHeadRot(yaw);
        hologram.setYBodyRot(yaw);
        hologram.prepareSummon(player, pos);
        // 加入事件被取消时 addFreshEntity 返回 false；有的模组在加入事件里直接 discard 而不取消，返回值仍是 true，一并算失败
        if (!level.addFreshEntity(hologram) || hologram.isRemoved()) {
            return fail(Result.SPAWN_FAILED);
        }
        level.removeBlock(pos, false);
        CriteriaTriggers.SUMMONED_ENTITY.trigger(player, hologram);
        return new Outcome(Result.OK, hologram);
    }

    /** 投影台这一列往上没有挡路的方块（{@code MOTION_BLOCKING} 高度图：会挡住移动的方块和液体都算；花草、火把不算） */
    public static boolean openSky(Level level, BlockPos pos) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) <= pos.getY() + 1;
    }

    /** 投影出现的位置：台子上方 5 格的正中（原作 x + 0.5, y + 5, z + 0.5） */
    public static Vec3 spawnPoint(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + HologramEntity.SUMMON_HEIGHT, pos.getZ() + 0.5D);
    }

    private static Outcome fail(Result result) {
        return new Outcome(result, null);
    }
}
