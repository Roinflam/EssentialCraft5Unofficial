package com.flameforge.essentialcraft5.worldgen.hoanna.outpost;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECHoannaStructures;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.RegistryObject;

/**
 * 审判庭前哨（第十三批 13e-S3，batch13.md §6.4）的注册入口：1 个结构类型 {@code inquisitorium_outpost}、4 个拼件类型 {@code outpost_arena}、{@code outpost_pylon}、{@code outpost_dais}、{@code outpost_camp}。
 * <p>
 * 由 {@code ECHoannaStructures.init()} 调 {@link #init()}：本类加载时静态字段登记，赶在 RegisterEvent 之前（写法见 ECHoannaStructures 的类注释）。
 * 结构见 {@link InquisitoriumOutpostStructure}，拼件见 {@link OutpostPieces}；数据包里的 {@code worldgen/structure/inquisitorium_outpost.json}、{@code worldgen/structure_set/inquisitorium_outpost.json}、
 * 群系标签 {@code has_structure/inquisitorium_outpost} 第十三批 13e 预备已写好。
 * <p>
 * <b>注册名上线后不能改</b>：结构类型写在数据包 JSON 里，拼件类型写进区块存档的结构起点（改了旧存档的区块读不回来）；
 * 名字照 {@code ECHoannaStructures.PLANNED_*}。拼件 NBT 里存的字段上线后只能加、不能改含义。
 */
public final class OutpostStructures {

    public static final RegistryObject<StructureType<InquisitoriumOutpostStructure>> INQUISITORIUM_OUTPOST =
            ECHoannaStructures.structureType("inquisitorium_outpost", InquisitoriumOutpostStructure.CODEC);

    public static final RegistryObject<StructurePieceType> OUTPOST_ARENA = ECHoannaStructures.contextlessPiece("outpost_arena", OutpostPieces.Arena::new);
    public static final RegistryObject<StructurePieceType> OUTPOST_PYLON = ECHoannaStructures.contextlessPiece("outpost_pylon", OutpostPieces.Pylon::new);
    public static final RegistryObject<StructurePieceType> OUTPOST_DAIS = ECHoannaStructures.contextlessPiece("outpost_dais", OutpostPieces.Dais::new);
    public static final RegistryObject<StructurePieceType> OUTPOST_CAMP = ECHoannaStructures.contextlessPiece("outpost_camp", OutpostPieces.Camp::new);

    /** 结构 {@code essentialcraft5:inquisitorium_outpost}（数据包 {@code worldgen/structure}） */
    public static final ResourceKey<Structure> STRUCTURE = ResourceKey.create(Registries.STRUCTURE, EssentialCraft5.id("inquisitorium_outpost"));
    /** 结构集 {@code essentialcraft5:inquisitorium_outpost}（数据包 {@code worldgen/structure_set}；解码器改指、普查按它找） */
    public static final ResourceKey<StructureSet> SET = ResourceKey.create(Registries.STRUCTURE_SET, EssentialCraft5.id("inquisitorium_outpost"));
    /** 结构集 JSON 里的 salt（上线后不能改：改了所有前哨都挪位置）；JSON 和它一致由 GameTest 守着 */
    public static final int SALT = 202610267;

    private OutpostStructures() {
    }

    /** 调用它只是为了触发本类加载（静态字段登记） */
    public static void init() {
    }
}
