package com.flameforge.essentialcraft5.client.screen.element;

import com.flameforge.essentialcraft5.client.screen.ECTextures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * 槽位框里画一个只看不能拿的物品图标（18×18）。原作 GuiMRUGenerated 的自然熔炉分支画过这种框（显示正在烧的那株植物）；
 * 第四批自然熔炉改成真的植物格之后，<b>目前没有界面在用</b>，留给以后要显示「只看不能拿」的界面。
 * 物品按 {@code Item.getId} 同步（Forge 会把服务端的数字 id 表同步给客户端，两边一致），0 是空气，不画。
 */
public record ItemIconElement(int x, int y, IntSupplier itemId) implements ScreenElement {

    private ItemStack stack() {
        Item item = Item.byId(itemId.getAsInt());
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    public void render(GuiGraphics graphics, int left, int top) {
        graphics.blit(ECTextures.SLOT_COMMON, left, top, 0, 0, 18, 18);
        ItemStack stack = stack();
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, left + 1, top + 1);
        }
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
    public List<Component> tooltip() {
        ItemStack stack = stack();
        return stack.isEmpty() ? List.of() : List.of(stack.getHoverName());
    }
}
