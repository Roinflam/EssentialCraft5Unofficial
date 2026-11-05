package com.flameforge.essentialcraft5.client.guide;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.GuideConfigSnapshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 手册里所有 {@code {cfg:键}} 与 {@code cfg:} 条件的取值来源：EC5 SERVER 配置的快照（{@link GuideConfigSnapshot}）
 *
 * <h3>版本号由配置事件驱动</h3>
 * <p>客户端读 SERVER 配置就能拿到服务端的值（进服握手时 Forge 下发、热重载后 EC5 补发，见 {@link GuideConfigSnapshot}）。
 * 快照按版本号缓存，版本号在这几件事发生时 +1，<b>不轮询</b>：</p>
 * <ul>
 *   <li>本模组 SERVER 配置的 {@code ModConfigEvent.Loading / Reloading / Unloading}（模组总线；可能在文件监视线程上发，所以只动计数）；</li>
 *   <li>退出世界（{@code ClientPlayerNetworkEvent.LoggingOut}，Forge 总线）。</li>
 * </ul>
 * <p>快照在主线程按需重算（{@link #snapshot()}），{@link GuideView#current()} 发现版本号变了就重建。</p>
 *
 * <p>给展示厅截图留了一个覆盖入口（{@link #setOverride}）：拍同一页「开 / 关」两种样子时用，不动真配置
 * （GameTest 的规矩一样：SERVER 配置别用 ConfigValue.set，自动存盘后文件监视器会在后台重读、冲掉设的值）。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class GuideClientConfig {

    private static volatile int revision;

    /** 缓存的快照与它对应的版本号 */
    @Nullable
    private static Map<String, String> cached;
    private static int cachedRevision = -1;

    /** 展示厅的覆盖值：键 → 字符串值（盖在真快照上面）；null 表示不覆盖 */
    @Nullable
    private static volatile Map<String, String> override;

    private GuideClientConfig() {}

    /**
     * 快照版本号：配置加载 / 重载 / 卸载、退出世界、覆盖值变化时 +1
     *
     * @return 版本号
     */
    public static int revision() {
        return revision;
    }

    /**
     * 当前数值是不是来自已加载的配置（没进世界时是默认值）
     *
     * @return false 表示配置没加载，快照里是默认值
     */
    public static boolean isSynced() {
        return com.flameforge.essentialcraft5.config.ECConfig.spec() != null
                && com.flameforge.essentialcraft5.config.ECConfig.spec().isLoaded();
    }

    /**
     * 取一份完整快照（按版本号缓存；只在主线程调用）
     *
     * @return 不可变的键 → 值
     */
    @Nonnull
    public static synchronized Map<String, String> snapshot() {
        int now = revision;
        if (cached == null || cachedRevision != now) {
            Map<String, String> values = GuideConfigSnapshot.capture();
            Map<String, String> extra = override;
            if (extra != null) {
                values.putAll(extra);
            }
            cached = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            cachedRevision = now;
        }
        return cached;
    }

    /**
     * 展示厅截图用：临时覆盖几个键（传 null 恢复按配置）。只影响手册显示，不改真配置
     *
     * @param values 键 → 字符串值（布尔写 "true" / "false"）
     */
    public static void setOverride(@Nullable Map<String, String> values) {
        override = values == null ? null : Map.copyOf(values);
        revision++;
    }

    private static boolean ours(ModConfig config) {
        return EssentialCraft5.MODID.equals(config.getModId()) && config.getType() == ModConfig.Type.SERVER;
    }

    /**
     * 配置事件（模组总线）。内部类名带前缀：同一个包里别的类也有监听配置事件的内部类，撞名会有一个静默失效
     */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class GuideConfigRevisionEvents {

        private GuideConfigRevisionEvents() {
        }

        @SubscribeEvent
        public static void onGuideConfigLoading(ModConfigEvent.Loading event) {
            if (ours(event.getConfig())) {
                revision++;
            }
        }

        @SubscribeEvent
        public static void onGuideConfigReloading(ModConfigEvent.Reloading event) {
            if (ours(event.getConfig())) {
                revision++;
            }
        }

        @SubscribeEvent
        public static void onGuideConfigUnloading(ModConfigEvent.Unloading event) {
            if (ours(event.getConfig())) {
                revision++;
            }
        }
    }

    /**
     * 退出世界（Forge 总线）：上一个服务器的值不能带进下一个存档 / 服务器
     */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
    public static final class GuideConfigLogoutEvents {

        private GuideConfigLogoutEvents() {
        }

        @SubscribeEvent
        public static void onGuideClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            revision++;
        }
    }
}
