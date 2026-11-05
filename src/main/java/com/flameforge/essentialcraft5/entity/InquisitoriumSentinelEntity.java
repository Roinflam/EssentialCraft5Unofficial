package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.block.InquisitoriumBeaconBlock;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.item.InquisitoriumCoordinatesItem;
import com.flameforge.essentialcraft5.registry.ECHologram;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 审判庭哨兵（第十批 10c-M1，batch10.md §5.10.5）：全息投影（{@link HologramEntity}）的进阶型，从审判庭信标召出来。
 * <p>
 * 照搬全息投影的一切：悬在信标上方 5 格不动、四招轮流放、只在休息期打得动、只吃真玩家的伤害（不是近战的 ÷4）、单次封顶
 * （{@code general.hologram.max_damage_per_hit}，{@link HologramEvents} 的最后一道也认它）、参战名单与拉回（走 {@code ECTeleport}，被取消不拉不扣血、
 * 人留在名单里）、24 格外打不动、对轨道打击免疫、不吃状态效果、进 {@code #forge:bosses}、和平难度消失、放弃时掉回召唤它的方块。不一样的：
 * <ul>
 *   <li>生命 {@code general.inquisitorium.max_health}（默认 800），紫色血条。</li>
 *   <li><b>回声</b>：一招放完、下一招开始时，上一招以 {@code echo_ratio}（默认 0.5）的强度跟着再来一遍，两招叠在一起躲
 *       （{@link #echoStep}）。克隆体、二分场的个数和护甲破坏者的频率乘这个比例（至少 1 只 / 1 个），轨道打击的伤害乘这个比例；
 *       回声的出手和本招错开半拍（克隆体在剩 {@value #ECHO_CLONE_TICK} 刻时、破坏者 / 轨道打击 / 二分场在两次本招之间）。
 *       回声出的克隆体、破坏者、二分场和本招共用这一只的上限（{@code max_clones / max_armor_destroyers / max_dividers}）；
 *       下一招和上一招是同一招时这一轮没有回声——同一种实体不会翻倍，每只 Boss 的技能实体合计仍不超过第八批算的上限。</li>
 *   <li><b>矩阵损伤</b>：它的招式每打实参战者一下（轨道打击、克隆体的近战，伤害真的落下：{@code LivingDamageEvent} 到了、没被取消、数值 &gt; 0），
 *       另加 {@code matrix_damage_per_hit}（默认 1000）点矩阵损伤，走 {@code CorruptionEffects.addMatrixDamage}（有上限）。
 *       被领地模组、混合端插件拦下的不加；二分场（直接把生命减半，不是伤害）、护甲破坏者（不伤人）不加。处理在 {@link InquisitoriumEvents}。</li>
 *   <li>二分场、轨道打击对它无效：在 {@code essentialcraft5:divider_immune} 里（二分枪的首领模式只走 {@code addEffect}，它也不吃状态效果），
 *       轨道打击照父类一律拒绝。</li>
 *   <li>掉落表 {@code entities/inquisitorium_sentinel}（tools/gen/hoanna_loot.py）：审判庭芯片 ×1、故障核心 ×1（带随机目标平衡值）、海纳尔遗物约 20 抽，
 *       全部只在玩家打倒时掉；50 点经验同全息投影。参战者拿到进度 {@code main/inquisitorium}（不发全息投影的两个进度）。</li>
 *   <li>放弃、和平难度时掉回的是审判庭信标，带着原来那份坐标（{@link #returnedSummoningItem}）。</li>
 *   <li>刷怪蛋、发射器放出来的照维度名单 {@code general.inquisitorium.summon_dimensions}（{@link #allowedIn}）和「64 格内只能有一只」。</li>
 * </ul>
 * 渲染在客户端 {@code client/render/InquisitoriumSentinelRenderer}：全息投影的贴图换成暗紫、加品红描边，头顶的核心之外，回声那一招的核心绕着转
 * （同步数据 {@link #echoAttack()}）。
 */
public class InquisitoriumSentinelEntity extends HologramEntity {

    /** 回声的克隆体在剩几刻时出（本招是 {@value HologramEntity#CLONE_SPAWN_TICK}） */
    public static final int ECHO_CLONE_TICK = 60;

    private static final EntityDataAccessor<Byte> DATA_ECHO = SynchedEntityData.defineId(InquisitoriumSentinelEntity.class, EntityDataSerializers.BYTE);
    private static final String TAG_BEACON = "beacon_target";

    /** 这一轮回声的第几发轨道打击（伤害照本招的翻倍规律再乘强度） */
    private int echoOrbitalCount;
    /** 召出它的信标记着的坐标（放弃时掉回的信标带上） */
    @Nullable
    private InquisitoriumCoordinatesItem.Target beaconTarget;
    /** GameTest 用：下一招固定成这一招（用一次就清掉） */
    @Nullable
    private Integer testNextAttack;

    public InquisitoriumSentinelEntity(EntityType<? extends InquisitoriumSentinelEntity> type, Level level) {
        super(type, level);
        setBossBarColor(BossEvent.BossBarColor.PURPLE);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, ECInquisitorium.Rules.DEFAULTS.maxHealth())
                .add(Attributes.MOVEMENT_SPEED, 0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_ECHO, (byte) REST);
    }

    // ------------------------------------------------------------------
    // 和全息投影不一样的地方
    // ------------------------------------------------------------------

    @Override
    protected double configuredMaxHealth() {
        return ECInquisitorium.Rules.values().maxHealth();
    }

    @Override
    public boolean allowedIn(Level level) {
        return InquisitoriumBeaconBlock.dimensionAllowed(level);
    }

    @Override
    protected int chooseAttack() {
        Integer forced = testNextAttack;
        if (forced != null) {
            testNextAttack = null;
            return forced;
        }
        return super.chooseAttack();
    }

    /** 新的一招开始：上一招（和这一招不是同一招、回声强度 &gt; 0 时）成为这一轮的回声 */
    @Override
    protected void onAttackStart(ServerLevel server, int attack) {
        int previous = prevAttackId();
        boolean echo = ECInquisitorium.Rules.values().echoRatio() > 0D && previous >= ADDITION && previous <= DIVISION && previous != attack;
        echoOrbitalCount = 0;
        setEchoAttack(echo ? previous : REST);
    }

    /** 回声那一招在这一刻该做的事（见类注释；timer 是本招减过以后的剩余刻数） */
    @Override
    protected void echoStep(ServerLevel server, int timer, float h) {
        int echo = echoAttack();
        double ratio = ECInquisitorium.Rules.values().echoRatio();
        if (echo == REST || ratio <= 0D) {
            return;
        }
        switch (echo) {
            case ADDITION -> {
                if (timer == ECHO_CLONE_TICK) {
                    spawnClones(server, scaled(cloneCount(h), ratio));
                }
            }
            case SUBTRACTION -> {
                int interval = echoDestroyerInterval(ratio);
                if (timer % interval == interval / 2) {
                    shootDestroyers(server);
                }
            }
            case MULTIPLICATION -> {
                if (timer % ORBITAL_INTERVAL == ORBITAL_INTERVAL / 2) {
                    float damage = strikeDamage(echoOrbitalCount++, ECHologram.Config.values().orbitalMaxDamage()) * (float) ratio;
                    strikeRandomFighter(server, damage, orbitalDelay(h));
                }
            }
            case DIVISION -> {
                if (timer % DIVIDER_INTERVAL == DIVIDER_INTERVAL / 2) {
                    placeDividers(server, scaled(dividerCount(h), ratio));
                }
            }
            default -> {
            }
        }
    }

    @Override
    protected void onAttackEnd(ServerLevel server, int attack) {
        setEchoAttack(REST);
    }

    /** 回声的个数：本招的个数 × 强度，四舍五入，至少 1 */
    public static int scaled(int count, double ratio) {
        return Math.max(1, (int) Math.round(count * ratio));
    }

    /** 回声的护甲破坏者每几刻一发：本招的间隔 ÷ 强度（强度 0.5 时 10 刻一发），不比本招密 */
    public static int echoDestroyerInterval(double ratio) {
        return Math.max(DESTROYER_INTERVAL, (int) Math.round(DESTROYER_INTERVAL / Math.max(ratio, 1.0E-3D)));
    }

    /** 打倒它的参战者拿「审判庭」进度（不发全息投影的两个） */
    @Override
    protected void awardVictory(ServerPlayer player) {
        PlayerPentacleBlockEntity.award(player, ECInquisitorium.ADVANCEMENT_SENTINEL, ECHologram.ADVANCEMENT_CRITERION);
    }

    /** 放弃时掉回审判庭信标，带着原来的坐标 */
    @Override
    protected ItemStack returnedSummoningItem() {
        ItemStack stack = new ItemStack(ECInquisitorium.INQUISITORIUM_BEACON.get());
        InquisitoriumCoordinatesItem.Target target = beaconTarget;
        if (target != null) {
            target.write(stack.getOrCreateTag());
        }
        return stack;
    }

    /** 掉落表 entities/inquisitorium_sentinel（不按招式分） */
    @Override
    protected ResourceLocation getDefaultLootTable() {
        return getType().getDefaultLootTable();
    }

    // ------------------------------------------------------------------
    // 状态
    // ------------------------------------------------------------------

    /** 这一轮的回声是哪一招（{@link #REST} = 没有）；两端都有（同步数据） */
    public int echoAttack() {
        return entityData.get(DATA_ECHO);
    }

    private void setEchoAttack(int attack) {
        entityData.set(DATA_ECHO, (byte) (attack < ADDITION || attack > DIVISION ? REST : attack));
    }

    /** 召出它的信标记着的坐标（刷怪蛋出来的为 null） */
    @Nullable
    public InquisitoriumCoordinatesItem.Target beaconTarget() {
        return beaconTarget;
    }

    /** 信标召唤时调（加进世界之前） */
    public void setBeaconTarget(@Nullable InquisitoriumCoordinatesItem.Target target) {
        this.beaconTarget = target;
    }

    /** GameTest / 展示厅用：下一次挑招固定成 attack（只管一次） */
    public void setTestNextAttack(@Nullable Integer attack) {
        this.testNextAttack = attack;
    }

    /** GameTest / 展示厅用：直接摆这一轮的回声（配合 {@link #setTestState}） */
    public void setTestEcho(int attack) {
        echoOrbitalCount = 0;
        setEchoAttack(attack);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (beaconTarget != null) {
            CompoundTag at = new CompoundTag();
            beaconTarget.write(at);
            tag.put(TAG_BEACON, at);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        beaconTarget = tag.contains(TAG_BEACON, Tag.TAG_COMPOUND) ? InquisitoriumCoordinatesItem.Target.read(tag.getCompound(TAG_BEACON)) : null;
        // 读档回到休息（父类），回声也清掉
        echoOrbitalCount = 0;
        setEchoAttack(REST);
    }
}
