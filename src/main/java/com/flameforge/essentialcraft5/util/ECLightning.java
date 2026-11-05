package com.flameforge.essentialcraft5.util;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 只在客户端出现的闪电（第七批框架 7-F，从腐化 #15 的写法提出来，余烬元素剑的引雷、以后的轨道打击都用它）。
 * <p>
 * 建一个<b>不进世界</b>的 {@link LightningBolt}（{@code setVisualOnly(true)}），只把「生成实体」包发给正在看那个区块的玩家：
 * 客户端照常画闪电、打雷、闪天，闪完自己消失（{@code LightningBolt.tick} 在客户端也会 discard）。
 * 服务端没有这个实体，所以不会点火、伤人、把村民 / 猪 / 苦力怕变掉、让铜块脱氧、给避雷针充能、发游戏事件——
 * 光是 {@code setVisualOnly(true)} 然后 {@code addFreshEntity} 不够：原版 {@code LightningBolt.tick} 不查 visualOnly，
 * 铜块和避雷针照样受影响（policy §1.1、porting_guide §8.1）。
 * <p>
 * 要伤害就由调用方自己对目标单独造成（带攻击者的伤害源），或者像腐化 #15 那样拿返回的闪电调本人的 {@code thunderHit}。
 */
public final class ECLightning {

    private ECLightning() {
    }

    /**
     * 在 {@code pos} 发一道视觉闪电。
     *
     * @return 建出来的闪电（不在世界里，只当参数用，例如 {@code player.thunderHit(level, bolt)}）；建不出来时返回 null、什么也不发
     */
    @Nullable
    public static LightningBolt visual(ServerLevel level, Vec3 pos) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) {
            return null;
        }
        bolt.moveTo(pos.x, pos.y, pos.z);
        bolt.setVisualOnly(true);
        Packet<?> packet = bolt.getAddEntityPacket();
        for (ServerPlayer viewer : level.getChunkSource().chunkMap.getPlayers(new ChunkPos(bolt.blockPosition()), false)) {
            viewer.connection.send(packet);
        }
        return bolt;
    }
}
