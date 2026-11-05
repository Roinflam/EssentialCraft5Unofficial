package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.guide.CodexTheme;
import com.flameforge.essentialcraft5.client.hoanna.HoannaClientEvents;
import com.flameforge.essentialcraft5.hoanna.HoannaSurveyClientConfig;
import com.flameforge.essentialcraft5.hoanna.event.HoannaEvent;
import com.flameforge.essentialcraft5.item.HoannaSurveyMapItem;
import com.flameforge.essentialcraft5.item.InquisitoriumCoordinatesItem;
import com.flameforge.essentialcraft5.loot.condition.HoannaLootConditions;
import com.flameforge.essentialcraft5.registry.ECHoanna;
import com.flameforge.essentialcraft5.registry.ECHoannaSurvey;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * 第十三批 13d-H3「探索与续航」的客户端（batch13.md §6.3；HS §2.3.1）：
 * <ul>
 *   <li>模组总线：勘测图的物品属性 {@link HoannaSurveyMapItem#STATE_PREDICATE}（没绑定 / 走到过 / 已绑定三张图）。</li>
 *   <li>Forge 总线（{@link HoannaSurveyClientEvents}）：主手或副手拿着已绑定的勘测图、人在海纳尔时，每 {@value #HEADING_INTERVAL} 刻在动作栏写
 *       八方位和水平距离（按物品 NBT 自己算，不发包；照审判庭坐标的写法）；夜里或黑暗天灾中、目标在
 *       {@value #BEAM_MIN}~{@value #BEAM_MAX} 格时，在目标那一列画一道只有自己看得见的淡紫光柱（宽 1 格、从地表到建筑高度上限，
 *       alpha {@value #BEAM_ALPHA}，不闪、不呼吸；{@code [hoanna] survey_beam} 可关）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HoannaSurveyClient {

    /** 动作栏多久刷新一次（刻；一条显示 3 秒，10 刻刷一次就一直亮着） */
    public static final int HEADING_INTERVAL = 10;
    /** 光柱只在目标离你这么远时画（格，水平） */
    public static final int BEAM_MIN = 32;
    public static final int BEAM_MAX = 512;
    /** 光柱的不透明度（固定，不随时间变） */
    public static final float BEAM_ALPHA = 0.35F;

    private HoannaSurveyClient() {
    }

    @SubscribeEvent
    public static void hoannaSurveyClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ECHoannaSurvey.HOANNA_SURVEY_MAP.get(), HoannaSurveyMapItem.STATE_PREDICATE,
                (stack, level, entity, seed) -> HoannaSurveyMapItem.modelState(stack)));
    }

    /** 主手优先、再副手：拿着的勘测图（没拿着为空） */
    public static ItemStack heldMap(LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof HoannaSurveyMapItem) {
            return player.getMainHandItem();
        }
        return player.getOffhandItem().getItem() instanceof HoannaSurveyMapItem ? player.getOffhandItem() : ItemStack.EMPTY;
    }

    /** 动作栏那一句（没拿着、没绑定、不在海纳尔为 null） */
    @Nullable
    public static Component headingLine(LocalPlayer player) {
        ItemStack held = heldMap(player);
        BlockPos target = held.isEmpty() ? null : HoannaSurveyMapItem.target(held);
        if (target == null || !ECHoanna.isHoanna(player.level())) {
            return null;
        }
        Component kind = HoannaSurveyMapItem.kindName(held);
        Component label = kind != null ? kind : Component.translatable(HoannaSurveyMapItem.TOWN.nameKey());
        double distance = HoannaSurveyMapItem.horizontalDistance(target, player.getX(), player.getZ());
        if (distance <= ECHoannaSurvey.Config.values().arriveRadius()) {
            return Component.translatable("message.essentialcraft5.hoanna_survey_map.here", label).withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        int heading = InquisitoriumCoordinatesItem.heading(target.getX() + 0.5D - player.getX(), target.getZ() + 0.5D - player.getZ());
        return Component.translatable("message.essentialcraft5.hoanna_survey_map.heading", label,
                Component.translatable(InquisitoriumCoordinatesItem.headingKey(heading)), Mth.floor(distance)).withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    /** 这一帧画不画光柱、画在哪（不画为 null） */
    @Nullable
    static BlockPos beamTarget(LocalPlayer player, ClientLevel level) {
        if (!HoannaSurveyClientConfig.surveyBeam() || !ECHoanna.isHoanna(level)) {
            return null;
        }
        if (!HoannaLootConditions.isNight(level.getDayTime()) && HoannaClientEvents.current() != HoannaEvent.DARKNESS) {
            return null;
        }
        ItemStack held = heldMap(player);
        BlockPos target = held.isEmpty() ? null : HoannaSurveyMapItem.target(held);
        if (target == null) {
            return null;
        }
        double distance = HoannaSurveyMapItem.horizontalDistance(target, player.getX(), player.getZ());
        return distance >= BEAM_MIN && distance <= BEAM_MAX ? target : null;
    }

    /** Forge 总线上的客户端事件 */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
    public static final class HoannaSurveyClientEvents {

        private HoannaSurveyClientEvents() {
        }

        @SubscribeEvent
        public static void onHoannaSurveyClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player == null || minecraft.isPaused() || player.tickCount % HEADING_INTERVAL != 0) {
                return;
            }
            Component line = headingLine(player);
            if (line != null) {
                minecraft.gui.setOverlayMessage(line, false);
            }
        }

        @SubscribeEvent
        public static void onHoannaSurveyRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            ClientLevel level = minecraft.level;
            if (player == null || level == null) {
                return;
            }
            BlockPos target = beamTarget(player, level);
            if (target == null) {
                return;
            }
            int bottom = level.hasChunkAt(target) ? level.getHeight(Heightmap.Types.WORLD_SURFACE, target.getX(), target.getZ()) : level.getSeaLevel();
            int top = level.getMaxBuildHeight();
            if (bottom >= top) {
                return;
            }
            Vec3 camera = event.getCamera().getPosition();
            PoseStack poseStack = event.getPoseStack();
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            VertexConsumer quads = buffers.getBuffer(BeamRenderType.BEAM);
            Matrix4f pose = poseStack.last().pose();
            float x0 = (float) (target.getX() - camera.x);
            float z0 = (float) (target.getZ() - camera.z);
            float x1 = x0 + 1F;
            float z1 = z0 + 1F;
            float y0 = (float) (bottom - camera.y);
            float y1 = (float) (top - camera.y);
            int rgb = CodexTheme.MRU_VIOLET;
            float r = ((rgb >> 16) & 0xFF) / 255F;
            float g = ((rgb >> 8) & 0xFF) / 255F;
            float b = (rgb & 0xFF) / 255F;
            face(quads, pose, x0, z0, x1, z0, y0, y1, r, g, b);
            face(quads, pose, x1, z0, x1, z1, y0, y1, r, g, b);
            face(quads, pose, x1, z1, x0, z1, y0, y1, r, g, b);
            face(quads, pose, x0, z1, x0, z0, y0, y1, r, g, b);
            buffers.endBatch(BeamRenderType.BEAM);
        }

        private static void face(VertexConsumer quads, Matrix4f pose, float ax, float az, float bx, float bz, float y0, float y1,
                                 float r, float g, float b) {
            quads.vertex(pose, ax, y0, az).color(r, g, b, BEAM_ALPHA).endVertex();
            quads.vertex(pose, bx, y0, bz).color(r, g, b, BEAM_ALPHA).endVertex();
            quads.vertex(pose, bx, y1, bz).color(r, g, b, BEAM_ALPHA).endVertex();
            quads.vertex(pose, ax, y1, az).color(r, g, b, BEAM_ALPHA).endVertex();
        }
    }

    /** 继承 RenderType 只是为了用 RenderStateShard 里 protected 的状态常量 */
    private static final class BeamRenderType extends RenderType {

        /** 纯色半透明四边形：不写深度（叠在别的半透明东西上不挖洞）、两面都画、照常被地形挡住 */
        static final RenderType BEAM = create(EssentialCraft5.MODID + ":survey_beam",
                DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private BeamRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                               boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }
    }
}
