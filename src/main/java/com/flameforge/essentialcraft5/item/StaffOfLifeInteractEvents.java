package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 生命法杖（ECStaffs）右键生物的入口，Forge 总线，两端都注册。
 * <p>
 * 原版顺序是 Player#interactOn：先发 {@code EntityInteract} → 生物自己的 mobInteract → 返回 PASS 才轮到物品的 interactLivingEntity。
 * 幼年村民（摇头，要潜行才绕得过去）、野生小马 / 驴 / 骡 / 羊驼（发怒，潜行也绕不过）、自己驯服的小狗（服务端切换坐下，客户端对驯服的狗一律 CONSUME）
 * 都会先返回 SUCCESS，把右键吃掉，法杖就催不熟它们。所以在 {@code EntityInteract} 里先调 {@link StaffOfLifeItem#interactLivingEntity}：
 * 返回不是 PASS（成了，或者别人的宠物、MRU 不够）就取消事件、把结果交回去，生物自己的交互不再发生（狗不会坐下、马不会发怒）；
 * 是 PASS（不是僵尸村民或幼年生物、僵尸村民已经在转化）就不管，照原版走。
 * <p>
 * 最低优先级：领地 / 保护类模组在前面取消了的（没开 receiveCanceled）就不做。混合端的 Bukkit PlayerInteractEntityEvent 在 interactOn 之前，
 * 领地插件照样管得到（policy §7 裁定 4）。取消以后原版不再发 ENTITY_INTERACT 游戏事件，成了的时候这里补发（幽匿感测体照样听得到）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class StaffOfLifeInteractEvents {

    private StaffOfLifeInteractEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof StaffOfLifeItem staff) || !(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        Player player = event.getEntity();
        InteractionResult result = staff.interactLivingEntity(stack, player, target, event.getHand());
        if (result == InteractionResult.PASS) {
            return;
        }
        if (result.consumesAction()) {
            player.level().gameEvent(GameEvent.ENTITY_INTERACT, target.position(), GameEvent.Context.of(player));
        }
        event.setCancellationResult(result);
        event.setCanceled(true);
    }
}
