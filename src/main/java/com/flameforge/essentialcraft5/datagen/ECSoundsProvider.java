package com.flameforge.essentialcraft5.datagen;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/**
 * assets/essentialcraft5/sounds.json：整个命名空间只有一份，由各模块的 tools/sounds/*.json 合并而成
 * （格式就是 sounds.json 本身的条目）。同名条目出现两次直接报错。
 */
public final class ECSoundsProvider implements DataProvider {

    private final PackOutput output;

    public ECSoundsProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        Map<String, JsonElement> merged = new TreeMap<>();
        for (Path file : DataFiles.jsonFiles(DataFiles.tools().resolve("sounds"))) {
            for (Map.Entry<String, JsonElement> entry : DataFiles.read(file).getAsJsonObject().entrySet()) {
                if (merged.put(entry.getKey(), entry.getValue()) != null) {
                    throw new IllegalStateException("音效 " + entry.getKey() + " 定义了两次（" + file.getFileName() + "）");
                }
            }
        }
        JsonObject root = new JsonObject();
        merged.forEach(root::add);
        Path target = output.getOutputFolder(PackOutput.Target.RESOURCE_PACK)
                .resolve(EssentialCraft5.MODID).resolve("sounds.json");
        return DataProvider.saveStable(cache, root, target);
    }

    @Override
    public String getName() {
        return "Sounds: " + EssentialCraft5.MODID;
    }
}
