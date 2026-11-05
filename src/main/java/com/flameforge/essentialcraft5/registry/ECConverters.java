package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.block.MRUConverterBlock;
import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity.ConverterTier;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.MRUConverterMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * MRU 转换器（EC5 新机器，原作没有；规划 tools/plan/mru_converter.md，policy §4.14）：把 MRU 换成 Forge 能量（FE）或植物魔法的魔力，
 * 推给贴着它六个面、收得下的方块。<b>本模组唯一的联动</b>：2026-10-13 起「不做任何联动」只对这一组机器例外。
 * <p>
 * 四档生存可做，按材料命名（D3 定为 A）：{@code mru_converter} 低档、{@code pale_mru_converter} 中档、{@code mithriline_mru_converter} 高档、
 * {@code demonic_mru_converter} 终极档；第五台 {@code infinite_mru_converter} 是无尽道具，注册在 {@link ECCreative}（只放创造栏）。
 * 五台共用一个方块类 {@link MRUConverterBlock}、一个方块实体类 {@link MRUConverterBlockEntity}（档位是 {@link ConverterTier}）、一个菜单类型；
 * 四档生存的方块实体类型是这里的 {@code mru_converter}，无尽档另有自己的类型（登记在 ECCreative）。
 * <p>
 * 方块属性照地脉凝聚器（硬度 1、抗爆按 1.12 规则换算、镐挖掉自己）；模型由 datagen 按 {@code models/block/mru_converter_base.json}
 * 生成（每档 4 个：能量 / 魔力 × 待机 / 工作，只换侧面中间的输出口叠层），贴图在 {@code textures/block/mru_converter/}。
 * 文字在 {@code tools/lang/keys/ecconverters.json}，配方 {@code data/essentialcraft5/recipes/converters/}（逐档要上一档，只用 EC5 和原版材料），
 * 手册支线 {@code guide/<语言>/converter.json}；客户端部分见 {@code client/ConvertersClient}。
 * 植物魔法是可选的：魔力只经 {@code compat/botania/ManaBridge} 推，没装时魔力按钮灰掉，任何类都不会去加载植物魔法的类型。
 */
public final class ECConverters {

    public static final RegistryObject<MRUConverterBlock> MRU_CONVERTER = ECBlocks.register("mru_converter",
            () -> new MRUConverterBlock(ConverterTier.BASIC, survival(MapColor.COLOR_PURPLE)),
            BlockSpec.of(MRUConverterBlock.models("basic")));

    public static final RegistryObject<MRUConverterBlock> PALE_MRU_CONVERTER = ECBlocks.register("pale_mru_converter",
            () -> new MRUConverterBlock(ConverterTier.PALE, survival(MapColor.COLOR_BLUE)),
            BlockSpec.of(MRUConverterBlock.models("pale")));

    public static final RegistryObject<MRUConverterBlock> MITHRILINE_MRU_CONVERTER = ECBlocks.register("mithriline_mru_converter",
            () -> new MRUConverterBlock(ConverterTier.MITHRILINE, survival(MapColor.COLOR_GREEN)),
            BlockSpec.of(MRUConverterBlock.models("mithriline")));

    public static final RegistryObject<MRUConverterBlock> DEMONIC_MRU_CONVERTER = ECBlocks.register("demonic_mru_converter",
            () -> new MRUConverterBlock(ConverterTier.DEMONIC, survival(MapColor.COLOR_RED)),
            BlockSpec.of(MRUConverterBlock.models("demonic")));

    /** 四档生存共用的方块实体类型（无尽档的在 {@link ECCreative#INFINITE_MRU_CONVERTER_BE}） */
    public static final RegistryObject<BlockEntityType<MRUConverterBlockEntity>> MRU_CONVERTER_BE =
            ECBlockEntities.type("mru_converter", MRUConverterBlockEntity::new,
                    MRU_CONVERTER, PALE_MRU_CONVERTER, MITHRILINE_MRU_CONVERTER, DEMONIC_MRU_CONVERTER);

    /** 五台共用的菜单（客户端按额外数据里的档位定槽数：无尽档没有宝石槽） */
    public static final RegistryObject<MenuType<MRUConverterMenu>> MRU_CONVERTER_MENU =
            ECMenus.register("mru_converter", MRUConverterMenu::new);

    private ECConverters() {
    }

    /** 四档生存转换器的方块属性：硬度 1，抗爆照地脉凝聚器按 1.12 规则换算（先抗爆后硬度 1 / 1 → 1.0），要镐才掉落 */
    private static BlockBehaviour.Properties survival(MapColor color) {
        return ECBlocks.machine(color, 1F, ECBlocks.legacyResistance(1F, 1F, true));
    }

    /**
     * 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister，这里再登记配置小节 machines.mru_converter
     * （无尽档的 {@code infinite.max_mru_per_tick} 也在这一小节里，ECCreative 不用另外登记）。
     */
    public static void init() {
        ECConfig.machine(MRUConverterBlockEntity.Config::define);
    }
}
