package com.flameforge.essentialcraft5.client.render.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.resources.ResourceLocation;

/**
 * 新怪物贴图的路径约定（第十三批 13c-Fb，batch13.md §10「贴图」）：每只怪一个目录 {@code textures/entity/<id>/}，
 * 主贴图 {@code <id>.png}，派生图由 {@code tools/gen/mob_textures.py} 从主贴图生成：
 * <ul>
 *   <li>{@code <id>_glow.png}：发光层（只留会发光的像素，其余全透明），{@link MobGlowLayer} 用；</li>
 *   <li>{@code <id>_elite.png}：精英层（叠在主贴图上的纹路，其余全透明），{@link MobEliteLayer} 用；</li>
 *   <li>{@code <id>_<名字>.png}：换色、换皮的整张派生（{@link #variant}），渲染器按实体状态挑。</li>
 * </ul>
 * 老的实体（恶魔、全息投影等）贴图平放在 {@code textures/entity/} 下，不走这套约定，也不改。
 */
public final class MobTextures {

    private MobTextures() {
    }

    /** 主贴图 {@code textures/entity/<id>/<id>.png} */
    public static ResourceLocation base(String id) {
        return path(id, id);
    }

    /** 发光层 {@code textures/entity/<id>/<id>_glow.png} */
    public static ResourceLocation glow(String id) {
        return path(id, id + "_glow");
    }

    /** 精英层 {@code textures/entity/<id>/<id>_elite.png} */
    public static ResourceLocation elite(String id) {
        return path(id, id + "_elite");
    }

    /** 整张派生 {@code textures/entity/<id>/<id>_<name>.png} */
    public static ResourceLocation variant(String id, String name) {
        return path(id, id + "_" + name);
    }

    private static ResourceLocation path(String id, String file) {
        return EssentialCraft5.id("textures/entity/" + id + "/" + file + ".png");
    }
}
