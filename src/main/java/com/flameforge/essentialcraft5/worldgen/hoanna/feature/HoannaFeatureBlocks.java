package com.flameforge.essentialcraft5.worldgen.hoanna.feature;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * 海纳尔地物用到的第十三批方块（第十三批 13d-H1b，batch13.md §6.1）。和 {@link com.flameforge.essentialcraft5.worldgen.hoanna.HoannaStructureBlocks}
 * 同一个写法：<b>按注册名取</b>（不 import 注册它们的 ECHoannaResources / ECHoannaMaterials / ECHoannaSurvey），没注册时退回一种已有方块，
 * 地物照样能放、测试用 {@link #isRegistered} 跳过相关断言。状态属性按名字套（{@code charge}、{@code stage}、{@code facing}、{@code waterlogged}、{@code ripe}），
 * 退回的方块没有这个属性就不套。
 * <p>
 * 退回规则：承重 / 占一整格的（地脉喷口、榍石矿、霜髓、渊盐结壳、里程碑）退回外观相近的原版方块；挂在别的方块上的小东西（两种晶簇、潮晶芽）退回空气。
 * 巢穴核心不在这里：一律走 {@code HoannaNests.placeCore}（要写方块实体里的类型）。
 */
public final class HoannaFeatureBlocks {

    public static final ResourceLocation LEY_VENT_ID = EssentialCraft5.id("ley_vent");
    public static final ResourceLocation TITANITE_ORE_ID = EssentialCraft5.id("titanite_ore");
    public static final ResourceLocation RADIANT_CLUSTER_ID = EssentialCraft5.id("radiant_cluster");
    public static final ResourceLocation TIDE_BUD_ID = EssentialCraft5.id("tide_bud");
    public static final ResourceLocation UMBRAL_CLUSTER_ID = EssentialCraft5.id("umbral_cluster");
    public static final ResourceLocation FROST_MARROW_ID = EssentialCraft5.id("frost_marrow");
    public static final ResourceLocation ABYSSAL_SALT_CRUST_ID = EssentialCraft5.id("abyssal_salt_crust");
    public static final ResourceLocation RUIN_MILESTONE_ID = EssentialCraft5.id("ruin_milestone");

    /** 本类按名字取的全部方块（测试、文档用） */
    public static final List<ResourceLocation> ALL_IDS = List.of(LEY_VENT_ID, TITANITE_ORE_ID, RADIANT_CLUSTER_ID, TIDE_BUD_ID,
            UMBRAL_CLUSTER_ID, FROST_MARROW_ID, ABYSSAL_SALT_CRUST_ID, RUIN_MILESTONE_ID);

    private static final RegistryObject<Block> LEY_VENT = RegistryObject.create(LEY_VENT_ID, ForgeRegistries.BLOCKS);
    private static final RegistryObject<Block> TITANITE_ORE = RegistryObject.create(TITANITE_ORE_ID, ForgeRegistries.BLOCKS);
    private static final RegistryObject<Block> RADIANT_CLUSTER = RegistryObject.create(RADIANT_CLUSTER_ID, ForgeRegistries.BLOCKS);
    private static final RegistryObject<Block> TIDE_BUD = RegistryObject.create(TIDE_BUD_ID, ForgeRegistries.BLOCKS);
    private static final RegistryObject<Block> UMBRAL_CLUSTER = RegistryObject.create(UMBRAL_CLUSTER_ID, ForgeRegistries.BLOCKS);
    private static final RegistryObject<Block> FROST_MARROW = RegistryObject.create(FROST_MARROW_ID, ForgeRegistries.BLOCKS);
    private static final RegistryObject<Block> ABYSSAL_SALT_CRUST = RegistryObject.create(ABYSSAL_SALT_CRUST_ID, ForgeRegistries.BLOCKS);
    private static final RegistryObject<Block> RUIN_MILESTONE = RegistryObject.create(RUIN_MILESTONE_ID, ForgeRegistries.BLOCKS);

    private HoannaFeatureBlocks() {
    }

    /** 地脉喷口（charge 0~3）；没注册时是岩浆块 */
    public static BlockState leyVent(int charge) {
        return withInt(state(LEY_VENT, Blocks.MAGMA_BLOCK), "charge", charge);
    }

    /** 榍石矿；没注册时是平滑砂岩 */
    public static BlockState titaniteOre() {
        return state(TITANITE_ORE, Blocks.SMOOTH_SANDSTONE);
    }

    /** 辐晶簇（朝 facing 长出去）；没注册时是空气 */
    public static BlockState radiantCluster(Direction facing) {
        return with(state(RADIANT_CLUSTER, Blocks.AIR), BlockStateProperties.FACING, facing);
    }

    /** 影晶簇（朝 facing、成熟与否）；没注册时是空气 */
    public static BlockState umbralCluster(Direction facing, boolean ripe) {
        BlockState state = with(state(UMBRAL_CLUSTER, Blocks.AIR), BlockStateProperties.FACING, facing);
        return withBool(state, "ripe", ripe);
    }

    /** 潮晶芽（stage 0~3）；没注册时是空气 */
    public static BlockState tideBud(int stage) {
        return withInt(state(TIDE_BUD, Blocks.AIR), "stage", stage);
    }

    /** 霜髓；没注册时是蓝冰 */
    public static BlockState frostMarrow() {
        return state(FROST_MARROW, Blocks.BLUE_ICE);
    }

    /** 渊盐结壳；没注册时是沙砾 */
    public static BlockState abyssalSaltCrust() {
        return state(ABYSSAL_SALT_CRUST, Blocks.GRAVEL);
    }

    /** 里程碑（柱顶那一格）；没注册时是錾制石砖 */
    public static BlockState ruinMilestone() {
        return state(RUIN_MILESTONE, Blocks.CHISELED_STONE_BRICKS);
    }

    /** 这个注册名的方块已经注册了 */
    public static boolean isRegistered(ResourceLocation id) {
        return ForgeRegistries.BLOCKS.containsKey(id);
    }

    /** 方块状态有这个名字的整数属性就设上（值超出范围就不设） */
    public static BlockState withInt(BlockState state, String name, int value) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        if (property instanceof IntegerProperty integer && integer.getPossibleValues().contains(value)) {
            return state.setValue(integer, value);
        }
        return state;
    }

    /** 方块状态有这个名字的布尔属性就设上 */
    public static BlockState withBool(BlockState state, String name, boolean value) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        if (property instanceof BooleanProperty bool) {
            return state.setValue(bool, value);
        }
        return state;
    }

    /** 方块状态有这个属性就设上 */
    public static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, T value) {
        return state.hasProperty(property) && property.getPossibleValues().contains(value) ? state.setValue(property, value) : state;
    }

    private static BlockState state(RegistryObject<Block> block, Block fallback) {
        return (block.isPresent() ? block.get() : fallback).defaultBlockState();
    }
}
