package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.registry.ECBossFramework;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 本模组首领的基类（第十三批 13c-Fb，batch13.md §5.3「共同」）：把 {@link ECBoss} 要做的接线都做好，子类只管招式、模型和自己的配置。
 * 照全息投影另写的一份（全息投影、哨兵的代码不动），和它的差别：名单、封顶、掉落都是组件（{@link BossRoster}、{@link BossDamageBudget}、
 * {@link BossLoot}），受伤规矩走事件（{@link BossRules}），所以不继承这个类、自己实现 {@link ECBoss} 的首领也是同一套规矩。
 *
 * <h2>子类要给的</h2>
 * <ul>
 *   <li>构造器传档位（{@link BossTier}）和血条颜色；属性照常用 {@code ECEntities.attributes} 登记；</li>
 *   <li>{@link #bossLimits()}：单次封顶 / 每秒桶（读自己的配置，0 = 不限）；{@link #rosterSettings()}：参战、拉回、脱战距离与开关
 *       （{@link BossRoster.Settings#arena} / {@link BossRoster.Settings#guardian}）；有固定场地的覆盖 {@link #arenaFloor()}；</li>
 *   <li>没被打倒就结束时（{@link #endBoss}）要掉回召唤物、退还材料的覆盖它，默认原地散去（{@link #dissipate}）；
 *       打倒以后要加的事覆盖 {@link #onBossDefeated}；战利品表照常是 {@code getDefaultLootTable()}（{@code entities/<名字>}）。</li>
 * </ul>
 *
 * <h2>这里做了的</h2>
 * <ul>
 *   <li>服务端每刻（不是 NoAI 时）推进名单（{@link #serverBossStep}：和平难度、名单检查、拉回、飞行压制、散去计时、血条）；</li>
 *   <li>死亡：原版的掉落一步换成「每名分得到的人一份进背包」（{@link #dropFromLootTable}），之后记首杀、发异兽录进度、调 {@link #onBossDefeated}
 *       （只在有玩家参与的死亡：{@code /kill}、掉出世界这类绕过无敌、又不是参战玩家打的致死一下，哪怕之前刚有人打过它，也不掉东西、
 *       不给经验、不算首杀，见 {@link #revokesPlayerCredit}；子类还能用 {@link #rewardsAllowed} 整个关掉），收掉还在的技能实体；</li>
 *   <li>不自然消失、和平难度不走原版的直接删除（交给名单，好让子类退还东西）、拴不住、不换维度、不捡东西、不骑任何载具；
 *       Boss 档另：推不动、不被水流推、不吃状态效果（发光除外）、没有摔落伤害（其余在 {@link BossRules} 的事件里）；</li>
 *   <li>加进 / 移出世界时登记 / 撤掉名单（飞行压制器、血条），移出世界时收掉技能实体；</li>
 *   <li>存读档：名单、伤害账、放弃计时（{@code boss_roster}）。技能实体不存盘。</li>
 * </ul>
 * 技能打谁用 {@link #isSkillTarget}（名单里在线的参战者），放几个用 {@link #skills()} 数（{@link BossSkillSlots}）。
 *
 * <h2>从 {@link ECMonster} 继承来的</h2>
 * 来源（{@code SpawnOrigin}；玩家召出的首领本体由召唤代码 {@code SpawnOrigin.set(首领, Origin.BOSS)}）、元素弱点标签（{@code MobWeakness}）、
 * 玩家伤害占比（{@code MobDamageShare}：首领记的是 {@link BossDamageBudget#resolve} 夹过以后的数，小 Boss 的 {@code fair_kill} 读它）、
 * 不开门、不踩耕地、不捡东西。首领没有精英（{@link #hasEliteVariant} 恒为 false，生成时不掷、{@code variant} 存档读回来也不算）；
 * 技能目标换成更严的名单判定（{@link #isSkillTarget}）。地面寻路是 {@code ECMonster} 那份（不开门），会飞的首领自己换移动控制和寻路。
 */
public abstract class ECBossMonster extends ECMonster implements ECBoss, BossHealthGuard.Guarded {

    private static final String TAG_ROSTER = "boss_roster";

    private final BossTier tier;
    private final BossRoster roster;
    private final BossDamageBudget budget = new BossDamageBudget();
    /** 绕开伤害流程的掉血也走同一份预算（构造期间还是 null：父类构造器里就会 setHealth） */
    private final BossHealthGuard healthGuard = new BossHealthGuard();
    private final BossSkillSlots skills = new BossSkillSlots();
    private boolean defeated;
    private boolean defeatedByPlayers;
    /** 这一次死亡选好的分账名单（{@link #rewardWinners}；不存盘） */
    @Nullable
    private List<ServerPlayer> rewardWinners;

    protected ECBossMonster(EntityType<? extends ECBossMonster> type, Level level, BossTier tier, BossEvent.BossBarColor barColor) {
        super(type, level);
        this.tier = tier;
        this.roster = new BossRoster(this, barColor);
    }

    // ------------------------------------------------------------------
    // ECBoss
    // ------------------------------------------------------------------

    @Override
    public BossTier bossTier() {
        return tier;
    }

    @Override
    public BossRoster bossRoster() {
        return roster;
    }

    @Override
    public BossDamageBudget bossBudget() {
        return budget;
    }

    // ------------------------------------------------------------------
    // 生命结算（BossHealthGuard）
    // ------------------------------------------------------------------

    @Override
    public BossHealthGuard healthGuard() {
        return healthGuard;
    }

    @Override
    public BossDamageBudget guardBudget() {
        return budget;
    }

    @Override
    public BossDamageBudget.Limits guardLimits() {
        BossDamageBudget.Limits limits = effectiveLimits();
        return tier.usesBucket() ? limits : limits.withoutBucket();
    }

    /** 直接改生命的写法也按单次封顶 + 每秒桶结算（见 {@link BossHealthGuard}） */
    @Override
    public void setHealth(float health) {
        if (healthGuard == null || level().isClientSide) {
            super.setHealth(health);
            return;
        }
        healthGuard.set(this, this, health);
    }

    /** 自己的机制改生命（复活、重立）：不结算 */
    protected void setHealthUnguarded(float health) {
        if (healthGuard == null || level().isClientSide) {
            super.setHealth(health);
            return;
        }
        healthGuard.unguarded(this, health);
    }

    /** 血量同步数据被直接改了（反射拿到数据键再 set 的写法）：当场结算 */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (healthGuard != null && BossHealthGuard.isHealth(key) && !level().isClientSide) {
            healthGuard.observe(this, this);
        }
    }

    /** 每刻开头对一遍生命，兜住绕开 set 直接改数据项的写法；顺带结算被压低的生命上限 */
    @Override
    public void tick() {
        if (healthGuard != null && !level().isClientSide) {
            healthGuard.observe(this, this);
        }
        super.tick();
    }

    /** 放出来的技能实体（按种类数上限） */
    public BossSkillSlots skills() {
        return skills;
    }

    /** 技能打不打这个目标：名单里在线、同世界的生存 / 冒险参战者（比普通怪的规矩严：不打仇恨目标里的生物） */
    @Override
    public boolean isSkillTarget(@Nullable LivingEntity target) {
        return BossRules.isSkillTarget(this, target);
    }

    /** 首领没有精英变体（不管类型进没进 {@code #forge:bosses}） */
    @Override
    public boolean hasEliteVariant() {
        return false;
    }

    /** 没被打倒就结束（名单空了到点、和平难度）：默认原地散去 */
    @Override
    public void endBoss(BossEnd reason) {
        dissipate();
    }

    /** 原地散去：冒一团烟、放散去音效、收掉技能实体、移除自己（不掉东西、不给经验） */
    public void dissipate() {
        if (isRemoved()) {
            return;
        }
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() * 0.5D, getZ(), 24,
                    getBbWidth() * 0.5D, getBbHeight() * 0.4D, getBbWidth() * 0.5D, 0.02D);
            server.playSound(null, getX(), getY(), getZ(), ECBossFramework.SOUND_DISSIPATE.get(), SoundSource.HOSTILE, 1.5F, 1F);
        }
        skills.discardAll();
        discard();
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        serverBossStep();
    }

    /** 服务端的一刻里首领通用件那部分（{@link #customServerAiStep} 每刻调；GameTest 对 NoAI 的首领直接调） */
    public void serverBossStep() {
        roster.tick();
    }

    // ------------------------------------------------------------------
    // 死亡与掉落
    // ------------------------------------------------------------------

    @Override
    public void die(DamageSource source) {
        if (healthGuard != null && !level().isClientSide) {
            // 先对一遍生命（绕开 set 直接改数据项、紧接着调 die() 的写法），生命没真扣到 0 就不算死，也不掉落
            healthGuard.observe(this, this);
            if (healthGuard.refusesDeath(this)) {
                return;
            }
        }
        boolean wasDead = dead;
        int hurtByPlayerTime = lastHurtByPlayerTime;
        Player hurtByPlayer = lastHurtByPlayer;
        boolean revoked = !wasDead && revokesPlayerCredit(source);
        if (revoked) {
            // 原版的掉落、经验、killed_by_player 都只看「100 刻内有没有玩家打过」，这里先抹掉，三样一起按没有玩家参与算
            lastHurtByPlayerTime = 0;
            lastHurtByPlayer = null;
        }
        boolean byPlayer = lastHurtByPlayerTime > 0;
        super.die(source);
        if (revoked && !dead) {
            // 死亡被取消了：还原
            lastHurtByPlayerTime = hurtByPlayerTime;
            lastHurtByPlayer = hurtByPlayer;
        }
        if (wasDead || !dead || defeated || !(level() instanceof ServerLevel)) {
            return;
        }
        defeated = true;
        if (byPlayer && rewardsAllowed()) {
            defeatedByPlayers = true;
            List<ServerPlayer> winners = rewardWinners(true);
            BossLoot.recordVictory(this, winners);
            onBossDefeated(winners);
        }
        skills.discardAll();
        roster.bar().removeAllPlayers();
    }

    /**
     * 致死的这一下绕过了无敌（{@code /kill}、掉出世界、清怪的指令和模组），又不是参战的生存 / 冒险真玩家打的：
     * 哪怕之前 100 刻内有人打过它，也不算玩家打倒的（不掉东西、不给经验、不记首杀）。故意把它弄进虚空、再用指令补一下，只能拿回失败的退还。
     */
    public static boolean revokesPlayerCredit(DamageSource source) {
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return !(source.getEntity() instanceof ServerPlayer player && ECProtection.isRealPlayer(player) && BossRules.fighting(player));
    }

    /**
     * 这一只此刻打倒了给不给东西：战利品、经验、首杀、异兽录、{@link #onBossDefeated} 都看它。默认给；
     * 矩阵之影覆盖成「契约还开着、没在散去」（刷怪蛋放的、已经按失败退过的不给）。
     */
    protected boolean rewardsAllowed() {
        return true;
    }

    @Override
    protected boolean shouldDropLoot() {
        return rewardsAllowed() && super.shouldDropLoot();
    }

    @Override
    public boolean shouldDropExperience() {
        return rewardsAllowed() && super.shouldDropExperience();
    }

    /** 这一只是被参战的玩家打倒的（死了、有玩家参与、给了东西）；没死、没有玩家参与、不给东西时都是 false */
    public boolean defeatedByPlayers() {
        return defeatedByPlayers;
    }

    /** 原版在死亡位置掷一次表、撒在地上；这里换成每名分得到的人各掷一次、直接进背包（{@link BossLoot#distribute}） */
    @Override
    protected void dropFromLootTable(DamageSource source, boolean byPlayer) {
        if (level() instanceof ServerLevel server) {
            BossLoot.distribute(this, bossLootTable(server), source, byPlayer, rewardWinners(byPlayer));
        }
    }

    /**
     * 这一次死亡里分掉落、记首杀、发异兽录的人：{@link BossLoot#winners} 再过一遍 {@link #selectRewardWinners}，同一次死亡只选一次
     * （掉落在 {@code super.die} 里先分，首杀和异兽录后记，两边必须是同一批人）。没有玩家参与、不给东西时不选（{@code killed_by_player} 的池子反正不出）。
     */
    protected final List<ServerPlayer> rewardWinners(boolean byPlayer) {
        if (!byPlayer || !rewardsAllowed()) {
            return BossLoot.winners(this);
        }
        if (rewardWinners == null) {
            rewardWinners = List.copyOf(selectRewardWinners(BossLoot.winners(this)));
        }
        return rewardWinners;
    }

    /**
     * 从打够伤害的人里挑真正分得到东西的（默认全部）。每人每日只领一份的首领（渊潮领主）在这里滤掉今天领过的、给剩下的记上今天；
     * 一次死亡只调一次。
     */
    protected List<ServerPlayer> selectRewardWinners(List<ServerPlayer> qualified) {
        return qualified;
    }

    /** 掉落用的那张表（默认 {@code getLootTable()}，GameTest 可以换成手搭的表） */
    protected LootTable bossLootTable(ServerLevel server) {
        return server.getServer().getLootData().getLootTable(getLootTable());
    }

    /** 已经被打倒（打倒的结算做过了） */
    public boolean defeated() {
        return defeated;
    }

    // ------------------------------------------------------------------
    // 世界登记、血条
    // ------------------------------------------------------------------

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        roster.attach();
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        roster.detach();
        if (!level().isClientSide) {
            skills.discardAll();
        }
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        roster.bar().setName(getDisplayName());
    }

    // ------------------------------------------------------------------
    // 不消失、不被摆弄
    // ------------------------------------------------------------------

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    /** 和平难度不走原版的直接删除：交给名单（{@link BossRoster#tick}），子类在 {@link #endBoss} 里退还东西 */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return !tier.immovable() && super.isPushable();
    }

    @Override
    public boolean isPushedByFluid() {
        return !tier.immovable() && super.isPushedByFluid();
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return BossRules.effectAllowed(this, effect) && super.canBeAffected(effect);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return !tier.environmentImmune() && super.causeFallDamage(distance, multiplier, source);
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        CompoundTag rosterTag = new CompoundTag();
        roster.save(rosterTag);
        tag.put(TAG_ROSTER, rosterTag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        roster.load(tag.getCompound(TAG_ROSTER));
        budget.reset();
        healthGuard.reset();
        if (tag.contains(TAG_ROSTER, Tag.TAG_COMPOUND)) {
            roster.bar().setName(getDisplayName());
        }
    }
}
