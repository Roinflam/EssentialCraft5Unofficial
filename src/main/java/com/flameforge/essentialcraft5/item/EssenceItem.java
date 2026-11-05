package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.IElementalCrystal;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 精华（火焰 / 奔流 / 大地 / 狂风精华）。对应原作 ItemEssence 的 16 个 meta：meta = 稀有度 × 4 + 元素（0 火、1 水、2 地、3 风）。
 * 1.20.1 拆成 16 个物品（{@code common_fire_essence} …… {@code exceptional_air_essence}），同一元素的四个稀有度同名，
 * 照原作靠名字颜色（Rarity：白、黄、青、品红）和提示行「稀有度：……」区分。
 * <p>
 * 来源只有水晶提取器；用处：水晶控制器（定向改水晶的元素）、元素燃料。
 * 移植改动：最大堆叠 16 → 64（batch2 D2）。原作提取器一格塞到 64，玩家实际能拿到一格 64 个，照 64 才保住原作的产量。
 */
public class EssenceItem extends Item {

    /** 稀有度名，下标就是原作 meta / 4；也是注册名前缀和提示行的翻译键后缀 */
    public static final List<String> RARITY_NAMES = List.of("common", "uncommon", "rare", "exceptional");
    /** 原作 getRarity：0 普通、1 罕见、2 稀有、3 卓越（EPIC） */
    private static final Rarity[] RARITIES = {Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC};
    /** 原作提示行的颜色码：§f / §e / §b / §d */
    private static final ChatFormatting[] RARITY_COLORS = {
            ChatFormatting.WHITE, ChatFormatting.YELLOW, ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE};

    private final IElementalCrystal.Element element;
    private final int rarity;

    public EssenceItem(Properties properties, IElementalCrystal.Element element, int rarity) {
        super(properties.rarity(RARITIES[rarity]));
        this.element = element;
        this.rarity = rarity;
    }

    /** 元素（原作 meta % 4） */
    public IElementalCrystal.Element element() {
        return element;
    }

    /** 稀有度 0~3（原作 meta / 4） */
    public int rarity() {
        return rarity;
    }

    /** 原作的 meta 编号：稀有度 × 4 + 元素 */
    public int legacyMeta() {
        return rarity * 4 + element.ordinal();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.essentialcraft5.rarity",
                        Component.translatable("tooltip.essentialcraft5.rarity." + RARITY_NAMES.get(rarity))
                                .withStyle(RARITY_COLORS[rarity]))
                .withStyle(ChatFormatting.GRAY));
    }
}
