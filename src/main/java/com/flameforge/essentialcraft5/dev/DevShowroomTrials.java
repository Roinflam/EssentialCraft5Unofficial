package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity.CollapseValues;
import com.flameforge.essentialcraft5.blockentity.EnrichmentStateCheckerBlockEntity;
import com.flameforge.essentialcraft5.entity.HoloDecoyEntity;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.entity.OrbitalStrikeEntity;
import com.flameforge.essentialcraft5.entity.WindMageEntity;
import com.flameforge.essentialcraft5.entity.WindMageSummons;
import com.flameforge.essentialcraft5.gun.LensEffects;
import com.flameforge.essentialcraft5.item.EmberInlayItem;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.ember.EmberKind;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.player.WindWishes;
import com.flameforge.essentialcraft5.registry.ECEffects;
import com.flameforge.essentialcraft5.registry.ECEmberForge;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import com.flameforge.essentialcraft5.registry.ECHoloDecoy;
import com.flameforge.essentialcraft5.registry.ECHoloWeapons;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECPentacle;
import com.flameforge.essentialcraft5.registry.ECVoid;
import com.flameforge.essentialcraft5.registry.ECWind;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * 展示厅的第十批 10b 部分（主线加长与机制型新能力，只在 -PecShowroom10b 时跑；batch10 §5.9 第 5 条）。
 * <p>
 * <b>场景</b>（04_scene_trials_*，镜头从南边朝北拍）：风之心愿的聊天栏提示；号角召来的大法师和两个随从；坍缩前的富集仓（南墙是虚空玻璃，看得见里面的聚集体）、
 * 中途打开的状态检测器（{@code 02_gui_trials_state_checker}，坍缩进度条）、坍缩后躺在仓底的虚空核心；三种透镜打中以后的尸壳（腐蚀、冻僵、魔力透镜剥效果前后）；
 * 挂着二分的尸壳（二分枪首领模式挂的效果）；轨道遥控器对准尸壳时立在它脚下的预览光柱、锁定后跟着它走的打击光柱；全息诱饵和改去打它的尸壳；第三人称的风之偏转白雾；
 * 身上同时带四个新效果时右上角的图标。
 * <p>
 * <b>界面</b>（02_gui_trials_*）：风之石板（好感 3500、风之印记 3 / 7）、风之号角、余烬嵌片、嵌好的铁剑、全息诱饵投影仪的提示框；铁砧上嵌刻；
 * 14 种嵌片排一排；生存背包左边的四个效果（界面缩放 3、2 各一张）。手册页见 {@link DevShowroom} 的 {@code trialsGuidePages}。
 * <p>
 * 怪物要难度不是和平：本部分开拍时调成简单，场景拍完 {@link DevShowroom} 照例调回和平。坍缩用测试覆盖值把要攒的进度改成 {@value #COLLAPSE_SECONDS} 秒、
 * 维度名单换成主世界（不动「当成海纳尔」的测试开关，那个会让辐射、天灾也把主世界当海纳尔），场景拍完撤掉。
 */
public final class DevShowroomTrials {

    private DevShowroomTrials() {
    }

    /** 本类占几个场景位 */
    static final int SCENE_SLOTS = 9;
    /** 坍缩场景里要攒的进度（秒）：开拍约 6 秒时拍「坍缩前」、约 8 秒时开状态检测器、20 秒后拍「坍缩后」 */
    static final int COLLAPSE_SECONDS = 20;

    /** 场景里生成的实体（服务端线程读写；每组场景开拍前删掉上一组的，最后全部删掉） */
    private static final List<Entity> SPAWNED = new ArrayList<>();
    @Nullable
    private static BlockPos chamberChecker;
    @Nullable
    private static Husk lockTarget;
    private static int cloudTicks;

    // ================================================================ 入口

    static void build(ServerLevel level, ServerPlayer player, IntFunction<BlockPos> sceneBase, int firstIndex) {
        buildWishScene(sceneBase.apply(firstIndex));
        buildMageScene(sceneBase.apply(firstIndex + 1));
        buildCollapseScene(level, sceneBase.apply(firstIndex + 2));
        buildLensScene(sceneBase.apply(firstIndex + 3));
        buildBisectedScene(sceneBase.apply(firstIndex + 4));
        buildOrbitalScene(sceneBase.apply(firstIndex + 5));
        buildDecoyScene(sceneBase.apply(firstIndex + 6));
        buildDeflectScene(sceneBase.apply(firstIndex + 7));
        buildEffectsHudScene(sceneBase.apply(firstIndex + 8));
        buildScreens();
    }

    /** 客户端：场景阶段结束时把镜头、HUD 放回（DevShowroom 调） */
    static void clientFinish(Minecraft mc) {
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (mc.screen != null) {
            mc.setScreen(null);
        }
    }

    /** 服务端：场景拍完后删掉生成的实体、撤掉坍缩的测试覆盖值（DevShowroom.finishScenes 调） */
    static void finish(ServerPlayer player) {
        discardSpawned();
        EnrichmentControllerBlockEntity.Config.setCollapseTestOverride(null);
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

    private static void discardSpawned() {
        for (Entity entity : SPAWNED) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
        SPAWNED.clear();
        lockTarget = null;
    }

    /** NoAI 的尸壳（白天不着火），面朝 yaw */
    private static Husk husk(ServerLevel level, Vec3 at, float yaw) {
        Husk husk = EntityType.HUSK.create(level);
        if (husk == null) {
            throw new IllegalStateException("生成不了尸壳");
        }
        husk.moveTo(at.x, at.y, at.z, yaw, 0F);
        husk.setYHeadRot(yaw);
        husk.setYBodyRot(yaw);
        husk.setNoAi(true);
        level.addFreshEntity(husk);
        SPAWNED.add(husk);
        return husk;
    }

    /** 让 mob 面朝 target（NoAI 的不会自己转头） */
    private static void face(Mob mob, Vec3 target) {
        double dx = target.x - mob.getX();
        double dz = target.z - mob.getZ();
        float yaw = (float) (Math.atan2(dz, dx) * 180D / Math.PI) - 90F;
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
    }

    @Nullable
    private static IPlayerData data(ServerPlayer player) {
        return PlayerDataEvents.get(player);
    }

    // ================================================================ 场景

    /** 风之心愿：受眷顾以后问风，聊天栏里风说出的心愿（开着 HUD 拍聊天栏） */
    private static void buildWishScene(BlockPos base) {
        Consumer<ServerPlayer> ask = p -> {
            easy(p);
            discardSpawned();
            IPlayerData data = data(p);
            if (data == null) {
                EssentialCraft5.LOGGER.warn("[showroom] 展示玩家没有玩家数据");
                return;
            }
            data.setWindbound(true);
            data.setWindWish(new net.minecraft.nbt.CompoundTag(), true);
            WindWishes.AskResult result = WindWishes.ask(p);
            EssentialCraft5.LOGGER.info("[showroom] 问风要心愿：{}", result);
        };
        scene("trials_wish_chat", ask, at(base, 0.5, 2.6, 6), at(base, 0.5, 4, -10), DevShowroom.FIRST_SCENE_WAIT, mc -> mc.options.hideGui = false);
    }

    /** 风之号角召来的大法师和两个随从（走召唤的真流程生成，再设成 NoAI、转过来朝着镜头） */
    private static void buildMageScene(BlockPos base) {
        Consumer<ServerPlayer> summon = p -> {
            easy(p);
            discardSpawned();
            List<BlockPos> points = List.of(base.offset(0, 0, -6), base.offset(-3, 0, -5), base.offset(3, 0, -5));
            List<WindMageEntity> mages = WindMageSummons.spawn(p, new WindMageSummons.Plan(p.level().dimension(), points));
            if (mages.isEmpty()) {
                EssentialCraft5.LOGGER.warn("[showroom] 召不出风法师");
            }
            Vec3 camera = at(base, 0.5, 1.6, 4);
            for (WindMageEntity mage : mages) {
                mage.setNoAi(true);
                mage.setTarget(null);
                face(mage, camera);
                SPAWNED.add(mage);
            }
        };
        scene("trials_wind_horn_mages", summon, at(base, 0.5, 2.4, 4), at(base, 0.5, 1.4, -6), 40, null);
    }

    /**
     * 坍缩：5 × 3 × 5 的强化石仓（北墙控制器、西墙存储方块、东墙稳定器、底面状态检测器），南墙中间 3 × 3 是虚空玻璃（凑够 8 块虚空级方块，
     * 也当窗户）。开拍时放进一团满的聚集体、仓里存 12 万冰冻 MRU（平衡值 0.4），坍缩后仓底正中躺着一个虚空核心。
     */
    private static void buildCollapseScene(ServerLevel level, BlockPos base) {
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
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                level.setBlockAndUpdate(new BlockPos(base.getX() + dx, min.getY() + dy, max.getZ()), ECVoid.VOID_GLASS.get().defaultBlockState());
            }
        }
        BlockPos controller = new BlockPos(base.getX(), min.getY() + 1, min.getZ());
        level.setBlockAndUpdate(controller, ECEnrichment.EC_CONTROLLER.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(min.getX(), min.getY() + 1, base.getZ() - 6), ECEnrichment.EC_HOLDING_CHAMBER.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(max.getX(), min.getY() + 1, base.getZ() - 6), ECPentacle.EC_BALANCER.get().defaultBlockState());
        BlockPos checker = new BlockPos(base.getX(), min.getY(), base.getZ() - 6);
        level.setBlockAndUpdate(checker, ECEnrichment.EC_STATE_CHECKER.get().defaultBlockState());
        chamberChecker = checker;
        Vec3 center = Vec3.atCenterOf(new BlockPos(base.getX(), min.getY() + 1, base.getZ() - 6));

        Consumer<ServerPlayer> start = p -> {
            discardSpawned();
            EnrichmentControllerBlockEntity.Config.setCollapseTestOverride(new CollapseValues(true, 100_000, COLLAPSE_SECONDS, 3, 8,
                    List.of(p.level().dimension().location().toString()), MRUPresenceEntity.MAX_MRU, 0.5D, 1.5D));
            if (!(p.level().getBlockEntity(controller) instanceof EnrichmentControllerBlockEntity ec)) {
                EssentialCraft5.LOGGER.warn("[showroom] 富集仓控制器没放上");
                return;
            }
            EssentialCraft5.LOGGER.info("[showroom] 富集仓外壳：{}，虚空级方块 {} 块", ec.checkNow(), ec.voidCasing());
            ec.storage().setMRU(120_000);
            ec.storage().setBalance(0.4F);
            MRUPresenceEntity presence = MRUPresenceEntity.create(p.level(), center.x, center.y, center.z, MRUPresenceEntity.MAX_MRU, 1F, false, true);
            p.level().addFreshEntity(presence);
            SPAWNED.add(presence);
        };
        Vec3 eye = at(base, 0.5, 2.2, 0.5);
        Vec3 look = Vec3.atCenterOf(new BlockPos(base.getX(), min.getY() + 1, base.getZ() - 6));
        scene("trials_collapse_before", start, eye, look, DevShowroom.FIRST_SCENE_WAIT, null);
        // 中途打开状态检测器：坍缩进度条（蓝色 = 在凝虚空核心）。名字以 02_ 开头，原样当截图名，下一张场景的 client 负责关掉
        Consumer<ServerPlayer> openChecker = p -> {
            if (chamberChecker != null && p.level().getBlockEntity(chamberChecker) instanceof EnrichmentStateCheckerBlockEntity be) {
                NetworkHooks.openScreen(p, be, chamberChecker);
            } else {
                EssentialCraft5.LOGGER.warn("[showroom] 找不到状态检测器");
            }
        };
        scene("02_gui_trials_state_checker", openChecker, eye, look, 40, null);
        scene("trials_collapse_after", null, eye, look, (COLLAPSE_SECONDS - 6) * 20, mc -> {
            if (mc.player != null) {
                mc.player.closeContainer();
            }
            mc.setScreen(null);
        });
        // 坍缩以后拆掉南墙正中那块虚空玻璃（手册说的「拆一块外壳进去拿」），看躺在仓底的产物（不会消失）
        BlockPos window = new BlockPos(base.getX(), min.getY() + 1, max.getZ());
        scene("trials_collapse_opened", p -> {
            ServerLevel world = p.serverLevel();
            List<net.minecraft.world.entity.item.ItemEntity> items = world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(min, max.offset(1, 1, 1)), e -> true);
            String state = world.getBlockEntity(controller) instanceof EnrichmentControllerBlockEntity ec
                    ? ec.collapseState() + " 进度 " + ec.collapseProgress() + " MRU " + ec.storage().getMRU() : "没有控制器";
            EssentialCraft5.LOGGER.info("[showroom] 坍缩以后：{}；仓里的掉落物 {}", state, items.stream().map(e -> e.getItem().toString()).toList());
            world.removeBlock(window, false);
        }, eye, look, 20, null);
    }

    /**
     * 三种透镜打中以后：腐蚀（绿色效果粒子）、冻僵（发抖）、魔力透镜剥效果前后（左边带着迅捷、右边被剥掉）。
     * 冻值每刻自己退 2，这里一次给够，拍的时候还是冻僵的。
     */
    private static void buildLensScene(BlockPos base) {
        Consumer<ServerPlayer> hits = p -> {
            easy(p);
            discardSpawned();
            ServerLevel level = p.serverLevel();
            Husk corroded = husk(level, at(base, -3, 0, -4), 0F);
            LensEffects.corrode(corroded, p, 6000);
            Husk frozen = husk(level, at(base, -1, 0, -4), 0F);
            frozen.setTicksFrozen(frozen.getTicksRequiredToFreeze() + 2 * (DevShowroom.FIRST_SCENE_WAIT + 60));
            // 冻僵每 2 秒挨一下冻伤，受击时会红一下：设成无敌，只看发抖
            frozen.setInvulnerable(true);
            Husk before = husk(level, at(base, 1, 0, -4), 0F);
            before.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 6000, 1));
            Husk after = husk(level, at(base, 3, 0, -4), 0F);
            after.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 6000, 1));
            MobEffect dispelled = LensEffects.dispelOne(after);
            EssentialCraft5.LOGGER.info("[showroom] 魔力透镜剥掉：{}", dispelled);
        };
        scene("trials_lens_hits", hits, at(base, 0.5, 2.2, 2.5), at(base, 0.5, 1.2, -4), DevShowroom.FIRST_SCENE_WAIT, null);
    }

    /**
     * 二分枪的首领模式挂的「二分」：一只 NoAI 的尸壳挂上二分，看效果粒子。原版首领拍不出这个画面——凋灵对状态效果免疫（{@code addEffect} 一律拒绝），
     * 首领模式只走 addEffect、照它的设计不吃；末影龙过不了探测。这里直接给尸壳加效果，只为看二分本身的样子。
     */
    private static void buildBisectedScene(BlockPos base) {
        Consumer<ServerPlayer> bisect = p -> {
            easy(p);
            discardSpawned();
            Husk bisected = husk(p.serverLevel(), at(base, 0.5, 0, -4), 0F);
            bisected.addEffect(new MobEffectInstance(ECEffects.BISECTED.get(), 6000, 0), p);
        };
        scene("trials_divider_bisected", bisect, at(base, 0.5, 2.2, 2.5), at(base, 0.5, 1.2, -4), DevShowroom.FIRST_SCENE_WAIT, null);
    }

    /** 轨道锁定：手持遥控器、准星对着 12 格外的尸壳时预览光柱立在它脚下；锁定以后尸壳挪了地方，光柱跟过去 */
    private static void buildOrbitalScene(BlockPos base) {
        Consumer<ServerPlayer> aimAt = p -> {
            easy(p);
            discardSpawned();
            lockTarget = husk(p.serverLevel(), at(base, 0.5, 0, -10), 0F);
            hold(p, MRUStaffItem.full(ECHoloWeapons.ORBITAL_REMOTE.get()));
        };
        scene("trials_orbital_lock_preview", aimAt, at(base, 0.5, 2.6, 2), at(base, 0.5, 1.1, -10), DevShowroom.FIRST_SCENE_WAIT, null);
        Consumer<ServerPlayer> follow = p -> {
            hold(p, ItemStack.EMPTY);
            Husk target = lockTarget;
            if (target == null || target.isRemoved()) {
                EssentialCraft5.LOGGER.warn("[showroom] 锁定的尸壳没了");
                return;
            }
            OrbitalStrikeEntity strike = OrbitalStrikeEntity.spawn(p.serverLevel(), target.position(), 0F, 1F, 400, p, entity -> false, target);
            if (strike != null) {
                SPAWNED.add(strike);
            }
            target.teleportTo(target.getX() + 3D, target.getY(), target.getZ() + 1D);
        };
        scene("trials_orbital_lock_follow", follow, at(base, 1.5, 7, 9), at(base, 3, 1, -9), 30, null);
    }

    /** 全息诱饵：半透明的分身，三只尸壳转过去盯着它 */
    private static void buildDecoyScene(BlockPos base) {
        Consumer<ServerPlayer> decoy = p -> {
            easy(p);
            discardSpawned();
            ServerLevel level = p.serverLevel();
            List<Husk> husks = List.of(husk(level, at(base, -3, 0, -7), 0F), husk(level, at(base, 3.5, 0, -7.5), 0F), husk(level, at(base, 0.5, 0, -10), 0F));
            for (Husk husk : husks) {
                husk.setTarget(p);
            }
            Vec3 spot = at(base, 0.5, 0, -6.5);
            HoloDecoyEntity entity = HoloDecoyEntity.spawn(level, p, spot, new ECHoloDecoy.Config.Values(5000, 600, 6000, 16, 8, 60));
            if (entity == null) {
                EssentialCraft5.LOGGER.warn("[showroom] 全息诱饵没生成出来");
                return;
            }
            entity.setYRot(180F);
            entity.setYHeadRot(180F);
            entity.yBodyRot = 180F;
            entity.activate();
            SPAWNED.add(entity);
            for (Husk husk : husks) {
                face(husk, spot);
                EssentialCraft5.LOGGER.info("[showroom] 尸壳的目标：{}", HoloDecoyEntity.currentTarget(husk));
            }
        };
        scene("trials_holo_decoy", decoy, at(base, 0.5, 2.6, -1), at(base, 0.5, 1.2, -8), DevShowroom.FIRST_SCENE_WAIT, null);
    }

    /**
     * 风之偏转：第三人称从前面看，身边停着一支箭、冒一团白雾（白雾在客户端最后几刻补出来，粒子活不了几刻）
     */
    private static void buildDeflectScene(BlockPos base) {
        Consumer<ServerPlayer> arrow = p -> {
            discardSpawned();
            Arrow shot = EntityType.ARROW.create(p.serverLevel());
            if (shot == null) {
                return;
            }
            Vec3 at = at(base, 1.2, 1.4, 0.5);
            shot.moveTo(at.x, at.y, at.z, 90F, 0F);
            shot.setNoGravity(true);
            shot.setDeltaMovement(Vec3.ZERO);
            p.serverLevel().addFreshEntity(shot);
            SPAWNED.add(shot);
            cloudTicks = 0;
        };
        DevShowroom.scenes.add(new DevShowroom.SceneShot("trials_deflect_cloud", arrow, at(base, 0.5, 1.62, 0.5), at(base, 0.5, 1.62, -6),
                DevShowroom.FIRST_SCENE_WAIT, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT), mc -> {
            if (mc.level != null && mc.player != null && cloudTicks % 2 == 0) {
                Vec3 at = mc.player.position().add(0.7D, 1.2D, 0D);
                for (int i = 0; i < 6; i++) {
                    mc.level.addParticle(ParticleTypes.CLOUD, at.x + (mc.level.random.nextDouble() - 0.5D) * 0.6D, at.y + mc.level.random.nextDouble() * 0.6D,
                            at.z + (mc.level.random.nextDouble() - 0.5D) * 0.6D, 0D, 0.02D, 0D);
                }
            }
            return ++cloudTicks > 10;
        }));
    }

    /** 四个新效果同时在身上：开着 HUD，看右上角的效果图标 */
    private static void buildEffectsHudScene(BlockPos base) {
        Consumer<ServerPlayer> effects = p -> {
            discardSpawned();
            applyEffects(p);
            cloudTicks = 0;
        };
        DevShowroom.scenes.add(new DevShowroom.SceneShot("trials_effects_hud", effects, at(base, 0.5, 2.6, 4), at(base, 0.5, 6, -10), 20, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.hideGui = false;
        }, mc -> {
            // 进度弹窗会盖住右上角的效果图标：等它们清掉
            mc.getToasts().clear();
            return ++cloudTicks > 30;
        }));
    }

    /** 四个效果都不冒粒子（自己身上的效果粒子会飘到镜头前），图标照常显示 */
    private static void applyEffects(ServerPlayer player) {
        int ticks = 20 * 60 * 20;
        player.addEffect(new MobEffectInstance(ECEffects.CORROSION.get(), ticks, 0, false, false, true));
        player.addEffect(new MobEffectInstance(ECEffects.ARMOR_SHRED.get(), ticks, 2, false, false, true));
        player.addEffect(new MobEffectInstance(ECEffects.BISECTED.get(), ticks, 0, false, false, true));
        player.addEffect(new MobEffectInstance(ECEffects.ANCHORED.get(), ticks, 0, false, false, true));
    }

    // ================================================================ 界面

    private static ItemStack inlay() {
        return ECEmberForge.EMBER_INLAY.get().create(EmberInlayItem.Pair.of(EmberKind.DIVINE, EmberKind.MAGICAL));
    }

    private static ItemStack inlaidSword() {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        EmberInlayItem.setInlay(sword, EmberInlayItem.Pair.of(EmberKind.DIVINE, EmberKind.MAGICAL));
        return sword;
    }

    private static void tooltip(String name, Consumer<ServerPlayer> prepare, java.util.function.Supplier<ItemStack> stack) {
        DevShowroom.screens.add(new DevShowroom.ScreenShot(name, prepare, p -> Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.getToasts().clear();
            mc.setScreen(new TooltipShot(List.of(stack.get())));
        })));
    }

    private static void buildScreens() {
        // 风之石板：好感 3500（真名全显）、完成 3 个心愿（风之印记 3 / 7）
        tooltip("trials_tablet_tooltip", p -> {
            IPlayerData data = data(p);
            if (data != null) {
                data.setWindPoints(ECWind.Config.tabletPoints());
                data.setWindWishes(3);
            }
        }, () -> new ItemStack(ECWind.WIND_TABLET.get()));
        tooltip("trials_wind_horn_tooltip", p -> { }, () -> new ItemStack(ECWind.WIND_HORN.get()));
        tooltip("trials_inlay_tooltip", p -> { }, DevShowroomTrials::inlay);
        tooltip("trials_inlaid_sword_tooltip", p -> { }, DevShowroomTrials::inlaidSword);
        tooltip("trials_decoy_projector_tooltip", p -> { }, () -> MRUStaffItem.full(ECHoloDecoy.HOLO_DECOY_PROJECTOR.get()));
        // 14 种嵌片排一排（看两层染色）
        DevShowroom.screens.add(new DevShowroom.ScreenShot("trials_inlay_wall", p -> { }, p -> Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.getToasts().clear();
            List<ItemStack> all = new ArrayList<>();
            for (EmberInlayItem.Pair pair : EmberInlayItem.craftablePairs()) {
                all.add(ECEmberForge.EMBER_INLAY.get().create(pair));
            }
            mc.setScreen(new ItemWall(all));
        })));
        // 铁砧：左格铁剑、右格嵌片，出口是嵌好的剑（结果由 InlayEvents 在 AnvilUpdateEvent 里算）
        DevShowroom.screens.add(new DevShowroom.ScreenShot("trials_anvil_inlay", p -> { }, p -> {
            p.openMenu(new SimpleMenuProvider((id, inventory, who) -> new AnvilMenu(id, inventory, ContainerLevelAccess.NULL),
                    Component.translatable("container.repair")));
            if (p.containerMenu instanceof AnvilMenu anvil) {
                anvil.getSlot(0).set(new ItemStack(Items.IRON_SWORD));
                anvil.getSlot(1).set(inlay());
                anvil.createResult();
                anvil.broadcastChanges();
                EssentialCraft5.LOGGER.info("[showroom] 铁砧出口：{}", anvil.getSlot(2).getItem());
            }
        }));
        // 生存背包左边的效果栏：界面缩放 3、2 各一张（创造模式开的是创造栏，临时换成生存）
        DevShowroom.screens.add(new DevShowroom.ScreenShot("trials_effects_inventory_s3", p -> {
            p.setGameMode(GameType.SURVIVAL);
            applyEffects(p);
        }, p -> Minecraft.getInstance().execute(() -> openInventory(3))));
        DevShowroom.screens.add(new DevShowroom.ScreenShot("trials_effects_inventory_s2", p -> applyEffects(p),
                p -> Minecraft.getInstance().execute(() -> openInventory(2))));
        DevShowroom.screens.add(new DevShowroom.ScreenShot("trials_effects_done", p -> {
            p.setGameMode(GameType.CREATIVE);
            p.removeAllEffects();
        }, p -> Minecraft.getInstance().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.guiScale().get() != 3) {
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
            }
        })));
    }

    private static void openInventory(int scale) {
        Minecraft mc = Minecraft.getInstance();
        mc.getToasts().clear();
        if (mc.options.guiScale().get() != scale) {
            mc.options.guiScale().set(scale);
            mc.resizeDisplay();
        }
        if (mc.player != null) {
            mc.setScreen(new InventoryScreen(mc.player));
        }
    }

    /** 只画物品图标和它的提示框（用游戏自己的提示内容和样式），截图用，不动鼠标 */
    private static final class TooltipShot extends Screen {
        private final List<ItemStack> stacks;

        TooltipShot(List<ItemStack> stacks) {
            super(Component.empty());
            this.stacks = stacks;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(graphics);
            ItemStack stack = stacks.get(0);
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
                graphics.renderItem(stacks.get(i), 8 + (i % 7) * 20, 8 + (i / 7) * 22);
            }
            graphics.pose().popPose();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
