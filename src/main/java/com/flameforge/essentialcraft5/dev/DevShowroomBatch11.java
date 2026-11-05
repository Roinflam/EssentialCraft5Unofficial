package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUConverterBlockEntity.Mode;
import com.flameforge.essentialcraft5.client.GunsClient;
import com.flameforge.essentialcraft5.gametest.ConverterTestEnergy;
import com.flameforge.essentialcraft5.gun.GunParts;
import com.flameforge.essentialcraft5.gun.GunType;
import com.flameforge.essentialcraft5.item.GunItem;
import com.flameforge.essentialcraft5.registry.ECConverters;
import com.flameforge.essentialcraft5.registry.ECCreative;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * 展示厅的第十一批部分（{@link DevShowroom} 调，只在 -PecShowroom / -PecShowroom11 时跑；batch11.md）。
 * <p>
 * <b>场景</b>（04_scene_b11_*，沿 X 排在方块排北边，镜头从南边朝北拍）：
 * <ul>
 *   <li>{@code converter_states}：五台 MRU 转换器 × 四种外观（由近到远：能量待机、能量工作、魔力待机、魔力工作），用 block_display 摆——
 *       它只按方块状态画模型、没有方块实体，外观不会被 tick 改回去，没装植物魔法也看得到魔力口；{@code converter_states_close} 凑近看叠层和导管；</li>
 *   <li>{@code converter_working}：五台真放下的转换器，北边各贴一个挂了能量接收能力的木桶（{@link ConverterTestEnergy}），南边 3 格一台无尽 MRU 源
 *       每秒补满它们，能量模式一直在推，方块状态 active 由方块实体自己切；从东南角斜上方拍，看导管跨棱接不接得上、叠层有没有闪；</li>
 *   <li>{@code obj_*}：第十一批修过贴图坐标的机器 OBJ 近景（{@code tools/gen/fix_obj_uv.py}），每台垫一格屏障方块，低角度的镜头不会钻进地里。</li>
 * </ul>
 * <b>界面</b>（02_gui_*）：只拍本批时补拍三台转换器的界面（全量展示厅的方块排已经每台都拍了）；低档、恶魔档、无尽档三种的物品提示；
 * 生存能做的最好的加特林（枪托、握把、机栓全是恶魔材料，两格混沌透镜）的物品提示，收起和按住 Shift 展开属性各一张。
 */
public final class DevShowroomBatch11 {

    private DevShowroomBatch11() {
    }

    /** 全量展示厅里本类占几个场景位（OBJ 那一排占三个位） */
    static final int SCENE_SLOTS = 5;

    /** OBJ 那一排相邻两台的间距 */
    private static final int OBJ_SPACING = 5;

    // ================================================================ 入口

    static void build(ServerLevel level, ServerPlayer player, IntFunction<BlockPos> sceneBase, int firstIndex, boolean onlyThisBatch) {
        buildStatesScene(level, sceneBase.apply(firstIndex));
        BlockPos[] working = buildWorkingScene(level, player, sceneBase.apply(firstIndex + 1));
        buildObjScene(level, player, sceneBase.apply(firstIndex + 2));
        if (onlyThisBatch) {
            DevShowroom.screens.add(new DevShowroom.ScreenShot("b11_mru_converter", p -> stand(p, working[0]), p -> open(p, working[0])));
            DevShowroom.screens.add(new DevShowroom.ScreenShot("b11_demonic_mru_converter", p -> stand(p, working[3]), p -> open(p, working[3])));
            DevShowroom.screens.add(new DevShowroom.ScreenShot("b11_infinite_mru_converter", p -> stand(p, working[4]), p -> open(p, working[4])));
        }
        tooltip("tooltip_mru_converter", () -> new ItemStack(ECConverters.MRU_CONVERTER.get()));
        tooltip("tooltip_demonic_mru_converter", () -> new ItemStack(ECConverters.DEMONIC_MRU_CONVERTER.get()));
        tooltip("tooltip_infinite_mru_converter", () -> new ItemStack(ECCreative.INFINITE_MRU_CONVERTER.get()));
        tooltip("tooltip_gatling_demonic", DevShowroomBatch11::bestGatling, false);
        tooltip("tooltip_gatling_demonic_shift", DevShowroomBatch11::bestGatling, true);
    }

    /** 生存能做的最好的加特林：三种枪身部件都用恶魔材料（阿克罗尼特锭），两格混沌透镜，灵魂格最多转进 5000 MRU */
    private static ItemStack bestGatling() {
        return GunItem.create(GunType.GATLING, GunParts.uniform("demonic").withLens("chaos"), 5000);
    }

    // ================================================================ 小工具

    private static Vec3 at(BlockPos base, double dx, double dy, double dz) {
        return DevShowroom.at(base, dx, dy, dz);
    }

    private static List<Supplier<? extends Block>> converters() {
        return List.of(ECConverters.MRU_CONVERTER, ECConverters.PALE_MRU_CONVERTER, ECConverters.MITHRILINE_MRU_CONVERTER,
                ECConverters.DEMONIC_MRU_CONVERTER, ECCreative.INFINITE_MRU_CONVERTER);
    }

