package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.menu.DimensionalTransceiverMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * MRU 跨维度收发器界面，元素与坐标照搬原作 GuiMRUDimTransciever：MRU 球 (7, 4)、宝石状态 (88, 4)、平衡值 (46, 31)、MRU 存量 (25, 58)；
 * 宝石格 (29, 32) 在菜单里。原作宝石状态在客户端现算，宝石指向别的维度时什么都不显示；这里的状态由服务端算好随菜单同步，跨维度也显示。
 */
public class DimensionalTransceiverScreen extends MachineScreen<DimensionalTransceiverMenu> {

    public DimensionalTransceiverScreen(DimensionalTransceiverMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(boundGemState(88, 4));
        elements.add(balance(46, 31));
        elements.add(mruState(25, 58));
    }
}
