package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.ForceDomeBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.blockentity.RadiatingChamberBlockEntity;
import com.flameforge.essentialcraft5.client.CurioSealClient;
import com.flameforge.essentialcraft5.client.screen.ComputerTerminalScreen;
import com.flameforge.essentialcraft5.entity.HologramEntity;
import com.flameforge.essentialcraft5.entity.InquisitoriumSentinelEntity;
import com.flameforge.essentialcraft5.item.ComputerArmorItem;
import com.flameforge.essentialcraft5.item.GlitchyCoreItem;
import com.flameforge.essentialcraft5.item.InquisitoriumArmorItem;
import com.flameforge.essentialcraft5.item.InquisitoriumCoordinatesItem;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.module.ArmorModules;
import com.flameforge.essentialcraft5.item.module.ModuleCache;
import com.flameforge.essentialcraft5.item.module.ModuleType;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket.FieldState;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket.ModulePanel;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket.ModuleRow;
import com.flameforge.essentialcraft5.registry.ECCombatModules;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECForceDome;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECModules;
import com.flameforge.essentialcraft5.registry.ECRadiatingChamber;
import com.flameforge.essentialcraft5.registry.ECSurvivalModules;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * 展示厅的第十批 10c 部分（审判庭、终端与模块、力场穹顶，只在 -PecShowroom10c 时跑；batch10 §5.14 第 6 条）。截图名都带 {@code c10_}，不覆盖以前批次的。
 * <p>
 * <b>场景</b>（04_scene_c10_*，镜头从南边朝北拍）：五只 NoAI 的审判庭哨兵（休息 + 加减乘除四招）一张全景、休息那只和带回声那只各一张近景、开着 HUD 的紫色血条；
 * 草地上的审判庭信标、力场穹顶；手里拿着审判庭坐标时动作栏的方位行（坐标的维度写成主世界）；成形的 3 档灵魂五芒星；
 * 穿审判庭套装的盔甲架；三只挂着锚定（相位锚的效果）的末影人。
 * <p>
 * <b>界面</b>（02_gui_c10_*）：三种字色的故障核心提示（冰冻 / 纯净 / 混沌）；辐射仓解码中、平衡值没对准、定位中三张；坐标、信标、芯片、空白模块、六块模块、
 * 装了两块模块的宅男头盔、审判庭胸甲的提示框；宅男终端「模块」页（缩放 3、2 各一张，另一张假鼠标压在冷却中的状态字上）；工作中的力场穹顶界面；本批物品放大一排。
 * 手册页见 {@link DevShowroom} 的 {@code modulesGuidePages}。
 * <p>
 * 辐射仓三张用测试覆盖值：找坐标的步长临时改小（定位多停几百刻，拍得到「定位中」），拍完撤掉。终端的数据走 {@link CurioSealClient#setShowroomTerminal}，只改本客户端画面。
 */
public final class DevShowroomModules {

    private DevShowroomModules() {
    }

    /** 本类占几个场景位 */
    static final int SCENE_SLOTS = 6;
    /** 解码那几张的目标平衡值（仓里调成同一个数） */
    private static final float DECODE_TARGET = 0.73F;

    /** 场景里放出来的哨兵（服务端线程读写；场景拍完全部删掉） */
    private static final List<InquisitoriumSentinelEntity> SENTINELS = new ArrayList<>();
    /** 盔甲架、末影人（同上，场景拍完删掉） */
    private static final List<Entity> EXTRAS = new ArrayList<>();
    @Nullable
    private static BlockPos chamber;
    @Nullable
    private static BlockPos dome;

    // ================================================================ 入口

    static void build(ServerLevel level, ServerPlayer player, IntFunction<BlockPos> sceneBase, int firstIndex) {
        buildSentinelScene(sceneBase.apply(firstIndex));
        buildBlocksScene(level, sceneBase.apply(firstIndex + 1));
        buildHeadingScene(sceneBase.apply(firstIndex + 2));
        buildPentacleScene(level, player, sceneBase.apply(firstIndex + 3));
        buildArmorAndAnchorScene(sceneBase.apply(firstIndex + 5));
        BlockPos machines = sceneBase.apply(firstIndex + 4);
        chamber = machines.offset(1, 0, -2);
        dome = machines.offset(5, 0, -2);
        level.setBlockAndUpdate(chamber, ECRadiatingChamber.RADIATING_CHAMBER.get().defaultBlockState());
        level.setBlockAndUpdate(dome, ECForceDome.FORCE_DOME.get().defaultBlockState());
        ECForceDome.FORCE_DOME.get().setPlacedBy(level, dome, level.getBlockState(dome), player, new ItemStack(ECForceDome.FORCE_DOME.get()));
        buildScreens();
    }

    /** 客户端：场景阶段结束时把镜头放回（DevShowroom 调） */
    static void clientFinish(Minecraft mc) {
        mc.options.setCameraType(CameraType.FIRST_PERSON);
    }

    /** 服务端：场景拍完删掉哨兵（DevShowroom.finishScenes 调） */
    static void finish(ServerPlayer player) {
        for (InquisitoriumSentinelEntity sentinel : SENTINELS) {
            if (!sentinel.isRemoved()) {
                sentinel.discard();
            }
        }
        SENTINELS.clear();
        for (Entity entity : EXTRAS) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
        EXTRAS.clear();
        hold(player, ItemStack.EMPTY);
    }

    // ================================================================ 小工具

    private static void scene(String name, @Nullable Consumer<ServerPlayer> prepare, Vec3 eye, Vec3 target, int delay,
                              @Nullable Consumer<Minecraft> client) {
        DevShowroom.scenes.add(new DevShowroom.SceneShot(name, prepare, eye, target, delay, client, null));
    }

    private static Vec3 at(BlockPos base, double dx, double dy, double dz) {
        return DevShowroom.at(base, dx, dy, dz);
    }

    private static void easy(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server != null && server.getWorldData().getDifficulty() != Difficulty.EASY) {
            server.setDifficulty(Difficulty.EASY, true);
        }
    }

    private static void hold(ServerPlayer player, ItemStack stack) {
        player.getInventory().setItem(player.getInventory().selected, stack);
    }

    /** NoAI 的审判庭哨兵，摆好招式（{@link HologramEntity#setTestState}）；echo 不为 null 时摆上这一轮的回声 */
    private static void sentinel(ServerLevel level, Vec3 at, int attack, @Nullable Integer echo) {
        InquisitoriumSentinelEntity sentinel = ECInquisitorium.INQUISITORIUM_SENTINEL.get().create(level);
        if (sentinel == null) {
            EssentialCraft5.LOGGER.warn("[showroom] 造不出审判庭哨兵");
            return;
        }
        sentinel.moveTo(at.x, at.y, at.z, 0F, 0F);
        sentinel.setYHeadRot(0F);
        sentinel.setYBodyRot(0F);
        sentinel.setNoAi(true);
        sentinel.applyConfiguredHealth();
        level.addFreshEntity(sentinel);
        sentinel.setTestState(attack, 50, 100, null);
        if (echo != null) {
            sentinel.setTestEcho(echo);
        }
        SENTINELS.add(sentinel);
    }

    private static ItemStack core(float target) {
        ItemStack stack = new ItemStack(ECMaterials.GLITCHY_CORE.get());
        GlitchyCoreItem.setTargetBalance(stack, target);
        return stack;
    }

    // ================================================================ 场景

    /** 五只哨兵排一排（休息、加、减、乘、除），第六只带回声（这一招乘法，回声是上一招的加法）；最后只留休息那只、开 HUD 拍紫色血条 */
    private static void buildSentinelScene(BlockPos base) {
        int[] states = {HologramEntity.REST, HologramEntity.ADDITION, HologramEntity.SUBTRACTION, HologramEntity.MULTIPLICATION,
                HologramEntity.DIVISION};
        Consumer<ServerPlayer> spawn = p -> {
            easy(p);
            for (int i = 0; i < states.length; i++) {
                sentinel(p.serverLevel(), at(base, 0.5 + i * 4, 0, -4), states[i], null);
            }
            sentinel(p.serverLevel(), at(base, 0.5 + states.length * 4, 0, -4), HologramEntity.MULTIPLICATION, HologramEntity.ADDITION);
        };
        scene("c10_sentinel_lineup", spawn, at(base, 10.5, 4.8, 7.5), at(base, 10.5, 1.4, -4), DevShowroom.FIRST_SCENE_WAIT, null);
        scene("c10_sentinel_rest", null, at(base, 0.5, 2.2, 0.2), at(base, 0.5, 1.9, -4), 15, null);
        scene("c10_sentinel_attack_division", null, at(base, 16.5, 2.2, 0.2), at(base, 16.5, 1.9, -4), 15, null);
        scene("c10_sentinel_echo", null, at(base, 20.5, 2.4, 0.4), at(base, 20.5, 2.2, -4), 15, null);
        Consumer<ServerPlayer> keepOne = p -> {
            for (int i = 1; i < SENTINELS.size(); i++) {
                SENTINELS.get(i).discard();
            }
            if (SENTINELS.size() > 1) {
                SENTINELS.subList(1, SENTINELS.size()).clear();
            }
        };
        scene("c10_sentinel_bossbar", keepOne, at(base, 0.5, 2.6, 4), at(base, 0.5, 1.8, -4), 20, mc -> mc.options.hideGui = false);
    }

    /** 草地上的审判庭信标（左）和力场穹顶（右）：一张两个、各一张近景 */
    private static void buildBlocksScene(ServerLevel level, BlockPos base) {
        level.setBlockAndUpdate(base.offset(1, 0, -2), ECInquisitorium.INQUISITORIUM_BEACON.get().defaultBlockState());
        level.setBlockAndUpdate(base.offset(4, 0, -2), ECForceDome.FORCE_DOME.get().defaultBlockState());
        // 哨兵拍完就删：留着的那只的血条会一直挂在后面几张开 HUD 的截图顶上
        scene("c10_blocks", DevShowroomModules::finish, at(base, 2.5, 2.4, 2.5), at(base, 2.5, 0.4, -2), 30, null);
        scene("c10_beacon_close", null, at(base, 1.9, 1.3, -0.4), at(base, 1.5, 0.4, -2), 15, null);
        scene("c10_force_dome_close", null, at(base, 4.9, 1.3, -0.4), at(base, 4.5, 0.4, -2), 15, null);
    }

    /** 手里拿着审判庭坐标（维度写成主世界、在东北方约 300 格）：开 HUD 拍动作栏上的方位行 */
    private static void buildHeadingScene(BlockPos base) {
        Consumer<ServerPlayer> give = p -> hold(p, InquisitoriumCoordinatesItem.create(
                new InquisitoriumCoordinatesItem.Target(ResourceLocation.withDefaultNamespace("overworld"), base.getX() + 212, base.getZ() - 212)));
        scene("c10_coordinates_heading", give, at(base, 0.5, 1.62, 2.5), at(base, 0.5, 1.4, -6), 45, mc -> {
            mc.options.hideGui = false;
            mc.getToasts().clear();
        });
    }

    /** 成形的 3 档灵魂五芒星（审判庭一章的最后一步） */
    private static void buildPentacleScene(ServerLevel level, ServerPlayer player, BlockPos base) {
        BlockPos pentacle = base.offset(3, 0, -3);
        DevShowroomDemon.buildPentacleForShowroom(level, player, pentacle, 3);
        scene("c10_pentacle_tier3", null, at(base, 3.5, 7, 6.5), at(base, 3.5, 1, -3), 40, null);
    }

    /** 左边盔甲架穿审判庭套装（第十批起四件都能做）；右边三只 NoAI 的末影人挂着锚定（相位锚给的效果，看紫色粒子） */
    private static void buildArmorAndAnchorScene(BlockPos base) {
        Consumer<ServerPlayer> standUp = p -> {
            ArmorStand stand = EntityType.ARMOR_STAND.create(p.serverLevel());
            if (stand == null) {
                return;
            }
            stand.moveTo(base.getX() + 1.5, base.getY(), base.getZ() - 2.5, 0F, 0F);
            for (RegistryObject<InquisitoriumArmorItem> piece : ECInquisitorium.all()) {
                stand.setItemSlot(piece.get().getEquipmentSlot(), new ItemStack(piece.get()));
            }
            p.serverLevel().addFreshEntity(stand);
            EXTRAS.add(stand);
        };
        scene("c10_inquisitorium_armor_stand", standUp, at(base, 1.5, 1.7, 0.9), at(base, 1.5, 1.0, -2.5), 20, null);
        Consumer<ServerPlayer> anchored = p -> {
            for (int i = 0; i < 3; i++) {
                EnderMan man = EntityType.ENDERMAN.create(p.serverLevel());
                if (man == null) {
                    return;
                }
                man.moveTo(base.getX() + 8.5 + i * 2.5, base.getY(), base.getZ() - 3.5, 0F, 0F);
                man.setYHeadRot(0F);
                man.setYBodyRot(0F);
                man.setNoAi(true);
                man.setSilent(true);
                p.serverLevel().addFreshEntity(man);
                man.addEffect(new MobEffectInstance(ECEffects.ANCHORED.get(), 20 * 60, 0, false, true));
                EXTRAS.add(man);
            }
        };
        scene("c10_phase_anchor_endermen", anchored, at(base, 11, 2.4, 4), at(base, 11, 1.6, -3.5), 40, null);
    }

    // ================================================================ 界面

    private static void tooltip(String name, Consumer<ServerPlayer> prepare, Supplier<ItemStack> stack) {
        DevShowroom.screens.add(new DevShowroom.ScreenShot(name, prepare, p -> Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.getToasts().clear();
            mc.setScreen(new TooltipShot(stack.get()));
        })));
    }

    private static void buildScreens() {
        tooltip("c10_glitchy_core_frozen", p -> { }, () -> core(0.35F));
        tooltip("c10_glitchy_core_pure", p -> { }, () -> core(1.00F));
        tooltip("c10_glitchy_core_chaos", p -> { }, () -> core(1.62F));
        chamberScreens();
        tooltip("c10_coordinates_tooltip", p -> { }, () -> InquisitoriumCoordinatesItem.create(
                new InquisitoriumCoordinatesItem.Target(ECHoanna.ID, 1873, -612)));
        tooltip("c10_beacon_tooltip", p -> { }, () -> new ItemStack(ECInquisitorium.INQUISITORIUM_BEACON.get()));
        tooltip("c10_chip_tooltip", p -> { }, () -> new ItemStack(ECInquisitorium.INQUISITORIUM_CHIP.get()));
        tooltip("c10_inquisitorium_chestplate_tooltip", p -> { }, () -> new ItemStack(ECInquisitorium.INQUISITORIUM_CHESTPLATE.get()));
        tooltip("c10_blank_module_tooltip", p -> { }, () -> new ItemStack(ECModules.BLANK_MODULE.get()));
        moduleTooltip("c10_rewind_module_tooltip", ECSurvivalModules.REWIND_MODULE);
        moduleTooltip("c10_matrix_filter_module_tooltip", ECSurvivalModules.MATRIX_FILTER_MODULE);
        moduleTooltip("c10_matrix_shunt_module_tooltip", ECSurvivalModules.MATRIX_SHUNT_MODULE);
        moduleTooltip("c10_accumulator_module_tooltip", ECCombatModules.ACCUMULATOR_MODULE);
        moduleTooltip("c10_shred_module_tooltip", ECCombatModules.SHRED_MODULE);
        moduleTooltip("c10_phase_anchor_module_tooltip", ECCombatModules.PHASE_ANCHOR_MODULE);
        tooltip("c10_helmet_two_modules_tooltip", p -> { }, DevShowroomModules::helmetWithTwo);
        terminalScreens();
        domeScreen();
        DevShowroom.screens.add(new DevShowroom.ScreenShot("c10_items_wall", p -> { }, p -> Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.getToasts().clear();
            mc.setScreen(new ItemWall(List.of(core(1.0F), new ItemStack(ECInquisitorium.INQUISITORIUM_COORDINATES.get()),
                    new ItemStack(ECInquisitorium.INQUISITORIUM_CHIP.get()), new ItemStack(ECInquisitorium.INQUISITORIUM_BEACON.get()),
                    new ItemStack(ECForceDome.FORCE_DOME.get()), new ItemStack(ECModules.BLANK_MODULE.get()),
                    new ItemStack(ECSurvivalModules.REWIND_MODULE.get()), new ItemStack(ECSurvivalModules.MATRIX_FILTER_MODULE.get()),
                    new ItemStack(ECSurvivalModules.MATRIX_SHUNT_MODULE.get()), new ItemStack(ECCombatModules.ACCUMULATOR_MODULE.get()),
                    new ItemStack(ECCombatModules.SHRED_MODULE.get()), new ItemStack(ECCombatModules.PHASE_ANCHOR_MODULE.get()))));
        })));
    }

    private static void moduleTooltip(String name, RegistryObject<? extends net.minecraft.world.item.Item> item) {
        tooltip(name, p -> { }, () -> new ItemStack(item.get()));
    }

    /** 宅男头盔装上回溯和矩阵过滤（满电，提示框里「模块槽：2 / 2」和每块一行） */
    private static ItemStack helmetWithTwo() {
        ItemStack helmet = MRUStaffItem.full(ECComputer.COMPUTER_HELMET.get());
        ItemStack one = ArmorModules.install(helmet, ECSurvivalModules.REWIND);
        ItemStack two = one.isEmpty() ? ItemStack.EMPTY : ArmorModules.install(one, ECSurvivalModules.MATRIX_FILTER);
        if (two.isEmpty()) {
            EssentialCraft5.LOGGER.warn("[showroom] 宅男头盔装不上两块模块");
            return helmet;
        }
        return two;
    }

    // ---------------- 辐射仓解码 ----------------

    private static void chamberScreens() {
        // 解码中：手动推到 340 刻，再走 35 刻左右拍（1000 刻满，约 37%）
        DevShowroom.screens.add(new DevShowroom.ScreenShot("c10_radiating_decoding", p -> {
            RadiatingChamberBlockEntity be = chamberBe(p);
            if (be == null) {
                return;
            }
            standAt(p, chamber);
            be.getInventory().setStackInSlot(RadiatingChamberBlockEntity.SLOT_TOP, core(DECODE_TARGET));
            be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
            be.getMRUStorage().setBalance(DECODE_TARGET);
            for (int i = 0; i < 340; i++) {
                MRUBlockEntity.tickServer(be.getLevel(), be.getBlockPos(), be.getBlockState(), be);
            }
            EssentialCraft5.LOGGER.info("[showroom] 辐射仓解码：{}，进度 {}", be.getStatus(), be.getDecodeProgress());
        }, DevShowroomModules::openChamber));
        // 平衡值没对准：仓里调成 0.95，停住、进度保留
        DevShowroom.screens.add(new DevShowroom.ScreenShot("c10_radiating_decode_balance", p -> {
            RadiatingChamberBlockEntity be = chamberBe(p);
            if (be != null) {
                be.getMRUStorage().setBalance(0.95F);
            }
        }, DevShowroomModules::openChamber));
        // 定位中：找坐标的步长临时改成 8（93 × 93 个点变成约 750 × 750 个，定位要几百刻），平衡值调回去、手动推满进度
        DevShowroom.screens.add(new DevShowroom.ScreenShot("c10_radiating_locating", p -> {
            RadiatingChamberBlockEntity be = chamberBe(p);
            if (be == null) {
                return;
            }
            ECInquisitorium.Rules.Values d = ECInquisitorium.Rules.DEFAULTS;
            ECInquisitorium.Rules.setTestOverrides(new ECInquisitorium.Rules.Values(d.maxHealth(), d.echoRatio(), d.matrixDamagePerHit(), d.decodeMru(),
                    d.decodeTicks(), d.decodeTolerance(), d.balanceMin(), d.balanceMax(), d.searchRadius(), 8, d.fallbackDistance(), d.arriveRadius(),
                    d.summonDimensions()));
            be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
            be.getMRUStorage().setBalance(DECODE_TARGET);
            for (int i = 0; i < 1200 && be.getStatus() != RadiatingChamberBlockEntity.Status.LOCATING; i++) {
                MRUBlockEntity.tickServer(be.getLevel(), be.getBlockPos(), be.getBlockState(), be);
            }
            EssentialCraft5.LOGGER.info("[showroom] 辐射仓定位：{}", be.getStatus());
        }, DevShowroomModules::openChamber));
        DevShowroom.screens.add(new DevShowroom.ScreenShot("c10_radiating_done", p -> ECInquisitorium.Rules.setTestOverrides(null), p -> {
            RadiatingChamberBlockEntity be = chamberBe(p);
            if (be != null) {
                EssentialCraft5.LOGGER.info("[showroom] 辐射仓定位以后：{}，输出格 {}", be.getStatus(),
                        be.getInventory().getStackInSlot(RadiatingChamberBlockEntity.SLOT_OUTPUT));
            }
            openChamber(p);
        }));
    }

    @Nullable
    private static RadiatingChamberBlockEntity chamberBe(ServerPlayer player) {
        if (chamber != null && player.serverLevel().getBlockEntity(chamber) instanceof RadiatingChamberBlockEntity be) {
            return be;
        }
        EssentialCraft5.LOGGER.warn("[showroom] 找不到展示用的辐射仓");
        return null;
    }

    private static void openChamber(ServerPlayer player) {
        RadiatingChamberBlockEntity be = chamberBe(player);
        if (be != null) {
            NetworkHooks.openScreen(player, be, be.getBlockPos());
        }
    }

    private static void standAt(ServerPlayer player, @Nullable BlockPos machine) {
        if (machine != null) {
            player.teleportTo(machine.getX() + 0.5, machine.getY(), machine.getZ() + 2.5);
        }
    }

    // ---------------- 宅男终端「模块」页 ----------------

    /** 一份带名单、带模块段的终端数据：头盔回溯冷却 3 分钟、矩阵过滤生效；胸甲累加器关着、削减生效；护腿相位锚缺电；靴子相位锚重复 */
    private static S2CComputerTerminalPacket terminalData() {
        List<ModuleRow> rows = List.of(
                new ModuleRow((byte) 0, (byte) 0, id("rewind_module"), (byte) ModuleCache.Status.COOLDOWN.ordinal(), true, 3600),
                new ModuleRow((byte) 0, (byte) 1, id("matrix_filter_module"), (byte) ModuleCache.Status.ACTIVE.ordinal(), true, 0),
                new ModuleRow((byte) 1, (byte) 0, id("accumulator_module"), (byte) ModuleCache.Status.OFF.ordinal(), false, 0),
                new ModuleRow((byte) 1, (byte) 1, id("shred_module"), (byte) ModuleCache.Status.ACTIVE.ordinal(), true, 0),
                new ModuleRow((byte) 2, (byte) 0, id("phase_anchor_module"), (byte) ModuleCache.Status.NO_POWER.ordinal(), true, 0),
                new ModuleRow((byte) 3, (byte) 0, id("phase_anchor_module"), (byte) ModuleCache.Status.DUPLICATE.ordinal(), true, 0));
        return new S2CComputerTerminalPacket((byte) FieldState.STANDBY.ordinal(), (byte) 0, 0, 735_000L, 0, true, List.of(),
                new ModulePanel(true, true, new byte[]{2, 2, 1, 1}, 200, rows));
    }

    private static ResourceLocation id(String path) {
        return EssentialCraft5.id(path);
    }

    private static void terminalScreens() {
        DevShowroom.screens.add(new DevShowroom.ScreenShot("c10_terminal_modules_s3", DevShowroomModules::dress,
                p -> Minecraft.getInstance().execute(() -> openTerminal(3, false))));
        DevShowroom.screens.add(new DevShowroom.ScreenShot("c10_terminal_modules_s2", p -> { },
                p -> Minecraft.getInstance().execute(() -> openTerminal(2, false))));
        DevShowroom.screens.add(new DevShowroom.ScreenShot("c10_terminal_modules_hover", p -> { },
                p -> Minecraft.getInstance().execute(() -> openTerminal(3, true))));
        DevShowroom.screens.add(new DevShowroom.ScreenShot("c10_terminal_done", DevShowroomModules::undress, p -> Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            CurioSealClient.setShowroomTerminal(null);
            ComputerTerminalScreen.setPageForNextOpen(false);
            if (mc.options.guiScale().get() != 3) {
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
            }
            mc.setScreen(null);
        })));
    }

    /** 客户端：换界面缩放、塞终端数据、开「模块」页；hover 时用假鼠标压在头盔第 0 格（回溯，冷却中）的状态字上 */
    private static void openTerminal(int scale, boolean hover) {
        Minecraft mc = Minecraft.getInstance();
        mc.getToasts().clear();
        if (mc.options.guiScale().get() != scale) {
            mc.options.guiScale().set(scale);
            mc.resizeDisplay();
        }
        CurioSealClient.setShowroomTerminal(terminalData());
        ComputerTerminalScreen.setPageForNextOpen(true);
        mc.setScreen(hover ? new HoverTerminal() : new ComputerTerminalScreen());
    }

    /** 宅男终端，鼠标换成固定的一点（不动真鼠标）：模块页第 2 行（头盔第 0 格）的状态字。面板 236 × 222、列表从 53 起每行 13、状态列在 96（见 ComputerTerminalScreen） */
    private static final class HoverTerminal extends Screen {
        private final ComputerTerminalScreen inner = new ComputerTerminalScreen();

        HoverTerminal() {
            super(Component.empty());
        }

        @Override
        protected void init() {
            inner.init(Minecraft.getInstance(), width, height);
        }

        @Override
        public void tick() {
            inner.tick();
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int left = (width - 236) / 2;
            int top = Math.max(16, (height - 222) / 2);
            inner.render(graphics, left + 96 + 6, top + 53 + 13 + 5, partialTick);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    /** 穿满电的宅男全套（拍终端时；头盔要戴着界面才开得了） */
    private static void dress(ServerPlayer player) {
        for (RegistryObject<? extends ComputerArmorItem> piece : ECComputer.armor()) {
            ComputerArmorItem item = piece.get();
            player.setItemSlot(item.getEquipmentSlot(), MRUStaffItem.full(item));
        }
    }

    private static void undress(ServerPlayer player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    // ---------------- 力场穹顶 ----------------

    /** 穹顶主人 = 展示玩家（单人里就是房主，在线、能动这片地），MRU 加满、马上判一次：「工作中」，区块数都是可拦 */
    private static void domeScreen() {
        DevShowroom.screens.add(new DevShowroom.ScreenShot("c10_force_dome_gui", p -> {
            if (dome == null || !(p.serverLevel().getBlockEntity(dome) instanceof ForceDomeBlockEntity be)) {
                EssentialCraft5.LOGGER.warn("[showroom] 找不到展示用的力场穹顶");
                return;
            }
            standAt(p, dome);
            be.setOwner(p.getUUID(), p.getGameProfile().getName());
            be.markPlaced(p.serverLevel().getGameTime());
            be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
            be.evaluateNow();
            EssentialCraft5.LOGGER.info("[showroom] 力场穹顶：{}，可拦 {} / 共 {} 块", be.status(), be.blockableChunks(), be.coveredChunks());
        }, p -> {
            if (dome != null && p.serverLevel().getBlockEntity(dome) instanceof ForceDomeBlockEntity be) {
                NetworkHooks.openScreen(p, be, dome);
            }
        }));
    }

    // ================================================================ 画面

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

    /** 一排物品，放大两倍画（看贴图） */
    private static final class ItemWall extends Screen {
        private final List<ItemStack> stacks;

        ItemWall(List<ItemStack> stacks) {
            super(Component.empty());
            this.stacks = stacks;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            graphics.pose().pushPose();
            graphics.pose().scale(2F, 2F, 1F);
            for (int i = 0; i < stacks.size(); i++) {
                graphics.renderItem(stacks.get(i), 8 + (i % 6) * 20, 8 + (i / 6) * 22);
            }
            graphics.pose().popPose();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
