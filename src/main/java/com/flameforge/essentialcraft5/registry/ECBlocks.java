package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.ChargingChamberBlock;
import com.flameforge.essentialcraft5.block.CreativeMRUStorageBlock;
import com.flameforge.essentialcraft5.block.MoonWellBlock;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 方块注册入口。各模块（EC* 注册类）用 {@link #register} 登记方块和它的 {@link BlockSpec}，
 * 同名 BlockItem 自动注册，datagen、创造模式物品栏都按登记顺序遍历。
 * <p>
 * 抗爆换算用 {@link #legacyResistance}：1.12 的 setResistance(r) 存 3r，setHardness(h) 会把它抬到至少 5h，
 * 爆炸时再除以 5，所以结果取决于两者的调用顺序。原作 registerBlockSimple(方块, 名, 硬度, 抗爆, 不透光度) 是先抗爆后硬度。
 * 原作 Material.ROCK 的方块要镐才掉落：{@code requiresCorrectToolForDrops()} + mineable/pickaxe 标签（BlockSpec 默认就有）。
 * 原作 setLightOpacity(0) 的机器，方块类构造时传 transmitsSkylight=true（见 MachineBlock）。
 * <p>
 * 这里放的是框架验证时移植的几样：moonWell → moon_well；chargingChamber → charging_chamber；
 * device:4 → creative_mru_storage。新旧注册名总表见 tools/id_map_blocks.json。
 */
public final class ECBlocks {

    public record Entry(RegistryObject<? extends Block> block, BlockSpec spec) {
    }

    private static final List<Entry> ALL = new ArrayList<>();

    /** 原作 moonWell：setHardness(1).setResistance(1).setLightOpacity(0)，MapColor.PURPLE → 抗爆 0.6 */
    public static final RegistryObject<MoonWellBlock> MOON_WELL = register("moon_well",
            () -> new MoonWellBlock(machine(MapColor.COLOR_PURPLE, 1F, legacyResistance(1F, 1F, false)).noOcclusion()),
            BlockSpec.of(BlockSpec.existing("moon_well")));

    /** 原作 chargingChamber：registerBlockSimple(…, 1, 1, 0)（先抗爆后硬度，不透光度 0），OBJ 模型 → 抗爆 1.0 */
    public static final RegistryObject<ChargingChamberBlock> CHARGING_CHAMBER = register("charging_chamber",
            () -> new ChargingChamberBlock(machine(MapColor.STONE, 1F, legacyResistance(1F, 1F, true)).noOcclusion()),
            BlockSpec.of(BlockSpec.obj("chargingchamber", "block/model/chargingchamber")));

    /** 原作 device 方块的 meta 4（creative_mru_storage）：setHardness(1).setResistance(1) → 抗爆 0.6 */
    public static final RegistryObject<CreativeMRUStorageBlock> CREATIVE_MRU_STORAGE = register("creative_mru_storage",
            () -> new CreativeMRUStorageBlock(machine(MapColor.STONE, 1F, legacyResistance(1F, 1F, false))),
            BlockSpec.of(BlockSpec.cubeAll("block/creativemrustorage")));

    private ECBlocks() {
    }

    static void init() {
    }

    /** 登记顺序即创造模式物品栏里的顺序 */
    public static List<Entry> entries() {
        return Collections.unmodifiableList(ALL);
    }

    public static List<RegistryObject<? extends Block>> all() {
        return ALL.stream().<RegistryObject<? extends Block>>map(Entry::block).toList();
    }

    /** 注册方块（和它的物品，除非 spec.item(null)） */
    public static <B extends Block> RegistryObject<B> register(String name, Supplier<B> block, BlockSpec spec) {
        RegistryObject<B> object = ECRegistries.BLOCKS.register(name, block);
        if (spec.itemFactory != null) {
            ECRegistries.ITEMS.register(name, () -> {
                Item item = spec.itemFactory.apply(object.get());
                return item;
            });
        }
        ALL.add(new Entry(object, spec));
        return object;
    }

    /**
     * 原作 Material.ROCK 机器的通用属性。
     *
     * @param resistance 1.20.1 的抗爆值（用 {@link #legacyResistance} 从原作数值换算）
     */
    public static BlockBehaviour.Properties machine(MapColor color, float hardness, float resistance) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .sound(SoundType.STONE)
                .strength(hardness, resistance)
                .requiresCorrectToolForDrops();
    }

    /**
     * 按 1.12 的规则把原作的硬度、抗爆换算成 1.20.1 的抗爆值：
     * setResistance(r) 令内部值 = 3r，setHardness(h) 令内部值 = max(内部值, 5h)，最终抗爆 = 内部值 / 5。
     *
     * @param resistanceFirst 原作是否先调 setResistance 再调 setHardness（registerBlockSimple 是先抗爆）
     */
    public static float legacyResistance(float hardness, float resistance, boolean resistanceFirst) {
        float value = resistanceFirst ? Math.max(3F * resistance, 5F * hardness) : 3F * resistance;
        return value / 5F;
    }
}
