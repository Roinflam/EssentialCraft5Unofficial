package com.flameforge.essentialcraft5.worldgen.hoanna.vault;

import com.flameforge.essentialcraft5.registry.ECHoannaStructures;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.RegistryObject;

/**
 * 封印宝库（第十三批 13e-S1，batch13.md §6.4）的注册入口：1 个结构类型 {@code sealed_vault}、4 个拼件类型 {@code vault_stele}、{@code vault_shaft}、{@code vault_antechamber}、{@code vault_chamber}。
 * <p>
 * 由 {@code ECHoannaStructures.init()} 调 {@link #init()}：本类加载时静态字段登记，赶在 RegisterEvent 之前（写法见 ECHoannaStructures 的类注释）。
 * 结构见 {@link SealedVaultStructure}，拼件见 {@link VaultPieces}；数据包里的 {@code worldgen/structure/sealed_vault.json}、{@code worldgen/structure_set/sealed_vault.json}、
 * 群系标签 {@code has_structure/sealed_vault} 第十三批 13e 预备已写好。
 * <p>
 * <b>注册名上线后不能改</b>：结构类型写在数据包 JSON 里，拼件类型写进区块存档的结构起点（改了旧存档的区块读不回来）；
 * 名字照 {@code ECHoannaStructures.PLANNED_*}。拼件 NBT 里存的字段上线后只能加、不能改含义。
 */
public final class VaultStructures {

    public static final RegistryObject<StructureType<SealedVaultStructure>> SEALED_VAULT =
            ECHoannaStructures.structureType("sealed_vault", SealedVaultStructure.CODEC);

    public static final RegistryObject<StructurePieceType> VAULT_STELE = ECHoannaStructures.contextlessPiece("vault_stele", VaultPieces.Stele::new);
    public static final RegistryObject<StructurePieceType> VAULT_SHAFT = ECHoannaStructures.contextlessPiece("vault_shaft", VaultPieces.Shaft::new);
    public static final RegistryObject<StructurePieceType> VAULT_ANTECHAMBER = ECHoannaStructures.contextlessPiece("vault_antechamber", VaultPieces.Antechamber::new);
    public static final RegistryObject<StructurePieceType> VAULT_CHAMBER = ECHoannaStructures.contextlessPiece("vault_chamber", VaultPieces.Chamber::new);

    private VaultStructures() {
    }

    /** 调用它只是为了触发本类加载（静态字段登记） */
    public static void init() {
    }
}
