package com.flameforge.essentialcraft5.gun;

import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * 枪械配置（第五批框架 F5-5）：SERVER 配置 {@code general.guns}，由 {@code ECGuns.init()} 登记。默认值照原作（D2 取 A）。
 * <ul>
 *   <li>{@code matrix_damage_per_damage}（默认 100，0~1000，0 = 关）：射线<b>打中生存 / 冒险玩家</b>就按枪的每点伤害加多少矩阵损伤，
 *       伤害生效没有都算（GUNS-4 修订：无敌帧、伤害被取消、被护甲吸收照加），攻击被 PvP / 队伍 / 领地拦下的不算（{@code RayHitGate}）。
 *       原作 {@code EntityMRURay.causeMRUDamage}：{@code floor(damage × 100)}，在伤害之前就加、被拦和创造模式也照加。</li>
 *   <li>{@code player_damage_multiplier}（默认 1.0，0~10）：射线打玩家的伤害倍率（打生物不受影响）；<b>不影响矩阵损伤</b>，0 = 只损伤矩阵、不掉血。</li>
 *   <li>{@code max_targets_per_shot}（默认 32，1~256）：一发最多打中几个生物（原作一线全打、不封顶）。</li>
 * </ul>
 * 读值一律走这里的静态方法（配置没加载时返回默认值）。GameTest 用 {@link #setTestOverrides} 临时覆盖，<b>不许用 ConfigValue.set</b>
 * （服务端配置自动存盘、文件监视器后台重读会冲掉刚设的值），覆盖值是全局的：只在同一段同步代码里设、测、置回 null。
 */
public final class GunConfig {

    public static final int DEFAULT_MATRIX_DAMAGE_PER_DAMAGE = 100;
    public static final double DEFAULT_PLAYER_DAMAGE_MULTIPLIER = 1.0D;
    public static final int DEFAULT_MAX_TARGETS_PER_SHOT = 32;

    public static ForgeConfigSpec.IntValue matrixDamagePerDamage;
    public static ForgeConfigSpec.DoubleValue playerDamageMultiplier;
    public static ForgeConfigSpec.IntValue maxTargetsPerShot;

    @Nullable
    private static Integer testMatrixDamagePerDamage;
    @Nullable
    private static Double testPlayerDamageMultiplier;
    @Nullable
    private static Integer testMaxTargetsPerShot;

    private GunConfig() {
    }

    public static void define(ForgeConfigSpec.Builder builder) {
        builder.comment("MRU guns.", "MRU 射线枪").push("guns");
        matrixDamagePerDamage = builder.comment(
                        "Matrix damage added to a survival / adventure player per point of the gun's damage whenever a ray hits them, "
                                + "even if the damage itself does not go through (invulnerability frames, cancelled, absorbed by armor). "
                                + "Hits stopped by PvP, teams or claims do not count. 0 = off.",
                        "射线打中生存 / 冒险玩家时，按枪的每点伤害加多少矩阵损伤；伤害生效没有都算（无敌时间、被取消、被护甲吸收），"
                                + "被 PvP、队伍、领地拦下的不算；0 = 关")
                .defineInRange("matrix_damage_per_damage", DEFAULT_MATRIX_DAMAGE_PER_DAMAGE, 0, 1000);
        playerDamageMultiplier = builder.comment(
                        "Damage multiplier when a ray hits a player (mobs are not affected). Matrix damage does not use it: 0 = rays only damage the matrix.",
                        "射线打玩家的伤害倍率（打生物不受影响）；矩阵损伤不乘它：0 = 只损伤矩阵、不掉血")
                .defineInRange("player_damage_multiplier", DEFAULT_PLAYER_DAMAGE_MULTIPLIER, 0D, 10D);
        maxTargetsPerShot = builder.comment("At most this many creatures are hit by one shot (nearest first).",
                        "一发最多打中几个生物（近的先）")
                .defineInRange("max_targets_per_shot", DEFAULT_MAX_TARGETS_PER_SHOT, 1, 256);
        // 第十批 10b：透镜的命中效果（腐蚀 / 冻结 / 驱散）放在同一个小节
        LensEffects.Config.define(builder);
        builder.pop();
    }

    /** 每点伤害加的矩阵损伤（0 = 不加） */
    public static int matrixDamagePerDamage() {
        if (testMatrixDamagePerDamage != null) {
            return testMatrixDamagePerDamage;
        }
        return matrixDamagePerDamage == null ? DEFAULT_MATRIX_DAMAGE_PER_DAMAGE : ECConfig.get(matrixDamagePerDamage);
    }

    /** 射线打玩家的伤害倍率 */
    public static double playerDamageMultiplier() {
        if (testPlayerDamageMultiplier != null) {
            return testPlayerDamageMultiplier;
        }
        return playerDamageMultiplier == null ? DEFAULT_PLAYER_DAMAGE_MULTIPLIER : ECConfig.get(playerDamageMultiplier);
    }

    /** 一发最多打中几个生物 */
    public static int maxTargetsPerShot() {
        if (testMaxTargetsPerShot != null) {
            return testMaxTargetsPerShot;
        }
        return maxTargetsPerShot == null ? DEFAULT_MAX_TARGETS_PER_SHOT : ECConfig.get(maxTargetsPerShot);
    }

    /** GameTest 用：临时覆盖三项（传 null = 按配置），测完必须置回 null（见类注释） */
    public static void setTestOverrides(@Nullable Integer matrixDamagePerDamage, @Nullable Double playerDamageMultiplier,
                                        @Nullable Integer maxTargetsPerShot) {
        testMatrixDamagePerDamage = matrixDamagePerDamage;
        testPlayerDamageMultiplier = playerDamageMultiplier;
        testMaxTargetsPerShot = maxTargetsPerShot;
    }
}
