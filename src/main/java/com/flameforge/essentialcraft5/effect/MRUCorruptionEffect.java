package com.flameforge.essentialcraft5.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 旧的「MRU 腐化」药水效果（原作 PotionMRUCorruption，颜色 0xFF00FF，图标取原作 textures/special/potions.png 的 (3, 1) 格，
 * 导入到 textures/mob_effect/mru_corruption.png）。
 * <p>
 * MRU 腐化现在是矩阵后遗症（{@code corruption/MRUCorruption}、{@code corruption/Afflictions}）：做成药水效果的话牛奶一喝就没了。
 * 这个效果只为存档兼容还注册着（删掉的话 Forge 读档会弹「缺少注册项」），自己什么都不做；身上挂着的（旧存档、{@code /effect}、别的模组加的）
 * 下一 tick 就折算成后遗症并去掉。图标和名字照旧，后遗症的 HUD 借它的图。
 */
public class MRUCorruptionEffect extends MobEffect {

    public static final int COLOR = 0xFF00FF;

    public MRUCorruptionEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false;
    }
}
