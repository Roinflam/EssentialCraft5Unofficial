package com.flameforge.essentialcraft5.effect;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.hoanna.HoannaHazardsConfig;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 海纳尔辐射（原作 {@code PotionRadiation}，70 行；第六批 6a M4，batch6a.md §5.4）：压生命上限的算法，以及「辐射」的名字和图标。
 * <p>
 * 这个效果<b>不再挂到玩家身上</b>：原作按剂量挂一个无限时长的药水效果，不死图腾、/effect clear、净化类饰品都能去掉它，
 * 「免疫负面效果」的模组还能直接拦下。现在由 {@code hoanna/Radiation#pulse} 每 tick 按剂量直接判，图标画在快捷栏左边
 * （{@code client/AfflictionHud}）。效果仍然注册着：旧存档里身上带着它的玩家读档不报错（下一次结算撤掉），HUD 也借它的图标和名字。
 * <p>
 * 每 tick（放大值 &gt; 0 时，原作 {@code isReady} 恒 true、performEffect 里判 amp）以 {@code nextInt(16) < 放大值} 的概率把生命压到上限
 * {@link #healthCap}（判定用原放大值，戴 δ 指环时压的级数减半，原作 L25~48 同样先判后减半）。
 * 「辐射」（不到 1 万）不压；1 万起压低的级数 = 名字上的罗马数字（辐射 II 压 2 级），戴 δ 压 {@code 放大值 / 2 + 1} 级（{@link #capLevels}）。
 * <p>
 * 每级压低多少：原作是 1 点（{@code (int) 最大生命 − (放大值 + 1)}）；2026-10-13 起改成当前最大生命的
 * {@code general.radiation.cap_loss_percent_per_level}（默认 5%），合计最多 {@code max_cap_loss_percent}（默认一半），上限最低 1 点
 * （{@link #capLossFraction}）。整合包里玩家生命常常是几百上千，每级 1 点等于没压；20 点生命时 5% 正好是 1 点，和原作一样。
 * 这是 setHealth 类机制（照原作不改）：不走伤害流程、护甲和抗性提升挡不住。
 */
public class RadiationEffect extends MobEffect {

    /** 原作 new PotionRadiation(true, 0x660066) */
    public static final int COLOR = 0x660066;
    /** 提示框：说明、剂量与每秒变化、生命上限、最后一行 */
    public static final String DESC_KEY = "effect." + EssentialCraft5.MODID + ".radiation.desc";
    public static final String DOSE_KEY = "effect." + EssentialCraft5.MODID + ".radiation.dose";
    public static final String CAP_KEY = "effect." + EssentialCraft5.MODID + ".radiation.cap";
    public static final String FOOTER_KEY = "effect." + EssentialCraft5.MODID + ".radiation.footer";
    /** 原作 performEffect：{@code rand.nextInt(16) < amplifier} */
    public static final int HIT_BOUND = 16;

    public RadiationEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    /** 旧存档里残留的实例什么都不做（压上限由 {@code Radiation#pulse} 按剂量判，不然会判两遍） */
    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false;
    }

    /** 这一 tick 压不压上限：{@code nextInt(16) < 放大值} */
    public static boolean hits(RandomSource random, int amplifier) {
        return amplifier > 0 && random.nextInt(HIT_BOUND) < amplifier;
    }

    /** 判中以后：生命超过上限就压到上限（戴 δ 指环的玩家压的级数减半） */
    public static void capHealth(LivingEntity entity, int amplifier) {
        boolean halved = entity instanceof Player && CuriosCompat.has(entity, ECBaubles.HALVES_RADIATION);
        float cap = healthCap(entity.getMaxHealth(), amplifier, halved);
        if (entity.getHealth() > cap) {
            entity.setHealth(cap);
        }
    }

    /**
     * 放大值 amp 时压低几级：0 级（「辐射」，不到 1 万）不压，否则 放大值 + 1（= 效果名上的罗马数字）；
     * halved = 戴着 δ 指环：放大值 / 2 + 1（辐射 II 压 1 级、辐射 IV 压 2 级）。不能写成「按 放大值 / 2 算再 + 1 判 0 级」：
     * 放大值 1 时 1 / 2 = 0，照样压 1 级（判中用的是原放大值）。
     */
    public static int capLevels(int amplifier, boolean halved) {
        if (amplifier <= 0) {
            return 0;
        }
        return (halved ? amplifier / 2 : amplifier) + 1;
    }

    /** 压低最大生命的比例（按现在的配置）：{@code min(合计上限, 每级比例 × 级数)} */
    public static double capLossFraction(int amplifier, boolean halved) {
        return capLossFraction(capLevels(amplifier, halved), HoannaHazardsConfig.capLossPerLevel(), HoannaHazardsConfig.maxCapLoss());
    }

    /** {@code min(max, perLevel × levels)}，夹在 [0, 1]（纯函数，GameTest 测） */
    public static double capLossFraction(int levels, double perLevel, double max) {
        if (levels <= 0 || !(perLevel > 0D) || !(max > 0D)) {
            return 0D;
        }
        return Math.min(Math.min(1D, max), Math.min(1D, perLevel * levels));
    }

    /** 生命上限：{@code max(1, 最大生命 × (1 − 压低的比例))}（按现在的配置） */
    public static float healthCap(float maxHealth, int amplifier, boolean halved) {
        return healthCapByFraction(maxHealth, capLossFraction(amplifier, halved));
    }

    /** {@code max(1, 最大生命 × (1 − lossFraction))}（纯函数；不和上面同名，免得 {@code healthCap(最大生命, 放大值)} 被当成比例调到这里） */
    public static float healthCapByFraction(float maxHealth, double lossFraction) {
        double loss = Math.max(0D, Math.min(1D, lossFraction));
        return (float) Math.max(1D, maxHealth * (1D - loss));
    }

    /** 背包文字「上限 −N%」的 N：压低的比例 × 100，最多一位小数、去掉多余的 0（10、7.5）；0 级返回 null */
    @Nullable
    public static String capLossPercentText(int amplifier, boolean halved) {
        double percent = capLossFraction(amplifier, halved) * 100D;
        if (!(percent > 0D)) {
            return null;
        }
        return BigDecimal.valueOf(percent).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** 牛奶解不了（每次返回新表：Forge 会往 MobEffectInstance 的表里加东西） */
    @Override
    public List<ItemStack> getCurativeItems() {
        return new ArrayList<>();
    }
}
