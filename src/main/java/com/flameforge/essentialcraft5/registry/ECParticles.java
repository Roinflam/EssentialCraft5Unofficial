package com.flameforge.essentialcraft5.registry;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.RegistryObject;

/**
 * 自定义粒子类型的登记入口（第十三批 13c 起）：登记进 {@link ECRegistries#PARTICLE_TYPES}，模块在自己注册类的静态字段里调。
 * <ul>
 *   <li>定义文件 {@code assets/essentialcraft5/particles/<名字>.json}（{@code {"textures": ["essentialcraft5:<贴图>"]}}，
 *       贴图放 {@code textures/particle/}）；</li>
 *   <li>客户端在模块的客户端注册类里处理 {@code RegisterParticleProvidersEvent}：{@code event.registerSpriteSet(类型.get(), XxxParticle.Provider::new)}。</li>
 * </ul>
 * 能用原版粒子（尘埃、末地烛、电火花、带颜色的 {@code DustParticleOptions}）解决的就别加新类型。
 */
public final class ECParticles {

    private ECParticles() {
    }

    /** 由 ECRegistries.register() 调，只为触发类加载 */
    static void init() {
    }

    /**
     * @param overrideLimiter 为真时不受客户端「粒子：减少 / 最少」设置的压制（原版只给少数几种开，一般填 false）
     */
    public static RegistryObject<SimpleParticleType> register(String name, boolean overrideLimiter) {
        return ECRegistries.PARTICLE_TYPES.register(name, () -> new SimpleParticleType(overrideLimiter));
    }
}
