package com.flameforge.essentialcraft5.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.Gson;
import net.minecraftforge.common.ForgeConfigSpec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 研究手册用的配置快照：把 EC5 唯一那份 SERVER 配置（{@link ECConfig#spec()}）拍平成「键 → 字符串值」
 *
 * <h3>为什么客户端直接读就行</h3>
 * <p>EC5 的数值全在 SERVER 配置里，Forge 在玩家进服握手时把服务端的配置文件原样发给客户端（ConfigSync），
 * 客户端载入后同一个规格对象读出来的就是服务端的值；单人游戏和局域网房主本来就和集成服共用这个对象。
 * 所以不用像 COMMON 配置那样自己发快照包单独下发，直接遍历规格即可。
 * 远程服务器热重载配置后 Forge 不会重发，EC5 自己补了一个包（{@code network/S2CConfigResyncPacket}），走的是和握手同一条路。</p>
 *
 * <h3>键名</h3>
 * <p>完整的点分路径，与 toml 一致：{@code general.<小节>.<项>}、{@code machines.<机器>.<项>}，
 * 例如 {@code machines.enrichment_chamber.min_size}、{@code general.baubles.disabled}。
 * datagen 导出的 {@code guide/config_keys.json} 用的是同一套键名（datagen/ConfigKeysProvider），校验脚本按它查。</p>
 *
 * <h3>值的编码</h3>
 * <ul>
 *   <li>布尔、数字、字符串：{@code String.valueOf}（double 可能是 {@code 1.0E-4} 这种写法，客户端用 {@code Double.parseDouble} 解析即可）</li>
 *   <li>枚举：{@code name()}</li>
 *   <li>列表：JSON 数组字符串（Gson）</li>
 * </ul>
 * <p>配置还没加载时（没进世界、刚退出）取默认值：书在标题界面打不开，但查阅索引、GameTest 这类调用方不该拿到一张空表。</p>
 *
 * <p>公共类（不引用客户端代码）：GameTest 也用它核对手册里的 {@code {cfg:}} 键都真实存在。</p>
 */
public final class GuideConfigSnapshot {

    private static final Gson GSON = new Gson();

    private GuideConfigSnapshot() {
    }

    /**
     * 读取当前生效的全部配置值（没加载的取默认值）
     *
     * @return 键 → 字符串值，按配置文件里的声明顺序；规格还没建好时是空表
     */
    @Nonnull
    public static Map<String, String> capture() {
        return capture(ECConfig.spec());
    }

    /**
     * 同上，指定规格
     *
     * @param spec 规格，可为 null
     * @return 键 → 字符串值
     */
    @Nonnull
    public static Map<String, String> capture(@Nullable ForgeConfigSpec spec) {
        Map<String, String> out = new LinkedHashMap<>();
        if (spec != null) {
            walk(spec.getValues(), spec.isLoaded(), out);
        }
        return out;
    }

    private static void walk(UnmodifiableConfig config, boolean loaded, Map<String, String> out) {
        for (Object node : config.valueMap().values()) {
            if (node instanceof UnmodifiableConfig sub) {
                walk(sub, loaded, out);
            } else if (node instanceof ForgeConfigSpec.ConfigValue<?> value) {
                try {
                    out.put(String.join(".", value.getPath()), encode(loaded ? value.get() : value.getDefault()));
                } catch (RuntimeException ignored) {
                    // 值损坏 / 正好在卸载：跳过这一项，书里按「缺值」处理（警告色的 ?）
                }
            }
        }
    }

    private static String encode(Object value) {
        if (value instanceof List<?> list) {
            return GSON.toJson(list == null ? Collections.emptyList() : list);
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        return String.valueOf(value);
    }
}
