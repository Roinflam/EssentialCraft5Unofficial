package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.MRUBlockEntity;
import com.flameforge.essentialcraft5.blockentity.HeatGeneratorBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MagicalTeleporterBlockEntity;
import com.flameforge.essentialcraft5.blockentity.MatrixCrucibleBlockEntity;
import com.flameforge.essentialcraft5.blockentity.RayTowerBlockEntity;
import com.flameforge.essentialcraft5.blockentity.SolarPrismBlockEntity;
import com.flameforge.essentialcraft5.client.render.MatrixHitEffects;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.registry.ECDemon;
import com.flameforge.essentialcraft5.registry.ECGenerators;
import com.flameforge.essentialcraft5.registry.ECItems;
import com.flameforge.essentialcraft5.registry.ECMatrix;
import com.flameforge.essentialcraft5.registry.ECReactor;
import com.flameforge.essentialcraft5.registry.ECSolar;
import com.flameforge.essentialcraft5.registry.ECTesla;
import com.flameforge.essentialcraft5.registry.ECVoidMachines;
import com.flameforge.essentialcraft5.registry.ECWind;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 机器特效的压测台（开发用，只在单人游戏里有效）：TestHelper {@code setstatic com.flameforge.essentialcraft5.dev.MachineVfxBench request <指令>}。
 * <ul>
 *   <li>{@code build}：在 (2000, 120, 2000) 起摆一块平台：50 台特斯拉线圈（满 MRU，每台旁边一只打不死的僵尸）、50 台自然熔炉、一座成形的反应堆，
 *       再各摆一台太阳棱镜、两座连着的射线塔、恶魔五芒星、风之符文、魔力传送器、矩阵淬炼台和一团 MRU 聚集体；调成夜里、简单难度；</li>
 *   <li>{@code fx}：在客户端点亮只靠服务端事件才出的画面：棱镜放一次光柱、传送器停在八成蓄能、淬炼台进入试炼、最近的僵尸身上放一次矩阵受损；</li>
 *   <li>{@code fxoff}：把上面几样关掉。</li>
 * </ul>
 * 机位用 {@code cmd tp} 自己摆（坐标见 {@link #BASE} 和各段的偏移）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class MachineVfxBench {

    public static final BlockPos BASE = new BlockPos(2000, 120, 2000);
    /** 线圈区：10 × 5，间隔 6；熔炉区在线圈区南边；反应堆和单台机器在东边 */
    public static final BlockPos COILS = BASE;
    public static final BlockPos FURNACES = BASE.offset(0, 0, 34);
    public static final BlockPos SINGLES = BASE.offset(64, 0, 0);

    public static String request = "";
    /** 摆好以后每秒把线圈充满，不然一直打僵尸很快就没 MRU 停了 */
    private static boolean keepCharged;
    private static int chargeTimer;

    private MachineVfxBench() {
    }

    @SubscribeEvent
    public static void onMachineVfxBenchTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (keepCharged && ++chargeTimer >= 20) {
            chargeTimer = 0;
            MinecraftServer srv = Minecraft.getInstance().getSingleplayerServer();
            if (srv != null) {
                srv.execute(() -> recharge(srv.overworld()));
            }
        }
        if (request.isEmpty()) {
            return;
        }
        String r = request;
        request = "";
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            EssentialCraft5.LOGGER.warn("[压测台] 只在单人游戏里能用");
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(mc.player.getUUID());
        switch (r) {
            case "build" -> server.execute(() -> build(player.serverLevel(), player));
            case "charge" -> keepCharged = true;
            case "fx" -> clientFx(mc, true);
            case "fxoff" -> clientFx(mc, false);
            default -> EssentialCraft5.LOGGER.warn("[压测台] 不认识的指令：{}", r);
        }
    }

    private static void build(ServerLevel level, ServerPlayer player) {
        level.getServer().setDifficulty(Difficulty.EASY, true);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.setDayTime(18000L);
        for (int x = -6; x < 90; x++) {
            for (int z = -6; z < 70; z++) {
                level.setBlock(BASE.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        // 50 台线圈：结构自带底座（5×5 护板 + 两层基座），线圈在底座上三格
        for (int i = 0; i < 50; i++) {
            BlockPos origin = COILS.offset((i % 10) * 6, 0, (i / 10) * 6);
            BlockPos coil = origin.above(3);
            place(level, ECTesla.COIL_STRUCTURE, coil, Direction.NORTH, player);
            if (level.getBlockEntity(coil) instanceof MRUBlockEntity be) {
                be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
            }
            Zombie zombie = EntityType.ZOMBIE.create(level);
            if (zombie != null) {
                zombie.moveTo(origin.getX() + 3.5D, origin.getY(), origin.getZ() + 0.5D, 0F, 0F);
                zombie.setNoAi(true);
                zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000D);
                zombie.setHealth(1000F);
                zombie.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, -1, 3, false, false));
                level.addFreshEntity(zombie);
            }
        }
        // 热能发电机塞满煤块，一直烧着冒火冒烟
        for (int i = 0; i < 50; i++) {
            BlockPos at = FURNACES.offset((i % 10) * 3, 0, (i / 10) * 3);
            level.setBlockAndUpdate(at, ECGenerators.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(at) instanceof HeatGeneratorBlockEntity be) {
                be.getInventory().setStackInSlot(HeatGeneratorBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL_BLOCK, 64));
            }
        }
        reactor(level, SINGLES.offset(0, 0, 4));
        // 太阳棱镜：落点三格下面摆一块石头，光柱由 fx 在客户端点亮
        BlockPos prism = SINGLES.offset(10, 4, 4);
        // 同层四个方向隔一格要有方块，不然棱镜第一次检查就自毁
        for (Direction d : Direction.Plane.HORIZONTAL) {
            level.setBlockAndUpdate(prism.relative(d, 2), Blocks.STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(prism, ECSolar.SOLAR_PRISM.get().defaultBlockState());
        // 两座射线塔，后一座绑到前一座
        BlockPos rayA = SINGLES.offset(16, 0, 0);
        BlockPos rayB = SINGLES.offset(16, 0, 10);
        level.setBlockAndUpdate(rayA, ECGenerators.RAY_TOWER.get().defaultBlockState());
        ECGenerators.RAY_TOWER.get().defaultBlockState().getBlock().setPlacedBy(level, rayA, level.getBlockState(rayA), player, ItemStack.EMPTY);
        level.setBlockAndUpdate(rayB, ECGenerators.RAY_TOWER.get().defaultBlockState());
        ECGenerators.RAY_TOWER.get().defaultBlockState().getBlock().setPlacedBy(level, rayB, level.getBlockState(rayB), player, ItemStack.EMPTY);
        if (level.getBlockEntity(rayA) instanceof RayTowerBlockEntity a && level.getBlockEntity(rayB) instanceof RayTowerBlockEntity b) {
            a.getMRUStorage().setMRU(a.getMRUStorage().getMaxMRU());
            b.getMRUStorage().setMRU(1000);
            ItemStack gem = new ItemStack(ECItems.BOUND_GEM.get());
            BoundGemItem.bind(gem, GlobalPos.of(level.dimension(), rayA), false);
            b.getInventory().setStackInSlot(RayTowerBlockEntity.SLOT_GEM, gem);
        }
        place(level, ECDemon.STRUCTURE, SINGLES.offset(0, 0, 20), Direction.NORTH, player);
        place(level, ECWind.RUNE_STRUCTURE, SINGLES.offset(10, 0, 20), Direction.NORTH, player);
        place(level, ECVoidMachines.TELEPORTER_STRUCTURE, SINGLES.offset(0, 0, 34), Direction.NORTH, player);
        level.setBlockAndUpdate(SINGLES.offset(10, 0, 34), ECMatrix.MATRIX_CRUCIBLE.get().defaultBlockState());
        level.addFreshEntity(MRUPresenceEntity.create(level, SINGLES.getX() + 16.5D, SINGLES.getY() + 1.5D, SINGLES.getZ() + 34.5D,
                15_000, 1F, false, true));
        player.connection.teleport(COILS.getX() + 27.5D, COILS.getY() + 14D, COILS.getZ() - 14D, 0F, 30F);
        keepCharged = true;
        EssentialCraft5.LOGGER.info("[压测台] 摆好了：线圈 {}，棱镜 {}", level.getBlockState(COILS.above(3)), level.getBlockState(SINGLES.offset(10, 4, 4)));
    }

    private static void recharge(ServerLevel level) {
        for (int i = 0; i < 50; i++) {
            BlockPos coil = COILS.offset((i % 10) * 6, 3, (i / 10) * 6);
            if (level.isLoaded(coil) && level.getBlockEntity(coil) instanceof MRUBlockEntity be) {
                be.getMRUStorage().setMRU(be.getMRUStorage().getMaxMRU());
            }
        }
    }

    /** 照结构表摆出全部必需格子（同展示厅），中心那一格最后放 */
    private static void place(ServerLevel level, StructurePattern pattern, BlockPos anchor, Direction facing, ServerPlayer player) {
        BlockState center = null;
        for (Map.Entry<BlockPos, StructurePattern.Rule> entry : pattern.rules().entrySet()) {
            StructurePattern.Rule rule = entry.getValue();
            if (rule.isAir()) {
                continue;
            }
            BlockState state = rule.displayState();
            if (entry.getKey().equals(BlockPos.ZERO)) {
                center = state;
                continue;
            }
            BlockPos pos = StructurePattern.toWorld(anchor, entry.getKey(), facing);
            level.setBlockAndUpdate(pos, state);
            state.getBlock().setPlacedBy(level, pos, state, player, new ItemStack(state.getBlock()));
        }
        if (center != null) {
            level.setBlockAndUpdate(anchor, center);
            center.getBlock().setPlacedBy(level, anchor, center, player, new ItemStack(center.getBlock()));
        }
    }

    /** 同展示厅的反应堆：支架从下往上按周围算状态 */
    private static void reactor(ServerLevel level, BlockPos base) {
        BlockPos core = base.above();
        List<BlockPos> supports = new ArrayList<>();
        for (Map.Entry<BlockPos, StructurePattern.Rule> entry : ECReactor.REACTOR_STRUCTURE.rules().entrySet()) {
            StructurePattern.Rule rule = entry.getValue();
            if (rule.isAir()) {
                continue;
            }
            BlockPos pos = StructurePattern.toWorld(core, entry.getKey(), ECReactor.X_AXIS);
            BlockState state = rule.displayState();
            if (state.is(ECReactor.REACTOR_SUPPORT.get())) {
                supports.add(pos);
            } else if (!state.is(ECReactor.REACTOR.get())) {
                level.setBlockAndUpdate(pos, state);
            }
        }
        supports.sort(Comparator.comparingInt(BlockPos::getY));
        for (BlockPos pos : supports) {
            BlockState state = Block.updateFromNeighbourShapes(ECReactor.REACTOR_SUPPORT.get().defaultBlockState(), level, pos);
            level.setBlockAndUpdate(pos, state);
        }
        level.setBlockAndUpdate(core, ECReactor.REACTOR.get().defaultBlockState());
    }

    private static void clientFx(Minecraft mc, boolean on) {
        var level = mc.level;
        BlockPos prism = SINGLES.offset(10, 4, 4);
        if (on && level.getBlockEntity(prism) instanceof SolarPrismBlockEntity be) {
            be.triggerEvent(SolarPrismBlockEntity.EVENT_BEAM, 4);
        }
        BlockPos teleporter = SINGLES.offset(0, 0, 34);
        if (on && level.getBlockEntity(teleporter) instanceof MagicalTeleporterBlockEntity be) {
            MagicalTeleporterBlockEntity.setShowroomCharging(teleporter, (int) (be.ticksRequired() * 0.8F));
        } else if (!on) {
            MagicalTeleporterBlockEntity.clearShowroomCharging();
        }
        if (level.getBlockEntity(SINGLES.offset(10, 0, 34)) instanceof MatrixCrucibleBlockEntity be) {
            try {
                Field f = MatrixCrucibleBlockEntity.class.getDeclaredField("trialActiveClient");
                f.setAccessible(true);
                f.setBoolean(be, on);
            } catch (ReflectiveOperationException e) {
                EssentialCraft5.LOGGER.warn("[压测台] 设不了淬炼台的试炼状态", e);
            }
        }
        if (on && mc.player != null) {
            level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(48D)).stream()
                    .min(Comparator.comparingDouble(z -> z.distanceToSqr(mc.player)))
                    .ifPresent(z -> MatrixHitEffects.play(z, 3));
        }
    }
}
