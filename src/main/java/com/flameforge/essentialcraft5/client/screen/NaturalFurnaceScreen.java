package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.client.screen.element.FlameProgressElement;
import com.flameforge.essentialcraft5.client.screen.element.MRUGeneratedElement;
import com.flameforge.essentialcraft5.client.screen.element.ScreenElement;
import com.flameforge.essentialcraft5.menu.NaturalFurnaceMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * 自然熔炉界面，元素与坐标照搬原作 GuiFlowerBurner：
 * 原作 GuiMRUGenerated("naturalFurnace") 画「N MRU/t」文字、右边 82 像素处一个槽位框装着正在烧的植物、槽位框上方一个燃烧火苗。
 * <p>
 * 第四批改造（batch4.md §0.0）：那个槽位框 (107, 40) 现在是真的植物格（菜单槽位 (108, 41)，框由 MachineScreen 按槽位画），
 * 火苗仍在它正上方 (107, 25)；正在烧的是哪一株不再画在框里（格子里放的是还没烧的），改成悬停火苗时写出来。
 * 文字条悬停时说明怎么用。第三批加的主人状态条（左上角 (25, 4)）随主人规则一起去掉了。
 */
public class NaturalFurnaceScreen extends MachineScreen<NaturalFurnaceMenu> {

    public NaturalFurnaceScreen(NaturalFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(mruState(25, 58));
        elements.add(balance(25, 22));
        elements.add(new MRUGeneratedElement(25, 40, menu::generation, "gui.essentialcraft5.natural_furnace.hint", 3));
        elements.add(new BurningFlameElement(new FlameProgressElement(NaturalFurnaceMenu.PLANT_SLOT_X - 1, 25,
                menu::burnTime, menu::maxBurnTime), menu));
    }

    /** 燃烧火苗，悬停时写正在烧哪一株、还剩几秒 */
    private record BurningFlameElement(FlameProgressElement flame, NaturalFurnaceMenu menu) implements ScreenElement {

        @Override
        public int x() {
            return flame.x();
        }

        @Override
        public int y() {
            return flame.y();
        }

        @Override
        public void render(GuiGraphics graphics, int left, int top) {
            flame.render(graphics, left, top);
        }

        @Override
        public int width() {
            return flame.width();
        }

        @Override
        public int height() {
            return flame.height();
        }

        @Override
        public List<Component> tooltip() {
            int burn = menu.burnTime();
            Item item = Item.byId(menu.burningItemId());
            if (burn <= 0 || item == Items.AIR) {
                return List.of(Component.translatable("gui.essentialcraft5.natural_furnace.idle").withStyle(ChatFormatting.GRAY));
            }
            int seconds = (burn + 19) / 20;
            return List.of(Component.translatable("gui.essentialcraft5.natural_furnace.burning", new ItemStack(item).getHoverName()),
                    Component.translatable("gui.essentialcraft5.natural_furnace.burn_left", seconds).withStyle(ChatFormatting.GRAY));
        }
    }
}
