package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.item.WindGearEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeHooks;

/**
 * 风之护甲空中跳的服务端测试用：照原版 {@code ServerGamePacketListenerImpl.handleMovePlayer} 里和起跳、摔落距离有关的几步，
 * 一个一个地「收」移动包，再按原版一刻的顺序调 {@link WindGearEvents} 的两头（GameTest 和混合端自检 C63 共用）。
 * <ul>
 *   <li>服务端记着在地上、这个包报离地且往上走：原版调 {@code jumpFromGround}，这里只发它里面的 Forge 事件 {@code LivingJumpEvent}
 *       （不加统计、不扣饱食度）；</li>
 *   <li>摔落距离：报着地的包清零（原版先结算摔伤，这里不结算），往下走累加，往上走清零（{@code if (flag) player.resetFallDistance()}）；</li>
 *   <li>一刻：先 {@link WindGearEvents#serverTickStart}（PlayerTickEvent START，位置是最后一个包报的），再 {@link WindGearEvents#serverTick}（END）。
 *       服务端自己按重力模拟的那一步不在这里，要模拟它就在两刻之间直接 {@code setOnGround(true)}。</li>
 * </ul>
 * 测试玩家不在世界里、不自己 tick，所以位置、onGround、摔落距离全由这里设。
 */
public final class WindMovePackets {

    private WindMovePackets() {
    }

    /** 收一个移动包：挪到 (x, y, z)，客户端报的着地是 onGround */
    public static void packet(ServerPlayer player, double x, double y, double z, boolean onGround) {
        double dy = y - player.getY();
        if (player.onGround() && !onGround && dy > 0D) {
            ForgeHooks.onLivingJump(player);
        }
        if (onGround) {
            player.resetFallDistance();
        } else if (dy < 0D) {
            player.fallDistance -= (float) dy;
        }
        player.setPos(x, y, z);
        player.setOnGround(onGround);
        if (dy > 0D) {
            player.resetFallDistance();
        }
    }

    /** 竖直方向收一个包（水平不动） */
    public static void packet(ServerPlayer player, double y, boolean onGround) {
        packet(player, player.getX(), y, player.getZ(), onGround);
    }

    /** 服务端一刻（START 认空中跳、END 补摘修饰符） */
    public static void tick(ServerPlayer player) {
        WindGearEvents.serverTickStart(player);
        WindGearEvents.serverTick(player);
    }
}
