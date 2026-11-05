package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.MagicalWingsItem;
import com.flameforge.essentialcraft5.network.WingsFlight;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 护符与随身物品模块的客户端部分（Forge 总线）：戴在腰带槽的魔力之翼按住跳跃往上飞、下落时缓降。
 * <p>
 * 在本地玩家每刻开头（{@code PlayerTickEvent} START，移动之前）调 {@link MagicalWingsItem#clientWornTick}：
 * 它改本地速度、出粒子，按住跳跃的开关交给框架的 WingsFlight（状态变了才发只带布尔的 C2SWingsPacket），服务端扣 MRU。
 * 原作在 Baubles 的 onWornTick 里读跳跃键（ClientProxy.wingsAction），这里读 {@code options.keyJump}，开着界面时不算按住。
 * 手持翅膀的飞行、背包里的翅膀潜行缓降不经过这里（在物品的 inventoryTick 里）；手里拿着能用的翅膀时戴着的那对让给手持，
 * 手里那对没电时戴着的照常起作用（{@link MagicalWingsItem#heldWingsUsable}）。本模块没有要在模组总线上注册的客户端东西（贴图动画靠 mcmeta）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class CharmsClient {

    private CharmsClient() {
    }

    @SubscribeEvent
    public static void onCharmsLocalPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.side != LogicalSide.CLIENT) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (event.player != minecraft.player || minecraft.player == null) {
            return;
        }
        // 按注册名取（和 WingsFlight 一样），不在这里加载 ECCharms：模块没接线时类加载会往已经关门的 DeferredRegister 里登记
        Item wings = ForgeRegistries.ITEMS.getValue(WingsFlight.WINGS_ID);
        if (!(wings instanceof MagicalWingsItem)) {
            return;
        }
        boolean jumpHeld = minecraft.screen == null && minecraft.options.keyJump.isDown();
        MagicalWingsItem.clientWornTick(minecraft.player, wings, jumpHeld);
    }
}
