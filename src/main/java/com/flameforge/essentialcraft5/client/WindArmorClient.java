package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.WindGearEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.lang.ref.WeakReference;

/**
 * 风之护甲全套的空中跳（第十三批 13b，batch13.md §4.3「御风」）：本地玩家在空中<b>新按下</b>跳跃（上一刻没按着）时调
 * {@code WindGearEvents.airJump}，自己把竖直速度提上去。位置本来就由客户端定、服务端照着挪，所以不加包；空中跳那一下的移动包往上走，
 * 原版服务端收包时就把摔落距离清了，服务端另外从移动包的痕迹认出这一跳、给旁人放粒子（{@code WindGearEvents.trackMotion}）。
 * <ul>
 *   <li>按着跳跃走下边沿、或从地上起跳以后一直按着，都不算新按下；上一刻还在地上时按下的也不算（{@code WindGearEvents.isAirJumpPress}）：
 *       那一下原版已经拿去地面起跳了，头顶只有两格高、起跳撞了天花板时也一样，免得一按跳两下。</li>
 *   <li>着地（地上、水里、岩浆里、梯子上、坐骑上）次数清零；换了玩家对象（重生、换维度）重新记。</li>
 *   <li>次数、速度读 {@code general.wind_armor}（SERVER 配置，进服后是服务器同步来的值），服务器填 0 就跳不了。</li>
 * </ul>
 * 读的是 {@code LocalPlayer.input.jumping}（原版这一刻处理过的跳跃键状态），在玩家这一刻走完以后（PlayerTickEvent END）判断，速度下一刻生效。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class WindArmorClient {

    private static WeakReference<LocalPlayer> tracked = new WeakReference<>(null);
    private static boolean wasJumping;
    private static boolean wasGrounded = true;
    private static int used;

    private WindArmorClient() {
    }

    @SubscribeEvent
    public static void windArmorClientTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.CLIENT) {
            return;
        }
        if (event.player instanceof LocalPlayer player && player == Minecraft.getInstance().player) {
            tick(player);
        }
    }

    private static void tick(LocalPlayer player) {
        boolean jumping = player.input.jumping;
        if (tracked.get() != player) {
            tracked = new WeakReference<>(player);
            wasJumping = jumping;
            wasGrounded = true;
            used = 0;
            return;
        }
        boolean pressed = jumping && !wasJumping;
        wasJumping = jumping;
        boolean grounded = player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable() || player.isPassenger();
        boolean groundedBefore = wasGrounded;
        wasGrounded = grounded;
        if (grounded) {
            used = 0;
            return;
        }
        if (!WindGearEvents.isAirJumpPress(pressed, grounded, groundedBefore)) {
            return;
        }
        if (WindGearEvents.airJump(player, used)) {
            used++;
            WindGearEvents.airJumpEffects(player);
        }
    }
}
