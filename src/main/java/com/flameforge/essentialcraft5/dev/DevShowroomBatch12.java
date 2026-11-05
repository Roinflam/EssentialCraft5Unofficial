package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.ElementalDecomposerBlock;
import com.flameforge.essentialcraft5.blockentity.ElementalDecomposerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalCultivatorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MatrixTunerBlockEntity;
import com.flameforge.essentialcraft5.item.AncientScrollItem;
import com.flameforge.essentialcraft5.item.EssentialTool;
import com.flameforge.essentialcraft5.item.HomewardCompassItem;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.lore.ScrollVolumes;
import com.flameforge.essentialcraft5.registry.ECCrystalPillars;
import com.flameforge.essentialcraft5.registry.ECCultivator;
import com.flameforge.essentialcraft5.registry.ECDecomposer;
import com.flameforge.essentialcraft5.registry.ECEssentialTools;
import com.flameforge.essentialcraft5.registry.ECHomewardCompass;
import com.flameforge.essentialcraft5.registry.ECLeyDetector;
import com.flameforge.essentialcraft5.registry.ECMatrixTuner;
import com.flameforge.essentialcraft5.registry.ECUtilityModules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * 展示厅的第十二批部分（{@link DevShowroom} 调，只在 -PecShowroom12 时跑；batch12.md §9.3）。截图名都以 {@code ec5_b12_} 开头。
 * <p>
 * <b>场景</b>（沿 X 排在方块排北边，镜头从南边朝北拍）：
 * <ul>
 *   <li>{@code b12_machines*}：三台机器摆一排，镜头在东南斜上方，同时看得到顶面和侧面。魔力栽培器两台：西边那台空着（玻璃顶下面是土槽和几株嫩芽），
 *       东边那台放了 16 颗小麦种子和一桶水、MRU 加满，拍之前把这一轮的进度拨到一半多，透过玻璃顶看得见长到一半的小麦；
 *       元素分解器朝南（正面对着镜头），输入格一组圆石、MRU 加满；矩阵调律器同一高度隔一格围 8 根恶魔水晶柱。几台各有一张近景；</li>
 *   <li>{@code b12_items*}：一面平滑石墙上挂隐形展示框，从左到右、从上到下：七卷源质残卷（序、卷一～卷六，看蜡封的七种颜色）、
 *       没绑定 / 绑定过的归途罗盘、满电的地脉探测仪、源质镐、源质斧、四块生活模块。</li>
 * </ul>
 * <b>界面</b>：三台机器各开一次界面。<b>手册</b>：第十二批新条目所在的每一章翻一页，古卷一章翻说明页和卷三的卷文
 * （展示玩家先记下读过卷三，那一篇才打开）。
 */
public final class DevShowroomBatch12 {

    private DevShowroomBatch12() {
    }

    /** 卷文那一页拍哪一卷（展示玩家进世界时记成读过） */
    static final int SHOWN_VOLUME = 3;

    // ================================================================ 入口

    static void build(ServerLevel level, ServerPlayer player, IntFunction<BlockPos> sceneBase, int firstIndex) {
        BlockPos[] machines = buildMachineScene(level, player, sceneBase.apply(firstIndex));
        buildItemScene(level, sceneBase.apply(firstIndex + 1));
        DevShowroom.screens.add(new DevShowroom.ScreenShot("b12_gui_magical_cultivator", p -> stand(p, machines[1]), p -> open(p, machines[1])));
        DevShowroom.screens.add(new DevShowroom.ScreenShot("b12_gui_elemental_decomposer", p -> stand(p, machines[2]), p -> open(p, machines[2])));
        DevShowroom.screens.add(new DevShowroom.ScreenShot("b12_gui_matrix_tuner", p -> stand(p, machines[3]), p -> open(p, machines[3])));
        if (!ScrollVolumes.award(player, SHOWN_VOLUME) && !ScrollVolumes.isRead(player, SHOWN_VOLUME)) {
            EssentialCraft5.LOGGER.warn("[showroom] 没能记下读过残卷第 {} 卷，手册卷文那一页会是锁着的", SHOWN_VOLUME);
        }
    }

    // ================================================================ 小工具

    private static Vec3 at(BlockPos base, double dx, double dy, double dz) {
        return DevShowroom.at(base, dx, dy, dz);
    }

