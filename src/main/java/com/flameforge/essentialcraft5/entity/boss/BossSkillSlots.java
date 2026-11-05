package com.flameforge.essentialcraft5.entity.boss;

import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首领放出来的技能实体的上限（batch13.md §5.3「共同」：技能实体每只有上限；policy §4.18）。每只首领一份，按「种类」分开数
 * （镜影、心智钉、失序之柱……各有各的上限，上限由首领读自己的配置）。照全息投影的克隆体 / 护甲破坏者 / 二分场列表另写一份：
 * <pre>{@code
 * if (skills.hasRoom("mind_spike", max)) {
 *     MindSpike spike = ...;
 *     if (level.addFreshEntity(spike)) skills.track("mind_spike", spike);
 * }
 * }</pre>
 * 已经被移除的（打掉了、到时间了、区块卸载了）数的时候顺手剔掉。首领死了、散去时 {@link #discardAll} 把还在的全收掉（技能实体不存盘，
 * 读档后首领这份是空的，旧的那些也不会读回来）。只在服务端主线程用。
 */
public final class BossSkillSlots {

    private final Map<String, List<Entity>> live = new LinkedHashMap<>();

    /** 这一种还能不能再放一个（max ≤ 0 = 一个都不放） */
    public boolean hasRoom(String kind, int max) {
        return max > 0 && count(kind) < max;
    }

    /** 登记一个放出来的技能实体（加进世界成功以后调），原样返回 */
    public <E extends Entity> E track(String kind, E entity) {
        live.computeIfAbsent(kind, key -> new ArrayList<>()).add(entity);
        return entity;
    }

    /** 这一种现在还在的有几个 */
    public int count(String kind) {
        List<Entity> list = live.get(kind);
        if (list == null) {
            return 0;
        }
        list.removeIf(Entity::isRemoved);
        return list.size();
    }

    /** 这一种现在还在的（副本） */
    public List<Entity> alive(String kind) {
        count(kind);
        List<Entity> list = live.get(kind);
        return list == null ? List.of() : List.copyOf(list);
    }

    /** 所有种类合计还在的有几个 */
    public int total() {
        int sum = 0;
        for (String kind : List.copyOf(live.keySet())) {
            sum += count(kind);
        }
        return sum;
    }

    /** 把还在的全收掉（首领死了、散去） */
    public void discardAll() {
        for (List<Entity> list : live.values()) {
            for (Entity entity : list) {
                if (!entity.isRemoved()) {
                    entity.discard();
                }
            }
        }
        live.clear();
    }
}
