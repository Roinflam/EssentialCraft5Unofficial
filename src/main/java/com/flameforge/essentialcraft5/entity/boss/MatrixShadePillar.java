package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import com.flameforge.essentialcraft5.registry.ECMatrixShade;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 失序之柱（矩阵之影矩阵相的招，batch13.md §5.3.2）：落在参战者脚下的一圈预警（半径 {@link #RADIUS}），{@link #WARN} 刻（1.5 秒）后炸开，
 * 圈里的参战者挨 {@code pillar_damage} + 最大生命 × {@code pillar_health_percent}（{@code matrix_rend}，穿甲、算魔法、躲不开的那种只能跑出圈）。
 * 炸完留 {@link #LINGER} 刻给客户端画柱子散掉，然后消失。
 * <p>
 * 伤害的攻击者和直接实体都是矩阵之影本身（{@link ECDamageTypes#matrixRend}）：把柱子当直接实体会被别的模组当成召唤物挥砍，不再算魔法。
 * 不是生物（打不掉、挡不住人），不存盘、类别 MISC；放它的矩阵之影没了、散去时提前消失（不炸）。
 */
public class MatrixShadePillar extends Entity {

    public static final float RADIUS = 2.5F;
    public static final int WARN = 30;
    public static final int LINGER = 20;
    /** 炸开时圈里上下多高以内的人算在里面 */
    public static final double REACH_Y = 3D;

    @Nullable
    private MatrixShadeEntity owner;
    private boolean struck;

    public MatrixShadePillar(EntityType<? extends MatrixShadePillar> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** 放它的矩阵之影（加进世界之前调） */
    public void bind(MatrixShadeEntity shade) {
        owner = shade;
    }

    @Nullable
    public MatrixShadeEntity owner() {
        return owner;
    }

    public boolean struck() {
        return struck;
    }

    /** 预警进度 0~1（客户端画圈收缩用） */
    public float warnProgress(float partialTick) {
        return Math.min(1F, (tickCount + partialTick) / WARN);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        MatrixShadeEntity shade = owner;
        if (!struck && (shade == null || shade.isRemoved() || shade.isDeadOrDying() || shade.dissipating())) {
            BossWarnings.cancel(this);
            discard();
            return;
        }
        if (!struck) {
            // 预警圈由起手时发的预警包画（MatrixShadeSkills#pillars → BossWarnings），这里只等时间到
            if (tickCount >= WARN && shade != null) {
                strike(server, shade);
            }
        } else if (tickCount >= WARN + LINGER) {
            discard();
        }
    }

    /** 炸开：圈里的参战者挨一下；返回挨到的人（GameTest 用） */
    public List<ServerPlayer> strike(ServerLevel server, MatrixShadeEntity shade) {
        struck = true;
        List<ServerPlayer> hit = new ArrayList<>();
        ECMatrixShade.Config.Skills s = ECMatrixShade.Config.values().skills();
        // 炸开的画面由客户端画（FX_DISORDER）
        BossWarnings.resolve(this, S2CBossWarningPacket.FX_DISORDER, position(), RADIUS, BossWarnings.MATRIX);
        server.playSound(null, getX(), getY(), getZ(), ECMatrixShade.SOUND_PILLAR.get(), SoundSource.HOSTILE, 1.5F, 1F);
        for (ServerPlayer player : shade.bossRoster().online()) {
            if (!shade.isSkillTarget(player) || !inside(player.position())) {
                continue;
            }
            float amount = MatrixShadeSkills.damageFor(player, s.pillarDamage(), s.pillarPercent());
            if (amount > 0F && player.hurt(ECDamageTypes.matrixRend(shade), amount)) {
                hit.add(player);
            }
        }
        return hit;
    }

    /** 这一点在不在圈里（水平 {@link #RADIUS} 以内、上下 {@link #REACH_Y} 以内） */
    public boolean inside(Vec3 pos) {
        return Math.hypot(pos.x - getX(), pos.z - getZ()) <= RADIUS && Math.abs(pos.y - getY()) <= REACH_Y;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean canAddPassenger(Entity passenger) {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    /** 不和玩家互动 */
    @Override
    public void playerTouch(Player player) {
    }
}
