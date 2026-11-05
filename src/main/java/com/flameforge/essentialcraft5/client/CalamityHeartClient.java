package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.boss.BossFxClient;
import com.flameforge.essentialcraft5.client.boss.CalamityHeartOverlay;
import com.flameforge.essentialcraft5.client.model.mob.CalamityHeartModel;
import com.flameforge.essentialcraft5.client.model.mob.CalamityPillarModel;
import com.flameforge.essentialcraft5.client.render.mob.ECMobRenderer;
import com.flameforge.essentialcraft5.entity.boss.CalamityHeartEntity;
import com.flameforge.essentialcraft5.entity.boss.CalamityPillarEntity;
import com.flameforge.essentialcraft5.entity.boss.CalamityRubbleEntity;
import com.flameforge.essentialcraft5.network.S2CBossFxPacket;
import com.flameforge.essentialcraft5.registry.ECCalamityHeart;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 第十三批 13e-B「天灾之心」的客户端注册（模组总线，Forge 自动发现）：
 * <ul>
 *   <li>天灾之心：{@link CalamityHeartModel}（128 × 128）带发光层，发光随阶段变亮（护盾 0.6、失衡 0.85、崩解 1），散去时 10 秒淡到 0；
 *       熄光时本地玩家主手 / 副手都没拿光源（{@link CalamityHeartEntity#holdsLight}）就只画眼睛；</li>
 *   <li>灾柱：{@link CalamityPillarModel}（128 × 64、灰度贴图）按那一相的颜色整只染色，主灾那根亮一档；刚破土的那一根从地下升起
 *       （{@link CalamityPillarEntity#riseProgress}）；落石：一块原版石头；</li>
 *   <li>画面：熄光（{@link S2CBossFxPacket#BLACKOUT}）、崩解（{@link S2CBossFxPacket#COLLAPSE}）登记成边缘晕影；
 *       场地天色压暗（{@link S2CBossFxPacket#SKY_DIM}）也是边缘晕影，顺带记下「这只在放音乐」，由 {@link CalamityHeartMusicEvents} 起停音乐；
 *       开场名字、横幅、镜头震动交给 {@link CalamityHeartOverlay}。</li>
 * </ul>
 * 注册类是 {@code registry/ECCalamityHeart}；刷怪蛋的颜色由 Forge 的 ForgeSpawnEggItem 自动注册。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CalamityHeartClient {

    /** 散去时发光层淡掉的刻数（和 dissipate_ticks 默认值一样长） */
    private static final float FADE_TICKS = 200F;
    /** 多久检查一次音乐还在不在放（刻） */
    private static final int MUSIC_CHECK_INTERVAL = 40;
    /** 收到「天色压暗」强度 &gt; 0 的天灾之心（实体 id）：给它放音乐；强度 0 时移出 */
    private static final Map<Integer, CalamityHeartMusic> MUSIC = new HashMap<>();
    private static final Map<Integer, Boolean> WANTED = new HashMap<>();

    private CalamityHeartClient() {
    }

    @SubscribeEvent
    public static void calamityHeartLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CalamityHeartModel.LAYER, CalamityHeartModel::createLayer);
        event.registerLayerDefinition(CalamityPillarModel.LAYER, CalamityPillarModel::createLayer);
    }

    @SubscribeEvent
    public static void calamityHeartRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ECCalamityHeart.CALAMITY_HEART.get(), HeartRenderer::new);
        event.registerEntityRenderer(ECCalamityHeart.CALAMITY_PILLAR.get(), PillarRenderer::new);
        event.registerEntityRenderer(ECCalamityHeart.CALAMITY_RUBBLE.get(), RubbleRenderer::new);
    }

    @SubscribeEvent
    public static void calamityHeartClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            CalamityHeartOverlay.register();
            BossFxClient.registerEdge(S2CBossFxPacket.BLACKOUT);
            BossFxClient.registerEdge(S2CBossFxPacket.COLLAPSE);
            BossFxClient.register(S2CBossFxPacket.SKY_DIM, (packet, boss) -> {
                BossFxClient.edge(packet.type(), packet.entityId(), Math.round(packet.b()), packet.a());
                if (packet.a() > 0F) {
                    WANTED.put(packet.entityId(), Boolean.TRUE);
                } else {
                    WANTED.remove(packet.entityId());
                }
            });
        });
    }

    /** 发光层的强度：按阶段变亮；散去时 ticksIntoFade 刻里淡到 0 */
    static float glowFor(CalamityHeartEntity.Phase phase, boolean dissipating, int ticksIntoFade, float partialTick) {
        float base = switch (phase) {
            case SHIELD -> 0.6F;
            case UNBALANCED -> 0.85F;
            case COLLAPSE -> 1F;
        };
        if (!dissipating) {
            return base;
        }
        return base * (1F - Mth.clamp((ticksIntoFade + partialTick) / FADE_TICKS, 0F, 1F));
    }

    /** 熄光时本地玩家看不看得见本体（拿着光源才看得见） */
    static boolean eyesOnly(CalamityHeartEntity entity) {
        if (!entity.blackout()) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null || !CalamityHeartEntity.holdsLight(player);
    }

    /** 天灾之心 */
    static final class HeartRenderer extends ECMobRenderer<CalamityHeartEntity, CalamityHeartModel> {

        HeartRenderer(EntityRendererProvider.Context context) {
            super(context, new CalamityHeartModel(context.bakeLayer(CalamityHeartModel.LAYER)), 1.0F, "calamity_heart");
            withGlow();
        }

        @Override
        public void render(CalamityHeartEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            getModel().eyesOnly = eyesOnly(entity);
            super.render(entity, yaw, partialTick, pose, buffers, light);
        }

        @Override
        public float glowAlpha(CalamityHeartEntity entity, float partialTick) {
            int fade = entity.dissipating() ? Math.max(0, entity.tickCount - entity.dissipateStartTick()) : 0;
            return glowFor(entity.phase(), entity.dissipating(), fade, partialTick);
        }
    }

    /** 灾柱：灰度贴图按那一相的颜色染，主灾那根发光层亮一档 */
    static final class PillarRenderer extends ECMobRenderer<CalamityPillarEntity, CalamityPillarModel> {

        PillarRenderer(EntityRendererProvider.Context context) {
            super(context, new CalamityPillarModel(context.bakeLayer(CalamityPillarModel.LAYER)), 0.7F, "calamity_pillar");
            withTint();
            withGlow();
        }

        /** 还没到破土时刻的整根不画（连影子） */
        @Override
        public boolean shouldRender(CalamityPillarEntity entity, Frustum frustum, double x, double y, double z) {
            return entity.riseProgress(0F) > 0F && super.shouldRender(entity, frustum, x, y, z);
        }

        /** 刚破土的那一根：整根从地下往上升（按破土动画走到哪往下压） */
        @Override
        public void render(CalamityPillarEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            float rise = entity.riseProgress(partialTick);
            if (rise >= 1F) {
                super.render(entity, yaw, partialTick, pose, buffers, light);
                return;
            }
            float eased = 1F - (1F - rise) * (1F - rise);
            pose.pushPose();
            pose.translate(0D, -(1F - eased) * entity.getBbHeight(), 0D);
            super.render(entity, yaw, partialTick, pose, buffers, light);
            pose.popPose();
        }

        @Override
        public int tintColor(CalamityPillarEntity entity) {
            return entity.aspect().color();
        }

        @Override
        public float glowAlpha(CalamityPillarEntity entity, float partialTick) {
            return entity.main() ? 1F : 0.6F;
        }

        /**
         * 灰白发光层按相色和白色各一半染：直接乘相色时黑暗相（#4A1E7A）在 0.6 强度下暗处几乎看不见，
         * 不染又四根都是灰白、暗处分不出哪一相。
         */
        @Override
        public int glowColor(CalamityPillarEntity entity) {
            int c = entity.aspect().color();
            int r = (((c >> 16) & 0xFF) + 0xFF) / 2;
            int g = (((c >> 8) & 0xFF) + 0xFF) / 2;
            int b = ((c & 0xFF) + 0xFF) / 2;
            return (r << 16) | (g << 8) | b;
        }
    }

    /** 落石：一块原版石头（按方块模型画，不用自己的贴图） */
    static final class RubbleRenderer extends EntityRenderer<CalamityRubbleEntity> {

        private static final BlockState STONE = Blocks.STONE.defaultBlockState();
        private final BlockRenderDispatcher blocks;

        RubbleRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.blocks = context.getBlockRenderDispatcher();
            this.shadowRadius = 0.5F;
        }

        @Override
        public void render(CalamityRubbleEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
            pose.pushPose();
            float spin = (entity.tickCount + partialTick) * 9F;
            pose.translate(0D, 0.5D, 0D);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(spin));
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(spin * 0.7F));
            pose.translate(-0.5D, -0.5D, -0.5D);
            blocks.renderSingleBlock(STONE, pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            pose.popPose();
            super.render(entity, yaw, partialTick, pose, buffers, light);
        }

        @Override
        @SuppressWarnings("deprecation")
        public ResourceLocation getTextureLocation(CalamityRubbleEntity entity) {
            return TextureAtlas.LOCATION_BLOCKS;
        }
    }

    /**
     * 首领音乐：绑在一只天灾之心身上的循环音乐（RECORDS 类别、不做距离衰减，同全息投影的音乐）。
     * 只在本地玩家收到它的「天色压暗」（= 在它的名单里）时放，强度 0（打倒、散去、离开名单）或实体没了就停；客户端配置关掉时不放。
     */
    static final class CalamityHeartMusic extends AbstractTickableSoundInstance {

        private final int entityId;

        CalamityHeartMusic(Entity heart) {
            super(ECCalamityHeart.MUSIC.get(), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.entityId = heart.getId();
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
            this.x = 0D;
            this.y = 0D;
            this.z = 0D;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            Entity heart = mc.level == null ? null : mc.level.getEntity(entityId);
            if (heart == null || !heart.isAlive() || !WANTED.containsKey(entityId) || !ECCalamityHeart.ClientConfig.music()) {
                stop();
            }
        }
    }

    /** 音乐的起停（Forge 总线，客户端；方法名带 calamityHeart 前缀） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
    public static final class CalamityHeartMusicEvents {

        private CalamityHeartMusicEvents() {
        }

        @SubscribeEvent
        public static void calamityHeartMusicTick(TickEvent.ClientTickEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (event.phase != TickEvent.Phase.END || mc.level == null) {
                return;
            }
            SoundManager sounds = mc.getSoundManager();
            for (Iterator<Map.Entry<Integer, CalamityHeartMusic>> it = MUSIC.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<Integer, CalamityHeartMusic> entry = it.next();
                if (entry.getValue().isStopped()) {
                    it.remove();
                }
            }
            WANTED.keySet().removeIf(id -> {
                Entity heart = mc.level.getEntity(id);
                return heart == null || !heart.isAlive();
            });
            if (!ECCalamityHeart.ClientConfig.music() || mc.level.getGameTime() % MUSIC_CHECK_INTERVAL != 0 && !newWanted()) {
                return;
            }
            for (Integer id : WANTED.keySet()) {
                CalamityHeartMusic current = MUSIC.get(id);
                if (current == null || current.isStopped() || !sounds.isActive(current)) {
                    Entity heart = mc.level.getEntity(id);
                    if (heart != null) {
                        CalamityHeartMusic music = new CalamityHeartMusic(heart);
                        MUSIC.put(id, music);
                        sounds.play(music);
                    }
                }
            }
        }

        private static boolean newWanted() {
            for (Integer id : WANTED.keySet()) {
                if (!MUSIC.containsKey(id)) {
                    return true;
                }
            }
            return false;
        }

        @SubscribeEvent
        public static void calamityHeartMusicLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            for (CalamityHeartMusic music : MUSIC.values()) {
                Minecraft.getInstance().getSoundManager().stop(music);
            }
            MUSIC.clear();
            WANTED.clear();
        }
    }
}
