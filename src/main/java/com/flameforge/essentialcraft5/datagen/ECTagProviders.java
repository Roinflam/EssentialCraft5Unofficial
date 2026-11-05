package com.flameforge.essentialcraft5.datagen;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 方块与物品标签，两个来源：
 * <ul>
 *   <li>注册时声明的（BlockSpec / ItemSpec 的 tags），代码里要查的标签常量放 registry/ECTags。</li>
 *   <li>tools/tags/*.json（每个模块一个文件）：{ "blocks": { "标签": [成员…] }, "items": { … } }。
 *       成员写注册名（"essentialcraft5:mru_catcher"、"minecraft:iron_ingot"），或 "#标签" 引用别的标签。
 *       本模组的成员必须存在（写错 datagen 报错），别的命名空间的成员按可选处理。
 *       原作矿辞到标签的对照见 tools/oredict_map.json。</li>
 * </ul>
 */
public final class ECTagProviders {

    private ECTagProviders() {
    }

    public static class Blocks extends BlockTagsProvider {

        public Blocks(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper files) {
            super(output, lookup, EssentialCraft5.MODID, files);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            for (ECBlocks.Entry entry : ECBlocks.entries()) {
                for (TagKey<Block> tag : entry.spec().blockTagList()) {
                    tag(tag).add(ResourceKey.create(Registries.BLOCK, entry.block().getId()));
                }
            }
            fromFiles("blocks", Registries.BLOCK, this::tag);
        }
    }

    public static class Items extends ItemTagsProvider {

        public Items(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
                     CompletableFuture<TagLookup<Block>> blockTags, ExistingFileHelper files) {
            super(output, lookup, blockTags, EssentialCraft5.MODID, files);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            for (ECItems.Entry entry : ECItems.entries()) {
                for (TagKey<Item> tag : entry.spec().tagList()) {
                    tag(tag).add(ResourceKey.create(Registries.ITEM, entry.item().getId()));
                }
            }
            for (ECBlocks.Entry entry : ECBlocks.entries()) {
                for (TagKey<Item> tag : entry.spec().itemTagList()) {
                    tag(tag).add(ResourceKey.create(Registries.ITEM, entry.block().getId()));
                }
            }
            fromFiles("items", Registries.ITEM, this::tag);
        }
    }

    private static <T> void fromFiles(String section, ResourceKey<? extends Registry<T>> registry,
                                      Function<TagKey<T>, TagsProvider.TagAppender<T>> appender) {
        Path dir = DataFiles.tools().resolve("tags");
        for (Path file : DataFiles.jsonFiles(dir)) {
            JsonObject root = DataFiles.read(file).getAsJsonObject();
            if (!root.has(section)) {
                continue;
            }
            for (Map.Entry<String, JsonElement> tagEntry : root.getAsJsonObject(section).entrySet()) {
                TagKey<T> tag = TagKey.create(registry, ResourceLocation.parse(tagEntry.getKey()));
                TagsProvider.TagAppender<T> tagAppender = appender.apply(tag);
                for (JsonElement member : tagEntry.getValue().getAsJsonArray()) {
                    String value = member.getAsString();
                    if (value.startsWith("#")) {
                        tagAppender.addTag(TagKey.create(registry, ResourceLocation.parse(value.substring(1))));
                    } else {
                        ResourceLocation id = ResourceLocation.parse(value);
                        if (id.getNamespace().equals(EssentialCraft5.MODID) || id.getNamespace().equals("minecraft")) {
                            tagAppender.add(ResourceKey.create(registry, id));
                        } else {
                            tagAppender.addOptional(id);
                        }
                    }
                }
            }
        }
    }
}
