package com.flameforge.essentialcraft5.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 法术粒子（原作 ParticleCSpell / {@code "cSpellFX"}，只在客户端用）：一团黑色光晕包着一个白色小芯，在 40~49 刻里从起点匀速挪到「起点 + 位移」，
 * 贴图从原版 generic_7 缩到 generic_0（同原版烟），越到后面越亮。魔力传送器、魔力熔炉成形时冒（位移 (0, 2, 0)：往上飘两格），
 * 第七批的余烬锻炉（原作 {@code TileEmberForge} L350 同一种粒子）直接用这里。
 * <p>
 * 不注册粒子类型：直接交给 {@code ParticleEngine}，贴图从粒子图集按名字取（原版的 generic_0~7，资源包换了也跟着换）。
 * 方块实体在 {@code clientTick} 里调，签名里没有客户端专用的类型，专用服加载方块实体类时不会碰到这里。
 * 粒子设置为「最少」时不放；放之前用 {@link #nearCamera} 判断离镜头够不够近。
 */
public final class SpellFx {

    /** 光晕大小（原作 particleScale 1 → 0.1 格） */
    private static final float OUTER_SIZE = 0.1F;
    /** 白芯大小（原作 0.4 → 0.04 格） */
    private static final float INNER_SIZE = 0.04F;

    private SpellFx() {
    }

    /** 在 (x, y, z) 放一团，寿命内匀速挪 (dx, dy, dz)。只在客户端世界有效，服务端调用什么都不做 */
    public static void spawn(Level level, double x, double y, double z, double dx, double dy, double dz) {
        if (!level.isClientSide || !(level instanceof ClientLevel clientLevel)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != clientLevel || minecraft.options.particles().get() == ParticleStatus.MINIMAL) {
            return;
        }
        TextureAtlasSprite first = sprite(7);
        if (first == null) {
            return;
        }
        minecraft.particleEngine.add(new Spell(clientLevel, x, y, z, dx, dy, dz));
    }

    /** 镜头离 (x, y, z) 是否不超过 range 格（镜头还没就位时为 false） */
    public static boolean nearCamera(double x, double y, double z, double range) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        return camera.isInitialized() && camera.getPosition().distanceToSqr(x, y, z) <= range * range;
    }

    /** 粒子图集里的原版 generic_i（0~7）；图集还没建好时 null */
    @Nullable
    private static TextureAtlasSprite sprite(int index) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
        if (!(texture instanceof TextureAtlas atlas)) {
            return null;
        }
        return atlas.getSprite(ResourceLocation.withDefaultNamespace("generic_" + Math.max(0, Math.min(7, index))));
    }

    private static final class Spell extends TextureSheetParticle {

        private final double startX;
        private final double startY;
        private final double startZ;
        private final double moveX;
        private final double moveY;
        private final double moveZ;

        Spell(ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            super(level, x, y, z);
            startX = x;
            startY = y;
            startZ = z;
            moveX = dx;
            moveY = dy;
            moveZ = dz;
            xd = 0D;
            yd = 0D;
            zd = 0D;
            gravity = 0F;
            hasPhysics = false;
            alpha = 0.99F;
            lifetime = 40 + random.nextInt(10);
            quadSize = OUTER_SIZE;
            TextureAtlasSprite first = sprite(7);
            if (first != null) {
                setSprite(first);
            }
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) {
                remove();
                return;
            }
            float f = (float) age / lifetime;
            setPos(startX + moveX * f, startY + moveY * f, startZ + moveZ * f);
            TextureAtlasSprite frame = sprite(7 - age * 8 / lifetime);
            if (frame != null) {
                setSprite(frame);
            }
        }

        /** 先画大一点的黑色光晕，再画白色小芯（原作 renderParticle 画两遍） */
        @Override
        public void render(VertexConsumer buffer, Camera camera, float partialTick) {
            setColor(0F, 0F, 0F);
            quadSize = OUTER_SIZE;
            super.render(buffer, camera, partialTick);
            setColor(1F, 1F, 1F);
            quadSize = INNER_SIZE;
            super.render(buffer, camera, partialTick);
            quadSize = OUTER_SIZE;
        }

        /** 越到后面越亮：天空光那一半加上 (age / lifetime)^4 × 240（原作 getBrightnessForRender） */
        @Override
        protected int getLightColor(float partialTick) {
            int packed = super.getLightColor(partialTick);
            float f = (float) age / lifetime;
            f *= f;
            f *= f;
            int block = packed & 255;
            int sky = Math.min(240, (packed >> 16 & 255) + (int) (f * 15F * 16F));
            return block | sky << 16;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }
}
