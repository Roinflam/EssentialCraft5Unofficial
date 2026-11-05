package com.flameforge.essentialcraft5.item.module;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 模块快照的作废（{@link ModuleCache}）：装备变化、登录、下线、重生、换维度、关服、配置重载。
 * 盔甲槽里的物品换了对象时快照自己也会发现（拿快照时比对），这里补的是「同一个物品、NBT 变了」和「人换了一个对象」这两种。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class ModuleEvents {

    private ModuleEvents() {
    }

    /** 只在服务端发（原版在服务端比对装备）；同一件物品 NBT 变了也发 */
    @SubscribeEvent
    public static void onModuleEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getSlot().getType() == EquipmentSlot.Type.ARMOR && event.getEntity() instanceof Player player) {
            ModuleCache.invalidate(player);
        }
    }

    @SubscribeEvent
    public static void onModuleLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ModuleCache.invalidate(event.getEntity());
    }

    @SubscribeEvent
    public static void onModuleLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ModuleCache.invalidate(event.getEntity());
        ModuleTicker.forget(event.getEntity());
    }

    @SubscribeEvent
    public static void onModuleRespawn(PlayerEvent.PlayerRespawnEvent event) {
        ModuleCache.invalidate(event.getEntity());
    }

    @SubscribeEvent
    public static void onModuleChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        ModuleCache.invalidate(event.getEntity());
    }

    @SubscribeEvent
    public static void onModuleServerStopped(ServerStoppedEvent event) {
        ModuleCache.clearServer();
        ModuleTicker.clear();
        ModuleTerminal.clear();
    }

    /** 配置加载 / 重载（模组总线；可能在文件监视线程上发，只改计数） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ConfigBus {

        private ConfigBus() {
        }

        @SubscribeEvent
        public static void onModuleConfigLoad(ModConfigEvent.Loading event) {
            if (EssentialCraft5.MODID.equals(event.getConfig().getModId())) {
                ModuleConfig.bump();
            }
        }

        @SubscribeEvent
        public static void onModuleConfigReload(ModConfigEvent.Reloading event) {
            if (EssentialCraft5.MODID.equals(event.getConfig().getModId())) {
                ModuleConfig.bump();
            }
        }
    }
}
