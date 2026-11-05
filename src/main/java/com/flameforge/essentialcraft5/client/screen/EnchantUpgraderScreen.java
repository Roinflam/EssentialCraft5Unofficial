package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.blockentity.EnchantUpgraderBlockEntity.Status;
import com.flameforge.essentialcraft5.client.screen.element.StatusTextElement;
import com.flameforge.essentialcraft5.client.screen.element.TextBarElement;
import com.flameforge.essentialcraft5.enchant.EnchantUpgrades;
import com.flameforge.essentialcraft5.enchant.EnchantUpgrades.Entry;
import com.flameforge.essentialcraft5.enchant.EnchantUpgrades.Need;
import com.flameforge.essentialcraft5.enchant.EnchantUpgrades.Step;
import com.flameforge.essentialcraft5.menu.EnchantUpgraderMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 附魔增幅台 / 升华台界面。左边 MRU 条 (7, 4)，状态条 (27, 4)（悬停写这一步要什么），右上 ESPE 球 (134, 4)；
 * 第二行是目标等级和四个调节按钮 (−5 −1 +1 +5)；中间一排槽位见 {@link EnchantUpgraderMenu}；下面一条写下一级是什么、做到几成。
 * 价格读服务端配置（进服时 Forge 发给客户端），数值都从菜单同步来，不读客户端方块实体。
 */
public class EnchantUpgraderScreen extends MachineScreen<EnchantUpgraderMenu> {

    private static final String KEY = "gui.essentialcraft5.enchant_upgrade.";
    /** 菜单里附魔书槽的序号（addMachineSlot 的第一个） */
    private static final int MENU_BOOK = 0;
    private static final String[] BUTTON_LABELS = {"-5", "-1", "+1", "+5"};

    public EnchantUpgraderScreen(EnchantUpgraderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(new StatusTextElement(27, 4, 100, () -> statusText(menu.status()), () -> menu.status().color,
                null, 0, this::stepTooltip));
        elements.add(espeStorage(134, 4));
        elements.add(new TextBarElement(27, 22, 68, () -> Component.translatable(KEY + "target", menu.targetLevel()),
                () -> 0xFFFFFF, false));
        elements.add(new StatusTextElement(27, 64, 140, this::bottomText, () -> menu.status() == Status.WORKING ? 0x00FF00 : 0xD0D0D0,
                null, 0, this::stepTooltip));
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < BUTTON_LABELS.length; i++) {
            int id = i;
            addRenderableWidget(Button.builder(Component.literal(BUTTON_LABELS[i]), b -> {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                }
            }).bounds(leftPos + 98 + i * 19, topPos + 23, 17, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable(KEY + "target.hint")))
                    .build());
        }
    }

    private ItemStack book() {
        return menu.slots.get(MENU_BOOK).getItem();
    }

    static Component statusText(Status status) {
        return Component.translatable(switch (status) {
            case IDLE -> KEY + "status.idle";
            case BAD_BOOK -> KEY + "status.bad_book";
            case NOT_ACCEPTED -> KEY + "status.not_accepted";
            case TOO_LOW -> KEY + "status.too_low";
            case OUTPUT_FULL -> KEY + "status.output_full";
            case POWERED -> "gui.essentialcraft5.devices.redstone_paused";
            case NO_MATERIAL -> KEY + "status.no_material";
            case NO_OWNER -> KEY + "status.no_owner";
            case LOW_TIER -> KEY + "status.low_tier";
            case MATRIX_FULL -> KEY + "status.matrix_full";
            case TOO_EXPENSIVE -> KEY + "status.too_expensive";
            case NO_MRU -> "gui.essentialcraft5.no_mru";
            case NO_ESPE -> KEY + "status.no_espe";
            case WORKING -> KEY + "status.working";
        });
    }

    /** 下面那条：做着的时候写「第 n 级 xx%」，否则写书的下一级；没书时写提示 */
    private Component bottomText() {
        Entry entry = EnchantUpgrades.read(book());
        if (menu.stepLevel() > 0 && menu.required() > 0) {
            int percent = (int) Math.min(100L, (long) menu.progress() * 100L / menu.required());
            return Component.translatable(KEY + "progress", menu.stepLevel(), percent);
        }
        if (entry == null) {
            return Component.translatable(KEY + "no_book");
        }
        return Component.translatable(KEY + "next", entry.enchantment().getFullname(entry.level()),
                Component.translatable("enchantment.level." + (entry.level() + 1)));
    }

    /** 悬停：这一步要付的全部东西（没有可升的书时写原因的提示） */
    private List<Component> stepTooltip() {
        List<Component> lines = new ArrayList<>();
        Entry entry = EnchantUpgrades.read(book());
        Status status = menu.status();
        String hint = switch (status) {
            case BAD_BOOK -> KEY + "status.bad_book.hint";
            case NOT_ACCEPTED -> KEY + "status.not_accepted.hint";
            case TOO_LOW -> KEY + "status.too_low.hint";
            case OUTPUT_FULL -> KEY + "status.output_full.hint";
            case NO_OWNER -> KEY + "status.no_owner.hint";
            case LOW_TIER -> KEY + "status.low_tier.hint";
            case MATRIX_FULL -> KEY + "status.matrix_full.hint";
            case TOO_EXPENSIVE -> KEY + "status.too_expensive.hint";
            default -> null;
        };
        if (hint != null) {
            lines.add(Component.translatable(hint).withStyle(ChatFormatting.GRAY));
        }
        if (entry == null || !EnchantUpgrades.canUpgrade(entry, menu.tier())) {
            if (hint == null) {
                lines.add(Component.translatable(KEY + "status.idle.hint").withStyle(ChatFormatting.GRAY));
            }
            return lines;
        }
        int next = entry.level() + 1;
        Step step = EnchantUpgrades.step(menu.tier(), next);
        lines.add(Component.translatable(KEY + "step.title", entry.enchantment().getFullname(entry.level()),
                Component.translatable("enchantment.level." + next)).withStyle(ChatFormatting.WHITE));
        lines.add(Component.translatable(KEY + "step.mru", step.mru(), step.mruPerTick()));
        if (step.espe() > 0) {
            lines.add(Component.translatable(KEY + "step.espe", step.espe()));
        }
        lines.add(Component.translatable(KEY + "step.time", seconds(step.ticks())));
        lines.add(need(KEY + "step.a", step.a()));
        lines.add(need(KEY + "step.b", step.b()));
        if (step.matrix() > 0) {
            lines.add(Component.translatable(KEY + "step.matrix", step.matrix()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return lines;
    }

    private static Component need(String key, Need need) {
        ItemStack first = need.any().isEmpty() ? ItemStack.EMPTY : new ItemStack(need.any().get(0));
        Component name = first.isEmpty() ? Component.literal("?") : first.getHoverName();
        return Component.translatable(need.any().size() > 1 ? key + ".any" : key, need.count(), name);
    }

    private static String seconds(int ticks) {
        return BigDecimal.valueOf(Math.max(ticks, 0)).divide(BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString();
    }
}
