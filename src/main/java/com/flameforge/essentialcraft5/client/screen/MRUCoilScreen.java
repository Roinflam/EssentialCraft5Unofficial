package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.MRUCoilBlockEntity;
import com.flameforge.essentialcraft5.client.screen.element.TextBarElement;
import com.flameforge.essentialcraft5.menu.MRUCoilMenu;
import com.flameforge.essentialcraft5.registry.ECTesla;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * MRU 特斯拉线圈界面。原作 GuiMRUCoil：MRU 球 (7, 4)、平衡值 (25, 4)、绑定宝石状态 (25, 22)、MRU 数值 (25, 58)；
 * 槽位（菜单里）绑定宝石 (108, 23)、玩家白名单 (140, 41)。
 * <p>
 * 新加：
 * <ul>
 *   <li>(25, 40)（原作空着）状态条：射程 / 结构不完整 / 红石停机 / MRU 不足，悬停写原因和怎么办（结构不对时写出第一格不对的坐标）。</li>
 *   <li>(109, 4)（原作空着）「打玩家」开关：只有放下它的人和创造模式能点（服务端再校验一次）；没有主人的线圈显示「不打玩家」、点不动。</li>
 *   <li>悬停 0、1 号格：空着时说明放什么；看界面的人不能拿放时加一行「只有放下它的人能拿放」。</li>
 * </ul>
 * 数值都从菜单同步来（{@link MRUCoilMenu}），不读客户端方块实体。坐标没进游戏核对过，按原作元素宽度排的。
 */
public class MRUCoilScreen extends MachineScreen<MRUCoilMenu> {

    private static final int TOGGLE_X = 109;
    private static final int TOGGLE_Y = 4;
    private static final int TOGGLE_WIDTH = 62;
    private static final int TOGGLE_HEIGHT = 18;

    private ToggleButton toggle;

    public MRUCoilScreen(MRUCoilMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(balance(25, 4));
        elements.add(boundGemState(25, 22));
        elements.add(new StatusElement(menu));
        elements.add(mruState(25, 58));
    }

    @Override
    protected void init() {
        super.init();
        toggle = addRenderableWidget(new ToggleButton(leftPos + TOGGLE_X, topPos + TOGGLE_Y));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (toggle != null) {
            toggle.active = canToggle();
        }
    }

