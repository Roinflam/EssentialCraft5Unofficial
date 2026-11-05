package com.flameforge.essentialcraft5.worldgen.hoanna.town;

import com.flameforge.essentialcraft5.registry.ECHoannaStructures;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.RegistryObject;

/**
 * 城镇废墟（M5，batch6a §5.5）的注册入口：1 个结构类型 {@code hoanna_town}、7 个拼件类型
 * {@code town_start / town_road / town_tower / town_house / town_spreader / town_torch / town_crater}。
 * <p>
 * 由 {@code ECHoannaStructures.init()} 调 {@link #init()}：本类加载时静态字段登记，赶在 RegisterEvent 之前（写法见 ECHoannaStructures 的类注释）。
 * 结构见 {@link HoannaTownStructure}，布局与拼件见 {@link HoannaTownPieces}；数据包里的 {@code worldgen/structure/hoanna_town.json}、
 * {@code worldgen/structure_set/hoanna_town.json}、群系标签 {@code has_structure/hoanna_town}、方块标签 {@code town_road_replaceable} 也归本模块。
 * <p>
 * <b>注册名上线后不能改</b>：结构类型写在数据包 JSON 里，拼件类型写进区块存档的结构起点（改了旧存档的区块读不回来）。
 * 原作的拼件名（ToStart、ToRoad、ToTower、ToHouse1~4、ToLight、ToSpreader、ToExplosion）是 1.12 存档里的，1.20.1 读不了 1.12 的结构数据，不用兼容；
 * House1~4 合成一个 {@code town_house}，Explosion 由 {@code town_crater} 取代。
 */
public final class TownStructures {

    public static final RegistryObject<StructureType<HoannaTownStructure>> HOANNA_TOWN =
            ECHoannaStructures.structureType("hoanna_town", HoannaTownStructure.CODEC);

    public static final RegistryObject<StructurePieceType> TOWN_START = ECHoannaStructures.contextlessPiece("town_start", HoannaTownPieces.Start::new);
    public static final RegistryObject<StructurePieceType> TOWN_ROAD = ECHoannaStructures.contextlessPiece("town_road", HoannaTownPieces.Road::new);
    public static final RegistryObject<StructurePieceType> TOWN_TOWER = ECHoannaStructures.contextlessPiece("town_tower", HoannaTownPieces.Tower::new);
    public static final RegistryObject<StructurePieceType> TOWN_HOUSE = ECHoannaStructures.contextlessPiece("town_house", HoannaTownPieces.House::new);
    public static final RegistryObject<StructurePieceType> TOWN_SPREADER =
            ECHoannaStructures.contextlessPiece("town_spreader", HoannaTownPieces.Spreader::new);
    public static final RegistryObject<StructurePieceType> TOWN_TORCH = ECHoannaStructures.contextlessPiece("town_torch", HoannaTownPieces.Torch::new);
    public static final RegistryObject<StructurePieceType> TOWN_CRATER = ECHoannaStructures.contextlessPiece("town_crater", HoannaTownPieces.Crater::new);

    private TownStructures() {
    }

    /** 调用它只是为了触发本类加载（静态字段登记） */
    public static void init() {
    }
}
