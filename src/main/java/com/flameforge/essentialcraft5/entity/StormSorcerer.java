package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECWildMobs;
import com.flameforge.essentialcraft5.util.ECLightning;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToDoubleFunction;

/**
 * 风暴术士（风法师的第 4 种，{@link WindMageEntity#STORM_SORCERER}；13c-M1，batch13.md §5.2）的规矩都在这里，风法师类里只留几处接线：
 * <ul>
 *   <li><b>哪来的</b>：只在自然刷出时掷（来源公平：自然、事件）——主世界雷雨时风法师有 {@code thunder_chance}（默认 1/5）是风暴术士，
 *       海纳尔里不看天气按 {@code hoanna_chance}（默认 10%），再乘 {@link #addChanceMultiplier} 登记的倍数（13d 按需接，比如地脉风暴时提高）。
 *       刷怪蛋、刷怪笼、风之号角、指令出来的不会变成风暴术士；老存档里的 type 0~2 照旧（读档只夹到 0~3）。</li>
 *   <li><b>属性</b>：生命 {@code max_health}（50）、护甲 {@code armor}（2），用固定 UUID 的属性修饰符挂在风法师的基础值上，读档先摘再挂，不叠两层。</li>
 *   <li><b>疾风弹</b>：远程攻击（节奏同风法师）换成 {@link StormGustEntity}，打中击退。</li>
 *   <li><b>预警落雷</b>：每 {@code lightning_cooldown_ticks} 刻最多一次，目标在 16 格内、看得见、是技能目标时，在它脚下标一个圈
 *       （电火花粒子 + 声音），{@code lightning_warning_ticks}（20 刻 = 1 秒）后在那一格落一道只有画面的闪电（{@link ECLightning#visual}，
 *       不点火、不劈生物、不碰方块），圈里（水平 {@code lightning_radius} 格、上下 3 格）的技能目标各挨 {@code lightning_damage} 点
 *       {@code aberrant_strike}（无视护甲、闪不掉，保护附魔和抗性照常减）。走开就躲过去了。术士死了、没了，这道雷就不落。</li>
 *   <li><b>名字</b>：{@code entity.essentialcraft5.wind_mage.storm}；掉落表 entities/wind_mage_storm（受困之风 25% 带「风暴余息」标记，见
 *       {@code api/WindAffinity#isStormResidue}）。</li>
 * </ul>
 */
public final class StormSorcerer {

    public static final UUID HEALTH_ID = UUID.fromString("5b1e0c0d-13c1-4f6e-9a77-57c0a1e5f001");
    public static final UUID ARMOR_ID = UUID.fromString("5b1e0c0d-13c1-4f6e-9a77-57c0a1e5f002");
    private static final String MODIFIER_NAME = "essentialcraft5 storm sorcerer";
    /** 落雷的出手距离（格） */
    public static final double LIGHTNING_RANGE = 16D;
    /** 落雷圈的上下范围（格） */
    public static final double LIGHTNING_HEIGHT = 3D;

    private static final List<ToDoubleFunction<WindMageEntity>> CHANCE_MULTIPLIERS = new CopyOnWriteArrayList<>();

    private StormSorcerer() {
    }

    // ------------------------------------------------------------------ 刷出

    /** 登记一个几率倍数（13d：海纳尔天灾 / 地脉风暴时调高之类）；几项相乘 */
    public static void addChanceMultiplier(ToDoubleFunction<WindMageEntity> multiplier) {
        CHANCE_MULTIPLIERS.add(multiplier);
    }

    public static void removeChanceMultiplier(ToDoubleFunction<WindMageEntity> multiplier) {
        CHANCE_MULTIPLIERS.remove(multiplier);
    }

    /** 这一只风法师变成风暴术士的几率：海纳尔按 {@code hoanna_chance}，别处雷雨时按 {@code thunder_chance}、不打雷是 0；再乘登记的倍数 */
    public static double chance(@Nullable WindMageEntity mage, boolean hoanna, boolean thundering) {
        double chance = hoanna ? ECWildMobs.Config.stormHoannaChance() : thundering ? ECWildMobs.Config.stormThunderChance() : 0D;
        if (mage != null) {
            for (ToDoubleFunction<WindMageEntity> multiplier : CHANCE_MULTIPLIERS) {
                chance *= multiplier.applyAsDouble(mage);
            }
        }
        return Mth.clamp(chance, 0D, 1D);
    }

    /** 生成初始化时掷（{@link WindMageEntity#finalizeSpawn} 调）：来源是自然或事件才掷 */
    public static boolean roll(WindMageEntity mage, ServerLevelAccessor level, RandomSource random) {
        return roll(mage, ECHoanna.isHoanna(level.getLevel()), level.getLevel().isThundering(), random);
    }

    /** 同上，海纳尔、打雷由调用方给（测试不去动全局天气） */
    public static boolean roll(WindMageEntity mage, boolean hoanna, boolean thundering, RandomSource random) {
        SpawnOrigin.Origin origin = SpawnOrigin.of(mage);
        if (origin != SpawnOrigin.Origin.NATURAL && origin != SpawnOrigin.Origin.EVENT) {
            return false;
        }
        double chance = chance(mage, hoanna, thundering);
        return chance > 0D && random.nextDouble() < chance;
    }

    // ------------------------------------------------------------------ 属性

