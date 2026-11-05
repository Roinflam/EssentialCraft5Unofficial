package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * 首领生命的统一结算：不管血是怎么掉的，都按同一份单次封顶 + 每秒桶（{@link BossDamageBudget}）放行。
 *
 * <h2>为什么要有这一层</h2>
 * {@link BossDamageBudget} 只管走伤害流程的那一下（{@code LivingDamageEvent}）。绕开伤害流程掉血的写法一样能把首领秒掉：
 * 直接 {@code setHealth}、反射找到血量那条同步数据直接 {@code set}、血没扣到 0 就调 {@code die()}、
 * 用 {@code /kill} 同款的伤害（{@code generic_kill}，绕过无敌）兜底、给生命上限挂一个很大的负修饰符。
 * 这里不认是谁写的，只看「生命比上次认可的低了多少」，统一结算。
 *
 * <h2>怎么结算</h2>
 * <ul>
 *   <li>记一个「认可的生命」。生命往上（治疗、读档）一律照收；往下的那段叫一次掉血。</li>
 *   <li>伤害流程放行过的量（{@link #allow}，在 {@code LivingDamageEvent} 最后夹完时记，桶已经扣过）先抵掉这次掉血。
 *       原版 / Mohist 系混合端扣血都是「夹完以后 {@code setHealth(当前 − 伤害)}」，正好全被放行量抵掉，不多扣桶。</li>
 *   <li>抵不掉的部分当成额外的伤害：和抵掉的量合起来不超过单次封顶；绕开伤害流程的掉血没有受击无敌，
 *       所以每 {@value #DIRECT_WINDOW} 刻（原版受击无敌的时长）合起来最多算一下封顶；再从每秒桶里拿。</li>
 *   <li>首领此刻打不动时（{@link Guarded#guardFloor}，例：天灾之心护盾期），绕开伤害流程的掉血压不到下限以下（伤害流程放行过的不管，
 *       实体自己的 {@code hurt} 已经判过打不打得动）。</li>
 *   <li>结算出来比请求的高，就把生命写回结算值。写的入口有三个：{@code setHealth}（{@link #set}）、血量同步数据被改
 *       （{@code onSyncedDataUpdated} → {@link #observe}）、每刻开头对一遍（{@link #observe}，兜住绕开 {@code set} 直接改数据项的写法）。</li>
 *   <li>生命上限被压到当前生命以下：生命先留在上限之上（{@code getMaxHealth} 是 final，没法顶住上限，首领血条按满格画），
 *       每刻开头按同样的规则往下结算，拿得到多少就降多少，直到降到真实上限；这期间治疗不往上加。</li>
 *   <li>{@code die()}：生命还大于 0 就不算死（{@link #refusesDeath}）。</li>
 * </ul>
 * 只在服务端用；实体构造期间（字段还没初始化）和客户端不经过这里。刚生成 / 刚读档、还没过第一刻（还没认过生命）时写入照收：
 * 生成时按配置设上限、回满血，读档恢复生命都在这时候。
 *
 * <h2>限额跟着生命上限放大（{@link #limitScale}）</h2>
 * 首领的最大生命可能在加入世界时被抬高（生命上限挂上倍乘的属性修饰，几十到上百倍都有），
 * 单次封顶和每秒桶不跟着变的话首领厚出几十倍、按每秒的上限打几个小时也打不完。所以两项都乘「生命上限 ÷ 生命的基础值」
 * （基础值就是首领按配置设的那份生命），最小 1：没被放大时和原来一样；挂负修饰把上限往下压也压不小限额。
 * 倍数在出生后 {@value #SCALE_SETTLE_TICKS} 刻认下（之前照当时算），存进实体的 Forge 持久数据，读档沿用，之后生命上限再变都不跟：
 * 打的过程中给首领挂生命提升之类的效果（上限变大、生命不变）不能顺手把限额抬上去。
 */
public final class BossHealthGuard {

    /** 受这层结算保护的实体 */
    public interface Guarded {

        BossHealthGuard healthGuard();

        /** 结算用的桶（和伤害流程共用同一份） */
        BossDamageBudget guardBudget();

        /** 结算用的限额（不用每秒桶的首领给不带桶的那份） */
        BossDamageBudget.Limits guardLimits();

        /** 绕开伤害流程掉血时最低掉到多少（此刻打不动就给正无穷；默认 0） */
        default float guardFloor() {
            return 0F;
        }
    }

    /** {@code LivingEntity.DATA_HEALTH_ID}（私有，取一次） */
    private static final EntityDataAccessor<Float> HEALTH = ObfuscationReflectionHelper.getPrivateValue(LivingEntity.class, null, "f_20961_");

    /** 绕开伤害流程的掉血，多少刻里合起来算一下（原版受击无敌 20 刻、过半就能再挨打，等于 10 刻一下） */
    public static final int DIRECT_WINDOW = 10;

    private static final float EPSILON = 1.0E-4F;

    /** 出生后多少刻认下限额的放大倍数（生命上限多在加入世界时被抬高，晚的也在头几刻里） */
    public static final int SCALE_SETTLE_TICKS = 20;
    /** 认下的放大倍数存在实体的 Forge 持久数据里的键 */
    public static final String TAG_LIMIT_SCALE = EssentialCraft5.MODID + ":limit_scale";

    /** 认下的放大倍数；NaN = 还没认 */
    private float limitScale = Float.NaN;
    /** 认可的生命；NaN = 还没认过（刚生成 / 读档、还没过第一刻，写入照收） */
    private float accepted = Float.NaN;
    /** 伤害流程放行过、还没落到生命上的量（只在记下的那一刻有效） */
    private float allowance;
    private long allowanceTick = Long.MIN_VALUE;
    /** 这一窗绕开伤害流程已经掉了多少 */
    private float directUsed;
    private long directWindow = Long.MIN_VALUE;
    private int writing;

    /** 是不是血量那条同步数据 */
    public static boolean isHealth(EntityDataAccessor<?> key) {
        return HEALTH.equals(key);
    }

    /** 伤害流程夹完以后放行的量（{@code LivingDamageEvent} 最后一道） */
    public void allow(float amount, long gameTime) {
        if (!(amount > 0F)) {
            return;
        }
        if (allowanceTick != gameTime) {
            allowance = 0F;
            allowanceTick = gameTime;
        }
        allowance = Float.isInfinite(amount) ? Float.POSITIVE_INFINITY : allowance + amount;
    }

    /** 实体的 {@code setHealth}：结算后写入 */
    public void set(LivingEntity entity, Guarded guarded, float requested) {
        if (writing > 0 || Float.isNaN(accepted)) {
            rawWrite(entity, Mth.clamp(requested, 0F, entity.getMaxHealth()));
            return;
        }
        observe(entity, guarded);
        float wanted = Float.isNaN(requested) ? entity.getHealth() : requested;
        settleAndWrite(entity, guarded, wanted);
    }

    /** 血量同步数据被改过，或者每刻开头：生命比认可的低就结算，结算值更高就写回；顺带结算被压低的生命上限 */
    public void observe(LivingEntity entity, Guarded guarded) {
        if (writing > 0) {
            return;
        }
        if (Float.isNaN(limitScale) && entity.tickCount >= SCALE_SETTLE_TICKS) {
            limitScale(entity);
        }
        float now = entity.getHealth();
        if (Float.isNaN(accepted)) {
            if (Float.isFinite(now)) {
                accepted = now;
            } else {
                write(entity, entity.getMaxHealth());
            }
            return;
        }
        // NaN / 负无穷当成扣到 0 结算（负无穷放着不管，下一刻就走死亡流程）；正无穷当成回满
        if (!Float.isFinite(now) || now < accepted - EPSILON) {
            settleAndWrite(entity, guarded, Float.isNaN(now) ? 0F : now);
            return;
        }
        accepted = now;
        settleMaxHealth(entity, guarded);
    }

    /** 自己的机制改生命（复活、重立）：不结算，照原版夹到上限 */
    public void unguarded(LivingEntity entity, float health) {
        write(entity, Mth.clamp(health, 0F, entity.getMaxHealth()));
    }

    /** 生命还大于 0 时调 {@code die()}：不算死 */
    public boolean refusesDeath(LivingEntity entity) {
        return entity.getHealth() > 0F;
    }

    /**
     * 单次封顶、每秒桶乘多少（见类注释「限额跟着生命上限放大」）：认下了就用认下的；没认下照当前的生命上限算，
     * 出生满 {@value #SCALE_SETTLE_TICKS} 刻（客户端不认）时认下并存盘
     */
    public float limitScale(LivingEntity entity) {
        if (!Float.isNaN(limitScale)) {
            return limitScale;
        }
        CompoundTag data = entity.getPersistentData();
        if (data.contains(TAG_LIMIT_SCALE, Tag.TAG_FLOAT)) {
            float saved = data.getFloat(TAG_LIMIT_SCALE);
            limitScale = Float.isFinite(saved) ? Math.max(1F, saved) : 1F;
            return limitScale;
        }
        float live = liveScale(entity);
        if (entity.tickCount >= SCALE_SETTLE_TICKS && !entity.level().isClientSide) {
            limitScale = live;
            data.putFloat(TAG_LIMIT_SCALE, live);
        }
        return live;
    }

    /** 受这层结算保护的实体按 {@link #limitScale}；别的、还没建好结算的照当前生命上限算 */
    public static float limitScaleOf(LivingEntity entity) {
        BossHealthGuard guard = entity instanceof Guarded guarded ? guarded.healthGuard() : null;
        return guard != null ? guard.limitScale(entity) : liveScale(entity);
    }

    /** 当前生命上限 ÷ 基础值，最小 1 */
    private static float liveScale(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) {
            return 1F;
        }
        double base = instance.getBaseValue();
        double ratio = instance.getValue() / base;
        return base > 0D && ratio > 1D && Double.isFinite(ratio) ? (float) ratio : 1F;
    }

    /** 读档 / GameTest：从当前生命重新开始认 */
    public void reset() {
        accepted = Float.NaN;
        allowance = 0F;
        allowanceTick = Long.MIN_VALUE;
        directUsed = 0F;
        directWindow = Long.MIN_VALUE;
    }

    // ------------------------------------------------------------------

    /** 想把生命改成 wanted：往上照收（生命在上限之上时不再往上加），往下按规则结算出能掉到多少 */
    private void settleAndWrite(LivingEntity entity, Guarded guarded, float wanted) {
        float target = wanted;
        if (wanted < accepted) {
            long now = entity.level().getGameTime();
            // 伤害流程放行过的照收（打不打得动、夹多少，实体自己的 hurt 和伤害事件已经判过）
            float covered = 0F;
            if (allowanceTick == now && allowance > 0F) {
                covered = Math.min(accepted - wanted, allowance);
                allowance -= covered;
            }
            float afterHit = accepted - covered;
            // 多出来的那段先按下限截，再结算：打不动的那段不占封顶和桶
            float floor = Math.min(afterHit, Math.max(0F, guarded.guardFloor()));
            float limit = Math.max(wanted, floor);
            target = limit < afterHit ? afterHit - takeDirect(entity, guarded, afterHit - limit, covered, now) : afterHit;
        }
        float attribute = entity.getMaxHealth();
        float upper = Math.max(attribute, Math.min(target, accepted));
        float value = Mth.clamp(target, 0F, upper);
        if (!(Math.abs(value - entity.getHealth()) <= EPSILON)) {
            write(entity, value);
        } else {
            accepted = entity.getHealth();
        }
    }

    /** 绕开伤害流程的这段放行多少：和同一刻伤害流程放行过的合起来不超过单次封顶，再按直接掉血的窗口、每秒桶依次夹 */
    private float takeDirect(LivingEntity entity, Guarded guarded, float drop, float covered, long now) {
        BossDamageBudget.Limits limits = guarded.guardLimits();
        float room = drop;
        if (limits.perHit() > 0F) {
            long window = Math.floorDiv(now, DIRECT_WINDOW);
            if (directWindow != window) {
                directWindow = window;
                directUsed = 0F;
            }
            room = Math.min(room, Math.max(0F, Math.min(limits.perHit() - covered, limits.perHit() - directUsed)));
        }
        if (!(room > 0F)) {
            return 0F;
        }
        float granted = guarded.guardBudget().take(room, new BossDamageBudget.Limits(0F, limits.perSecond()), now);
        directUsed += granted;
        return granted;
    }

    /** 生命上限被压到当前生命以下：按规则一点点降到真实上限 */
    private void settleMaxHealth(LivingEntity entity, Guarded guarded) {
        float attribute = (float) entity.getAttributeValue(Attributes.MAX_HEALTH);
        float health = entity.getHealth();
        if (health > attribute + EPSILON) {
            float target = Math.max(attribute, health - takeDirect(entity, guarded, health - attribute, 0F, entity.level().getGameTime()));
            if (target < health - EPSILON) {
                write(entity, target);
            }
        }
    }

    /** 写生命数据（不经过 {@code setHealth}，可以留在上限之上）并认下 */
    private void write(LivingEntity entity, float value) {
        rawWrite(entity, value);
        accepted = entity.getHealth();
    }

    private void rawWrite(LivingEntity entity, float value) {
        writing++;
        try {
            entity.getEntityData().set(HEALTH, value);
        } finally {
            writing--;
        }
    }
}
