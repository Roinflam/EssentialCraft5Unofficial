package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.DimensionalTransceiverBlock;
import com.flameforge.essentialcraft5.block.MagicalFurnaceBlock;
import com.flameforge.essentialcraft5.block.MagicalTeleporterBlock;
import com.flameforge.essentialcraft5.blockentity.DimensionalTransceiverBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalFurnaceBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalTeleporterBlockEntity;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.DimensionalTransceiverMenu;
import com.flameforge.essentialcraft5.menu.MagicalFurnaceMenu;
import com.flameforge.essentialcraft5.menu.MagicalTeleporterMenu;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.structure.StructurePatterns;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * 第六批 6b M2「传送与熔炼」（计划 tools/plan/batch6b.md §4.2）的注册类：魔力传送器 {@code magical_teleporter}、
 * 魔力熔炉 {@code magical_furnace}（两台都带多方块结构）、MRU 跨维度收发器 {@code dimensional_transceiver}（只放创造栏，不给配方）。
 * <p>
 * 新旧注册名：magicalTeleporter → {@code magical_teleporter}（原作 BlockMagicalTeleporter + TileMagicalTeleporter）；
 * magicalFurnace → {@code magical_furnace}（BlockMagicalFurnace + TileMagicalFurnace）；
 * dimTransciever → {@code dimensional_transceiver}（BlockDimensionalTransciever + TileMRUDimensionalTransciever，原作把 transceiver 拼成了 transciever）。
 * 三个方块原作都是 {@code registerBlockSimple(…, 1, 1, 0)}（{@code BlocksCore} L101~102、L189：先抗爆后硬度 → 1.20.1 抗爆 1.0；不透光度 0 → 天空光穿过），
 * Material.ROCK（要镐），传送器地图色紫、另外两台石头色；挖掉撒出物品栏，潜行右键不开界面（{@code MachineBlock} 照做）。
 * <p>
 * 玩家视角、规则和与原作的不同见三个方块实体的类注释；服务端配置 {@code machines.magical_teleporter / magical_furnace / dimensional_transceiver}。
 * 结构 {@link #TELEPORTER_STRUCTURE}、{@link #FURNACE_STRUCTURE}（手册结构页按 id 画，成形时触发进度条件 {@code essentialcraft5:structure_formed}）。
 * 工作台配方在 data/essentialcraft5/recipes/void_machines/，物品标签 {@link #NO_BONUS}（tools/tags/ecvoidmachines.json），
 * 文字在 tools/lang/keys/ecvoidmachines.json，手册片段 tools/guide/fragments/void_machines/m2.*。客户端部分见 client/VoidMachinesClient。
 */
public final class ECVoidMachines {

    /** 结构 id（手册结构页、进度条件 structure_formed 用）；登记处写的是同一个字面量（手册离线检查按字面量找登记） */
    public static final ResourceLocation TELEPORTER_STRUCTURE_ID = EssentialCraft5.id("magical_teleporter");
    public static final ResourceLocation FURNACE_STRUCTURE_ID = EssentialCraft5.id("magical_furnace");

    /**
     * 魔力熔炉不多出产物的输入（batch6b §9 D2）：湿海绵 ↔ 海绵、「锭 → 粉 → 锭」这类一圈下来能凭空多 30% 的循环。
     * 默认 {@code minecraft:wet_sponge}、{@code #forge:dusts}；整合包里别的循环往这个标签里加。炉渣几率不受影响。
     */
    public static final TagKey<Item> NO_BONUS = ECTags.modItem("magical_furnace_no_bonus");

    // ---------------- 魔力传送器 ----------------

    public static final RegistryObject<MagicalTeleporterBlock> MAGICAL_TELEPORTER = ECBlocks.register("magical_teleporter",
            () -> new MagicalTeleporterBlock(ECBlocks.machine(MapColor.COLOR_PURPLE, 1F, ECBlocks.legacyResistance(1F, 1F, true)).noOcclusion()),
            BlockSpec.of((provider, block) -> provider.simpleBlockWithItem(block,
                    provider.models().cubeTop(provider.name(block), provider.tex("block/magicplatingblock"), provider.tex("block/magic_teleporter_top")))));

    public static final RegistryObject<BlockEntityType<MagicalTeleporterBlockEntity>> MAGICAL_TELEPORTER_BE =
            ECBlockEntities.type("magical_teleporter", MagicalTeleporterBlockEntity::new, MAGICAL_TELEPORTER);

    public static final RegistryObject<MenuType<MagicalTeleporterMenu>> MAGICAL_TELEPORTER_MENU =
            ECMenus.register("magical_teleporter", MagicalTeleporterMenu::new);

    // ---------------- 魔力熔炉 ----------------

    public static final RegistryObject<MagicalFurnaceBlock> MAGICAL_FURNACE = ECBlocks.register("magical_furnace",
            () -> new MagicalFurnaceBlock(ECBlocks.machine(MapColor.STONE, 1F, ECBlocks.legacyResistance(1F, 1F, true)).noOcclusion()),
            BlockSpec.of((provider, block) -> provider.simpleBlockWithItem(block,
                    provider.models().cubeTop(provider.name(block), provider.tex("block/fortifiedstone"), provider.tex("block/magic_furnace_top")))));

    public static final RegistryObject<BlockEntityType<MagicalFurnaceBlockEntity>> MAGICAL_FURNACE_BE =
            ECBlockEntities.type("magical_furnace", MagicalFurnaceBlockEntity::new, MAGICAL_FURNACE);

    public static final RegistryObject<MenuType<MagicalFurnaceMenu>> MAGICAL_FURNACE_MENU =
            ECMenus.register("magical_furnace", MagicalFurnaceMenu::new);

    // ---------------- MRU 跨维度收发器 ----------------

    /** 侧面 dimtranciever_side 是动画贴图（mcmeta 已随贴图导入） */
    public static final RegistryObject<DimensionalTransceiverBlock> DIMENSIONAL_TRANSCEIVER = ECBlocks.register("dimensional_transceiver",
            () -> new DimensionalTransceiverBlock(ECBlocks.machine(MapColor.STONE, 1F, ECBlocks.legacyResistance(1F, 1F, true)).noOcclusion()),
            BlockSpec.of((provider, block) -> provider.simpleBlockWithItem(block,
                    provider.models().cubeBottomTop(provider.name(block), provider.tex("block/dimtranciever_side"),
                            provider.tex("block/dimtranciever_bot"), provider.tex("block/dimtranciever_top")))));

    public static final RegistryObject<BlockEntityType<DimensionalTransceiverBlockEntity>> DIMENSIONAL_TRANSCEIVER_BE =
            ECBlockEntities.type("dimensional_transceiver", DimensionalTransceiverBlockEntity::new, DIMENSIONAL_TRANSCEIVER);

    public static final RegistryObject<MenuType<DimensionalTransceiverMenu>> DIMENSIONAL_TRANSCEIVER_MENU =
            ECMenus.register("dimensional_transceiver", DimensionalTransceiverMenu::new);

    // ---------------- 结构（字段写在它引用的 RegistryObject 之后，别的模块的方块用 lambda 取） ----------------

    /** 虚空石（任意颜色：染色是同一个方块的状态） */
    public static final StructurePattern.Rule VOID_STONE = StructurePattern.Rule.block(() -> ECVoid.VOID_STONE.get(),
            "structure.essentialcraft5.void_machines.void_stone");
    /** 魔力护板（任意颜色；淡青、米斯莱护板不算，同原作） */
    public static final StructurePattern.Rule MAGIC_PLATING = StructurePattern.Rule.block(() -> ECAlloys.MAGIC_PLATING.get(),
            "structure.essentialcraft5.void_machines.magic_plating");
    /** 普通热力产能器（终极热力产能器不算，同原作 {@code TileMagicalFurnace} L80~83 的 {@code == BlocksCore.heatGenerator}） */
    public static final StructurePattern.Rule HEAT_GENERATOR = StructurePattern.Rule.block(() -> ECGenerators.HEAT_GENERATOR.get());

    /**
     * 魔力传送器（原作 {@code TileMagicalTeleporter.structureChecker} L74~108）：机器所在层 5×5，正四向距离 2 的 4 格是魔力护板、其余 20 格虚空石；
     * 往上第 1、2 层在 (±1, ±2)、(±2, ±1) 各一块虚空石（每层 8 块，四角和正四向空着，机器正上方两格也空着让人站）。合计虚空石 36、护板 4。
     */
    public static final StructurePattern TELEPORTER_STRUCTURE = StructurePatterns.register(EssentialCraft5.id("magical_teleporter"),
            StructurePattern.builder()
                    .where('V', VOID_STONE)
                    .where('P', MAGIC_PLATING)
                    .where('T', StructurePattern.Rule.block(MAGICAL_TELEPORTER))
                    .anchor('T')
                    .layer("VVPVV", "VVVVV", "PVTVP", "VVVVV", "VVPVV")   // y = 0：机器层
                    .layer(" V V ", "V   V", "     ", "V   V", " V V ")   // y = +1
                    .layer(" V V ", "V   V", "     ", "V   V", " V V ")   // y = +2
                    .build());

    /**
     * 魔力熔炉（原作 {@code TileMagicalFurnace.structureChecker} L65~84）：机器下一层 5×5 虚空石（25），同层四角虚空石 ×4，
     * 上一层四角普通热力产能器 ×4（产物、炉渣就落在这四台的顶上）。
     */
    public static final StructurePattern FURNACE_STRUCTURE = StructurePatterns.register(EssentialCraft5.id("magical_furnace"),
            StructurePattern.builder()
                    .where('V', VOID_STONE)
                    .where('H', HEAT_GENERATOR)
                    .where('F', StructurePattern.Rule.block(MAGICAL_FURNACE))
                    .anchor('F')
                    .layer("VVVVV", "VVVVV", "VVVVV", "VVVVV", "VVVVV")   // y = −1
                    .layer("V   V", "     ", "  F  ", "     ", "V   V")   // y = 0：机器层，四角虚空石
                    .layer("H   H", "     ", "     ", "     ", "H   H")   // y = +1：四角热力产能器
                    .build());

    private ECVoidMachines() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，另登记三台机器的配置 */
    public static void init() {
        ECConfig.machine(MagicalTeleporterBlockEntity.Config::define);
        ECConfig.machine(MagicalFurnaceBlockEntity.Config::define);
        ECConfig.machine(DimensionalTransceiverBlockEntity.Config::define);
    }
}
