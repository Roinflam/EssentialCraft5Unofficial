package com.flameforge.essentialcraft5.block;

import com.flameforge.essentialcraft5.blockentity.MithrilineCrystalPillarBlockEntity;
import com.flameforge.essentialcraft5.registry.ECMithriline;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * ESPE 水晶柱：三格高的 ESPE 源，对应原作 BlockMithrilineCrystal。原作一个方块带 5 种水晶（TYPE：米斯莱 / 淡青 / 虚空 / 恶魔 / 暗渎，
 * {@link Tier}），1.20 按类型拆注册名，每种一个方块，都用这个类，靠构造参数区分档位。
 * 这一个类名还叫 Mithriline 是因为第二批只有米斯莱一种（注册名 mithriline_crystal_pillar，存档已经在用），别的档位照样用它。
 * <p>
 * 三段的放置和拆除见 {@link TripleTallBlock}（修了原作敲柱顶删掉上方方块、一根变两根的 bug）。
 * 方块模型是空的（只有破坏粒子），浮在柱子里转的水晶由 BER 画（client/render/MithrilineCrystalPillarRenderer），同原作 TESR。
 * 碰撞箱照原作：三段都是 4~12 像素宽的整高柱子。
 *
 * <h2>档位（原作 new TileMithrilineCrystal(meta / 3)）</h2>
 * 档位 = 底座方块实体里 ESPE 存储的 tier（{@link com.flameforge.essentialcraft5.api.IESPEHandler#getTier()}，灵魂五芒星按它判「几档以上的柱子」），
 * 以及附魔加成（原作 getEnchantPowerBonus：7.5 / 15 / 30 / 60 / 120，只在底座）。产能、上限五种一样（原作 TileMithrilineCrystal 的
 * energyEachTick / maxEnergy 是全类共用的静态配置，不分档）：看得见天空每 tick 0.025，末地 0.1，上限 1 万，见方块实体的 Config。
 * 档位不存盘，每次按方块现取，所以米斯莱柱的存档和第二批完全一样。
 *
 * <h2>别的模块注册新的水晶柱（第三批 ECPentacle 的淡青水晶柱）</h2>
 * 方块实体类直接复用 {@link MithrilineCrystalPillarBlockEntity}，但每种柱子要注册<b>自己的</b>方块实体类型（原版的方块实体类型只认构造时给的方块，
 * 不在里面的方块不 tick），通过构造参数告诉方块用哪个类型：
 * <pre>{@code
 * public static final RegistryObject<MithrilineCrystalPillarBlock> PALE_CRYSTAL_PILLAR = ECBlocks.register("pale_crystal_pillar",
 *         () -> new MithrilineCrystalPillarBlock(BlockBehaviour.Properties.of()
 *                         .mapColor(MapColor.LAPIS)          // 原作 CrystalType.PALE 的地图颜色
 *                         .sound(SoundType.GLASS)
 *                         .strength(1F, ECBlocks.legacyResistance(1F, 1F, false))
 *                         .requiresCorrectToolForDrops()
 *                         .noOcclusion()
 *                         .pushReaction(PushReaction.BLOCK),  // 三段会被推散，必须推不动
 *                 MithrilineCrystalPillarBlock.Tier.PALE,
 *                 // 类型字段写在方块字段后面：lambda 里要写类名限定（ECPentacle.xxx），只写字段名是非法前向引用
 *                 () -> ECPentacle.PALE_CRYSTAL_PILLAR_BE.get()),
 *         BlockSpec.of(...).loot(...));   // 模型、战利品表照 ECMithriline 的米斯莱柱写（只在 part=bottom 掉自己）
 * public static final RegistryObject<BlockEntityType<MithrilineCrystalPillarBlockEntity>> PALE_CRYSTAL_PILLAR_BE =
 *         ECBlockEntities.type("pale_crystal_pillar", MithrilineCrystalPillarBlockEntity::new, PALE_CRYSTAL_PILLAR);
 * }</pre>
 * 方块实体的构造器按方块取类型和档位（{@link MithrilineCrystalPillarBlockEntity#MithrilineCrystalPillarBlockEntity(BlockPos, BlockState)}），
 * 配置小节（产能、上限）五种共用米斯莱那一份，不用再登记。新柱子记得加进方块标签 {@link #ESPE_PILLARS}（在模块自己的 tools/tags/*.json 里写）；
 * 浮动水晶的渲染器要在模块的客户端类里给自己的方块实体类型登记一次（贴图不同，见报告里的渲染器说明）。
 *
 * <h2>结构里要「任意档的水晶柱」</h2>
 * 用 {@link #pillarRule}：这一格是 {@link #ESPE_PILLARS} 标签里的方块的<b>底座</b>、档位 ≥ 要求（原作五芒星 testESPEHandler(w, pos, minTier)）。
 * 只用 {@code Rule.tag(ESPE_PILLARS, …)} 的话，柱子放低一格（中段落在这一格）也算数，但那一格没有方块实体、抽不到 ESPE。
 */
public class MithrilineCrystalPillarBlock extends TripleTallBlock {

    /**
     * 原作 BlockMithrilineCrystal.CrystalType 的五种水晶。{@link #index()} 就是原作的 TYPE 序号 / ESPE 档位，
     * {@link #enchantPower()} 是原作 getEnchantPowerBonus 的值（原版附魔台最多吃到 15，魔力附魔台按它自己的规则累加）。
     */
    public enum Tier {
        /** 米斯莱（第二批，ECMithriline） */
        MITHRILINE(7.5F),
        /** 淡青（第三批，ECPentacle） */
        PALE(15F),
        /** 虚空（虚空与恶魔批） */
        VOID(30F),
        /** 恶魔（虚空与恶魔批） */
        DEMONIC(60F),
        /** 暗渎（原作没有配方、只在创造栏；第十三批 13e 起魔力工作台有生存配方） */
        SHADE(120F);

        private static final Tier[] VALUES = values();

        private final float enchantPower;

        Tier(float enchantPower) {
            this.enchantPower = enchantPower;
        }

        /** 0~4：原作 TYPE 序号，也是 ESPE 存储的 tier */
        public int index() {
            return ordinal();
        }

        public float enchantPower() {
            return enchantPower;
        }

        /** 按序号取，越界返回 null */
        @Nullable
        public static Tier byIndex(int index) {
            return index >= 0 && index < VALUES.length ? VALUES[index] : null;
        }
    }

    /**
     * 所有 ESPE 水晶柱（方块标签 essentialcraft5:espe_pillars）。框架在 tools/tags/framework3c.json 建标签、放米斯莱柱，
     * 以后的柱子由注册它的模块在自己的 tags 文件里追加。风之符文、灵魂五芒星的结构用它（配合 {@link #pillarRule}）。
     */
    public static final TagKey<Block> ESPE_PILLARS = ECTags.modBlock("espe_pillars");

    /** 原作 BLOCK_AABB (0.25, 0, 0.25, 0.75, 1, 0.75) */
    private static final VoxelShape SHAPE = Block.box(4D, 0D, 4D, 12D, 16D, 12D);

    private final Tier tier;
    private final Supplier<BlockEntityType<MithrilineCrystalPillarBlockEntity>> blockEntityType;

    /**
     * @param tier            档位（决定 ESPE 存储的 tier 和附魔加成）
     * @param blockEntityType 这种柱子自己的方块实体类型（注册时把这个方块传进去）；用的时候才取，所以可以指向后面才注册的类型
     */
    public MithrilineCrystalPillarBlock(Properties properties, Tier tier,
                                        Supplier<BlockEntityType<MithrilineCrystalPillarBlockEntity>> blockEntityType) {
        super(properties);
        this.tier = Objects.requireNonNull(tier);
        this.blockEntityType = Objects.requireNonNull(blockEntityType);
    }

    public Tier tier() {
        return tier;
    }

    public BlockEntityType<MithrilineCrystalPillarBlockEntity> blockEntityType() {
        return blockEntityType.get();
    }

    /** 这个方块状态是不是水晶柱（任何一段），是的话返回档位序号 0~4，不是返回 −1 */
    public static int tierOf(BlockState state) {
        return state.getBlock() instanceof MithrilineCrystalPillarBlock pillar ? pillar.tier.index() : -1;
    }

    /** 这一格是 {@link #ESPE_PILLARS} 里的水晶柱的底座（有方块实体、存 ESPE 的那一段），而且档位 ≥ minTier */
    public static boolean isPillarBase(BlockState state, int minTier) {
        return state.is(ESPE_PILLARS) && state.hasProperty(PART) && isBottom(state) && tierOf(state) >= minTier;
    }

    /**
     * 结构规则：这一格要放档位 ≥ minTier 的水晶柱（底座）。手册里画 display（比如米斯莱柱的默认状态）。
     * 例：风之符文的 8 根任意档柱子 {@code pillarRule(0, () -> ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get().defaultBlockState(), "structure.essentialcraft5.wind_rune.pillar")}。
     */
    public static StructurePattern.Rule pillarRule(int minTier, Supplier<BlockState> display, String legendKey) {
        return StructurePattern.Rule.predicate((level, pos, state) -> isPillarBase(state, minTier), display, legendKey);
    }

    /** {@link #pillarRule(int, Supplier, String)} 的任意档版本，手册里画米斯莱柱 */
    public static StructurePattern.Rule pillarRule(String legendKey) {
        return pillarRule(0, () -> ECMithriline.MITHRILINE_CRYSTAL_PILLAR.get().defaultBlockState(), legendKey);
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** 只有底座有方块实体（原作 createNewTileEntity 里 meta%3 == 0） */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isBottom(state) ? new MithrilineCrystalPillarBlockEntity(pos, state) : null;
    }

    /** 逻辑只在服务端；客户端的浮动动画由 BER 按世界时间算，不用 tick */
    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, blockEntityType(), MithrilineCrystalPillarBlockEntity::serverTick);
    }

    /** 原作 getEnchantPowerBonus：只在底座，按档位 7.5 / 15 / 30 / 60 / 120 */
    @Override
    public float getEnchantPowerBonus(BlockState state, LevelReader level, BlockPos pos) {
        return isBottom(state) ? tier.enchantPower() : 0F;
    }
}
