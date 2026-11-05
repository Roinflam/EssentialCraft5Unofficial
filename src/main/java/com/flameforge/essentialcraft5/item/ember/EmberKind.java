package com.flameforge.essentialcraft5.item.ember;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECVoid;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * 8 种余烬（原作 {@code ItemEmber.unlocalisedName} 的前 8 个，meta 8「unknown」是占位、没移植）。顺序照原作，
 * 余烬元素剑的组合表（{@link EmberEffect#COMBOS}）按这个顺序两两配对。
 * <p>
 * 原作把翻译键里的种类名当 id 用（{@code embercorrupted}），剑的效果表却按 {@code "corruption"} 去匹配，腐化余烬的 7 对组合从没生效；
 * 这里按注册名认（{@code <种类>_ember}，{@code ECVoid} 注册），剑上存的也是注册名。
 */
public enum EmberKind {

    ACIDIC(ECVoid.ACIDIC_EMBER),
    CHAOS(ECVoid.CHAOS_EMBER),
    /** 阴燃余烬（原作 Smouldering Ember，翻译键 embercommon） */
    COMMON(ECVoid.COMMON_EMBER),
    CORRUPTED(ECVoid.CORRUPTED_EMBER),
    CRYSTAL(ECVoid.CRYSTAL_EMBER),
    DIVINE(ECVoid.DIVINE_EMBER),
    MAGICAL(ECVoid.MAGICAL_EMBER),
    /** 燃烧余烬（原作 Burning Ember，翻译键 emberflame） */
    FLAME(ECVoid.FLAME_EMBER);

    private static final EmberKind[] VALUES = values();

    private final Supplier<Item> item;
    private final ResourceLocation id;

    EmberKind(RegistryObject<Item> item) {
        this.item = item;
        this.id = EssentialCraft5.id(name().toLowerCase(Locale.ROOT) + "_ember");
    }

    /** 注册名 {@code essentialcraft5:<种类>_ember}（不用等注册表，剑的 NBT 和纯函数都按它比） */
    public ResourceLocation id() {
        return id;
    }

    public Item item() {
        return item.get();
    }

    /** 按注册名找；不是余烬返回 null */
    @Nullable
    public static EmberKind byId(@Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        for (EmberKind kind : VALUES) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }

    /** 按物品本身认（不看 NBT、数量）；不是余烬返回 null */
    @Nullable
    public static EmberKind of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Item target = stack.getItem();
        for (EmberKind kind : VALUES) {
            if (kind.item() == target) {
                return kind;
            }
        }
        return null;
    }
}
