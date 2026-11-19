package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.advancement.StructureFormedTrigger;
import com.flameforge.essentialcraft5.ambience.AmbienceParticles;
import com.flameforge.essentialcraft5.api.IESPEHandler;
import com.flameforge.essentialcraft5.demon.DemonTrades;
import com.flameforge.essentialcraft5.demon.DemonWant;
import com.flameforge.essentialcraft5.entity.DemonEntity;
import com.flameforge.essentialcraft5.espe.ESPETransfer;
import com.flameforge.essentialcraft5.registry.ECDemon;
import com.flameforge.essentialcraft5.structure.StructureCache;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructureResult;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 恶魔五芒星的方块实体（原作 TileDemonicPentacle）：判结构、召唤恶魔、客户端粒子。不存 ESPE、没有物品栏，ESPE 全从结构里的水晶柱抽。
 *
 * <h2>结构</h2>
 * {@link StructureCache}：没标脏时每 {@link #RECHECK_INTERVAL}（40，原作 sCheckTick）刻复查一次，紧挨着的方块变了（{@code neighborChanged}）标脏后
 * 过了 20 刻就重算；结构有一部分在没加载的区块里时沿用上次的结论。从没成形翻到成形时给附近的玩家发 {@code structure_formed}（手册打勾）。
 * 成形与否随更新包发给客户端（符文颜色、柱子粒子）。守阵的恶魔读 {@link #guardState()}。
 *
 * <h2>召唤（{@link #summon}，只在服务端）</h2>
 * 按顺序查，任何一条不满足都不扣 ESPE，快捷栏写原因：结构完整（当场重判一次，刚搭完不用等）→ 交易池不空 → 五芒星周围一格、往上两格以内没有恶魔
 * （一阵一只）→ 恶魔站的地方没有挡住它的方块 → 16 根柱子的原始 ESPE（不打灵魂五芒星那种 0.36 的折）合计够 {@code general.demon.summon_espe}。
 * 都满足才生成恶魔（生成被别的模组拦下、或加入世界时就被移除也不扣），再按原作 coords 的顺序一根一根抽（抽空一根再抽下一根）。恶魔记下召唤者（真玩家）、五芒星位置和需求，
 * 放召唤声，触发原版 {@code summoned_entity}（手册打勾）。
 *
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li>原作服务端每 tick 查一次恶魔只为客户端画烟、柱子粒子每 tick 32 个：这里恶魔检测只在客户端、20 刻一次，粒子每 2 刻一次。</li>
 *   <li>原作没成形时什么都不画，放下就找不到：这里画暗灰符文（BER）。</li>
 *   <li>原作认任何带 ESPE 能力、档位够的方块实体当柱子：这里照灵魂五芒星只认水晶柱底座。</li>
 * </ul>
 */
public class DemonicPentacleBlockEntity extends BlockEntity {

    /** 原作 sCheckTick = 40 */
    public static final int RECHECK_INTERVAL = 40;
    /** 客户端多久查一次五芒星上有没有恶魔（冒烟用） */
    public static final int DEMON_CHECK_INTERVAL = 20;
    private static final int PARTICLE_INTERVAL = 2;
    private static final double EPS = 1.0E-6D;
    private static final String TAG_FORMED = "formed";

    /** 召唤结果 */
    public enum Result {
        OK, NOT_LOADED, NOT_FORMED, NO_TRADES, OCCUPIED, BLOCKED, NO_ESPE, SPAWN_FAILED
    }

    /**
     * @param result  结果
     * @param demon   召出来的恶魔（只在 OK 时有）
     * @param message 快捷栏提示（OK 时为 null）
     */
    public record Outcome(Result result, @Nullable DemonEntity demon, @Nullable Component message) {
    }

    /** 守阵恶魔看到的结构状态：确定成形、确定坏了、还不知道（不知道时不算坏） */
    public enum GuardState {
        FORMED, BROKEN, UNKNOWN
    }

    private final StructureCache structure = new StructureCache(ECDemon.STRUCTURE, RECHECK_INTERVAL);
    /** 客户端上是服务端同步来的值；服务端 = structure.formed() */
    private boolean formed;
    /** 客户端：上次查到五芒星上有恶魔 */
    private boolean demonPresent;
    private int clientTicks;

    public DemonicPentacleBlockEntity(BlockPos pos, BlockState state) {
        super(ECDemon.DEMONIC_PENTACLE_BE.get(), pos, state);
    }

    public StructureCache structure() {
        return structure;
    }

    /** 结构成形（客户端上是同步来的值） */
    public boolean isFormed() {
        return formed;
    }

    public GuardState guardState() {
        return switch (structure.lastKnown()) {
            case MATCH -> GuardState.FORMED;
            case MISMATCH -> GuardState.BROKEN;
            default -> GuardState.UNKNOWN;
        };
    }

    // ------------------------------------------------------------------
    // 服务端
    // ------------------------------------------------------------------

    public void serverTick() {
        if (level == null) {
            return;
        }
        boolean was = structure.formed();
        structure.check(level, worldPosition, Direction.NORTH);
        updateFormed(was);
    }

    private void updateFormed(boolean was) {
        boolean now = structure.formed();
        if (!was && now && level instanceof ServerLevel serverLevel) {
            StructureFormedTrigger.triggerNearby(serverLevel, worldPosition, ECDemon.STRUCTURE_ID, 0);
        }
        if (now != formed) {
            formed = now;
            if (level != null) {
                level.blockEntityChanged(worldPosition);
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** 召唤一只恶魔（见类注释）。只在服务端调；召唤者要是真玩家（方块的 use 里判过） */
    public Outcome summon(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return fail(Result.NOT_LOADED, Component.translatable("message.essentialcraft5.demonic_pentacle.not_loaded"));
        }
        boolean was = structure.formed();
        StructureResult result = structure.checkNow(level, worldPosition, Direction.NORTH);
        updateFormed(was);
        if (result == StructureResult.UNKNOWN) {
            return fail(Result.NOT_LOADED, Component.translatable("message.essentialcraft5.demonic_pentacle.not_loaded"));
        }
        if (result != StructureResult.MATCH) {
            StructurePattern.Mismatch mismatch = structure.mismatch();
            Component detail = mismatch != null ? mismatch.message() : Component.empty();
            return fail(Result.NOT_FORMED, Component.translatable("message.essentialcraft5.demonic_pentacle.not_formed", detail));
        }
        if (DemonTrades.entries().isEmpty()) {
            return fail(Result.NO_TRADES, Component.translatable("message.essentialcraft5.demonic_pentacle.no_trades"));
        }
        if (!level.getEntitiesOfClass(DemonEntity.class, occupiedArea()).isEmpty()) {
            return fail(Result.OCCUPIED, Component.translatable("message.essentialcraft5.demonic_pentacle.occupied"));
        }
        double x = worldPosition.getX() + 0.5D;
        double y = worldPosition.getY() + 0.1D;
        double z = worldPosition.getZ() + 0.5D;
        if (blocked(x, y, z)) {
            return fail(Result.BLOCKED, Component.translatable("message.essentialcraft5.demonic_pentacle.blocked"));
        }
        int cost = ECDemon.Config.summonEspe();
        List<IESPEHandler> pillars = pillars();
        double available = 0D;
        for (IESPEHandler pillar : pillars) {
            available += pillar.getESPE();
        }
        if (available + EPS < cost) {
            return fail(Result.NO_ESPE, Component.translatable("message.essentialcraft5.demonic_pentacle.no_espe",
                    Mth.floor(available), cost));
        }
        // 第十三批 13a：召唤者是契约之誉「熟客」时开两样要价（DemonTrades.rollFor）
        List<DemonWant> wants = DemonTrades.rollFor(player, serverLevel.getRandom());
        DemonEntity demon = ECDemon.DEMON.get().create(serverLevel);
        if (wants.isEmpty() || demon == null) {
            return fail(Result.SPAWN_FAILED, Component.translatable("message.essentialcraft5.demonic_pentacle.spawn_failed"));
        }
        // 面朝召唤者
        float yaw = (float) (Mth.atan2(player.getZ() - z, player.getX() - x) * Mth.RAD_TO_DEG) - 90F;
        demon.moveTo(x, y, z, yaw, 0F);
        demon.setYHeadRot(yaw);
        demon.setYBodyRot(yaw);
        demon.setSummoner(player.getUUID());
        demon.setPentacle(worldPosition);
        demon.setWants(wants);
        // 加入事件被取消时 addFreshEntity 返回 false；有的模组在加入事件里直接 discard 而不取消，返回值仍是 true，一并算失败
        if (!serverLevel.addFreshEntity(demon) || demon.isRemoved()) {
            return fail(Result.SPAWN_FAILED, Component.translatable("message.essentialcraft5.demonic_pentacle.spawn_failed"));
        }
        drain(pillars, cost);
        serverLevel.playSound(null, x, y, z, ECDemon.SOUND_SUMMON.get(), SoundSource.HOSTILE, 1F, 1F);
        CriteriaTriggers.SUMMONED_ENTITY.trigger(player, demon);
        return new Outcome(Result.OK, demon, null);
    }

    private static Outcome fail(Result result, Component message) {
        return new Outcome(result, null, message.copy().withStyle(ChatFormatting.RED));
    }

    /** 一阵一只：五芒星这一格往外一圈、往上两格以内有恶魔就不召唤 */
    public AABB occupiedArea() {
        return new AABB(worldPosition).inflate(1D, 0D, 1D).expandTowards(0D, 2D, 0D);
    }

    /** 恶魔站在 (x, y, z) 时碰撞箱里有没有方块 */
    private boolean blocked(double x, double y, double z) {
        if (level == null) {
            return true;
        }
        EntityDimensions size = ECDemon.DEMON.get().getDimensions();
        AABB box = size.makeBoundingBox(x, y, z).deflate(1.0E-4D);
        for (VoxelShape shape : level.getBlockCollisions(null, box)) {
            if (!shape.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 结构里的 16 根柱子（按原作 coords 的顺序），只取已加载的、带 ESPE 的 */
    private List<IESPEHandler> pillars() {
        List<IESPEHandler> out = new ArrayList<>();
        if (level == null) {
            return out;
        }
        for (BlockPos offset : ECDemon.PILLAR_OFFSETS) {
            BlockPos at = worldPosition.offset(offset);
            IESPEHandler handler = ESPETransfer.loadedHandler(level, at, ESPETransfer.sideFacing(at, worldPosition));
            if (handler != null) {
                out.add(handler);
            }
        }
        return out;
    }

    /** 原作 consumeEnderstarEnergy 的第二遍：一根一根抽，抽够为止（够不够调用方已经算过） */
    private static void drain(List<IESPEHandler> pillars, double cost) {
        double remaining = cost;
        for (IESPEHandler pillar : pillars) {
            if (remaining <= EPS) {
                break;
            }
            remaining -= pillar.extractESPE(remaining, true);
        }
    }

    // ------------------------------------------------------------------
    // 客户端粒子
    // ------------------------------------------------------------------

    public void clientTick() {
        if (level == null) {
            return;
        }
        clientTicks++;
        if (clientTicks % DEMON_CHECK_INTERVAL == 1) {
            demonPresent = !level.getEntitiesOfClass(DemonEntity.class, new AABB(worldPosition)).isEmpty();
        }
        if (clientTicks % PARTICLE_INTERVAL != 0) {
            return;
        }
        if (demonPresent) {
            // 原作：四缕烟绕着五芒星转（世界时间 × 10°，四缕各差 90°），半径 1.1
            long time = level.getGameTime();
            for (int i = 0; i < 4; i++) {
                double angle = Math.toRadians(((time + 9L * i) % 36L) * 10L);
                AmbienceParticles.add(level, ParticleTypes.SMOKE, worldPosition.getX() + 0.5D + Math.sin(angle) * 1.1D, worldPosition.getY(),
                        worldPosition.getZ() + 0.5D + Math.cos(angle) * 1.1D, 0D, 0.1D, 0D);
            }
        }
        if (!formed) {
            return;
        }
        // 原作 movement：60 tick 一个来回的三角波，红色粒子在柱子底座、+2 两处上下走
        int movement = (int) (level.getGameTime() % 60);
        if (movement > 30) {
            movement = 60 - movement;
        }
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1F, 0F, 0F), 1F);
        for (BlockPos offset : ECDemon.PILLAR_OFFSETS) {
            BlockPos at = worldPosition.offset(offset);
            if (!(level.getBlockEntity(at) instanceof MithrilineCrystalPillarBlockEntity)) {
                continue;
            }
            double x = at.getX() + 0.5D;
            double z = at.getZ() + 0.5D;
            AmbienceParticles.add(level, dust, x, at.getY() + movement / 30D, z, 0D, 0D, 0D);
            AmbienceParticles.add(level, dust, x, at.getY() + 2D + movement / 30D, z, 0D, 0D, 0D);
        }
    }

    // ------------------------------------------------------------------
    // 存盘与同步
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        structure.save(tag);
        tag.putBoolean(TAG_FORMED, formed);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        // 服务端：读回上次确定的结构结论（读档后第一刻立即重判）；客户端更新包里只有 formed
        structure.load(tag);
        formed = tag.getBoolean(TAG_FORMED);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_FORMED, formed);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** 符文是 3×3 的方形斜放、转着画（对角 4.24 格），比方块本身大 */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.offset(-2, 0, -2), worldPosition.offset(3, 1, 3));
    }
}
