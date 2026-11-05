package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.IWindAffinityItem;
import com.flameforge.essentialcraft5.api.MRUVision;
import com.flameforge.essentialcraft5.item.armor.ArmorLook;
import com.flameforge.essentialcraft5.item.armor.ECArmorItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 风法师套装（原作 ItemArmorEC 的 magicArmorItems[12~15]：helm_wind / chest_wind / legs_wind / boots_wind，aType 3）：
 * 兜帽 wind_mage_hood、长袍 wind_mage_robe、护腿 wind_mage_leggings、靴子 wind_mage_boots。只从大法师身上掉，没有配方。
 * <p>
 * 数值（原作 ItemsCore L171~182、WindRelations L32~33）：
 * <ul>
 *   <li>材料是原版皮革（{@link ArmorMaterials#LEATHER}）：护甲值 1 / 3 / 2 / 1、附魔能力 15、韧性 0、用皮革修；耐久 128 / 384 / 256 / 128（原作 setMaxDamage）。
 *       原作靠 1.12 的 ISpecialArmor 按「皮革护甲值 / 25」减伤（合计 7/25），和原版皮革一样，1.20 直接用原版护甲值（同二批 D13 的做法）。</li>
 *   <li>每件风之好感系数 +0.23（{@link IWindAffinityItem}，由框架 {@code WindAffinity.coefficient} 读）：基础 0.1，穿齐四件 1.02。</li>
 *   <li>兜帽能隔墙看见 MRU 聚集体（原作 allowItemToSeeMRU）：在 tools/tags/ecwindmage.json 里加进 {@code essentialcraft5:mru_vision}，提示行按标签显示。</li>
 *   <li>穿在身上的模型是原作 ModelArmorEC（{@link ArmorLook#modelArmorEC}，贴图 special/armor/armorec3wind_0 / _1，128×64）；
 *       物品图标是 3D 盔甲（框架 {@code ECArmorItem.icon3d}）。不进 minecraft:trimmable_armor（纹饰按 64×32 的 UV 排，套在这个模型上会错位，同魔力防护套装）。</li>
 * </ul>
 * 原作还对接了别的魔法模组的护目镜、减耗接口，不做联动，不移植。
 */
public class WindMageArmorItem extends ECArmorItem implements IWindAffinityItem {

    /** 原作 WindRelations.getPlayerWindRevModifier 里每件 +0.23F */
    public static final float WIND_AFFINITY = 0.23F;
    public static final ArmorLook LOOK = ArmorLook.modelArmorEC("special/armor/armorec3wind_0", "special/armor/armorec3wind_1");

    public WindMageArmorItem(Type type, Properties properties) {
        super(ArmorMaterials.LEATHER, type, properties, LOOK);
    }

    @Override
    public float getWindAffinity(ItemStack stack, Player player) {
        return WIND_AFFINITY;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.essentialcraft5.wind_mage_armor.affinity", "+" + WIND_AFFINITY)
                .withStyle(ChatFormatting.AQUA));
        if (MRUVision.grantsVision(stack)) {
            // 键在 ECGear 的 tools/lang/keys/ecgear.json（二批，已集成）
            tooltip.add(Component.translatable("tooltip.essentialcraft5.mru_vision.worn").withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