    private boolean canToggle() {
        return menu.hasFlag(MRUCoilMenu.FLAG_EDITABLE) && menu.hasFlag(MRUCoilMenu.FLAG_HAS_OWNER) && menu.hasFlag(MRUCoilMenu.FLAG_PLAYERS_ALLOWED);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        if (toggle != null && toggle.isHovered()) {
            graphics.renderComponentTooltip(font, toggleTooltip(), mouseX, mouseY);
        } else if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index < MRUCoilBlockEntity.SLOTS) {
            List<Component> lines = new ArrayList<>();
            String key = hoveredSlot.index == MRUCoilBlockEntity.SLOT_GEM ? "gui.essentialcraft5.mru_coil.slot.gem" : "gui.essentialcraft5.mru_coil.slot.list";
            lines.add(Component.translatable(key + ".0"));
            lines.add(Component.translatable(key + ".1").withStyle(ChatFormatting.GRAY));
            if (!menu.hasFlag(MRUCoilMenu.FLAG_EDITABLE)) {
                lines.add(Component.translatable("gui.essentialcraft5.mru_coil.slot.locked").withStyle(ChatFormatting.RED));
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    /** 放着东西的 0、1 号格：物品提示末尾加一行「只有放下它的人能拿放」 */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = super.getTooltipFromContainerItem(stack);
        if (hoveredSlot != null && hoveredSlot.index < MRUCoilBlockEntity.SLOTS && !menu.hasFlag(MRUCoilMenu.FLAG_EDITABLE)) {
            lines = new ArrayList<>(lines);
            lines.add(Component.translatable("gui.essentialcraft5.mru_coil.slot.locked").withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    private Component toggleLabel() {
        if (!menu.hasFlag(MRUCoilMenu.FLAG_PLAYERS_ALLOWED) || !menu.hasFlag(MRUCoilMenu.FLAG_HAS_OWNER)) {
            return Component.translatable("gui.essentialcraft5.mru_coil.players.never").withStyle(ChatFormatting.GRAY);
        }
        return menu.hasFlag(MRUCoilMenu.FLAG_HURT_PLAYERS)
                ? Component.translatable("gui.essentialcraft5.mru_coil.players.on").withStyle(ChatFormatting.RED)
                : Component.translatable("gui.essentialcraft5.mru_coil.players.off").withStyle(ChatFormatting.GREEN);
    }

    private List<Component> toggleTooltip() {
        List<Component> lines = new ArrayList<>();
        if (!menu.hasFlag(MRUCoilMenu.FLAG_PLAYERS_ALLOWED)) {
            lines.add(Component.translatable("gui.essentialcraft5.mru_coil.players.disabled"));
            return lines;
        }
        if (!menu.hasFlag(MRUCoilMenu.FLAG_HAS_OWNER)) {
            lines.add(Component.translatable("gui.essentialcraft5.mru_coil.players.no_owner.0"));
            lines.add(Component.translatable("gui.essentialcraft5.mru_coil.players.no_owner.1").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        String onHint = menu.hasFlag(MRUCoilMenu.FLAG_TRESPASSERS_ONLY)
                ? "gui.essentialcraft5.mru_coil.players.on.hint.trespassers" : "gui.essentialcraft5.mru_coil.players.on.hint";
        lines.add(Component.translatable(menu.hasFlag(MRUCoilMenu.FLAG_HURT_PLAYERS) ? onHint : "gui.essentialcraft5.mru_coil.players.off.hint"));
        lines.add(Component.translatable("gui.essentialcraft5.mru_coil.players.rules").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(menu.hasFlag(MRUCoilMenu.FLAG_EDITABLE)
                ? "gui.essentialcraft5.mru_coil.players.click" : "gui.essentialcraft5.mru_coil.players.locked").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /** 「打玩家」开关：文字按状态变，太长时原版按钮会自动横向滚动 */
    private final class ToggleButton extends AbstractButton {

        ToggleButton(int x, int y) {
            super(x, y, TOGGLE_WIDTH, TOGGLE_HEIGHT, Component.empty());
            this.active = false;
        }

        @Override
        public Component getMessage() {
            return toggleLabel();
        }

        @Override
        public void onPress() {
            if (minecraft != null && minecraft.gameMode != null && canToggle()) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MRUCoilMenu.BUTTON_TOGGLE_PLAYERS);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, toggleLabel());
        }
    }

    /** 状态条 (25, 40)：射程 / 结构不完整 / 红石停机 / MRU 不足 / 检查中 */
    private static final class StatusElement extends TextBarElement {
        private final MRUCoilMenu menu;

        StatusElement(MRUCoilMenu menu) {
            super(25, 40, NORMAL_WIDTH, () -> label(menu), () -> color(menu.status()), true);
            this.menu = menu;
        }

        private static Component label(MRUCoilMenu menu) {
            MRUCoilBlockEntity.Status status = menu.status();
            if (status == MRUCoilBlockEntity.Status.ACTIVE) {
                return Component.translatable("gui.essentialcraft5.mru_coil.status.active", menu.radius());
            }
            return Component.translatable("gui.essentialcraft5.mru_coil.status." + key(status));
        }

        private static String key(MRUCoilBlockEntity.Status status) {
            return status == null ? "unknown" : switch (status) {
                case UNKNOWN -> "unknown";
                case INCOMPLETE -> "incomplete";
                case REDSTONE -> "redstone";
                case NO_MRU -> "no_mru";
                case ACTIVE -> "active";
            };
        }

        private static int color(MRUCoilBlockEntity.Status status) {
            ChatFormatting format = status == null ? ChatFormatting.GRAY : switch (status) {
                case ACTIVE -> ChatFormatting.GREEN;
                case INCOMPLETE -> ChatFormatting.RED;
                case REDSTONE, NO_MRU -> ChatFormatting.GOLD;
                case UNKNOWN -> ChatFormatting.GRAY;
            };
            Integer rgb = format.getColor();
            return rgb == null ? 0xFFFFFF : rgb;
        }

        @Override
        public List<Component> tooltip() {
            MRUCoilBlockEntity.Status status = menu.status();
            List<Component> lines = new ArrayList<>();
            String key = "gui.essentialcraft5.mru_coil.status." + key(status) + ".hint";
            switch (status == null ? MRUCoilBlockEntity.Status.UNKNOWN : status) {
                case ACTIVE -> {
                    lines.add(Component.translatable(key + ".0", menu.radius()));
                    lines.add(Component.translatable(key + ".1", menu.mruPerHit()).withStyle(ChatFormatting.GRAY));
                }
                case INCOMPLETE -> {
                    BlockPos at = menu.mismatchPos();
                    if (at != null) {
                        lines.add(Component.translatable(StructurePattern.KEY_MISMATCH, ECTesla.BASE_RULE.legend(), at.getX(), at.getY(), at.getZ())
                                .withStyle(ChatFormatting.RED));
                    }
                    lines.add(Component.translatable(key + ".0"));
                    lines.add(Component.translatable(key + ".1").withStyle(ChatFormatting.GRAY));
                }
                case NO_MRU -> {
                    lines.add(Component.translatable(key + ".0", menu.mruPerHit()));
                    lines.add(Component.translatable(key + ".1").withStyle(ChatFormatting.GRAY));
                }
                default -> lines.add(Component.translatable(key));
            }
            return lines;
        }
    }
}
