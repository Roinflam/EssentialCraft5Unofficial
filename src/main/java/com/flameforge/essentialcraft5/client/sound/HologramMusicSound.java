package com.flameforge.essentialcraft5.client.sound;

import com.flameforge.essentialcraft5.entity.HologramEntity;
import com.flameforge.essentialcraft5.registry.ECHologram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/**
 * 全息投影的 Boss 战音乐（原作用全局 id 播 {@code records.hologram}）：绑在一只全息投影身上的循环音效，照原版蜜蜂嗡嗡声的写法。
 * <ul>
 *   <li>声音类别 RECORDS（「唱片机 / 音符盒」音量滑块管得到）；实体活着、玩家离它 {@value #RANGE} 格以内就出声，走远了静音、回来接着放，
 *       实体死了（开始死亡动画）或被移出世界就停。</li>
 *   <li>不做距离衰减：曲子是双声道的，原版本来就不给双声道做距离衰减，范围里音量一样（和第七批的恶魔音效一样）。</li>
 * </ul>
 * 原作放完不重放、Boss 被卸载时不清、几只共用一首；这里每只一首，放完自己从头循环。由 {@code client/HologramClient#musicTick} 起播、掉了补上。
 */
public class HologramMusicSound extends AbstractTickableSoundInstance {

    /** 多远以内听得到（和参战者脱战的默认距离一样） */
    public static final double RANGE = 64D;

    private final HologramEntity hologram;

    public HologramMusicSound(HologramEntity hologram) {
        super(ECHologram.RECORD.get(), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.hologram = hologram;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.NONE;
        follow();
    }

    public HologramEntity hologram() {
        return hologram;
    }

    @Override
    public void tick() {
        if (hologram.isRemoved() || !hologram.isAlive()) {
            stop();
            return;
        }
        follow();
    }

    private void follow() {
        x = hologram.getX();
        y = hologram.getY();
        z = hologram.getZ();
        LocalPlayer player = Minecraft.getInstance().player;
        volume = player != null && player.level() == hologram.level() && player.distanceToSqr(hologram) <= RANGE * RANGE ? 1F : 0F;
    }

    /** 走远了是静音，不是停：回到范围里接着放 */
    @Override
    public boolean canStartSilent() {
        return true;
    }
}
