package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.MRUCoilBlock;
import com.flameforge.essentialcraft5.block.MRUCoilHardenerBlock;
import com.flameforge.essentialcraft5.blockentity.MRUCoilBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.PlayerListItem;
import com.flameforge.essentialcraft5.menu.MRUCoilMenu;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructurePatterns;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 特斯拉线圈模块（第四批 4b，batch4 §3.11）：MRU 特斯拉线圈、线圈基座、MRU 磁体、玩家白名单。
 * <p>
 * 新旧注册名：mruCoil → {@code mru_coil}、mruCoilHardener → {@code mru_coil_hardener}、genItem meta 43（mruMagnet，矿辞 ingotMagnet）→ {@code mru_magnet}、
 * playerList → {@code player_list}。原作 BlocksCore L120~121：两个方块都是 {@code registerBlockSimple(…, 1, 100, 0)}（先抗爆后硬度、不透光 0），
 * Material.ROCK、MapColor.PURPLE、不是完整方块。
 * <p>
 * <b>线圈是本模组唯一会打玩家的东西</b>（2026-09-25 定、2026-09-28 D2 取 C 档 + 七条附加克制）：打敌对生物和白名单以外的玩家，
 * 主人永远不打，PvP 关了也打（伤害不带攻击者），不看领地；数值和规则见 {@link MRUCoilBlockEntity} 的类注释。
 * <p>
 * 结构：线圈正下方叠 0~64 层基座（每层射程 +2），最下面踩一个 7×7 十字圆形的 25 格底座（标签 {@link #MRU_COIL_BASE}：魔力 / 淡青 / 米斯莱护板；
 * 以后的虚空石、恶魔护板由它们的批次在自己的标签文件里追加）。判定用 {@link #COIL_FOUNDATION}（只有底座，原点在底座正中），
 * 基座层数另数（原作 TileMRUCoil.initStructure L64~134）；手册画 {@link #COIL_STRUCTURE}（底座 + 两层基座 + 线圈，只给手册和进度用，不参与判定）。
 * <p>
 * 工作台配方在 data/essentialcraft5/recipes/tesla/（磁体的辐射仓配方第一批就预写好了：radiating_chamber/mru_magnet.json），
 * 文字在 tools/lang/keys/ectesla.json，方块标签在 tools/tags/ectesla.json，实体类型标签 {@link #MRU_COIL_IMMUNE} 是静态 JSON
 * data/essentialcraft5/tags/entity_types/mru_coil_immune.json（默认空，服主用数据包往里加）。
 * 手册「特斯拉：线圈与反应堆」章 assets/essentialcraft5/guide/tesla.json（本模块拥有，ECReactor 交部件进来）。客户端部分见 client/TeslaClient。
 */
public final class ECTesla {

    // ---------------- 标签 ----------------

    /** 线圈底座允许的方块（原作 StructureRegistry L52~63 的 MRU_COIL 一组） */
    public static final TagKey<Block> MRU_COIL_BASE = ECTags.modBlock("mru_coil_base");

    /** 线圈不打的实体类型（默认空；服主可以用数据包加，例如某些打死要给玩家算击杀的 Boss 型怪物） */
    public static final TagKey<EntityType<?>> MRU_COIL_IMMUNE =
            TagKey.create(Registries.ENTITY_TYPE, EssentialCraft5.id("mru_coil_immune"));

    // ---------------- 方块 ----------------

    /** 线圈：模型是原作 mrucoil.json（导入为 models/block/mru_coil.json），碰撞形状照模型 */
    public static final RegistryObject<MRUCoilBlock> MRU_COIL = ECBlocks.register("mru_coil",
            () -> new MRUCoilBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 100F, true)).noOcclusion()),
            BlockSpec.of(BlockSpec.existing("mru_coil")));

    public static final RegistryObject<BlockEntityType<MRUCoilBlockEntity>> MRU_COIL_BE =
            ECBlockEntities.type("mru_coil", MRUCoilBlockEntity::new, MRU_COIL);

    public static final RegistryObject<MenuType<MRUCoilMenu>> MRU_COIL_MENU = ECMenus.register("mru_coil", MRUCoilMenu::new);

    /** 基座：原作 OBJ 模型 mrucoilhardener；普通方块，没有方块实体（原作的方块实体只为客户端冒火花） */
    public static final RegistryObject<MRUCoilHardenerBlock> MRU_COIL_HARDENER = ECBlocks.register("mru_coil_hardener",
            () -> new MRUCoilHardenerBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 100F, true)).noOcclusion()),
            BlockSpec.of(BlockSpec.obj("mrucoilhardener", "block/model/mrucoilhardener")));

    // ---------------- 物品 ----------------

    /** MRU 磁体：纯中间材料（线圈、基座、反应堆、支架的配方用），辐射仓：铁锭 + 魔力精华，1 000 MRU */
    public static final RegistryObject<Item> MRU_MAGNET = ECItems.register("mru_magnet",
            () -> new Item(new Item.Properties()), ItemSpec.generated("item/misc/mrumagnet"));

    /** 玩家白名单：原作借用原版纸的贴图（ItemPlayerList L72），这里也一样 */
    public static final RegistryObject<PlayerListItem> PLAYER_LIST = ECItems.register("player_list",
            () -> new PlayerListItem(new Item.Properties().stacksTo(1)), ItemSpec.generated("minecraft:item/paper"));

    // ---------------- 结构 ----------------
    // 字段必须写在它引用的 RegistryObject 之后（porting_guide §7 补充）；规则里的方块都是 Supplier，用到时才取

    /** 结构 id：线圈成形进度（structure_formed）和手册的结构页都用它 */
    public static final ResourceLocation COIL_STRUCTURE_ID = EssentialCraft5.id("mru_coil");

    /** 底座的一格：标签里的任意方块（手册里按成员轮换显示，代表方块魔力护板） */
    public static final StructurePattern.Rule BASE_RULE = StructurePattern.Rule.tag(MRU_COIL_BASE,
            () -> ECAlloys.MAGIC_PLATING.get().defaultBlockState(), "structure.essentialcraft5.mru_coil.base");

    /** 手册里画的基座（可选、可以叠很多层） */
    public static final StructurePattern.Rule HARDENER_RULE =
            StructurePattern.Rule.block(MRU_COIL_HARDENER, "structure.essentialcraft5.mru_coil.hardener").optional();

    /**
     * 线圈判定用的底座：原作 initStructure L76~102 的 25 格，原点 C 在底座正中（就是最下面那层基座正下方、没有基座时线圈正下方那一格），
     * 中心格本身也要是底座方块。四面对称，判定时朝向传 NORTH。
     */
    public static final StructurePattern COIL_FOUNDATION = StructurePatterns.register(EssentialCraft5.id("mru_coil_foundation"),
            StructurePattern.builder()
                    .where('P', BASE_RULE)
                    .where('C', BASE_RULE)
                    .anchor('C')
                    .layer("  PPP  ",
                            "   P   ",
                            "P PPP P",
                            "PPPCPPP",
                            "P PPP P",
                            "   P   ",
                            "  PPP  ")
                    .build());

    /**
     * 手册与进度用的整座塔：底座（高度 -3）+ 两层基座（可选，画淡）+ 线圈（原点）。<b>不参与判定</b>：基座层数可变，
     * 线圈自己数基座、再用 {@link #COIL_FOUNDATION} 判底座。
     */
    public static final StructurePattern COIL_STRUCTURE = StructurePatterns.register(EssentialCraft5.id("mru_coil"),
            StructurePattern.builder()
                    .where('P', BASE_RULE)
                    .where('H', HARDENER_RULE)
                    .where('M', StructurePattern.Rule.block(MRU_COIL))
                    .anchor('M')
                    .layer("  PPP  ", "   P   ", "P PPP P", "PPPPPPP", "P PPP P", "   P   ", "  PPP  ")
                    .layer("       ", "       ", "       ", "   H   ", "       ", "       ", "       ")
                    .layer("       ", "       ", "       ", "   H   ", "       ", "       ", "       ")
                    .layer("       ", "       ", "       ", "   M   ", "       ", "       ", "       ")
                    .build());

    private ECTesla() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 */
    public static void init() {
        ECConfig.machine(MRUCoilBlockEntity.Config::define);
    }
}
