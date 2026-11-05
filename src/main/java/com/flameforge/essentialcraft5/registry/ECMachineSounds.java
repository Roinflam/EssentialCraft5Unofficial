package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.ambience.MachineAmbienceConfig;
import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * 机器工作时的声音。定义在 {@code tools/sounds/machines.json}，音频由 {@code tools/gen/machine_sounds.py} 合成
 * （{@code assets/essentialcraft5/sounds/machine/*.ogg}），怎么在机器上用见 {@link com.flameforge.essentialcraft5.ambience.MachineAmbiences}。
 * <ul>
 *   <li>环形底噪（每 20 刻重播一次，片段 1.1 秒，首尾叠 0.1 秒）：{@link #REACTOR_HUM}、{@link #RADIATION_HUM}、{@link #EARTH_RUMBLE}、
 *       {@link #ELECTRIC_HUM}、{@link #ARCANE_SHIMMER}、{@link #ENCHANT_SWIRL}、{@link #FIRE_ROAR}、{@link #MAGMA_BUBBLE}、{@link #FROST_HISS}、
 *       {@link #VOID_DRAW}、{@link #CRYSTAL_RESONANCE}。这些片段的音高不能变，变了接缝就对不上。</li>
 *   <li>点缀音（随机时刻、可以变调）：{@link #GEIGER}、{@link #REACTOR_VENT}、{@link #ARCANE_CHIME}、{@link #CRYSTAL_TINK}、{@link #SPARK_CRACKLE}。</li>
 *   <li>事件音（服务端在完成时播给周围的人）：{@link #ENCHANT_DONE}、{@link #ASCEND_DONE}。</li>
 * </ul>
 */
public final class ECMachineSounds {

    public static final RegistryObject<SoundEvent> REACTOR_HUM = ECSounds.register("machine.reactor_hum");
    public static final RegistryObject<SoundEvent> RADIATION_HUM = ECSounds.register("machine.radiation_hum");
    public static final RegistryObject<SoundEvent> EARTH_RUMBLE = ECSounds.register("machine.earth_rumble");
    public static final RegistryObject<SoundEvent> ELECTRIC_HUM = ECSounds.register("machine.electric_hum");
    public static final RegistryObject<SoundEvent> ARCANE_SHIMMER = ECSounds.register("machine.arcane_shimmer");
    public static final RegistryObject<SoundEvent> ENCHANT_SWIRL = ECSounds.register("machine.enchant_swirl");
    public static final RegistryObject<SoundEvent> FIRE_ROAR = ECSounds.register("machine.fire_roar");
    public static final RegistryObject<SoundEvent> MAGMA_BUBBLE = ECSounds.register("machine.magma_bubble");
    public static final RegistryObject<SoundEvent> FROST_HISS = ECSounds.register("machine.frost_hiss");
    public static final RegistryObject<SoundEvent> VOID_DRAW = ECSounds.register("machine.void_draw");
    public static final RegistryObject<SoundEvent> CRYSTAL_RESONANCE = ECSounds.register("machine.crystal_resonance");

    public static final RegistryObject<SoundEvent> GEIGER = ECSounds.register("machine.geiger");
    public static final RegistryObject<SoundEvent> REACTOR_VENT = ECSounds.register("machine.reactor_vent");
    public static final RegistryObject<SoundEvent> ARCANE_CHIME = ECSounds.register("machine.arcane_chime");
    public static final RegistryObject<SoundEvent> CRYSTAL_TINK = ECSounds.register("machine.crystal_tink");
    public static final RegistryObject<SoundEvent> SPARK_CRACKLE = ECSounds.register("machine.spark_crackle");

    public static final RegistryObject<SoundEvent> ENCHANT_DONE = ECSounds.register("machine.enchant_done");
    public static final RegistryObject<SoundEvent> ASCEND_DONE = ECSounds.register("machine.ascend_done");

    private ECMachineSounds() {
    }

    /** 由 ECRegistries.register() 调：触发类加载，并登记个人开关（CLIENT 配置 [machine_ambience]） */
    static void init() {
        ECConfig.client(MachineAmbienceConfig::define);
    }
}
