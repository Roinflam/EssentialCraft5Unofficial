package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.worldgen.hoanna.outpost.OutpostStructures;
import com.flameforge.essentialcraft5.worldgen.hoanna.sunken.SunkenStructures;
import com.flameforge.essentialcraft5.worldgen.hoanna.town.TownStructures;
import com.flameforge.essentialcraft5.worldgen.hoanna.underground.UndergroundStructures;
import com.flameforge.essentialcraft5.worldgen.hoanna.vault.VaultStructures;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 海纳尔三种结构的结构类型与拼件类型注册（第六批 F6b，batch6a §6.2 第 1 条）。内容由两个模块填：
 * {@link TownStructures}（M5 城镇废墟）、{@link UndergroundStructures}（M6 古代墓穴与现代矿井），本类的 {@link #init()} 调它们的 init()。
 * 第十三批 13e（batch13.md §6.4）再加三座：{@link VaultStructures}（封印宝库）、{@link SunkenStructures}（沉没城区）、
 * {@link OutpostStructures}（审判庭前哨），13e 预备注册了类型和拼件的空壳，内容由 13e-S1 / S2 / S3 填。
 * <p>
 * <b>写法</b>（二选一里选了 RegisterEvent，同 ECMRUCU）：ECRegistries 没有 STRUCTURE_TYPE / STRUCTURE_PIECE 的 DeferredRegister，共用文件不能改，
 * 所以这里自己登记、在模组总线的 {@link RegisterEvent} 里注册。模块在自己的静态字段里调：
 * <pre>
 * // TownStructures（字段写在 TownStructures 里，它的 init() 被调时类加载、字段登记）
 * public static final RegistryObject&lt;StructureType&lt;HoannaTownStructure&gt;&gt; HOANNA_TOWN =
 *         ECHoannaStructures.structureType("hoanna_town", HoannaTownStructure.CODEC);   // CODEC = Structure.simpleCodec(HoannaTownStructure::new)
 * public static final RegistryObject&lt;StructurePieceType&gt; TOWN_ROAD =
 *         ECHoannaStructures.contextlessPiece("town_road", HoannaTownPieces.Road::new);   // Road(CompoundTag) 读档构造器
 * </pre>
 * 结构的 {@code type()} 返回 {@code HOANNA_TOWN.get()}；拼件构造器传 {@code TOWN_ROAD.get()}。登记必须在 RegisterEvent 之前（模组构造期、init() 链上），晚了抛异常。
 * <p>
 * <b>名字</b>：结构类型 id 写在数据包 {@code worldgen/structure/*.json} 的 type 里，拼件类型 id 写进区块存档的结构起点——<b>上线后都不能改名</b>
 * （改了旧存档的区块读不回来）。计划里的全部名字列在 {@link #PLANNED_STRUCTURE_TYPES}、{@link #PLANNED_PIECE_TYPES}，GameTest 核对注册的都在表里。
 * <p>
 * <b>结构 JSON 不在框架里出</b>：{@code worldgen/structure/*.json} 引用的类型没注册时数据包加载失败、服务器起不来，所以由注册类型的模块自己带。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ECHoannaStructures {

    /**
     * 计划里的结构类型（batch6a §1.1）：M5 hoanna_town；M6 old_catacombs、modern_shafts。
     * 第十三批 13e 的三座（batch13.md §6.4，13d 预备先登记名字，13e 预备注册）：封印宝库 sealed_vault、沉没城区 sunken_district、审判庭前哨 inquisitorium_outpost。
     * 13d、13e 一起发布，发布以后这三个名字和下面的拼件名不能改。
     */
    public static final Set<String> PLANNED_STRUCTURE_TYPES = Set.of("hoanna_town", "old_catacombs", "modern_shafts",
            "sealed_vault", "sunken_district", "inquisitorium_outpost");
    /** 计划里的城镇拼件（M5，7 种） */
    public static final Set<String> PLANNED_TOWN_PIECES = Set.of("town_start", "town_road", "town_tower", "town_house", "town_spreader", "town_torch",
            "town_crater");
    /** 计划里的地下拼件（M6，墓穴 3 种 + 矿井 4 种） */
    public static final Set<String> PLANNED_UNDERGROUND_PIECES = Set.of("catacomb_room", "catacomb_corridor", "catacomb_exit",
            "shaft_room", "shaft_corridor", "shaft_crossing", "shaft_stairs");
    /** 第十三批 13e 封印宝库的拼件（HS §2.4.1：地面先辈碑与平台、螺旋井、前厅、宝库室，4 种） */
    public static final Set<String> PLANNED_VAULT_PIECES = Set.of("vault_stele", "vault_shaft", "vault_antechamber", "vault_chamber");
    /** 第十三批 13e 沉没城区的拼件（HS §2.4.2：空气穹顶、沉房、倒塌的塔身，3 种） */
    public static final Set<String> PLANNED_SUNKEN_PIECES = Set.of("sunken_dome", "sunken_house", "sunken_tower");
    /** 第十三批 13e 审判庭前哨的拼件（HS §2.4.3：场地圆盘、外圈石柱、中央信标座、场外营地，4 种） */
    public static final Set<String> PLANNED_OUTPOST_PIECES = Set.of("outpost_arena", "outpost_pylon", "outpost_dais", "outpost_camp");
    /** 计划里的全部拼件（6a 的 14 种 + 13e 的 11 种） */
    public static final Set<String> PLANNED_PIECE_TYPES = union(PLANNED_TOWN_PIECES, PLANNED_UNDERGROUND_PIECES, PLANNED_VAULT_PIECES,
            PLANNED_SUNKEN_PIECES, PLANNED_OUTPOST_PIECES);

    private static final Map<ResourceLocation, StructureType<?>> STRUCTURE_TYPES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, StructurePieceType> PIECE_TYPES = new LinkedHashMap<>();
    private static boolean structureTypesRegistered;
    private static boolean pieceTypesRegistered;

    private ECHoannaStructures() {
    }

    /** 由 ECRegistries.register() 调（F6a 接线）：触发两个模块注册类的类加载，它们的静态字段在这时登记 */
    public static void init() {
        TownStructures.init();
        UndergroundStructures.init();
        VaultStructures.init();
        SunkenStructures.init();
        OutpostStructures.init();
    }

    // ---------------------------------------------------------------- 登记

    /**
     * 登记一个结构类型（{@code StructureType<S> = () -> codec}）。
     *
     * @param name  注册名（本模组命名空间），和结构 JSON 的 {@code "type": "essentialcraft5:<name>"} 一致
     * @param codec 通常是 {@code Structure.simpleCodec(构造器)}
     */
    public static synchronized <S extends Structure> RegistryObject<StructureType<S>> structureType(String name, Codec<S> codec) {
        if (structureTypesRegistered) {
            throw new IllegalStateException("海纳尔结构类型 " + name + " 登记得太晚：RegisterEvent 已经发过了（要在模组构造期、init() 链上登记）");
        }
        ResourceLocation id = EssentialCraft5.id(name);
        StructureType<S> type = () -> codec;
        if (STRUCTURE_TYPES.putIfAbsent(id, type) != null) {
            throw new IllegalStateException("海纳尔结构类型重名：" + id);
        }
        return RegistryObject.create(id, Registries.STRUCTURE_TYPE, EssentialCraft5.MODID);
    }

    /**
     * 登记一个拼件类型（要读档上下文的：{@code load(StructurePieceSerializationContext, CompoundTag)}）。
     * 只要 CompoundTag 的用 {@link #contextlessPiece}（两个方法名字不同，免得构造器引用在重载之间有歧义）。
     */
    public static synchronized RegistryObject<StructurePieceType> pieceType(String name, StructurePieceType type) {
        if (pieceTypesRegistered) {
            throw new IllegalStateException("海纳尔拼件类型 " + name + " 登记得太晚：RegisterEvent 已经发过了（要在模组构造期、init() 链上登记）");
        }
        ResourceLocation id = EssentialCraft5.id(name);
        if (PIECE_TYPES.putIfAbsent(id, type) != null) {
            throw new IllegalStateException("海纳尔拼件类型重名：" + id);
        }
        return RegistryObject.create(id, Registries.STRUCTURE_PIECE, EssentialCraft5.MODID);
    }

    /** 登记一个只靠 CompoundTag 读档的拼件类型（例：{@code contextlessPiece("town_road", Road::new)}，Road 有 {@code Road(CompoundTag)} 构造器） */
    public static RegistryObject<StructurePieceType> contextlessPiece(String name, StructurePieceType.ContextlessType type) {
        return pieceType(name, type);
    }

    /** 已登记的结构类型 id（登记顺序） */
    public static synchronized List<ResourceLocation> structureTypeIds() {
        return List.copyOf(STRUCTURE_TYPES.keySet());
    }

    /** 已登记的拼件类型 id（登记顺序） */
    public static synchronized List<ResourceLocation> pieceTypeIds() {
        return List.copyOf(PIECE_TYPES.keySet());
    }

    // ---------------------------------------------------------------- 注册

    @SubscribeEvent
    public static void registerHoannaStructures(RegisterEvent event) {
        event.register(Registries.STRUCTURE_TYPE, helper -> {
            synchronized (ECHoannaStructures.class) {
                structureTypesRegistered = true;
                STRUCTURE_TYPES.forEach(helper::register);
            }
        });
        event.register(Registries.STRUCTURE_PIECE, helper -> {
            synchronized (ECHoannaStructures.class) {
                pieceTypesRegistered = true;
                PIECE_TYPES.forEach(helper::register);
            }
        });
    }

    @SafeVarargs
    private static Set<String> union(Set<String>... sets) {
        Set<String> all = new java.util.LinkedHashSet<>();
        for (Set<String> set : sets) {
            all.addAll(set);
        }
        return Collections.unmodifiableSet(all);
    }
}
