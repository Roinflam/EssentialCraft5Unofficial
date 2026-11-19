package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.registry.ECSolar;
import com.flameforge.essentialcraft5.structure.StructureCache;
import com.flameforge.essentialcraft5.structure.StructureResult;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import com.flameforge.essentialcraft5.util.ECLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 太阳棱镜。对应原作 TileSolarPrism（棱镜）+ EntitySolarBeam（光束实体）+ RenderSolarBeam（光束渲染）。
 *
 * <h2>行为</h2>
 * <ul>
 *   <li>摆法（{@link ECSolar#PRISM_STRUCTURE}）：同层东南西北隔一格各有一个方块、紧挨着的 8 格是空气。不满足就自毁掉落（D5）。</li>
 *   <li>露天（正上方看得到天空）、一天中的时间在 5000~7000 之间（正午前后）、没下雨时，每刻有 2.5% 的几率往正下方打一道光束。
 *       条件都可在配置里关掉，同原作。光束落在棱镜正下方第一个不是空气的方块上；棱镜正下方紧挨着的那格不是空气就打不出来（同原作）。</li>
 *   <li>一道光束持续 20 刻：让一台日光吸收器在这 20 刻里每刻产 500 MRU（一道光束 = 1 万 MRU，几道光束重叠不叠加，同原作）；
 *       落点周围水平 ±6、往下 6 往上 134 格内的敌对生物着火 5 秒，每 10 刻受 3 点火焰伤害（第十批起只烧敌对生物，见下）。</li>
 * </ul>
 *
 * <h2>移植改动</h2>
 * <ul>
 *   <li>光束不做实体：服务端直接通知吸收器，再把原版方块事件包（{@link ClientboundBlockEventPacket}）发给追踪棱镜所在区块的玩家，
 *       让他们的客户端画光柱（{@code SolarPrismRenderer}，水平 256 格内可见，同信标）、放声音。
 *       不用 {@code level.blockEvent}：它只广播给离棱镜 64 格（三维距离）以内的玩家，棱镜比吸收器高出 64 格以上时，站在吸收器旁的人
 *       看不到光柱也听不到声音；原作光束实体在落点、按落点追踪，站在吸收器旁总看得见。
 *       原作的光束实体会存进区块，重进世界时复活；1.12 的 EntityWeatherEffect 也会被存盘。</li>
 *   <li>★ 一道光束只给<b>一台</b>吸收器：落点就是吸收器时给它，否则给落点周围（水平 ±1、往下 1 往上 2，和原作的判定范围一样）最近的一台。
 *       原作光束实体用的是默认碰撞箱 0.6×1.8，吸收器又在自己周围 3×3×3 里找光束，一个棱镜能同时喂最多 34 台吸收器（改玩法，见报告）。
 *       多个棱镜照同一台吸收器的摆法照旧（错开高度，落点都在吸收器旁边）。</li>
 *   <li>摆法判定：原作两端都跑、每刻都跑，这里只在服务端、借框架 StructureCache 每 20 刻判一次（D5 仍是自毁）；
 *       有格子在没加载的区块里（结果 UNKNOWN）时不自毁。</li>
 *   <li>往下找落点：原作只找到 y&gt;0，这里到世界底部（{@code getMinBuildHeight}）。</li>
 *   <li>放火：原作光束落地会在周围放火，移植后不放（2026-09-28 起机器不自动放方块，policy §3.1；以前是默认关的配置 sets_fire，已删）。</li>
 *   <li>伤害（D4 原定「不看 PvP」，第十批改成只烧敌对生物，见本条末尾）：原作每刻查一次 12×140×12 的实体，靠无敌帧每 10 刻才真正扣一次血；这里直接每 10 刻查一次，结果相同。
 *       伤害类型 {@code damageSources().onFire()}（原作 DamageSource.ON_FIRE）。
 *       只打活着的生物（原作连掉落物、画、物品展示框一起打，它们挨一下就掉了/烧了）；盔甲架也不打（它算 LivingEntity，
 *       但火焰伤害每下直接扣 4 点、不吃无敌帧，原作几刻就烧毁，盔甲架物品不掉，只散出身上的装备）；
 *       创造模式、旁观者不打（本来就不掉血，原作只是让他们身上冒火）。
 *       第三批安全回补（全模组安全原则 P1，policy.md §5，P-7）：另外跳过被驯服的（有主人的狼、猫、鹦鹉、马……）、
 *       拴着绳的、起了名字的生物（{@link #sparedFromBeam}），免得光柱边上 6 格内邻居的宠物、拴着的牲口被烧；
 *       第三批审查 RB-3 又补上村民（含流浪商人）、玩家造的铁傀儡和雪傀儡、认了主人的悦灵（交易所里的村民很少有人起名字）；
 *       第四批 4a 审查 FW-7 补上装了鞍的骆驼（骆驼不用驯服就能骑、不记主人，没装鞍的照野生动物打）。
 *       <b>第十批权限修补 A2：只烧敌对生物</b>（{@link #mayBurn}，同反应堆第四批定稿的口径）：火焰伤害没有攻击者，PvP 开关、领地的 PvP / 动物保护都拦不住，
 *       落点 6 格内又会伸进邻居的领地，以前会烧到邻居没起名的牲口和路过的玩家。玩家、动物、村庄自己刷的铁傀儡都不再烧；
 *       想要原作那样连玩家一起烧的打开配置 {@code burn_players}（动物仍然不烧）。不分纯 Forge / 混合端。</li>
 *   <li>光柱画法：原作 renderBeamSegment 的内芯半径是 0（只剩外面一层 0.5 格的光晕），这里用信标同款 0.2 的内芯 + 0.5 的光晕，从落点画到世界顶部，黄色。</li>
 * </ul>
 */
public class SolarPrismBlockEntity extends BlockEntity {

    /** 原作 tileentities.solarprism */
    public static final class Config {
        public static ForgeConfigSpec.DoubleValue beamChance;
        public static ForgeConfigSpec.BooleanValue requiresSky;
        public static ForgeConfigSpec.BooleanValue requiresMidday;
        public static ForgeConfigSpec.BooleanValue ignoreRain;
        public static ForgeConfigSpec.BooleanValue burnPlayers;
        /** GameTest 用的 burn_players 覆盖值（null = 按配置；不用 ConfigValue.set，见 CorruptionEffects.setTestOverrides） */
        @Nullable
        private static volatile Boolean testBurnPlayers;

        /** 光束烧不烧玩家（配置 {@code burn_players}，默认 false） */
        public static boolean burnPlayers() {
            Boolean override = testBurnPlayers;
            return override != null ? override : burnPlayers != null && ECConfig.get(burnPlayers);
        }

        /** GameTest 用：临时覆盖 burn_players（传 null 恢复按配置），只在同一段同步代码里设、测、还原 */
        public static void setBurnPlayersForTest(@Nullable Boolean value) {
            testBurnPlayers = value;
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("solar_prism");
            beamChance = builder.comment("Chance each tick to fire a solar beam while the conditions hold.",
                            "条件满足时每刻打出一道太阳光束的几率")
                    .defineInRange("solar_beam_chance", 0.025D, 0D, 1D);
            requiresSky = builder.comment("Whether the prism must see the sky.", "棱镜正上方是否必须看得到天空")
                    .define("requires_unobstructed_sky", true);
            requiresMidday = builder.comment("Whether beams only fire around midday (day time 5000-7000).",
                            "是否只在正午前后（一天中的时间 5000~7000）出光束")
                    .define("requires_midday", true);
            ignoreRain = builder.comment("Whether beams also fire while it rains.", "下雨时是否照样出光束")
                    .define("ignore_rain", false);
            burnPlayers = builder.comment(
                            "Beams only burn hostile mobs. Turn this on to also burn players near the landing spot, like the original mod",
                            "(the fire has no attacker, so PvP settings and claims cannot stop it).",
                            "光束只烧敌对生物。打开后落点附近的玩家也烧，同原作（火焰伤害没有攻击者，PvP 开关和领地都拦不住）")
                    .define("burn_players", false);
            builder.pop();
        }
    }

    /** 一道光束持续的刻数（原作 beamLiveTime） */
    public static final int BEAM_DURATION = 20;
    /** 伤害间隔：和生物的无敌帧一致 */
    public static final int DAMAGE_INTERVAL = 10;
    /** 光束伤害（原作 attackEntityFrom(ON_FIRE, 3)） */
    public static final float BEAM_DAMAGE = 3F;
    /** 着火秒数（原作 setFire(5)） */
    public static final int BEAM_FIRE_SECONDS = 5;
    /** 伤害范围：落点水平 ±6、往下 6，往上 128 + 6（原作 d0 = 6、posY + 128） */
    public static final double DAMAGE_RADIUS = 6D;
    public static final double DAMAGE_HEIGHT = 128D;
    /** 一天中出光束的时间窗（含两端，原作 getWorldTime() % 24000 在 [5000, 7000]） */
    public static final long WINDOW_START = 5000L;
    public static final long WINDOW_END = 7000L;
    /**
     * 方块事件 id：光束开始（{@link #fireBeam} 直接发 ClientboundBlockEventPacket，客户端经 BaseEntityBlock.triggerEvent 走到 {@link #triggerEvent}）。
     * 方块事件的两个参数在网络包里都只有一个无符号字节，
     * 落点深度（比棱镜低多少格，主世界最多 383）拆成两半：id = EVENT_BEAM + 深度 / 256，参数 = 深度 % 256。
     */
    public static final int EVENT_BEAM = 1;
    private static final int EVENT_BEAM_LAST = EVENT_BEAM + 15;
    private static final int MAX_DEPTH = ((EVENT_BEAM_LAST - EVENT_BEAM + 1) << 8) - 1;

    private final StructureCache structure = new StructureCache(ECSolar.PRISM_STRUCTURE);

    // ---- 服务端，不存盘（光束只有 1 秒） ----
    private int beamTicks;
    private int beamLandingY;
    private int damageCooldown;
    @Nullable
    private BlockPos lastTarget;

    // ---- 客户端，由方块事件设置（初值保证 gameTime 从 0 起也不会误判成有光束） ----
    private long clientBeamUntil = -100L;
    private int clientBeamDepth;

    public SolarPrismBlockEntity(BlockPos pos, BlockState state) {
        super(ECSolar.SOLAR_PRISM_BE.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // 服务端
    // ------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, SolarPrismBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        // D5：摆法不对就自毁掉落。StructureCache 最多 20 刻重算一次；区块没加载（UNKNOWN）不算不对
        if (be.structure.check(level, pos, Direction.NORTH) == StructureResult.MISMATCH) {
            level.destroyBlock(pos, true);
            return;
        }
        be.tickBeam(server);
        if (be.structure.formed() && level.random.nextDouble() < ECConfig.get(Config.beamChance) && canFire(level, pos)) {
            be.fireBeam(server);
        }
    }

    /** 除了几率以外出光束的条件：正午前后、没下雨、看得到天空（各条都能在配置里关掉） */
    public static boolean canFire(Level level, BlockPos pos) {
        if (ECConfig.get(Config.requiresMidday) && !isSolarWindow(level.getDayTime())) {
            return false;
        }
        if (!ECConfig.get(Config.ignoreRain) && level.isRaining()) {
            return false;
        }
        // 棱镜本身透天空光，看它上面一格和看它自己一样；原作 canBlockSeeSky(pos)
        return !ECConfig.get(Config.requiresSky) || level.canSeeSky(pos.above());
    }

    /** 一天中的时间是否在出光束的窗口里（原作 getWorldTime() % 24000 在 [5000, 7000]） */
    public static boolean isSolarWindow(long dayTime) {
        long time = Math.floorMod(dayTime, 24000L);
        return time >= WINDOW_START && time <= WINDOW_END;
    }

    /**
     * 立即打一道光束（不看几率和时间天气条件；GameTest 也直接调它）：
     * 找落点、点亮一台吸收器、通知客户端画光柱。
     *
     * @return 落点；棱镜正下方紧挨着的格子不是空气、或者一路到世界底部都是空气时返回 null（不出光束，同原作）
     */
    @Nullable
    public BlockPos fireBeam(ServerLevel level) {
        BlockPos landing = findLanding(level, worldPosition);
        if (landing == null) {
            return null;
        }
        if (beamTicks <= 0) {
            // 新的一段光照：下一刻立即伤害；光束重叠时沿用原来的伤害节奏
            damageCooldown = 0;
        }
        beamTicks = BEAM_DURATION;
        beamLandingY = landing.getY();
        SunRayAbsorberBlockEntity target = findAbsorber(level, landing);
        lastTarget = target == null ? null : target.getBlockPos();
        if (target != null) {
            target.illuminate(BEAM_DURATION);
        }
        // 通知客户端画光柱：原版方块事件包直接发给追踪棱镜所在区块的玩家（落点在同一竖列、同一区块），每道光束一个包。
        // 不走 level.blockEvent：ServerLevel.runBlockEvents 只广播给离棱镜 64 格（三维距离）以内的玩家，棱镜架得高时吸收器旁的人收不到
        int depth = Math.min(MAX_DEPTH, worldPosition.getY() - landing.getY());
        PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(worldPosition)).send(
                new ClientboundBlockEventPacket(worldPosition, getBlockState().getBlock(), EVENT_BEAM + (depth >> 8), depth & 0xFF));
        return landing;
    }

    /**
     * 落点：棱镜正下方第一个不是空气的方块。原作的循环从正下方那格开始，那格不是空气就一道光束都不出；
     * 原作找到 y=0 为止，这里到世界底部。只查棱镜所在这一竖列（和棱镜同一个区块）。
     */
    @Nullable
    public static BlockPos findLanding(Level level, BlockPos prism) {
        int min = level.getMinBuildHeight();
        BlockPos.MutableBlockPos cursor = prism.mutable().move(Direction.DOWN);
        if (cursor.getY() < min || !level.getBlockState(cursor).isAir()) {
            return null;
        }
        while (cursor.getY() > min) {
            cursor.move(Direction.DOWN);
            if (!level.getBlockState(cursor).isAir()) {
                return cursor.immutable();
            }
        }
        return null;
    }

    /**
     * 这道光束给哪台吸收器：落点本身是吸收器就给它；否则在落点周围（水平 ±1、往下 1 往上 2，即原作光束碰撞箱能碰到的吸收器）
     * 找最近的一台，距离相同时取先找到的。只读已加载的区块。
     */
    @Nullable
    public static SunRayAbsorberBlockEntity findAbsorber(Level level, BlockPos landing) {
        if (ECLevels.loadedBlockEntity(level, landing) instanceof SunRayAbsorberBlockEntity direct) {
            return direct;
        }
        SunRayAbsorberBlockEntity best = null;
        int bestDistance = Integer.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int distance = dx * dx + dy * dy + dz * dz;
                    if (distance == 0 || distance >= bestDistance) {
                        continue;
                    }
                    cursor.setWithOffset(landing, dx, dy, dz);
                    if (ECLevels.loadedBlockEntity(level, cursor) instanceof SunRayAbsorberBlockEntity absorber) {
                        best = absorber;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    /** 光束还亮着时每 10 刻伤害一次范围内的生物（第一次在光束出现后的下一刻）；只打敌对生物，见 {@link #mayBurn} */
    private void tickBeam(ServerLevel level) {
        if (beamTicks <= 0) {
            return;
        }
        if (damageCooldown <= 0) {
            hurtEntities(level);
            damageCooldown = DAMAGE_INTERVAL;
        }
        damageCooldown--;
        beamTicks--;
    }

    private void hurtEntities(ServerLevel level) {
        double x = worldPosition.getX() + 0.5D;
        double z = worldPosition.getZ() + 0.5D;
        AABB box = new AABB(x - DAMAGE_RADIUS, beamLandingY - DAMAGE_RADIUS, z - DAMAGE_RADIUS,
                x + DAMAGE_RADIUS, beamLandingY + DAMAGE_HEIGHT + DAMAGE_RADIUS, z + DAMAGE_RADIUS);
        burn(level, level.getEntitiesOfClass(LivingEntity.class, box, EntitySelector.NO_SPECTATORS));
    }

    /** 对这些生物里光束该烧的（{@link #mayBurn}）点火、打一下。混合端自检直接调它（测试玩家不在世界的实体表里，查范围查不到） */
    public static void burn(ServerLevel level, List<? extends LivingEntity> entities) {
        for (LivingEntity entity : entities) {
            if (mayBurn(entity)) {
                entity.setSecondsOnFire(BEAM_FIRE_SECONDS);
                entity.hurt(level.damageSources().onFire(), BEAM_DAMAGE);
            }
        }
    }

    /**
     * 光束烧不烧这只生物（第十批权限修补 A2）：只烧敌对生物（{@link Enemy}），而且不是谁都不许碰的（{@link #sparedFromBeam}：起了名字、拴着绳……）；
     * 玩家只在 {@code burn_players} 开着时烧（创造、旁观照旧不烧）；动物、村民、宠物、盔甲架一律不烧。
     * 光束的火焰伤害没有攻击者，PvP 开关、领地的 PvP / 动物保护都拦不住，落点水平 6 格又会伸进邻居的领地，所以收窄到敌对生物（同反应堆第四批定稿的口径）。
     */
    public static boolean mayBurn(LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator()) {
            return false;
        }
        if (entity instanceof Player player) {
            return Config.burnPlayers() && !player.getAbilities().invulnerable;
        }
        // 盔甲架是装饰：ArmorStand.hurt 对火焰伤害直接扣 4 点、没有无敌帧，打两下再烧 5 秒就毁了，而且盔甲架物品不掉（它也不是 Enemy，这里写明）
        if (entity instanceof ArmorStand) {
            return false;
        }
        return entity instanceof Enemy && !sparedFromBeam(entity);
    }

    /**
     * 光束不烧的「有主的」生物（P-7、RB-3）：规则挪到了 {@link ECEntityRules#isSpared}（第四批 F4-12，冷凝器共用，以后线圈、反应堆也用），
     * 这里只是转调。挪过去以后多了一类：装了鞍的骆驼（4a 审查 FW-7；骆驼不用驯服就能骑，没有别的办法看出是谁在用）。
     * 盔甲架、创造模式、旁观者在调用处另外跳过。
     */
    public static boolean sparedFromBeam(LivingEntity entity) {
        return ECEntityRules.isSpared(entity);
    }

    // ---- 给 GameTest 和调试看的 ----

    public boolean isBeamActive() {
        return beamTicks > 0;
    }

    /** 最近一道光束给了哪台吸收器（没有吸收器时为 null） */
    @Nullable
    public BlockPos lastTarget() {
        return lastTarget;
    }

    public StructureCache structure() {
        return structure;
    }

    // ------------------------------------------------------------------
    // 客户端
    // ------------------------------------------------------------------

    /**
     * 方块事件：客户端收到 {@link #fireBeam} 发的包后经 ClientPacketListener.handleBlockEvent → Level.blockEvent → BaseEntityBlock.triggerEvent 走到这里，
     * 记下光柱的结束时间和落点深度。服务端不再走 level.blockEvent，这里的服务端分支只是照惯例认领这些 id。
     */
    @Override
    public boolean triggerEvent(int id, int param) {
        if (id >= EVENT_BEAM && id <= EVENT_BEAM_LAST) {
            if (level != null && level.isClientSide) {
                clientBeamDepth = Math.max(1, ((id - EVENT_BEAM) << 8) | (param & 0xFF));
                clientBeamUntil = level.getGameTime() + BEAM_DURATION;
            }
            return true;
        }
        return super.triggerEvent(id, param);
    }

    /** 原作光束剩余寿命是 5 的倍数时（15、10、5、0）在落点放一次火焰环境音（WEATHER 类，音量 10、音调 2），这里在客户端本地放 */
    public static void clientTick(Level level, BlockPos pos, BlockState state, SolarPrismBlockEntity be) {
        long remaining = be.clientBeamUntil - level.getGameTime();
        if (remaining >= 0 && remaining < BEAM_DURATION && remaining % 5 == 0) {
            level.playLocalSound(pos.getX() + 0.5D, pos.getY() - be.clientBeamDepth, pos.getZ() + 0.5D,
                    SoundEvents.FIRE_AMBIENT, SoundSource.WEATHER, 10F, 2F, false);
        }
    }

    /** 客户端：这一刻是否要画光柱 */
    public boolean isBeamVisible(long gameTime) {
        return gameTime < clientBeamUntil;
    }

    /** 客户端：这一道光柱到第几刻结束（开始 = 结束 − {@link #BEAM_DURATION}） */
    public long clientBeamUntil() {
        return clientBeamUntil;
    }

    /** 客户端：落点比棱镜低多少格 */
    public int clientBeamDepth() {
        return clientBeamDepth;
    }

    /** 渲染范围：3×3 的板；有光柱时往下到落点、往上到世界顶部（光柱穿过棱镜一直画到天上，同原作） */
    @Override
    public AABB getRenderBoundingBox() {
        BlockPos p = worldPosition;
        double bottom = p.getY();
        double top = p.getY() + 1D;
        if (level != null && isBeamVisible(level.getGameTime())) {
            bottom = p.getY() - clientBeamDepth;
            top = Math.max(top, level.getMaxBuildHeight());
        }
        return new AABB(p.getX() - 1D, bottom, p.getZ() - 1D, p.getX() + 2D, top, p.getZ() + 2D);
    }

    // ------------------------------------------------------------------
    // 存盘（只存结构判定的上次结果，读档后邻区块还没加载时不会误判）
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        structure.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        structure.load(tag);
    }
}