    /**
     * 按类型挂上 / 摘掉风暴术士的生命和护甲修饰符（先按 UUID 摘掉；是风暴术士再按当前配置挂上）。
     * refill = true 时（刚生成成风暴术士）回满血；读档时传 false，生命照存档。
     */
    public static void applyModifiers(WindMageEntity mage, boolean storm, boolean refill) {
        AttributeInstance health = mage.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance armor = mage.getAttribute(Attributes.ARMOR);
        if (health != null) {
            health.removeModifier(HEALTH_ID);
            double extra = ECWildMobs.Config.stormHealth() - health.getBaseValue();
            if (storm && extra != 0D) {
                health.addPermanentModifier(new AttributeModifier(HEALTH_ID, MODIFIER_NAME, extra, AttributeModifier.Operation.ADDITION));
            }
        }
        if (armor != null) {
            armor.removeModifier(ARMOR_ID);
            double extra = ECWildMobs.Config.stormArmor();
            if (storm && extra != 0D) {
                armor.addPermanentModifier(new AttributeModifier(ARMOR_ID, MODIFIER_NAME, extra, AttributeModifier.Operation.ADDITION));
            }
        }
        if (refill && storm) {
            mage.setHealth(mage.getMaxHealth());
        } else if (mage.getHealth() > mage.getMaxHealth()) {
            mage.setHealth(mage.getMaxHealth());
        }
    }

    // ------------------------------------------------------------------ 疾风弹

    /** 朝 target 射一发疾风弹（替代 MRU 箭，瞄法同风法师） */
    public static StormGustEntity shootGust(WindMageEntity mage, LivingEntity target) {
        StormGustEntity gust = new StormGustEntity(mage.level(), mage);
        double dx = target.getX() - mage.getX();
        double dy = target.getY(0.5D) - gust.getY();
        double dz = target.getZ() - mage.getZ();
        gust.shoot(dx, dy, dz, StormGustEntity.SPEED, 14 - mage.level().getDifficulty().getId() * 4);
        mage.level().addFreshEntity(gust);
        mage.playSound(ECWildMobs.STORM_GUST_SHOOT.get(), 1F, 1F);
        return gust;
    }

    // ------------------------------------------------------------------ 预警落雷

    /** AI 每刻调：冷却到了、目标合适就标一个落点 */
    public static void aiTick(WindMageEntity mage) {
        if (mage.lightningCooldown > 0) {
            mage.lightningCooldown--;
            return;
        }
        LivingEntity target = mage.getTarget();
        if (mage.strikeAt != null || target == null || ECWildMobs.Config.stormLightningDamage() <= 0D) {
            return;
        }
        if (mage.distanceToSqr(target) <= LIGHTNING_RANGE * LIGHTNING_RANGE && mage.getSensing().hasLineOfSight(target)
                && ECMonster.isSkillTarget(mage, target)) {
            mark(mage, target);
        }
    }

    /** 在 target 脚下标落点（不看冷却；测试、展示厅也用）。返回 false = 目标不是技能目标或这一招关着 */
    public static boolean mark(WindMageEntity mage, LivingEntity target) {
        if (!(mage.level() instanceof ServerLevel level) || !ECMonster.isSkillTarget(mage, target) || ECWildMobs.Config.stormLightningDamage() <= 0D) {
            return false;
        }
        mage.strikeAt = target.position();
        mage.strikeTimer = ECWildMobs.Config.stormLightningWarning();
        mage.lightningCooldown = ECWildMobs.Config.stormLightningCooldown();
        level.playSound(null, target.getX(), target.getY(), target.getZ(), ECWildMobs.STORM_MARK.get(), SoundSource.HOSTILE, 1F, 1F);
        warn(level, mage.strikeAt);
        return true;
    }

    /** 实体每刻调（不看 AI，NoAI 的也照走）：倒计时、画预警圈、到点落雷。术士倒下了（死亡动画那 20 刻也算）这道雷作废 */
    public static void tickStrike(WindMageEntity mage) {
        if (mage.strikeAt == null || !(mage.level() instanceof ServerLevel level)) {
            return;
        }
        if (!mage.isAlive()) {
            mage.strikeAt = null;
            return;
        }
        if (--mage.strikeTimer > 0) {
            if (mage.strikeTimer % 4 == 0) {
                warn(level, mage.strikeAt);
            }
            return;
        }
        Vec3 at = mage.strikeAt;
        mage.strikeAt = null;
        strike(mage, level, at);
    }

    /** 落雷：只有画面的闪电 + 圈里的技能目标挨 {@code aberrant_strike}。返回挨了的个数 */
    public static int strike(WindMageEntity mage, ServerLevel level, Vec3 at) {
        ECLightning.visual(level, at);
        float damage = (float) ECWildMobs.Config.stormLightningDamage();
        double radius = ECWildMobs.Config.stormLightningRadius();
        AABB box = new AABB(at.x - radius, at.y - LIGHTNING_HEIGHT, at.z - radius, at.x + radius, at.y + LIGHTNING_HEIGHT, at.z + radius);
        int hit = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                living -> horizontalDistSq(living.position(), at) <= radius * radius && ECMonster.isSkillTarget(mage, living))) {
            if (damage > 0F && target.hurt(ECDamageTypes.aberrantStrike(mage), damage)) {
                hit++;
            }
        }
        return hit;
    }

    private static double horizontalDistSq(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    /** 预警圈：落点一圈电火花 */
    private static void warn(ServerLevel level, Vec3 at) {
        double radius = ECWildMobs.Config.stormLightningRadius();
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2D / 12D;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x + Math.cos(a) * radius, at.y + 0.1D, at.z + Math.sin(a) * radius,
                    1, 0D, 0D, 0D, 0D);
        }
    }

    /** 测试、展示厅：现在有没有标着的落点 */
    @Nullable
    public static Vec3 pendingStrike(WindMageEntity mage) {
        return mage.strikeAt;
    }
}
