package com.flameforge.essentialcraft5.worldgen.hoanna;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/**
 * 海纳尔结构用到、但由别的模块注册的方块（第六批 6a，batch6a §6.2 第 4 条、§5.8 裁定 31）。
 * <p>
 * 结构拼件（M5 城镇、M6 墓穴与矿井）**只通过这里**取下面 6 种方块，不 import 注册它们的模块类：
 * 虚空石 / 虚空栅栏 / 混凝土 / 悬浮器 / 散射器归 ECVoid，根系归 ECHoannaNature。按注册名取
 * （{@link RegistryObject#create(ResourceLocation, net.minecraftforge.registries.IForgeRegistry)}），
 * 还没注册（模块副本里没有那个模块）时退回：结构承重的（虚空石、虚空栅栏、混凝土）退回石头，纯装饰的（根系、悬浮器、散射器）退回空气——
 * 这样副本里结构照样能生成、测试照样能跑，相关断言用 {@link #isRegistered} 跳过。集成后 6 种都在。
 * <p>
 * 已经在前几批注册好的方块（强化石、强化玻璃、魔力护板、魔力栅栏、强化石栅栏、魔力光源……）直接用各自注册类的字段（ECMaterials、ECAlloys、ECEssence），不用经过这里。
 * <p>
 * 取到的是默认状态；栅栏的连接由 {@link HoannaStructurePiece#placeBlock} 补的形状后处理算出来，拼件里放默认状态就行。
 */
public final class HoannaStructureBlocks {

    public static final ResourceLocation VOID_STONE_ID = EssentialCraft5.id("void_stone");
    public static final ResourceLocation VOID_FENCE_ID = EssentialCraft5.id("void_fence");
    public static final ResourceLocation CONCRETE_ID = EssentialCraft5.id("concrete");
    public static final ResourceLocation ROOT_ID = EssentialCraft5.id("root");
    public static final ResourceLocation LEVITATOR_ID = EssentialCraft5.id("levitator");
    public static final ResourceLocation SPREADER_ID = EssentialCraft5.id("spreader");

    private static final RegistryObject<Block> VOID_STONE = RegistryObject.create(VOID_STONE_ID, ForgeRegistries.BLOCKS);
    private static final RegistryObject<Block> VOID_FENCE = RegistryObject.create(VOID_FENCE_ID, ForgeRegistries.BLOCKS);
    private static final RegistryObject<Block> CONCRETE = RegistryObject.create(CONCRETE_ID, ForgeRegistries.BLOCKS);
    private static final RegistryObject<Block> ROOT = RegistryObject.create(ROOT_ID, ForgeRegistries.BLOCKS);
    private static final RegistryObject<Block> LEVITATOR = RegistryObject.create(LEVITATOR_ID, ForgeRegistries.BLOCKS);
    private static final RegistryObject<Block> SPREADER = RegistryObject.create(SPREADER_ID, ForgeRegistries.BLOCKS);

    /** 6 种方块的注册名（测试、文档用） */
    public static final List<ResourceLocation> ALL_IDS = List.of(VOID_STONE_ID, VOID_FENCE_ID, CONCRETE_ID, ROOT_ID, LEVITATOR_ID, SPREADER_ID);

    private HoannaStructureBlocks() {
    }

    /** 虚空石（ECVoid）；没注册时是石头 */
    public static BlockState voidStone() {
        return state(VOID_STONE, Blocks.STONE);
    }

    /** 虚空栅栏（ECVoid，原版 FenceBlock）；没注册时是石头 */
    public static BlockState voidFence() {
        return state(VOID_FENCE, Blocks.STONE);
    }

    /** 混凝土（ECVoid）；没注册时是石头 */
    public static BlockState concrete() {
        return state(CONCRETE, Blocks.STONE);
    }

    /** 根系（ECHoannaNature）；没注册时是空气 */
    public static BlockState root() {
        return state(ROOT, Blocks.AIR);
    }

    /** MRU 悬浮器（ECVoid，小镇房屋四角的装饰）；没注册时是空气 */
    public static BlockState levitator() {
        return state(LEVITATOR, Blocks.AIR);
    }

    /** MRU 散射器（ECVoid，小镇 Spreader 件顶上的装饰）；没注册时是空气 */
    public static BlockState spreader() {
        return state(SPREADER, Blocks.AIR);
    }

    /** 这个注册名的方块已经注册了（模块副本里拿不到别的模块时，测试用它跳过相关断言） */
    public static boolean isRegistered(ResourceLocation id) {
        return ForgeRegistries.BLOCKS.containsKey(id);
    }

    private static BlockState state(RegistryObject<Block> block, Block fallback) {
        return (block.isPresent() ? block.get() : fallback).defaultBlockState();
    }
}
