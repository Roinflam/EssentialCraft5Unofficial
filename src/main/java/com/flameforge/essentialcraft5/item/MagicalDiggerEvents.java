package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 魔力挖掘器的范围挖（{@link MagicalDiggerItem}）：中心那格挖掉时登记一个待办，本 tick 的 {@code ServerTickEvent} END 再逐格挖邻格。
 * 为什么要推迟见 {@link MagicalDiggerItem} 的类注释（混合端的 Bukkit 破坏事件全局单槽，不许在另一次破坏过程里再发破坏事件）。
 * <p>
 * 每个待办（每名玩家同时最多一个；还没处理时再来的作废，原作也是一次最多 26 格）处理前核对：
 * 玩家还在（用待办里记下的 {@link ServerPlayer} 对象判断没断线、没被移除、活着、不是旁观者，<b>不查玩家列表</b>：
 * GameTest 的 {@code realPlayer} 不在列表里）、还在同一个维度、主手还是<b>同一个</b>物品对象、中心那格真的没了（被插件撤回的不算）。
 * 然后对 26 个邻格：已加载、和中心同一种方块、不是空气、硬度 ≥ 0、{@code level.mayInteract}（出生点保护、世界边界）、
 * {@code MRUDrain.canAfford} 够 {@value MagicalDiggerItem#COST} → 调玩家本人的 {@code gameMode.destroyBlock}（真破坏事件、掉落、附魔、经验）→
 * 返回 true 且那格真的变了才扣 {@value MagicalDiggerItem#COST}；钱不够就停。
 * <p>
 * 效果（只给挖的人，同原作：原作的粒子和音效都是在挖的人自己的客户端上放的）：挖掉的每格 4 个紫色尘埃粒子，音效一次。
 * 邻格的方块破碎粒子和声音原版发给附近其他玩家（{@code levelEvent 2001} 不发给挖的人自己），挖的人只看到紫色尘埃。
 * <p>
 * 只在服务端线程上用（待办表不加锁）。类名带模块前缀：Forge 按「包名 + 简单类名 + 方法名 + 事件类型」认监听器（见 tools/check_event_handlers.py）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class MagicalDiggerEvents {

    /** 紫色尘埃（原作 REDSTONE 粒子，颜色 1, 0, 1） */
    private static final DustParticleOptions DUST = new DustParticleOptions(new Vector3f(1F, 0F, 1F), 1F);
    /** 每格的粒子数（原作每格 10 个、只在客户端；这里走网络，减到 4） */
    private static final int PARTICLES_PER_BLOCK = 4;

    private static final ThreadLocal<Boolean> AREA_MINING = ThreadLocal.withInitial(() -> Boolean.FALSE);
    /** 玩家 UUID → 待办（按登记顺序处理） */
    private static final Map<UUID, Pending> PENDING = new LinkedHashMap<>();

    private record Pending(ServerPlayer player, ResourceKey<Level> dimension, BlockPos center, Block block, ItemStack tool) {
    }

    private MagicalDiggerEvents() {
    }

    /**
     * 当前是否在挖掘器的范围挖里（邻格的 destroyBlock 正在进行）。挖掘器自己的 {@code mineBlock} 看到它就什么都不做；
     * 别的「玩家挖了一格就连带做事」的监听器也可以用它区分（这些格子仍是玩家本人挖的，照常算掉落、经验和统计）。
     */
    public static boolean isAreaMining() {
        return AREA_MINING.get();
    }

    /**
     * 登记一次范围挖（{@link MagicalDiggerItem#mineBlock} 调用）。这名玩家已经有待办时这次作废，返回 false。
     *
     * @param tool 玩家主手里的那把挖掘器（按对象身份比对，处理时主手换了东西就作废）
     */
    static boolean schedule(ServerPlayer player, BlockPos center, Block block, ItemStack tool) {
        if (PENDING.containsKey(player.getUUID())) {
            return false;
        }
        PENDING.put(player.getUUID(), new Pending(player, player.level().dimension(), center.immutable(), block, tool));
        return true;
    }

    /** 本 tick 末尾处理全部待办（处理期间新登记的留到下一个 tick 末尾） */
    @SubscribeEvent
    public static void onDiggerServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        List<Pending> batch = new ArrayList<>(PENDING.values());
        PENDING.clear();
        for (Pending pending : batch) {
            run(pending);
        }
    }

    @SubscribeEvent
    public static void onDiggerServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    /** 处理一个待办 */
    private static void run(Pending pending) {
        ServerPlayer player = pending.player();
        if (!stillHolding(player, pending) || !(player.level() instanceof ServerLevel level) || level.dimension() != pending.dimension()) {
            return;
        }
        BlockPos center = pending.center();
        // 中心那格没挖成（还是原来的方块）：不连带
        if (!level.isLoaded(center) || level.getBlockState(center).is(pending.block())) {
            return;
        }
        List<BlockPos> broken = new ArrayList<>();
        broken.add(center);
        int radius = MagicalDiggerItem.AREA_RADIUS;
        outer:
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    // 挖的过程中玩家死了、主手被别的模组换掉了：停
                    if (!stillHolding(player, pending)) {
                        break outer;
                    }
                    BlockPos cell = center.offset(dx, dy, dz);
                    if (!level.isLoaded(cell)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cell);
                    if (state.isAir() || !state.is(pending.block()) || state.getDestroySpeed(level, cell) < 0F || !level.mayInteract(player, cell)) {
                        continue;
                    }
                    if (!MRUDrain.canAfford(player, pending.tool(), MagicalDiggerItem.COST)) {
                        break outer;
                    }
                    boolean destroyed;
                    AREA_MINING.set(Boolean.TRUE);
                    try {
                        destroyed = player.gameMode.destroyBlock(cell);
                    } finally {
                        AREA_MINING.set(Boolean.FALSE);
                    }
                    // destroyBlock 在方块没删掉时也可能返回 true（removeBlock 失败）：那格真的变了才扣
                    if (destroyed && !level.getBlockState(cell).is(pending.block())) {
                        MRUDrain.playerUseMRU(player, pending.tool(), MagicalDiggerItem.COST);
                        broken.add(cell);
                    }
                }
            }
        }
        for (BlockPos pos : broken) {
            level.sendParticles(player, DUST, false, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    PARTICLES_PER_BLOCK, 0.3D, 0.3D, 0.3D, 0D);
        }
        // 原作 BLOCK_FIRE_EXTINGUISH，音量 0.2，音调 6（被夹到 2）
        player.playNotifySound(SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.2F, 2F);
    }

    private static boolean stillHolding(ServerPlayer player, Pending pending) {
        return !player.hasDisconnected() && !player.isRemoved() && player.isAlive() && !player.isSpectator()
                && player.getMainHandItem() == pending.tool();
    }
}
