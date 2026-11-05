package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.datagen.ECItemModelProvider;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 物品在注册时声明的规格（方块物品不用，归 BlockSpec 管）：datagen 靠它生成物品模型和标签。
 * <pre>
 * ECItems.register("mru_catcher", () -&gt; new Item(new Item.Properties()), ItemSpec.generated("item/misc/mrucatcher"));
 * </pre>
 * lambda 只在 datagen 时执行。
 */
public final class ItemSpec {

    @FunctionalInterface
    public interface ModelGen {
        void generate(ECItemModelProvider provider, Item item);
    }

    final ModelGen model;
    final List<TagKey<Item>> tags = new ArrayList<>();
    boolean creativeTab = true;
    boolean decoration = false;

    private ItemSpec(ModelGen model) {
        this.model = model;
    }

    public static ItemSpec of(ModelGen model) {
        return new ItemSpec(model);
    }

    /** 普通物品：item/generated + 一张贴图（路径相对 textures/，如 "item/gem_bound"） */
    public static ItemSpec generated(String texture) {
        return new ItemSpec((provider, item) -> provider.generated(item, texture));
    }

    /** 工具、武器：item/handheld */
    public static ItemSpec handheld(String texture) {
        return new ItemSpec((provider, item) -> provider.handheld(item, texture));
    }

    @SafeVarargs
    public final ItemSpec tags(TagKey<Item>... tags) {
        Collections.addAll(this.tags, tags);
        return this;
    }

    public ItemSpec hidden() {
        this.creativeTab = false;
        return this;
    }

    public boolean inCreativeTab() {
        return creativeTab;
    }

    /** 放进创造栏第三页「源质魔法：装饰」（魔力凿这类只和装饰有关的物品，第五批 F5-3），不进「物品」页 */
    public ItemSpec decoration() {
        this.decoration = true;
        return this;
    }

    public boolean isDecoration() {
        return decoration;
    }

    public ModelGen model() {
        return model;
    }

    public List<TagKey<Item>> tagList() {
        return tags;
    }
}
