package com.flameforge.essentialcraft5.client.screen;

import com.flameforge.essentialcraft5.client.screen.element.FlameProgressElement;
import com.flameforge.essentialcraft5.client.screen.element.MRUGeneratedElement;
import com.flameforge.essentialcraft5.client.screen.element.ScreenElement;
import com.flameforge.essentialcraft5.menu.UltraNaturalFurnaceMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * 终极自然熔炉界面，元素与坐标照搬原作 GuiUltraFlowerBurner（和自然熔炉同一套）：
 * 原作 GuiMRUGenerated("ultraNaturalFurnace") 画「N MRU/t」文字（正在烧的那件的产量）、右边 82 像素处一个槽位框装着正在烧的植物、
 * 槽位框上方一个燃烧火苗。那个槽位框 (107, 40) 现在是真的格子（菜单槽位 (108, 41)），火苗仍在它正上方 (107, 25)，
 * 正在烧的是哪一件改成悬停火苗时写出来；火苗按这一件的总燃烧时间画（原作按 600 刻画，原木会溢出）。
 */
public class UltraNaturalFurnaceScreen extends MachineScreen<UltraNaturalFurnaceMenu> {

    public UltraNaturalFurnaceScreen(UltraNaturalFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        elements.add(mruStorage(7, 4));
        elements.add(mruState(25, 58));
        elements.add(balance(25, 22));
        elements.add(new MRUGeneratedElement(25, 40, menu::generation, "gui.essentialcraft5.ultra_natural_furnace.hint", 3));
        elements.add(new BurningFlameElement(new FlameProgressElement(UltraNaturalFurnaceMenu.PLANT_SLOT_X - 1, 25,
                menu::burnTime, menu::maxBurnTime), menu));
    }

    /** 燃烧火苗，悬停时写正在烧哪一件、还剩几秒（同自然熔炉界面那个，自然熔炉的是私有类，这里另写一份） */
    private record BurningFlameElement(FlameProgressElement flame, UltraNaturalFurnaceMenu menu) implements ScreenElement {

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
                return List.of(Component.translatable("gui.essentialcraft5.ultra_natural_furnace.idle").withStyle(ChatFormatting.GRAY));
            }
            int seconds = (burn + 19) / 20;
            return List.of(Component.translatable("gui.essentialcraft5.natural_furnace.burning", new ItemStack(item).getHoverName()),
                    Component.translatable("gui.essentialcraft5.natural_furnace.burn_left", seconds).withStyle(ChatFormatting.GRAY));
        }
    }
}
