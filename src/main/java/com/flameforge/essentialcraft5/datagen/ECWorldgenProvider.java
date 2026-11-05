package com.flameforge.essentialcraft5.datagen;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.worldgen.hoanna.HoannaTerrain;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.data.worldgen.NoiseData;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraftforge.common.data.DatapackBuiltinEntriesProvider;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 世界生成的数据包条目（第六批 6a 框架 F6a，batch6a.md §6.1 第 4 条；runData 生成到 src/generated/resources/data/essentialcraft5/worldgen/）：
 * <ul>
 *   <li>{@code noise_settings/hoanna.json}：海纳尔的噪声设置（{@link HoannaTerrain#settings}）；</li>
 *   <li>{@code noise/hoanna/*.json}、{@code density_function/hoanna/**.json}：原版主世界路由用到的噪声与具名密度函数的副本（D1 A′，
 *       为什么、怎么和原版保持一致见 {@link HoannaTerrain} 类注释）。</li>
 * </ul>
 * 用数据生成而不是手写 JSON：原版主世界的 final_density 和地表规则是上千行内联 JSON，手抄容易错；这里直接跑原版自己的 bootstrap 代码，
 * 只在「登记」和「查引用」两处把 id 换成副本（{@link RemapContext}）。
 * <p>
 * 复制哪些：从原版 {@code minecraft:overworld} 噪声设置的路由出发，把它编码成 JSON，凡是字符串值正好是一个原版密度函数 id 就收进来并继续展开，
 * 是一个原版噪声 id 就收进噪声集合（{@link #scan}）。不在集合里的（下界 / 末地 / 大型群系 / 放大化那些）不复制，
 * 原版 bootstrap 登记它们时只拿回原版的持有者（{@code fillMissingHolders} 按原版绑定），不写文件、也不报「未引用」。
 */
public final class ECWorldgenProvider extends DatapackBuiltinEntriesProvider {

    public ECWorldgenProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> vanilla) {
        this(output, vanilla, new CopySet());
    }

    private ECWorldgenProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> vanilla, CopySet copies) {
        // 先在原版注册表上算出要复制的集合，再让父类用 builder 打补丁（父类的 thenApply 排在这个之后）
        super(output, vanilla.thenApply(provider -> {
            copies.scan(provider);
            return provider;
        }), builder(copies), Set.of(EssentialCraft5.MODID));
    }

    private static RegistrySetBuilder builder(CopySet copies) {
        return new RegistrySetBuilder()
                .add(Registries.NOISE, context -> NoiseData.bootstrap(new RemapContext<>(context, copies)))
                .add(Registries.DENSITY_FUNCTION, context -> NoiseRouterData.bootstrap(new RemapContext<>(context, copies)))
                .add(Registries.NOISE_SETTINGS, context -> {
                    NoiseGeneratorSettings base = NoiseGeneratorSettings.overworld(new RemapContext<>(context, copies), false, false);
                    context.register(ECHoanna.NOISE_SETTINGS, HoannaTerrain.settings(base));
                });
    }

    @Override
    public String getName() {
        return "Worldgen (Hoanna noise settings + vanilla terrain noise copies): " + EssentialCraft5.MODID;
    }

    // ------------------------------------------------------------------
    // 要复制的原版 id
    // ------------------------------------------------------------------

    /** 要复制的原版密度函数与噪声 id（{@link #scan} 之后才有内容；builder 的 bootstrap 在它之后才跑） */
    static final class CopySet {
        private final Set<ResourceLocation> densityFunctions = new LinkedHashSet<>();
        private final Set<ResourceLocation> noises = new LinkedHashSet<>();
        private boolean scanned;

        /** 从原版主世界路由出发找全部依赖 */
        void scan(HolderLookup.Provider vanilla) {
            HolderLookup.RegistryLookup<DensityFunction> functions = vanilla.lookupOrThrow(Registries.DENSITY_FUNCTION);
            HolderLookup.RegistryLookup<NormalNoise.NoiseParameters> noiseLookup = vanilla.lookupOrThrow(Registries.NOISE);
            NoiseGeneratorSettings overworld = vanilla.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, vanilla);
            Deque<JsonElement> todo = new ArrayDeque<>();
            todo.add(NoiseRouter.CODEC.encodeStart(ops, overworld.noiseRouter()).getOrThrow(false, error -> {
                throw new IllegalStateException("编码原版主世界路由失败：" + error);
            }));
            while (!todo.isEmpty()) {
                strings(todo.pop(), value -> {
                    ResourceLocation id = ResourceLocation.tryParse(value);
                    if (id == null || !ResourceLocation.DEFAULT_NAMESPACE.equals(id.getNamespace())) {
                        return;
                    }
                    Optional<Holder.Reference<DensityFunction>> function = functions.get(ResourceKey.create(Registries.DENSITY_FUNCTION, id));
                    if (function.isPresent()) {
                        if (densityFunctions.add(id)) {
                            todo.add(DensityFunction.DIRECT_CODEC.encodeStart(ops, function.get().value()).getOrThrow(false, error -> {
                                throw new IllegalStateException("编码原版密度函数 " + id + " 失败：" + error);
                            }));
                        }
                    } else if (noiseLookup.get(ResourceKey.create(Registries.NOISE, id)).isPresent()) {
                        noises.add(id);
                    }
                });
            }
            if (densityFunctions.isEmpty() || noises.isEmpty()) {
                throw new IllegalStateException("没从原版主世界路由里找到要复制的噪声 / 密度函数：编码格式变了？");
            }
            scanned = true;
            EssentialCraft5.LOGGER.info("[海纳尔] 复制原版主世界地形：{} 个密度函数、{} 个噪声", densityFunctions.size(), noises.size());
        }

        private static void strings(JsonElement json, Consumer<String> sink) {
            if (json.isJsonPrimitive()) {
                if (json.getAsJsonPrimitive().isString()) {
                    sink.accept(json.getAsString());
                }
            } else if (json.isJsonArray()) {
                json.getAsJsonArray().forEach(element -> strings(element, sink));
            } else if (json.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                    strings(entry.getValue(), sink);
                }
            }
        }

        /** 在复制集合里的原版键换成副本键，其余原样返回（同一个实例） */
        <T> ResourceKey<T> map(ResourceKey<T> key) {
            if (!scanned) {
                throw new IllegalStateException("还没扫描原版路由就开始登记了");
            }
            ResourceLocation id = key.location();
            if (!ResourceLocation.DEFAULT_NAMESPACE.equals(id.getNamespace())) {
                return key;
            }
            boolean copied = key.isFor(Registries.DENSITY_FUNCTION) ? densityFunctions.contains(id)
                    : key.isFor(Registries.NOISE) && noises.contains(id);
            return copied ? HoannaTerrain.copyKey(key) : key;
        }

        Set<ResourceLocation> densityFunctions() {
            return Collections.unmodifiableSet(densityFunctions);
        }

        Set<ResourceLocation> noises() {
            return Collections.unmodifiableSet(noises);
        }
    }

    // ------------------------------------------------------------------
    // 把原版 bootstrap 的「登记」和「查引用」都换成副本 id
    // ------------------------------------------------------------------

    /**
     * 包一层 bootstrap 上下文：
     * <ul>
     *   <li>{@code register(原版键, 值)}：在复制集合里 → 以副本键登记（返回的持有者被原版代码拿去嵌进别的密度函数，于是那边也指向副本）；
     *       不在 → 不登记，返回原版键的持有者（补丁建好后按原版注册表绑定，不写文件）。</li>
     *   <li>{@code lookup(注册表).get(原版键)}：在复制集合里 → 查副本键，否则查原版键。</li>
     * </ul>
     */
    static final class RemapContext<T> implements BootstapContext<T> {
        private final BootstapContext<T> delegate;
        private final CopySet copies;

        RemapContext(BootstapContext<T> delegate, CopySet copies) {
            this.delegate = delegate;
            this.copies = copies;
        }

        @Override
        public Holder.Reference<T> register(ResourceKey<T> key, T value, Lifecycle lifecycle) {
            ResourceKey<T> mapped = copies.map(key);
            if (mapped != key) {
                return delegate.register(mapped, value, lifecycle);
            }
            ResourceKey<? extends Registry<T>> registry = ResourceKey.createRegistryKey(key.registry());
            HolderGetter<T> vanilla = delegate.lookup(registry);
            return vanilla.getOrThrow(key);
        }

        @Override
        public <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> registry) {
            HolderGetter<S> inner = delegate.lookup(registry);
            return new HolderGetter<>() {
                @Override
                public Optional<Holder.Reference<S>> get(ResourceKey<S> key) {
                    return inner.get(copies.map(key));
                }

                @Override
                public Optional<HolderSet.Named<S>> get(TagKey<S> tag) {
                    return inner.get(tag);
                }
            };
        }

        @Override
        public <S> Optional<HolderLookup.RegistryLookup<S>> registryLookup(ResourceKey<? extends Registry<? extends S>> registry) {
            return delegate.registryLookup(registry);
        }
    }
}
