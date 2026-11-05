package com.flameforge.essentialcraft5.hoanna.nest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.List;

/**
 * 一种巢穴（数据 {@code data/<ns>/hoanna_nests/<id>.json}，读取见 {@link HoannaNestTypes}；batch13.md §6.4、HS §2.4.4）。
 *
 * @param id             类型 id（文件路径，{@code essentialcraft5:frozen_camp}）
 * @param members        一波里的怪：类型、几只、是不是领头的（领头的一律当精英，别的一律普通）
 * @param spawnRadius    在核心周围几格内找地方放（水平）
 * @param activateRadius 生存 / 冒险的真玩家走到几格内放出这一波；清完时几格内出过力的人分奖励
 * @param leashRadius    放出来的怪拴在核心周围几格（{@code restrictTo}）；这么远以内一直没人，满重置时间就收回这一波
 * @param lootTable      奖励表（每名出过力的人掷一次）
 * @param rechargeTicks  清完以后休眠多久（刻）；−1 = 用配置 {@code general.hoanna_nests.recharge_ticks}
 * @param resetTicks     没人在场多久收回这一波（刻，只在方块实体走着的时候累计）；−1 = 用配置 {@code reset_ticks}
 */
public record HoannaNestType(ResourceLocation id, List<Member> members, int spawnRadius, int activateRadius, int leashRadius,
                             ResourceLocation lootTable, int rechargeTicks, int resetTicks) {

    /** 一波最多几只（batch13.md §6.4） */
    public static final int MAX_WAVE = 6;
    public static final int DEFAULT_SPAWN_RADIUS = 6;
    public static final int DEFAULT_ACTIVATE_RADIUS = 16;
    public static final int DEFAULT_LEASH_RADIUS = 48;
    /** 半径的上限（找地方、看玩家都按这个量级写的） */
    public static final int MAX_RADIUS = 64;
    /** 没写回充 / 重置时间 */
    public static final int USE_CONFIG = -1;

    /**
     * 一波里的一项。
     *
     * @param type   实体类型（要是 Mob；不是的放不出来、记 WARN）
     * @param count  几只（≥ 1）
     * @param leader 领头的：放出来以后 {@code setElite(true)}（没有精英变体的种类不起作用）
     */
    public record Member(EntityType<?> type, int count, boolean leader) {
    }

    public HoannaNestType {
        members = List.copyOf(members);
    }

    /** 一波一共几只 */
    public int waveSize() {
        return members.stream().mapToInt(Member::count).sum();
    }

    public int recharge(int configured) {
        return rechargeTicks >= 0 ? rechargeTicks : configured;
    }

    public int reset(int configured) {
        return resetTicks >= 0 ? resetTicks : configured;
    }
}
