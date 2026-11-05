package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.item.HoannaSpecialtyItem;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 第十三批 13d 海纳尔的共用材料（batch13.md §6.1、§6.6；EC5 新物品，原作没有）。几路都要引用（升阶配方、勘测图配方、掉落表、巢穴奖励、
 * 六阶祭品的特产标签），所以在预备里先注册，不归哪一路：
 * <ul>
 *   <li>{@link #LEY_CRYSTAL} 地脉结晶（全海纳尔通用：地脉喷口右键、地脉风暴的落晶）：风之护甲 3 阶、勘测图、宝库锁孔；</li>
 *   <li>{@link #RADIANT_CRYSTAL} 辐晶（恐惧之地辐射热点里的辐晶簇）：虚空 2 阶；</li>
 *   <li>{@link #TIDE_CRYSTAL} 潮晶（魔力腐化之地大潮夜的潮晶芽）：宅男 2 阶（主线）、渊潮领主召唤；</li>
 *   <li>{@link #UMBRAL_CRYSTAL} 影晶（暗影腐化之地的影晶簇，夜里或黑暗天灾中收得多）：第二十二章主线、宅男 5 阶、裁决之刃 5 阶；</li>
 *   <li>{@link #FROST_MARROW} 霜髓（冰冻腐化之地的冰刺里，方块）：审判庭 1 → 2 阶、装饰；<b>只有精准采集才掉本体，否则什么都不掉</b>（batch13.md §8.2）；
 *       滑度同浮冰 0.98、亮度 3、不融化（普通方块，不是冰）；不当冷凝器的冷源。</li>
 * </ul>
 * 异变残质、异变之心（怪物掉落）在 {@link ECMobDrops}；渊盐在 {@code ECHoannaSurvey}。这六样特产（四种结晶、霜髓、渊盐）进物品标签
 * {@link #HOANNA_SPECIALTIES}（{@code tools/tags/echoannamaterials.json}），矩阵阶六阶的祭品读它。
 * 名字在命名表 tools/id_map_items.json / id_map_blocks.json，提示框文字在 tools/lang/keys/echoannamaterials.json；
 * 贴图现在都是换色的占位图（{@code textures/item/<名>.png}、{@code textures/block/hoanna/frost_marrow.png}），正式贴图另画后同名覆盖。
 */
public final class ECHoannaMaterials {

    /** 物品标签 {@code essentialcraft5:hoanna_specialties}：海纳尔六种特产（矩阵阶六阶祭品「海纳尔特产 ×4」） */
    public static final TagKey<Item> HOANNA_SPECIALTIES = ECTags.modItem("hoanna_specialties");

    /** 霜髓的滑度（同原版浮冰） */
    public static final float FROST_MARROW_FRICTION = 0.98F;
    /** 霜髓的亮度 */
    public static final int FROST_MARROW_LIGHT = 3;

    public static final RegistryObject<HoannaSpecialtyItem> LEY_CRYSTAL = crystal("ley_crystal", Rarity.COMMON);
    public static final RegistryObject<HoannaSpecialtyItem> RADIANT_CRYSTAL = crystal("radiant_crystal", Rarity.COMMON);
    public static final RegistryObject<HoannaSpecialtyItem> TIDE_CRYSTAL = crystal("tide_crystal", Rarity.COMMON);
    public static final RegistryObject<HoannaSpecialtyItem> UMBRAL_CRYSTAL = crystal("umbral_crystal", Rarity.UNCOMMON);

    /** 霜髓：硬度 2、镐、只有精准采集掉本体 */
    public static final RegistryObject<Block> FROST_MARROW = ECBlocks.register("frost_marrow",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).instrument(NoteBlockInstrument.CHIME)
                    .strength(2F).friction(FROST_MARROW_FRICTION).lightLevel(state -> FROST_MARROW_LIGHT).sound(SoundType.GLASS)),
            BlockSpec.of(BlockSpec.cubeAll("block/hoanna/frost_marrow")).loot((loot, block) -> loot.silkTouchOnly(block)));

    private ECHoannaMaterials() {
    }

    private static RegistryObject<HoannaSpecialtyItem> crystal(String name, Rarity rarity) {
        return ECItems.register(name, () -> new HoannaSpecialtyItem(new Item.Properties().rarity(rarity), name), ItemSpec.generated("item/" + name));
    }

    /** 由 ECRegistries.register() 调，只为触发类加载 */
    public static void init() {
    }
}
