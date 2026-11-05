package com.flameforge.essentialcraft5.dev;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * 界面版式检查：只在 {@code -Dessentialcraft5.guiAudit=true} 时生效（./gradlew runClient -PecGuiAudit，和展示厅一起用）。
 * <p>
 * 展示厅每拍一张开着界面的图，就拿 {@link Recorder} 把当前界面再「空跑」一遍：鼠标放在屏幕外（不出悬停提示），
 * 每段文字、每次贴图、每块填色、每个物品图标都只记下变换后的坐标（界面坐标，未乘缩放）和调用处，不真的画。
 * 结果写到 {@code run/guiaudit/<语言>/<截图名>.json}，由 {@code tools/guiaudit/analyze.py} 查文字出框、压格子、文字互相重叠、
 * 文字骑在贴图边上等问题。研究手册也录（-PecShowroomGuide -PecGuiAudit）：空跑会动它的悬停和电弧状态，之后的截图不一定和平时一样，
 * 检查模式只看录下的数据。
 */
public final class GuiAudit {

    public static final boolean ENABLED = Boolean.getBoolean("essentialcraft5.guiAudit");
    /** 拍研究手册时只拍哪一轮（v427 / v640 / v1280，见 DevShowroom.GUIDE_ROUNDS） */
    static final String GUIDE_ROUND = System.getProperty("essentialcraft5.guiAudit.guideRound", "v640");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String OWN_PACKAGE = "com.flameforge.essentialcraft5.";

    private GuiAudit() {
    }

