package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity.Mode;
import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity.Status;
import com.flameforge.essentialcraft5.client.screen.element.ItemIconElement;
import com.flameforge.essentialcraft5.client.screen.element.StatusTextElement;
import com.flameforge.essentialcraft5.menu.MRUConverterMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * MRU 转换器界面（176 × 166，通用背景）。生存档：左边 MRU 条 (7, 4)，中间状态条 (27, 4)（悬停写原因）、绑定宝石状态 (27, 22)、
 * 宝石格 (112, 23)（菜单里）；无尽档没有 MRU 条和宝石格，左上画方块图标 (7, 4)、(27, 22) 写「不耗 MRU」。
 * 右边两个模式按钮「能量」(131, 23)、「魔力」(131, 41)，当前模式描绿边，出不了的变灰（没装植物魔法时魔力按钮悬停写「需要植物魔法」）；
 * 下面「近 1 秒平均每刻输出」(27, 40)、「缓存 / 上限」(27, 58)。
 * <p>
 * 状态颜色照地脉凝聚器：工作绿、暂停 / 排队橙、没活黄、错误红（{@link Status#color}）。数值都从菜单同步来，不读客户端方块实体；
 * 植物魔法装没装用服务端同步的标志，不各自查。没装植物魔法时不显示任何魔力数值（只有切到魔力的老存档才会出现魔力字样）。
 */
public class MRUConverterScreen extends MachineScreen<MRUConverterMenu> {

    private static final String KEY = "gui.essentialcraft5.mru_converter.";
    private static final int BUTTON_X = 131;
    private static final int BUTTON_Y = 23;
    private static final int BUTTON_W = 38;
    private static final int BUTTON_H = 16;
    private static final int BUTTON_PITCH = 18;

    private final List<ModeButton> buttons = new ArrayList<>();

    public MRUConverterScreen(MRUConverterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        boolean infinite = menu.tier().infinite();
        if (infinite) {
            elements.add(new ItemIconElement(7, 4, () -> Item.getId(menu.tier().block().asItem())));
            elements.add(new StatusTextElement(27, 22, 82, () -> Component.translatable(KEY + "infinite"), () -> 0xFFAA00,
                    null, 0, () -> List.of(Component.translatable(KEY + "infinite.hint", menu.mruPerTick()))));
        } else {
            elements.add(mruStorage(7, 4));
            elements.add(boundGemState(27, 22));
        }
        elements.add(new StatusTextElement(27, 4, 142, () -> statusText(menu.status()), () -> menu.status().color,
                null, 0, this::statusTooltip));
        elements.add(new StatusTextElement(27, 40, 102, this::outputText, () -> 0xFFFFFF, null, 0, this::outputTooltip));
        elements.add(new StatusTextElement(27, 58, 142, this::bufferText, () -> 0xFFFFFF, null, 0, this::bufferTooltip));
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        for (Mode mode : Mode.values()) {
            int x = leftPos + BUTTON_X;
            int y = topPos + BUTTON_Y + mode.ordinal() * BUTTON_PITCH;
            buttons.add(addRenderableWidget(new ModeButton(x, y, mode)));
        }
    }

    static Component statusText(Status status) {
        return Component.translatable(switch (status) {
            case WORKING -> KEY + "status.working";
            case NO_RECEIVER -> KEY + "status.no_receiver";
            case NO_MRU -> "gui.essentialcraft5.no_mru";
            case POWERED -> "gui.essentialcraft5.devices.redstone_paused";
            case DISABLED -> KEY + "status.disabled";
            case MODE_OFF -> KEY + "status.mode_off";
            case NO_BOTANIA -> KEY + "status.no_botania";
            case OWNER_LIMIT -> KEY + "status.owner_limit";
            case NO_OWNER -> "gui.essentialcraft5.owner.none";
        });
    }

    private List<Component> statusTooltip() {
        List<Component> lines = new ArrayList<>();
        switch (menu.status()) {
            case WORKING -> lines.add(Component.translatable(KEY + "status.working.hint"));
            case NO_RECEIVER -> lines.add(Component.translatable(menu.mode() == Mode.MANA
                    ? KEY + "status.no_receiver.hint_mana" : KEY + "status.no_receiver.hint"));
            case NO_MRU -> lines.add(Component.translatable(KEY + "status.no_mru.hint"));
            case MODE_OFF -> lines.add(Component.translatable(KEY + "status.mode_off.hint"));
            case NO_BOTANIA -> lines.add(Component.translatable(KEY + "status.no_botania.hint"));
            case OWNER_LIMIT -> {
                lines.add(Component.translatable(KEY + "status.owner_limit_count", MRUConverterBlockEntity.Config.maxPerOwner()));
                lines.add(Component.translatable(KEY + "status.owner_limit.hint").withStyle(ChatFormatting.GRAY));
            }
            case NO_OWNER -> lines.add(Component.translatable(KEY + "status.no_owner.hint"));
            default -> {
            }
        }
        return lines;
    }

    private String unitKey() {
        return menu.mode() == Mode.MANA ? KEY + "unit.mana" : KEY + "unit.energy";
    }

    private Component outputText() {
        return Component.translatable(KEY + "output", average(menu.recentOutput()), Component.translatable(unitKey()));
    }

    private List<Component> outputTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(KEY + "output.hint"));
        lines.add(Component.translatable(KEY + "output.max", menu.quota(), Component.translatable(unitKey())).withStyle(ChatFormatting.GRAY));
        if (menu.tier().infinite()) {
            lines.add(Component.translatable(KEY + "infinite.hint", menu.mruPerTick()).withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable(KEY + "output.mru", menu.mruPerTick()).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private Component bufferText() {
        return Component.translatable(KEY + "buffer", menu.buffer(), menu.bufferCap(), Component.translatable(unitKey()));
    }

    private List<Component> bufferTooltip() {
        return List.of(Component.translatable(KEY + "buffer.hint"),
                Component.translatable(KEY + "buffer.switch").withStyle(ChatFormatting.GRAY));
    }

    /** 近 1 秒的合计 → 平均每刻（保留 2 位小数，去掉多余的 0） */
    private static String average(int sum) {
        return BigDecimal.valueOf(Math.max(sum, 0)).divide(BigDecimal.valueOf(MRUConverterBlockEntity.ACTIVE_WINDOW), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private List<Component> buttonTooltip(Mode mode) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(KEY + "mode." + mode.getSerializedName() + ".tooltip"));
        if (mode == menu.mode()) {
            lines.add(Component.translatable(KEY + "mode.selected").withStyle(ChatFormatting.GREEN));
        } else if (menu.modeUsable(mode)) {
            lines.add(Component.translatable(KEY + "mode.switch").withStyle(ChatFormatting.DARK_GRAY));
        } else if (mode == Mode.MANA && !menu.botaniaAvailable()) {
            lines.add(Component.translatable(KEY + "mode.needs_botania").withStyle(ChatFormatting.RED));
        } else {
            lines.add(Component.translatable(KEY + "mode.off").withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (ModeButton button : buttons) {
            // 当前模式的按钮保持亮着（描绿边），点了什么都不做；出不了的变灰
            button.active = menu.modeUsable(button.mode) || button.mode == menu.mode();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        for (ModeButton button : buttons) {
            if (button.isHovered()) {
                graphics.renderComponentTooltip(font, buttonTooltip(button.mode), mouseX, mouseY);
                return;
            }
        }
    }

    /** 模式按钮：原版按钮底 + 文字；当前模式描一圈绿边（点了不发包），出不了的盖一层灰 */
    private final class ModeButton extends AbstractButton {

        private final Mode mode;

        ModeButton(int x, int y, Mode mode) {
            super(x, y, BUTTON_W, BUTTON_H, Component.translatable(KEY + "mode." + mode.getSerializedName()));
            this.mode = mode;
        }

        @Override
        public void onPress() {
            if (mode != menu.mode() && minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, mode.ordinal());
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            int left = getX();
            int top = getY();
            int right = left + width;
            int bottom = top + height;
            if (mode == menu.mode()) {
                int color = 0xFF55FF55;
                graphics.fill(left, top, right, top + 1, color);
                graphics.fill(left, bottom - 1, right, bottom, color);
                graphics.fill(left, top, left + 1, bottom, color);
                graphics.fill(right - 1, top, right, bottom, color);
            } else if (!menu.modeUsable(mode)) {
                graphics.fill(left + 1, top + 1, right - 1, bottom - 1, 0x80202020);
            }
        }

        /** 原版按钮字比 width-4 宽就来回滚动（英文「Energy」比按钮多 2 像素，总是只露半截），这里改成整条缩小到放得下 */
        @Override
        public void renderString(GuiGraphics graphics, Font font, int color) {
            int room = width - 4;
            int textWidth = font.width(getMessage());
            if (textWidth <= room) {
                super.renderString(graphics, font, color);
                return;
            }
            float scale = room / (float) textWidth;
            graphics.pose().pushPose();
            graphics.pose().translate(getX() + 2, getY() + height / 2F - 4 * scale, 0F);
            graphics.pose().scale(scale, scale, 1F);
            graphics.drawString(font, getMessage(), 0, 0, color, true);
            graphics.pose().popPose();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }
    }
}
