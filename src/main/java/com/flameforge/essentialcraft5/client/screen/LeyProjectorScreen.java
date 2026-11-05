package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.LeyProjectorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.LeyProjectorBlockEntity.Status;
import com.flameforge.essentialcraft5.client.screen.element.ProgressBarElement;
import com.flameforge.essentialcraft5.client.screen.element.StatusTextElement;
import com.flameforge.essentialcraft5.menu.LeyProjectorMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 地脉投影仪界面。左栏沿用原作 GuiMagicalQuarry 的元素：MRU 球 (7, 4)、绑定宝石状态 (25, 22)、MRU 数值 (25, 58)；
 * 平衡值从原作的 (25, 40) 挪到原作空着的 (25, 4)，(25, 40) 换成状态条（悬停写原因：挡路那台的坐标、没有地脉、没有主人……）。
 * MRU 数值原作是 145 宽、压在右边的槽位上，这里改成 82 宽、放不下时整条缩小（{@link StatusTextElement}）。
 * <p>
 * 右边：绑定宝石 (108, 5)、透镜 (126, 5)、「保留岩石」开关 (146, 5)（16×16，圆石图标，右下角小方块绿 = 开、红 = 关），
 * 3×3 输出格（菜单里），输出格右边一根竖箭头 (163, 27) 是这一格的投影进度。坐标按元素宽度排的，没进游戏核对过。
 * <p>
 * 状态颜色照魔力修复器：工作绿、暂停 / 排队橙、缺东西黄、错误红（{@link Status#color}）。数值都从菜单同步来，不读客户端方块实体。
 */
public class LeyProjectorScreen extends MachineScreen<LeyProjectorMenu> {

    private static final String KEY = "gui.essentialcraft5.ley_projector.";
    private static final int TOGGLE_X = 146;
    private static final int TOGGLE_Y = 5;
    private static final ItemStack ROCK_ICON = new ItemStack(Items.COBBLESTONE);

    private KeepRockButton toggle;

    public LeyProjectorScreen(LeyProjectorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(balance(25, 4));
        elements.add(boundGemState(25, 22));
        elements.add(new StatusTextElement(25, 40, 82, () -> statusText(menu.status()), () -> menu.status().color,
                null, 0, this::statusTooltip));
        elements.add(new StatusTextElement(25, 58, 82,
                () -> Component.translatable("tooltip.essentialcraft5.mru", data().mru(), data().maxMRU()), () -> 0xFFFFFF));
        elements.add(new ProgressBarElement(163, 27, ProgressBarElement.Bar.ARROW_DOWN, ProgressBarElement.Grow.DOWN,
                menu::progress, menu::maxProgress));
    }

    @Override
    protected void init() {
        super.init();
        toggle = addRenderableWidget(new KeepRockButton(leftPos + TOGGLE_X, topPos + TOGGLE_Y));
    }

    static Component statusText(Status status) {
        return Component.translatable(switch (status) {
            case WORKING -> KEY + "status.working";
            case DISABLED -> KEY + "status.disabled";
            case NO_OWNER -> "gui.essentialcraft5.owner.none";
            case NO_LEY -> KEY + "status.no_ley";
            case DUPLICATE -> KEY + "status.duplicate";
            case POWERED -> "gui.essentialcraft5.devices.redstone_paused";
            case OUTPUT_FULL -> KEY + "status.output_full";
            case NO_MRU -> "gui.essentialcraft5.no_mru";
        });
    }

    private List<Component> statusTooltip() {
        List<Component> lines = new ArrayList<>();
        switch (menu.status()) {
            case WORKING -> lines.add(Component.translatable(KEY + "status.working.hint", seconds(menu.maxProgress())));
            case NO_OWNER -> lines.add(Component.translatable(KEY + "status.no_owner.hint"));
            case NO_LEY -> lines.add(Component.translatable(KEY + "status.no_ley.hint"));
            case DUPLICATE -> {
                BlockPos at = menu.blocker();
                if (at != null) {
                    lines.add(Component.translatable(KEY + "status.duplicate_at", at.getX(), at.getY(), at.getZ()));
                }
                lines.add(Component.translatable(KEY + "status.duplicate.hint").withStyle(ChatFormatting.GRAY));
            }
            case OUTPUT_FULL -> lines.add(Component.translatable(KEY + "status.output_full.hint"));
            default -> {
            }
        }
        return lines;
    }

    /** tick → 秒（20 的约数都能除尽，去掉多余的 0：20 → 1，30 → 1.5） */
    private static String seconds(int ticks) {
        return BigDecimal.valueOf(Math.max(ticks, 0)).divide(BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        if (toggle != null && toggle.isHovered()) {
            graphics.renderComponentTooltip(font, toggleTooltip(), mouseX, mouseY);
        } else if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index == LeyProjectorBlockEntity.SLOT_LENS) {
            graphics.renderComponentTooltip(font, List.of(Component.translatable(KEY + "lens_empty")), mouseX, mouseY);
        }
    }

    private Component toggleLabel() {
        return Component.translatable(KEY + "keep_rock", Component.translatable(KEY + (menu.keepRock() ? "keep_rock.on" : "keep_rock.off"))
                .withStyle(menu.keepRock() ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    private List<Component> toggleTooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(toggleLabel());
        lines.add(Component.translatable(KEY + "keep_rock.hint").withStyle(ChatFormatting.GRAY));
        // 岩石不进待放、每种一格、合计最多 2 格（LeyProjectorBlockEntity.MAX_ROCK_SLOTS，审查 LEY-1）
        lines.add(Component.translatable(KEY + "keep_rock.limit").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(KEY + "keep_rock.click").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /** 「保留岩石」开关：16×16 原版按钮底 + 缩小的圆石图标 + 右下角状态小方块 */
    private final class KeepRockButton extends AbstractButton {

        KeepRockButton(int x, int y) {
            super(x, y, 16, 16, Component.empty());
        }

        @Override
        public void onPress() {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, LeyProjectorMenu.BUTTON_KEEP_ROCK);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            graphics.pose().pushPose();
            graphics.pose().translate(getX() + 2, getY() + 2, 0F);
            graphics.pose().scale(0.75F, 0.75F, 1F);
            graphics.renderItem(ROCK_ICON, 0, 0);
            graphics.pose().popPose();
            // 物品画在 z≈150 以上，小方块要再往上垫才不被盖住
            graphics.pose().pushPose();
            graphics.pose().translate(0F, 0F, 300F);
            int left = getX() + 11;
            int top = getY() + 11;
            graphics.fill(left - 1, top - 1, left + 4, top + 4, 0xFF000000);
            graphics.fill(left, top, left + 3, top + 3, menu.keepRock() ? 0xFF55FF55 : 0xFFDD3333);
            graphics.pose().popPose();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, toggleLabel());
        }
    }
}