    /** 展示厅截图之后调：当前开着界面就录一份 */
    public static void record(Minecraft mc, String name) {
        Screen screen = mc.screen;
        if (!ENABLED || screen == null) {
            return;
        }
        Recorder recorder = new Recorder(mc);
        JsonObject root = new JsonObject();
        root.addProperty("name", name);
        root.addProperty("screen", screen.getClass().getName());
        root.addProperty("lang", mc.getLanguageManager().getSelected());
        root.addProperty("guiScale", mc.getWindow().getGuiScale());
        root.addProperty("width", screen.width);
        root.addProperty("height", screen.height);
        if (screen instanceof AbstractContainerScreen<?> container) {
            JsonObject panel = new JsonObject();
            panel.addProperty("x", container.getGuiLeft());
            panel.addProperty("y", container.getGuiTop());
            panel.addProperty("w", container.getXSize());
            panel.addProperty("h", container.getYSize());
            root.add("panel", panel);
            JsonArray slots = new JsonArray();
            for (Slot slot : container.getMenu().slots) {
                if (!slot.isActive()) {
                    continue;
                }
                JsonObject s = new JsonObject();
                s.addProperty("x", container.getGuiLeft() + slot.x);
                s.addProperty("y", container.getGuiTop() + slot.y);
                s.addProperty("index", slot.index);
                slots.add(s);
            }
            root.add("slots", slots);
        }
        try {
            screen.render(recorder, -10000, -10000, 0F);
        } catch (RuntimeException e) {
            root.addProperty("error", e.toString());
            EssentialCraft5.LOGGER.warn("[界面检查] {} 空跑出错", name, e);
        }
        root.add("ops", recorder.ops);
        Path dir = mc.gameDirectory.toPath().resolve("guiaudit").resolve(mc.getLanguageManager().getSelected());
        try {
            Files.createDirectories(dir);
            try (Writer writer = Files.newBufferedWriter(dir.resolve(name + ".json"), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            EssentialCraft5.LOGGER.info("[界面检查] {}：{} 笔", name, recorder.ops.size());
        } catch (IOException e) {
            EssentialCraft5.LOGGER.warn("[界面检查] 写 {} 失败", name, e);
        }
    }

    /** 只记不画的 GuiGraphics：所有公开的绘制入口最后都落到这里覆盖的几个方法上 */
    static final class Recorder extends GuiGraphics {

        final JsonArray ops = new JsonArray();
        private final Deque<int[]> scissors = new ArrayDeque<>();
        /** 正在画物品数量 / 耐久条时为 true，这期间的文字记成 decoration */
        private boolean decorating;

        Recorder(Minecraft mc) {
            super(mc, MultiBufferSource.immediate(new BufferBuilder(256)));
        }

        private JsonObject op(String type, float x0, float y0, float x1, float y1) {
            Matrix4f m = pose().last().pose();
            Vector4f a = m.transform(new Vector4f(x0, y0, 0F, 1F));
            Vector4f b = m.transform(new Vector4f(x1, y1, 0F, 1F));
            JsonObject o = new JsonObject();
            o.addProperty("type", type);
            o.addProperty("x0", Math.min(a.x, b.x));
            o.addProperty("y0", Math.min(a.y, b.y));
            o.addProperty("x1", Math.max(a.x, b.x));
            o.addProperty("y1", Math.max(a.y, b.y));
            float scale = m.m00();
            if (Math.abs(scale - 1F) > 1.0E-3F) {
                o.addProperty("scale", scale);
            }
            if (!scissors.isEmpty()) {
                int[] s = scissors.peek();
                JsonArray clip = new JsonArray();
                for (int v : s) {
                    clip.add(v);
                }
                o.add("clip", clip);
            }
            o.addProperty("at", caller());
            ops.add(o);
            return o;
        }

        /** 第一个本模组的调用处（跳过本类），没有就记原版里最近的一处 */
        private static String caller() {
            return StackWalker.getInstance().walk(frames -> {
                Optional<StackWalker.StackFrame> own = frames
                        .filter(f -> f.getClassName().startsWith(OWN_PACKAGE) && !f.getClassName().startsWith(GuiAudit.class.getName()))
                        .findFirst();
                return own.map(f -> f.getClassName().substring(OWN_PACKAGE.length()) + ":" + f.getLineNumber()).orElse("");
            });
        }

        private void text(Font font, String plain, int width, float x, float y, int color, boolean shadow) {
            JsonObject o = op(decorating ? "decoration" : "text", x, y, x + width, y + font.lineHeight);
            o.addProperty("text", plain);
            o.addProperty("w", width);
            o.addProperty("color", String.format("%08X", color));
            o.addProperty("shadow", shadow);
        }

        @Override
        public int drawString(Font font, String text, float x, float y, int color, boolean shadow) {
            if (text == null) {
                return 0;
            }
            int width = font.width(text);
            text(font, text, width, x, y, color, shadow);
            return (int) x + width + (shadow ? 1 : 0);
        }

        @Override
        public int drawString(Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow) {
            StringBuilder plain = new StringBuilder();
            text.accept((index, style, codePoint) -> {
                plain.appendCodePoint(codePoint);
                return true;
            });
            int width = font.width(text);
            text(font, plain.toString(), width, x, y, color, shadow);
            return (int) x + width + (shadow ? 1 : 0);
        }

        private void blitOp(ResourceLocation texture, int x, int y, int w, int h, float u, float v, int uw, int vh, int tw, int th) {
            JsonObject o = op("blit", x, y, x + w, y + h);
            o.addProperty("tex", texture.toString());
            o.addProperty("u", u);
            o.addProperty("v", v);
            o.addProperty("uw", uw);
            o.addProperty("vh", vh);
            o.addProperty("tw", tw);
            o.addProperty("th", th);
        }

        @Override
        public void blit(ResourceLocation texture, int x, int y, int z, float u, float v, int w, int h, int tw, int th) {
            blitOp(texture, x, y, w, h, u, v, w, h, tw, th);
        }

        @Override
        public void blit(ResourceLocation texture, int x, int y, int w, int h, float u, float v, int uw, int vh, int tw, int th) {
            blitOp(texture, x, y, w, h, u, v, uw, vh, tw, th);
        }

        @Override
        public void blit(int x, int y, int z, int w, int h, TextureAtlasSprite sprite) {
            op("sprite", x, y, x + w, y + h).addProperty("tex", sprite.contents().name().toString());
        }

        @Override
        public void blit(int x, int y, int z, int w, int h, TextureAtlasSprite sprite, float r, float g, float b, float a) {
            op("sprite", x, y, x + w, y + h).addProperty("tex", sprite.contents().name().toString());
        }

        @Override
        public void fill(RenderType type, int x0, int y0, int x1, int y1, int z, int color) {
            op(decorating ? "decoration_fill" : "fill", Math.min(x0, x1), Math.min(y0, y1), Math.max(x0, x1), Math.max(y0, y1))
                    .addProperty("color", String.format("%08X", color));
        }

        @Override
        public void fillGradient(RenderType type, int x0, int y0, int x1, int y1, int top, int bottom, int z) {
            op("gradient", x0, y0, x1, y1).addProperty("color", String.format("%08X/%08X", top, bottom));
        }

        private void item(ItemStack stack, int x, int y) {
            if (!stack.isEmpty()) {
                op("item", x, y, x + 16, y + 16).addProperty("item", stack.getItem().toString());
            }
        }

        @Override
        public void renderItem(ItemStack stack, int x, int y) {
            item(stack, x, y);
        }

        @Override
        public void renderItem(ItemStack stack, int x, int y, int seed) {
            item(stack, x, y);
        }

        @Override
        public void renderItem(ItemStack stack, int x, int y, int seed, int guiOffset) {
            item(stack, x, y);
        }

        @Override
        public void renderFakeItem(ItemStack stack, int x, int y) {
            item(stack, x, y);
        }

        @Override
        public void renderItem(LivingEntity entity, ItemStack stack, int x, int y, int seed) {
            item(stack, x, y);
        }

        @Override
        public void renderItemDecorations(Font font, ItemStack stack, int x, int y, String countText) {
            decorating = true;
            try {
                super.renderItemDecorations(font, stack, x, y, countText);
            } finally {
                decorating = false;
            }
        }

        @Override
        public void enableScissor(int x0, int y0, int x1, int y1) {
            scissors.push(new int[]{x0, y0, x1, y1});
        }

        @Override
        public void disableScissor() {
            if (!scissors.isEmpty()) {
                scissors.pop();
            }
        }
    }
}
