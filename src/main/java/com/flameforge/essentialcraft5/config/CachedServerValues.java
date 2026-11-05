package com.flameforge.essentialcraft5.config;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 一组 SERVER 配置值拼成的不可变记录，按配置版本号缓存：怪物 tick、技能判定里每刻都要读整组配置，每次新建记录、
 * 逐项 {@code ConfigValue.get} 是没有必要的分配（整合包服的瓶颈本来就是分配速率）。
 * <p>
 * 版本号在本模组 SERVER 配置的 {@code ModConfigEvent.Loading / Reloading / Unloading} 时 +1（模组总线，可能在文件监视线程上发，
 * 所以只动计数，下一次读的时候在调用方的线程上重建）。用法：
 * <pre>{@code
 * private static final CachedServerValues<FrostValues> FROST_CACHE = new CachedServerValues<>();
 * return FROST_CACHE.get(() -> new FrostValues(ECConfig.get(...), ...));
 * }</pre>
 * 传进来的 lambda 只读静态字段、不捕获局部变量，本身不分配；命中缓存时整个调用不分配。GameTest 的测试覆盖值照旧在 get 之前判。
 */
public final class CachedServerValues<T> {

    private static final AtomicInteger REVISION = new AtomicInteger();

    /** 版本号和那一版的值放在同一个对象里，读到的永远是成对的 */
    private record Snapshot<T>(int revision, T value) {
    }

    @Nullable
    private volatile Snapshot<T> snapshot;

    /**
     * 这一版配置对应的值；版本号变了（配置加载、热重载、卸载）就用 build 重建一次
     *
     * @param build 按当前配置拼一份新的记录
     * @return 缓存的记录
     */
    public T get(Supplier<T> build) {
        int now = REVISION.get();
        Snapshot<T> current = snapshot;
        if (current == null || current.revision() != now) {
            // 先读版本号再建：建的途中又重载了，版本号已经变了，下一次读会再建
            current = new Snapshot<>(now, build.get());
            snapshot = current;
        }
        return current.value();
    }

    /** 当前配置版本号 */
    public static int revision() {
        return REVISION.get();
    }

    /** GameTest：当成配置重载过一次（不动真配置） */
    public static void bumpForTest() {
        REVISION.incrementAndGet();
    }

    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Revision {

        private Revision() {
        }

        private static void bump(ModConfigEvent event) {
            if (ECConfig.spec() != null && event.getConfig().getSpec() == ECConfig.spec()) {
                REVISION.incrementAndGet();
            }
        }

        @SubscribeEvent
        public static void cachedServerValuesLoading(ModConfigEvent.Loading event) {
            bump(event);
        }

        @SubscribeEvent
        public static void cachedServerValuesReloading(ModConfigEvent.Reloading event) {
            bump(event);
        }

        @SubscribeEvent
        public static void cachedServerValuesUnloading(ModConfigEvent.Unloading event) {
            bump(event);
        }
    }
}
