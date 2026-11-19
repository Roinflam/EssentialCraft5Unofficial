package com.flameforge.essentialcraft5.client.vfx.resolve;

import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

/**
 * 结算画面包（{@link S2CBossWarningPacket#RESOLVE}）按画面编号建对应的特效（特效大改第 2 批）。由 {@code BossWarningClient} 收包时调，
 * 持续画面（漩涡、连线）也登记在那边，撤销包一起管。只在客户端加载。
 */
public final class BossResolveClient {

    private BossResolveClient() {
    }

    /** 不认识的编号返回 null（新服务端发了这个客户端还没有的画面：不画，不影响结算） */
    @Nullable
    public static ResolveFx create(S2CBossWarningPacket packet, ClientLevel level) {
        ResolveFx fx = switch (packet.style()) {
            case S2CBossWarningPacket.FX_BACKLASH, S2CBossWarningPacket.FX_PILLAR_RISE -> new EruptionFx(packet, level);
            case S2CBossWarningPacket.FX_CONVERGE -> new ConvergeFx(packet, level);
            case S2CBossWarningPacket.FX_PRISM, S2CBossWarningPacket.FX_SUNFALL -> new SolarFx(packet, level);
            case S2CBossWarningPacket.FX_NOON, S2CBossWarningPacket.FX_STRIKE, S2CBossWarningPacket.FX_RUBBLE, S2CBossWarningPacket.FX_PULSE,
                 S2CBossWarningPacket.FX_SLAM, S2CBossWarningPacket.FX_SHOCK, S2CBossWarningPacket.FX_DISORDER, S2CBossWarningPacket.FX_GOLEM_SLAM,
                 S2CBossWarningPacket.FX_SUPPRESS -> new ImpactFx(packet, level);
            case S2CBossWarningPacket.FX_COMBO, S2CBossWarningPacket.FX_THROE -> new CastFx(packet, level);
            case S2CBossWarningPacket.FX_ARC, S2CBossWarningPacket.FX_LINK -> new ArcFx(packet, level);
            case S2CBossWarningPacket.FX_MIRROR, S2CBossWarningPacket.FX_SHATTER -> new MirrorFx(packet, level);
            case S2CBossWarningPacket.FX_TRACER -> new TracerFx(packet, level);
            case S2CBossWarningPacket.FX_VORTEX -> new VortexFx(packet, level);
            default -> null;
        };
        return fx == null ? null : fx.begin();
    }
}
