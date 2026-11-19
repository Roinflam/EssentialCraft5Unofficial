package com.flameforge.essentialcraft5.network;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 装备动作的特效事件（喷射、闪避、定罪、空中跳）走 {@link S2CGearFxPacket}，同一个实体同一种事件 minGap 刻内只发一次：
 * 冷却配成 0、或者一刻里被打好几下时，画面合成一次就够，不让包跟着刷。只在服务端主线程用。
 */
public final class GearFxLimiter {

    private static final Map<Entity, long[]> LAST = new WeakHashMap<>();

    private GearFxLimiter() {
    }

    /** @return 真发出去了 */
    public static boolean send(ServerLevel level, GearFx type, Entity entity, Vec3 pos, float param, int minGap) {
        long now = level.getGameTime();
        long[] last = LAST.computeIfAbsent(entity, e -> new long[GearFx.values().length]);
        int i = type.ordinal();
        if (last[i] != 0L && now >= last[i] && now - last[i] < minGap) {
            return false;
        }
        last[i] = now == 0L ? 1L : now;
        S2CGearFxPacket.sendAround(level, type, entity, pos, param);
        return true;
    }
}
