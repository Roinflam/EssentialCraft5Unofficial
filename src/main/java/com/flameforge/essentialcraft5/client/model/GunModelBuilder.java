package com.flameforge.essentialcraft5.client.model;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.datagen.ECItemModelProvider;
import com.flameforge.essentialcraft5.gun.GunType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.client.model.generators.CustomLoaderBuilder;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

/**
 * 枪的物品模型（datagen 用，ECGuns）：
 * <pre>
 * { "parent": "forge:item/default-tool", "loader": "essentialcraft5:gun", "gun_type": "pistol",
 *   "textures": { "layer0": "essentialcraft5:item/guns/pistol/pistol_generic" } }
 * </pre>
 * 运行时由 {@link GunModel} 读（{@code gun_type} 决定部件贴图目录 {@code item/guns/<枪>/…}）。
 * <b>父模型不能用 {@code item/handheld}</b>：它的根是 {@code builtin/generated}，原版烘焙时见到这个根就直接走 ItemModelGenerator，
 * 自定义 loader 整个被跳过（{@code ModelBakery} 的 ModelBakerImpl.bake）；Forge 的 {@code forge:item/default-tool} 是同一套手持变换、根不是它（Forge 的桶模型也是这么做的）。
 * 只在 datagen 里用到；{@code ECGuns} 的模型 lambda 调 {@link #gunItem}，专用服不会执行、也不会加载本类。
 */
public final class GunModelBuilder extends CustomLoaderBuilder<ItemModelBuilder> {

    /** Forge 自带的手持物品父模型（和 item/handheld 同样的变换，但不以 builtin/generated 为根） */
    public static final ResourceLocation DEFAULT_TOOL = ResourceLocation.fromNamespaceAndPath("forge", "item/default-tool");

    private final GunType type;

    private GunModelBuilder(ItemModelBuilder parent, ExistingFileHelper helper, GunType type) {
        super(EssentialCraft5.id(GunModel.LOADER_NAME), parent, helper);
        this.type = type;
    }

    /** 生成一把枪的物品模型 */
    public static void gunItem(ECItemModelProvider provider, Item item, GunType type) {
        provider.getBuilder(provider.name(item))
                .parent(new ModelFile.UncheckedModelFile(DEFAULT_TOOL))
                .texture("layer0", provider.modLoc(GunModel.genericTexture(type)))
                .customLoader((parent, helper) -> new GunModelBuilder(parent, helper, type))
                .end();
    }

    @Override
    public JsonObject toJson(JsonObject json) {
        json = super.toJson(json);
        json.addProperty(GunModel.TYPE_KEY, type.id());
        return json;
    }
}
