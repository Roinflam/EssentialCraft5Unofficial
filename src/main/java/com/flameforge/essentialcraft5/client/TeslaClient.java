package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.MRUCoilHardenerBlock;
import com.flameforge.essentialcraft5.blockentity.MRUCoilBlockEntity;
import com.flameforge.essentialcraft5.client.render.MRUCoilRenderer;
import com.flameforge.essentialcraft5.client.screen.MRUCoilScreen;
import com.flameforge.essentialcraft5.client.vfx.machine.CoilVfx;
import com.flameforge.essentialcraft5.registry.ECSounds;
import com.flameforge.essentialcraft5.registry.ECTesla;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * 特斯拉线圈模块的客户端部分（模组总线，Forge 自动发现）：界面、线圈的 BER；另外两个客户端效果的静态入口：
 * <ul>
 *   <li>{@link #coilStrike}：线圈放电，服务端把打中的实体 id 放进更新包（{@code MRUCoilBlockEntity.getUpdateTag}），客户端读到后从线圈画电弧到每个目标
 *       （{@link CoilVfx#strike}，等离子画法），在线圈处放一次 {@code lightning_hit}（原作音量 2、音高 2）。</li>
 *   <li>{@link #hardenerSpark}：基座的 {@code animateTick}（已按 {@link MRUCoilHardenerBlock#SPARK_CHANCE} 抽过）：往上最多找 64 格，线圈在工作就在随机一面冒一道
 *       1 格高的小电弧、放 0.1 音量的 {@code gen_electricity}；按位置记 {@link #SPARK_COOLDOWN} 刻冷却（原作电弧存活 40 刻、期间不再冒）。
 *       冷却表换世界清空、超过 {@link #SPARK_TABLE_LIMIT} 条时清掉过期的。</li>
 * </ul>
 * 只在客户端加载（方块、方块实体经 DistExecutor 调进来）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TeslaClient {

    /** 同一个基座两次火花至少隔几刻 */
    public static final int SPARK_COOLDOWN = 40;
    /** 冷却表超过这么多条时清掉过期的 */
    public static final int SPARK_TABLE_LIMIT = 512;
    /** 基座往上最多找几格 */
    public static final int SPARK_SEARCH = 64;

    private static final Long2LongOpenHashMap SPARK_READY_AT = new Long2LongOpenHashMap();
    private static WeakReference<Level> sparkLevel = new WeakReference<>(null);

    private TeslaClient() {
    }

    @SubscribeEvent
    public static void teslaClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ECTesla.MRU_COIL_MENU.get(), MRUCoilScreen::new));
    }

    @SubscribeEvent
    public static void teslaRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ECTesla.MRU_COIL_BE.get(), MRUCoilRenderer::new);
    }

    /** 线圈放电：从线圈画电弧到每个还在的目标（身体中间），放一次声音 */
    public static void coilStrike(MRUCoilBlockEntity coil, int[] targetIds) {
        Level level = coil.getLevel();
        if (level == null || targetIds.length == 0) {
            return;
        }
        List<Entity> targets = new ArrayList<>(targetIds.length);
        for (int id : targetIds) {
            Entity target = level.getEntity(id);
            if (target != null) {
                targets.add(target);
            }
        }
        if (!targets.isEmpty()) {
            CoilVfx.strike(coil.emitPoint(), targets);
            BlockPos pos = coil.getBlockPos();
            level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, ECSounds.LIGHTNING_HIT.get(),
                    SoundSource.BLOCKS, 2F, 2F, false);
        }
    }

    /** 基座冒火花（见类注释） */
    public static void hardenerSpark(Level level, BlockPos pos, RandomSource random) {
        if (sparkLevel.get() != level) {
            SPARK_READY_AT.clear();
            sparkLevel = new WeakReference<>(level);
        }
        long now = level.getGameTime();
        long key = pos.asLong();
        if (SPARK_READY_AT.containsKey(key) && SPARK_READY_AT.get(key) > now) {
            return;
        }
        if (!coilAboveIsActive(level, pos)) {
            return;
        }
        if (SPARK_READY_AT.size() >= SPARK_TABLE_LIMIT) {
            SPARK_READY_AT.long2LongEntrySet().removeIf(entry -> entry.getLongValue() <= now);
        }
        SPARK_READY_AT.put(key, now + SPARK_COOLDOWN);
        // 原作 TileMRUCoilHardener L33~35：随机一个水平方向，离中心 1/2.3 格，从方块底到顶
        Direction side = Direction.from2DDataValue(random.nextInt(4));
        double x = pos.getX() + 0.5D + side.getStepX() / 2.3D;
        double z = pos.getZ() + 0.5D + side.getStepZ() / 2.3D;
        CoilVfx.spark(new Vec3(x, pos.getY(), z), new Vec3(x, pos.getY() + 1D, z));
        level.playLocalSound(x, pos.getY() + 0.5D, z, ECSounds.GEN_ELECTRICITY.get(), SoundSource.BLOCKS, 0.1F, 1F, false);
    }

    /** 从基座往上跳过连续的基座（最多 {@link #SPARK_SEARCH} 格），那一格是在工作的线圈 */
    private static boolean coilAboveIsActive(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 0; i < SPARK_SEARCH; i++) {
            cursor.move(Direction.UP);
            BlockState state = level.getBlockState(cursor);
            if (state.getBlock() instanceof MRUCoilHardenerBlock) {
                continue;
            }
            return level.getBlockEntity(cursor) instanceof MRUCoilBlockEntity coil && coil.isClientActive();
        }
        return false;
    }
}
