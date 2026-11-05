package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * 代码里要用到的标签常量。只在数据文件里用、代码不查的标签（forge:ingots/xxx 等）写在 tools/tags/*.json，由 datagen 生成。
 * 矿辞到标签的对照见 tools/oredict_map.json。
 */
public final class ECTags {

    /** Curios 戒指位：原作 Baubles 的 RING */
    public static final TagKey<Item> CURIOS_RING = item("curios", "ring");
    public static final TagKey<Item> CURIOS_NECKLACE = item("curios", "necklace");
    public static final TagKey<Item> CURIOS_BELT = item("curios", "belt");

    /**
     * 能看见 MRU 的物品（拿在主手 / 副手或戴在头盔槽，见 api/MRUVision）。框架在 tools/tags/framework.json 建空标签，
     * 物品所在的模块往里加成员（ECGear：魔力单片镜、二阶 MRUCU 移动魔杖、魔力防护头盔）。
     */
    public static final TagKey<Item> MRU_VISION = modItem("mru_vision");

    /**
     * 只有风之 Tier（{@link com.flameforge.essentialcraft5.item.WindTier#WIND}，排在元素之后）及以上的工具才能挖出掉落的方块。
     * 框架在 tools/tags/framework3.json 建空标签（第三批 G7，目前没有方块要求它，给以后的方块和数据包用）。
     */
    public static final TagKey<Block> NEEDS_WIND_TOOL = modBlock("needs_wind_tool");

    // ---------------------------------------------------------------- 第十三批 13a（batch13.md §9.1 预备）

    /**
     * 不计成长的实体：矩阵图谱、同调度、击杀台账都不认（玩家克隆体、全息诱饵、毒雾、矩阵回响；13c 起再加召唤物）。
     * 静态数据 {@code data/essentialcraft5/tags/entity_types/growth_excluded.json}，还没注册的实体写成 {@code required: false}。
     */
    public static final TagKey<EntityType<?>> GROWTH_EXCLUDED = modEntity("growth_excluded");
    /** 矩阵图谱的「末地」组（潜影贝、末影龙） */
    public static final TagKey<EntityType<?>> CODEX_END = modEntity("codex_end");
    /** 矩阵图谱的「远征」组（掠夺者、卫道士、唤魔者、恼鬼、劫掠兽、守卫者、远古守卫者、监守者） */
    public static final TagKey<EntityType<?>> CODEX_EXPEDITION = modEntity("codex_expedition");
    /** 下界本地的九种敌对生物（烈焰人、恶魂、岩浆怪、凋灵骷髅、猪灵、猪灵蛮兵、疣猪兽、僵尸疣猪兽、僵尸猪灵），图谱「下界」组可以按它数 */
    public static final TagKey<EntityType<?>> CODEX_NETHER = modEntity("codex_nether");
    /** 海纳尔原生生物（13d 填，现在是空的）：图谱「海纳尔原生」组 */
    public static final TagKey<EntityType<?>> HOANNA_NATIVES = modEntity("hoanna_natives");
    /**
     * 不算进护盾「魔法覆盖」、暗影相性、审判庭抗魔的伤害类型（batch13.md §0.4 第 21 条：没有攻击者的自伤本来就不算，这个标签另可排除）。
     * 静态数据 {@code data/essentialcraft5/tags/damage_type/unshieldable.json}，现在是空的。
     */
    public static final TagKey<DamageType> UNSHIELDABLE = TagKey.create(Registries.DAMAGE_TYPE, EssentialCraft5.id("unshieldable"));
    /**
     * 恶魔交易里当作「一次只能交 1 件」的物品（{@code tools/tags/ecdemonic.json}）：一组虽然能放很多，可每件带着各不相同的 NBT，
     * 两件叠不到同一格里（已填充地图的编号、玩家头颅的主人、成书的内容）。交易界面只有一个投放格，要价超过 1 件就永远交不上，
     * 所以这些物品的要价夹到 1 件，也不进贵价池（{@link com.flameforge.essentialcraft5.demon.DemonWant#deliverLimit}）。
     * 整合包作者往交易池里加了同类物品时往这里加。
     */
    public static final TagKey<Item> DEMON_UNSTACKABLE = modItem("demon_unstackable");
    /** 能触发矩阵特性的武器（{@code tools/tags/matrix.json}；整合包作者可以用数据包往里加别的武器） */
    public static final TagKey<Item> MATRIX_ATTUNED = modItem("matrix_attuned");
    /** 凝结矩阵要的外壳方块：魔力护板、淡青护板（{@code tools/tags/condense.json}） */
    public static final TagKey<Block> CONDENSE_CASING = modBlock("condense_casing");

    // ---------------------------------------------------------------- 第十三批 13b

    /**
     * 不可修理、魔力修复器照样修的物品：能同调的四套盔甲（魔力防护、风之护甲、虚空、审判庭；{@code tools/tags/attunement.json}，引用四个家族标签）。
     * 它们的 {@code isRepairable} 一律为假：原版工作台修理配方 {@code minecraft:repair_item} 新建一件、只留诅咒附魔，砂轮合并两件只留上面那件，
     * 同调阶、同调度、阶戳、存着的 MRU 都会丢；修理配方只问网格里第一件能不能修，按「有没有同调数据」判断挡不全，所以整件不可修理。
     * 魔力修复器原地修、不动 NBT，按这个标签放行（{@code MagicalRepairerBlockEntity.canRepair}）；铁砧照原版（两件合修时右边那件当材料吃掉）。
     * 宅男盔甲不在里面：修复器本来就不修它（穿在玩家身上不掉耐久，见 {@code ComputerArmorItem.isRepairable}）。
     */
    public static final TagKey<Item> MRU_REPAIRABLE = modItem("mru_repairable");

    private ECTags() {
    }

    public static TagKey<Item> item(String namespace, String path) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    public static TagKey<Item> modItem(String path) {
        return ItemTags.create(EssentialCraft5.id(path));
    }

    public static TagKey<Block> block(String namespace, String path) {
        return BlockTags.create(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    public static TagKey<Block> modBlock(String path) {
        return BlockTags.create(EssentialCraft5.id(path));
    }

    public static TagKey<EntityType<?>> modEntity(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, EssentialCraft5.id(path));
    }

    /**
     * 同调家族的物品标签 {@code essentialcraft5:attunement/<家族>}（{@code tools/tags/attunement.json}；第十三批 13a，batch13.md §3.3.1）：
     * mhs_armor、wind_armor、void_armor、inquisitorium_armor、computer_armor、ember_sword、verdict_blade。
     */
    public static TagKey<Item> attunementFamily(String family) {
        return modItem("attunement/" + family);
    }
}
