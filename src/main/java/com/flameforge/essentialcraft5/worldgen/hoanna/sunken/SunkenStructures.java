package com.flameforge.essentialcraft5.worldgen.hoanna.sunken;

import com.flameforge.essentialcraft5.registry.ECHoannaStructures;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.RegistryObject;

/**
 * 沉没城区（第十三批 13e-S2，batch13.md §6.4）的注册入口：1 个结构类型 {@code sunken_district}、3 个拼件类型 {@code sunken_dome}、{@code sunken_house}、{@code sunken_tower}。
 * <p>
 * 由 {@code ECHoannaStructures.init()} 调 {@link #init()}：本类加载时静态字段登记，赶在 RegisterEvent 之前（写法见 ECHoannaStructures 的类注释）。
 * 结构见 {@link SunkenDistrictStructure}，拼件见 {@link SunkenPieces}；数据包里的 {@code worldgen/structure/sunken_district.json}、{@code worldgen/structure_set/sunken_district.json}、
 * 群系标签 {@code has_structure/sunken_district} 第十三批 13e 预备已写好。
 * <p>
 * <b>注册名上线后不能改</b>：结构类型写在数据包 JSON 里，拼件类型写进区块存档的结构起点（改了旧存档的区块读不回来）；
 * 名字照 {@code ECHoannaStructures.PLANNED_*}。拼件 NBT 里存的字段上线后只能加、不能改含义。
 */
public final class SunkenStructures {

    public static final RegistryObject<StructureType<SunkenDistrictStructure>> SUNKEN_DISTRICT =
            ECHoannaStructures.structureType("sunken_district", SunkenDistrictStructure.CODEC);

    public static final RegistryObject<StructurePieceType> SUNKEN_DOME = ECHoannaStructures.contextlessPiece("sunken_dome", SunkenPieces.Dome::new);
    public static final RegistryObject<StructurePieceType> SUNKEN_HOUSE = ECHoannaStructures.contextlessPiece("sunken_house", SunkenPieces.House::new);
    public static final RegistryObject<StructurePieceType> SUNKEN_TOWER = ECHoannaStructures.contextlessPiece("sunken_tower", SunkenPieces.Tower::new);

    private SunkenStructures() {
    }

    /** 调用它只是为了触发本类加载（静态字段登记） */
    public static void init() {
    }
}
