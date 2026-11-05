package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.loot.FortuneScaledCount;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 矿石与世界生成：15 种元素蕴魔矿石（原作 oreDrops:0~14，BlockDropsOre）和 3 种米斯莱矿石（oreMithriline:0~2，BlockMithrilineOre）。
 * 原作靠 DIMENSION × TYPE 两个方块状态区分，这里拆成 18 个注册名：
 * oreDrops 的 meta = 维度 × 5 + 矿序号（元素 0、火 1、水 2、地 3、风 4），维度 0 主世界、1 下界（nether_ 前缀）、2 末地（end_ 前缀）；
 * oreMithriline 的 meta = 维度。新旧名对照见 tools/id_map_blocks.json。
 *
 * <h2>方块</h2>
 * <ul>
 *   <li>元素蕴魔矿石：registerBlockSimple(…, 1, 1, 1) → 硬度 1、抗爆 1.0；米斯莱矿石：setHardness(0.4).setResistance(1) → 硬度 0.4、抗爆 0.6。
 *       原作是 Material.ROCK，任何镐都能挖（不要求石镐），徒手不掉落。下界、末地的变种和主世界完全一样，原作没区分。
 *       原作元素矿的不透光度是 registerBlockSimple 顺手填的 1（光能穿过矿脉），这里按普通矿石完全不透光；
 *       声音：下界变种用原版下界矿石的声音（原作全是石头声，纯表现）。</li>
 *   <li>经验：原作 getExpDrop = 0~2 且精准采集不给（1.12 Forge 的 BreakEvent 规则）→ 原版 DropExperienceBlock(UniformInt 0~2)。</li>
 *   <li>掉落：精准采集掉矿石本身；否则元素矿掉 1 ~ 2×(时运+1) 颗对应魔晶（元素矿掉元素晶石），米斯莱矿掉 3 ~ 8×(时运+1)+2 份米斯莱粉，
 *       都是均匀分布，用自定义战利品函数 {@link FortuneScaledCount} 精确还原；爆炸衰减跟随原版游戏规则：方块爆炸、生物爆炸默认按 1/半径逐个保留（和原作一样），
 *       TNT 按 tntExplosionDropDecay（默认关）全额掉落——原作 TNT 也衰减，这一点和原作不同，跟原版走。</li>
 *   <li>模型：原作 overlayed 双层方块（里层是动态的元素贴图、不受光照明暗，外层是挖了洞的母岩贴图），
 *       导入为 models/block/ore_overlayed.json（cutout_mipped），每种矿石一个子模型换贴图。</li>
 * </ul>
 *
 * <h2>世界生成（数据文件在 data/essentialcraft5/worldgen 与 data/essentialcraft5/forge/biome_modifier）</h2>
 * 原作 WorldGenElementalDrops：每区块尝试 Config.oreGenAttempts 次（默认 4，0 为关闭），每次在区块内随机取 x、z，
 * y 在 [0, 256) 均匀；然后从 6 种（元素、火、水、地、风、米斯莱）里<b>有放回</b>地抽 3 种，各生成一条矿脉
 * （元素矿脉大小 16，其余 8；原版 WorldGenMinable，与 1.20 的 OreFeature 是同一个算法）。维度决定替换的母岩和生成哪一套矿石。
 * <p>
 * 换算：对某一种矿石，每区块 4 次 × 3 抽 = 12 抽，每抽选中它的概率 1/6，所以每区块条数 ~ 二项分布 B(12, 1/6)，期望 2 条。
 * placed feature 写成 {@code count 12 → rarity_filter 1/6 → in_square → height_range → biome}，
 * 12 个候选点各自以 1/6 保留，<b>正好就是 B(12, 1/6)</b>，逐区块的分布和原作一模一样（不只是平均数）。
 * 与原作的细微差别：原作同一次尝试里抽中的矿脉挨在一起（元素矿在尝试点上，其余在 ±5 格内），这里各种矿石的位置互相独立；
 * 各矿石的边缘分布不变，只是「几种矿扎堆」少了。
 * <p>
 * 高度：三个维度都是绝对 y 0~255，和原作一样。
 * <ul>
 *   <li>主世界：1.18 起往下扩到 −64，但 y &lt; 0 全是深板岩。<b>不做深板岩变种</b>——要新画 6 张母岩贴图、多 6 个注册名（命名表里没有），
 *       原作的矿本来也只在 0 以上；0 以上的石头层（0 ~ 地表，山地更高）和 1.12 的石头层体量相当，所以每区块实际成矿量和原作接近。
 *       目标是 #minecraft:stone_ore_replaceables（石头、花岗岩、闪长岩、安山岩，正好是 1.12 的 Blocks.STONE 各变种），
 *       不碰深板岩和凝灰岩，所以 0~8 的过渡层里矿脉只会替换其中的石头。</li>
 *   <li>下界、末地：维度高度仍是 0~256，原样照搬。下界 128 以上是空的，原作就有一半尝试落空，照搬后等效条数不变（每区块约 1 条）；
 *       OreFeature 在地表高度图以上的尝试会直接返回，几乎不花时间。有模组把下界地形加高时，矿石按同样的密度跟着长上去。</li>
 * </ul>
 * 原作配置 OreGenAttempts 的等价物：数据包覆盖 placed_feature 的 count（= 3 × 尝试次数，0 为不生成），
 * 或把 biome_modifier 覆盖成 {@code {"type": "forge:none"}} 整维度关闭。不做 Java 配置。
 * 原作的模组维度走主世界分支；1.20 按生物群系标签 #minecraft:is_overworld / is_nether / is_end，模组维度默认不生成。
 * 原作的海纳尔维度也走主世界分支：第六批 6a（M1）加了 {@code forge/biome_modifier/hoanna_ores.json}（biomes = #essentialcraft5:is_hoanna），
 * 海纳尔直接复用主世界这 6 个 placed feature（同样的密度，由 tools/gen/hoanna_biomes.py 生成）。
 * <b>以后主世界矿要加稀有度、改密度，必须同时拆出一份 {@code hoanna_} 前缀的 placed feature 给海纳尔用</b>（上古遗迹就是这么拆的：
 * 主世界 {@code elder_mrucu} 带稀有度 16，海纳尔 {@code elder_mrucu_hoanna} 带 3），否则海纳尔的矿会跟着变少。
 */
