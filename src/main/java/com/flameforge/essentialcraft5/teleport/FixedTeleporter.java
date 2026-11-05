package com.flameforge.essentialcraft5.teleport;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraftforge.common.util.ITeleporter;

import java.util.Objects;
import java.util.function.Function;

/**
 * 只装一个在 {@code changeDimension} 之前算好的落点的 {@link ITeleporter}（魔力传送器跨维度用，见 {@link ECTeleport}）。
 * <p>
 * 照 6a 海纳尔之门的 {@code HoannaTeleporter} 另写一份（不动 6a 的类）：
 * {@link #getPortalInfo} 永不返回 null（那时玩家已经从原世界移除，返回 null 会卡在没有世界的状态）；
 * {@link #placeEntity} 走原版挪人逻辑 {@code apply(false)}（进末地不生成黑曜石平台）；不是原版传送器（从末地回主世界不放终章动画）。
 * 只有 {@link #playTeleportSound} 和它不同：返回 false，不放传送门音效——传送器自己在出发点、到达点各放一次烟花爆炸声。
 */
public record FixedTeleporter(PortalInfo info) implements ITeleporter {

    public FixedTeleporter {
        Objects.requireNonNull(info, "落点必须在换维度之前算好");
    }

    @Override
    public PortalInfo getPortalInfo(Entity entity, ServerLevel destWorld, Function<ServerLevel, PortalInfo> defaultPortalInfo) {
        return info;
    }

    @Override
    public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, Function<Boolean, Entity> repositionEntity) {
        return repositionEntity.apply(false);
    }

    @Override
    public boolean isVanilla() {
        return false;
    }

    @Override
    public boolean playTeleportSound(ServerPlayer player, ServerLevel sourceWorld, ServerLevel destWorld) {
        return false;
    }
}
