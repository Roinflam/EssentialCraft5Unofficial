package com.flameforge.essentialcraft5.item.attunement;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * 一件同调装备这一阶的词条表（tools/plan/gear_growth.md 第 1 节）：{@link #of} 按家族查配置现算，{@link #next} 给提示框的下一阶预告。
 * 服务端结算（{@link GearCombatEvents}）和客户端提示框共用同一份算法，客户端读到的是服务端同步来的配置，两边的数对得上。
 * <p>
 * 枪这条线带 {@link GearStat#DAMAGE} = 阶 × {@code weapon.damage_per_stage}（刀剑每阶的攻击加在属性栏上，见 {@link AttunementBonuses}，不在表里）；
 * 各家族另外的词条由 {@link #register} 登记的填表函数给
 * （本模组的三条武器线在这里登记，盔甲在各自模块里登记）。战痕的加成另算（{@link #scarDamage}），不混进表里，提示框分开写。
 * <p>
 * 满阶觉醒（终审、元素爆发、过载射线……）用 {@link #registerAwakening} 登记名字和说明，提示框照登记的写。
 */
public final class GearStatSheet {

    /** 往表里填这一阶的词条 */
    @FunctionalInterface
    public interface Filler {
        void fill(ItemStack stack, int stage, EnumMap<GearStat, Double> out);
    }

    /**
     * 一个觉醒：几阶解锁、名字和说明的语言键前缀（{@code <key>} 是名字、{@code <key>.desc} 是说明），说明的参数按配置现算。
     */
    public record Awakening(IntSupplier stage, String key, Function<ItemStack, Object[]> args) {

        public int unlockStage() {
            return stage.getAsInt();
        }
    }

    private static final Map<ResourceLocation, Filler> FILLERS = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<ResourceLocation, List<Awakening>> AWAKENINGS = Collections.synchronizedMap(new LinkedHashMap<>());

    public static final ResourceLocation EMBER_SWORD = EssentialCraft5.id("ember_sword");
    public static final ResourceLocation VERDICT_BLADE = EssentialCraft5.id("verdict_blade");
    public static final ResourceLocation MRU_GUN = EssentialCraft5.id("mru_gun");

    static {
        register(EMBER_SWORD, (stack, stage, out) -> put(out, GearStat.LIFESTEAL, GearGrowthConfig.emberLifesteal(stage)));
        register(VERDICT_BLADE, (stack, stage, out) -> {
            put(out, GearStat.LIFESTEAL, GearGrowthConfig.verdictLifesteal(stage));
            put(out, GearStat.ARMOR_PEN, GearGrowthConfig.verdictArmorPen(stage));
            put(out, GearStat.COOLDOWN, GearGrowthConfig.verdictJudgmentCdr(stage));
            if (stage >= GearGrowthConfig.executeStage()) {
                put(out, GearStat.EXECUTE, GearGrowthConfig.executeBonus());
            }
        });
        register(MRU_GUN, (stack, stage, out) -> {
            put(out, GearStat.MRU_DISCOUNT, GearGrowthConfig.gunMruDiscount(stage));
            put(out, GearStat.ARMOR_PEN, GearGrowthConfig.gunArmorPen(stage));
            put(out, GearStat.COOLDOWN, GearGrowthConfig.gunReloadCdr(stage));
        });
        String prefix = "tooltip." + EssentialCraft5.MODID + ".gear_awakening.";
        registerAwakening(VERDICT_BLADE, new Awakening(GearGrowthConfig::finalVerdictStage, prefix + "final_verdict",
                stack -> new Object[]{AttunementStageLines.seconds(GearGrowthConfig.finalVerdictMarkTicks()),
                        AttunementStageLines.number(GearGrowthConfig.finalVerdictPercent()),
                        AttunementStageLines.number(GearGrowthConfig.finalVerdictCap()),
                        AttunementStageLines.percent(GearGrowthConfig.finalVerdictBossPool())}));
        registerAwakening(EMBER_SWORD, new Awakening(GearGrowthConfig::burstStage, prefix + "element_burst",
                stack -> new Object[]{AttunementStageLines.percent(GearGrowthConfig.burstChance()),
                        AttunementStageLines.number(GearGrowthConfig.burstRadius()),
                        AttunementStageLines.seconds(GearGrowthConfig.burstCooldown())}));
        registerAwakening(MRU_GUN, new Awakening(GearGrowthConfig::overloadStage, prefix + "overload",
                stack -> new Object[]{GearGrowthConfig.overloadShots(), GearGrowthConfig.overloadShotsSingle(),
                        AttunementStageLines.percent(GearGrowthConfig.overloadRatio()),
                        AttunementStageLines.number(GearGrowthConfig.overloadRadius())}));
    }

    private GearStatSheet() {
    }

    /** 登记一个家族的填表函数（重复登记同一个家族时后登记的覆盖前面的） */
    public static void register(ResourceLocation family, Filler filler) {
        FILLERS.put(family, filler);
    }

    /** 登记一个家族的觉醒（同一家族可以有几个） */
    public static void registerAwakening(ResourceLocation family, Awakening awakening) {
        AWAKENINGS.computeIfAbsent(family, id -> Collections.synchronizedList(new ArrayList<>())).add(awakening);
    }

    /** 这个家族登记的觉醒（没有是空表） */
    public static List<Awakening> awakenings(AttunementFamily family) {
        List<Awakening> list = AWAKENINGS.get(family.id());
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            return List.copyOf(list);
        }
    }

    private static void put(EnumMap<GearStat, Double> out, GearStat stat, double value) {
        if (value > 0D && Double.isFinite(value)) {
            out.merge(stat, value, Double::sum);
        }
    }

    /** 这件这一阶的词条（不是同调装备是空表；不含战痕） */
    public static EnumMap<GearStat, Double> of(ItemStack stack) {
        AttunementFamily family = AttunementFamily.of(stack);
        return family == null ? new EnumMap<>(GearStat.class) : at(stack, family, Attunement.stage(stack));
    }

    /** 这件到下一阶时的词条（已经最高阶返回 null） */
    @Nullable
    public static EnumMap<GearStat, Double> next(ItemStack stack) {
        AttunementFamily family = AttunementFamily.of(stack);
        if (family == null) {
            return null;
        }
        int stage = Attunement.stage(stack);
        return stage >= family.maxStage() ? null : at(stack, family, stage + 1);
    }

    /** 这件在 stage 阶时的词条 */
    public static EnumMap<GearStat, Double> at(ItemStack stack, AttunementFamily family, int stage) {
        EnumMap<GearStat, Double> out = new EnumMap<>(GearStat.class);
        if (family.kind() == AttunementFamily.Kind.WEAPON && family.line() == AttunementFamily.Line.GUN) {
            put(out, GearStat.DAMAGE, GearGrowthConfig.weaponDamagePerStage() * stage);
        }
        Filler filler = FILLERS.get(family.id());
        if (filler != null) {
            filler.fill(stack, stage, out);
        }
        return out;
    }

    /** 表里某一项（没有是 0） */
    public static double get(ItemStack stack, GearStat stat) {
        Double value = of(stack).get(stat);
        return value == null ? 0D : value;
    }

    /** 武器的战痕增伤：等级 × {@code scars.weapon_per_level}（盔甲、不是同调装备是 0） */
    public static double scarDamage(ItemStack stack) {
        AttunementFamily family = AttunementFamily.of(stack);
        if (family == null || family.kind() != AttunementFamily.Kind.WEAPON) {
            return 0D;
        }
        return BattleScars.level(stack) * Math.max(0D, GearGrowthConfig.scarWeaponPerLevel());
    }
}
