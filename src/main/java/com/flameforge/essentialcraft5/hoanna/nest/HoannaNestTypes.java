package com.flameforge.essentialcraft5.hoanna.nest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 巢穴类型的读取器（第十三批 13d-HF，batch13.md §6.4）：数据包目录 {@code data/<ns>/hoanna_nests/<id>.json}，数据包重载时重读。
 * <pre>
 * {
 *   "entities": [{"type": "essentialcraft5:frostbitten", "count": 4}, {"type": "essentialcraft5:frostbitten", "count": 1, "leader": true}],
 *   "spawn_radius": 6, "activate_radius": 16, "leash_radius": 48,
 *   "loot_table": "essentialcraft5:hoanna_nests/frozen_camp",
 *   "recharge_ticks": 72000, "reset_ticks": 6000
 * }
 * </pre>
 * 半径都可以不写（默认 6 / 16 / 48，上限 {@value HoannaNestType#MAX_RADIUS}，要 spawn ≤ activate ≤ leash）；{@code recharge_ticks} /
 * {@code reset_ticks} 不写就用配置 {@code general.hoanna_nests.*}。一波一共 1~{@value HoannaNestType#MAX_WAVE} 只。
 * <b>坏条目只跳过那一种</b>（实体类型没注册、数量不对、少了奖励表……）并记 WARN，别的照读；方块实体里记着的类型找不到时巢穴什么都不做（不放怪、不报错）。
 * GameTest 用 {@link #putForTest} / {@link #clearTest} 塞测试用的类型（优先于数据包里的同名类型）。
 */
public final class HoannaNestTypes extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "hoanna_nests";

    /** 要写在 {@link #INSTANCE} 前面：静态字段按书写顺序初始化，构造器里要用到它 */
    private static final Gson GSON = new GsonBuilder().create();
    public static final HoannaNestTypes INSTANCE = new HoannaNestTypes();

    private static volatile Map<ResourceLocation, HoannaNestType> types = Map.of();
    private static final Map<ResourceLocation, HoannaNestType> TEST = new ConcurrentHashMap<>();

    private HoannaNestTypes() {
        super(GSON, DIRECTORY);
    }

    /**
     * 解析结果。
     *
     * @param types    读进来的类型
     * @param warnings 跳过的条目（已写进日志）
     */
    public record Result(Map<ResourceLocation, HoannaNestType> types, List<String> warnings) {
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Result result = parse(files);
        result.warnings().forEach(warning -> EssentialCraft5.LOGGER.warn("[海纳尔巢穴] {}", warning));
        types = Map.copyOf(result.types());
        EssentialCraft5.LOGGER.info("[海纳尔巢穴] 读到 {} 种巢穴（{} 个文件）", types.size(), files.size());
    }

    /** 类型 id → 类型；测试塞的优先。找不到返回 null */
    @Nullable
    public static HoannaNestType get(@Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        HoannaNestType test = TEST.get(id);
        return test != null ? test : types.get(id);
    }

    /** 现在读到的全部类型（不含测试塞的） */
    public static Map<ResourceLocation, HoannaNestType> all() {
        return types;
    }

    /** GameTest 用：塞一种类型（优先于数据包里的同名类型）；用完 {@link #clearTest} */
    public static void putForTest(ResourceLocation id, HoannaNestType type) {
        TEST.put(id, type);
    }

    public static void clearTest() {
        TEST.clear();
    }

    public static Result parse(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, HoannaNestType> out = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        files.forEach((id, json) -> {
            try {
                out.put(id, parseOne(id, json));
            } catch (RuntimeException e) {
                warnings.add(id + " 跳过：" + e.getMessage());
            }
        });
        return new Result(out, warnings);
    }

    /** 解析一种；不合法抛 {@link IllegalArgumentException}（带原因） */
    public static HoannaNestType parseOne(ResourceLocation id, JsonElement json) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("不是 JSON 对象");
        }
        JsonObject root = json.getAsJsonObject();
        JsonArray entities = GsonHelper.getAsJsonArray(root, "entities");
        List<HoannaNestType.Member> members = new ArrayList<>();
        for (JsonElement element : entities) {
            JsonObject entry = GsonHelper.convertToJsonObject(element, "entities 里的一项");
            ResourceLocation typeId = ResourceLocation.tryParse(GsonHelper.getAsString(entry, "type"));
            if (typeId == null || !ForgeRegistries.ENTITY_TYPES.containsKey(typeId)) {
                throw new IllegalArgumentException("实体类型没注册：" + GsonHelper.getAsString(entry, "type"));
            }
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(typeId);
            int count = GsonHelper.getAsInt(entry, "count", 1);
            if (count < 1) {
                throw new IllegalArgumentException(typeId + " 的 count 要 ≥ 1，实际 " + count);
            }
            members.add(new HoannaNestType.Member(type, count, GsonHelper.getAsBoolean(entry, "leader", false)));
        }
        int total = members.stream().mapToInt(HoannaNestType.Member::count).sum();
        if (total < 1 || total > HoannaNestType.MAX_WAVE) {
            throw new IllegalArgumentException("一波一共要 1~" + HoannaNestType.MAX_WAVE + " 只，实际 " + total);
        }
        int spawn = radius(root, "spawn_radius", HoannaNestType.DEFAULT_SPAWN_RADIUS);
        int activate = radius(root, "activate_radius", HoannaNestType.DEFAULT_ACTIVATE_RADIUS);
        int leash = radius(root, "leash_radius", HoannaNestType.DEFAULT_LEASH_RADIUS);
        if (spawn > activate || activate > leash) {
            throw new IllegalArgumentException("半径要 spawn ≤ activate ≤ leash，实际 " + spawn + " / " + activate + " / " + leash);
        }
        ResourceLocation loot = ResourceLocation.tryParse(GsonHelper.getAsString(root, "loot_table"));
        if (loot == null) {
            throw new IllegalArgumentException("loot_table 不是合法的 id");
        }
        int recharge = ticks(root, "recharge_ticks");
        int reset = ticks(root, "reset_ticks");
        return new HoannaNestType(id, members, spawn, activate, leash, loot, recharge, reset);
    }

    private static int radius(JsonObject root, String key, int fallback) {
        int value = GsonHelper.getAsInt(root, key, fallback);
        if (value < 1 || value > HoannaNestType.MAX_RADIUS) {
            throw new IllegalArgumentException(key + " 要在 1~" + HoannaNestType.MAX_RADIUS + " 之间，实际 " + value);
        }
        return value;
    }

    private static int ticks(JsonObject root, String key) {
        if (!root.has(key)) {
            return HoannaNestType.USE_CONFIG;
        }
        int value = GsonHelper.getAsInt(root, key);
        if (value < 1) {
            throw new IllegalArgumentException(key + " 要 ≥ 1，实际 " + value);
        }
        return value;
    }

    /** 数据包重载时登记读取器（Forge 总线） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class HoannaNestTypesEvents {

        private HoannaNestTypesEvents() {
        }

        @SubscribeEvent
        public static void hoannaNestTypesReload(AddReloadListenerEvent event) {
            event.addListener(INSTANCE);
        }
    }
}