public final class ECWorldGen {

    /**
     * 母岩（原作 OreDimensionType）：注册名前缀、外层贴图、地图颜色、声音。
     * 世界生成替换的方块写在 configured_feature 里：主世界 #minecraft:stone_ore_replaceables，下界 netherrack，末地 end_stone。
     */
    public enum Host {
        OVERWORLD("", "block/elemental_base", MapColor.STONE, SoundType.STONE),
        NETHER("nether_", "block/elemental_nether", MapColor.NETHER, SoundType.NETHER_ORE),
        END("end_", "block/elemental_end", MapColor.SAND, SoundType.STONE);

        public final String prefix;
        final String texture;
        final MapColor mapColor;
        final SoundType sound;

        Host(String prefix, String texture, MapColor mapColor, SoundType sound) {
            this.prefix = prefix;
            this.texture = texture;
            this.mapColor = mapColor;
            this.sound = sound;
        }
    }

    /**
     * 一种矿石的规格，GameTest（核对掉落表和世界生成数据）和指南模块可以直接遍历。
     *
     * @param drop     普通挖掘掉的物品
     * @param minDrops 时运 0 时的最少掉落数
     * @param spread   掉落数 = minDrops + [0, spread × (时运+1))
     * @param veinSize 矿脉大小（OreFeature 的 size）
     */
    public record Ore(RegistryObject<DropExperienceBlock> block, Host host, Supplier<? extends ItemLike> drop,
                      int minDrops, int spread, int veinSize) {
    }

    /** 原作 getExpDrop：MathHelper.getInt(RANDOM, 0, 2)，两端都含 */
    private static final UniformInt EXPERIENCE = UniformInt.of(0, 2);
    private static final List<Ore> ORES = new ArrayList<>();

    // ---- 元素蕴魔矿石（oreDrops）。登记顺序即创造模式物品栏顺序，和原作 getSubBlocks 的 meta 0~14 一致 ----

    public static final RegistryObject<DropExperienceBlock> ELEMENTAL_INFUSED_ORE =
            infusedOre(Host.OVERWORLD, "elemental", () -> ECMaterials.ELEMENTAL_GEM.get());
    public static final RegistryObject<DropExperienceBlock> FIRE_INFUSED_ORE =
            infusedOre(Host.OVERWORLD, "fire", () -> ECMaterials.FIRE_DROPS.get());
    public static final RegistryObject<DropExperienceBlock> WATER_INFUSED_ORE =
            infusedOre(Host.OVERWORLD, "water", () -> ECMaterials.WATER_DROPS.get());
    public static final RegistryObject<DropExperienceBlock> EARTH_INFUSED_ORE =
            infusedOre(Host.OVERWORLD, "earth", () -> ECMaterials.EARTH_DROPS.get());
    public static final RegistryObject<DropExperienceBlock> AIR_INFUSED_ORE =
            infusedOre(Host.OVERWORLD, "air", () -> ECMaterials.AIR_DROPS.get());

    public static final RegistryObject<DropExperienceBlock> NETHER_ELEMENTAL_INFUSED_ORE =
            infusedOre(Host.NETHER, "elemental", () -> ECMaterials.ELEMENTAL_GEM.get());
    public static final RegistryObject<DropExperienceBlock> NETHER_FIRE_INFUSED_ORE =
            infusedOre(Host.NETHER, "fire", () -> ECMaterials.FIRE_DROPS.get());
    public static final RegistryObject<DropExperienceBlock> NETHER_WATER_INFUSED_ORE =
            infusedOre(Host.NETHER, "water", () -> ECMaterials.WATER_DROPS.get());
    public static final RegistryObject<DropExperienceBlock> NETHER_EARTH_INFUSED_ORE =
            infusedOre(Host.NETHER, "earth", () -> ECMaterials.EARTH_DROPS.get());
    public static final RegistryObject<DropExperienceBlock> NETHER_AIR_INFUSED_ORE =
            infusedOre(Host.NETHER, "air", () -> ECMaterials.AIR_DROPS.get());

