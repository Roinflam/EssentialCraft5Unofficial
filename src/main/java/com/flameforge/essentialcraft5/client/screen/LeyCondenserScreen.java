package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.LeyCondenserBlockEntity;
import com.flameforge.essentialcraft5.blockentity.LeyCondenserBlockEntity.Config;
import com.flameforge.essentialcraft5.blockentity.LeyCondenserBlockEntity.Status;
import com.flameforge.essentialcraft5.blockentity.LeyCondenserBlockEntity.Target;
import com.flameforge.essentialcraft5.client.screen.element.ScreenElement;
import com.flameforge.essentialcraft5.client.screen.element.StatusTextElement;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.LeyCondenserMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 地脉凝聚器界面。左边 MRU 条 (7, 4)；中间一列：状态条 (27, 4)（悬停写原因）、绑定宝石状态 (27, 22)、10 个目标按钮两行 × 5（(28, 43) 起，间距 17 × 18）；
 * 右边：绑定宝石格 (116, 5)、ESPE 球 (134, 4)、「当前目标 + 进度」小框 (152, 4)，下面 3×3 输出格（菜单里）。
 * <p>
 * 目标按钮：16×16 原版按钮底 + 缩小的产物图标；选中的描一圈绿边，不收的变灰（点不了），这里没有地脉时要地脉的那几种右上角一个红点。
 * 悬停写每个的 MRU / ESPE / 用时、满负荷每刻多少 MRU、换目标会清空进度。价格读服务端配置（进服时 Forge 发给客户端，热改后重发）。
 * 状态颜色照投影仪：工作绿、暂停 / 排队橙、缺东西黄、错误红（{@link Status#color}）。数值都从菜单同步来，不读客户端方块实体。
 */
public class LeyCondenserScreen extends MachineScreen<LeyCondenserMenu> {

    private static final String KEY = "gui.essentialcraft5.ley_condenser.";
    private static final int BUTTONS_X = 28;
    private static final int BUTTONS_Y = 43;
    private static final int BUTTON_PITCH_X = 17;
    private static final int BUTTON_PITCH_Y = 18;
    private static final int PER_ROW = 5;

    /** 开发展示厅：强制显示第几个目标按钮的悬停提示（−1 = 不显示；不动真鼠标） */
    public static int showroomHover = -1;

    private final List<TargetButton> buttons = new ArrayList<>();
    /** 每种目标收不收（查燃烧时间会发一个 Forge 事件，每秒刷新一次，不每帧查） */
    private final boolean[] enabled = new boolean[Target.VALUES.length];
    private int enabledRefresh;

    public LeyCondenserScreen(LeyCondenserMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(new StatusTextElement(27, 4, 82, () -> statusText(menu.status()), () -> menu.status().color,
                null, 0, this::statusTooltip));
        elements.add(boundGemState(27, 22));
        elements.add(espeStorage(134, 4));
        elements.add(new TargetBox(152, 4));
    }

    @Override
    protected void init() {
        super.init();
        refreshEnabled();
        buttons.clear();
        for (Target target : Target.VALUES) {
            int i = target.ordinal();
            int x = leftPos + BUTTONS_X + (i % PER_ROW) * BUTTON_PITCH_X;
            int y = topPos + BUTTONS_Y + (i / PER_ROW) * BUTTON_PITCH_Y;
            buttons.add(addRenderableWidget(new TargetButton(x, y, target)));
        }
    }

    static Component statusText(Status status) {
        return Component.translatable(switch (status) {
            case WORKING -> KEY + "status.working";
            case DISABLED -> KEY + "status.disabled";
            case NO_OWNER -> "gui.essentialcraft5.owner.none";
            case NO_TARGET -> KEY + "status.no_target";
            case TARGET_OFF -> KEY + "status.target_off";
            case NO_LEY -> KEY + "status.no_ley";
            case DUPLICATE -> KEY + "status.duplicate";
            case OWNER_LIMIT -> KEY + "status.owner_limit";
            case POWERED -> "gui.essentialcraft5.devices.redstone_paused";
            case OUTPUT_FULL -> KEY + "status.output_full";
            case TOO_EXPENSIVE -> KEY + "status.too_expensive";
            case NO_MRU -> "gui.essentialcraft5.no_mru";
            case NO_ESPE -> KEY + "status.no_espe";
        });
    }

    private List<Component> statusTooltip() {
        List<Component> lines = new ArrayList<>();
        Target target = menu.target();
        switch (menu.status()) {
            case WORKING -> {
                if (target != null) {
                    lines.add(Component.translatable(KEY + "status.working.hint", seconds(menu.maxProgress()), name(target)));
                }
            }
            case NO_OWNER -> lines.add(Component.translatable(KEY + "status.no_owner.hint"));
            case NO_TARGET -> lines.add(Component.translatable(KEY + "status.no_target.hint"));
            case TARGET_OFF -> lines.add(Component.translatable(KEY + "status.target_off.hint"));
            case NO_LEY -> lines.add(Component.translatable(KEY + "status.no_ley.hint"));
            case DUPLICATE -> {
                BlockPos at = menu.blocker();
                if (at != null) {
                    lines.add(Component.translatable(KEY + "status.duplicate_at", at.getX(), at.getY(), at.getZ()));
                }
                lines.add(Component.translatable(KEY + "status.duplicate.hint").withStyle(ChatFormatting.GRAY));
            }
            case OWNER_LIMIT -> {
                lines.add(Component.translatable(KEY + "status.owner_limit_count", ECConfig.get(Config.maxPerOwner)));
                lines.add(Component.translatable(KEY + "status.owner_limit.hint").withStyle(ChatFormatting.GRAY));
            }
            case OUTPUT_FULL -> lines.add(Component.translatable(KEY + "status.output_full.hint"));
            case TOO_EXPENSIVE -> lines.add(Component.translatable(KEY + "status.too_expensive.hint"));
            case NO_ESPE -> lines.add(Component.translatable(KEY + "status.no_espe.hint"));
            default -> {
            }
        }
        return lines;
    }

    private static Component name(Target target) {
        return new ItemStack(target.product).getHoverName();
    }

    /** tick → 秒（去掉多余的 0：10 → 0.5，600 → 30） */
    private static String seconds(int ticks) {
        return BigDecimal.valueOf(Math.max(ticks, 0)).divide(BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString();
    }

    /** 满负荷每刻多少 MRU（保留 1 位小数，去掉多余的 0） */
    private static String perTick(int total, int ticks) {
        if (ticks <= 0) {
            return "0";
        }
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(ticks), 1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private boolean enabled(Target target) {
        return enabled[target.ordinal()];
    }

    private void refreshEnabled() {
        for (Target target : Target.VALUES) {
            enabled[target.ordinal()] = Config.ticks(target) > 0 && !LeyCondenserBlockEntity.isFuel(target);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (--enabledRefresh <= 0) {
            enabledRefresh = 20;
            refreshEnabled();
        }
    }

    private List<Component> buttonTooltip(Target target) {
        List<Component> lines = new ArrayList<>();
        lines.add(name(target));
        boolean on = enabled(target);
        if (on) {
            int mru = Config.mru(target);
            int espe = Config.espe(target);
            int ticks = Config.ticks(target);
            lines.add(Component.translatable(KEY + "target.cost", mru).withStyle(ChatFormatting.GRAY));
            if (espe > 0) {
                lines.add(Component.translatable(KEY + "target.espe", espe).withStyle(ChatFormatting.GRAY));
            }
            lines.add(Component.translatable(KEY + "target.time", seconds(ticks)).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(KEY + "target.draw", perTick(mru, ticks)).withStyle(ChatFormatting.GRAY));
            if (target.needsLey && !menu.hasLey()) {
                lines.add(Component.translatable(KEY + "target.no_ley").withStyle(ChatFormatting.RED));
            }
            if (!target.needsLey) {
                lines.add(Component.translatable(KEY + "target.pillars").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        } else {
            lines.add(Component.translatable(KEY + "target.off").withStyle(ChatFormatting.RED));
        }
        if (target == menu.target()) {
            lines.add(Component.translatable(KEY + "target.selected").withStyle(ChatFormatting.GREEN));
        } else if (on) {
            lines.add(Component.translatable(menu.progress() > 0 ? KEY + "target.reset" : KEY + "target.click")
                    .withStyle(menu.progress() > 0 ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (TargetButton button : buttons) {
            // 选中的那个即使被改成不收了也要能点（取消）
            button.active = enabled(button.target) || button.target == menu.target();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        if (showroomHover >= 0 && showroomHover < buttons.size()) {
            TargetButton button = buttons.get(showroomHover);
            graphics.renderComponentTooltip(font, buttonTooltip(button.target), button.getX() + 8, button.getY() + 8);
            return;
        }
        for (TargetButton button : buttons) {
            if (button.isHovered()) {
                graphics.renderComponentTooltip(font, buttonTooltip(button.target), mouseX, mouseY);
                return;
            }
        }
    }

    /** 一种目标的按钮：16×16 原版按钮底 + 缩小的产物图标；选中描绿边，不收的盖一层灰，没有地脉时右上角红点 */
    private final class TargetButton extends AbstractButton {

        private final Target target;
        private final ItemStack icon;

        TargetButton(int x, int y, Target target) {
            super(x, y, 16, 16, Component.empty());
            this.target = target;
            this.icon = new ItemStack(target.product);
        }

        @Override
        public void onPress() {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, target.ordinal());
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            graphics.pose().pushPose();
            graphics.pose().translate(getX() + 2, getY() + 2, 0F);
            graphics.pose().scale(0.75F, 0.75F, 1F);
            graphics.renderItem(icon, 0, 0);
            graphics.pose().popPose();
            // 物品画在 z≈150 以上，描边、灰层、红点要再往上垫才不被盖住
            graphics.pose().pushPose();
            graphics.pose().translate(0F, 0F, 300F);
            int left = getX();
            int top = getY();
            if (!enabled(target)) {
                graphics.fill(left + 1, top + 1, left + 15, top + 15, 0xA0202020);
            }
            if (target == menu.target()) {
                int color = 0xFF55FF55;
                graphics.fill(left, top, left + 16, top + 1, color);
                graphics.fill(left, top + 15, left + 16, top + 16, color);
                graphics.fill(left, top, left + 1, top + 16, color);
                graphics.fill(left + 15, top, left + 16, top + 16, color);
            }
            if (target.needsLey && !menu.hasLey()) {
                graphics.fill(left + 11, top + 1, left + 15, top + 5, 0xFF000000);
                graphics.fill(left + 12, top + 2, left + 14, top + 4, 0xFFDD3333);
            }
            graphics.pose().popPose();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, name(target));
        }
    }

    /** 「当前目标 + 进度」小框（18×18）：槽位框里画目标的图标，进度从下往上盖一层浅绿；悬停写目标和进度 */
    private final class TargetBox implements ScreenElement {

        private final int x;
        private final int y;

        TargetBox(int x, int y) {
            this.x = x;
            this.y = y;
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
            return 18;
        }

        @Override
        public int height() {
            return 18;
        }

        @Override
        public void render(GuiGraphics graphics, int left, int top) {
            graphics.blit(ECTextures.GENERIC_BACKGROUND, left, top, 7, 83, 18, 18);
            Target target = menu.target();
            if (target == null) {
                return;
            }
            graphics.renderItem(new ItemStack(target.product), left + 1, top + 1);
            int max = menu.maxProgress();
            int filled = max > 0 ? ECTextures.scaled(Math.min(menu.progress(), max), max, 16) : 0;
            if (filled > 0) {
                graphics.pose().pushPose();
                graphics.pose().translate(0F, 0F, 300F);
                graphics.fill(left + 1, top + 17 - filled, left + 17, top + 17, 0x6055FF55);
                graphics.pose().popPose();
            }
        }

        @Override
        public List<Component> tooltip() {
            Target target = menu.target();
            if (target == null) {
                return List.of(Component.translatable(KEY + "status.no_target.hint"));
            }
            return List.of(name(target), Component.translatable(KEY + "progress", seconds(menu.progress()), seconds(menu.maxProgress()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
