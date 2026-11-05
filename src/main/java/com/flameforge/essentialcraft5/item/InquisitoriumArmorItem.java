package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.IMRUResistanceItem;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.api.MRUVision;
import com.flameforge.essentialcraft5.item.armor.ArmorBonus;
import com.flameforge.essentialcraft5.item.armor.ArmorLook;
import com.flameforge.essentialcraft5.item.armor.ConfiguredArmorStats;
import com.flameforge.essentialcraft5.item.armor.ECArmorItem;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.matrix.MatrixResonance;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * 审判庭套装的一件（原作 ItemArmorEC，aType 2：magicArmorItems[8~11]）。注册与配置见 {@link ECInquisitorium}。
 * <ul>
 *   <li>属性：护甲 / 韧性 / 击退抗性按服务端配置 {@code general.inquisitorium_armor}，胸甲另有最大生命、靴子另有移速（四件都放这两条修饰符，
 *       没有的部位数额 0，热改后脱装不留残余，见 {@link ConfiguredArmorStats}）。原作 getAttributeModifiers 不调父类，护甲属性是 0，
 *       盔甲条按材料画出 40，实际靠 ISpecialArmor 每件吸收 0.4；这里护甲是真的原版属性。</li>
 *   <li>耐久照原作；第十三批 13b 起穿在玩家身上不掉（D14：这套的身份就是「不用养」，{@code keep_durability} 可关），别的生物穿着照常掉。
 *       原作 aType 2 的 damageArmor 就是 damageItem，照常掉。</li>
 *   <li>全套「审判庭之律」和同调阶效果在 {@link InquisitoriumArmorEvents}；提示框写 0 阶的四条，阶效果那几行由同调提示框画。</li>
 *   <li>MRU 抗性每件 1 / 1 / 1；头盔在 {@code essentialcraft5:mru_vision} 里（提示行由框架和这里各加一行）。</li>
 *   <li>提示框：原作写死在代码里的一句台词（改成翻译键）。第十批起生存里能做（每件要一个审判庭芯片），不再写「没有合成配方」。</li>
 * </ul>
 */
public class InquisitoriumArmorItem extends ECArmorItem implements IMRUResistanceItem {

    public static final ArmorLook LOOK = ArmorLook.modelArmorEC("special/armor/armorec3inquisitorium_0", "special/armor/armorec3inquisitorium_1");
    /** 生命 / 移速修饰符 UUID 用的套装名（{@link ConfiguredArmorStats#healthModifierId}） */
    public static final String STATS_KEY = "inquisitorium_armor";

    private final ConfiguredArmorStats stats;

    public InquisitoriumArmorItem(Type type, Properties properties) {
        super(ECInquisitorium.MATERIAL, type, properties, LOOK, ArmorBonus.none());
        this.stats = new ConfiguredArmorStats(type, STATS_KEY, true, ArmorBonus.none());
    }

    /** 这一件现在按配置算出的数：护甲 / 韧性 / 击退抗性每件一样；生命只有胸甲有、移速只有靴子有 */
    public static ConfiguredArmorStats.Piece piece(Type type, ECInquisitorium.Config.Values values) {
        return new ConfiguredArmorStats.Piece(values.armor(), values.toughness(), values.knockbackResistance(),
                type == Type.CHESTPLATE ? values.chestHealth() : 0D,
                type == Type.BOOTS ? values.bootsSpeed() : 0D);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != getEquipmentSlot()) {
            return super.getDefaultAttributeModifiers(slot);
        }
        return stats.modifiers(piece(getType(), ECInquisitorium.Config.values()));
    }

    @Override
    public float getResistance(ItemStack stack, MRUResistance.Type type) {
        return 1F;
    }

    /** 这件的台词键（原作 setDescription，tools/lang/hardcoded_strings.md L73~76） */
    public static String descriptionKey(Type type) {
        return switch (type) {
            case HELMET -> "tooltip.essentialcraft5.inquisitorium_helmet";
            case CHESTPLATE -> "tooltip.essentialcraft5.inquisitorium_chestplate";
            case LEGGINGS -> "tooltip.essentialcraft5.inquisitorium_leggings";
            case BOOTS -> "tooltip.essentialcraft5.inquisitorium_boots";
        };
    }

    /** 穿在玩家身上不掉耐久（D14，{@code keep_durability} 关掉时照原作掉）；别的生物照常掉 */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<T> onBroken) {
        return entity instanceof Player && ECInquisitorium.Config.law().keepDurability() ? 0 : amount;
    }

    /**
     * 不可修理：工作台修理配方、砂轮合并两件会丢掉同调阶和同调度（每阶要几万 MRU 加一批材料）。穿在玩家身上本来就不掉耐久，
     * 老存档里已经掉的耐久照样能进魔力修复器修（{@link ECTags#MRU_REPAIRABLE}，batch13.md §4.9）。
     */
    @Override
    public boolean isRepairable(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(descriptionKey(getType())).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        lawTooltip(tooltip);
        if (MRUVision.grantsVision(stack)) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.mru_vision.worn").withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    /**
     * 不掉耐久、全套「审判庭之律」四条（0 阶的数；同调阶换掉的数写在阶效果那几行里）。填 0 关掉的那条不写。
     * 定罪那行只要还在定罪（时长、上限都不是 0）就写：1 阶赦罪、2 阶增伤都靠它，0 阶增伤填 0 时只写「被定罪」那半句；
     * 秒数带一位小数（配置按刻填，30 刻是 1.5 秒）；对玩家的增伤和对怪不一样时（{@code pvp_effect_scale} 不是 1）另起一行写对玩家多多少。
     */
    public static void lawTooltip(List<Component> tooltip) {
        ECInquisitorium.Config.Law law = ECInquisitorium.Config.law();
        String prefix = "tooltip.essentialcraft5.inquisitorium_armor.";
        if (law.keepDurability()) {
            tooltip.add(Component.translatable(prefix + "keep_durability").withStyle(ChatFormatting.DARK_PURPLE));
        }
        tooltip.add(Component.translatable(prefix + "fullset").withStyle(ChatFormatting.GRAY));
        if (law.magicResistance() > 0D) {
            tooltip.add(Component.translatable(prefix + "fullset.magic", percent(law.magicResistance())).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        if (law.bossResistance() > 0D) {
            tooltip.add(Component.translatable(prefix + "fullset.boss", percent(law.bossResistance())).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        if (InquisitoriumArmorEvents.edictCut(law.edictReduction()) > 0) {
            tooltip.add(Component.translatable(prefix + "fullset.edict", percent(law.edictReduction())).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        if (law.convictionTicks() > 0 && law.convictionMaxTargets() > 0) {
            String seconds = MatrixResonance.decimal(law.convictionTicks() / 20D);
            if (!(law.convictionBonus() > 0D)) {
                tooltip.add(Component.translatable(prefix + "fullset.conviction.mark_only", seconds, law.convictionMaxTargets())
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                return;
            }
            tooltip.add(Component.translatable(prefix + "fullset.conviction", seconds, law.convictionMaxTargets(),
                    percent(law.convictionBonus())).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            double scale = Math.max(0D, AttunementConfig.pvpEffectScale());
            if (scale <= 0D) {
                tooltip.add(Component.translatable(prefix + "fullset.conviction.pvp_none").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            } else if (scale != 1D) {
                tooltip.add(Component.translatable(prefix + "fullset.conviction.pvp", percent(law.convictionBonus() * scale))
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        }
    }

    private static String percent(double fraction) {
        return Math.round(fraction * 100D) + "%";
    }
}