    public static final RegistryObject<DropExperienceBlock> END_ELEMENTAL_INFUSED_ORE =
            infusedOre(Host.END, "elemental", () -> ECMaterials.ELEMENTAL_GEM.get());
    public static final RegistryObject<DropExperienceBlock> END_FIRE_INFUSED_ORE =
            infusedOre(Host.END, "fire", () -> ECMaterials.FIRE_DROPS.get());
    public static final RegistryObject<DropExperienceBlock> END_WATER_INFUSED_ORE =
            infusedOre(Host.END, "water", () -> ECMaterials.WATER_DROPS.get());
    public static final RegistryObject<DropExperienceBlock> END_EARTH_INFUSED_ORE =
            infusedOre(Host.END, "earth", () -> ECMaterials.EARTH_DROPS.get());
    public static final RegistryObject<DropExperienceBlock> END_AIR_INFUSED_ORE =
            infusedOre(Host.END, "air", () -> ECMaterials.AIR_DROPS.get());

    // ---- 米斯莱矿石（oreMithriline:0~2） ----

    public static final RegistryObject<DropExperienceBlock> MITHRILINE_ORE = mithrilineOre(Host.OVERWORLD);
    public static final RegistryObject<DropExperienceBlock> NETHER_MITHRILINE_ORE = mithrilineOre(Host.NETHER);
    public static final RegistryObject<DropExperienceBlock> END_MITHRILINE_ORE = mithrilineOre(Host.END);

    private ECWorldGen() {
    }

    /** 没有机器配置要登记；静态字段在类加载时已登记进 DeferredRegister，调用即触发 */
    public static void init() {
    }

    public static List<Ore> ores() {
        return Collections.unmodifiableList(ORES);
    }

    /**
     * 元素蕴魔矿石：硬度 1、抗爆 legacyResistance(1, 1, 先抗爆) = 1.0；元素晶石矿脉 16，其余 8；
     * 掉落 1 ~ 2×(时运+1)（原作 nextInt(2×(fortune+1)) + 1）。
     */
    private static RegistryObject<DropExperienceBlock> infusedOre(Host host, String element, Supplier<? extends ItemLike> drop) {
        return ore(host, host.prefix + element + "_infused_ore", "block/elemental_" + element,
                1F, ECBlocks.legacyResistance(1F, 1F, true), drop, 1, 2, "elemental".equals(element) ? 16 : 8);
    }

    /**
     * 米斯莱矿石：setHardness(0.4).setResistance(1)（先硬度后抗爆）→ 抗爆 0.6；矿脉 8；
     * 掉落 3 ~ 8×(时运+1)+2（原作 nextInt(8×(fortune+1)) + 3）。
     */
    private static RegistryObject<DropExperienceBlock> mithrilineOre(Host host) {
        return ore(host, host.prefix + "mithriline_ore", "block/mithriline",
                0.4F, ECBlocks.legacyResistance(0.4F, 1F, false), () -> ECMaterials.MITHRILINE_DUST.get(), 3, 8, 8);
    }

    private static RegistryObject<DropExperienceBlock> ore(Host host, String name, String innerTexture,
                                                           float hardness, float resistance,
                                                           Supplier<? extends ItemLike> drop, int minDrops, int spread, int veinSize) {
        RegistryObject<DropExperienceBlock> block = ECBlocks.register(name,
                () -> new DropExperienceBlock(BlockBehaviour.Properties.of()
                        .mapColor(host.mapColor)
                        .instrument(NoteBlockInstrument.BASEDRUM)
                        .sound(host.sound)
                        .strength(hardness, resistance)
                        .requiresCorrectToolForDrops(), EXPERIENCE),
                // 标签（forge:ores 等）写在 tools/tags/worldgen.json；挖掘工具保持默认的 mineable/pickaxe、不要求等级
                BlockSpec.of(overlayedModel(innerTexture, host.texture)).loot(fortuneLoot(drop, minDrops, spread)));
        ORES.add(new Ore(block, host, drop, minDrops, spread, veinSize));
        return block;
    }

    /** 原作 overlayed 模型：in = 里层元素贴图（动态），out = 外层母岩贴图（有透明洞） */
    private static BlockSpec.ModelGen overlayedModel(String inner, String outer) {
        return (provider, block) -> provider.simpleBlockWithItem(block, provider.models()
                .withExistingParent(provider.name(block), provider.modLoc("block/ore_overlayed"))
                .texture("in", provider.tex(inner))
                .texture("out", provider.tex(outer))
                .renderType("cutout_mipped"));
    }

    /** 精准采集掉矿石本身；否则按原作公式掉 drop，再受爆炸衰减 */
    private static BlockSpec.LootGen fortuneLoot(Supplier<? extends ItemLike> drop, int minDrops, int spread) {
        return (loot, block) -> loot.table(block, loot.silkTouchOr(block,
                LootItem.lootTableItem(drop.get()).apply(FortuneScaledCount.count(minDrops, spread))));
    }
}
