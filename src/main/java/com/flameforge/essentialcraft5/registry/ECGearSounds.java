package com.flameforge.essentialcraft5.registry;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * 装备成长的音效（tools/plan/gear_growth.md 第 1 节）：升阶、战痕升级、各觉醒。定义在 {@code tools/sounds/ecattunement.json}，
 * 先指向原版声音换音调，正式音效等特效第 3 / 5 批。由 {@code ECAttunement.init()} 触发加载。
 */
public final class ECGearSounds {

    public static final RegistryObject<SoundEvent> ASCEND = ECSounds.register("sound.gear.ascend");
    public static final RegistryObject<SoundEvent> SCAR_UP = ECSounds.register("sound.gear.scar_up");
    public static final RegistryObject<SoundEvent> VERDICT_SEAL = ECSounds.register("sound.gear.verdict_seal");
    public static final RegistryObject<SoundEvent> VERDICT_BURST = ECSounds.register("sound.gear.verdict_burst");
    public static final RegistryObject<SoundEvent> ELEMENT_BURST = ECSounds.register("sound.gear.element_burst");
    public static final RegistryObject<SoundEvent> OVERLOAD_READY = ECSounds.register("sound.gear.overload_ready");
    public static final RegistryObject<SoundEvent> OVERLOAD_FIRE = ECSounds.register("sound.gear.overload_fire");
    public static final RegistryObject<SoundEvent> EXECUTE = ECSounds.register("sound.gear.execute");
    public static final RegistryObject<SoundEvent> SURGE = ECSounds.register("sound.gear.surge");
    public static final RegistryObject<SoundEvent> STORM_STEP = ECSounds.register("sound.gear.storm_step");
    public static final RegistryObject<SoundEvent> AFTERIMAGE = ECSounds.register("sound.gear.afterimage");
    public static final RegistryObject<SoundEvent> SENTENCE = ECSounds.register("sound.gear.sentence");
    public static final RegistryObject<SoundEvent> OVERCHARGE_PULSE = ECSounds.register("sound.gear.overcharge_pulse");

    private ECGearSounds() {
    }

    static void init() {
    }

    /** 在 pos 放一声（服务端调，附近的人都听得到；音效还没注册好时什么都不做） */
    public static void play(Level level, Vec3 pos, RegistryObject<SoundEvent> sound, SoundSource source, float volume, float pitch) {
        if (level.isClientSide || !sound.isPresent()) {
            return;
        }
        level.playSound(null, pos.x, pos.y, pos.z, sound.get(), source, volume, pitch);
    }
}
