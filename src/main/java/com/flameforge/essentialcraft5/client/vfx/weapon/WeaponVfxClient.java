package com.flameforge.essentialcraft5.client.vfx.weapon;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.DividerFieldEntity;
import com.flameforge.essentialcraft5.entity.DividerProjectileEntity;
import com.flameforge.essentialcraft5.entity.MRUArrowEntity;
import com.flameforge.essentialcraft5.entity.OrbitalStrikeEntity;
import com.flameforge.essentialcraft5.entity.StormGustEntity;
import com.flameforge.essentialcraft5.client.vfx.VfxEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 武器特效的客户端入口：技能实体、弹丸一进客户端世界就挂上跟着它的特效；每刻看一遍玩家（断罪蓄力、风之剑挥砍、余烬剑命中、遥控器预览）；
 * 客户端初始化时把武器同调事件的正式画法登记进 {@code GearFxClient}。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
public final class WeaponVfxClient {

    private WeaponVfxClient() {
    }

    @SubscribeEvent
    public static void onWeaponVfxJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof OrbitalStrikeEntity strike) {
            VfxEffects.add(new OrbitalStrikeFx(strike));
        } else if (entity instanceof DividerFieldEntity field) {
            VfxEffects.add(new DividerFieldFx(field));
        } else if (entity instanceof DividerProjectileEntity) {
            ProjectileTrailFx.attach(entity, ProjectileTrailFx.DIVIDER);
        } else if (entity instanceof MRUArrowEntity) {
            ProjectileTrailFx.attach(entity, ProjectileTrailFx.MRU_ARROW);
        } else if (entity instanceof StormGustEntity) {
            ProjectileTrailFx.attach(entity, ProjectileTrailFx.STORM_GUST);
        }
    }

    @SubscribeEvent
    public static void onWeaponVfxTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        OrbitalPreviewFx.update(mc.player);
        VerdictClient.tickPlayers(mc.level);
        WindSlashFx.watch(mc.level);
        ElementFx.watchHits(mc.level);
    }

    @SubscribeEvent
    public static void onWeaponVfxUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            reset();
        }
    }

    @SubscribeEvent
    public static void onWeaponVfxLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    private static void reset() {
        GunFireFx.clear();
        VerdictClient.clear();
        WeaponGearFx.clear();
        ProjectileTrailFx.resetCount();
    }

    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {

        private Setup() {
        }

        @SubscribeEvent
        public static void onWeaponVfxSetup(FMLClientSetupEvent event) {
            event.enqueueWork(WeaponGearFx::register);
        }
    }
}
