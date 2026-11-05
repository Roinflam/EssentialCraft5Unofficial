package com.flameforge.essentialcraft5.effect;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 旧的「心智悖论」药水效果（原作 PotionMindfoldParadox，62 行；图标取原作 textures/special/potions.png 的 (7, 2) 格）。
 * <p>
 * 心智悖论现在是矩阵后遗症（{@code corruption/Afflictions}，腐化效果 mind_5「宇宙崩裂」发作时给 100 秒）：做成药水效果的话牛奶一喝就没了。
 * 这个效果只为存档兼容还注册着，自己什么都不做；身上挂着的下一 tick 就折算成后遗症并去掉。
 * 特效全在客户端 {@code client/CorruptionClient}（耳鸣、心跳、开头 20 秒的「油画」滤镜、四选一的发作）。
 */
public class MindfoldParadoxEffect extends MobEffect {

    /** 原作 new PotionMindfoldParadox(true, 0xFFFFFF) */
    public static final int COLOR = 0xFFFFFF;
    /** 名字下面那一行（原作 potion.paradox.txt「世界正在碎裂」）；后遗症的提示框用它代替剩余时间 */
    public static final String SUBTITLE_KEY = "effect." + EssentialCraft5.MODID + ".mindfold_paradox.subtitle";

    public MindfoldParadoxEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false;
    }
}
