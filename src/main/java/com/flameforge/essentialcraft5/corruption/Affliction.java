package com.flameforge.essentialcraft5.corruption;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECCorruption;
import com.flameforge.essentialcraft5.registry.ECMRUCU;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * 矩阵后遗症的种类（{@link Afflictions}）。腐化效果发作、机器泄漏留在人身上的那几样「状态」，不做成药水效果：
 * 牛奶、不死图腾、各种净化道具和「免疫负面效果」的饰品清的都是药水效果，矩阵本身的伤清不掉，只能等它自己过去，死一次才会散。
 * <p>
 * 存在玩家数据里（键 {@code afflictions}，id 就是下面的 {@link #id}），只往末尾加、id 不改（存档按 id 读，认不出的跳过）。
 * 名字键 {@code affliction.essentialcraft5.<id>}、说明 {@code affliction.essentialcraft5.<id>.desc}。
 * 图标借同一个意思的药水效果的图（只拿图，不挂效果）。
 */
public enum Affliction {

    /** 腐化 #0「双手迟钝」：挖得慢、挥得慢（等同挖掘疲劳；原作这里是药水效果） */
    HEAVY_HANDS("heavy_hands", () -> MobEffects.DIG_SLOWDOWN, true),
    /** 腐化 #1「双手无力」：近战伤害打折（原作是虚弱） */
    WEAK_GRIP("weak_grip", () -> MobEffects.WEAKNESS, true),
    /** 腐化 #3「双腿迟缓」：走得慢（等同缓慢） */
    LEADEN_LEGS("leaden_legs", () -> MobEffects.MOVEMENT_SLOWDOWN, true),
    /** 腐化 #5「宇宙崩裂」给的心智悖论：客户端的耳鸣、心跳、滤镜和四种发作（{@code client/CorruptionClient}）。照原作不显示剩余时间 */
    PARADOX("paradox", ECCorruption.MINDFOLD_PARADOX, false),
    /** 守在漏 MRU 的机器旁边叠上来的 MRU 腐化：每秒按等级掷骰扣一下血（{@link MRUCorruption}） */
    MRU_CORRUPTION("mru_corruption", ECMRUCU.MRU_CORRUPTION, true);

    private static final Affliction[] VALUES = values();

    private final String id;
    private final Supplier<? extends MobEffect> icon;
    private final boolean showsTime;

    Affliction(String id, Supplier<? extends MobEffect> icon, boolean showsTime) {
        this.id = id;
        this.icon = icon;
        this.showsTime = showsTime;
    }

    /** 存档里的 id */
    public String id() {
        return id;
    }

    /** 图标借用的药水效果（只取它在效果图集里的图） */
    public MobEffect icon() {
        return icon.get();
    }

    /** 界面上写不写剩余时间（心智悖论照原作不写） */
    public boolean showsTime() {
        return showsTime;
    }

    public String nameKey() {
        return "affliction." + EssentialCraft5.MODID + "." + id;
    }

    public String descKey() {
        return nameKey() + ".desc";
    }

    @Nullable
    public static Affliction byId(String id) {
        for (Affliction affliction : VALUES) {
            if (affliction.id.equals(id)) {
                return affliction;
            }
        }
        return null;
    }

    /** 全部种类（共享的数组，别改） */
    public static Affliction[] all() {
        return VALUES;
    }
}
