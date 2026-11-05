package com.flameforge.essentialcraft5.client.model;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.datagen.ECItemModelProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.model.generators.CustomLoaderBuilder;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

/**
 * 余烬元素剑的物品模型（datagen 用，ECEmberForge）：
 * <pre>
 * { "parent": "forge:item/default-tool", "loader": "essentialcraft5:forged_sword",
 *   "textures": { "layer0": "essentialcraft5:item/tools/elementalsword" } }
 * </pre>
 * 运行时由 {@link ForgedSwordModel} 读。父模型用 Forge 的 {@code forge:item/default-tool}（和 item/handheld 同样的手持变换），
 * 不能用 {@code item/handheld}：它的根是 {@code builtin/generated}，原版见到这个根就直接走 ItemModelGenerator，自定义 loader 整个被跳过（同枪的模型）。
 * 只在 datagen 里用到，专用服不会加载本类。
 */
public final class ForgedSwordModelBuilder extends CustomLoaderBuilder<ItemModelBuilder> {

    /** Forge 自带的手持物品父模型（和 item/handheld 同样的变换，但不以 builtin/generated 为根） */
    public static final ResourceLocation DEFAULT_TOOL = ResourceLocation.fromNamespaceAndPath("forge", "item/default-tool");

    private ForgedSwordModelBuilder(ItemModelBuilder parent, ExistingFileHelper helper) {
        super(EssentialCraft5.id(ForgedSwordModel.LOADER_NAME), parent, helper);
    }

    /** 生成剑的物品模型 */
    public static void swordItem(ECItemModelProvider provider, Item item) {
        provider.getBuilder(provider.name(item))
                .parent(new ModelFile.UncheckedModelFile(DEFAULT_TOOL))
                .texture("layer0", provider.modLoc(ForgedSwordModel.WHOLE_SWORD))
                .customLoader(ForgedSwordModelBuilder::new)
                .end();
    }
}