    private static void placeAs(ServerLevel level, ServerPlayer player, BlockPos pos, Block block) {
        BlockState state = block.defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        block.setPlacedBy(level, pos, state, player, new ItemStack(block));
    }

    /** 在 pos 摆一个只画方块模型的 block_display（模型的最小角落在 pos） */
    private static void display(ServerLevel level, BlockPos pos, Block block, Mode mode, boolean active) {
        MinecraftServer server = level.getServer();
        String name = ForgeRegistries.BLOCKS.getKey(block).toString();
        String command = String.format("summon minecraft:block_display %d %d %d {block_state:{Name:\"%s\",Properties:{mode:\"%s\",active:\"%s\"}}}",
                pos.getX(), pos.getY(), pos.getZ(), name, mode.getSerializedName(), active);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withSuppressedOutput(), command);
    }

    private static void stand(ServerPlayer player, BlockPos machine) {
        player.teleportTo(machine.getX() + 0.5, machine.getY(), machine.getZ() + 2.5);
    }

    private static void open(ServerPlayer player, BlockPos pos) {
        if (player.serverLevel().getBlockEntity(pos) instanceof MRUBlockEntity be) {
            NetworkHooks.openScreen(player, be, pos);
        }
    }

    private static void tooltip(String name, Supplier<ItemStack> stack) {
        tooltip(name, stack, false);
    }

    /** shift = 枪的提示按「按着 Shift」画（展开属性）；拍下一张时由下一个提示截图改回去 */
    private static void tooltip(String name, Supplier<ItemStack> stack, boolean shift) {
        DevShowroom.screens.add(new DevShowroom.ScreenShot(name, p -> { }, p -> Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.getToasts().clear();
            GunsClient.setShowroomShift(shift);
            mc.setScreen(new TooltipShot(stack.get()));
        })));
    }

    /**
     * 围着一格方块拍：{@code azimuth} 是镜头在方块的哪个方位（0 = 正南、90 = 正东，度），{@code elevation} 是仰角（负数 = 从下往上看），
     * {@code distance} 是镜头到方块中心的距离（格）。镜头就是展示玩家的眼睛，身体的碰撞箱不能和方块重叠（会被推开、画面偏掉），
     * 所以从正上方往下看时仰角别超过 60°、距离留够
     */
    private static DevShowroom.SceneShot around(String name, BlockPos block, double azimuth, double elevation, double distance, int delay) {
        double az = Math.toRadians(azimuth);
        double el = Math.toRadians(elevation);
        Vec3 center = at(block, 0.5, 0.5, 0.5);
        Vec3 eye = center.add(Math.sin(az) * Math.cos(el) * distance, Math.sin(el) * distance, Math.cos(az) * Math.cos(el) * distance);
        return new DevShowroom.SceneShot(name, null, eye, center, delay);
    }

    // ================================================================ 场景

    /** 五台 × 四种外观：列是档位（西 → 东），行是外观（南 → 北：能量待机、能量工作、魔力待机、魔力工作） */
    private static void buildStatesScene(ServerLevel level, BlockPos base) {
        List<Supplier<? extends Block>> blocks = converters();
        Mode[] modes = {Mode.ENERGY, Mode.ENERGY, Mode.MANA, Mode.MANA};
        boolean[] active = {false, true, false, true};
        for (int column = 0; column < blocks.size(); column++) {
            for (int row = 0; row < modes.length; row++) {
                display(level, base.offset(column * 2, 0, -row * 2), blocks.get(column).get(), modes[row], active[row]);
            }
        }
        DevShowroom.scenes.add(new DevShowroom.SceneShot("b11_converter_states", null, at(base, 6.5, 6.5, 6.5), at(base, 4.5, 0.2, -3),
                DevShowroom.FIRST_SCENE_WAIT));
        // 近景：米斯莱、恶魔、无尽三档的能量工作 / 魔力工作两行
        DevShowroom.scenes.add(new DevShowroom.SceneShot("b11_converter_states_close", null, at(base, 10.6, 2.4, 2.4), at(base, 6.5, 0.5, -2.0), 30));
        DevShowroom.scenes.add(new DevShowroom.SceneShot("b11_converter_states_top", null, at(base, 4.6, 7.5, 0.6), at(base, 4.5, 0.5, -3.0), 20));
    }

    /**
     * 五台真放下的转换器（展示玩家放下、MRU 加满、能量模式、不受每人台数限制），北边各贴一个挂了能量接收能力的木桶，
     * 南边 3 格放一台无尽 MRU 源（每秒给 8 格内的机器补满，不然低档几秒就把 MRU 推光了）；返回五台的位置（界面截图用）
     */
    private static BlockPos[] buildWorkingScene(ServerLevel level, ServerPlayer player, BlockPos base) {
        List<Supplier<? extends Block>> blocks = converters();
        BlockPos[] placed = new BlockPos[blocks.size()];
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos pos = base.offset(i * 3, 0, 0);
            BlockPos barrel = pos.north();
            // 先登记再放木桶：能力在木桶的方块实体建好时就挂上
            ConverterTestEnergy.expect(barrel, Integer.MAX_VALUE, Integer.MAX_VALUE);
            level.setBlockAndUpdate(barrel, Blocks.BARREL.defaultBlockState());
            placeAs(level, player, pos, blocks.get(i).get());
            if (level.getBlockEntity(pos) instanceof MRUConverterBlockEntity be) {
                be.setOwnerLimitForTest(0);
                be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
            } else {
                EssentialCraft5.LOGGER.warn("[showroom] {} 没有 MRU 转换器方块实体", pos);
            }
            placed[i] = pos;
        }
        level.setBlockAndUpdate(base.offset(6, 0, 3), ECCreative.INFINITE_MRU_SOURCE.get().defaultBlockState());
        DevShowroom.scenes.add(new DevShowroom.SceneShot("b11_converter_working", null, at(base, 9.5, 4.2, 7.0), at(base, 6.5, 0.5, 0.0), 80));
        DevShowroom.scenes.add(around("b11_converter_working_close", placed[3], 40, 28, 2.6, 20));
        return placed;
    }

    /** OBJ 机器一排（每台垫一格屏障）；镜头按修贴图坐标时列的要看的角度拍 */
    private static void buildObjScene(ServerLevel level, ServerPlayer player, BlockPos base) {
        String[] names = {"radiating_chamber", "charging_chamber", "crystal_controller", "magical_repairer", "matrix_absorber",
                "mithriline_furnace", "ender_generator", "heat_generator", "ultra_heat_generator", "natural_furnace",
                "ultra_natural_furnace", "sun_ray_absorber"};
        BlockPos[] at = new BlockPos[names.length];
        for (int i = 0; i < names.length; i++) {
            BlockPos pos = base.offset(i * OBJ_SPACING, 1, 0);
            at[i] = pos;
            Block block = ForgeRegistries.BLOCKS.getValue(EssentialCraft5.id(names[i]));
            if (block == null || block == Blocks.AIR) {
                EssentialCraft5.LOGGER.warn("[showroom] 没有方块 {}", names[i]);
                continue;
            }
            level.setBlockAndUpdate(pos.below(), Blocks.BARRIER.defaultBlockState());
            placeAs(level, player, pos, block);
        }
        List<DevShowroom.SceneShot> shots = DevShowroom.scenes;
        shots.add(new DevShowroom.SceneShot("b11_obj_lineup", null, at(base, 27.5, 5.5, 12.0), at(base, 27.5, 1.0, 0.5), DevShowroom.FIRST_SCENE_WAIT));
        // 辐射仓：贴着侧面低角度看圆板厚边；斜上方靠近一个角看护角块侧面和顶上的井壁；正对圆板看外圈四角的斜接缝
        shots.add(around("b11_obj_radiating_graze", at[0], 65, 10, 2.0, 30));
        shots.add(around("b11_obj_radiating_corner", at[0], 40, 28, 1.9, 15));
        shots.add(around("b11_obj_radiating_plate", at[0], 0, 0, 1.9, 15));
        shots.add(around("b11_obj_radiating_plate_east", at[0], 90, 0, 1.9, 15));
        shots.add(around("b11_obj_charging_chamber", at[1], 35, 5, 2.1, 20));
        shots.add(around("b11_obj_crystal_controller", at[2], 40, 20, 1.8, 20));
        shots.add(around("b11_obj_magical_repairer", at[3], 40, 30, 1.9, 20));
        shots.add(around("b11_obj_magical_repairer_front", at[3], 0, 0, 1.7, 15));
        shots.add(around("b11_obj_matrix_absorber", at[4], 40, 25, 2.1, 20));
        shots.add(around("b11_obj_mithriline_furnace_low", at[5], 215, -15, 2.1, 20));
        shots.add(around("b11_obj_mithriline_furnace", at[5], 35, 15, 2.1, 15));
        shots.add(around("b11_obj_ender_generator", at[6], 30, 45, 2.3, 20));
        shots.add(around("b11_obj_heat_generator_top", at[7], 15, 60, 2.6, 20));
        shots.add(around("b11_obj_ultra_heat_generator_top", at[8], 15, 60, 2.6, 20));
        shots.add(around("b11_obj_natural_furnace", at[9], 30, 5, 1.9, 20));
        shots.add(around("b11_obj_ultra_natural_furnace", at[10], 30, 5, 1.9, 20));
        shots.add(around("b11_obj_sun_ray_absorber", at[11], 20, 10, 2.1, 20));
    }

    /** 只画一个物品图标和它的提示框（游戏自己的提示内容和样式），截图用，不动鼠标 */
    private static final class TooltipShot extends Screen {
        private final ItemStack stack;

        TooltipShot(ItemStack stack) {
            super(Component.empty());
            this.stack = stack;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            graphics.renderItem(stack, 16, 16);
            graphics.renderTooltip(font, stack, 30, 30);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
