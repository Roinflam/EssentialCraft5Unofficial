package com.flameforge.essentialcraft5.enrichment;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.EnrichmentControllerBlockEntity;
import com.flameforge.essentialcraft5.registry.ECEnrichment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 每个维度里已加载的富集仓控制器（只在服务端），用来在外壳方块被放下 / 拆掉时给附近的控制器标脏。
 * <p>
 * 外壳方块大多是别的模块的（强化石、强化玻璃、各种护板），不能给它们加 neighborChanged；
 * 这里听 Forge 的放置、破坏、爆炸事件，方块在外壳标签里就通知「盯着这一格」的控制器（成形的看自己的外壳外扩 1 格，
 * 没成形的看自己周围 maxSize 格）。命令、活塞、别的模组直接 setBlock 不发这些事件的，靠控制器每 200 tick 的复查兜底。
 * 本模块自己的部件方块在 onPlace / onRemove 里直接调 {@link #notifyChanged}。
 * <p>
 * 控制器在第一次服务端 tick 时登记、setRemoved（拆掉或区块卸载）时注销；维度卸载时整张表丢掉。
 * 一个维度里的控制器一般只有几个，事件里遍历一遍的开销可以忽略。只在服务端线程上读写（单人游戏的客户端线程不碰它）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class EnrichmentControllers {

    private static final Map<LevelAccessor, List<EnrichmentControllerBlockEntity>> LOADED = new WeakHashMap<>();

    private EnrichmentControllers() {
    }

    public static void add(EnrichmentControllerBlockEntity controller) {
        Level level = controller.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        List<EnrichmentControllerBlockEntity> list = LOADED.computeIfAbsent(level, l -> new ArrayList<>());
        if (!list.contains(controller)) {
            list.add(controller);
        }
    }

    public static void remove(EnrichmentControllerBlockEntity controller) {
        Level level = controller.getLevel();
        if (level == null) {
            return;
        }
        List<EnrichmentControllerBlockEntity> list = LOADED.get(level);
        if (list != null) {
            list.remove(controller);
            if (list.isEmpty()) {
                LOADED.remove(level);
            }
        }
    }

    /** pos 处的方块变了（放下或拆掉外壳方块），给盯着这一格的控制器标脏 */
    public static void notifyChanged(LevelAccessor level, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        List<EnrichmentControllerBlockEntity> list = LOADED.get(level);
        if (list == null) {
            return;
        }
        for (EnrichmentControllerBlockEntity controller : list) {
            if (controller.watches(pos)) {
                controller.markDirty();
            }
        }
    }

    private static boolean isCasing(BlockState state) {
        return state.is(ECEnrichment.CASING);
    }

    // 下面几个事件先看这个维度有没有已加载的控制器：绝大多数时候没有，直接返回（也就不会去碰标签）

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && LOADED.containsKey(event.getLevel()) && isCasing(event.getPlacedBlock())) {
            notifyChanged(event.getLevel(), event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && LOADED.containsKey(event.getLevel()) && isCasing(event.getState())) {
            notifyChanged(event.getLevel(), event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide || !LOADED.containsKey(level)) {
            return;
        }
        for (BlockPos pos : event.getAffectedBlocks()) {
            // 爆炸影响的格子都在已加载的区块里
            if (isCasing(level.getBlockState(pos))) {
                notifyChanged(level, pos);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        // 客户端的维度卸载在客户端线程上发，这张表只在服务端线程上用，不能碰
        if (!event.getLevel().isClientSide()) {
            LOADED.remove(event.getLevel());
        }
    }
}
