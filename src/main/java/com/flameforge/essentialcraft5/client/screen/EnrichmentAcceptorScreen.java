package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.client.screen.element.TextBarElement;
import com.flameforge.essentialcraft5.menu.EnrichmentAcceptorMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 富集仓 MRU 接收器界面，照原作 GuiMRUAcceptor：只有一条宝石状态 (48, 50)。
 * 没连上已成形的仓时这条写「未连接富集仓」（红色），这时宝石状态没有意义。
 */
public class EnrichmentAcceptorScreen extends MachineScreen<EnrichmentAcceptorMenu> {

    public EnrichmentAcceptorScreen(EnrichmentAcceptorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(new TextBarElement(48, 50, TextBarElement.NORMAL_WIDTH,
                () -> menu.linked() ? data().linkStatus().label() : Component.translatable("gui.essentialcraft5.enrichment.no_chamber"),
                () -> menu.linked() ? data().linkStatus().color : 0xFF0000, true));
    }
}
