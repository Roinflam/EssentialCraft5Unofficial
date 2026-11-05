package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.GunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 枪的瞄具（游戏总线，客户端，ECGuns；原作 {@code ECEventHandler.onAimZoom}、{@code RenderHandlerEC} 的狙击镜部分）：
 * <ul>
 *   <li>主手的枪装了瞄具、潜行：视野缩小到 1 / 倍率（普通瞄具 1.2×，狙击镜 3~16×）。</li>
 *   <li>倍率超过 10 时再补一刀：原版把视野系数压在 0.1 以下不去（{@code GameRenderer.tickFov}），原作的虚空 12×、恶魔 16× 狙击镜实际都是 10×；
 *       这里在最终视野角上再乘 {@code 10 / 倍率}，随原版的平滑进度渐入。</li>
 *   <li>开狙击镜（第一人称）时藏掉主手的枪和准星（镜子贴图自带十字线）；第三人称照常显示枪（原作连第三人称的枪一起换成了空模型）。</li>
 *   <li>按住枪（连发、预转、装填）时打开任何界面就松开（{@link #onGunScreenOpening}）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class GunsClientEvents {

    /** 原版视野系数的下限 0.1 对应的倍率 */
    private static final float VANILLA_MAX_ZOOM = 10F;
    private static final double VANILLA_MIN_MODIFIER = 0.1D;

    private GunsClientEvents() {
    }

    @SubscribeEvent
    public static void onGunFovModifier(ComputeFovModifierEvent event) {
        float zoom = GunsClient.activeZoom(event.getPlayer());
        if (zoom > 0F) {
            event.setNewFovModifier(1F / zoom);
        }
    }

    @SubscribeEvent
    public static void onGunComputeFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || event.getCamera().getEntity() != minecraft.player) {
            return;
        }
        float zoom = GunsClient.activeZoom(minecraft.player);
        if (zoom <= VANILLA_MAX_ZOOM) {
            return;
        }
        double configured = minecraft.options.fov().get();
        if (configured <= 0D) {
            return;
        }
        // 当前（平滑中的）视野系数；到了原版下限 0.1 才乘满 10 / 倍率
        double modifier = event.getFOV() / configured;
        double progress = Mth.clamp((1D - modifier) / (1D - VANILLA_MIN_MODIFIER), 0D, 1D);
        event.setFOV(event.getFOV() * Mth.lerp(progress, 1D, VANILLA_MAX_ZOOM / zoom));
    }

    /**
     * 按住枪（步枪 / 加特林连发、预转、装填）时打开任何界面就替玩家松开右键：发「松开使用」包、本地停止使用。
     * 原版只在按键处理里发这个包（{@code Minecraft.handleKeybinds}：没按着使用键就 releaseUsingItem），界面开着时按键处理不跑，
     * 这些情况下服务端会一直当你按着，接着开火（加特林最多打完一匣约 33 秒），人却看不见、停不下来：
     * <ul>
     *   <li>多人游戏里按 Esc 开暂停菜单、切出窗口（默认失焦自动暂停）——单人会暂停整合服，不受影响；</li>
     *   <li>骑着马 / 驴 / 羊驼 / 骆驼、坐在运输船里按 E（打开坐骑物品栏只发「打开物品栏」包，这一刻使用键还按着），模组由服务端打开的界面（背包快捷键之类）。
     *       界面里还能把手上的枪换掉，服务端靠 {@link GunItem#canContinueUsing} 拦「借装填时间、跳过预转」，这里让原版客户端连这条路都走不到。</li>
     * </ul>
     * 排在最后（LOWEST）、被别的监听取消的打开不管；E / T 这类原版本来就会松开的，这里先松开，原版那边看到已经不在使用就不再发。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGunScreenOpening(ScreenEvent.Opening event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameMode == null || !player.isUsingItem() || !(player.getUseItem().getItem() instanceof GunItem)) {
            return;
        }
        minecraft.gameMode.releaseUsingItem(player);
    }

    @SubscribeEvent
    public static void onGunRenderHand(RenderHandEvent event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && GunsClient.sniperScoped()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGunRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id()) && GunsClient.sniperScoped()) {
            event.setCanceled(true);
        }
    }
}
