package com.flameforge.essentialcraft5.client.model.mob.anim;

import com.flameforge.essentialcraft5.client.model.mob.EmberSpiritModel;
import net.minecraft.client.animation.AnimationDefinition;

import static com.flameforge.essentialcraft5.client.model.mob.anim.MobKeyframes.*;

/**
 * 余烬灵的关键帧（13d-N2），时长和实体上的出手节奏对齐（第一颗在 {@code WIND_UP} = 4 刻后吐，精英隔 {@code VOLLEY_GAP} = 6 刻吐第二颗）：
 * <ul>
 *   <li>{@link #CAST}：0.4 秒——两圈碎块往里一收、余烬壳一胀，吐出去以后散回原位；</li>
 *   <li>{@link #VOLLEY}：0.8 秒——精英连吐两颗，胀两下。</li>
 * </ul>
 */
public final class EmberSpiritAnimations {

    public static final AnimationDefinition CAST = clip(0.4F)
            .addAnimation(EmberSpiritModel.SHELL, scaling(scale(0F, 1F, 1F, 1F), scale(0.15F, 1.5F, 1.5F, 1.5F), scale(0.4F, 1F, 1F, 1F)))
            .addAnimation(EmberSpiritModel.UPPER_RING, scaling(scale(0F, 1F, 1F, 1F), scale(0.15F, 0.6F, 1F, 0.6F), scale(0.4F, 1F, 1F, 1F)))
            .addAnimation(EmberSpiritModel.LOWER_RING, scaling(scale(0F, 1F, 1F, 1F), scale(0.15F, 0.6F, 1F, 0.6F), scale(0.4F, 1F, 1F, 1F)))
            .addAnimation(EmberSpiritModel.SPIRIT, position(pos(0F, 0F, 0F, 0F), pos(0.15F, 0F, 0F, 1.5F), pos(0.4F, 0F, 0F, 0F)))
            .build();

    public static final AnimationDefinition VOLLEY = clip(0.8F)
            .addAnimation(EmberSpiritModel.SHELL, scaling(scale(0F, 1F, 1F, 1F), scale(0.15F, 1.5F, 1.5F, 1.5F), scale(0.3F, 1.1F, 1.1F, 1.1F),
                    scale(0.45F, 1.5F, 1.5F, 1.5F), scale(0.8F, 1F, 1F, 1F)))
            .addAnimation(EmberSpiritModel.UPPER_RING, scaling(scale(0F, 1F, 1F, 1F), scale(0.15F, 0.6F, 1F, 0.6F), scale(0.45F, 0.6F, 1F, 0.6F),
                    scale(0.8F, 1F, 1F, 1F)))
            .addAnimation(EmberSpiritModel.LOWER_RING, scaling(scale(0F, 1F, 1F, 1F), scale(0.15F, 0.6F, 1F, 0.6F), scale(0.45F, 0.6F, 1F, 0.6F),
                    scale(0.8F, 1F, 1F, 1F)))
            .addAnimation(EmberSpiritModel.UPPER_RING, rotation(rot(0F, 0F, 0F, 0F), rot(0.8F, 0F, 360F, 0F, true)))
            .build();

    private EmberSpiritAnimations() {
    }
}
