package com.flameforge.essentialcraft5.hoanna.portal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraftforge.common.util.ITeleporter;

import java.util.Objects;
import java.util.function.Function;

/**
 * 海纳尔之门的 {@link ITeleporter}：只装一个在 {@code changeDimension} 之前算好的落点（见 {@link HoannaTeleport} 类注释）。
 * {@link #getPortalInfo} 永不返回 null（那时玩家已经从原世界移除，返回 null 会卡在没有世界的状态）；不是原版传送器；照常放传送音效；
 * {@link #placeEntity} 走原版的挪人逻辑，不生成末地平台。
 */
public record HoannaTeleporter(PortalInfo info) implements ITeleporter {

    public HoannaTeleporter {
        Objects.requireNonNull(info, "海纳尔之门的落点必须在换维度之前算好");
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
        return true;
    }
}
