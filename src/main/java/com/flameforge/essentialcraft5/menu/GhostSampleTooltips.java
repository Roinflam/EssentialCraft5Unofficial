package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 哈希样本（NBT 太大、只存了 id + 内容指纹的幽灵格样本，见 {@link GhostSlots#sampleOf}）的物品提示：
 * 界面上它显示成不带 NBT 的那个物品（例：一本没标题的成书），不说明的话玩家会以为样本丢了内容。只在客户端。
 * 类名带前缀：同包的事件处理器撞名会静默失效（tools/check_event_handlers.py）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GhostSampleTooltips {

    private GhostSampleTooltips() {
    }

    @SubscribeEvent
    public static void onGhostSampleTooltip(ItemTooltipEvent event) {
        if (GhostSlots.isHashed(event.getItemStack())) {
            event.getToolTip().add(Component.translatable("tooltip.essentialcraft5.ghost_sample.hashed").withStyle(ChatFormatting.GRAY));
        }
    }
}
