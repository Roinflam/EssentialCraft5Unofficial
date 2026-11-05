package com.flameforge.essentialcraft5.gun;

import com.flameforge.essentialcraft5.network.S2CGunMatrixHitPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 矩阵损伤特效的服务端一侧（GUNS-4 补的「有损坏的特效」，batch5.md §8.1）：射线加了矩阵损伤以后，给被打的人和所有看得见他的人
 * 发一个 {@link S2CGunMatrixHitPacket}（{@code TRACKING_ENTITY_AND_SELF}），客户端 {@code client/render/MatrixHitEffects} 画电弧、粒子、放音效，
 * 被打的人自己屏幕边缘泛一圈品红。
 * <p>
 * <b>限频</b>（加特林预转完每刻一发，不许每发都发包）：同一个目标两次特效至少隔 {@value #TARGET_INTERVAL_TICKS} 刻（每秒至多 2 次，
 * 也保证了被打的人屏幕上的晕影每秒不超过 3 次）；全服每刻至多 {@value #MAX_PER_TICK} 个（一发穿透一排玩家时）。被限掉的只是特效，矩阵损伤照加。
 * 客户端另有一道同样的限频兜底（网络卡顿时几个包挤在同一刻到）。
 */
public final class GunMatrixFx {

    /** 同一个目标两次特效至少隔多少刻 */
    public static final int TARGET_INTERVAL_TICKS = 10;
    /** 全服每刻最多发几个特效包 */
    public static final int MAX_PER_TICK = 8;
    /** 特效强度 1~3 的分界（枪的伤害） */
    private static final float MEDIUM_DAMAGE = 10F;
    private static final float HEAVY_DAMAGE = 30F;

    /** 正式的服务端限频器 */
    static final Throttle SERVER = new Throttle();

    /** 正式的出口：限频后发包给被打的人和看得见他的人 */
    public static final RayShot.MatrixFxSink PACKET = (target, intensity) -> {
        if (SERVER.tryAcquire(target.getUUID(), target.server.getTickCount())) {
            S2CGunMatrixHitPacket.sendAround(target, intensity);
        }
    };

    private GunMatrixFx() {
    }

    /** 特效强度（1 轻、2 中、3 重）：按枪的伤害分三档（加特林、入门手枪、恶魔狙击这种量级） */
    public static int intensity(float gunDamage) {
        if (gunDamage >= HEAVY_DAMAGE) {
            return 3;
        }
        return gunDamage >= MEDIUM_DAMAGE ? 2 : 1;
    }

    /** 限频器：按目标记上一次的刻数、按刻记本刻已发几个。GameTest 自己 new 一个测（正式的那个多个测试并行时会互相占预算） */
    public static final class Throttle {
        private static final int PRUNE_THRESHOLD = 512;
        private final Map<UUID, Integer> last = new HashMap<>();
        private int budgetTick = Integer.MIN_VALUE;
        private int used;

        /** 这一刻能不能给这个目标发一次特效；能就记账 */
        public boolean tryAcquire(UUID target, int tick) {
            if (tick != budgetTick) {
                budgetTick = tick;
                used = 0;
            }
            if (used >= MAX_PER_TICK) {
                return false;
            }
            Integer previous = last.get(target);
            // 刻数比记录还小（服务器重启、刻数从 0 重新算）的当过期
            if (previous != null && tick - previous >= 0 && tick - previous < TARGET_INTERVAL_TICKS) {
                return false;
            }
            if (last.size() >= PRUNE_THRESHOLD) {
                last.values().removeIf(at -> tick - at < 0 || tick - at >= TARGET_INTERVAL_TICKS);
            }
            last.put(target, tick);
            used++;
            return true;
        }
    }
}
