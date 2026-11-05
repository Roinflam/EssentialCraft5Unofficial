package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity.CondenseValues;
import com.flameforge.essentialcraft5.blockentity.EnrichmentStateCheckerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MatrixCrucibleBlockEntity;
import com.flameforge.essentialcraft5.client.CurioSealClient;
import com.flameforge.essentialcraft5.client.screen.ComputerTerminalScreen;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.entity.MatrixEchoEntity;
import com.flameforge.essentialcraft5.item.ComputerSealField;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.SoulStoneItem;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.matrix.MatrixCodex;
import com.flameforge.essentialcraft5.matrix.MatrixRank;
import com.flameforge.essentialcraft5.matrix.MatrixTrials;
import com.flameforge.essentialcraft5.network.S2CComputerTerminalPacket;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECAlloys;
import com.flameforge.essentialcraft5.registry.ECBootstrap;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECMatrix;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import com.flameforge.essentialcraft5.registry.ECStaffs;
import com.flameforge.essentialcraft5.registry.ECVoid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 展示厅的第十三批 13a 部分（矩阵阶、同调、凝结，只在 -PecShowroom13a 时跑；batch13.md §12.3）。截图名都以 {@code b13a_} 开头，存成 {@code ec5_b13a_*.png}。
 * <p>
 * <b>场景</b>（镜头从南边朝北拍）：
 * <ul>
 *   <li>{@code b13a_crucible*}：一台淬炼台的近景；</li>
 *   <li>{@code b13a_trial_*}：用真数据开一阶试炼（图谱、祭品、UBMRU 现场补齐），回响不带 AI、站着不动。展示玩家的矩阵属性依次换成
 *       中性、混沌、冰冻、魔力、暗影，每换一次重开一次，看回响的五种着色（生成点是随机的，两只都放出来以后挪到台子左右、面朝镜头再拍，
 *       {@link #echoesInFrame}）；最后一张开着 HUD 拍首领血条，再打倒回响拍成功的标题；</li>
 *   <li>{@code b13a_channel}：试炼当成和平难度（{@link MatrixRank#setTestPeaceful}），拍引导的血条（先清掉上一张的成功标题和聊天）；</li>
 *   <li>{@code b13a_condense*}：5 × 3 × 5 的富集仓，底面 8 块护板、东墙稳定器、底面正中状态检测器，仓里一团 1.5 万的聚集体，
 *       要攒的秒数临时改成 {@link #CONDENSE_SECONDS}，中途开状态检测器拍凝结进度条。</li>
 * </ul>
 * <b>界面</b>（场景拍完世界已经切成和平难度，这几张按非和平画：{@link MatrixRank#setTestPeaceful} 设 false，最后一张撤掉）：
 * 淬炼台两张（零阶什么都没备、一阶条件全齐）；灵魂石（三阶中性、六阶混沌）、神圣重锤（七阶混沌）、宅男胸甲（0 阶、2 阶一半、2 阶满、5 阶）、
 * 3 阶宅男头盔（看光泽）、凝结矩阵的提示框；穿着四件 5 阶宅男套装开终端「模块」页（数据照真的算，见 {@link ComputerSealField#packet}）。
 * 手册页见 {@link DevShowroom} 的 {@code batch13aGuidePages}。
 */
public final class DevShowroomBatch13a {

    private DevShowroomBatch13a() {
    }

    /** 凝结要攒的秒数（默认 900，拍照时改短，中途开检测器能看到进度条走了一截） */
    private static final int CONDENSE_SECONDS = 60;
    /** 一阶试炼要的图谱（四种，只要种数） */
    private static final List<EntityType<?>> CODEX = List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER);

    @Nullable
    private static BlockPos crucible;
    @Nullable
    private static BlockPos controller;
    @Nullable
    private static BlockPos checker;
    /** 场景里生成的聚集体（场景拍完删掉） */
    private static final List<MRUPresenceEntity> SPAWNED = new ArrayList<>();
    /** 着色场景：两只回响挪到镜头前以后过了几刻；-1 = 还没挪好（每次开试炼置 -1，服务端挪好时置 0，客户端刻里往上数） */
    private static final AtomicInteger ECHOES_PLACED = new AtomicInteger(-1);
    /** 挪好以后再等几刻才拍：服务端隔几刻才把新位置发给客户端，客户端还要插值 3 刻 */
    private static final int ECHO_SETTLE_TICKS = 8;

    // ================================================================ 入口

    static void build(ServerLevel level, ServerPlayer player, IntFunction<BlockPos> sceneBase, int firstIndex) {
        buildTrialScene(level, player, sceneBase.apply(firstIndex));
        buildCondenseScene(level, sceneBase.apply(firstIndex + 1));
        buildScreens();
    }

    /** 服务端：场景拍完撤掉各种覆盖值、结束试炼、删掉聚集体（DevShowroom.finishScenes 调；没跑过 build 时什么都不做） */
    static void finish(ServerPlayer player) {
        if (crucible == null) {
            return;
        }
        MatrixTrials.clearForTest(player.getUUID());
        MatrixTrials.clearTestOverrides();
        MatrixRank.clearTestOverrides();
        EnrichmentControllerBlockEntity.Config.setCondenseTestOverride(null);
        for (MRUPresenceEntity presence : SPAWNED) {
            if (!presence.isRemoved()) {
                presence.discard();
            }
        }
        SPAWNED.clear();
        hold(player, ItemStack.EMPTY);
    }

    // ================================================================ 小工具

    private static Vec3 at(BlockPos base, double dx, double dy, double dz) {
        return DevShowroom.at(base, dx, dy, dz);
    }

    private static void scene(String name, @Nullable Consumer<ServerPlayer> prepare, Vec3 eye, Vec3 target, int delay,
                              @Nullable Consumer<Minecraft> client) {
        scene(name, prepare, eye, target, delay, client, null);
    }

    private static void scene(String name, @Nullable Consumer<ServerPlayer> prepare, Vec3 eye, Vec3 target, int delay,
                              @Nullable Consumer<Minecraft> client, @Nullable Predicate<Minecraft> ready) {
        DevShowroom.scenes.add(new DevShowroom.SceneShot(name, prepare, eye, target, delay, client, ready));
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

    private static void placeAs(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state);
        state.getBlock().setPlacedBy(level, pos, state, player, new ItemStack(state.getBlock()));
    }

    @Nullable
    private static MatrixCrucibleBlockEntity crucibleBe(ServerPlayer player) {
        if (crucible != null && player.level().getBlockEntity(crucible) instanceof MatrixCrucibleBlockEntity be) {
            return be;
        }
        EssentialCraft5.LOGGER.warn("[showroom] 淬炼台没放上");
        return null;
    }

    private static ItemStack boundStone(ServerPlayer player) {
        ItemStack stone = new ItemStack(ECBootstrap.SOUL_STONE.get());
        SoulStoneItem.bind(stone, player);
        return stone;
    }

    /**
     * 玩家的矩阵状态：阶、属性、矩阵容量（开着换入）、UBMRU；codex 为 true 时图谱补上一阶要的四种，否则清空。
     * 改的都是展示玩家自己的数据，玩家数据每 10 刻同步一次，界面开出来时客户端已经收到
     */
    private static void matrix(ServerPlayer player, int rank, int type, int pool, int ubmru, boolean codex) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            EssentialCraft5.LOGGER.warn("[showroom] 展示玩家没有玩家数据");
            return;
        }
        MatrixRank.set(player, rank);
        data.setMatrixType(type);
        data.setMatrixPool(pool, true);
        data.setMatrixPoolAbsorb(true);
        data.setUBMRU(ubmru);
        data.setMatrixTrialCooldown(0);
        data.setMatrixTrialReserve(0);
        MatrixCodex.clear(player);
        if (codex) {
            for (EntityType<?> kind : CODEX) {
                MatrixCodex.add(player, kind, false);
            }
        }
        MatrixCodex.resend(player);
    }

    /** 淬炼台：一阶的祭品（四种魔晶各 4 个），MRU 加满 */
    private static void fillCrucible(MatrixCrucibleBlockEntity be, boolean offerings) {
        Item[] drops = {ECMaterials.FIRE_DROPS.get(), ECMaterials.WATER_DROPS.get(), ECMaterials.EARTH_DROPS.get(), ECMaterials.AIR_DROPS.get()};
        for (int i = 0; i < MatrixCrucibleBlockEntity.OFFERING_SLOTS; i++) {
            be.getInventory().setStackInSlot(MatrixCrucibleBlockEntity.FIRST_OFFERING + i, offerings ? new ItemStack(drops[i], 4) : ItemStack.EMPTY);
        }
        be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
    }

    private static void standAt(ServerPlayer player, BlockPos pos) {
        player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 2.5);
    }

    // ================================================================ 场景

    /** 一台淬炼台；试炼从这里开（展示玩家拿着自己的灵魂石站到台子南边 2 格） */
    private static void buildTrialScene(ServerLevel level, ServerPlayer player, BlockPos base) {
        BlockPos pos = base.offset(0, 0, -4);
        crucible = pos;
        placeAs(level, player, pos, ECMatrix.MATRIX_CRUCIBLE.get().defaultBlockState());
        Vec3 look = at(pos, 0.5, 0.8, 0.5);
        scene("b13a_crucible", null, at(pos, 2.6, 2.0, 3.0), look, DevShowroom.FIRST_SCENE_WAIT, null);
        scene("b13a_crucible_front", null, at(pos, 0.5, 1.7, 2.4), look, 15, null);

        // 回响五种着色：每张重开一次一阶试炼（回响一只 10 刻放一只，一阶两只）
        String[] names = {"neutral", "chaos", "frozen", "magic", "shade"};
        Vec3 eye = at(pos, 0.5, 2.6, 3.0);
        Vec3 wide = at(pos, 0.5, 0.8, -1.0);
        for (int type = 0; type < names.length; type++) {
            int matrixType = type;
            scene("b13a_trial_" + names[type], p -> startTrial(p, matrixType, false), eye, wide, 60, null,
                    DevShowroomBatch13a::echoesInFrame);
        }
        // 同一场开着 HUD：顶上的首领血条
        scene("b13a_trial_bossbar", null, eye, wide, 10, mc -> mc.options.hideGui = false);
        // 打倒回响：屏幕中间的「矩阵 · 一阶」标题
        scene("b13a_trial_success", DevShowroomBatch13a::killEchoes, eye, wide, 12, mc -> mc.options.hideGui = false);
        // 和平难度改成引导：站在台子 3 格内不动，血条写着还要站几秒。上一张的成功标题（约 3.5 秒）和矩阵受损的聊天还在屏幕上，先清掉
        scene("b13a_channel", p -> startTrial(p, 0, true), at(pos, 0.5, 2.2, 2.2), look, 40, mc -> {
            mc.options.hideGui = false;
            mc.gui.clear();
            mc.gui.getChat().clearMessages(false);
        });
    }

    /** 重开一阶试炼：零阶、属性 type、图谱和祭品补齐、身上 1 万 UBMRU；channel 为 true 时按和平难度走引导 */
    private static void startTrial(ServerPlayer player, int type, boolean channel) {
        easy(player);
        MatrixTrials.clearForTest(player.getUUID());
        ECHOES_PLACED.set(-1);
        MatrixRank.setTestPeaceful(channel);
        MatrixTrials.setTestOverrides(new int[]{3, 4}, null, null, null, true);
        MatrixCrucibleBlockEntity be = crucibleBe(player);
        if (be == null || crucible == null) {
            return;
        }
        matrix(player, 0, type, 0, 10_000, true);
        fillCrucible(be, true);
        hold(player, boundStone(player));
        standAt(player, crucible);
        MatrixTrials.Start start = MatrixTrials.tryStart(player, be);
        if (!start.started()) {
            EssentialCraft5.LOGGER.warn("[showroom] 一阶试炼没开起来：{} / {}", start.result(), start.blocker());
        }
    }

    /**
     * 着色场景的 ready：回响生成点在台子 3~4 格内随机、方向也随机，镜头却固定在台子南边往北看，放在南侧或两侧时会整只出画。
     * 等两只都放出来（10 刻一只），在服务端挪到台子左右各一只、面朝镜头，再等 {@value #ECHO_SETTLE_TICKS} 刻让客户端收到新位置。
     * 试炼没开起来就不等
     */
    private static boolean echoesInFrame(Minecraft mc) {
        if (ECHOES_PLACED.get() >= 0) {
            return ECHOES_PLACED.incrementAndGet() > ECHO_SETTLE_TICKS;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return true;
        }
        server.execute(() -> {
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (ECHOES_PLACED.get() < 0 && !players.isEmpty() && placeEchoes(players.get(0))) {
                ECHOES_PLACED.compareAndSet(-1, 0);
            }
        });
        return false;
    }

    /**
     * 台子周围活着的回响挪两只到台子左右各 1.4 格、面朝南边的镜头（那里站得下才挪）；卡住计数清零，免得试炼的「走不到就挪到试炼者身边」
     * 紧接着又把它挪走。还没放齐两只返回 false（下一刻再看），试炼没在跑返回 true（不再等）
     */
    private static boolean placeEchoes(ServerPlayer player) {
        if (crucible == null || MatrixTrials.trialOf(player.getUUID()) == null) {
            return true;
        }
        ServerLevel level = player.serverLevel();
        List<MatrixEchoEntity> echoes = level.getEntitiesOfClass(MatrixEchoEntity.class, new AABB(crucible).inflate(16), MatrixEchoEntity::isAlive);
        if (echoes.size() < 2) {
            return false;
        }
        double[] offsets = {-1.4D, 1.4D};
        for (int i = 0; i < offsets.length; i++) {
            MatrixEchoEntity echo = echoes.get(i);
            double x = crucible.getX() + 0.5D + offsets[i];
            double y = crucible.getY();
            double z = crucible.getZ() + 0.5D;
            if (!level.noCollision(echo, echo.getType().getAABB(x, y, z))) {
                EssentialCraft5.LOGGER.warn("[showroom] 台子{}边站不下回响，留在原处", offsets[i] < 0 ? "西" : "东");
                continue;
            }
            echo.moveTo(x, y, z, 0F, 0F);
            echo.setYHeadRot(0F);
            echo.setYBodyRot(0F);
            echo.setDeltaMovement(Vec3.ZERO);
            echo.setStuckStrikes(0);
        }
        return true;
    }

    /** 以试炼者的身份打倒台子周围的回响（一阶两只都放出来以后调） */
    private static void killEchoes(ServerPlayer player) {
        if (crucible == null) {
            return;
        }
        AABB box = new AABB(crucible).inflate(16);
        List<MatrixEchoEntity> echoes = player.serverLevel().getEntitiesOfClass(MatrixEchoEntity.class, box, echo -> echo.isAlive());
        if (echoes.isEmpty()) {
            EssentialCraft5.LOGGER.warn("[showroom] 台子周围没有回响，成功标题拍不到");
        }
        for (MatrixEchoEntity echo : echoes) {
            echo.hurt(player.damageSources().playerAttack(player), echo.getMaxHealth() * 10F);
        }
    }

    /**
     * 凝结：5 × 3 × 5 的强化石仓（北墙控制器、西墙存储方块、东墙稳定器、底面正中状态检测器），底面其余 8 格是护板（前 4 块魔力护板、后 4 块淡青护板），
     * 仓里存 6 万 MRU、平衡值 1.0。开拍时放进一团 1.5 万的聚集体
     */
    private static void buildCondenseScene(ServerLevel level, BlockPos base) {
        BlockPos min = base.offset(-2, 0, -8);
        BlockPos max = base.offset(2, 2, -4);
        Block stone = ECMaterials.FORTIFIED_STONE.get();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            boolean shell = pos.getX() == min.getX() || pos.getX() == max.getX() || pos.getY() == min.getY() || pos.getY() == max.getY()
                    || pos.getZ() == min.getZ() || pos.getZ() == max.getZ();
            if (shell) {
                level.setBlockAndUpdate(pos, stone.defaultBlockState());
            }
        }
        BlockPos middle = new BlockPos(base.getX(), min.getY(), base.getZ() - 6);
        int placed = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Block plating = placed < 4 ? ECAlloys.MAGIC_PLATING.get() : ECAlloys.PALE_PLATING.get();
                level.setBlockAndUpdate(middle.offset(dx, 0, dz), plating.defaultBlockState());
                placed++;
            }
        }
        // 南墙正中一格换成虚空玻璃当窗户（同坍缩那张，虚空玻璃本来就能当外壳）
        level.setBlockAndUpdate(new BlockPos(base.getX(), min.getY() + 1, max.getZ()), ECVoid.VOID_GLASS.get().defaultBlockState());
        controller = new BlockPos(base.getX(), min.getY() + 1, min.getZ());
        level.setBlockAndUpdate(controller, ECEnrichment.EC_CONTROLLER.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(min.getX(), min.getY() + 1, base.getZ() - 6), ECEnrichment.EC_HOLDING_CHAMBER.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(max.getX(), min.getY() + 1, base.getZ() - 6), ECPentacle.EC_BALANCER.get().defaultBlockState());
        checker = middle;
        level.setBlockAndUpdate(checker, ECEnrichment.EC_STATE_CHECKER.get().defaultBlockState());
        Vec3 center = Vec3.atCenterOf(middle.above());

        Consumer<ServerPlayer> start = p -> {
            // 前面引导那场还开着，走远了会按离开算失败，先收掉
            MatrixTrials.clearForTest(p.getUUID());
            EnrichmentControllerBlockEntity.Config.setCondenseTestOverride(new CondenseValues(true, 50_000, CONDENSE_SECONDS, 8, 15_000,
                    0.8D, 1.2D, 1, List.of()));
            if (controller == null || !(p.level().getBlockEntity(controller) instanceof EnrichmentControllerBlockEntity ec)) {
                EssentialCraft5.LOGGER.warn("[showroom] 富集仓控制器没放上");
                return;
            }
            EssentialCraft5.LOGGER.info("[showroom] 凝结仓外壳：{}，护板 {} 块", ec.checkNow(), ec.condenseCasing());
            ec.storage().setMRU(60_000);
            ec.storage().setBalance(1.0F);
            MRUPresenceEntity presence = MRUPresenceEntity.create(p.level(), center.x, center.y, center.z, 15_000, 1F, false, true);
            p.level().addFreshEntity(presence);
            SPAWNED.add(presence);
        };
        Vec3 eye = at(base, 0.5, 2.2, 0.5);
        Vec3 look = center;
        scene("b13a_condense", start, eye, look, DevShowroom.FIRST_SCENE_WAIT, null);
        // 攒到一小半时开状态检测器：凝结那一条进度条
        Consumer<ServerPlayer> openChecker = p -> {
            if (checker != null && p.level().getBlockEntity(checker) instanceof EnrichmentStateCheckerBlockEntity be) {
                NetworkHooks.openScreen(p, be, checker);
            } else {
                EssentialCraft5.LOGGER.warn("[showroom] 找不到状态检测器");
            }
        };
        scene("b13a_gui_condense_checker", openChecker, eye, look, 300, null);
        scene("b13a_condense_side", null, at(base, 3.5, 3.4, -1.5), look, 15, mc -> {
            if (mc.player != null) {
                mc.player.closeContainer();
            }
            mc.setScreen(null);
        });
    }

    // ================================================================ 界面

    private static void buildScreens() {
        DevShowroom.screens.add(new DevShowroom.ScreenShot("b13a_gui_crucible_rank0", p -> {
            // 场景拍完时 DevShowroom.finishScenes 把世界切成了和平（后面的手册截图要），淬炼台和灵魂石照和平画就会把图谱算成已满足、
            // 多一行「和平难度：站着引导」。这几张按非和平画，最后一张撤掉覆盖值，世界难度不动
            MatrixRank.setTestPeaceful(false);
            MatrixCrucibleBlockEntity be = crucibleBe(p);
            if (be == null || crucible == null) {
                return;
            }
            matrix(p, 0, 0, 0, 0, false);
            fillCrucible(be, false);
            hold(p, boundStone(p));
            standAt(p, crucible);
        }, DevShowroomBatch13a::openCrucible));
        DevShowroom.screens.add(new DevShowroom.ScreenShot("b13a_gui_crucible_ready", p -> {
            MatrixCrucibleBlockEntity be = crucibleBe(p);
            if (be == null || crucible == null) {
                return;
            }
            matrix(p, 0, 0, 0, 10_000, true);
            fillCrucible(be, true);
            hold(p, boundStone(p));
            standAt(p, crucible);
        }, DevShowroomBatch13a::openCrucible));

        tooltip("b13a_soul_stone_rank3", p -> matrix(p, 3, 0, 6_200, 2_000, true), p -> boundStone(p));
        tooltip("b13a_soul_stone_rank6_chaos", p -> matrix(p, 6, 1, 24_000, 2_000, true), p -> boundStone(p));
        tooltip("b13a_holy_mace_rank7_chaos", p -> matrix(p, 7, 1, 30_000, 2_000, true), p -> new ItemStack(ECStaffs.HOLY_MACE.get()));

        tooltip("b13a_computer_chestplate_stage0", p -> { }, p -> attuned(ECComputer.COMPUTER_CHESTPLATE, 0, 0F));
        tooltip("b13a_computer_chestplate_stage2_half", p -> { }, p -> attuned(ECComputer.COMPUTER_CHESTPLATE, 2, 0.5F));
        tooltip("b13a_computer_chestplate_stage2_full", p -> { }, p -> attuned(ECComputer.COMPUTER_CHESTPLATE, 2, 1F));
        tooltip("b13a_computer_chestplate_stage5", p -> { }, p -> attuned(ECComputer.COMPUTER_CHESTPLATE, 5, 0F));
        tooltip("b13a_computer_helmet_stage3", p -> { }, p -> attuned(ECComputer.COMPUTER_HELMET, 3, 0.3F));

        // 终端「模块」页：穿上四件 5 阶宅男套装，数据照服务端真的算（ModuleTerminal.panel 按阶给槽数）
        DevShowroom.screens.add(new DevShowroom.ScreenShot("b13a_terminal_modules_stage5", p -> dress(p, 5), p -> {
            S2CComputerTerminalPacket packet = ComputerSealField.packet(p, true);
            Minecraft.getInstance().execute(() -> {
                Minecraft mc = Minecraft.getInstance();
                mc.getToasts().clear();
                CurioSealClient.setShowroomTerminal(packet);
                ComputerTerminalScreen.setPageForNextOpen(true);
                mc.setScreen(new ComputerTerminalScreen());
            });
        }));
        // 凝结矩阵的提示框；顺带脱掉宅男套装、撤掉终端的展示数据
        DevShowroom.screens.add(new DevShowroom.ScreenShot("b13a_condensed_matrix", p -> {
            dress(p, -1);
            MatrixRank.setTestPeaceful(null);
        }, p -> {
            ItemStack stack = new ItemStack(ECEnrichment.CONDENSED_MATRIX.get());
            Minecraft.getInstance().execute(() -> {
                Minecraft mc = Minecraft.getInstance();
                mc.getToasts().clear();
                CurioSealClient.setShowroomTerminal(null);
                ComputerTerminalScreen.setPageForNextOpen(false);
                mc.setScreen(new TooltipShot(stack));
            });
        }));
    }

    private static void openCrucible(ServerPlayer player) {
        if (crucible != null && player.level().getBlockEntity(crucible) instanceof MatrixCrucibleBlockEntity be) {
            NetworkHooks.openScreen(player, be, crucible);
        }
    }

    /** 一张提示框：prepare 在服务端改玩家状态，物品也在服务端造好（灵魂石要绑定展示玩家），5 刻后在客户端开 {@link TooltipShot} */
    private static void tooltip(String name, Consumer<ServerPlayer> prepare, Function<ServerPlayer, ItemStack> stack) {
        DevShowroom.screens.add(new DevShowroom.ScreenShot(name, prepare, p -> {
            ItemStack shown = stack.apply(p);
            Minecraft.getInstance().execute(() -> {
                Minecraft mc = Minecraft.getInstance();
                mc.getToasts().clear();
                mc.setScreen(new TooltipShot(shown));
            });
        }));
    }

    /** 满电、同调 stage 阶、这一阶攒了 fill（0~1）的一件 */
    private static ItemStack attuned(RegistryObject<? extends Item> item, int stage, float fill) {
        ItemStack stack = MRUStaffItem.full(item.get());
        Attunement.set(stack, stage, 0);
        int points = Math.round(Attunement.need(stack) * fill);
        if (points > 0) {
            Attunement.set(stack, stage, points);
        }
        return stack;
    }

    /** 穿上四件同调 stage 阶的宅男套装（stage &lt; 0 时脱光） */
    private static void dress(ServerPlayer player, int stage) {
        List<Supplier<ItemStack>> pieces = List.of(
                () -> attuned(ECComputer.COMPUTER_HELMET, stage, 0F), () -> attuned(ECComputer.COMPUTER_CHESTPLATE, stage, 0F),
                () -> attuned(ECComputer.COMPUTER_LEGGINGS, stage, 0F), () -> attuned(ECComputer.COMPUTER_BOOTS, stage, 0F));
        EquipmentSlot[] slots = Attunement.ARMOR_SLOTS;
        for (int i = 0; i < slots.length; i++) {
            player.setItemSlot(slots[i], stage < 0 ? ItemStack.EMPTY : pieces.get(i).get());
        }
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
