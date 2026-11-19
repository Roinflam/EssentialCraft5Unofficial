package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import com.flameforge.essentialcraft5.registry.ECCalamityHeart;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 落石（天灾之心地震相，batch13.md §5.3.5）：从落点正上方 {@value #FALL_HEIGHT} 格落下，正好 {@code rubble_warning_ticks}（20 刻）落地；
 * 落地前地上一直有一圈影子（烟），砸中 {@value #RADIUS} 格内的参战者 {@code rubble_damage}（吃护甲，攻击者是天灾之心）。
 * <b>落地即消失、不放方块、不碰方块</b>（穿过树叶、屋顶直接落到记下的地面高度：挖洞躲不开，只能看影子挪开）。
 * <p>
 * 不是活物（同 13c 失序之柱），不存盘，读档后不会回来；天灾之心没了（死了、散了、卸载了）就跟着消失。
 * 客户端画成一个原版石头方块。注册在 {@code registry/ECCalamityHeart}。
 */
public class CalamityRubbleEntity extends Entity {

    /** 从多高落下 */
    public static final double FALL_HEIGHT = 14D;
    /** 砸多大一圈（水平） */
    public static final double RADIUS = 1.5D;
    /** 落地画面里尘土的颜色（石灰） */
    private static final int RUBBLE_DUST = 0x8C8478;

    @Nullable
    private CalamityHeartEntity heart;
    private double groundY;
    private double speed = 0.7D;
    private boolean armed;

    public CalamityRubbleEntity(EntityType<? extends CalamityRubbleEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** 天灾之心放落石时调（加进世界之前）：落点（地面顶面）、几刻落地 */
    public void bind(CalamityHeartEntity owner, Vec3 ground, int warnTicks) {
        heart = owner;
        groundY = ground.y;
        speed = FALL_HEIGHT / Math.max(1, warnTicks);
        armed = true;
        moveTo(ground.x, ground.y + FALL_HEIGHT, ground.z, 0F, 0F);
    }

    public double groundY() {
        return groundY;
    }

    /** 加进世界后调一次：落点的预警圈发给客户端（{@link BossWarnings}），落多久就预警多久 */
    public void announce() {
        int ticks = (int) Math.ceil((getY() - groundY) / Math.max(1.0E-3D, speed));
        BossWarnings.circle(this, null, new Vec3(getX(), groundY, getZ()), RADIUS, ticks, BossWarnings.DANGER);
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
        CalamityHeartEntity owner = heart;
        if (!armed || owner == null || owner.isRemoved() || !owner.isAlive() || owner.dissipating()) {
            BossWarnings.cancel(this);
            discard();
            return;
        }
        double next = getY() - speed;
        if (next > groundY) {
            setPos(getX(), next, getZ());
            return;
        }
        setPos(getX(), groundY, getZ());
        land(server, owner);
    }

    /** 落地：砸参战者、出声、碎石粒子，然后消失（不放方块）；返回砸中了几人 */
    public int land(ServerLevel server, CalamityHeartEntity owner) {
        int hit = 0;
        double damage = ECCalamityHeart.Config.values().skills().rubbleDamage();
        for (ServerPlayer player : owner.heartSkills().targets(Double.MAX_VALUE)) {
            if (Math.hypot(player.getX() - getX(), player.getZ() - getZ()) <= RADIUS && Math.abs(player.getY() - groundY) <= 2.5D
                    && damage > 0D && player.hurt(owner.damageSources().mobAttack(owner), (float) damage)) {
                hit++;
            }
        }
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), getX(), groundY + 0.2D, getZ(),
                20, 0.5D, 0.2D, 0.5D, 0.1D);
        // 砸地的冲击、火星、尘土由客户端画（FX_RUBBLE，地震的熔橙 + 石灰色）
        BossWarnings.resolve(this, S2CBossWarningPacket.FX_RUBBLE, new Vec3(getX(), groundY, getZ()), RADIUS, 0D, Vec3.ZERO, 0, BossWarnings.QUAKE,
                RUBBLE_DUST);
        server.playSound(null, getX(), groundY, getZ(), ECCalamityHeart.SOUND_RUBBLE.get(), SoundSource.HOSTILE, 1.5F, 0.7F);
        armed = false;
        discard();
        return hit;
    }

    /** 技能实体：不存盘 */
    @Override
    public boolean shouldBeSaved() {
        return false;
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
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
