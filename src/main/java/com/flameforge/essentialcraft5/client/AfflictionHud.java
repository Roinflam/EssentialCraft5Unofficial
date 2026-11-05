package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.corruption.Affliction;
import com.flameforge.essentialcraft5.effect.MindfoldParadoxEffect;
import com.flameforge.essentialcraft5.effect.RadiationEffect;
import com.flameforge.essentialcraft5.hoanna.HoannaHazardsConfig;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import com.flameforge.essentialcraft5.registry.ECHoannaHazards;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 矩阵后遗症的 HUD（{@code corruption/Afflictions}）。后遗症不是药水效果，原版右上角和物品栏的效果列表里没有它，所以单独画：
 * 快捷栏左边一排小方框（副手格、被压制的图标在那儿时再往左挪），深紫底、源质紫边，里面是同一个意思的效果图标，
 * 上面写剩余时间（心智悖论照原作不写），MRU 腐化右下角写等级；最后 10 秒照原版效果图标的节奏闪。
 * 打开界面时（物品栏、聊天栏……）鼠标停在方框上出提示框：名字、说明、剩余时间，最后一行说明它是后遗症、牛奶治不好。
 * 读的是客户端那份玩家数据（服务端同步过来，本地每 tick 跟着减）。
 * <p>
 * 海纳尔辐射（{@code hoanna/Radiation}）同样不是药水效果，剂量 &gt; 0 时也占一格，放在最靠快捷栏的位置（它常驻，后遗症来来去去不挤动它）：
 * 上面写生命上限被压低了多少，右下角写等级，提示框里是剂量和此地每秒的净变化。
 * <p>
 * 穿着 MRU 护盾盔甲、护甲电量条开着时，最靠快捷栏的那一格归电量条（{@link ArmorChargeHud}，第十三批 13b），这一排整体往左让一格（{@link #layout}）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class AfflictionHud {

    /** 方框边长（里面的图标 18） */
    static final int BOX = 20;
    /** 方框之间的空隙（剩余时间的字比方框宽一点） */
    static final int GAP = 6;
    private static final int FILL = 0xC0180A26;
    private static final int BORDER = 0xFF8A4FC0;
    private static final int TIME_COLOR = 0xFFE6D6FA;
    private static final int CAP_COLOR = 0xFFE08A8A;
    /** 提示框说明一行最宽多少像素 */
    private static final int TOOLTIP_WIDTH = 200;

    static final String REMAINING_KEY = "affliction." + EssentialCraft5.MODID + ".remaining";
    static final String FOOTER_KEY = "affliction." + EssentialCraft5.MODID + ".footer";

    /**
     * 一个方框：画在哪、画什么。affliction 为 null 是辐射格，这时 level 是放大值（剂量 / 1 万）、dose 是剂量，ticks 不用。
     */
    record Slot(@Nullable Affliction affliction, int x, int y, int ticks, int level, int dose) {

        boolean radiation() {
            return affliction == null;
        }

        MobEffect icon() {
            return affliction == null ? ECHoannaHazards.RADIATION.get() : affliction.icon();
        }
    }

    private AfflictionHud() {
    }

    /**
     * 快捷栏左边能用的最右一格的右边缘：快捷栏左边缘在 width / 2 - 91，再空 4；副手格在左边（主手在右）且有东西时占了 29 像素；
     * 被压制的图标再占 20。护甲电量条（{@link ArmorChargeHud}）从这里往左画，后遗症再往左让一格。
     */
    static int hotbarLeft(LocalPlayer player, int width) {
        int right = width / 2 - 91 - 4;
        if (!player.getOffhandItem().isEmpty() && player.getMainArm() == HumanoidArm.RIGHT) {
            right -= 29;
        }
        if (CurioSealClient.showsHotbarIcon(player)) {
            right -= 20;
        }
        return right;
    }

    /** 按现在身上的辐射和后遗症排方框（从右往左：辐射，然后后遗症按 {@link Affliction} 的顺序） */
    static List<Slot> layout(LocalPlayer player, int width, int height) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            return List.of();
        }
        int dose = data.getRadiation() > 0 && HoannaHazardsConfig.radiationEnabled() ? data.getRadiation() : 0;
        if (dose <= 0 && data.getAfflictionIds().isEmpty()) {
            return List.of();
        }
        int right = hotbarLeft(player, width);
        // 护甲电量条（第十三批 13b）贴着快捷栏占第一格，这一排整体往左让一格
        if (ArmorChargeHud.shown(player)) {
            right -= BOX + GAP;
        }
        int y = height - BOX - 1;
        List<Slot> slots = new ArrayList<>();
        if (dose > 0) {
            slots.add(new Slot(null, right - BOX, y, 0, Radiation.level(dose), dose));
        }
        for (Affliction affliction : Affliction.all()) {
            int ticks = data.getAfflictionTicks(affliction.id());
            if (ticks > 0) {
                int x = right - BOX - slots.size() * (BOX + GAP);
                slots.add(new Slot(affliction, x, y, ticks, data.getAfflictionLevel(affliction.id()), 0));
            }
        }
        return slots;
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()) {
            return;
        }
        List<Slot> slots = layout(player, width, height);
        if (slots.isEmpty()) {
            return;
        }
        Font font = minecraft.font;
        RenderSystem.enableBlend();
        for (Slot slot : slots) {
            graphics.fill(slot.x(), slot.y(), slot.x() + BOX, slot.y() + BOX, FILL);
            graphics.renderOutline(slot.x(), slot.y(), BOX, BOX, BORDER);
            graphics.setColor(1F, 1F, 1F, slot.radiation() ? 1F : alpha(slot.ticks()));
            graphics.blit(slot.x() + 1, slot.y() + 1, 0, 18, 18, minecraft.getMobEffectTextures().get(slot.icon()));
            graphics.setColor(1F, 1F, 1F, 1F);
            if (slot.radiation()) {
                String loss = RadiationEffect.capLossPercentText(slot.level(), halved(player));
                if (loss != null) {
                    String text = "-" + loss + "%";
                    graphics.drawString(font, text, slot.x() + (BOX - font.width(text)) / 2, slot.y() - 9, CAP_COLOR, true);
                }
            } else if (slot.affliction().showsTime()) {
                String time = StringUtil.formatTickDuration(slot.ticks());
                graphics.drawString(font, time, slot.x() + (BOX - font.width(time)) / 2, slot.y() - 9, TIME_COLOR, true);
            }
            // 只有 MRU 腐化和辐射的等级会变、而且决定扣血 / 压上限的几率，写在角上；别的固定 II 级，提示框里看
            if ((slot.radiation() || slot.affliction() == Affliction.MRU_CORRUPTION) && slot.level() > 0) {
                String level = String.valueOf(slot.level() + 1);
                graphics.pose().pushPose();
                graphics.pose().translate(0F, 0F, 200F);
                graphics.drawString(font, level, slot.x() + BOX - 1 - font.width(level), slot.y() + BOX - 8, 0xFFFFFFFF, true);
                graphics.pose().popPose();
            }
        }
        RenderSystem.disableBlend();
    }

    /** 戴着 δ 指环（辐射压的级数减半，上限按实际压的写） */
    private static boolean halved(LocalPlayer player) {
        return CuriosCompat.has(player, ECBaubles.HALVES_RADIATION);
    }

    /** 最后 10 秒闪烁（原版 Gui.renderEffects 的算法），之前不透明 */
    static float alpha(int ticks) {
        if (ticks > 200) {
            return 1F;
        }
        int m = 10 - ticks / 20;
        return Mth.clamp(ticks / 10F / 5F * 0.5F, 0F, 0.5F)
                + Mth.cos(ticks * (float) Math.PI / 5F) * Mth.clamp(m / 10F * 0.25F, 0F, 0.25F);
    }

    /** 打开界面时鼠标停在方框上：出提示框。容器界面盖住的地方不出（免得和物品的提示框打架） */
    @SubscribeEvent
    public static void onAfflictionScreenRender(ScreenEvent.Render.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()) {
            return;
        }
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen
                && mouseX >= screen.getGuiLeft() && mouseX < screen.getGuiLeft() + screen.getXSize()
                && mouseY >= screen.getGuiTop() && mouseY < screen.getGuiTop() + screen.getYSize()) {
            return;
        }
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        for (Slot slot : layout(player, width, height)) {
            if (mouseX >= slot.x() && mouseX < slot.x() + BOX && mouseY >= slot.y() && mouseY < slot.y() + BOX) {
                List<FormattedCharSequence> lines = slot.radiation()
                        ? radiationTooltip(minecraft.font, slot, player) : tooltip(minecraft.font, slot);
                event.getGuiGraphics().renderTooltip(minecraft.font, lines, mouseX, mouseY);
                return;
            }
        }
    }

    static List<FormattedCharSequence> tooltip(Font font, Slot slot) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        MutableComponent name = Component.translatable(slot.affliction().nameKey());
        if (slot.level() > 0) {
            name.append(" ").append(Component.translatable("enchantment.level." + (slot.level() + 1)));
        }
        lines.add(name.withStyle(ChatFormatting.LIGHT_PURPLE).getVisualOrderText());
        lines.addAll(font.split(Component.translatable(slot.affliction().descKey()).withStyle(ChatFormatting.GRAY), TOOLTIP_WIDTH));
        Component state = slot.affliction().showsTime()
                ? Component.translatable(REMAINING_KEY, StringUtil.formatTickDuration(slot.ticks()))
                : Component.translatable(MindfoldParadoxEffect.SUBTITLE_KEY);
        lines.add(state.copy().withStyle(ChatFormatting.WHITE).getVisualOrderText());
        lines.addAll(font.split(Component.translatable(FOOTER_KEY).withStyle(ChatFormatting.DARK_GRAY), TOOLTIP_WIDTH));
        return lines;
    }

    /** 辐射格的提示框：名字和等级、说明、剂量与此地每秒净变化、生命上限压低多少、最后一行说明它去不掉 */
    static List<FormattedCharSequence> radiationTooltip(Font font, Slot slot, LocalPlayer player) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        MutableComponent name = ECHoannaHazards.RADIATION.get().getDisplayName().copy();
        if (slot.level() >= 1 && slot.level() <= 9) {
            name.append(" ").append(Component.translatable("enchantment.level." + (slot.level() + 1)));
        } else if (slot.level() > 9) {
            name.append(" " + (slot.level() + 1));
        }
        lines.add(name.withStyle(ChatFormatting.LIGHT_PURPLE).getVisualOrderText());
        lines.addAll(font.split(Component.translatable(RadiationEffect.DESC_KEY).withStyle(ChatFormatting.GRAY), TOOLTIP_WIDTH));
        int rate = Radiation.netPerCycle(player, slot.dose());
        String rateText = rate > 0 ? "+" + rate : Integer.toString(rate);
        lines.add(Component.translatable(RadiationEffect.DOSE_KEY, String.format(Locale.ROOT, "%,d", slot.dose()), rateText)
                .withStyle(rate > 0 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE).getVisualOrderText());
        String loss = RadiationEffect.capLossPercentText(slot.level(), halved(player));
        if (loss != null) {
            lines.add(Component.translatable(RadiationEffect.CAP_KEY, loss).withStyle(ChatFormatting.RED).getVisualOrderText());
        }
        lines.addAll(font.split(Component.translatable(RadiationEffect.FOOTER_KEY).withStyle(ChatFormatting.DARK_GRAY), TOOLTIP_WIDTH));
        return lines;
    }

    /** 模组总线：登记快捷栏旁那一层 */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class AfflictionHudRegistration {

        private AfflictionHudRegistration() {
        }

        @SubscribeEvent
        public static void registerAfflictionOverlay(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "afflictions", AfflictionHud::render);
        }
    }
}
