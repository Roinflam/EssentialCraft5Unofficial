package com.flameforge.essentialcraft5.datagen;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECBlocks;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.data.LanguageProvider;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 中英文语言文件，全部从数据表生成，不在代码里写译文：
 * <ul>
 *   <li>方块、物品的名字：按注册名在 tools/id_map_blocks.json / id_map_items.json 里找到原作的翻译键，
 *       英文取原作 en_us（tools/legacy_lang），中文取命名表的 zh 字段（已按 tools/lang/glossary.md 统一过；
 *       译名唯一的条目和 tools/lang/zh_cn_overrides.json 一致）。</li>
 *   <li>其它文字（界面、提示、消息）：tools/lang/keys/*.json，每个模块一个文件，格式 { "键": { "en": "...", "zh": "..." } }。</li>
 * </ul>
 * 注册了但表里找不到名字的方块/物品，datagen 直接报错（不会悄悄生成没名字的东西）。
 */
public class ECLanguageProvider extends LanguageProvider {

    private final boolean chinese;

    public ECLanguageProvider(PackOutput output, String locale) {
        super(output, EssentialCraft5.MODID, locale);
        this.chinese = locale.equals("zh_cn");
    }

    @Override
    protected void addTranslations() {
        Path tools = DataFiles.tools();
        Map<String, JsonObject> idMap = new HashMap<>();
        for (String file : List.of("id_map_blocks.json", "id_map_items.json")) {
            for (JsonElement element : DataFiles.read(tools.resolve(file)).getAsJsonArray()) {
                JsonObject entry = element.getAsJsonObject();
                if (!"removed".equals(entry.get("status").getAsString())) {
                    idMap.put(entry.get("new_id").getAsString(), entry);
                }
            }
        }
        JsonObject legacyEn = DataFiles.read(tools.resolve("legacy_lang/en_us.json")).getAsJsonObject();

        List<String> missing = new ArrayList<>();
        for (ECBlocks.Entry entry : ECBlocks.entries()) {
            Block block = entry.block().get();
            String name = name(idMap, legacyEn, entry.block().getId().getPath(), missing);
            if (name != null) {
                add(block, name);
            }
        }
        for (ECItems.Entry entry : ECItems.entries()) {
            Item item = entry.item().get();
            if (item instanceof BlockItem) {
                continue;
            }
            String name = name(idMap, legacyEn, ForgeRegistries.ITEMS.getKey(item).getPath(), missing);
            if (name != null) {
                add(item, name);
            }
        }

        for (Path file : DataFiles.jsonFiles(tools.resolve("lang/keys"))) {
            for (Map.Entry<String, JsonElement> key : DataFiles.read(file).getAsJsonObject().entrySet()) {
                JsonObject texts = key.getValue().getAsJsonObject();
                add(key.getKey(), texts.get(chinese ? "zh" : "en").getAsString());
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException("以下注册名在命名表里找不到名字（补进 tools/id_map_*.json）：" + missing);
        }
    }

    private String name(Map<String, JsonObject> idMap, JsonObject legacyEn, String id, List<String> missing) {
        JsonObject entry = idMap.get(id);
        if (entry == null) {
            missing.add(id);
            return null;
        }
        if (chinese) {
            JsonElement zh = entry.get("zh");
            if (zh == null || zh.isJsonNull() || zh.getAsString().isBlank()) {
                missing.add(id + "（缺中文）");
                return null;
            }
            return zh.getAsString();
        }
        // 原作一个键拆成多个子类型时，en_us 的原文是共用的，要用命名表里按子类型推断的英文名
        JsonElement key = entry.get("old_key");
        JsonElement en = entry.get("en");
        if (en != null && !en.isJsonNull() && !en.getAsString().isBlank()) {
            return en.getAsString();
        }
        if (key != null && legacyEn.has(key.getAsString())) {
            return legacyEn.get(key.getAsString()).getAsString();
        }
        missing.add(id + "（缺英文）");
        return null;
    }
}
