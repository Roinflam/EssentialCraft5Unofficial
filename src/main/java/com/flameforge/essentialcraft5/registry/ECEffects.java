package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.effect.ArmorShredEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * 公共药水效果注册入口（第三批框架 G2），登记进 {@link ECRegistries#MOB_EFFECTS}。
 * <p>
 * 用法（写在模块自己的注册类里）：
 * <pre>
 * // 原作 PotionUnnormalLightness(true, 0xFFFFCC)：第一个参数 isBadEffect，颜色照抄
 * public static final RegistryObject&lt;ParanormalLightnessEffect&gt; PARANORMAL_LIGHTNESS =
 *         ECEffects.register("paranormal_lightness", ParanormalLightnessEffect::new);
 * // 没有自己的逻辑（行为全在事件里）的效果不用写类：
 * public static final RegistryObject&lt;MobEffect&gt; WIND_TOUCH = ECEffects.simple("wind_touch", MobEffectCategory.BENEFICIAL, 0xCCFFCC);
 * </pre>
 * <ul>
 *   <li>原作 Potion 的 isBadEffect → {@link MobEffectCategory#HARMFUL}（true）/ {@link MobEffectCategory#BENEFICIAL}（false）。</li>
 *   <li>图标：{@code assets/essentialcraft5/textures/mob_effect/<注册名>.png}（18×18，原版约定；原作是一张图集，按格子切出来）；
 *       名字键 {@code effect.essentialcraft5.<注册名>} 写进模块的 tools/lang/keys。RegistryFrameworkTests 会查注册了的效果都有图标和中英文名字。</li>
 *   <li>要带属性修饰符（如移速）用 {@code MobEffect#addAttributeModifier}（等级自动乘），不要每 tick 改属性。</li>
 *   <li>每 tick 的逻辑写在 {@code applyEffectTick}，间隔在 {@code isDurationEffectTick}；只在服务端改世界（policy §1.2：药水效果只在没有或快到期时刷新）。</li>
 * </ul>
 * ECMRUCU 的 mru_corruption 仍在 ECMRUCU 里用 RegisterEvent 注册（注册名不变，不迁移）。
 */
public final class ECEffects {

    private ECEffects() {
    }

    /** 由 ECRegistries.register() 调，只为触发类加载 */
    static void init() {
    }

    // ---------------------------------------------------------------- 第十批 10b：四个机制效果（batch10.md §4.2.1）
    // 谁加的都一样生效，事件处理在 effect/EffectEvents；都是负面效果，牛奶和别的模组的净化清得掉，
    // 对状态效果免疫的首领照它自己的设计不吃（MobEffectEvent.Applicable / canBeAffected）。

    /** 腐蚀：回不了血（{@code LivingEntity#heal} 被取消；直接 setHealth 的拦不住） */
    public static final RegistryObject<MobEffect> CORROSION = simple("corrosion", MobEffectCategory.HARMFUL, 0x7FB24A);
    /** 破甲：每级护甲 −2、盔甲韧性 −1（{@link ArmorShredEffect}） */
    public static final RegistryObject<ArmorShredEffect> ARMOR_SHRED = register("armor_shred", ArmorShredEffect::new);
    /** 二分：带着它的生物造成的伤害减半、受到的治疗减半 */
    public static final RegistryObject<MobEffect> BISECTED = simple("bisected", MobEffectCategory.HARMFUL, 0xF040E8);
    /** 锚定：末影人 / 潜影贝的瞬移、末影珍珠、紫颂果都传不走（指令、传送门、别的模组的传送不管） */
    public static final RegistryObject<MobEffect> ANCHORED = simple("anchored", MobEffectCategory.HARMFUL, 0x6E5AB4);

    /**
     * 效果标签 {@code essentialcraft5:unfilterable}（{@code data/essentialcraft5/tags/mob_effect/unfilterable.json}）：
     * 矩阵过滤模块（第十批 10c）不拦的效果——辐射、MRU 腐化、心智悖论、锚定，免得「拦下的效果记成矩阵损伤 → 矩阵损伤又带来这些效果」绕成圈。
     * MRU 腐化、心智悖论现在是矩阵后遗症（corruption/Afflictions），旧的效果只为存档兼容还注册着，留在标签里，免得 {@code /effect} 给的被拦下记账。
     */
    public static final TagKey<MobEffect> UNFILTERABLE = TagKey.create(Registries.MOB_EFFECT, EssentialCraft5.id("unfilterable"));

    public static <E extends MobEffect> RegistryObject<E> register(String name, Supplier<E> effect) {
        return ECRegistries.MOB_EFFECTS.register(name, effect);
    }

    /** 没有自己逻辑的效果（MobEffect 的构造器是 protected，这里给一个空子类） */
    public static RegistryObject<MobEffect> simple(String name, MobEffectCategory category, int color) {
        return register(name, () -> new SimpleEffect(category, color));
    }

    /** {@link #simple} 用的空效果；行为写在模块的事件监听里（查 {@code entity.hasEffect(X.get())}） */
    public static final class SimpleEffect extends MobEffect {

        public SimpleEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }
}
