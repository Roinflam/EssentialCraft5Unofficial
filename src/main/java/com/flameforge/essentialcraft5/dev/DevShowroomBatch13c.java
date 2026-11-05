package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.WindAffinity;
import com.flameforge.essentialcraft5.client.MRUGolemClient;
import com.flameforge.essentialcraft5.client.boss.BossFxClient;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.entity.WindMageEntity;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.boss.ECBossMonster;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeEntity;
import com.flameforge.essentialcraft5.entity.boss.MatrixShadeMirror;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.entity.mob.ElementalWispEntity;
import com.flameforge.essentialcraft5.entity.mob.MRUGolemEntity;
import com.flameforge.essentialcraft5.entity.mob.MatrixWraithEntity;
import com.flameforge.essentialcraft5.entity.mob.NetherImpEntity;
import com.flameforge.essentialcraft5.entity.mob.VoidStalkerEntity;
import com.flameforge.essentialcraft5.registry.ECDemonic;
import com.flameforge.essentialcraft5.registry.ECMRUGolem;
import com.flameforge.essentialcraft5.registry.ECMatrixShade;
import com.flameforge.essentialcraft5.registry.ECMobDrops;
import com.flameforge.essentialcraft5.registry.ECNetherEndMobs;
import com.flameforge.essentialcraft5.registry.ECWildMobs;
import com.flameforge.essentialcraft5.registry.ECWind;
import com.flameforge.essentialcraft5.registry.ECWindMage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * 展示厅的第十三批 13c 部分（新怪物与首领，只在 -PecShowroom13c 时跑；batch13.md §12.3）。截图名都以 {@code b13c_} 开头，存成 {@code ec5_b13c_*.png}。
 * <p>
 * 每组怪摆在一个场景格里，都不带 AI、不会消失、脸朝南（朝镜头），镜头从南边朝北拍；普通的在左、精英在右（首领没有精英）。
 * <b>定格姿势</b>：客户端把那一段动画起在实体当前的刻上，再手动往前推到要的那一秒（{@link AnimationState#updateTime}），然后 {@link MobAnimStates#freeze}：
 * 模型按 0 速度播，画面就停在那一帧（不等真时间、不受帧率影响）。不带 AI 的怪服务端不会再起别的段。
 * <ul>
 *   <li>{@code b13c_wisp_*}：四种元素的元素游灵一排 + 一只精英（元素聚灵，火）：待机、蓄力顶点（CHARGE 0.42 秒）、冲撞（DASH 0.3 秒）、精英施法（CAST 0.12 秒，
 *       旁边的普通游灵回到待机）；游灵个头小，镜头离一排约 3 格；</li>
 *   <li>{@code b13c_wraith_*}：矩阵游魂普通 / 精英（矩阵怨灵）：待机、抓击举爪（ATTACK 0.15 秒）、抓下（0.3 秒）、闪身缩团（BLINK_OUT 0.5 秒）、现身（BLINK_IN 0.2 秒）；
 *       旁边一团 6 000 MRU 的聚集体看比例；</li>
 *   <li>{@code b13c_mages}：学徒、法师、大法师、风暴术士四只风法师并排（风暴术士高一点、袍子深蓝紫）；</li>
 *   <li>{@code b13c_imp_*}：炼狱小鬼普通 / 精英（小鬼头目）：待机悬浮、吐余火弹（CAST 0.2 秒）、头目连发（BURST 0.5 秒）、头目念咒（SUMMON 0.6 秒）；镜头离 2.5 格；</li>
 *   <li>{@code b13c_stalker_*}：虚空潜行者普通 / 精英（裂隙行者）：待机、爪击（CLAW 0.08 秒，抬爪顶点）、裂隙爪（RIFT_CLAW 0.3 秒）、相位预警（PHASE 0.3 秒）、
 *       行者拉人（PULL 0.5 秒），再拍两只都进相位（半透明）：白天一张、半夜一张（{@code b13c_stalker_phased_night}，暗处才看得清相位时只剩发光点）；</li>
 *   <li>{@code b13c_golem_*}：聚能魔像，旁边 6 格放它守着的一团 6 000 MRU 聚集体：待机、重锤（HAMMER 0.15 秒）、聚能冲击（SLAM 0.95 秒）、电弧鞭（WHIP 0.5 秒）、
 *       护盾电弧（客户端补一道抵伤时的粗电弧）、首领血条（{@code b13c_golem_bar}，开着 HUD）；</li>
 *   <li>{@code b13c_shade_*}：矩阵之影（没有契约）：待机、坠身（SLAM 0.95 秒）、施法（CAST 0.6 秒）、收束（CONVERGE 1.9 秒）、矩阵电弧（ARC 1.0 秒）；
 *       三相各一张（开着 HUD、顶上挂着首领血条，同时摆一层已经淡到位的悖论画面：身相暗红 0.35、心相紫 0.6、矩阵相品红 0.8），
 *       心相那张两边各放一个镜影（胸口格暗的是镜影）。</li>
 * </ul>
 * <b>开着 HUD 的几张</b>（{@link #hudShot}）：血条只挂给玩家看，不把玩家加进参战名单（不会拉回、压飞行）。展示厅的首领都不带 AI，
 * 名单检查不跑、血条不会自己撤，所以下一组开拍前手动撤；首领拍完删掉时也会撤。第一人称的手不画；镜头往旁边偏一点，
 * 让准星落在空处，不压在胸口格上。精英没有常驻名字牌（精英前缀走 getTypeName、不设自定义名，batch13.md §9.3），这里也拍不到。
 * <b>界面</b>：矩阵导流符、带风暴余息的受困之风、灵魂刻刀、上古晶核、矩阵结晶的提示框。手册页见 {@link DevShowroom} 的 {@code batch13cGuidePages}。
 * 心智钉、失序之柱、聚能冲击的蓝圈、坠身的阴影圈要招式按参战者放，这里不拍。
 */
public final class DevShowroomBatch13c {

    private DevShowroomBatch13c() {
    }

    /** 拍完删掉的实体 */
    private static final List<Entity> SPAWNED = new ArrayList<>();
    private static final List<Integer> WISPS = new ArrayList<>();
    private static final List<Integer> WRAITHS = new ArrayList<>();
    private static final List<Integer> IMPS = new ArrayList<>();
    private static final List<Integer> STALKERS = new ArrayList<>();
    /** 定过格的实体（客户端收尾时解冻） */
    private static final List<Integer> FROZEN = new ArrayList<>();
    private static int golemId = -1;
    private static int shadeId = -1;
    @Nullable
    private static MRUGolemEntity golem;
    @Nullable
    private static MatrixShadeEntity shade;
    @Nullable
    private static VoidStalkerEntity stalker;
    @Nullable
    private static VoidStalkerEntity stalkerElite;
    @Nullable
    private static BlockPos shadeBase;
    /** 拍夜景前的时间（拍完调回去）；-1 = 没改过 */
    private static long dayTimeBefore = -1L;
    private static boolean built;

    // ================================================================ 入口

    static void build(ServerLevel level, ServerPlayer player, IntFunction<BlockPos> sceneBase, int firstIndex) {
        built = true;
        MinecraftServer server = level.getServer();
        if (server.getWorldData().getDifficulty() != Difficulty.EASY) {
            // 和平难度下怪物会被删掉；场景拍完 DevShowroom.finishScenes 调回和平
            server.setDifficulty(Difficulty.EASY, true);
        }
        buildWisps(level, sceneBase.apply(firstIndex));
        buildWraiths(level, sceneBase.apply(firstIndex + 1));
        buildMages(level, sceneBase.apply(firstIndex + 2));
        buildImps(level, sceneBase.apply(firstIndex + 3));
        buildStalkers(level, sceneBase.apply(firstIndex + 4));
        buildGolem(level, sceneBase.apply(firstIndex + 5));
        buildShade(level, sceneBase.apply(firstIndex + 6));
        buildScreens();
    }

    /** 服务端：删掉生成的实体（DevShowroom.finishScenes 调；没跑过 build 时什么都不做） */
    static void finish(ServerPlayer player) {
        if (!built) {
            return;
        }
        for (Entity entity : SPAWNED) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
        SPAWNED.clear();
        restoreDayTime(player.serverLevel());
        golem = null;
        shade = null;
        stalker = null;
        stalkerElite = null;
    }

    /** 客户端：场景拍完解冻、清掉悖论画面（DevShowroom 场景收尾时调） */
    static void clientFinish(Minecraft mc) {
        if (!built) {
            return;
        }
        BossFxClient.showroomEdge(0, 0F);
        mc.gameRenderer.setRenderHand(true);
        if (mc.level != null) {
            for (int id : FROZEN) {
                MobAnimStates anims = anims(mc.level.getEntity(id));
                if (anims != null) {
                    anims.stopAll();
                    anims.freeze(false);
                }
            }
        }
        FROZEN.clear();
    }

    // ================================================================ 小工具

    private static Vec3 at(BlockPos base, double dx, double dy, double dz) {
        return DevShowroom.at(base, dx, dy, dz);
    }

    private static void scene(String name, @Nullable Consumer<ServerPlayer> prepare, Vec3 eye, Vec3 target, int delay, @Nullable Consumer<Minecraft> client) {
        DevShowroom.scenes.add(new DevShowroom.SceneShot(name, prepare, eye, target, delay, client, null));
    }

    /** 不带 AI、不会消失、脸朝南的一只；setup 在进世界之前调 */
    @Nullable
    private static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, Vec3 pos, @Nullable Consumer<T> setup) {
        T mob = type.create(level);
        if (mob == null) {
            EssentialCraft5.LOGGER.warn("[showroom] 生成不了 {}", EntityType.getKey(type));
            return null;
        }
        mob.moveTo(pos.x, pos.y, pos.z, 0F, 0F);
        mob.setYHeadRot(0F);
        mob.setYBodyRot(0F);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        if (setup != null) {
            setup.accept(mob);
        }
        if (!level.addFreshEntity(mob)) {
            EssentialCraft5.LOGGER.warn("[showroom] {} 没进世界", EntityType.getKey(type));
            return null;
        }
        SPAWNED.add(mob);
        return mob;
    }

    /** 普通 / 精英 */
    private static <T extends ECMonster> Consumer<T> elite(boolean elite) {
        return mob -> mob.setElite(elite);
    }

    @Nullable
    private static MobAnimStates anims(@Nullable Entity entity) {
        if (entity instanceof ElementalWispEntity wisp) {
            return wisp.anims;
        }
        if (entity instanceof MatrixWraithEntity wraith) {
            return wraith.anims;
        }
        if (entity instanceof NetherImpEntity imp) {
            return imp.anims;
        }
        if (entity instanceof VoidStalkerEntity voidStalker) {
            return voidStalker.anims;
        }
        if (entity instanceof MRUGolemEntity golem) {
            return golem.anims;
        }
        if (entity instanceof MatrixShadeEntity matrixShade) {
            return matrixShade.anims;
        }
        if (entity instanceof MatrixShadeMirror mirror) {
            return mirror.anims;
        }
        return null;
    }

    /**
     * 客户端：这几只都定在第 slot 段的第 seconds 秒（slot &lt; 0 = 不放动画，回到待机姿势）。见类注释「定格姿势」
     */
    private static Consumer<Minecraft> pose(Supplier<List<Integer>> ids, int slot, float seconds) {
        return mc -> {
            if (mc.level == null) {
                return;
            }
            for (int id : ids.get()) {
                Entity entity = mc.level.getEntity(id);
                MobAnimStates anims = anims(entity);
                if (anims == null) {
                    EssentialCraft5.LOGGER.warn("[showroom] 实体 {} 不在客户端或没有动画，定格跳过", id);
                    continue;
                }
                anims.stopAll();
                anims.freeze(true);
                if (slot >= 0) {
                    anims.start(slot, entity.tickCount);
                    anims.get(slot).updateTime(entity.tickCount + seconds * 20F, 1F);
                }
                if (!FROZEN.contains(id)) {
                    FROZEN.add(id);
                }
            }
        };
    }

    private static Supplier<List<Integer>> ids(List<Integer> list) {
        return () -> list;
    }

    private static Supplier<List<Integer>> one(Supplier<Integer> id) {
        return () -> List.of(id.get());
    }

    /**
     * 客户端：开着 HUD 拍（血条、悖论画面都画在 HUD 层），第一人称的手不画（开 HUD 时原版会画一只空手）；
     * on = false 时关 HUD、手的开关还原（关着 HUD 时原版本来就不画手）
     */
    private static void hudShot(Minecraft mc, boolean on) {
        mc.options.hideGui = !on;
        mc.gameRenderer.setRenderHand(!on);
    }

    /** 服务端：只把这名玩家挂到首领的血条上 / 撤下来，不进参战名单 */
    private static void bossBar(@Nullable ECBossMonster boss, ServerPlayer player, boolean show) {
        if (boss == null) {
            return;
        }
        if (show && !boss.isRemoved()) {
            boss.bossRoster().bar().addPlayer(player);
        } else {
            boss.bossRoster().bar().removePlayer(player);
        }
    }

    /** 服务端：改时间，并马上把时间发给客户端（原版每 20 刻才同步一次，来不及就拍成了白天） */
    private static void setDayTime(ServerLevel level, long time) {
        level.setDayTime(time);
        ClientboundSetTimePacket packet = new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(),
                level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT));
        for (ServerPlayer player : level.players()) {
            player.connection.send(packet);
        }
    }

    /** 服务端：拍过夜景就把时间调回去 */
    private static void restoreDayTime(ServerLevel level) {
        if (dayTimeBefore >= 0L) {
            setDayTime(level, dayTimeBefore);
            dayTimeBefore = -1L;
        }
    }

    // ================================================================ 场景

    /** 四种元素的元素游灵（各悬在离地 1.2 格）+ 一只火之元素聚灵 */
    private static void buildWisps(ServerLevel level, BlockPos base) {
        ElementalWispEntity.Element[] elements = ElementalWispEntity.Element.values();
        for (int i = 0; i < elements.length; i++) {
            ElementalWispEntity.Element element = elements[i];
            ElementalWispEntity wisp = spawn(level, ECWildMobs.ELEMENTAL_WISP.get(), at(base, -2.4 + i * 1.2, 1.2, -3.5), mob -> mob.setElement(element));
            if (wisp != null) {
                WISPS.add(wisp.getId());
            }
        }
        ElementalWispEntity converged = spawn(level, ECWildMobs.ELEMENTAL_WISP.get(), at(base, 2.6, 1.2, -3.5), mob -> {
            mob.setElement(ElementalWispEntity.Element.FIRE);
            mob.setElite(true);
        });
        List<Integer> eliteOnly = new ArrayList<>();
        if (converged != null) {
            WISPS.add(converged.getId());
            eliteOnly.add(converged.getId());
        }
        // 游灵只有 0.8 格高：镜头离一排 3.2 格、对准一排正中（-2.4 ~ 2.6），16:9 下左右各能装下约 4 格
        Vec3 eye = at(base, 0.1, 1.7, -0.3);
        Vec3 look = at(base, 0.1, 1.3, -3.5);
        scene("b13c_wisp_idle", null, eye, look, DevShowroom.FIRST_SCENE_WAIT, pose(ids(WISPS), -1, 0F));
        scene("b13c_wisp_charge", null, eye, look, 5, pose(ids(WISPS), ElementalWispEntity.ANIM_CHARGE, 0.42F));
        scene("b13c_wisp_dash", null, eye, look, 5, pose(ids(WISPS), ElementalWispEntity.ANIM_DASH, 0.3F));
        // 先把全体放回待机（不然入画的普通游灵还停在上一张的冲撞姿势），再只给精英定施法
        scene("b13c_wisp_elite_cast", null, at(base, 2.6, 1.8, -0.5), at(base, 2.6, 1.4, -3.5), 5,
                pose(ids(WISPS), -1, 0F).andThen(pose(ids(eliteOnly), ElementalWispEntity.ANIM_CAST, 0.12F)));
    }

    /** 矩阵游魂普通 / 精英，左边一团 6 000 MRU 的聚集体看比例 */
    private static void buildWraiths(ServerLevel level, BlockPos base) {
        for (boolean isElite : new boolean[]{false, true}) {
            MatrixWraithEntity wraith = spawn(level, ECWildMobs.MATRIX_WRAITH.get(), at(base, isElite ? 2.0 : -1.0, 0, -3.5), elite(isElite));
            if (wraith != null) {
                WRAITHS.add(wraith.getId());
            }
        }
        MRUPresenceEntity presence = MRUPresenceEntity.create(level, base.getX() - 3.5D, base.getY() + 1.5D, base.getZ() - 3.5D, 6000, 1F, false, true);
        if (level.addFreshEntity(presence)) {
            SPAWNED.add(presence);
        }
        Vec3 eye = at(base, 0.0, 1.9, 2.8);
        Vec3 look = at(base, 0.0, 1.2, -3.5);
        scene("b13c_wraith_idle", null, eye, look, 10, pose(ids(WRAITHS), -1, 0F));
        scene("b13c_wraith_attack_raise", null, eye, look, 5, pose(ids(WRAITHS), MatrixWraithEntity.ANIM_ATTACK, 0.15F));
        scene("b13c_wraith_attack", null, eye, look, 5, pose(ids(WRAITHS), MatrixWraithEntity.ANIM_ATTACK, 0.3F));
        scene("b13c_wraith_blink_out", null, eye, look, 5, pose(ids(WRAITHS), MatrixWraithEntity.ANIM_BLINK_OUT, 0.5F));
        scene("b13c_wraith_blink_in", null, eye, look, 5, pose(ids(WRAITHS), MatrixWraithEntity.ANIM_BLINK_IN, 0.2F));
    }

    /** 学徒、法师、大法师、风暴术士 */
    private static void buildMages(ServerLevel level, BlockPos base) {
        for (int type = 0; type < WindMageEntity.TYPE_COUNT; type++) {
            int mageType = type;
            spawn(level, ECWindMage.WIND_MAGE.get(), at(base, -2.5 + type * 1.6, 0, -3.5), mob -> mob.setMageType(mageType));
        }
        scene("b13c_mages", null, at(base, 0.0, 1.8, 2.5), at(base, 0.0, 1.1, -3.5), 10, null);
    }

    /** 炼狱小鬼普通 / 精英（悬在离地 1 格） */
    private static void buildImps(ServerLevel level, BlockPos base) {
        List<Integer> eliteOnly = new ArrayList<>();
        for (boolean isElite : new boolean[]{false, true}) {
            NetherImpEntity imp = spawn(level, ECNetherEndMobs.NETHER_IMP.get(), at(base, isElite ? 1.8 : -0.8, 1.0, -3.5), elite(isElite));
            if (imp != null) {
                IMPS.add(imp.getId());
                if (isElite) {
                    eliteOnly.add(imp.getId());
                }
            }
        }
        // 小鬼 1.2 格高：镜头离 2.5 格，左右两只（各偏 1.3 格）都装得下
        Vec3 eye = at(base, 0.5, 1.6, -1.0);
        Vec3 look = at(base, 0.5, 1.4, -3.5);
        scene("b13c_imp_idle", null, eye, look, 10, pose(ids(IMPS), -1, 0F));
        scene("b13c_imp_cast", null, eye, look, 5, pose(ids(IMPS), NetherImpEntity.ANIM_CAST, 0.2F));
        scene("b13c_imp_elite_burst", null, eye, look, 5, pose(ids(eliteOnly), NetherImpEntity.ANIM_BURST, 0.5F));
        scene("b13c_imp_elite_summon", null, eye, look, 5, pose(ids(eliteOnly), NetherImpEntity.ANIM_SUMMON, 0.6F));
    }

    /** 虚空潜行者普通 / 精英；最后一张两只都进相位（半透明） */
    private static void buildStalkers(ServerLevel level, BlockPos base) {
        stalker = spawn(level, ECNetherEndMobs.VOID_STALKER.get(), at(base, -1.0, 0, -3.5), elite(false));
        stalkerElite = spawn(level, ECNetherEndMobs.VOID_STALKER.get(), at(base, 2.0, 0, -3.5), elite(true));
        for (VoidStalkerEntity one : new VoidStalkerEntity[]{stalker, stalkerElite}) {
            if (one != null) {
                STALKERS.add(one.getId());
            }
        }
        Supplier<List<Integer>> eliteOnly = () -> stalkerElite == null ? List.of() : List.of(stalkerElite.getId());
        Vec3 eye = at(base, 0.5, 2.2, 3.0);
        Vec3 look = at(base, 0.5, 1.4, -3.5);
        scene("b13c_stalker_idle", null, eye, look, 10, pose(ids(STALKERS), -1, 0F));
        // 0.08 秒是抬爪的顶点；0.15 秒已经挥过头、在收势，看着和待机差不多
        scene("b13c_stalker_claw", null, eye, look, 5, pose(ids(STALKERS), VoidStalkerEntity.ANIM_CLAW, 0.08F));
        scene("b13c_stalker_rift_claw", null, eye, look, 5, pose(ids(STALKERS), VoidStalkerEntity.ANIM_RIFT_CLAW, 0.3F));
        scene("b13c_stalker_phase_warning", null, eye, look, 5, pose(ids(STALKERS), VoidStalkerEntity.ANIM_PHASE, 0.3F));
        scene("b13c_stalker_elite_pull", null, eye, look, 5, pose(eliteOnly, VoidStalkerEntity.ANIM_PULL, 0.5F));
        Consumer<ServerPlayer> phased = p -> {
            for (VoidStalkerEntity one : new VoidStalkerEntity[]{stalker, stalkerElite}) {
                if (one != null && !one.isRemoved()) {
                    one.setPhase(VoidStalkerEntity.PHASED, 1_000_000);
                }
            }
        };
        scene("b13c_stalker_phased", phased, eye, look, 20, pose(ids(STALKERS), -1, 0F));
        // 半夜再拍一张：相位时身子只剩 0.3 不透明，白天草地上发光点淡得看不出（魔像那组开拍时调回原来的时间）
        Consumer<ServerPlayer> night = p -> {
            ServerLevel here = p.serverLevel();
            if (dayTimeBefore < 0L) {
                dayTimeBefore = here.getDayTime();
            }
            setDayTime(here, 18000L);
        };
        scene("b13c_stalker_phased_night", night, eye, look, 10, pose(ids(STALKERS), -1, 0F));
    }

    /** 聚能魔像，右边 6 格是它守着的那团聚集体（护盾在） */
    private static void buildGolem(ServerLevel level, BlockPos base) {
        MRUPresenceEntity presence = MRUPresenceEntity.create(level, base.getX() + 6.5D, base.getY() + 1.5D, base.getZ() - 4.5D, 6000, 1F, false, true);
        boolean presenceIn = level.addFreshEntity(presence);
        if (presenceIn) {
            SPAWNED.add(presence);
        }
        golem = spawn(level, ECMRUGolem.MRU_GOLEM.get(), at(base, 0.5, 0, -4.5), null);
        if (golem != null) {
            golemId = golem.getId();
            if (presenceIn) {
                golem.guard(presence);
                golem.refreshGuard();
            }
        }
        Supplier<List<Integer>> golems = one(() -> golemId);
        Vec3 eye = at(base, 2.0, 2.6, 4.5);
        Vec3 look = at(base, 2.0, 1.5, -4.5);
        scene("b13c_golem_idle", p -> restoreDayTime(p.serverLevel()), eye, look, 10, pose(golems, -1, 0F));
        scene("b13c_golem_hammer", null, eye, look, 5, pose(golems, MRUGolemEntity.ANIM_HAMMER, 0.15F));
        scene("b13c_golem_slam", null, eye, look, 5, pose(golems, MRUGolemEntity.ANIM_SLAM, 0.95F));
        scene("b13c_golem_whip", null, eye, look, 5, pose(golems, MRUGolemEntity.ANIM_WHIP, 0.5F));
        // 护盾电弧：抵掉一下时那道粗的，电弧只亮几刻，补完马上拍
        Consumer<Minecraft> arc = pose(golems, -1, 0F).andThen(mc -> {
            Entity entity = mc.level == null ? null : mc.level.getEntity(golemId);
            if (entity instanceof MRUGolemEntity clientGolem) {
                MRUGolemClient.shieldArc(clientGolem, false);
            }
        });
        scene("b13c_golem_shield_arc", null, eye, look, 2, arc);
        // 首领血条：魔像在画面左边，准星（画面正中）抬到胸口高度以上，落在魔像右边的空处
        // （聚集体到魔像胸口的护盾电弧待机时也会冒，在 1.5 格高上下）
        scene("b13c_golem_bar", p -> bossBar(golem, p, true), eye, at(base, 2.0, 2.4, -4.5), 10,
                pose(golems, -1, 0F).andThen(mc -> hudShot(mc, true)));
    }

    /** 矩阵之影（没有契约、场地中心就是脚下）；三相、镜影 */
    private static void buildShade(ServerLevel level, BlockPos base) {
        shadeBase = base;
        shade = spawn(level, ECMatrixShade.MATRIX_SHADE.get(), at(base, 0.5, 0, -5.5), null);
        if (shade != null) {
            shadeId = shade.getId();
        }
        Supplier<List<Integer>> shades = one(() -> shadeId);
        Vec3 eye = at(base, 0.5, 2.6, 3.0);
        Vec3 look = at(base, 0.5, 1.8, -5.5);
        scene("b13c_shade_idle", p -> bossBar(golem, p, false), eye, look, 10, pose(shades, -1, 0F).andThen(mc -> hudShot(mc, false)));
        scene("b13c_shade_slam", null, eye, look, 5, pose(shades, MatrixShadeEntity.ANIM_SLAM, 0.95F));
        scene("b13c_shade_cast", null, eye, look, 5, pose(shades, MatrixShadeEntity.ANIM_CAST, 0.6F));
        scene("b13c_shade_converge", null, eye, look, 5, pose(shades, MatrixShadeEntity.ANIM_CONVERGE, 1.9F));
        scene("b13c_shade_arc", null, eye, look, 5, pose(shades, MatrixShadeEntity.ANIM_ARC, 1.0F));
        // 三相：开着 HUD（悖论画面、血条都画在 HUD 层），换相后等发光层同步过来。
        // 镜头整体往右平移 1.5 格：准星落在矩阵之影和右边镜影（+3.0 格）之间的空处，不压在胸口格上
        Vec3 hudEye = at(base, 2.0, 2.6, 3.0);
        Vec3 hudLook = at(base, 2.0, 1.8, -5.5);
        for (MatrixShadeEntity.Phase phase : MatrixShadeEntity.Phase.values()) {
            Consumer<ServerPlayer> enter = p -> {
                if (shade != null && !shade.isRemoved()) {
                    shade.enterPhase(phase);
                }
                bossBar(shade, p, true);
                if (phase == MatrixShadeEntity.Phase.MIND) {
                    mirrors(p.serverLevel());
                }
            };
            Consumer<Minecraft> edge = pose(shades, -1, 0F).andThen(mc -> {
                hudShot(mc, true);
                BossFxClient.showroomEdge(phase.color(), phase.strength());
            });
            scene("b13c_shade_phase_" + phase.name().toLowerCase(java.util.Locale.ROOT), enter, hudEye, hudLook, 15, edge);
        }
        scene("b13c_shade_after", p -> bossBar(shade, p, false), eye, look, 5, mc -> {
            BossFxClient.showroomEdge(0, 0F);
            hudShot(mc, false);
        });
    }

    /** 心相：矩阵之影两边各放一个镜影（胸口格暗的是镜影） */
    private static void mirrors(ServerLevel level) {
        if (shade == null || shadeBase == null) {
            return;
        }
        for (double dx : new double[]{-2.5, 3.5}) {
            MatrixShadeMirror mirror = ECMatrixShade.SHADE_MIRROR.get().create(level);
            if (mirror == null) {
                continue;
            }
            mirror.bind(shade);
            Vec3 pos = at(shadeBase, dx, 0, -5.5);
            mirror.moveTo(pos.x, pos.y, pos.z, 0F, 0F);
            mirror.setYHeadRot(0F);
            mirror.setYBodyRot(0F);
            mirror.setNoAi(true);
            if (level.addFreshEntity(mirror)) {
                SPAWNED.add(mirror);
            }
        }
    }

    // ================================================================ 界面

    private static void buildScreens() {
        tooltip("b13c_matrix_conduit", () -> new ItemStack(ECMatrixShade.MATRIX_CONDUIT.get()));
        tooltip("b13c_wind_keeper_storm", () -> WindAffinity.markStormResidue(new ItemStack(ECWind.WIND_KEEPER.get())));
        tooltip("b13c_soul_scriber", () -> new ItemStack(ECDemonic.SOUL_SCRIBER.get()));
        tooltip("b13c_ancient_core", () -> new ItemStack(ECMobDrops.ANCIENT_CORE.get()));
        tooltip("b13c_matrix_crystal", () -> new ItemStack(ECMobDrops.MATRIX_CRYSTAL.get()));
    }

    /** 一张提示框：物品在服务端造好，5 刻后在客户端开 {@link TooltipShot} */
    private static void tooltip(String name, Supplier<ItemStack> stack) {
        DevShowroom.screens.add(new DevShowroom.ScreenShot(name, p -> { }, p -> {
            ItemStack shown = stack.get();
            Minecraft.getInstance().execute(() -> {
                Minecraft mc = Minecraft.getInstance();
                mc.getToasts().clear();
                mc.setScreen(new TooltipShot(shown));
            });
        }));
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
