package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.guide.EntityPreviewSpec;
import com.flameforge.essentialcraft5.entity.WindMageEntity;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.registry.ECMRUGolem;
import com.flameforge.essentialcraft5.registry.ECWildMobs;
import com.flameforge.essentialcraft5.registry.ECWindMage;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 研究手册 entity 块框下的名字（tools/guide/FORMAT.md 的 entity 一行）：预览框下默认写实体类型名，
 * 但 nbt 摆出的变体可能自己换了名字（风法师 {@code Type:3} 是风暴术士、精英是「元素聚灵」之类），这时内容要写 {@code name}，
 * 不然页面标题写风暴术士、框下却写风法师。
 * <p>
 * 这里真建一只（不进世界），用和手册同一个 {@link EntityPreviewSpec#merge} 把 nbt 并进去，取它自己的名字键，
 * 和框下会写的键（{@code name}，没写就是类型的名字键）比。手册界面是客户端类、专用服上没有，所以直接用 Gson 读 jar 里的内容文件。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class GuidePreviewNameTests {

    private static final String EMPTY = "empty";
    private static final List<String> LANGUAGES = List.of("zh_cn", "en_us", "zh_tw", "zh_hk");

    private GuidePreviewNameTests() {
    }

    // ---------------------------------------------------------------- 判定

    /**
     * 一个 entity 块框下的名字对不对：对、或者建不出实体（框里退成问号，名字照写类型名）返回 null，不对返回说明
     */
    @Nullable
    static String problem(ServerLevel level, JsonObject block) {
        String id = str(block, "id");
        ResourceLocation rl = id == null ? null : ResourceLocation.tryParse(id);
        EntityType<?> type = rl != null && ForgeRegistries.ENTITY_TYPES.containsKey(rl) ? ForgeRegistries.ENTITY_TYPES.getValue(rl) : null;
        if (type == null) {
            return "实体 " + id + " 不存在";
        }
        String name = str(block, "name");
        String shown = name != null ? name : type.getDescriptionId();
        Entity entity = type.create(level);
        if (entity == null) {
            return null;
        }
        try {
            CompoundTag nbt = EntityPreviewSpec.parseNbt(str(block, "nbt"));
            if (nbt != null) {
                EntityPreviewSpec.merge(entity, nbt);
            }
            if (!(entity.getName().getContents() instanceof TranslatableContents contents)) {
                return null;
            }
            String own = contents.getKey();
            if (!own.equals(shown)) {
                return id + (nbt == null ? "" : " " + str(block, "nbt")) + " 自己叫 " + own + "，框下写的是 " + shown
                        + (name == null ? "（要写 name）" : "");
            }
            return null;
        } finally {
            entity.discard();
        }
    }

    // ---------------------------------------------------------------- 测试

    /** 四种语言里所有 entity 块：框下的名字和建出来那只（并上 nbt）自己的名字一致 */
    @GameTest(template = EMPTY)
    public static void guideEntityPreviewNames(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        int blocks = 0;
        int withNbt = 0;
        for (String language : LANGUAGES) {
            for (Path file : files(language)) {
                JsonObject category = read(file);
                for (JsonObject entry : objects(category.get("entries"))) {
                    for (JsonObject block : objects(entry.get("blocks"))) {
                        if (!"entity".equals(str(block, "type"))) {
                            continue;
                        }
                        blocks++;
                        if (str(block, "nbt") != null) {
                            withNbt++;
                        }
                        String p = problem(level, block);
                        if (p != null) {
                            problems.add(language + "/" + file.getFileName() + " 的 " + str(entry, "id") + "：" + p);
                        }
                    }
                }
            }
        }
        // 一块都没读到说明路径或格式变了，不能当成通过
        helper.assertTrue(blocks > 0, "手册里一个 entity 块都没读到");
        helper.assertTrue(withNbt > 0, "手册里没有带 nbt 的 entity 块（异兽录的风暴术士应该有），这条测试就测了个空");
        if (!problems.isEmpty()) {
            for (String p : problems) {
                EssentialCraft5.LOGGER.error("[指南] 实体预览名字：{}", p);
            }
            helper.fail("实体预览框下的名字：" + problems.size() + " 处不对，第一处 " + problems.get(0));
            return;
        }
        helper.succeed();
    }

    /** 判定本身：漏写 name、写错键都要拦下；类型名不变的变体、首领的精英标记、只写了位置的 nbt 不要求 name */
    @GameTest(template = EMPTY)
    public static void guideEntityPreviewNameCatchesVariants(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String mage = EntityType.getKey(ECWindMage.WIND_MAGE.get()).toString();
        String wisp = EntityType.getKey(ECWildMobs.ELEMENTAL_WISP.get()).toString();
        String golem = EntityType.getKey(ECMRUGolem.MRU_GOLEM.get()).toString();
        String storm = "{" + WindMageEntity.TYPE_KEY + ":" + WindMageEntity.STORM_SORCERER + "b}";
        String elite = "{" + ECMonster.TAG_VARIANT + ":" + ECMonster.VARIANT_ELITE + "b}";
        String wispElite = ECWildMobs.ELEMENTAL_WISP.get().getDescriptionId() + ".elite";

        helper.assertTrue(problem(level, block(mage, storm, null)) != null, "风暴术士没写 name 应该报");
        helper.assertTrue(problem(level, block(mage, storm, ECWindMage.WIND_MAGE.get().getDescriptionId())) != null,
                "风暴术士 name 写成风法师的键应该报");
        helper.assertTrue(problem(level, block(mage, storm, WindMageEntity.STORM_NAME_KEY)) == null, "风暴术士写对 name 不该报");
        helper.assertTrue(problem(level, block(mage, "{" + WindMageEntity.TYPE_KEY + ":1b}", null)) == null, "法师（第 2 种）还叫风法师，不用写 name");
        helper.assertTrue(problem(level, block(mage, null, null)) == null, "不带 nbt 的不用写 name");
        helper.assertTrue(problem(level, block(mage, null, WindMageEntity.STORM_NAME_KEY)) != null, "不带 nbt 却写了风暴术士的名字应该报");

        helper.assertTrue(problem(level, block(wisp, elite, null)) != null, "精英游灵没写 name 应该报");
        helper.assertTrue(problem(level, block(wisp, elite, wispElite)) == null, "精英游灵写对 name 不该报");
        // 首领没有精英：精英标记读进去也还是普通的，名字不变
        helper.assertTrue(problem(level, block(golem, elite, null)) == null, "首领带精英标记也不变名，不用写 name");
        // 位置之类的键合并前就拿掉了，不影响名字
        helper.assertTrue(problem(level, block(wisp, "{Pos:[0d,300d,0d]}", null)) == null, "只写位置的 nbt 不用写 name");
        helper.assertTrue(problem(level, block("essentialcraft5:no_such_mob", null, null)) != null, "不存在的实体应该报");
        helper.succeed();
    }

    // ---------------------------------------------------------------- 小工具

    private static JsonObject block(String id, @Nullable String nbt, @Nullable String name) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "entity");
        o.addProperty("id", id);
        if (nbt != null) {
            o.addProperty("nbt", nbt);
        }
        if (name != null) {
            o.addProperty("name", name);
        }
        return o;
    }

    private static List<Path> files(String language) {
        Path dir = ModList.get().getModFileById(EssentialCraft5.MODID).getFile()
                .findResource("assets", EssentialCraft5.MODID, "guide", language);
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(out::add);
        } catch (IOException e) {
            throw new IllegalStateException("列不出 " + dir, e);
        }
        return out;
    }

    private static JsonObject read(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("读不了 " + file + "：" + e.getMessage(), e);
        }
    }

    private static List<JsonObject> objects(@Nullable JsonElement element) {
        List<JsonObject> out = new ArrayList<>();
        if (element instanceof JsonArray array) {
            for (JsonElement e : array) {
                if (e.isJsonObject()) {
                    out.add(e.getAsJsonObject());
                }
            }
        }
        return out;
    }

    @Nullable
    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }
}
