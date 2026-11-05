package com.flameforge.essentialcraft5.worldgen.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.core.Registry;
import net.minecraft.data.worldgen.SurfaceRuleData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceRules;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 海纳尔的地形（噪声设置 {@code essentialcraft5:hoanna}，第六批 6a 框架 F6a；D1 = A′，batch6a.md §4.2）。
 * <p>
 * <b>做法</b>：噪声路由照搬 1.20.1 原版主世界（{@code NoiseGeneratorSettings.overworld(ctx, false, false)}：y −64~320、单元 4 × 8、
 * 含 1.18 以来的洞穴、山地、含水层），但路由用到的每一个原版噪声（{@code worldgen/noise}）和具名密度函数（{@code worldgen/density_function}）
 * 都复制一份到 {@code essentialcraft5:hoanna/<原路径>}，路由改引用副本。
 * <p>
 * <b>为什么要复制</b>：所有维度共用世界种子（{@code ChunkMap} L155~157 用 {@code level.getSeed()} 建 {@code RandomState}），
 * 而非 legacy 随机源下每个噪声按「种子 + 噪声 id」派生（{@code RandomState} L70 {@code getOrCreateNoise} → {@code fromHashOf(id)}）。
 * 直接引用 {@code minecraft:continentalness} 这类原版 id，海纳尔的山、河谷、海岸线、洞穴、气候（= 群系分布）会和主世界同坐标逐格相同；
 * 换成副本 id 就换了一套噪声，地貌和主世界不重合，开销完全一样。{@code old_blended_noise}（3D 细节）固定按 "terrain" 派生、仍和主世界相同，
 * 但大地貌由大陆度 / 侵蚀 / 山脊决定，看不出来。地表规则里的噪声条件（{@code minecraft:surface} 等）没复制，只影响原版群系分支的花纹，海纳尔群系用不到。
 * <p>
 * <b>洞穴有两种</b>：噪声洞穴（奶酪、面条、意面）是路由里的密度函数，跟着上面的副本换掉了；雕刻器（configured_carver 的老式洞穴和峡谷）不走噪声，
 * 第 l 个 carver 的随机种子是「世界种子 + l」（{@code NoiseBasedChunkGenerator.applyCarvers}），和 carver 的 id、维度都无关。
 * 海纳尔群系的 carver 列表前面垫了 3 个 probability 0 的占位（{@code tools/gen/hoanna_biomes.py} 的 {@code CARVER_PLACEHOLDERS}），
 * 真正的洞穴、峡谷排在 3~5 号，和主世界的 0~2 号种子错开，所以这一半也不和主世界重合（{@code hoannaTerrainNotOverworld} 一并查）。
 * <p>
 * <b>怎么和原版保持一致</b>：副本不是手抄的，是 runData 时 {@code datagen/ECWorldgenProvider} 用原版自己的 bootstrap 代码
 * （{@code NoiseData.bootstrap}、{@code NoiseRouterData.bootstrap}、{@code NoiseGeneratorSettings.overworld}）重新算出来、只把 id 改掉：
 * 升级 Minecraft 后重跑 runData 就跟着新版本走。要复制哪些 id 也不是写死的：从原版主世界路由出发，沿 JSON 里的引用找出全部依赖（闭包）。
 * GameTest {@code HoannaFrameworkTests.hoannaNoiseCopiesMatchVanilla} 逐个比对副本与原版（只差 id），
 * {@code hoannaTerrainNotOverworld} 查路由里没有原版噪声 / 密度函数、同种子下地形噪声的取值和主世界不同、carver 序号和主世界错开。
 * <p>
 * 其余设置（batch6a.md §6.1 第 4 条）：默认方块石头、默认流体水、海平面 63；{@code disable_mob_generation} true（生成区块时不放动物）、
 * 含水层开（关了 63 以下的洞穴会被水淹）、{@code ore_veins} 关（1.18 的铜铁大矿脉，原作没有）、非 legacy 随机源。
 */
public final class HoannaTerrain {

    /** 副本 id 的路径前缀：{@code minecraft:overworld/continents} → {@code essentialcraft5:hoanna/overworld/continents} */
    public static final String COPY_PREFIX = "hoanna/";
    public static final int SEA_LEVEL = 63;

    private HoannaTerrain() {
    }

    /** 原版 id 对应的副本 id（只接受 minecraft 命名空间） */
    public static ResourceLocation copyId(ResourceLocation vanilla) {
        if (!ResourceLocation.DEFAULT_NAMESPACE.equals(vanilla.getNamespace())) {
            throw new IllegalArgumentException("只复制原版的噪声 / 密度函数：" + vanilla);
        }
        return EssentialCraft5.id(COPY_PREFIX + vanilla.getPath());
    }

    /** 副本 id 对应的原版 id；不是副本返回 null */
    @Nullable
    public static ResourceLocation vanillaId(ResourceLocation copy) {
        if (!EssentialCraft5.MODID.equals(copy.getNamespace()) || !copy.getPath().startsWith(COPY_PREFIX)) {
            return null;
        }
        return ResourceLocation.withDefaultNamespace(copy.getPath().substring(COPY_PREFIX.length()));
    }

    public static <T> ResourceKey<T> copyKey(ResourceKey<T> vanilla) {
        ResourceKey<? extends Registry<T>> registry = ResourceKey.createRegistryKey(vanilla.registry());
        return ResourceKey.create(registry, copyId(vanilla.location()));
    }

    /**
     * 海纳尔的噪声设置。{@code base} = 用「引用副本」的上下文算出来的 {@code NoiseGeneratorSettings.overworld(ctx, false, false)}，
     * 这里只换掉路由以外的字段。
     */
    public static NoiseGeneratorSettings settings(NoiseGeneratorSettings base) {
        return new NoiseGeneratorSettings(base.noiseSettings(), Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState(),
                base.noiseRouter(), surfaceRule(), base.spawnTarget(), SEA_LEVEL,
                true,    // disable_mob_generation：生成区块时不放动物（群系 JSON 的 creature 表本来就空）
                true,    // aquifers_enabled
                false,   // ore_veins_enabled：铜铁大矿脉（原作没有）
                false);  // legacy_random_source
    }

    /** 海纳尔自己的规则（{@link HoannaSurfaceRules#rules()}，M1）在前，原版主世界规则在后 */
    public static SurfaceRules.RuleSource surfaceRule() {
        List<SurfaceRules.RuleSource> rules = new ArrayList<>(HoannaSurfaceRules.rules());
        rules.add(SurfaceRuleData.overworld());
        return SurfaceRules.sequence(rules.toArray(new SurfaceRules.RuleSource[0]));
    }
}
