package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.IMRUResistanceItem;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.api.MRUVision;
import com.flameforge.essentialcraft5.item.armor.ArmorBonus;
import com.flameforge.essentialcraft5.item.armor.ArmorLook;
import com.flameforge.essentialcraft5.item.armor.DyeableECArmorItem;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementStageLines;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.registry.ECWindGear;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 风之护甲（原作 ItemGenericArmor，ItemsCore L240~255）：可染色（框架 {@link DyeableECArmorItem}，没染过是贴图原色），四件都带 MRU 抗性。
 * <ul>
 *   <li>MRU 抗性每件 0.5 / 0.75 / 0.5（MRU 腐化 / 腐化辐射 / 海纳尔辐射，原作 ApiCore.setItemResistances(item, 0.5F, 0.75F, 0.5F)）；提示行由框架自动加。
 *       穿齐四件 MRU 腐化易伤 0.5。</li>
 *   <li>头盔能看见 MRU（原作 allowItemToSeeMRU）：进 {@code essentialcraft5:mru_vision} 标签（tools/tags/ecwindgear.json）。</li>
 *   <li>胸甲移速、靴子登高是 {@link ArmorBonus}（在 ECWindGear 里给）；护腿起跳加速度在 {@link WindGearEvents#onLivingJump}。</li>
 *   <li>全套「御风」（第十三批 13b，batch13.md §4.3、§4.4）：判定在 {@link #fullSetStage}，效果在 {@link WindGearEvents}、{@code player/WindPact}（偏转）、
 *       {@code client/WindArmorClient}（空中跳）。提示框按这一件的阶写全套那三样（偏转间隔、空中跳次数按 {@code deflectCooldown(阶)}、
 *       {@code airJumps(阶)} 现算，标题写明要四件都到这一阶，和全套取四件最低阶的判定一致）；同调提示框里各阶那一行也按配置填数
 *       （{@link #attunementStageLine}），服主改了每阶的列表或把空中跳填 0，上下两段说的是同一套数。</li>
 * </ul>
 * 原作的 getAttributeModifiers 覆写后没调父类，所以材质里的护甲值从没生效（实际护甲 0）；这里按 batch3.md E4 给护甲值，数值在 ECWindGear 的材料里。
 */
public class WindArmorItem extends DyeableECArmorItem implements IMRUResistanceItem, AttunementStageLines {

    public static final float MRU_CORRUPTION_RESISTANCE = 0.5F;
    public static final float CORRUPTION_RADIATION_RESISTANCE = 0.75F;
    public static final float HOANNA_RADIATION_RESISTANCE = 0.5F;

    public WindArmorItem(ArmorMaterial material, Type type, Properties properties, ArmorLook look, ArmorBonus bonus) {
        super(material, type, properties, look, bonus);
    }

    /**
     * 全套的同调阶：头、胸、腿、脚四个槽穿的都是风之护甲（各在自己的部位）时，取四件里最低的阶；没穿齐返回 -1。
     * 按类判断，不看家族标签：数据包往家族标签里加了别的物品，也不会让别的盔甲凑成风之护甲全套。两端都能算（盔甲和 NBT 都会同步）。
     */
    public static int fullSetStage(LivingEntity entity) {
        int min = Integer.MAX_VALUE;
        for (EquipmentSlot slot : Attunement.ARMOR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!(stack.getItem() instanceof WindArmorItem armor) || armor.getEquipmentSlot() != slot) {
                return -1;
            }
            min = Math.min(min, Attunement.stage(stack));
        }
        return min;
    }

    @Override
    public float getResistance(ItemStack stack, MRUResistance.Type type) {
        return switch (type) {
            case MRU_CORRUPTION -> MRU_CORRUPTION_RESISTANCE;
            case CORRUPTION_RADIATION -> CORRUPTION_RADIATION_RESISTANCE;
            case HOANNA_RADIATION -> HOANNA_RADIATION_RESISTANCE;
        };
    }

    /**
     * 不可修理：工作台修理配方、砂轮合并两件会丢掉同调。磨损了用魔力修复器修（{@link ECTags#MRU_REPAIRABLE}），
     * 或者在铁砧上两件合修（右边那件当材料吃掉）、靠经验修补；染色走的是原版染色配方，不受影响。
     */
    @Override
    public boolean isRepairable(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (MRUVision.grantsVision(stack)) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.mru_vision.worn").withStyle(ChatFormatting.DARK_AQUA));
        }
        if (getType() == Type.LEGGINGS) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.wind_armor.leggings").withStyle(ChatFormatting.DARK_AQUA));
        }
        setLines(tooltip, Attunement.stage(stack));
    }

    /**
     * 全套的三样，按 stage 阶写（这一件的阶；全套要四件都到这一阶才是这些数），数按现在的配置（客户端读到的是服务器同步来的值）；
     * 被配置关掉的那样不写。0 阶的标题不带阶数
     */
    public static void setLines(List<Component> tooltip, int stage) {
        ECWindGear.Config.Values values = ECWindGear.Config.values();
        tooltip.add((stage > 0 ? Component.translatable("tooltip.essentialcraft5.wind_armor.set_stage", stage)
                : Component.translatable("tooltip.essentialcraft5.wind_armor.set")).withStyle(ChatFormatting.GRAY));
        if (values.deflect()) {
            int cooldown = values.deflectCooldown(stage);
            // 间隔填 0 = 每一颗都偏开，写成「每 0 秒有一颗」读不通，换一句
            tooltip.add((cooldown > 0 ? Component.translatable("tooltip.essentialcraft5.wind_armor.set.deflect", seconds(cooldown))
                    : Component.translatable("tooltip.essentialcraft5.wind_armor.set.deflect_all")).withStyle(ChatFormatting.DARK_AQUA));
        }
        int jumps = values.airJumps(stage);
        if (jumps > 0) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.wind_armor.set.air_jump", jumps).withStyle(ChatFormatting.DARK_AQUA));
        }
        if (values.fallDamageMultiplier() < 1D) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.wind_armor.set.fall", percent(values.fallDamageMultiplier()))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    /**
     * 同调提示框里各阶那一行，数按配置：1 阶偏转间隔、2 阶空中跳次数和疾跑加速、3 阶偏转间隔和顺风（只有敌人射来的给，见
     * {@code WindGearEvents.tailwindSource}）；间隔填 0 时换成「每一颗都偏开」那句。
     * 某一阶的几样都被配置关掉（例：反作弊服把空中跳填 0、疾跑加速填 0）时那一行不写，只关掉一样时只写剩下的。
     */
    @Override
    @Nullable
    public Component attunementStageLine(ItemStack stack, int stage) {
        ECWindGear.Config.Values values = ECWindGear.Config.values();
        String prefix = "tooltip.essentialcraft5.attunement.wind_armor.stage_";
        return switch (stage) {
            case 1 -> {
                if (!values.deflect()) {
                    yield Component.translatable(prefix + "1.no_deflect");
                }
                // 间隔填 0 = 每一颗都偏开，同 setLines 换一句
                int cooldown = values.deflectCooldown(1);
                yield cooldown > 0 ? Component.translatable(prefix + "1", seconds(cooldown)) : Component.translatable(prefix + "1.deflect_all");
            }
            case 2 -> {
                int jumps = values.airJumps(2);
                boolean sprint = values.sprintSpeedBonus() > 0D;
                if (jumps > 0 && sprint) {
                    yield Component.translatable(prefix + "2", jumps, percent(values.sprintSpeedBonus()));
                }
                if (jumps > 0) {
                    yield Component.translatable(prefix + "2.jumps", jumps);
                }
                yield sprint ? Component.translatable(prefix + "2.sprint", percent(values.sprintSpeedBonus())) : null;
            }
            case 3 -> {
                if (!values.deflect()) {
                    // 顺风要偏开一颗才给，偏转关了这一阶什么都不加
                    yield null;
                }
                int cooldown = values.deflectCooldown(3);
                String deflect = seconds(cooldown);
                if (values.tailwindTicks() <= 0) {
                    yield cooldown > 0 ? Component.translatable(prefix + "3.no_tailwind", deflect)
                            : Component.translatable(prefix + "3.deflect_all_no_tailwind");
                }
                String tailwind = seconds(values.tailwindTicks());
                String speed = percent(values.tailwindSpeedBonus());
                String attackSpeed = percent(values.tailwindAttackSpeedBonus());
                yield cooldown > 0 ? Component.translatable(prefix + "3", deflect, tailwind, speed, attackSpeed)
                        : Component.translatable(prefix + "3.deflect_all", tailwind, speed, attackSpeed);
            }
            default -> null;
        };
    }

    /** 刻数写成秒（120 → 6、30 → 1.5） */
    public static String seconds(int ticks) {
        return ticks % 20 == 0 ? Integer.toString(ticks / 20) : Float.toString(ticks / 20F);
    }

    /** 倍数写成百分数（0.25 → 25） */
    public static String percent(double multiplier) {
        double value = Math.round(multiplier * 1000D) / 10D;
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
