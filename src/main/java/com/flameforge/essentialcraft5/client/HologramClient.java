package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.render.HologramRenderer;
import com.flameforge.essentialcraft5.client.render.PlayerCloneRenderer;
import com.flameforge.essentialcraft5.client.sound.HologramMusicSound;
import com.flameforge.essentialcraft5.entity.HologramEntity;
import com.flameforge.essentialcraft5.registry.ECHologram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.sounds.SoundManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 全息投影模块的客户端（模组总线，Forge 自动发现）：全息投影的模型层（64 × 32 的老布局）与渲染器、玩家克隆体渲染器、
 * 护甲破坏者（原版投掷物渲染，画魔力熔渣），以及 Boss 战音乐（{@link #musicTick}，由实体的客户端 tick 调）。
 * 刷怪蛋的颜色由 Forge 的 ForgeSpawnEggItem 自动注册。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HologramClient {

    /** 全息投影的人形模型层：原版人形网格按 64 × 32 的贴图建（左手左腿镜像右边，同原版骷髅的老布局） */
    public static final ModelLayerLocation HOLOGRAM_LAYER = new ModelLayerLocation(EssentialCraft5.id("hologram"), "main");
    /** 多久检查一次音乐还在不在放（刻） */
    private static final int MUSIC_CHECK_INTERVAL = 40;
    /** 每只全息投影正在放的音乐（实体没了就跟着被回收） */
    private static final Map<HologramEntity, HologramMusicSound> MUSIC = new WeakHashMap<>();

    private HologramClient() {
    }

    @SubscribeEvent
    public static void hologramLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HOLOGRAM_LAYER, () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0F), 64, 32));
    }

    @SubscribeEvent
    public static void hologramRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECHologram.HOLOGRAM.get(), HologramRenderer::new);
        event.registerEntityRenderer(ECHologram.PLAYER_CLONE.get(), PlayerCloneRenderer::new);
        event.registerEntityRenderer(ECHologram.ARMOR_DESTROYER.get(), ThrownItemRenderer::new);
    }

    /**
     * 全息投影的客户端 tick 里调：第一次见到它时起播 Boss 战音乐，之后每 {@value #MUSIC_CHECK_INTERVAL} 刻看一眼，
     * 声音引擎把它丢了（重载资源包、换音频设备）就补上。实体死了不再起播。
     */
    public static void musicTick(HologramEntity hologram) {
        if (!hologram.isAlive()) {
            return;
        }
        HologramMusicSound current = MUSIC.get(hologram);
        if (current != null && hologram.tickCount % MUSIC_CHECK_INTERVAL != 0) {
            return;
        }
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        if (current == null || current.isStopped() || !sounds.isActive(current)) {
            HologramMusicSound music = new HologramMusicSound(hologram);
            MUSIC.put(hologram, music);
            sounds.play(music);
        }
    }
}
