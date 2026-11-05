package com.flameforge.essentialcraft5.client.render;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.MagicalDisplayBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 魔力显示屏的方块实体渲染（原作 RenderMagicalDisplay）。
 * <p>
 * 坐标系：以方块中心为原点转到「屏的坐标」——+z 朝屏外（FACING），站在屏前的人看来 +x 向右、+y 向上；薄板贴在背面，正面在 z = -0.45。
 * 墙上用 -FACING.toYRot() 绕竖直轴转（同原版告示牌），地上 / 天花板绕 X 轴转 ∓90°（同原版物品展示框）。
 * 图标照物品展示框的画法（FIXED 显示方式、再转 180° 让正面朝外），方块类物品往外挪半个厚度，不陷进墙里。
 * <p>
 * 布局照原作的位置表（原作用 GROUND 显示、位置按贴图边缘量，这里换成以屏中心为原点的等价位置）：
 * <ul>
 *   <li>方式 0：图标居中，边长 0.5 格（原作 GROUND 0.5 倍 × 1）。</li>
 *   <li>方式 1（墙上）：图标 0.25 格、偏上；名字在图标下方，<b>水平居中</b>（原作起点 = 名字长度 / 160，想居中但只在特定长度对得上）。</li>
 *   <li>方式 2（墙上）：图标 0.1875 格在左上；名字在图标右边；物品说明从中间往下，左右各留约 0.1 格边（左沿和图标对齐，不贴屏框）。</li>
 *   <li>地上、天花板只画图标，三种方式只差大小（原作 L36）。名字和说明只在墙上、32 格内画。</li>
 * </ul>
 * 修原作：名字缩放 {@code 0.06F / (长度 / 2)} 是整数除法，1 个字的名字除以 0、2~3 个字的特别大 → 按字体算出的实际宽度缩放、封顶；
 * 说明原作<b>每帧</b>调一次 addInformation → 每块屏缓存，物品变了或每 {@value #TOOLTIP_REFRESH} tick 重算一次。
 */
public class MagicalDisplayRenderer implements BlockEntityRenderer<MagicalDisplayBlockEntity> {

    /** 名字和说明只在这个距离内画（图标照默认 64 格） */
    private static final double TEXT_DISTANCE = 32D;
    /** 屏正面（薄板 0.8 像素厚，贴在背面） */
    private static final float FRONT = -0.5F + 0.05F;
    private static final float TEXT_Z = FRONT + 0.005F;
    /** 三种方式的图标边长（平面物品；方块是它的一半），原作 GROUND（0.5 倍）× 1 / 0.5 / 0.375 */
    private static final float[] ICON_SIZE = {0.5F, 0.25F, 0.1875F};
    private static final int TOOLTIP_REFRESH = 20;
    /** 说明按这个宽度（字体像素）折行 */
    private static final int WRAP_WIDTH = 200;
    private static final int MAX_LINES = 16;
    private static final int WHITE = 0xFFFFFF;

    private final ItemRenderer itemRenderer;
    private final Font font;
    private final BlockEntityRenderDispatcher dispatcher;
    private final Map<MagicalDisplayBlockEntity, Description> descriptions = new WeakHashMap<>();

    public MagicalDisplayRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
        this.font = context.getFont();
        this.dispatcher = context.getBlockEntityRenderDispatcher();
    }

    @Override
    public void render(MagicalDisplayBlockEntity display, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        ItemStack stack = display.getDisplayed();
        if (stack.isEmpty()) {
            return;
        }
        Direction facing = display.facing();
        int mode = Math.floorMod(display.getMode(), ICON_SIZE.length);
        Level level = display.getLevel();
        int seed = (int) display.getBlockPos().asLong();

        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        if (facing.getAxis().isHorizontal()) {
            pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        } else {
            pose.mulPose(Axis.XP.rotationDegrees(facing == Direction.UP ? -90F : 90F));
        }

        boolean wall = facing.getAxis().isHorizontal();
        boolean text = wall && mode > 0 && dispatcher.camera != null
                && Vec3.atCenterOf(display.getBlockPos()).closerThan(dispatcher.camera.getPosition(), TEXT_DISTANCE);
        if (!wall || mode == 0) {
            icon(pose, buffers, stack, 0F, 0F, ICON_SIZE[mode], packedLight, level, seed);
        } else if (mode == 1) {
            icon(pose, buffers, stack, 0F, 0.12F, ICON_SIZE[1], packedLight, level, seed);
            if (text) {
                name(pose, buffers, stack, 0F, -0.08F, 0.84F, 1F / 64F, true, packedLight);
            }
        } else {
            icon(pose, buffers, stack, -0.3F, 0.3F, ICON_SIZE[2], packedLight, level, seed);
            if (text) {
                // 右沿到 0.4，和下面的说明一样不贴屏框
                name(pose, buffers, stack, -0.17F, 0.3F, 0.57F, 1F / 96F, false, packedLight);
                description(pose, buffers, display, stack, packedLight);
            }
        }
        pose.popPose();
    }

    /** (u, v)：图标中心在屏上的位置 */
    private void icon(PoseStack pose, MultiBufferSource buffers, ItemStack stack, float u, float v, float size, int light,
                      Level level, int seed) {
        BakedModel model = itemRenderer.getModel(stack, level, null, seed);
        float depth = model.isGui3d() ? size * 0.25F + 0.01F : 0.02F;
        pose.pushPose();
        pose.translate(u, v, FRONT + depth);
        pose.mulPose(Axis.YP.rotationDegrees(180F));
        pose.scale(size, size, size);
        itemRenderer.render(stack, ItemDisplayContext.FIXED, false, pose, buffers, light, OverlayTexture.NO_OVERLAY, model);
        pose.popPose();
    }

    /**
     * 名字：宽度不超过 maxWidth 格、字号不超过 maxScale（每字体像素多少格）。
     * centered：u 是中心、v 是上沿；否则 u 是左沿、v 是竖直中线（和左边的图标对齐）。
     */
    private void name(PoseStack pose, MultiBufferSource buffers, ItemStack stack, float u, float v, float maxWidth,
                      float maxScale, boolean centered, int light) {
        MutableComponent name = stack.getHoverName().copy().withStyle(stack.getRarity().getStyleModifier());
        if (stack.hasCustomHoverName()) {
            name.withStyle(ChatFormatting.ITALIC);
        }
        FormattedCharSequence line = name.getVisualOrderText();
        int width = Math.max(1, font.width(line));
        float scale = Math.min(maxWidth / width, maxScale);
        float x = centered ? u - width * scale / 2F : u;
        float y = centered ? v : v + font.lineHeight * scale / 2F;
        pose.pushPose();
        pose.translate(x, y, TEXT_Z);
        pose.scale(scale, -scale, scale);
        font.drawInBatch(line, 0F, 0F, WHITE, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }

    /**
     * 物品说明（提示框去掉第一行名字）：从 v = 0.15 往下，宽 0.8 格（u 从 -0.4 到 0.4，左沿和图标左沿对齐；
     * 原来 0.88 格从 -0.44 起，贴着屏框）、高 0.58 格内能放多大放多大，字号封顶 1/128
     */
    private void description(PoseStack pose, MultiBufferSource buffers, MagicalDisplayBlockEntity display, ItemStack stack, int light) {
        Description lines = description(display, stack);
        if (lines.lines().isEmpty()) {
            return;
        }
        float scale = Math.min(1F / 128F, Math.min(0.80F / Math.max(1, lines.width()),
                0.58F / (lines.lines().size() * font.lineHeight)));
        pose.pushPose();
        pose.translate(-0.40F, 0.15F, TEXT_Z);
        pose.scale(scale, -scale, scale);
        int y = 0;
        for (FormattedCharSequence line : lines.lines()) {
            font.drawInBatch(line, 0F, y, WHITE, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
            y += font.lineHeight;
        }
        pose.popPose();
    }

    private Description description(MagicalDisplayBlockEntity display, ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        Description cached = descriptions.get(display);
        if (cached != null && cached.stack() == stack && now - cached.time() < TOOLTIP_REFRESH && now >= cached.time()) {
            return cached;
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        int width = 0;
        try {
            List<Component> tooltip = stack.getTooltipLines(minecraft.player, TooltipFlag.NORMAL);
            for (int i = 1; i < tooltip.size() && lines.size() < MAX_LINES; i++) {
                for (FormattedCharSequence part : font.split(tooltip.get(i), WRAP_WIDTH)) {
                    if (lines.size() >= MAX_LINES) {
                        break;
                    }
                    lines.add(part);
                    width = Math.max(width, font.width(part));
                }
            }
        } catch (RuntimeException e) {
            // 别的模组的提示框监听器在这种场合（不是鼠标悬停）可能出错：说明不画，不让整个画面崩
            EssentialCraft5.LOGGER.debug("魔力显示屏取物品说明失败：{}", stack, e);
            lines.clear();
        }
        Description fresh = new Description(stack, now, List.copyOf(lines), width);
        descriptions.put(display, fresh);
        return fresh;
    }

    private record Description(ItemStack stack, long time, List<FormattedCharSequence> lines, int width) {
    }
}