    private static void placeAs(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state);
        state.getBlock().setPlacedBy(level, pos, state, player, new ItemStack(state.getBlock()));
    }

    private static void stand(ServerPlayer player, BlockPos machine) {
        player.teleportTo(machine.getX() + 0.5, machine.getY(), machine.getZ() + 2.5);
    }

    private static void open(ServerPlayer player, BlockPos pos) {
        if (player.serverLevel().getBlockEntity(pos) instanceof MenuProvider provider) {
            NetworkHooks.openScreen(player, provider, pos);
        }
    }

    /**
     * 围着一格方块拍（同第十一批）：{@code azimuth} 是镜头在方块的哪个方位（0 = 正南、90 = 正东，度），{@code elevation} 是仰角，
     * {@code distance} 是镜头到方块中心的距离（格）。镜头就是展示玩家的眼睛，身体的碰撞箱不能和方块重叠
     */
    private static DevShowroom.SceneShot around(String name, BlockPos block, double azimuth, double elevation, double distance, int delay) {
        double az = Math.toRadians(azimuth);
        double el = Math.toRadians(elevation);
        Vec3 center = at(block, 0.5, 0.5, 0.5);
        Vec3 eye = center.add(Math.sin(az) * Math.cos(el) * distance, Math.sin(el) * distance, Math.cos(az) * Math.cos(el) * distance);
        return new DevShowroom.SceneShot(name, null, eye, center, delay);
    }

    // ================================================================ 场景

    /**
     * 三台机器一排（西 → 东）：空的栽培器、种着小麦的栽培器、元素分解器、矩阵调律器（四周 8 根恶魔水晶柱）；
     * 返回 {空栽培器, 种着的栽培器, 分解器, 调律器} 的位置
     */
    private static BlockPos[] buildMachineScene(ServerLevel level, ServerPlayer player, BlockPos base) {
        BlockPos emptyCultivator = base;
        BlockPos cultivator = base.offset(2, 0, 0);
        BlockPos decomposer = base.offset(4, 0, 0);
        BlockPos tuner = base.offset(9, 0, 0);
        Block cultivatorBlock = ECCultivator.MAGICAL_CULTIVATOR.get();
        placeAs(level, player, emptyCultivator, cultivatorBlock.defaultBlockState());
        placeAs(level, player, cultivator, cultivatorBlock.defaultBlockState());
        if (level.getBlockEntity(cultivator) instanceof MagicalCultivatorBlockEntity be) {
            be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
            be.getInventory().setStackInSlot(MagicalCultivatorBlockEntity.SLOT_SEED, new ItemStack(Items.WHEAT_SEEDS, 16));
            be.getInventory().setStackInSlot(MagicalCultivatorBlockEntity.SLOT_WATER, new ItemStack(Items.WATER_BUCKET));
        } else {
            EssentialCraft5.LOGGER.warn("[showroom] {} 没有魔力栽培器方块实体", cultivator);
        }
        placeAs(level, player, decomposer, ECDecomposer.ELEMENTAL_DECOMPOSER.get().defaultBlockState()
                .setValue(ElementalDecomposerBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(decomposer) instanceof ElementalDecomposerBlockEntity be) {
            be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
            be.getInventory().setStackInSlot(ElementalDecomposerBlockEntity.FIRST_INPUT, new ItemStack(Items.COBBLESTONE, 64));
        } else {
            EssentialCraft5.LOGGER.warn("[showroom] {} 没有元素分解器方块实体", decomposer);
        }
        placeAs(level, player, tuner, ECMatrixTuner.MATRIX_TUNER.get().defaultBlockState());
        if (!(level.getBlockEntity(tuner) instanceof MatrixTunerBlockEntity)) {
            EssentialCraft5.LOGGER.warn("[showroom] {} 没有矩阵调律器方块实体", tuner);
        }
        BlockState pillar = ECCrystalPillars.DEMONIC_CRYSTAL_PILLAR.get().defaultBlockState();
        for (int dx = -2; dx <= 2; dx += 2) {
            for (int dz = -2; dz <= 2; dz += 2) {
                if (dx != 0 || dz != 0) {
                    level.setBlockAndUpdate(tuner.offset(dx, 0, dz), pillar);
                }
            }
        }
        // 种着的那台：拍之前把这一轮拨到一半多（机器进世界后自己开始一轮，这时早就开始了；渲染档跨档才同步，等 40 刻）
        Consumer<ServerPlayer> grow = p -> {
            if (level.getBlockEntity(cultivator) instanceof MagicalCultivatorBlockEntity be) {
                be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
                be.setTestProgress(1500);
            }
        };
        List<DevShowroom.SceneShot> shots = DevShowroom.scenes;
        shots.add(new DevShowroom.SceneShot("b12_machines", grow, at(base, 8.5, 5.5, 7.5), at(base, 4.5, 0.5, 0.5), DevShowroom.FIRST_SCENE_WAIT));
        // 栽培器：斜上方看两台的顶和侧面；再从高处往下看种着的那台、空的那台的玻璃顶
        shots.add(new DevShowroom.SceneShot("b12_cultivators", null, at(base, 3.6, 2.6, 3.2), at(base, 1.5, 0.5, 0.5), 20));
        shots.add(around("b12_cultivator_growing_top", cultivator, 25, 62, 2.2, 20));
        shots.add(around("b12_cultivator_empty_top", emptyCultivator, 25, 62, 2.2, 15));
        shots.add(around("b12_cultivator_empty_oblique", emptyCultivator, 40, 40, 2.0, 15));
        // 分解器：正面（南）、侧面（东）、顶面同时在画面里
        shots.add(around("b12_elemental_decomposer", decomposer, 35, 30, 2.3, 20));
        shots.add(around("b12_elemental_decomposer_front", decomposer, 0, 10, 2.0, 15));
        // 调律器：镜头在柱子圈外面
        shots.add(around("b12_matrix_tuner", tuner, 30, 40, 5.0, 20));
        shots.add(around("b12_matrix_tuner_top", tuner, 10, 60, 3.6, 15));
        return new BlockPos[]{emptyCultivator, cultivator, decomposer, tuner};
    }

    /** 一面墙上挂隐形展示框：七卷残卷一排，罗盘两种、探测仪、镐、斧一排，四块模块一排 */
    private static void buildItemScene(ServerLevel level, BlockPos base) {
        List<List<ItemStack>> rows = new ArrayList<>();
        List<ItemStack> scrolls = new ArrayList<>();
        for (int v = 0; v < ScrollVolumes.COUNT; v++) {
            scrolls.add(AncientScrollItem.of(v, 1));
        }
        rows.add(scrolls);
        ItemStack bound = new ItemStack(ECHomewardCompass.HOMEWARD_COMPASS.get());
        HomewardCompassItem.setHome(bound, level.dimension(), base);
        rows.add(List.of(new ItemStack(ECHomewardCompass.HOMEWARD_COMPASS.get()), bound, MRUStaffItem.full(ECLeyDetector.LEY_DETECTOR.get()),
                EssentialTool.full(ECEssentialTools.ESSENTIAL_PICKAXE.get()), EssentialTool.full(ECEssentialTools.ESSENTIAL_AXE.get())));
        rows.add(List.of(ECUtilityModules.ATTRACTION.stack(), ECUtilityModules.STRIDE.stack(), ECUtilityModules.STEADY_HAND.stack(),
                ECUtilityModules.VEIL.stack()));
        int columns = ScrollVolumes.COUNT;
        for (int x = -1; x <= columns; x++) {
            for (int y = 0; y <= rows.size() + 1; y++) {
                level.setBlockAndUpdate(base.offset(x, y, -1), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        for (int r = 0; r < rows.size(); r++) {
            List<ItemStack> row = rows.get(r);
            for (int i = 0; i < row.size(); i++) {
                ItemFrame frame = new ItemFrame(level, base.offset(i, rows.size() - r, 0), Direction.SOUTH);
                frame.setItem(row.get(i), false);
                frame.setInvisible(true);
                level.addFreshEntity(frame);
            }
        }
        DevShowroom.scenes.add(new DevShowroom.SceneShot("b12_items", null, at(base, 3.5, 2.5, 5.5), at(base, 3.5, 2.5, 0), 30));
        DevShowroom.scenes.add(new DevShowroom.SceneShot("b12_items_scrolls", null, at(base, 3.5, 3.5, 2.6), at(base, 3.5, 3.5, 0), 15));
    }
}
