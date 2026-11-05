package com.flameforge.essentialcraft5.block.vault;

import com.flameforge.essentialcraft5.registry.ECMaterials;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * 封印宝库谜题里的四种元素（batch13.md §6.4）：每座宝库祭座刻一种，碑文四句各对应一种；往祭座放的是第一章就有的元素魔晶
 * （主世界元素矿掉的 {@code fire_drops} / {@code water_drops} / {@code earth_drops} / {@code air_drops}）。
 * <p>
 * 序号 {@link #ordinal()} 写进宝库核心的 NBT（{@code VaultCoreBlockEntity}），也是祭座方块状态 {@code element} 的值；<b>上线后顺序不能改</b>。
 * 碑文里的叫法：火「烈焰」、水「潮声」、地「山岩」、风「长风」（翻译键 {@code message.essentialcraft5.vault.riddle.<名字>.<次序>}）。
 */
public enum VaultElement implements StringRepresentable {
    FIRE(ECMaterials.FIRE_DROPS),
    WATER(ECMaterials.WATER_DROPS),
    EARTH(ECMaterials.EARTH_DROPS),
    AIR(ECMaterials.AIR_DROPS);

    private static final VaultElement[] VALUES = values();

    private final Supplier<Item> crystal;
    private final String serializedName;

    VaultElement(Supplier<? extends Item> crystal) {
        this.crystal = crystal::get;
        this.serializedName = name().toLowerCase(Locale.ROOT);
    }

    /** 往这种元素的祭座里放的物品 */
    public Item crystal() {
        return crystal.get();
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    /** 序号 → 元素；越界按火 */
    public static VaultElement byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : FIRE;
    }

    /** 手里这件是不是四种元素魔晶之一；是的话返回哪一种 */
    @Nullable
    public static VaultElement ofCrystal(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        for (VaultElement element : VALUES) {
            if (stack.is(element.crystal())) {
                return element;
            }
        }
        return null;
    }
}
