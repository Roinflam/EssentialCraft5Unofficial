package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.worldgen.hoanna.feature.AttachedClusterFeature;
import com.flameforge.essentialcraft5.worldgen.hoanna.feature.DeadTreeFeature;
import com.flameforge.essentialcraft5.worldgen.hoanna.feature.DebrisPileFeature;
import com.flameforge.essentialcraft5.worldgen.hoanna.feature.LeyVentFeature;
import com.flameforge.essentialcraft5.worldgen.hoanna.feature.MarrowSpikeFeature;
import com.flameforge.essentialcraft5.worldgen.hoanna.feature.MidscapeFeature;
import com.flameforge.essentialcraft5.worldgen.hoanna.feature.MilestoneFeature;
import com.flameforge.essentialcraft5.worldgen.hoanna.feature.OutcropFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * 第十三批 13d-H1b「地物与中景观」的注册类（batch13.md §6.1、§7、§9.4；HS §2.1.3、§2.1.4）。
 * <ul>
 *   <li>Java 地物类型（{@code ECRegistries.FEATURES}，类在 {@code worldgen/hoanna/feature/}）：喷口、霜髓冰刺、残骸堆、枯树、榍石露头、
 *       贴附晶簇、中景观、里程碑。名字是地物<b>类型</b>的注册名（{@code minecraft:worldgen/feature}），和 placed feature 不是一个注册表。</li>
 *   <li>configured / placed feature 和各群系每一步的列表是数据，由 {@code tools/gen/hoanna_features.py} 生成（跑 {@code tools/gen/hoanna_biomes.py}）；
 *       这里只放 placed feature 的键，给测试和展示厅按键取（照 {@link ECHoannaNature} 的写法）。</li>
 * </ul>
 * 地物参数是数据（数据包可覆盖），没有服务端配置；也没有语言键、音效。所有新地物只挂海纳尔群系（主世界零变化，policy §4.19 P3）。
 */
public final class ECHoannaFeatures {

    // ------------------------------------------------------------------ 地物类型

    public static final RegistryObject<LeyVentFeature> LEY_VENT_FEATURE =
            ECRegistries.FEATURES.register("ley_vent_patch", () -> new LeyVentFeature(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<MarrowSpikeFeature> MARROW_SPIKE_FEATURE =
            ECRegistries.FEATURES.register("marrow_spike", () -> new MarrowSpikeFeature(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<DebrisPileFeature> DEBRIS_PILE_FEATURE =
            ECRegistries.FEATURES.register("debris_pile", () -> new DebrisPileFeature(DebrisPileFeature.Config.CODEC));
    public static final RegistryObject<DeadTreeFeature> DEAD_TREE_FEATURE =
            ECRegistries.FEATURES.register("dead_tree", () -> new DeadTreeFeature(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<OutcropFeature> OUTCROP_FEATURE =
            ECRegistries.FEATURES.register("outcrop", () -> new OutcropFeature(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<AttachedClusterFeature> ATTACHED_CLUSTER_FEATURE =
            ECRegistries.FEATURES.register("attached_cluster", () -> new AttachedClusterFeature(SimpleBlockConfiguration.CODEC));
    public static final RegistryObject<MidscapeFeature> MIDSCAPE_FEATURE =
            ECRegistries.FEATURES.register("midscape", () -> new MidscapeFeature(NoneFeatureConfiguration.CODEC));
    public static final RegistryObject<MilestoneFeature> MILESTONE_FEATURE =
            ECRegistries.FEATURES.register("milestone", () -> new MilestoneFeature(NoneFeatureConfiguration.CODEC));

    // ------------------------------------------------------------------ placed feature 的键（JSON 由 hoanna_features.py 生成）

    public static final ResourceKey<PlacedFeature> HOANNA_LEY_VENT = placed("hoanna_ley_vent");
    public static final ResourceKey<PlacedFeature> HOANNA_LEY_VENT_SPARSE = placed("hoanna_ley_vent_sparse");
    public static final ResourceKey<PlacedFeature> HOANNA_CRIMSON_GROWTH = placed("hoanna_crimson_growth");
    public static final ResourceKey<PlacedFeature> FROST_MARROW_SPIKES = placed("frost_marrow_spikes");
    public static final ResourceKey<PlacedFeature> FROZEN_DEBRIS = placed("frozen_debris");
    public static final ResourceKey<PlacedFeature> TIDE_BUD_BED = placed("tide_bud_bed");
    public static final ResourceKey<PlacedFeature> RADIANT_CLUSTER_HOTSPOT = placed("radiant_cluster_hotspot");
    public static final ResourceKey<PlacedFeature> SCORCHED_GROUND = placed("scorched_ground");
    public static final ResourceKey<PlacedFeature> DEAD_TREE = placed("dead_tree");
    public static final ResourceKey<PlacedFeature> BONE_PILE = placed("bone_pile");
    public static final ResourceKey<PlacedFeature> TITANITE_OUTCROP = placed("titanite_outcrop");
    public static final ResourceKey<PlacedFeature> UMBRAL_CLUSTER_PATCH = placed("umbral_cluster_patch");
    public static final ResourceKey<PlacedFeature> OBSIDIAN_STUB = placed("obsidian_stub");
    public static final ResourceKey<PlacedFeature> ABYSSAL_SALT_DISK = placed("abyssal_salt_disk");
    public static final ResourceKey<PlacedFeature> SUNKEN_DEBRIS = placed("sunken_debris");
    public static final ResourceKey<PlacedFeature> RUIN_DEBRIS = placed("ruin_debris");
    public static final ResourceKey<PlacedFeature> HOANNA_MIDSCAPE = placed("hoanna_midscape");
    public static final ResourceKey<PlacedFeature> HOANNA_MILESTONE = placed("hoanna_milestone");

    /** 本批新加的全部 placed feature（测试按它核「只在海纳尔」） */
    public static final List<ResourceKey<PlacedFeature>> ALL_PLACED = List.of(HOANNA_LEY_VENT, HOANNA_LEY_VENT_SPARSE, HOANNA_CRIMSON_GROWTH,
            FROST_MARROW_SPIKES, FROZEN_DEBRIS, TIDE_BUD_BED, RADIANT_CLUSTER_HOTSPOT, SCORCHED_GROUND, DEAD_TREE, BONE_PILE, TITANITE_OUTCROP,
            UMBRAL_CLUSTER_PATCH, OBSIDIAN_STUB, ABYSSAL_SALT_DISK, SUNKEN_DEBRIS, RUIN_DEBRIS, HOANNA_MIDSCAPE, HOANNA_MILESTONE);

    private ECHoannaFeatures() {
    }

    private static ResourceKey<PlacedFeature> placed(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, EssentialCraft5.id(name));
    }

    /** 由 ECRegistries.register() 调（模组构造阶段）：类加载即登记静态字段里的条目 */
    public static void init() {
    }
}
