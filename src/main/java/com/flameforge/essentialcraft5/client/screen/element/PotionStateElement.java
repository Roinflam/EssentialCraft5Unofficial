package com.flameforge.essentialcraft5.client.screen.element;

import com.flameforge.essentialcraft5.client.screen.ECTextures;
import com.flameforge.essentialcraft5.menu.PotionSpreaderMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 药水扩散器装着的药水（36×36）。对应原作 GuiPotionState：四块槽框拼成的大框，中间一个药水瓶，
 * 装着药水时按剩余次数从下往上画出药水的颜色。原作只画图，这里加了悬停提示（装着哪些效果、还能扩散几次）。
 * 空瓶用玻璃瓶，药水部分用带 CustomPotionColor 的药水物品（原版药水的染色处理器照这个上色），剪裁出下面那一截。
 */
public class PotionStateElement implements ScreenElement {

    private static final ItemStack BOTTLE = new ItemStack(Items.GLASS_BOTTLE);

    private final int x;
    private final int y;
    private final PotionSpreaderMenu menu;
    private final ItemStack potion = new ItemStack(Items.POTION);

    public PotionStateElement(int x, int y, PotionSpreaderMenu menu) {
        this.x = x;
        this.y = y;
        this.menu = menu;
    }

    @Override
    public int x() {
        return x;
    }

    @Override
    public int y() {
        return y;
    }

    @Override
    public int width() {
        return 36;
    }

    @Override
    public int height() {
        return 36;
    }

    @Override
    public void render(GuiGraphics graphics, int left, int top) {
        // 原作用 slot_common.png 的四个角拼 36×36 的框
        graphics.blit(ECTextures.SLOT_COMMON, left, top, 0, 0, 17, 17);
        graphics.blit(ECTextures.SLOT_COMMON, left + 19, top, 1, 0, 17, 17);
        graphics.blit(ECTextures.SLOT_COMMON, left, top + 19, 0, 1, 17, 17);
        graphics.blit(ECTextures.SLOT_COMMON, left + 19, top + 19, 1, 1, 17, 17);
        graphics.blit(ECTextures.SLOT_COMMON, left + 17, top, 1, 0, 2, 17);
        graphics.blit(ECTextures.SLOT_COMMON, left + 17, top + 19, 1, 1, 2, 17);
        graphics.blit(ECTextures.SLOT_COMMON, left, top + 17, 0, 1, 17, 2);
        graphics.blit(ECTextures.SLOT_COMMON, left + 19, top + 17, 1, 1, 17, 2);
        graphics.blit(ECTextures.SLOT_COMMON, left + 17, top + 17, 1, 1, 2, 2);

        int itemX = left + 10;
        int itemY = top + 10;
        graphics.renderItem(BOTTLE, itemX, itemY);
        if (menu.loaded()) {
            int fill = ECTextures.scaled(menu.usesLeft(), menu.maxUses(), 16);
            if (fill > 0) {
                potion.getOrCreateTag().putInt(PotionUtils.TAG_CUSTOM_POTION_COLOR, menu.color());
                // 剪裁用的是屏幕坐标（GUI 缩放后），元素本来就按屏幕坐标画
                graphics.enableScissor(itemX, itemY + 16 - fill, itemX + 16, itemY + 16);
                graphics.renderItem(potion, itemX, itemY);
                graphics.disableScissor();
            }
        }
    }

    @Override
    public List<Component> tooltip() {
        List<Component> lines = new ArrayList<>();
        if (!menu.loaded()) {
            lines.add(Component.translatable("gui.essentialcraft5.potion_spreader.empty"));
            lines.add(Component.translatable("gui.essentialcraft5.potion_spreader.empty.hint").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        lines.add(Component.translatable("gui.essentialcraft5.potion_spreader.uses", menu.usesLeft(), menu.maxUses()));
        for (int i = 0; i < menu.effectCount(); i++) {
            MobEffectInstance effect = menu.effect(i);
            if (effect == null) {
                // 只同步了前几个效果；后面的写「还有 n 种」
                lines.add(Component.translatable("gui.essentialcraft5.potion_spreader.more", menu.effectCount() - i)
                        .withStyle(ChatFormatting.GRAY));
                break;
            }
            MutableComponent name = effect.getEffect().getDisplayName().copy();
            if (effect.getAmplifier() > 0) {
                name = Component.translatable("potion.withAmplifier", name,
                        Component.translatable("potion.potency." + effect.getAmplifier()));
            }
            if (!effect.getEffect().isInstantenous()) {
                name = Component.translatable("potion.withDuration", name, MobEffectUtil.formatDuration(effect, 1F));
            }
            // 和原版药水提示一样：有益蓝、有害红
            lines.add(name.withStyle(effect.getEffect().getCategory().getTooltipFormatting()));
        }
        return lines;
    }
}
