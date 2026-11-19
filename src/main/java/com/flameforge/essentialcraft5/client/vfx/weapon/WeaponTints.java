package com.flameforge.essentialcraft5.client.vfx.weapon;

import com.flameforge.essentialcraft5.client.vfx.VfxPalette;
import com.flameforge.essentialcraft5.client.vfx.VfxPlasma;
import com.flameforge.essentialcraft5.item.attunement.AttunementFamily;
import com.flameforge.essentialcraft5.item.attunement.AttunementLook;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData;
import net.minecraft.util.Mth;

/**
 * 武器特效的配色。透镜五色取自枪上透镜那一格的贴图（textures/item/guns/&lt;枪&gt;/lense/*.png）：
 * 混沌 #E53624、冰冻 #1C28DA、魔力 #DD0DFF、暗影 #B3B3B3；没装透镜的中性光束用模组本色。
 */
public final class WeaponTints {

    /** 混沌：贴图的橙红，比首领的危险红偏橙，两种红放一起分得开 */
    public static final VfxPlasma.Tint CHAOS = new VfxPlasma.Tint(0xFFF0E2, 0xFF9C74, 0xF0462A, 0x7A1A10);
    /** 冰冻：贴图是纯蓝，往冷白里调；外晕压到靛蓝，和 MRU 的蓝紫外晕不撞 */
    public static final VfxPlasma.Tint FROZEN = new VfxPlasma.Tint(0xEEF4FF, 0x9EB4FF, 0x3E58FF, 0x18208A);
    /** 魔力：贴图的亮紫，比 MRU 本色的品红偏蓝 */
    public static final VfxPlasma.Tint MAGIC = new VfxPlasma.Tint(0xFFF0FF, 0xEFA2FF, 0xC83CFF, 0x50108A);
    /**
     * 暗影：贴图是灰，加色混合下灰会发白，所以芯和细丝留浅灰、辉光和外晕压成暗紫灰（矩阵投影贴图的暗紫），
     * 远看是一道发暗的光，不按通用算法提亮成白光
     */
    public static final VfxPlasma.Tint SHADE = new VfxPlasma.Tint(0xE8E2EE, 0xA898B4, 0x6A4E7E, 0x24162E);

    private static final VfxPlasma.Tint[] LENS = {VfxPlasma.MRU, CHAOS, FROZEN, MAGIC, SHADE};

    /** 余烬剑四元素 + 复合：火取剑贴图的余烬橙，水 MRU 蓝紫，地深紫往亮里提，风取风之剑的青绿，复合是模组本色 */
    private static final VfxPlasma.Tint[] ELEMENT = {
            new VfxPlasma.Tint(0xFFF2E4, 0xFFB088, VfxPalette.EMBER, 0x7A2410),
            VfxPlasma.tint(VfxPalette.ORB_BLUE),
            new VfxPlasma.Tint(0xF6EAFF, 0xC8A0FF, 0x8A4CE0, 0x2E1060),
            new VfxPlasma.Tint(0xF0FFF2, 0xB8F0C4, 0x68CC74, 0x1E4A2A),
            VfxPlasma.MRU};

    /** 风之剑（贴图主色是青绿，非电系：少冒电，用拖尾和光尘） */
    public static final VfxPlasma.Tint WIND = ELEMENT[3];

    private WeaponTints() {
    }

    /** 透镜 balance（0 中性 1 混沌 2 冰冻 3 魔力 4 暗影） */
    public static VfxPlasma.Tint lens(int balance) {
        return LENS[Mth.clamp(balance, 0, LENS.length - 1)];
    }

    public static VfxPlasma.Tint element(ForgedSwordData.Element element) {
        return ELEMENT[Math.min(element.ordinal(), ELEMENT.length - 1)];
    }

    /** 元素爆发包里的元素序号（0 火 1 水 2 地 3 风） */
    public static VfxPlasma.Tint element(int index) {
        return ELEMENT[Mth.clamp(index, 0, ELEMENT.length - 1)];
    }

    /** 同调家族的颜色（满阶前一阶，比贴图主色亮，加色下看得见） */
    public static VfxPlasma.Tint family(AttunementFamily family) {
        int[] stages = AttunementLook.palette(family).stages();
        return VfxPlasma.tint(stages[stages.length - 1]);
    }
}
