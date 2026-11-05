package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.hoanna.LeyVentBlock;
import com.flameforge.essentialcraft5.block.hoanna.NestCoreBlock;
import com.flameforge.essentialcraft5.block.hoanna.TideBudBlock;
import com.flameforge.essentialcraft5.block.hoanna.UmbralClusterBlock;
import com.flameforge.essentialcraft5.client.hoanna.HoannaClientEvents;
import com.flameforge.essentialcraft5.client.hoanna.HoannaEventHud;
import com.flameforge.essentialcraft5.entity.LeyStarfallEntity;
import com.flameforge.essentialcraft5.entity.anim.MobAnimStates;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.entity.mob.hoanna.AbyssalEelEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.CatacombRevenantEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.ChaosAberrantEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.EmberSpiritEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.FrostbittenEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.HoannaScavengerEntity;
import com.flameforge.essentialcraft5.entity.mob.hoanna.ManaPrismEntity;
import com.flameforge.essentialcraft5.hoanna.Radiation;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.item.HoannaSurveyMapItem;
import com.flameforge.essentialcraft5.item.ember.EmberKind;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaFramework;
import com.flameforge.essentialcraft5.registry.ECHoannaMaterials;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesA;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesB;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC;
import com.flameforge.essentialcraft5.registry.ECHoannaResources;
import com.flameforge.essentialcraft5.registry.ECHoannaSurvey;
import com.flameforge.essentialcraft5.registry.ECMaterials;
import com.flameforge.essentialcraft5.registry.ECMobDrops;
import com.flameforge.essentialcraft5.worldgen.hoanna.feature.HoannaMidscape;
import com.flameforge.essentialcraft5.worldgen.hoanna.feature.MilestoneFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * 展示厅的第十三批 13d 部分（海纳尔世界，只在 -PecShowroom13d 时跑；batch13.md §12.3 的 13d 行）。截图名都以 {@code b13d_} 开头，存成 {@code ec5_b13d_*.png}。
 * <p>
 * <b>主世界场景格</b>（和 13c 一样沿 X 排开、镜头从南边朝北拍，怪不带 AI、脸朝南；普通在左、精英在右；定格姿势的做法同 {@link DevShowroomBatch13c}）：
 * <ul>
 *   <li>{@code b13d_blocks_*}：特产方块排：喷口 charge 0~3、潮晶芽四阶、辐晶簇贴一块石头的六面、影晶簇成熟 / 残簇（地面、侧墙、天花板）、榍石矿、霜髓、
 *       渊盐结壳；白天一张、夜里一张（喷口亮度 0 / 2 / 4 / 6）；</li>
 *   <li>{@code b13d_nest_cores_*}：三块巢穴核心 armed / active / dormant 一字排开，白天、夜里各一张；</li>
 *   <li>{@code b13d_milestone}：一根里程碑柱（三格强化石 + 顶上里程碑）；</li>
 *   <li>{@code b13d_<怪>_*}：八种本地怪各四个关键姿势 + 精英（畸变母巢旁边两只幼体；余烬灵八种余烬一排；渊鳗在 3 × 3 × 10 的玻璃水箱里、镜头在水下）。</li>
 * </ul>
 * <b>真海纳尔</b>（取景点由 {@link DevShowroomHoanna#prepareSitesOnly} 按噪声找，镜头周围等区块收齐再拍）：七个群系各一张小景观（白天）；
 * 三种中景观（{@link HoannaMidscape#nearest} 找最近一处，远景俯拍 + 核心近景）；地脉风暴（夜空、白天天色、落晶、展开的提示条）；大潮夜光幕对新月；
 * 辐射热点的细尘（恐惧之地取景点附近底数 ≥ 4 的区块）；勘测图光柱和动作栏（夜里，离城镇约 200 格，手里拿着标好这座城的图）；
 * 日阵中露天的榍石矿、黑暗中的影晶簇（在沙漠 / 暗影取景点的地面上摆几块，画面上的天灾用客户端覆盖）。
 * <p>
 * <b>界面</b>：勘测图三态、残破勘测图、渊盐、六样特产、巢穴核心、异变两样的提示框。手册页见 {@link DevShowroom} 的 {@code batch13dGuidePages}。
 * 姿势的秒数照各路给的关键帧时刻估的，拍出来不对改这里的数，不动动画。
 */
final class DevShowroomBatch13d {

    private DevShowroomBatch13d() {
    }

    /** 拍完删掉的实体 */
    private static final List<Entity> SPAWNED = new ArrayList<>();
    /** 定过格的实体（客户端收尾时解冻） */
    private static final List<Integer> FROZEN = new ArrayList<>();
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
        int i = firstIndex;
        buildBlocks(level, sceneBase.apply(i++));
        buildNestCores(level, sceneBase.apply(i++));
        buildMilestone(level, sceneBase.apply(i++));
        buildFrostbitten(level, sceneBase.apply(i++));
        buildPrisms(level, sceneBase.apply(i++));
        buildScavengers(level, sceneBase.apply(i++));
        buildAberrants(level, sceneBase.apply(i++));
        buildEmbers(level, sceneBase.apply(i++));
        buildEels(level, sceneBase.apply(i++));
        buildPatrollers(level, sceneBase.apply(i++));
        buildRevenants(level, sceneBase.apply(i++));
        buildScreens();
        ServerLevel hoanna = DevShowroomHoanna.prepareSitesOnly(level);
        if (hoanna != null) {
            buildHoannaScenes(hoanna);
        }
    }

    /** 服务端：删掉生成的实体、时间调回去（DevShowroom.finishScenes 调；没跑过 build 时什么都不做） */
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
        restoreDayTime(player.getServer().overworld());
        player.removeEffect(MobEffects.NIGHT_VISION);
    }

    /** 客户端：场景拍完解冻（DevShowroom 场景收尾时调） */
    static void clientFinish(Minecraft mc) {
        if (!built) {
            return;
        }
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
        HoannaClientEvents.clearShowroomOverride();
        HoannaEventHud.clearShowroomTime();
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

    /** 普通 + 精英两只（普通在左 dx −1、精英在右 dx +2，离地 dy），返回两只的实体编号；elite 那只额外做 eliteSetup */
    private static <T extends ECMonster> List<Integer> pair(ServerLevel level, EntityType<T> type, BlockPos base, double dy, @Nullable Consumer<T> setup,
                                                          List<Integer> eliteOut) {
        List<Integer> ids = new ArrayList<>();
        for (boolean isElite : new boolean[]{false, true}) {
            T mob = spawn(level, type, at(base, isElite ? 2.0 : -1.0, dy, -3.5), m -> {
                m.setElite(isElite);
                if (setup != null) {
                    setup.accept(m);
                }
            });
            if (mob != null) {
                ids.add(mob.getId());
                if (isElite) {
                    eliteOut.add(mob.getId());
                }
            }
        }
        return ids;
    }

    @Nullable
    private static MobAnimStates anims(@Nullable Entity entity) {
        if (entity instanceof FrostbittenEntity e) {
            return e.anims;
        }
        if (entity instanceof ManaPrismEntity e) {
            return e.anims;
        }
        if (entity instanceof HoannaScavengerEntity e) {
            return e.anims;
        }
        if (entity instanceof ChaosAberrantEntity e) {
            return e.anims;
        }
        if (entity instanceof EmberSpiritEntity e) {
            return e.anims;
        }
        if (entity instanceof AbyssalEelEntity e) {
            return e.anims;
        }
        if (entity instanceof CatacombRevenantEntity e) {
            return e.anims;
        }
        return null;
    }

    /** 客户端：这几只都定在第 slot 段的第 seconds 秒（slot &lt; 0 = 不放动画，回到待机姿势） */
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

    /** 服务端：改时间，并马上把时间发给客户端（原版每 20 刻才同步一次，来不及就拍成了白天）；第一次改时记下原来的时间 */
    private static void setDayTime(ServerLevel level, long time) {
        if (dayTimeBefore < 0L) {
            dayTimeBefore = level.getDayTime();
        }
        level.setDayTime(time);
        ClientboundSetTimePacket packet = new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(),
                level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT));
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }

    private static void restoreDayTime(ServerLevel level) {
        if (dayTimeBefore >= 0L) {
            long before = dayTimeBefore;
            dayTimeBefore = -1L;
            level.setDayTime(before);
        }
    }

    private static Consumer<ServerPlayer> atTime(long time) {
        return p -> setDayTime(p.getServer().overworld(), time);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }

    // ================================================================ 方块

    /**
     * 特产方块排（离镜头 6 格、一排 11 列）：喷口 charge 0~3、潮晶芽四阶、一块石头六面都贴辐晶簇、影晶簇（地上成熟 / 残簇、侧墙、天花板）、
     * 榍石矿、霜髓、渊盐结壳
     */
    private static void buildBlocks(ServerLevel level, BlockPos base) {
        BlockPos row = base.offset(-5, 0, -5);
        for (int c = 0; c <= LeyVentBlock.MAX_CHARGE; c++) {
            set(level, row.offset(c, 0, 0), ECHoannaResources.LEY_VENT.get().defaultBlockState().setValue(LeyVentBlock.CHARGE, c));
        }
        for (int s = 0; s <= TideBudBlock.MAX_STAGE; s++) {
            set(level, row.offset(s, 0, 2).below(), Blocks.STONE.defaultBlockState());
            set(level, row.offset(s, 0, 2), ECHoannaResources.TIDE_BUD.get().defaultBlockState().setValue(TideBudBlock.STAGE, s));
        }
        // 辐晶簇：一块石头悬在 1 格高，六面各贴一簇（朝外）
        BlockPos stone = row.offset(6, 1, 0);
        set(level, stone, Blocks.STONE.defaultBlockState());
        for (Direction face : Direction.values()) {
            set(level, stone.relative(face), ECHoannaResources.RADIANT_CLUSTER.get().defaultBlockState().setValue(BlockStateProperties.FACING, face));
        }
        // 影晶簇：地上成熟 / 残簇；一面石墙上横着一簇、墙顶下面倒挂一簇
        BlockPos umbral = row.offset(9, 0, 0);
        BlockState ripe = ECHoannaResources.UMBRAL_CLUSTER.get().defaultBlockState().setValue(UmbralClusterBlock.RIPE, true);
        set(level, umbral, ripe);
        set(level, umbral.east(), ripe.setValue(UmbralClusterBlock.RIPE, false));
        for (int y = 0; y < 3; y++) {
            set(level, umbral.offset(0, y, -1), Blocks.STONE.defaultBlockState());
            set(level, umbral.offset(1, y, -1), Blocks.STONE.defaultBlockState());
        }
        set(level, umbral.offset(0, 2, 0), Blocks.STONE.defaultBlockState());
        set(level, umbral.offset(0, 1, 0), ripe.setValue(BlockStateProperties.FACING, Direction.DOWN));
        set(level, umbral.offset(1, 1, 0), ripe.setValue(BlockStateProperties.FACING, Direction.SOUTH));
        // 榍石矿、霜髓、渊盐结壳
        set(level, row.offset(6, 0, 2), ECHoannaResources.TITANITE_ORE.get().defaultBlockState());
        set(level, row.offset(7, 0, 2), ECHoannaResources.TITANITE_ORE.get().defaultBlockState());
        set(level, row.offset(8, 0, 2), ECHoannaMaterials.FROST_MARROW.get().defaultBlockState());
        set(level, row.offset(9, 0, 2), ECHoannaSurvey.ABYSSAL_SALT_CRUST.get().defaultBlockState());
        set(level, row.offset(10, 0, 2), ECHoannaSurvey.ABYSSAL_SALT_CRUST.get().defaultBlockState());

        Vec3 eye = at(base, 0.5, 4.0, 3.5);
        Vec3 look = at(base, 0.5, 0.5, -4.0);
        scene("b13d_blocks_day", atTime(6000), eye, look, 15, null);
        scene("b13d_blocks_night", atTime(18000), eye, look, 15, null);
        // 喷口一排近景（夜里看亮度 0 / 2 / 4 / 6，charge ≥ 2 的冒淡紫细尘）
        scene("b13d_vents_night", atTime(18000), at(base, -3.0, 2.5, -1.5), at(base, -3.0, 0.5, -5.0), 30, null);
        scene("b13d_tide_buds", atTime(6000), at(base, -3.0, 1.2, 0.0), at(base, -3.0, 0.3, -3.0), 10, null);
        scene("b13d_radiant_six_faces", atTime(6000), at(base, 1.5, 2.2, -1.5), at(base, 1.5, 1.5, -5.0), 10, null);
        scene("b13d_umbral_clusters_night", atTime(18000), at(base, 5.0, 1.6, -1.5), at(base, 5.0, 1.0, -5.0), 20, null);
    }

    /** 三块巢穴核心 armed / active / dormant，平视略俯 */
    private static void buildNestCores(ServerLevel level, BlockPos base) {
        NestCoreBlock.Phase[] phases = NestCoreBlock.Phase.values();
        for (int k = 0; k < phases.length; k++) {
            set(level, base.offset(-2 + k * 2, 0, -3), ECHoannaFramework.NEST_CORE.get().defaultBlockState().setValue(NestCoreBlock.PHASE, phases[k]));
        }
        Vec3 eye = at(base, 0.5, 2.2, 1.5);
        Vec3 look = at(base, 0.5, 0.5, -3.0);
        scene("b13d_nest_cores_day", atTime(6000), eye, look, 15, null);
        scene("b13d_nest_cores_night", atTime(18000), eye, look, 15, null);
    }

    /** 一根里程碑柱：三格强化石 + 顶上里程碑，白天平视略俯 */
    private static void buildMilestone(ServerLevel level, BlockPos base) {
        // 直接摆（MilestoneFeature.placeAt 只认世界生成时的可写范围）；层数照它的 HEIGHT
        BlockPos foot = base.offset(0, 0, -3);
        for (int y = 0; y < MilestoneFeature.HEIGHT - 1; y++) {
            set(level, foot.above(y), ECMaterials.FORTIFIED_STONE.get().defaultBlockState());
        }
        set(level, foot.above(MilestoneFeature.HEIGHT - 1), ECHoannaSurvey.RUIN_MILESTONE.get().defaultBlockState());
        scene("b13d_milestone", atTime(6000), at(base, 0.5, 3.5, 0.5), at(base, 0.5, 2.0, -3.0), 10, null);
    }

    // ================================================================ 八种本地怪

    private static void buildFrostbitten(ServerLevel level, BlockPos base) {
        List<Integer> elite = new ArrayList<>();
        List<Integer> both = pair(level, ECHoannaNativesA.FROSTBITTEN.get(), base, 0, null, elite);
        Vec3 eye = at(base, 0.5, 2.0, 1.5);
        Vec3 look = at(base, 0.5, 1.2, -3.5);
        scene("b13d_frostbitten_idle", atTime(6000), eye, look, 10, pose(ids(both), -1, 0F));
        scene("b13d_frostbitten_attack", null, eye, look, 5, pose(ids(both), FrostbittenEntity.ANIM_ATTACK, 0.2F));
        scene("b13d_frostbitten_breath_charge", null, eye, look, 5, pose(ids(both), FrostbittenEntity.ANIM_BREATH, 0.75F));
        scene("b13d_frostbitten_breath_release", null, eye, look, 5, pose(ids(both), FrostbittenEntity.ANIM_BREATH, 1.05F));
        scene("b13d_frostbitten_elite_seal", null, eye, look, 5, pose(ids(elite), FrostbittenEntity.ANIM_SEAL, 0.5F));
    }

    private static void buildPrisms(ServerLevel level, BlockPos base) {
        List<Integer> elite = new ArrayList<>();
        List<Integer> both = pair(level, ECHoannaNativesA.MANA_PRISM.get(), base, 1.5, null, elite);
        Vec3 eye = at(base, 0.5, 2.6, 1.5);
        Vec3 look = at(base, 0.5, 2.0, -3.5);
        scene("b13d_mana_prism_idle", atTime(18000), eye, look, 10, pose(ids(both), -1, 0F));
        scene("b13d_mana_prism_lock", null, eye, look, 5, pose(ids(both), ManaPrismEntity.ANIM_LOCK, 1.0F));
        scene("b13d_mana_prism_fire", null, eye, look, 5, pose(ids(both), ManaPrismEntity.ANIM_FIRE, 0.1F));
        scene("b13d_mana_prism_elite_fire", null, eye, look, 5, pose(ids(elite), ManaPrismEntity.ANIM_FIRE, 0.1F));
    }

    private static void buildScavengers(ServerLevel level, BlockPos base) {
        List<Integer> elite = new ArrayList<>();
        List<Integer> crowbar = pair(level, ECHoannaNativesA.HOANNA_SCAVENGER.get(), base, 0, m -> m.setWeapon(HoannaScavengerEntity.Weapon.CROWBAR), elite);
        HoannaScavengerEntity rifleman = spawn(level, ECHoannaNativesA.HOANNA_SCAVENGER.get(), at(base, 5.0, 0, -3.5),
                m -> m.setWeapon(HoannaScavengerEntity.Weapon.RIFLE));
        List<Integer> rifle = rifleman == null ? List.of() : List.of(rifleman.getId());
        Vec3 eye = at(base, 2.0, 2.0, 2.0);
        Vec3 look = at(base, 2.0, 1.2, -3.5);
        scene("b13d_scavenger_idle", atTime(6000), eye, look, 10, pose(ids(crowbar), -1, 0F).andThen(pose(ids(rifle), -1, 0F)));
        scene("b13d_scavenger_swing", null, eye, look, 5, pose(ids(crowbar), HoannaScavengerEntity.ANIM_SWING, 0.15F));
        scene("b13d_scavenger_aim", null, eye, look, 5, pose(ids(rifle), HoannaScavengerEntity.ANIM_AIM, 1.0F));
        scene("b13d_scavenger_stim", null, eye, look, 5, pose(ids(crowbar), HoannaScavengerEntity.ANIM_STIM, 0.45F));
        scene("b13d_scavenger_elite_flare", null, eye, look, 5, pose(ids(elite), HoannaScavengerEntity.ANIM_FLARE, 0.5F));
    }

    private static void buildAberrants(ServerLevel level, BlockPos base) {
        List<Integer> elite = new ArrayList<>();
        List<Integer> both = pair(level, ECHoannaNativesB.CHAOS_ABERRANT.get(), base, 0, null, elite);
        for (double dx : new double[]{3.6, 4.4}) {
            spawn(level, ECHoannaNativesB.CHAOS_LARVA.get(), at(base, dx, 0, -2.5), null);
        }
        Vec3 eye = at(base, 1.0, 2.4, 2.5);
        Vec3 look = at(base, 1.0, 0.8, -3.5);
        scene("b13d_chaos_aberrant_idle", atTime(6000), eye, look, 10, pose(ids(both), -1, 0F));
        scene("b13d_chaos_aberrant_windup", null, eye, look, 5, pose(ids(both), ChaosAberrantEntity.ANIM_WINDUP, 0.5F));
        scene("b13d_chaos_aberrant_dash", null, eye, look, 5, pose(ids(both), ChaosAberrantEntity.ANIM_DASH, 0.3F));
        scene("b13d_chaos_aberrant_stun", null, eye, look, 5, pose(ids(both), ChaosAberrantEntity.ANIM_STUN, 0.5F));
        scene("b13d_chaos_aberrant_elite_brood", null, eye, look, 5, pose(ids(elite), ChaosAberrantEntity.ANIM_BROOD, 0.5F));
    }

    /** 八种余烬各一只排成一排（看染色），右边一只余烬双生 */
    private static void buildEmbers(ServerLevel level, BlockPos base) {
        List<Integer> row = new ArrayList<>();
        EmberKind[] kinds = EmberKind.values();
        for (int k = 0; k < kinds.length; k++) {
            EmberKind kind = kinds[k];
            EmberSpiritEntity spirit = spawn(level, ECHoannaNativesB.EMBER_SPIRIT.get(), at(base, -6.0 + k * 1.5, 1.2, -4.0), m -> m.setEmber(kind));
            if (spirit != null) {
                row.add(spirit.getId());
            }
        }
        EmberSpiritEntity twins = spawn(level, ECHoannaNativesB.EMBER_SPIRIT.get(), at(base, 7.0, 1.2, -4.0), m -> {
            m.setElite(true);
            m.setEmber(EmberKind.FLAME);
            m.setSecondEmber(EmberKind.DIVINE);
        });
        List<Integer> elite = twins == null ? List.of() : List.of(twins.getId());
        Vec3 eye = at(base, 0.5, 2.4, 5.0);
        Vec3 look = at(base, 0.5, 1.6, -4.0);
        scene("b13d_ember_spirit_row", atTime(18000), eye, look, 10, pose(ids(row), -1, 0F).andThen(pose(ids(elite), -1, 0F)));
        scene("b13d_ember_spirit_cast", null, eye, look, 5, pose(ids(row), EmberSpiritEntity.ANIM_CAST, 0.3F));
        scene("b13d_ember_spirit_elite_volley", null, at(base, 6.0, 2.0, -1.0), at(base, 7.0, 1.6, -4.0), 5,
                pose(ids(elite), EmberSpiritEntity.ANIM_VOLLEY, 0.3F));
    }

    /** 3 × 3 × 10（东西长）的玻璃水箱，两条渊鳗（普通 / 群首）在里面；镜头也在水里（夜视） */
    private static void buildEels(ServerLevel level, BlockPos base) {
        BlockPos min = base.offset(-5, 0, -6);
        for (int x = -1; x <= 10; x++) {
            for (int y = -1; y <= 3; y++) {
                for (int z = -1; z <= 3; z++) {
                    boolean wall = x == -1 || x == 10 || y == -1 || y == 3 || z == -1 || z == 3;
                    set(level, min.offset(x, y, z), wall ? Blocks.GLASS.defaultBlockState() : Blocks.WATER.defaultBlockState());
                }
            }
        }
        List<Integer> both = new ArrayList<>();
        List<Integer> elite = new ArrayList<>();
        for (boolean isElite : new boolean[]{false, true}) {
            AbyssalEelEntity eel = spawn(level, ECHoannaNativesB.ABYSSAL_EEL.get(), Vec3.atBottomCenterOf(min.offset(isElite ? 6 : 2, 1, 1)),
                    m -> m.setElite(isElite));
            if (eel != null) {
                both.add(eel.getId());
                if (isElite) {
                    elite.add(eel.getId());
                }
            }
        }
        // 搁浅那张：箱子外的地上再放一条
        AbyssalEelEntity stranded = spawn(level, ECHoannaNativesB.ABYSSAL_EEL.get(), at(base, 7.0, 0, -1.0), null);
        List<Integer> dry = stranded == null ? List.of() : List.of(stranded.getId());
        Vec3 eye = Vec3.atCenterOf(min.offset(8, 1, 1));
        Vec3 look = Vec3.atCenterOf(min.offset(1, 1, 1));
        Consumer<ServerPlayer> underwater = atTime(6000).andThen(p -> p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 1200, 0, false, false)));
        scene("b13d_abyssal_eel_swim", underwater, eye, look, 15, pose(ids(both), -1, 0F));
        scene("b13d_abyssal_eel_bite", null, eye, look, 5, pose(ids(both), AbyssalEelEntity.ANIM_BITE, 0.2F));
        scene("b13d_abyssal_eel_drag", null, eye, look, 5, pose(ids(both), AbyssalEelEntity.ANIM_DRAG, 0.5F));
        scene("b13d_abyssal_eel_elite_call", null, eye, look, 5, pose(ids(elite), AbyssalEelEntity.ANIM_CALL, 0.5F));
        scene("b13d_abyssal_eel_stranded", p -> p.removeEffect(MobEffects.NIGHT_VISION), at(base, 7.0, 1.8, 2.0), at(base, 7.0, 0.3, -1.0), 5,
                pose(ids(dry), -1, 0F));
    }

    /** 巡卫没有关键帧动画（全息的模型 + 同步的施法姿势），只拍普通 / 执行机并排悬着，白天、夜里各一张 */
    private static void buildPatrollers(ServerLevel level, BlockPos base) {
        for (boolean isElite : new boolean[]{false, true}) {
            spawn(level, ECHoannaNativesC.INQUISITORIUM_PATROLLER.get(), at(base, isElite ? 2.0 : -1.0, 2.0, -3.5), m -> m.setElite(isElite));
        }
        Vec3 eye = at(base, 0.5, 2.8, 1.5);
        Vec3 look = at(base, 0.5, 2.6, -3.5);
        scene("b13d_patroller_day", atTime(6000), eye, look, 10, null);
        scene("b13d_patroller_night", atTime(18000), eye, look, 10, null);
    }

    private static void buildRevenants(ServerLevel level, BlockPos base) {
        List<Integer> elite = new ArrayList<>();
        List<Integer> both = pair(level, ECHoannaNativesC.CATACOMB_REVENANT.get(), base, 0, null, elite);
        Vec3 eye = at(base, 0.5, 2.0, 1.5);
        Vec3 look = at(base, 0.5, 1.2, -3.5);
        scene("b13d_revenant_idle", atTime(18000), eye, look, 10, pose(ids(both), -1, 0F));
        scene("b13d_revenant_slash", null, eye, look, 5, pose(ids(both), CatacombRevenantEntity.ANIM_SLASH, 0.3F));
        scene("b13d_revenant_elite_call", null, eye, look, 5, pose(ids(elite), CatacombRevenantEntity.ANIM_CALL, 0.5F));
    }

    // ================================================================ 真海纳尔

    private static void buildHoannaScenes(ServerLevel hoanna) {
        // 七个群系各一张小景观：白天、无天灾，略俯
        for (ResourceKey<Biome> biome : ECHoanna.BIOMES) {
            BlockPos site = DevShowroomHoanna.site(biome);
            if (site == null) {
                continue;
            }
            DevShowroomHoanna.hoanna("b13d_landscape_" + biome.location().getPath(), hoanna, site, 6000, DevShowroomHoanna.ARRIVE_WAIT, false,
                    null, null, lowLook(14), new ShowroomChunkWait(ECHoanna.LEVEL));
        }
        BlockPos magic = DevShowroomHoanna.site(ECHoanna.MAGIC_CORRUPTION);
        if (magic != null) {
            buildStormScenes(hoanna, magic);
        }
        buildMidscapes(hoanna);
        buildHotspot(hoanna);
        buildSurveyBeam(hoanna);
        buildCalamityWindows(hoanna);
    }

    /** 离取景点 dist 格、离地 dist / 2 格，往取景点俯看 */
    private static DevShowroomHoanna.Look lowLook(int dist) {
        return (level, site) -> {
            int ground = DevShowroomHoanna.ground(level, site.getX(), site.getZ());
            Vec3 target = new Vec3(site.getX() + 0.5, ground, site.getZ() + 0.5);
            Vec3 eye = target.add(0, dist / 2.0, dist);
            return new Vec3[]{eye, target};
        };
    }

    /** 地脉风暴（展示厅覆盖）：夜空仰角 30° 朝北、白天同机位、落晶、展开的提示条；大潮夜光幕对新月（不开风暴） */
    private static void buildStormScenes(ServerLevel hoanna, BlockPos site) {
        DevShowroomHoanna.Look sky = (level, s) -> {
            int ground = DevShowroomHoanna.ground(level, s.getX(), s.getZ());
            Vec3 eye = new Vec3(s.getX() + 0.5, ground + 2.6, s.getZ() + 0.5);
            return new Vec3[]{eye, eye.add(0, 57.7, -100)};
        };
        Consumer<Minecraft> storm = mc -> {
            HoannaClientEvents.setShowroomOverride(HoannaEvent.LEY_STORM, 1F);
            HoannaClientEvents.setShowroomTide(1F);
        };
        DevShowroomHoanna.hoanna("b13d_ley_storm_night_sky", hoanna, site, 18000, DevShowroomHoanna.ARRIVE_WAIT, false, storm, null, sky,
                new ShowroomChunkWait(ECHoanna.LEVEL));
        DevShowroomHoanna.hoanna("b13d_ley_storm_noon", hoanna, site, 6000, 40, false, storm, null, sky);
        // 落晶：玩家前方 6 格地面上停着 3 颗，8 格高处一颗正在落
        Consumer<ServerPlayer> starfalls = p -> {
            int ground = DevShowroomHoanna.ground(hoanna, site.getX(), site.getZ() - 6);
            for (int k = -1; k <= 1; k++) {
                double x = site.getX() + 0.5 + k * 1.5;
                double z = site.getZ() - 5.5;
                addStarfall(hoanna, x, ground + 1, z, ground + 1);
            }
            addStarfall(hoanna, site.getX() + 2.5, ground + 9, site.getZ() - 7.5, ground + 1);
        };
        DevShowroomHoanna.Look ahead = (level, s) -> {
            int ground = DevShowroomHoanna.ground(level, s.getX(), s.getZ());
            Vec3 eye = new Vec3(s.getX() + 0.5, ground + 2.0, s.getZ() + 1.5);
            return new Vec3[]{eye, new Vec3(s.getX() + 0.5, ground + 1.5, s.getZ() - 6.0)};
        };
        DevShowroomHoanna.hoanna("b13d_ley_starfall", hoanna, site, 18000, 40, false, storm, starfalls, ahead);
        DevShowroomHoanna.hoanna("b13d_ley_storm_hud", hoanna, site, 18000, 20, true,
                storm.andThen(mc -> HoannaEventHud.setShowroomTime(3_000, 6_000, true)), null, sky);
        DevShowroomHoanna.hoanna("b13d_aurora_spring_tide", hoanna, site, 18000, 20, false, mc -> HoannaClientEvents.setShowroomTide(1F), null, sky);
        DevShowroomHoanna.hoanna("b13d_aurora_new_moon", hoanna, site, 18000, 20, false, mc -> HoannaClientEvents.setShowroomTide(0F), null, sky);
    }

    private static void addStarfall(ServerLevel level, double x, double y, double z, double targetY) {
        LeyStarfallEntity starfall = new LeyStarfallEntity(level, x, y, z, targetY, null);
        if (level.addFreshEntity(starfall)) {
            SPAWNED.add(starfall);
        }
    }

    /** 三种中景观：从魔力取景点（没有就冰冻 / 混沌 / 恐惧之地的）起找最近一处；远景离中心 25 格、高 10 格俯拍，近景对着核心 */
    private static void buildMidscapes(ServerLevel hoanna) {
        BlockPos from = DevShowroomHoanna.site(ECHoanna.MAGIC_CORRUPTION);
        if (from == null) {
            from = DevShowroomHoanna.site(ECHoanna.FROZEN_CORRUPTION);
        }
        if (from == null) {
            EssentialCraft5.LOGGER.warn("[showroom] 没有取景点，b13d_midscape_* 跳过");
            return;
        }
        long seed = hoanna.getSeed();
        var source = hoanna.getChunkSource().getGenerator().getBiomeSource();
        var sampler = hoanna.getChunkSource().randomState().sampler();
        int seaLevel = hoanna.getChunkSource().getGenerator().getSeaLevel();
        for (HoannaMidscape.Kind kind : HoannaMidscape.Kind.values()) {
            ChunkPos chunk = HoannaMidscape.nearest(seed, source, sampler, seaLevel, new ChunkPos(from), kind, 40);
            if (chunk == null) {
                EssentialCraft5.LOGGER.warn("[showroom] 40 圈内找不到中景观 {}", kind);
                continue;
            }
            BlockPos center = new BlockPos(chunk.getMinBlockX() + 8, 0, chunk.getMinBlockZ() + 8);
            DevShowroomHoanna.ticket(hoanna, center, 3);
            String name = kind.name().toLowerCase(java.util.Locale.ROOT);
            int time = kind == HoannaMidscape.Kind.CHAOS_FISSURE ? 12500 : 6000;
            DevShowroomHoanna.Look far = (level, s) -> {
                int ground = DevShowroomHoanna.ground(level, s.getX(), s.getZ());
                Vec3 target = new Vec3(s.getX() + 0.5, ground, s.getZ() + 0.5);
                return new Vec3[]{target.add(0, 10, 25), target};
            };
            DevShowroomHoanna.hoanna("b13d_midscape_" + name + "_far", hoanna, center, time, DevShowroomHoanna.ARRIVE_WAIT, false, null, null, far,
                    new ShowroomChunkWait(ECHoanna.LEVEL));
            DevShowroomHoanna.hoanna("b13d_midscape_" + name + "_core", hoanna, center, time, 20, false, null, null, lowLook(8));
        }
    }

    /** 辐射热点的细尘：恐惧之地取景点附近找一个底数 ≥ 4 的区块，低机位站在里面等两秒 */
    private static void buildHotspot(ServerLevel hoanna) {
        BlockPos dread = DevShowroomHoanna.site(ECHoanna.DREADLANDS);
        if (dread == null) {
            return;
        }
        ChunkPos center = new ChunkPos(dread);
        ChunkPos hot = null;
        for (int r = 0; r <= 8 && hot == null; r++) {
            for (int dx = -r; dx <= r && hot == null; dx++) {
                for (int dz = -r; dz <= r && hot == null; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r && Radiation.chunkBase(center.x + dx, center.z + dz) >= 4) {
                        hot = new ChunkPos(center.x + dx, center.z + dz);
                    }
                }
            }
        }
        if (hot == null) {
            EssentialCraft5.LOGGER.warn("[showroom] 恐惧之地取景点附近 8 圈没有底数 ≥ 4 的区块，b13d_hotspot_dust 跳过");
            return;
        }
        BlockPos site = new BlockPos(hot.getMinBlockX() + 8, 0, hot.getMinBlockZ() + 8);
        DevShowroomHoanna.ticket(hoanna, site, 2);
        DevShowroomHoanna.hoanna("b13d_hotspot_dust", hoanna, site, 6000, DevShowroomHoanna.ARRIVE_WAIT, false, null, null, lowLook(4),
                new ShowroomChunkWait(ECHoanna.LEVEL));
    }

    /** 勘测图光柱：夜里站在城镇外约 200 格（西边），主手拿着标好这座城的图，开着 HUD（动作栏写方向距离） */
    private static void buildSurveyBeam(ServerLevel hoanna) {
        ChunkPos town = DevShowroomHoanna.townChunk();
        if (town == null) {
            EssentialCraft5.LOGGER.warn("[showroom] 没挑到城镇区块，b13d_survey_beam_* 跳过");
            return;
        }
        BlockPos townCenter = new BlockPos(town.getMinBlockX() + 8, 0, town.getMinBlockZ() + 8);
        BlockPos stand = townCenter.offset(-200, 0, 0);
        DevShowroomHoanna.ticket(hoanna, stand, 3);
        Consumer<ServerPlayer> holdMap = p -> {
            ItemStack map = new ItemStack(ECHoannaSurvey.HOANNA_SURVEY_MAP.get());
            map.getOrCreateTag().putString(HoannaSurveyMapItem.TAG_KIND, HoannaSurveyMapItem.TOWN.id());
            HoannaSurveyMapItem.bind(map, townCenter);
            p.getInventory().items.set(p.getInventory().selected, map);
            p.inventoryMenu.broadcastChanges();
        };
        DevShowroomHoanna.Look towardTown = (level, s) -> {
            int ground = DevShowroomHoanna.ground(level, s.getX(), s.getZ());
            Vec3 eye = new Vec3(s.getX() + 0.5, ground + 3.0, s.getZ() + 0.5);
            int townTop = Math.max(63, DevShowroomHoanna.estimatedSurface(level, townCenter.getX(), townCenter.getZ()));
            return new Vec3[]{eye, new Vec3(townCenter.getX() + 0.5, townTop + 40, townCenter.getZ() + 0.5)};
        };
        DevShowroomHoanna.hoanna("b13d_survey_beam_night", hoanna, stand, 18000, DevShowroomHoanna.ARRIVE_WAIT, true, null, holdMap, towardTown,
                new ShowroomChunkWait(ECHoanna.LEVEL));
        DevShowroomHoanna.hoanna("b13d_survey_beam_darkness_day", hoanna, stand, 6000, 30, true,
                mc -> HoannaClientEvents.setShowroomOverride(HoannaEvent.DARKNESS, 1F), holdMap, towardTown);
        DevShowroomHoanna.hoanna("b13d_survey_actionbar_day", hoanna, stand, 6000, 30, true, null, holdMap, towardTown);
    }

    /** 日阵中露天的榍石矿（沙漠取景点地面上摆 5 块）、黑暗中的影晶簇（暗影取景点地面上摆一片成熟 / 残簇） */
    private static void buildCalamityWindows(ServerLevel hoanna) {
        BlockPos desert = DevShowroomHoanna.site(ECHoanna.HOANNA_DESERT);
        if (desert != null) {
            Consumer<ServerPlayer> ores = p -> placeOnGround(hoanna, desert, (pos, k) -> ECHoannaResources.TITANITE_ORE.get().defaultBlockState(), true);
            DevShowroomHoanna.hoanna("b13d_sun_array_titanite", hoanna, desert, 6000, DevShowroomHoanna.ARRIVE_WAIT, false,
                    mc -> HoannaClientEvents.setShowroomOverride(HoannaEvent.SUN_ARRAY, 1F), ores, lowLook(5), new ShowroomChunkWait(ECHoanna.LEVEL));
        }
        BlockPos shadow = DevShowroomHoanna.site(ECHoanna.SHADOW_CORRUPTION);
        if (shadow != null) {
            BlockState ripe = ECHoannaResources.UMBRAL_CLUSTER.get().defaultBlockState();
            Consumer<ServerPlayer> clusters = p -> placeOnGround(hoanna, shadow,
                    (pos, k) -> ripe.setValue(UmbralClusterBlock.RIPE, k % 3 != 2), false);
            DevShowroomHoanna.hoanna("b13d_darkness_umbral", hoanna, shadow, 18000, DevShowroomHoanna.ARRIVE_WAIT, false,
                    mc -> HoannaClientEvents.setShowroomOverride(HoannaEvent.DARKNESS, 1F), clusters, lowLook(6), new ShowroomChunkWait(ECHoanna.LEVEL));
        }
    }

    @FunctionalInterface
    private interface StateAt {
        BlockState at(BlockPos pos, int index);
    }

    /** 取景点四周一圈 6 格（每隔一格）摆方块：replace = 替换地表那一格，否则放在地表上一格 */
    private static void placeOnGround(ServerLevel level, BlockPos site, StateAt state, boolean replace) {
        int k = 0;
        for (int dx = -2; dx <= 2; dx += 2) {
            for (int dz = -2; dz <= 0; dz += 2) {
                int x = site.getX() + dx;
                int z = site.getZ() + dz;
                int top = DevShowroomHoanna.ground(level, x, z);
                BlockPos pos = replace ? new BlockPos(x, top - 1, z) : new BlockPos(x, top, z);
                level.setBlock(pos, state.at(pos, k++), Block.UPDATE_ALL);
            }
        }
    }

    // ================================================================ 界面

    private static void buildScreens() {
        tooltip("b13d_survey_map_blank", () -> new ItemStack(ECHoannaSurvey.HOANNA_SURVEY_MAP.get()));
        tooltip("b13d_survey_map_bound", () -> {
            ItemStack map = new ItemStack(ECHoannaSurvey.HOANNA_SURVEY_MAP.get());
            map.getOrCreateTag().putString(HoannaSurveyMapItem.TAG_KIND, HoannaSurveyMapItem.TOWN.id());
            HoannaSurveyMapItem.bind(map, BlockPos.ZERO);
            return map;
        });
        tooltip("b13d_survey_map_visited", () -> {
            ItemStack map = new ItemStack(ECHoannaSurvey.HOANNA_SURVEY_MAP.get());
            map.getOrCreateTag().putString(HoannaSurveyMapItem.TAG_KIND, HoannaSurveyMapItem.TOWN.id());
            HoannaSurveyMapItem.addSkip(map, ChunkPos.ZERO);
            return map;
        });
        tooltip("b13d_torn_survey_map", () -> new ItemStack(ECHoannaSurvey.TORN_SURVEY_MAP.get()));
        tooltip("b13d_abyssal_salt", () -> new ItemStack(ECHoannaSurvey.ABYSSAL_SALT.get()));
        tooltip("b13d_ley_crystal", () -> new ItemStack(ECHoannaMaterials.LEY_CRYSTAL.get()));
        tooltip("b13d_radiant_crystal", () -> new ItemStack(ECHoannaMaterials.RADIANT_CRYSTAL.get()));
        tooltip("b13d_tide_crystal", () -> new ItemStack(ECHoannaMaterials.TIDE_CRYSTAL.get()));
        tooltip("b13d_umbral_crystal", () -> new ItemStack(ECHoannaMaterials.UMBRAL_CRYSTAL.get()));
        tooltip("b13d_frost_marrow", () -> new ItemStack(ECHoannaMaterials.FROST_MARROW.get()));
        tooltip("b13d_nest_core", () -> new ItemStack(ECHoannaFramework.NEST_CORE.get()));
        tooltip("b13d_aberrant_residue", () -> new ItemStack(ECMobDrops.ABERRANT_RESIDUE.get()));
        tooltip("b13d_aberrant_heart", () -> new ItemStack(ECMobDrops.ABERRANT_HEART.get()));
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
