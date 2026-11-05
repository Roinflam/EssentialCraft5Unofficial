package com.flameforge.essentialcraft5.util;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.bauble.BaubleEffects;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 伤害分类：这次伤害算不算「物理伤害」（第七批框架 7-F；虚空套装有电时只挡物理伤害，batch7 D1）。
 *
 * <h2>规则（命中一条即返回）</h2>
 * <ol>
 *   <li>带 {@code minecraft:bypasses_invulnerability} 的（{@code /kill}、掉出世界）不算。</li>
 *   <li>伤害类型在 {@code essentialcraft5:not_physical} 里 → 不算；在 {@code essentialcraft5:physical} 里 → 算（两个标签默认空，
 *       给服主和整合包按别的模组的伤害类型调整用，黑名单优先）。</li>
 *   <li>魔法伤害不算（{@link #isMagic}）：message_id 含 {@code magic}、在原版 {@code minecraft:witch_resistant_to} 里
 *       （魔法、间接魔法、音波、荆棘），在社区约定的 {@code forge:is_magic} 里（不少法术模组往里挂），
 *       或伤害类型的命名空间在服务端配置 {@code general.damage_kinds.magic_namespaces} 里（{@link Config}）。
 *       例外：召唤物 / 宠物替主人挥砍（直接实体是活物、攻击者是另一个实体）按近战算，不按魔法；
 *       伤害类型在 {@code general.damage_kinds.magic_exceptions} 里的一律不按魔法算（接着往下按元素 / 近战 / 弹射物判）。</li>
 *   <li>元素伤害不算：带 {@code is_fire}、{@code is_explosion}、{@code is_lightning}、{@code is_freezing} 的（火、爆炸、闪电、冰冻）。</li>
 *   <li>剩下的，有东西打过来才算：近战（直接实体是活物）、弹射物（带 {@code is_projectile}，或直接实体是弹射物，含箭、三叉戟、凋灵之首、潜影贝导弹）。
 *       摔落、溺水、窒息、仙人掌、饥饿、凋零、下落的铁砧这类没有攻击者的都不算。</li>
 * </ol>
 * 分类按伤害来源分「箭 / 弹射物 / 魔法 / 近战 / 其它」（前两类和近战是物理，魔法和其它不是），
 * 魔法判定是原版规则 + {@code forge:is_magic} + 按命名空间整体算魔法，召唤物挥砍排除在魔法之外。
 * 命名空间规则是为法术模组准备的：它们给每种法术各定义一个伤害类型，message_id 大多不带 magic，也不全挂 {@code forge:is_magic}
 * （例：有的法术伤害直接实体是施法者本人，不加这条就落进「近战」）。默认列表是核对过伤害源结构的 5 个法术模组，
 * 例外表 5 条（召唤物攻击、镰刀斩击、战利品爆炸、遣散这类挂在法术模组命名空间下、其实是物理的），只是配置数据，那些模组没装时不起作用。
 * 精确算魔法的伤害类型由伤害类型标签管（{@code forge:is_magic}、{@code essentialcraft5:not_physical}，数据包可改）。
 * 只看直接实体的话，苦力怕自爆（直接实体是苦力怕）会落进「近战」；这里按元素表（火、冰、电、爆炸都是元素，
 * 物理只有切割、穿刺、冲击）把这四类伤害排除。
 * <p>
 * 本模组自己的伤害类型按这套规则：恶魔清怪 {@code demon}（直接实体是恶魔）算物理；枪械射线 {@code mru_ray}（没有直接实体、不是弹射物）、
 * 线圈电弧 {@code mru_discharge}、余烬反噬 {@code ember_backlash}（没有攻击者）都不算；灵魂刻刀 {@code soul_capture} 是近战（直接实体是玩家），
 * 但它带 {@code bypasses_armor}，挡伤害的东西本来就不该管它（虚空套装另外跳过 {@code bypasses_armor}，照原作 ISpecialArmor 不处理无视护甲的伤害）。
 * 两端都能调（只看伤害源、标签和 SERVER 配置；SERVER 配置进服时同步给客户端）。
 */
public final class ECDamageKinds {

    /**
     * 社区约定的魔法伤害标签 {@code forge:is_magic}：Forge 47 没有这个常量，用到的模组各自 {@code TagKey.create}。
     * 数据包里不存在时 {@link DamageSource#is(TagKey)} 只会返回 false。
     */
    public static final TagKey<DamageType> FORGE_IS_MAGIC =
            TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("forge", "is_magic"));

    private ECDamageKinds() {
    }

    /**
     * 服务端配置 {@code general.damage_kinds}：按命名空间算魔法的模组 id、不算魔法的例外伤害类型。
     * 框架自己的小节，由 {@code ECConfig.register} 登记。
     * 配置没加载时两张表都当空的，只剩原版规则。GameTest 用 {@link #setTestOverrides}，不碰真配置。
     */
    public static final class Config {

        /** 默认的魔法命名空间（核对过伤害源结构的 5 个法术模组） */
        public static final List<String> DEFAULT_MAGIC_NAMESPACES =
                List.of("irons_spellbooks", "gtbcs_geomancy_plus", "goety", "ars_nouveau", "sweetmagic");
        /** 默认的例外（召唤物攻击、镰刀 / 末影刃斩击、战利品爆炸、遣散：挂在法术模组命名空间下，其实是物理） */
        public static final List<String> DEFAULT_MAGIC_EXCEPTIONS =
                List.of("goety:summon", "goety:sword", "goety:loot_explode", "goety:loot_explode_owned", "goety:dismissed");

        @Nullable
        private static ForgeConfigSpec.ConfigValue<List<? extends String>> magicNamespaces;
        @Nullable
        private static ForgeConfigSpec.ConfigValue<List<? extends String>> magicExceptions;

        /** 解析好的两张表；配置值的列表对象换了（重载）才重新解析 */
        record Parsed(List<? extends String> namespaceSource, List<? extends String> exceptionSource,
                      Set<String> namespaces, Set<ResourceLocation> exceptions) {
        }

        private static final Parsed EMPTY = new Parsed(List.of(), List.of(), Set.of(), Set.of());
        @Nullable
        private static volatile Parsed parsed;
        @Nullable
        private static volatile Parsed testOverride;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Damage classification: which damage counts as magic (magic is not physical; Void Armor only blocks physical damage).",
                    "伤害分类：哪些伤害算魔法（魔法不算物理伤害；虚空套装只挡物理伤害）").push("damage_kinds");
            magicNamespaces = builder.comment(
                            "Every damage type whose id is in one of these namespaces counts as magic (spell mods define one damage type per spell,",
                            "most without 'magic' in the message id). Mods listed here but not installed have no effect.",
                            "伤害类型 id 的命名空间在这里的一律算魔法（法术模组每种法术各定义一个伤害类型，message_id 大多不带 magic）。没装的模组写在这里没有影响")
                    .defineListAllowEmpty("magic_namespaces", DEFAULT_MAGIC_NAMESPACES, o -> o instanceof String s && !s.isBlank());
            magicExceptions = builder.comment(
                            "Damage type ids that never count as magic, even inside a namespace above (e.g. a summon's melee swing or a scythe slash).",
                            "They fall through to the other rules (fire / explosion / melee / projectile).",
                            "这些伤害类型 id 一律不算魔法，在上面的命名空间里也不算（例：召唤物挥砍、镰刀斩击），接着按火 / 爆炸 / 近战 / 弹射物判")
                    .defineListAllowEmpty("magic_exceptions", DEFAULT_MAGIC_EXCEPTIONS, o -> o instanceof String s && ResourceLocation.tryParse(s) != null);
            builder.pop();
        }

        /** 现在生效的两张表：测试覆盖值 → 配置（没加载时空表） */
        static Parsed current() {
            Parsed override = testOverride;
            if (override != null) {
                return override;
            }
            ForgeConfigSpec spec = ECConfig.spec();
            ForgeConfigSpec.ConfigValue<List<? extends String>> namespaceValue = magicNamespaces;
            ForgeConfigSpec.ConfigValue<List<? extends String>> exceptionValue = magicExceptions;
            if (namespaceValue == null || exceptionValue == null || spec == null || !spec.isLoaded()) {
                return EMPTY;
            }
            List<? extends String> namespaces = namespaceValue.get();
            List<? extends String> exceptions = exceptionValue.get();
            Parsed known = parsed;
            if (known != null && known.namespaceSource() == namespaces && known.exceptionSource() == exceptions) {
                return known;
            }
            Parsed fresh = parse(namespaces, exceptions);
            parsed = fresh;
            return fresh;
        }

        private static Parsed parse(List<? extends String> namespaces, List<? extends String> exceptions) {
            Set<String> ns = new HashSet<>();
            for (String s : namespaces) {
                if (s != null && !s.isBlank()) {
                    ns.add(s.trim().toLowerCase(Locale.ROOT));
                }
            }
            Set<ResourceLocation> ex = new HashSet<>();
            for (String s : exceptions) {
                ResourceLocation id = s == null ? null : ResourceLocation.tryParse(s.trim());
                if (id != null) {
                    ex.add(id);
                }
            }
            return new Parsed(namespaces, exceptions, Set.copyOf(ns), Set.copyOf(ex));
        }

        /**
         * GameTest 用：整套换成给定的两张表（两个都传 null 回到配置；只传一个时另一个当空表）。同一段同步代码里设、测、置回。
         * 不用 {@code ConfigValue.set}：服务端配置会自动存盘，文件监视器随后重读整份文件，可能把测试刚设的值冲掉。
         */
        public static void setTestOverrides(@Nullable List<String> namespaces, @Nullable List<String> exceptions) {
            testOverride = namespaces == null && exceptions == null ? null
                    : parse(namespaces == null ? List.of() : namespaces, exceptions == null ? List.of() : exceptions);
        }
    }

    /** 这次伤害算不算物理伤害（规则见类注释） */
    public static boolean isPhysical(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        if (source.is(ECDamageTypes.NOT_PHYSICAL)) {
            return false;
        }
        if (source.is(ECDamageTypes.PHYSICAL)) {
            return true;
        }
        if (isMagic(source) || isElemental(source)) {
            return false;
        }
        return isMelee(source) || isProjectile(source);
    }

    /**
     * 魔法伤害：message_id 含 magic、在 {@code minecraft:witch_resistant_to} 或 {@code forge:is_magic} 里，或命名空间在配置的魔法命名空间里；
     * 召唤物 / 宠物替主人挥砍、配置里的例外不算（见类注释第 3 条）。判断顺序：召唤物挥砍 → 例外 → 原版规则与标签 → 命名空间
     */
    public static boolean isMagic(DamageSource source) {
        if (isMinionMelee(source)) {
            return false;
        }
        Config.Parsed config = Config.current();
        ResourceLocation id = typeId(source);
        if (id != null && config.exceptions().contains(id)) {
            return false;
        }
        if (source.getMsgId().toLowerCase(Locale.ROOT).contains("magic")
                || source.is(DamageTypeTags.WITCH_RESISTANT_TO)
                || source.is(FORGE_IS_MAGIC)) {
            return true;
        }
        return id != null && config.namespaces().contains(id.getNamespace());
    }

    /**
     * 只按伤害类型 id 查配置的两张表：在例外里 → false；命名空间在魔法命名空间里 → true；都不在 → false（不看原版规则和标签）。
     * 给 GameTest 核对默认表用：没装的模组的伤害类型造不出伤害源，只能按 id 问
     */
    public static boolean isMagicById(ResourceLocation id) {
        Config.Parsed config = Config.current();
        return !config.exceptions().contains(id) && config.namespaces().contains(id.getNamespace());
    }

    @Nullable
    private static ResourceLocation typeId(DamageSource source) {
        Optional<ResourceKey<DamageType>> key = source.typeHolder().unwrapKey();
        return key.map(ResourceKey::location).orElse(null);
    }

    /** 元素伤害：火、爆炸、闪电、冰冻 */
    public static boolean isElemental(DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.IS_LIGHTNING) || source.is(DamageTypeTags.IS_FREEZING);
    }

    /** 近战：直接实体是活物（玩家、怪物本身，或替主人挥砍的召唤物） */
    public static boolean isMelee(DamageSource source) {
        return source.getDirectEntity() instanceof LivingEntity;
    }

    /** 弹射物：带 {@code is_projectile}，或直接实体是弹射物 */
    public static boolean isProjectile(DamageSource source) {
        return source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() instanceof Projectile;
    }

    /**
     * 反伤：挨打以后自动打回去的伤害，不是伤害记在谁头上的那个人自己动的手（第十批 10c）。三种都算：
     * 原版荆棘类型（荆棘附魔，宅男全套的反伤也用它）、伤害类型在 {@code essentialcraft5:reflected} 里的（别的模组的反伤，默认空），
     * 以及本模组饰品反伤作用域里打出的（σ 的反弹和普通近战同一个类型，只能靠作用域认，{@code BaubleEffects.asReflectDamage}）。
     * 交手记录（{@code ECCombatTags}）不把它当成「对方动过手」，削减模块不给它叠破甲。只在伤害事件发生的当时问（作用域是同步的）。
     */
    public static boolean isReflected(DamageSource source) {
        return source.is(DamageTypes.THORNS) || source.is(ECDamageTypes.REFLECTED) || BaubleEffects.isReflectDamage();
    }

    /** 直接实体是活物、攻击者是另一个实体：召唤物 / 宠物替主人挥砍 */
    private static boolean isMinionMelee(DamageSource source) {
        Entity direct = source.getDirectEntity();
        Entity owner = source.getEntity();
        return direct instanceof LivingEntity && owner != null && owner != direct;
    }
}
