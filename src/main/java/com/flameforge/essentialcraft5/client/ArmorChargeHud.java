package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.blockentity.ChargingChamberBlockEntity;
import com.flameforge.essentialcraft5.item.armor.ArmorCharge;
import com.flameforge.essentialcraft5.item.armor.IMRUShieldArmor;
import com.flameforge.essentialcraft5.item.armor.MRUShield;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 护甲电量条和「满电约挡」提示行（第十三批 13b「维持方便」，batch13.md §4.7 第 2、3 条，§8.2「护甲电量条」）。数都来自 {@link ArmorCharge}，
 * 读的是本人身上盔甲的物品 NBT（服务端照常同步过来的那份），不加包。
 * <ul>
 *   <li><b>电量条</b>：穿着至少一件 MRU 护盾盔甲（{@link IMRUShieldArmor}，穿在自己部位）时，快捷栏左边一个方框，样子和矩阵后遗症的方框（{@link AfflictionHud}）一样：
 *       深紫底、源质紫边，里面电量从下往上涨，上面压着一件盔甲的图标（有胸甲画胸甲），框上面写四件合计的百分比，不到 {@value #LOW_PERCENT}% 变红。
 *       它占最靠快捷栏的那一格（副手格、被压制的图标照旧在它右边），后遗症的方框往左让一格。打开界面时鼠标停在上面出提示框：合计、每件的存量、一句说明。
 *       客户端配置 {@code [hud] armor_charge}（{@link HudClientConfig}）可以关，关了以后后遗症不再让格。</li>
 *   <li><b>提示行</b>：MRU 护盾盔甲的提示框里，紧跟 MRU 那一行加一行「满电约挡：20 点 × N 下」（{@link ArmorCharge#fullChargeHits}，按这件现在的同调阶算），
 *       单价配成 0 时改写「不耗电」。各套的物品类不用知道这一行，新加的护盾盔甲实现了接口就有。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class ArmorChargeHud {

    private static final int BOX = AfflictionHud.BOX;
    private static final int FILL = 0xC0180A26;
    private static final int BORDER = 0xFF8A4FC0;
    /** 电量：薰衣草紫（MRU 线的颜色），半透明，图标压在上面还看得清 */
    private static final int GAUGE = 0x99B58CFF;
    private static final int TEXT_COLOR = 0xFFE6D6FA;
    private static final int LOW_COLOR = 0xFFE08A8A;
    /** 低于这个百分比字变红 */
    static final int LOW_PERCENT = 20;
    private static final int TOOLTIP_WIDTH = 200;
    /** 图标优先画哪件 */
    private static final ArmorItem.Type[] ICON_ORDER = {ArmorItem.Type.CHESTPLATE, ArmorItem.Type.HELMET, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};

    static final String NAME_KEY = "hud." + EssentialCraft5.MODID + ".armor_charge";
    static final String TOTAL_KEY = "hud." + EssentialCraft5.MODID + ".armor_charge.total";
    static final String PIECE_KEY = "hud." + EssentialCraft5.MODID + ".armor_charge.piece";
    static final String DESC_KEY = "hud." + EssentialCraft5.MODID + ".armor_charge.desc";
    /** 说明的第二句「站到自己的充能仓上面充电」：充能仓不给身上的盔甲充（charge_worn_armor 关掉）时不写 */
    static final String DESC_CHAMBER_KEY = "hud." + EssentialCraft5.MODID + ".armor_charge.desc.chamber";
    static final String HITS_KEY = "tooltip." + EssentialCraft5.MODID + ".armor_charge.full_hits";
    static final String FREE_KEY = "tooltip." + EssentialCraft5.MODID + ".armor_charge.free";
    static final String WITH_MRU_KEY = "tooltip." + EssentialCraft5.MODID + ".armor_charge.with_mru";
    static final String WITH_MRU_FREE_KEY = "tooltip." + EssentialCraft5.MODID + ".armor_charge.with_mru.free";
    /** MRU 那一行的键（{@code MRUStaffItem.mruTooltip}），提示行插在它后面 */
    private static final String MRU_LINE_KEY = "tooltip." + EssentialCraft5.MODID + ".mru";

    /**
     * 同一刻里电量条和后遗症排版每帧都要问一次合计，按（玩家对象、tickCount）缓存一份。
     * 玩家用弱引用：退到标题画面以后不再画，强引用会把上一个世界整个留在内存里。
     */
    private static WeakReference<LocalPlayer> cachedPlayer = new WeakReference<>(null);
    private static int cachedTick = Integer.MIN_VALUE;
    private static ArmorCharge.Total cachedTotal = ArmorCharge.Total.NONE;

    private ArmorChargeHud() {
    }

    /** 本人身上护盾盔甲的合计（同一刻只算一次） */
    static ArmorCharge.Total total(LocalPlayer player) {
        if (player != cachedPlayer.get() || player.tickCount != cachedTick) {
            cachedPlayer = new WeakReference<>(player);
            cachedTick = player.tickCount;
            cachedTotal = ArmorCharge.worn(player);
        }
        return cachedTotal;
    }

    /** 这一帧电量条在不在（{@link AfflictionHud} 据此让一格）：配置开着、不是旁观、穿着护盾盔甲 */
    static boolean shown(LocalPlayer player) {
        return HudClientConfig.armorCharge() && !player.isSpectator() && total(player).any();
    }

    /** 方框左上角的 x */
    private static int boxX(LocalPlayer player, int width) {
        return AfflictionHud.hotbarLeft(player, width) - BOX;
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || !shown(player)) {
            return;
        }
        ArmorCharge.Total total = total(player);
        int x = boxX(player, width);
        int y = height - BOX - 1;
        int inner = BOX - 2;
        RenderSystem.enableBlend();
        graphics.fill(x, y, x + BOX, y + BOX, FILL);
        int filled = Math.round(inner * total.fraction());
        if (filled == 0 && total.stored() > 0L) {
            filled = 1;
        }
        if (filled > 0) {
            graphics.fill(x + 1, y + 1 + inner - filled, x + 1 + inner, y + 1 + inner, GAUGE);
        }
        graphics.renderOutline(x, y, BOX, BOX, BORDER);
        ItemStack icon = iconPiece(player);
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, x + 2, y + 2);
        }
        int percent = total.percent();
        String text = percent + "%";
        graphics.drawString(minecraft.font, text, x + (BOX - minecraft.font.width(text)) / 2, y - 9,
                percent < LOW_PERCENT ? LOW_COLOR : TEXT_COLOR, true);
        RenderSystem.disableBlend();
    }

    /** 方框里画的那件：先胸甲，再头、腿、靴 */
    private static ItemStack iconPiece(LocalPlayer player) {
        for (ArmorItem.Type type : ICON_ORDER) {
            ItemStack stack = MRUShield.wornPiece(player, type);
            if (stack != null) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** 打开界面时鼠标停在方框上：出提示框。容器界面盖住的地方不出（同 {@link AfflictionHud}） */
    @SubscribeEvent
    public static void onArmorChargeScreenRender(ScreenEvent.Render.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || !shown(player)) {
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
        int x = boxX(player, width);
        int y = height - BOX - 1;
        if (mouseX >= x && mouseX < x + BOX && mouseY >= y && mouseY < y + BOX) {
            event.getGuiGraphics().renderTooltip(minecraft.font, tooltip(minecraft.font, player), mouseX, mouseY);
        }
    }

    /** 方框的提示框：名字、合计、每件（头到靴）的存量、说明（充能仓那句按 charge_worn_armor，SERVER 配置进服时同步过来） */
    static List<FormattedCharSequence> tooltip(Font font, LocalPlayer player) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        ArmorCharge.Total total = total(player);
        lines.add(Component.translatable(NAME_KEY).withStyle(ChatFormatting.LIGHT_PURPLE).getVisualOrderText());
        lines.add(Component.translatable(TOTAL_KEY, grouped(total.stored()), grouped(total.max())).withStyle(ChatFormatting.WHITE).getVisualOrderText());
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            ItemStack stack = MRUShield.wornPiece(player, type);
            IMRUHandlerItem handler = stack == null ? null : stack.getCapability(ECCapabilities.MRU_ITEM).orElse(null);
            if (handler != null) {
                lines.add(Component.translatable(PIECE_KEY, stack.getHoverName(), grouped(handler.getMRU()), grouped(handler.getMaxMRU()))
                        .withStyle(ChatFormatting.GRAY).getVisualOrderText());
            }
        }
        lines.addAll(font.split(Component.translatable(DESC_KEY).withStyle(ChatFormatting.DARK_GRAY), TOOLTIP_WIDTH));
        if (ChargingChamberBlockEntity.Config.chargesWornArmor()) {
            lines.addAll(font.split(Component.translatable(DESC_CHAMBER_KEY).withStyle(ChatFormatting.DARK_GRAY), TOOLTIP_WIDTH));
        }
        return lines;
    }

    private static String grouped(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    // ------------------------------------------------------------------
    // 「满电约挡」
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onArmorChargeTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof IMRUShieldArmor) || AttunementTooltips.holdsBackNotes(event)) {
            // 同调装备没按住 Shift 时 MRU 行收起来了，这一句跟着收
            return;
        }
        Level level = event.getEntity() != null ? event.getEntity().level() : Minecraft.getInstance().level;
        // 没进世界（标题画面）时没有伤害类型注册表，不写这一行
        DamageSource source = level == null ? null : ArmorCharge.samplePhysical(level);
        if (source == null) {
            return;
        }
        int hits = ArmorCharge.fullChargeHits(stack, source);
        if (hits == ArmorCharge.NOT_SHIELDED) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        int at = mruLine(tooltip);
        if (at >= 0) {
            // 找到 MRU 那一行就把满电次数并进去，原地替换，省一行
            Component mru = tooltip.get(at);
            Component merged = hits == ArmorCharge.FREE
                    ? Component.translatable(WITH_MRU_FREE_KEY, mru)
                    : Component.translatable(WITH_MRU_KEY, mru, Math.round(ArmorCharge.SAMPLE_DAMAGE), grouped(hits));
            tooltip.set(at, merged.copy().withStyle(mru.getStyle()));
            return;
        }
        Component line = hits == ArmorCharge.FREE
                ? Component.translatable(FREE_KEY).withStyle(ChatFormatting.GRAY)
                : Component.translatable(HITS_KEY, Math.round(ArmorCharge.SAMPLE_DAMAGE), grouped(hits)).withStyle(ChatFormatting.GRAY);
        tooltip.add(line);
    }

    /** MRU 那一行的下标；找不到返回 -1 */
    private static int mruLine(List<Component> tooltip) {
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getContents() instanceof TranslatableContents contents && MRU_LINE_KEY.equals(contents.getKey())) {
                return i;
            }
        }
        return -1;
    }

    /** 模组总线：登记快捷栏旁那一层 */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ArmorChargeHudRegistration {

        private ArmorChargeHudRegistration() {
        }

        @SubscribeEvent
        public static void registerArmorChargeOverlay(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "armor_charge", ArmorChargeHud::render);
        }
    }
}
