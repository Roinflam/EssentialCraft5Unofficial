package com.flameforge.essentialcraft5.client.vfx.warn;

import com.flameforge.essentialcraft5.client.vfx.VfxCracks;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import com.flameforge.essentialcraft5.client.vfx.resolve.BossResolveClient;
import com.flameforge.essentialcraft5.client.vfx.resolve.ResolveFx;
import com.flameforge.essentialcraft5.network.S2CBossWarningPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 首领预警包（{@link S2CBossWarningPacket}）的客户端处理：按类型建对应的预警组件交给 {@link VfxEffects}，撤销包把同一所属实体的全部淡掉。
 * 结算画面（{@link S2CBossWarningPacket#RESOLVE}）交给 {@link BossResolveClient}；其中持续的（漩涡、连线）也记在这里，撤销包一起撤。
 * 换世界时 {@link VfxEffects} 清空特效，这里的名单只留没结束的，下次收包时顺手清。只在客户端加载。
 */
public final class BossWarningClient {

    private static final List<WarnFx> ACTIVE = new ArrayList<>();
    private static final List<ResolveFx> ATTACHED = new ArrayList<>();

    private BossWarningClient() {
    }

    public static void handle(S2CBossWarningPacket packet) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (packet.kind() == S2CBossWarningPacket.CANCEL) {
            cancel(packet.owner());
            return;
        }
        if (packet.kind() == S2CBossWarningPacket.RESOLVE) {
            ResolveFx fx = BossResolveClient.create(packet, level);
            if (fx != null) {
                if (packet.persistent()) {
                    ATTACHED.removeIf(ResolveFx::finished);
                    ATTACHED.add(fx);
                }
                VfxEffects.add(fx);
            }
            return;
        }
        WarnFx fx = create(packet, level);
        if (fx != null) {
            ACTIVE.removeIf(WarnFx::finished);
            ACTIVE.add(fx);
            VfxEffects.add(fx);
        }
    }

    /** 按类型建预警组件；不认识的类型返回 null */
    @Nullable
    public static WarnFx create(S2CBossWarningPacket packet, ClientLevel level) {
        return switch (packet.kind()) {
            case S2CBossWarningPacket.CIRCLE -> new WarnCircleFx(packet, level);
            case S2CBossWarningPacket.LINE -> new WarnLineFx(packet, level);
            case S2CBossWarningPacket.COLUMN -> new WarnColumnFx(packet, level);
            case S2CBossWarningPacket.RING -> new WarnRingFx(packet, level);
            case S2CBossWarningPacket.SAFE_ZONE -> new WarnSafeZoneFx(packet, level);
            default -> null;
        };
    }

    /** 撤掉某个所属实体的全部预警 */
    public static void cancel(int owner) {
        for (WarnFx fx : ACTIVE) {
            if (fx.owner() == owner) {
                fx.cancel();
            }
        }
        for (ResolveFx fx : ATTACHED) {
            if (fx.owner() == owner) {
                fx.cancel();
            }
        }
        ACTIVE.removeIf(WarnFx::finished);
        ATTACHED.removeIf(ResolveFx::finished);
    }

    /**
     * 结算接手预警的裂纹网（预警时裂开的那张网落下时直接炸亮，不换形状）：同一所属实体、圆心 1.5 格内的那个预警交出来，
     * 交出去后预警自己不再画；配不上（没有裂纹网 / 早撤了）就是 null，结算照旧画自己的裂纹。
     */
    @Nullable
    public static VfxCracks claimCracks(int owner, Vec3 at) {
        for (WarnFx fx : ACTIVE) {
            if (fx.owner() == owner) {
                VfxCracks cracks = fx.handOff(at);
                if (cracks != null) {
                    return cracks;
                }
            }
        }
        return null;
    }

    /** 还没结束的预警有几个（调试用） */
    public static int active() {
        ACTIVE.removeIf(WarnFx::finished);
        return ACTIVE.size();
    }
}
