package com.flameforge.essentialcraft5.datagen;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.flameforge.essentialcraft5.EssentialCraft5;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 研究手册用的配置键表：{@code assets/essentialcraft5/guide/config_keys.json}。
 * <p>
 * 手册正文里的 {@code {cfg:键}} 和条件 {@code cfg:键} 用的是 SERVER 配置拍平后的完整点分路径
 * （{@code machines.enrichment_chamber.min_size}、{@code general.baubles.disabled}），和客户端快照的键名规则一致。
 * 离线校验脚本 {@code tools/guide/validate_guide.py} 与改写预处理 {@code prep_rewrite.py} 读这份表查键名、类型、默认值、范围，
 * 不再去解析 Java 源码（{@code defineListAllowEmpty}、在 register 里直接挂小节这类写法正则认不全）。
 * <p>
 * 表跟着 jar 走没有害处：手册加载器只读 {@code guide/<语言>/} 下的文件。
 * 规格从 {@link ConfigTracker} 里按 modid 找本模组唯一那份 SERVER 配置（{@code ECConfig.register} 注册的），
 * datagen 启动时模组构造器已经跑过，规格已经建好；没加载也没关系，这里只读默认值和范围。
 * <p>
 * 每项：{@code type}（boolean / int / long / double / string / enum / list / other）、{@code default}，
 * 数值项有范围时带 {@code min} / {@code max}，枚举带 {@code values}，写了注释的带 {@code comment}（常写着单位）。
 * <b>加了、改了配置项要重跑 runData。</b>
 */
public final class ConfigKeysProvider implements DataProvider {

    private final PackOutput output;

    public ConfigKeysProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        ForgeConfigSpec spec = serverSpec();
        JsonObject keys = new JsonObject();
        walk(spec, spec.getValues(), keys);
        if (keys.size() == 0) {
            throw new IllegalStateException("本模组的 SERVER 配置一项都没有，配置键表导不出来");
        }
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "datagen (ConfigKeysProvider) output, do not edit. "
                + "Keys usable in guide {cfg:...} placeholders and cfg: conditions. / 研究手册 {cfg:} 与条件可用的配置键，由 runData 生成，别手改");
        root.add("keys", keys);
        Path target = output.getOutputFolder(PackOutput.Target.RESOURCE_PACK)
                .resolve(EssentialCraft5.MODID).resolve("guide").resolve("config_keys.json");
        return DataProvider.saveStable(cache, root, target);
    }

    private static ForgeConfigSpec serverSpec() {
        for (ModConfig config : ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.SERVER)) {
            if (EssentialCraft5.MODID.equals(config.getModId()) && config.getSpec() instanceof ForgeConfigSpec spec) {
                return spec;
            }
        }
        throw new IllegalStateException("没找到 " + EssentialCraft5.MODID + " 的 SERVER 配置（ECConfig.register 没跑？）");
    }

    private static void walk(ForgeConfigSpec spec, UnmodifiableConfig values, JsonObject out) {
        for (Object node : values.valueMap().values()) {
            if (node instanceof UnmodifiableConfig sub) {
                walk(spec, sub, out);
            } else if (node instanceof ForgeConfigSpec.ConfigValue<?> value) {
                out.add(String.join(".", value.getPath()), describe(spec, value));
            }
        }
    }

    private static JsonObject describe(ForgeConfigSpec spec, ForgeConfigSpec.ConfigValue<?> value) {
        JsonObject o = new JsonObject();
        Object def = value.getDefault();
        String type;
        if (def instanceof Boolean) {
            type = "boolean";
        } else if (def instanceof Integer) {
            type = "int";
        } else if (def instanceof Long) {
            type = "long";
        } else if (def instanceof Double || def instanceof Float) {
            type = "double";
        } else if (def instanceof Enum<?>) {
            type = "enum";
        } else if (def instanceof String) {
            type = "string";
        } else if (def instanceof List<?>) {
            type = "list";
        } else {
            type = "other";
        }
        o.addProperty("type", type);
        o.add("default", json(def));
        if (def instanceof Enum<?> e) {
            JsonArray names = new JsonArray();
            for (Object c : e.getDeclaringClass().getEnumConstants()) {
                names.add(((Enum<?>) c).name());
            }
            o.add("values", names);
        }
        Object raw = spec.getSpec().get(value.getPath());
        if (raw instanceof ForgeConfigSpec.ValueSpec valueSpec) {
            ForgeConfigSpec.Range<?> range = valueSpec.getRange();
            if (range != null) {
                o.add("min", json(range.getMin()));
                o.add("max", json(range.getMax()));
            }
            // 配置注释（中英两行，常写着单位）：第二段重写正文时核对单位用
            String comment = valueSpec.getComment();
            if (comment != null && !comment.isBlank()) {
                o.addProperty("comment", comment);
            }
        }
        return o;
    }

    private static com.google.gson.JsonElement json(Object v) {
        if (v instanceof Boolean b) {
            return new JsonPrimitive(b);
        }
        if (v instanceof Number n) {
            // 范围写成 ±Double.MAX_VALUE 以外的无穷大时 Gson 严格模式写不出来，退回字符串
            if ((n instanceof Double || n instanceof Float) && !Double.isFinite(n.doubleValue())) {
                return new JsonPrimitive(String.valueOf(n));
            }
            return new JsonPrimitive(n);
        }
        if (v instanceof Enum<?> e) {
            return new JsonPrimitive(e.name());
        }
        if (v instanceof List<?> list) {
            JsonArray array = new JsonArray();
            for (Object x : list) {
                array.add(json(x));
            }
            return array;
        }
        return new JsonPrimitive(String.valueOf(v));
    }

    @Override
    public String getName() {
        return "Guide config keys: " + EssentialCraft5.MODID;
    }
}
