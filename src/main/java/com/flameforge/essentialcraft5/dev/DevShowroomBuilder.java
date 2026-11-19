package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.BuilderHud;
import com.flameforge.essentialcraft5.client.BuilderInput;
import com.flameforge.essentialcraft5.client.screen.BuilderPanelLayout;
import com.flameforge.essentialcraft5.client.screen.BuilderPanelScreen;
import com.flameforge.essentialcraft5.item.BuilderActions;
import com.flameforge.essentialcraft5.item.BuilderJobs;
import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.BuilderMix;
import com.flameforge.essentialcraft5.item.BuilderModules;
import com.flameforge.essentialcraft5.item.BuilderSalvage;
import com.flameforge.essentialcraft5.item.BuilderSelection;
import com.flameforge.essentialcraft5.item.BuilderShape;
import com.flameforge.essentialcraft5.item.BuilderShapes;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * 只拍创世神之杖升级第 1 批的客户端部分（-PecShowroomBuilder）：铺面预览（材料够 / 不够）、格子多时只画外框、直线预览、待确认、
 * 用完暂停、施工到一半、铺完、撤销后，界面缩放 3 和 2 各一轮（截图名 {@code 04_scene_builder_*_s<n>}），外加研究手册里形状和撤销两节。
 * 生存模式（会扣材料），飞着、低头看脚下的草方块顶面（离眼睛约 4 格，生存的点中距离以内）；范围里摆一块石头（已有方块不画）和一个盔甲架（挡住的格子画暗红小框）。
 * 尺寸、切形状、撤销都调 {@link BuilderInput} 的展示厅入口，走的是和按键一样的那段处理与操作包，不发按键。
 * 每张拍之前把准星下提示的排版（面板和每行的界面坐标）打进日志（「[showroom] builder hud」），量测脚本按它核截图。
 * 后面接着拍面板「蓝图台」（{@link #panelScenes}）；{@code -PecShowroomBuilderPanel} 只拍面板那一组。
 */
final class DevShowroomBuilder {

    private static final int[] SCALES = {3, 2};
    /** 面板拍的界面缩放 */
    private static final int[] PANEL_SCALES = {2, 3, 4};
    /** -PecShowroomBuilderPanel：只拍面板，不拍准星下提示那一组 */
    private static final boolean PANEL_ONLY = Boolean.getBoolean("essentialcraft5.showroom.builderPanel");
    /** 一开始的铺面尺寸、身上的圆石：7 × 7 = 49 格够，调到 12 × 12 = 144 格就不够 */
    private static final int START_SIZE = 7;
    private static final int COBBLE = 64;

    private static BlockPos ground;

    private DevShowroomBuilder() {
    }

    static void build(ServerLevel level, ServerPlayer player, IntFunction<BlockPos> sceneBase, int firstIndex) {
        BlockPos base = sceneBase.apply(firstIndex);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, base.getX(), base.getZ());
        ground = new BlockPos(base.getX(), top - 1, base.getZ());
        // 范围里一块石头（填充时跳过、不画）和一个盔甲架（那一格被挡住、画小框）；镜头朝北，铺面以 ground 为中心
        level.setBlockAndUpdate(ground.offset(-2, 1, 1), Blocks.STONE.defaultBlockState());
        ArmorStand stand = EntityType.ARMOR_STAND.create(level);
        if (stand != null) {
            stand.moveTo(ground.getX() + 2.5D, ground.getY() + 1D, ground.getZ() - 1.5D, 0F, 0F);
            stand.setNoGravity(true);
            level.addFreshEntity(stand);
        }
        Vec3 eye = new Vec3(ground.getX() + 0.5D, ground.getY() + 1D + 2.7D, ground.getZ() + 3.5D);
        Vec3 target = new Vec3(ground.getX() + 0.5D, ground.getY() + 1D, ground.getZ() + 0.5D);
        if (!PANEL_ONLY) {
            hudScenes(eye, target);
        }
        panelScenes(eye, target);
    }

    private static void hudScenes(Vec3 eye, Vec3 target) {
        for (int scale : SCALES) {
            String s = "_s" + scale;
            scene("builder_plane_ok" + s, scale, DevShowroomBuilder::freshStart, eye, target, null);
            scene("builder_plane_short" + s, scale, null, eye, target, mc -> scroll(5, false));
            scene("builder_outline" + s, scale, null, eye, target, mc -> scroll(12, false));
            scene("builder_line" + s, scale, null, eye, target, mc -> BuilderInput.showroomTap());
            scene("builder_line_wide" + s, scale, null, eye, target, mc -> {
                scroll(2, true);
                scroll(4, false);
            });
            scene("builder_confirm" + s, scale, p -> {
                ItemStack wand = p.getMainHandItem();
                BuilderSelection.setShape(wand, BuilderShape.PLANE);
                BuilderSelection.setSize(wand, BuilderShape.PLANE, 12, 12);
                BuilderJobs.request(p, InteractionHand.MAIN_HAND, new BuilderShapes.Hit(ground, Direction.UP), true);
            }, eye, target, null, 15);
            scene("builder_paused" + s, scale, p -> BuilderJobs.request(p, InteractionHand.MAIN_HAND,
                    new BuilderShapes.Hit(ground, Direction.UP), false), eye, target, null, 40);
            scene("builder_running" + s, scale, p -> {
                ECBuilder.Config.setTestOverrides(null, 1, null);
                p.getInventory().add(new ItemStack(Items.COBBLESTONE, COBBLE * 2));
            }, eye, target, null, 50);
            scene("builder_done" + s, scale, p -> ECBuilder.Config.setTestOverrides(null, null, null), eye, target, null, 40);
            scene("builder_undo" + s, scale, null, eye, target, mc -> BuilderInput.showroomUndo(), 50);
        }
    }

    /**
     * 面板「蓝图台」：窗口 1920 × 1080 下界面缩放 2、3、4 各一轮，每轮三页 × 空闲 / 施工中 / 暂停，另有缩放 3 的几种状态（混铺 4 种、丢弃清单、
     * 混铺停用、混铺施工中、上一单是清除）；1280 × 720 缩放 2、3，854 × 480（缩放 3 实际只能到 2）、640 × 480（缩放 2，界面 320 × 240，窄版版面）
     * 各拍三页空闲。一次只拍一种窗口：用 -PecShowroomWindow=WxH 选（不带是 1280 × 720）。截图名 {@code 04_scene_builder_panel_<状态>_<页>_<窗口>_s<n>}，
     * 每张拍之前把版面（每个框的界面坐标）打进日志（「[showroom] builder panel」），量对齐按它核。
     */
    private static void panelScenes(Vec3 eye, Vec3 target) {
        String[] pages = {"range", "block", "modules"};
        for (int scale : PANEL_SCALES) {
            String s = "_1920_s" + scale;
            for (int i = 0; i < 3; i++) {
                int page = i;
                panelScene("builder_panel_idle_" + pages[i] + s, scale, 1920, 1080, i == 0 ? DevShowroomBuilder::panelStart : null, eye, target,
                        page, i == 0 ? 40 : 15);
            }
            for (int i = 0; i < 3; i++) {
                // 每刻 1 格，144 格的铺面：拍三页的工夫还远没做完
                panelScene("builder_panel_running_" + pages[i] + s, scale, 1920, 1080, i == 0 ? p -> {
                    ECBuilder.Config.setTestOverrides(null, 1, null);
                    ItemStack wand = p.getMainHandItem();
                    BuilderSelection.setSize(wand, BuilderShape.PLANE, 12, 12);
                    BuilderJobs.request(p, InteractionHand.MAIN_HAND, new BuilderShapes.Hit(ground, Direction.UP), false);
                } : null, eye, target, i, i == 0 ? 20 : 10);
            }
            for (int i = 0; i < 3; i++) {
                // 放开每刻上限：身上的料一下用完，停在「材料用完」
                panelScene("builder_panel_paused_" + pages[i] + s, scale, 1920, 1080,
                        i == 0 ? p -> ECBuilder.Config.setTestOverrides(null, 64, null) : null, eye, target, i, i == 0 ? 40 : 10);
            }
        }
        int[][] windows = {{854, 480, 3}, {640, 480, 2}};
        for (int[] window : windows) {
            String s = "_" + window[0] + "_s" + window[2];
            for (int i = 0; i < 3; i++) {
                panelScene("builder_panel_idle_" + pages[i] + s, window[2], window[0], window[1], i == 0 ? DevShowroomBuilder::panelStart : null,
                        eye, target, i, i == 0 ? 40 : 15);
            }
        }
        // 1280 × 720：缩放 2（界面 640 × 360）、3（界面 426 × 240，窄版）各三页空闲
        for (int scale : new int[] {2, 3}) {
            String s = "_1280_s" + scale;
            for (int i = 0; i < 3; i++) {
                panelScene("builder_panel_idle_" + pages[i] + s, scale, 1280, 720, i == 0 ? DevShowroomBuilder::panelStart : null,
                        eye, target, i, i == 0 ? 40 : 15);
            }
        }
        // 其余状态都在 1920 × 1080 缩放 3 下拍
        String s3 = "_1920_s3";
        // 混铺表满 4 种（三种石砖地图色一样）、丢弃清单 9 项
        panelScene("builder_panel_mix4_block" + s3, 3, 1920, 1080, p -> {
            panelStart(p);
            ItemStack wand = p.getMainHandItem();
            BuilderMix.write(wand, List.of(new BuilderMix.Entry(Blocks.STONE_BRICKS, 5), new BuilderMix.Entry(Blocks.MOSSY_STONE_BRICKS, 2),
                    new BuilderMix.Entry(Blocks.CRACKED_STONE_BRICKS, 1), new BuilderMix.Entry(Blocks.OAK_PLANKS, 2)), 7);
            BuilderSalvage.writeDiscard(wand, List.of(Items.DIRT, Items.GRAVEL, Items.SAND, Items.ANDESITE, Items.GRANITE, Items.DIORITE,
                    Items.TUFF, Items.COBBLED_DEEPSLATE, Items.NETHERRACK));
            p.getInventory().setItem(5, new ItemStack(Items.STONE_BRICKS, 64));
            p.getInventory().setItem(6, new ItemStack(Items.MOSSY_STONE_BRICKS, 64));
            p.getInventory().setItem(7, new ItemStack(Items.CRACKED_STONE_BRICKS, 64));
            p.inventoryMenu.broadcastChanges();
        }, eye, target, 1, 40);
        panelScene("builder_panel_mix4_range" + s3, 3, 1920, 1080, null, eye, target, 0, 15);
        panelScene("builder_panel_discard_modules" + s3, 3, 1920, 1080, null, eye, target, 2, 15);
        // 混铺停用
        panelScene("builder_panel_mixoff_modules" + s3, 3, 1920, 1080, p -> {
            BuilderModules.setEnabled(p.getMainHandItem(), BuilderModules.Kind.MIX, false);
            p.inventoryMenu.broadcastChanges();
        }, eye, target, 2, 30);
        panelScene("builder_panel_mixoff_block" + s3, 3, 1920, 1080, null, eye, target, 1, 15);
        // 混铺 4 种施工中：蓝图按比例上色、点亮进度
        panelScene("builder_panel_mix4run_block" + s3, 3, 1920, 1080, p -> {
            BuilderModules.setEnabled(p.getMainHandItem(), BuilderModules.Kind.MIX, true);
            ECBuilder.Config.setTestOverrides(null, 1, null);
            BuilderSelection.setSize(p.getMainHandItem(), BuilderShape.PLANE, 12, 12);
            BuilderJobs.request(p, InteractionHand.MAIN_HAND, new BuilderShapes.Hit(ground, Direction.UP), false);
        }, eye, target, 1, 40);
        // 上一单是清除：先铺一片做完，再换清除模式清一片，撤销变灰
        panelScene("builder_panel_placed_range" + s3, 3, 1920, 1080, p -> {
            panelStart(p);
            ECBuilder.Config.setTestOverrides(null, 64, null);
            BuilderJobs.request(p, InteractionHand.MAIN_HAND, new BuilderShapes.Hit(ground, Direction.UP), false);
        }, eye, target, 0, 40);
        panelScene("builder_panel_cleared_range" + s3, 3, 1920, 1080, p -> {
            BuilderActions.setMode(p, InteractionHand.MAIN_HAND, BuilderMode.CLEAR.ordinal());
            BuilderJobs.request(p, InteractionHand.MAIN_HAND, new BuilderShapes.Hit(ground.above(), Direction.UP), false);
        }, eye, target, 0, 60);
        panelScene("builder_panel_cleared_block" + s3, 3, 1920, 1080, null, eye, target, 1, 15);
    }

    /** 面板每轮开头：停掉上一单，新魔杖装上疾工、混铺、回收（三槽满），混铺圆石 3 : 石头 2，丢弃清单里放泥土；身上几种方块 */
    private static void panelStart(ServerPlayer player) {
        BuilderJobs.stop(player);
        // 上一轮铺上的那层清掉（只留那块石头），每轮的空闲 / 施工中 / 暂停都从同一片空地开始
        ServerLevel level = player.serverLevel();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                level.setBlockAndUpdate(ground.offset(dx, 1, dz), dx == -2 && dz == 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
        }
        freshStart(player);
        ItemStack wand = player.getMainHandItem();
        for (BuilderModules.Kind kind : new BuilderModules.Kind[] {BuilderModules.Kind.HASTE, BuilderModules.Kind.MIX, BuilderModules.Kind.SALVAGE}) {
            ItemStack next = BuilderModules.install(wand, kind);
            if (!next.isEmpty()) {
                wand = next;
            }
        }
        BuilderMix.write(wand, List.of(new BuilderMix.Entry(Blocks.COBBLESTONE, 3), new BuilderMix.Entry(Blocks.STONE, 2)), 7);
        BuilderSalvage.writeDiscard(wand, List.of(Items.DIRT));
        player.getInventory().setItem(0, wand);
        player.getInventory().setItem(2, new ItemStack(Items.STONE, 32));
        player.getInventory().setItem(3, new ItemStack(Items.OAK_PLANKS, 16));
        player.getInventory().setItem(4, new ItemStack(Items.DIRT, 8));
        player.inventoryMenu.broadcastChanges();
    }

    /** 一张面板：窗口换成 w × h、界面缩放调到 scale、开着面板翻到第 page 页；拍之前打一行版面 */
    private static void panelScene(String name, int scale, int w, int h, Consumer<ServerPlayer> prepare, Vec3 eye, Vec3 target, int page, int delay) {
        // 进了世界以后在场景里改窗口尺寸实测不生效（截图一直是开头设的尺寸），每种窗口单跑一次：
        // -PecShowroomWindow=WxH 只拍这个宽度的那几张，不带时只拍 1280 宽的
        if (w != Integer.getInteger("essentialcraft5.showroom.width", 1280)) {
            return;
        }
        DevShowroom.scenes.add(new DevShowroom.SceneShot(name, prepare, eye, target, delay, mc -> {
            mc.options.hideGui = false;
            mc.getToasts().clear();
            if (mc.getWindow().getScreenWidth() != w || mc.getWindow().getScreenHeight() != h) {
                GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), w, h);
            }
            if (mc.options.guiScale().get() != scale) {
                mc.options.guiScale().set(scale);
            }
            mc.resizeDisplay();
            if (!(mc.screen instanceof BuilderPanelScreen)) {
                mc.setScreen(new BuilderPanelScreen(InteractionHand.MAIN_HAND));
            }
            ((BuilderPanelScreen) mc.screen).showroomPage(BuilderPanelLayout.Page.values()[page]);
        }, mc -> {
            if (mc.screen instanceof BuilderPanelScreen screen && screen.currentLayout() != null) {
                EssentialCraft5.LOGGER.info("[showroom] builder panel {}: scale {} {}", name, mc.getWindow().getGuiScale(),
                        screen.currentLayout().describe());
            }
            return true;
        }));
    }

    /** 每轮开头：生存模式、能飞；新魔杖（铺面 7 × 7、选定圆石、MRU 满），身上 64 个圆石 */
    private static void freshStart(ServerPlayer player) {
        ECBuilder.Config.setTestOverrides(null, null, null);
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.getInventory().clearContent();
        ItemStack wand = MRUStaffItem.full(ECBuilder.MAGICAL_BUILDER_FILL.get());
        BuilderSelection.setStoredBlock(wand, ForgeRegistries.BLOCKS.getKey(Blocks.COBBLESTONE));
        BuilderSelection.setShape(wand, BuilderShape.PLANE);
        BuilderSelection.setSize(wand, BuilderShape.PLANE, START_SIZE, START_SIZE);
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, wand);
        player.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, COBBLE));
        player.inventoryMenu.broadcastChanges();
    }

    private static void scroll(int times, boolean sneak) {
        for (int i = 0; i < times; i++) {
            BuilderInput.showroomScroll(1, sneak);
        }
    }

    private static void scene(String name, int scale, Consumer<ServerPlayer> prepare, Vec3 eye, Vec3 target, Consumer<Minecraft> client) {
        scene(name, scale, prepare, eye, target, client, 40);
    }

    /** 一张：开着界面拍，先把界面缩放调到 scale；拍之前打一行准星下提示的排版 */
    private static void scene(String name, int scale, Consumer<ServerPlayer> prepare, Vec3 eye, Vec3 target, Consumer<Minecraft> client, int delay) {
        DevShowroom.scenes.add(new DevShowroom.SceneShot(name, prepare, eye, target, delay, mc -> {
            mc.options.hideGui = false;
            mc.getToasts().clear();
            if (mc.options.guiScale().get() != scale) {
                mc.options.guiScale().set(scale);
                mc.resizeDisplay();
            }
            if (client != null) {
                client.accept(mc);
            }
        }, mc -> {
            EssentialCraft5.LOGGER.info("[showroom] builder hud {}: {}", name, BuilderHud.showroomLayout());
            return true;
        }));
    }

    /** 拍完：创造模式、界面缩放放回 3、每刻格数覆盖值置回 */
    static void clientFinish(Minecraft mc) {
        ECBuilder.Config.setTestOverrides(null, null, null);
        mc.setScreen(null);
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), Integer.getInteger("essentialcraft5.showroom.width", 1280),
                Integer.getInteger("essentialcraft5.showroom.height", 720));
        mc.options.guiScale().set(3);
        mc.resizeDisplay();
    }

    /** 服务端：回创造模式 */
    static void serverFinish(ServerPlayer player) {
        player.setGameMode(GameType.CREATIVE);
    }
}
