package com.flameforge.essentialcraft5.datagen;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.registry.ECItems;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 物品模型：遍历 ECItems 里登记的物品，执行各自 ItemSpec 里的模型生成。方块物品的模型随方块在 ECBlockStateProvider 里生成。
 * 这里的公开方法是给 ItemSpec 的 lambda 用的工具。
 */
public class ECItemModelProvider extends ItemModelProvider {

    public ECItemModelProvider(PackOutput output, ExistingFileHelper files) {
        super(output, EssentialCraft5.MODID, files);
    }

    @Override
    protected void registerModels() {
        for (ECItems.Entry entry : ECItems.entries()) {
            entry.spec().model().generate(this, entry.item().get());
        }
    }

    public String name(Item item) {
        return ForgeRegistries.ITEMS.getKey(item).getPath();
    }

    public ResourceLocation tex(String texture) {
        return texture.contains(":") ? ResourceLocation.parse(texture) : modLoc(texture);
    }

    public ItemModelBuilder generated(Item item, String texture) {
        return generated(name(item), texture);
    }

    public ItemModelBuilder generated(String name, String texture) {
        return withExistingParent(name, mcLoc("item/generated")).texture("layer0", tex(texture));
    }

    public ItemModelBuilder handheld(Item item, String texture) {
        return withExistingParent(name(item), mcLoc("item/handheld")).texture("layer0", tex(texture));
    }

    /** 原作 bound_gem 的 active=false/true 两个变体 → 物品属性 essentialcraft5:bound 切换 */
    public void boundGem(Item item) {
        ItemModelBuilder active = generated("bound_gem_active", "item/gem_bound_active");
        generated(item, "item/gem_bound").override().predicate(BoundGemItem.BOUND_PREDICATE, 1F).model(active).end();
    }
}
