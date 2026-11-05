package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.hoanna.HoannaVisuals;
import com.flameforge.essentialcraft5.hoanna.event.LeyStorm;
import com.flameforge.essentialcraft5.registry.ECHoannaStorm;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 地脉落晶（第十三批 13d，batch13.md §6.2；HS §2.2.3）：地脉风暴里落在露天玩家附近的一颗晶体，碰到就收（{@link LeyStorm#collect}，地脉结晶 1 个、满月 2 个）。
 * <ul>
 *   <li>不是 Mob（不参与刷怪、不占怪物上限），不受重力。<b>服务端的位置一生成就在悬停高度</b>（落点地表上方 {@value #HOVER} 格），一直不动；
 *       「从上方 {@value LeyStorm#STARFALL_DROP_HEIGHT} 格每刻往下 {@value #FALL_SPEED} 格」只是客户端的画法：同步下落高度 {@link #DROP}
 *       和生成时这个世界的 gameTime {@link #SPAWN_TICK}，客户端按两者算出往上的偏移（{@link #visualOffset}）。</li>
 *   <li>这样它在服务端不走刻也碰得到、也会过期：混合端（Spigot 系的实体激活距离）和纯 Forge 里「加载着但不 tick 实体」的区块都会让实体停刻，
 *       下落和寿命要是靠服务端 tick，停刻时服务端的碰撞箱会一直挂在 12 格高处、永不过期，还一直占着落给那名玩家的名额。</li>
 *   <li>落地时刻、过期时刻按<b>主世界</b> gameTime 记（{@link #landAt}、{@link #expireAt}；各世界的 gameTime 不一样，混合端尤其如此），不存盘。
 *       落地以前碰到不收（看着还在空中），过期以后碰到也不收、直接删掉；走刻时到期自己删。</li>
 *   <li><b>不存盘</b>（注册时 {@code noSave}，{@link #shouldBeSaved} 也是 false）：区块卸载、关服就没了，不会越攒越多。</li>
 *   <li>记着是落给谁的（{@link #owner}）：加进世界 / 离开世界时在 {@link LeyStorm} 的计数表里登记 / 摘掉（连同过期时刻），
 *       每人名下的上限靠它，不做实体查询；判名额时过期的那些不算（{@link LeyStorm#owned(UUID, long)}）。</li>
 *   <li>打不坏、推不动、不怕爆炸和液体，准星点不中它（{@link #isPickable} false），收它只能走过去碰。</li>
 * </ul>
 * 客户端画法在 {@code client/hoanna/LeyStarfallRenderer}（地脉结晶的物品模型放大 1.5 倍、全亮，不画新贴图）；下落时偶尔掉一颗淡紫尘。
 */
public class LeyStarfallEntity extends Entity {

    /** 寿命（刻，从生成算起，含下落） */
    public static final int LIFETIME = 2_400;
    /** 每刻下落多少格（画法） */
    public static final double FALL_SPEED = 0.05D;
    /** 停在地表上方几格 */
    public static final double HOVER = 1D;
    /** 下落时每刻掉一颗尘的几率（只在客户端） */
    private static final float TRAIL_CHANCE = 0.25F;
    private static final DustParticleOptions TRAIL = new DustParticleOptions(new Vector3f(
            HoannaVisuals.red(HoannaVisuals.STORM_MOTE_COLOR), HoannaVisuals.green(HoannaVisuals.STORM_MOTE_COLOR),
            HoannaVisuals.blue(HoannaVisuals.STORM_MOTE_COLOR)), HoannaVisuals.STORM_MOTE_SCALE);

    /** 从悬停高度往上多少格开始落（画法；0 = 不落） */
    private static final EntityDataAccessor<Float> DROP = SynchedEntityData.defineId(LeyStarfallEntity.class, EntityDataSerializers.FLOAT);
    /** 生成时所在世界的 gameTime（低 32 位，客户端算下落用；两端同一个世界的 gameTime 是同步的） */
    private static final EntityDataAccessor<Integer> SPAWN_TICK = SynchedEntityData.defineId(LeyStarfallEntity.class, EntityDataSerializers.INT);

    @Nullable
    private UUID owner;
    /** 已经在计数表里登记过（加进世界时置 true，离开时摘掉） */
    private boolean counted;
    /** 落地 / 过期的主世界 gameTime（只在服务端有意义） */
    private long landAt;
    private long expireAt;

    public LeyStarfallEntity(EntityType<? extends LeyStarfallEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
        if (!level.isClientSide) {
            long now = now(level);
            this.landAt = now;
            this.expireAt = now + LIFETIME;
            this.entityData.set(SPAWN_TICK, (int) level.getGameTime());
        }
    }

    /** 服务端生成用：停在 (x, hoverY, z)，看上去从 startY 落下来，算在 owner 名下 */
    public LeyStarfallEntity(Level level, double x, double startY, double z, double hoverY, @Nullable UUID owner) {
        this(ECHoannaStorm.LEY_STARFALL.get(), level);
        setPos(x, hoverY, z);
        float drop = (float) Math.max(0D, startY - hoverY);
        this.entityData.set(DROP, drop);
        this.landAt = now(level) + fallTicks(drop);
        this.owner = owner;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DROP, 0F);
        this.entityData.define(SPAWN_TICK, 0);
    }

    /** 按主世界 gameTime 的「现在」（没有服务器时退回这个世界自己的） */
    public static long now(Level level) {
        MinecraftServer server = level.getServer();
        return server != null ? server.overworld().getGameTime() : level.getGameTime();
    }

    /** 从 drop 格高落到悬停高度要几刻 */
    public static int fallTicks(double drop) {
        return drop > 0D ? (int) Math.ceil(drop / FALL_SPEED) : 0;
    }

    /** 生成后 elapsed 刻时还在悬停高度上方几格（纯函数，两端同一个算法） */
    public static double fallOffset(double drop, double elapsed) {
        return Math.max(0D, drop - Math.max(0D, elapsed) * FALL_SPEED);
    }

    /** 落给谁的（可能已经下线） */
    @Nullable
    public UUID owner() {
        return owner;
    }

    /** 从悬停高度往上多少格开始落（同步值） */
    public double dropHeight() {
        return this.entityData.get(DROP);
    }

    /** 画的时候往上偏多少格（客户端；按这个世界的 gameTime 算，实体停刻也照样往下落） */
    public double visualOffset(float partialTick) {
        int elapsed = (int) level().getGameTime() - this.entityData.get(SPAWN_TICK);
        return fallOffset(dropHeight(), elapsed + (double) partialTick);
    }

    /** 落地时刻（主世界 gameTime） */
    public long landAt() {
        return landAt;
    }

    /** 过期时刻（主世界 gameTime） */
    public long expireAt() {
        return expireAt;
    }

    /** 已经落地（服务端，按主世界 gameTime）：落地以前碰到不收 */
    public boolean landed() {
        return now(level()) >= landAt;
    }

    /** 已经过期（服务端，按主世界 gameTime） */
    public boolean expired() {
        return now(level()) >= expireAt;
    }

    // ------------------------------------------------------------------ 每刻

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            double offset = visualOffset(0F);
            if (offset > 0D && this.random.nextFloat() < TRAIL_CHANCE) {
                level().addParticle(TRAIL, getX() + (this.random.nextDouble() - 0.5D) * 0.3D, getY() + offset + 0.25D,
                        getZ() + (this.random.nextDouble() - 0.5D) * 0.3D, 0D, 0.02D, 0D);
            }
        } else if (expired()) {
            discard();
        }
    }

    /** 下落那一段也在画面里：剔除用的包围盒往上加上下落高度，免得还在空中时被当成出了视野 */
    @Override
    public AABB getBoundingBoxForCulling() {
        double drop = dropHeight();
        return drop > 0D ? getBoundingBox().expandTowards(0D, drop, 0D) : getBoundingBox();
    }

    /** 真玩家碰到（原版每刻对玩家身边的实体调一次，同经验球）就收；旁观者原版不调，假玩家、客户端在 {@link LeyStorm#collect} 里挡掉 */
    @Override
    public void playerTouch(Player player) {
        if (!level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            LeyStorm.collect(this, serverPlayer);
        }
    }

    // ------------------------------------------------------------------ 计数（只在服务端）

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide && owner != null && !counted) {
            counted = true;
            LeyStorm.track(owner, getUUID(), expireAt);
        }
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (counted && owner != null) {
            counted = false;
            LeyStorm.untrack(owner, getUUID());
        }
    }

    /** 只给 GameTest / 混合端自检：把落地、过期时刻往前挪 ticks 刻（当成已经过了这么久），计数表里的过期时刻跟着改 */
    public void rewindForTest(long ticks) {
        landAt -= ticks;
        expireAt -= ticks;
        if (counted && owner != null) {
            LeyStorm.track(owner, getUUID(), expireAt);
        }
    }

    /** 只给 GameTest / 混合端自检：当成已经落地 */
    public void landNowForTest() {
        long left = landAt - now(level());
        if (left > 0L) {
            rewindForTest(left);
        }
    }

    // ------------------------------------------------------------------ 不受外界影响

    @Override
    public boolean hurt(DamageSource source, float amount) {
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
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public void setDeltaMovement(Vec3 motion) {
        super.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** 追踪 4 个区块（64 格），离得再远也画（落点在 12~40 格外，按原版按体积算的距离只有 32 格左右） */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double max = 64D * getViewScale();
        return distance < max * max;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
