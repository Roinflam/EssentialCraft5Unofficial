package com.flameforge.essentialcraft5.client;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.BuilderMode;
import com.flameforge.essentialcraft5.item.BuilderSelection;
import com.flameforge.essentialcraft5.item.BuilderTier;
import com.flameforge.essentialcraft5.item.MagicalBuilderItem;
import com.flameforge.essentialcraft5.registry.ECBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * 创世神之杖的客户端部分（{@code registry/ECBuilder}）：
 * <ul>
 *   <li>三阶魔杖的外观：物品属性 {@code essentialcraft5:builder_tier}（{@link BuilderTier}）。</li>
 *   <li>物品格右下角画半尺寸的选定方块（原作 ItemOverlayMagicalBuilder，L502~524）：背包、快捷栏里一眼看出每根魔杖选的是什么。</li>
 *   <li>主手拿着魔杖时画选区框（原作 RenderHandlerEC L454~585）：品红（1, 0, 1）、线宽 2、关深度测试（隔着墙也看得见）；
 *       只有第一个点时框那一格，两个点都有时框整个选区；只在点所在的维度画（原作不查维度，到了下界照样在同样的坐标画）。</li>
 * </ul>
 * 选区和 NBT 的读法和服务端共用 {@link BuilderSelection}。线条用自建的 RenderType（关深度测试、只写颜色），
 * 状态的设置和恢复都交给 RenderType 的 setup / clear，不直接动 GL 状态。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BuilderClient {

    /** 选区框颜色（原作 GlStateManager.color(1, 0, 1)） */
    private static final float RED = 1F;
    private static final float GREEN = 0F;
    private static final float BLUE = 1F;
    /** 原作 glLineWidth(2) */
    private static final double LINE_WIDTH = 2D;

    private BuilderClient() {
    }

    @SubscribeEvent
    public static void registerBuilderDecorations(RegisterItemDecorationsEvent event) {
        for (RegistryObject<MagicalBuilderItem> wand : ECBuilder.WANDS) {
            event.register(wand.get(), StoredBlockIcon.INSTANCE);
        }
        event.register(ECBuilder.PAVING_WAND.get(), StoredBlockIcon.INSTANCE);
    }

    /** 物品属性 {@code essentialcraft5:builder_tier}（1 ~ 3）：5 个模式物品的模型按它换三阶外观 */
    @SubscribeEvent
    public static void registerBuilderTierProperty(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (RegistryObject<MagicalBuilderItem> wand : ECBuilder.WANDS) {
                ItemProperties.register(wand.get(), ECBuilder.TIER_PREDICATE, (stack, level, entity, seed) -> BuilderTier.of(stack).level());
            }
        });
    }

    /** 物品格右下角的选定方块：半尺寸，画在魔杖图标之上、数量文字之下 */
    private static final class StoredBlockIcon implements IItemDecorator {

        static final StoredBlockIcon INSTANCE = new StoredBlockIcon();

        /** 注册名 → 图标物品（每帧每格都要画，不每次都查注册表） */
        private final Map<ResourceLocation, ItemStack> icons = new HashMap<>();

        @Override
        public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
            ResourceLocation id = BuilderSelection.storedBlock(stack);
            if (id == null) {
                return false;
            }
            ItemStack icon = icons.computeIfAbsent(id, BuilderClient::iconOf);
            if (icon.isEmpty()) {
                return false;
            }
            PoseStack pose = graphics.pose();
            pose.pushPose();
            // 原作 translate(8 + x, 8 + y) + scale(0.5)：图标的 16×16 缩成 8×8 落在右下角。
            // z 往前推 100：魔杖图标在 z≈150，缩半后的方块在 175 附近，压在魔杖上面、数量文字（z=200）下面
            pose.translate(x + 8F, y + 8F, 100F);
            pose.scale(0.5F, 0.5F, 0.5F);
            graphics.renderItem(icon, 0, 0);
            pose.popPose();
            return true;
        }
    }

    private static ItemStack iconOf(ResourceLocation id) {
        Block block = ForgeRegistries.BLOCKS.containsKey(id) ? ForgeRegistries.BLOCKS.getValue(id) : null;
        return block == null ? ItemStack.EMPTY : new ItemStack(block.asItem());
    }

    /** 选区框（Forge 总线） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, value = Dist.CLIENT)
    public static final class BuilderSelectionBox {

        private BuilderSelectionBox() {
        }

        @SubscribeEvent
        public static void onBuilderRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            ClientLevel level = minecraft.level;
            if (player == null || level == null) {
                return;
            }
            ItemStack held = player.getMainHandItem();
            if (BuilderMode.of(held) == null) {
                return;
            }
            BlockPos first = BuilderSelection.first(held);
            if (first == null || !BuilderSelection.inDimension(held, level.dimension())) {
                return;
            }
            AABB box = BuilderSelection.outline(first, BuilderSelection.second(held));
            Vec3 camera = event.getCamera().getPosition();
            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(SelectionRenderType.LINES_THROUGH_WALLS), box, RED, GREEN, BLUE, 1F);
            buffers.endBatch(SelectionRenderType.LINES_THROUGH_WALLS);
            poseStack.popPose();
        }
    }

    /** 继承 RenderType 只是为了用 RenderStateShard 里 protected 的状态常量 */
    private static final class SelectionRenderType extends RenderType {

        /** 同原版 lines，但关深度测试（隔墙可见）、不写深度、画到主画面上、固定线宽 */
        static final RenderType LINES_THROUGH_WALLS = create(EssentialCraft5.MODID + ":builder_selection",
                DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 256, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(OptionalDouble.of(LINE_WIDTH)))
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .createCompositeState(false));

        private SelectionRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                                    boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
        }
    }
}
