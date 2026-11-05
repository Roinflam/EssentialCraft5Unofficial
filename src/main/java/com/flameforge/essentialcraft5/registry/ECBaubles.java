package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.hoanna.HoannaHazardsConfig;
import com.flameforge.essentialcraft5.item.bauble.BaubleEffects;
import com.flameforge.essentialcraft5.item.bauble.PortableMatrixDestructorItem;
import com.flameforge.essentialcraft5.item.bauble.SpecialBaubleItem;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * 特殊饰品模块（第三批 §3.8，海纳尔批 6a 补齐）：32 件 Curios 饰品 + 原版地牢箱子注入（{@code data/essentialcraft5/loot_tables/inject/simple_dungeon.json}）。
 * <p>
 * 原作是一个带 32 个 meta 的 {@code ItemBaublesSpecial}（baublesCore），这里拆成 32 个物品。第三批先做了 27 件；δ 指环、ε 腰带、λ 指环、μ 指环、ξ 腰带
 * 依赖海纳尔（辐射、余烬），6a（batch6a §6.2 第 6 条）注册：物品本身只挂效果标签，效果由海纳尔模块按标签读（δ λ μ ξ → ECHoannaHazards，ε → ECVoid）。
 * 新旧名（总表见 tools/id_map_items.json）：baublesCore:0 portableMD → portable_matrix_destructor；1~3 ringOfStability / Experience / Resistance →
 * ring_of_stability / experience / resistance；4 lifePendant → life_pendant；5 windCatcher → wind_catcher；6 windMagesTalisman → wind_mages_talisman；
 * 7 doctorsBelt → doctors_belt；8~10 alpha / beta / gamma → alpha_ring …；11 deltaRing → delta_ring；12 epsilonBelt → epsilon_belt；
 * 13 dzetaAmulet → zeta_amulet（拼写修正）；14 eta_amulet；15 theta_belt；16 iota_ring；17 kappa_ring；18 lambdaRing → lambda_ring；19 muRing → mu_ring；
 * 20 nu_amulet；21 xiBelt → xi_belt；22 omicron_amulet；23 pi_belt；24~31 rho / sigma / tau / upsilon / phi / chi / psi / omega_ring。
 * <p>
 * 槽位照原作 {@code btALST}（AMULET → 项链）：项链 8、腰带 5、戒指 19，槽位数量用 Curios / 整合包的默认值。
 * 效果分三处写：
 * <ul>
 *   <li>物品自己（{@link SpecialBaubleItem}）：击杀 UBMRU 修正（{@code IUBMRUGainModifyHandler}）、风之好感系数（{@code IWindAffinityItem}）、
 *       戴着时的周期效果（便携矩阵换能器、κ、医师腰带），实现在 {@link BaubleEffects}。</li>
 *   <li>事件（{@code item/bauble/BaubleCombatEvents}、{@code BaubleHarvestEvents}）：ζ η θ ι σ ψ υ（战斗）、π ρ φ χ（挖掘、剪、经验）。</li>
 *   <li>全局战利品修改器（{@code item/bauble/BaubleLootModifiers}，JSON 在 data/essentialcraft5/loot_modifiers/ 与 data/forge/loot_modifiers/）：ο（方块掉落）、τ（击杀掉落）。</li>
 *   <li>只进标签、由别的模块 / 框架读的：抗性指环 → wind_resistant、β → wind_boon、捕风挂坠 → wind_catcher（ECWind 读）；
 *       α → ubmru_from_magic_water、γ → heals_from_mru_corruption（框架 MagicalWaterItem / MRUCorruptionEffect 读）。标签写在 tools/tags/ecbaubles.json。</li>
 *   <li>海纳尔的 5 件同样只进标签（常量 {@link #HALVES_RADIATION} 等，判定用 {@code CuriosCompat.has(玩家, 标签)} 的每玩家缓存）：
 *       δ → halves_radiation、λ → purges_radiation、μ → hoanna_attuned、ξ → irradiates_on_hit（ECHoannaHazards 读）；ε → drops_embers（ECVoid 读）。</li>
 * </ul>
 * 全部饰品「同种只生效一件」（canEquip 拒第二件，查询也只认一件），配置 {@code general.baubles.disabled} 列进去的不生效、不进地牢池（CuriosCompat）。
 * 本模块自己的限制（policy §4.2）在 {@link Limits}：ν 每次最多打几只、ψ 同一只怪多久最多改一次目标、φ 两次剪之间的间隔。
 * <p>
 * 原作这些饰品没有配方，只从战利品来（原版地牢箱子、上古遗迹里的地牢箱子）。文字 tools/lang/keys/ecbaubles.json，研究手册支线章 assets/essentialcraft5/guide/baubles.json。
 */
public final class ECBaubles {

    /** 所有特殊饰品（数据包、手册、测试用；32 件全在里面） */
    public static final TagKey<Item> SPECIAL_BAUBLES = ECTags.modItem("special_baubles");

    // ---------------------------------------------------------------- 海纳尔 5 件的效果标签（6a；成员写在 tools/tags/ecbaubles.json）

    /** δ 指环（meta 11）：辐射压生命上限时按一半的等级算（ECHoannaHazards 的 RadiationEffect 读） */
    public static final TagKey<Item> HALVES_RADIATION = ECTags.modItem("halves_radiation");
    /** ε 腰带（meta 12）：真玩家亲手击杀生物时 5% 多掉一个随机余烬（ECVoid 读） */
    public static final TagKey<Item> DROPS_EMBERS = ECTags.modItem("drops_embers");
    /** λ 指环（meta 18）：不在海纳尔时辐射额外消退（ECHoannaHazards 读） */
    public static final TagKey<Item> PURGES_RADIATION = ECTags.modItem("purges_radiation");
    /** μ 指环（meta 19）：在海纳尔多吃辐射，换来不受日阵、毒雾、强震影响（ECHoannaHazards 读） */
    public static final TagKey<Item> HOANNA_ATTUNED = ECTags.modItem("hoanna_attuned");
    /** ξ 腰带（meta 21）：打人给辐射、打怪降最大生命、自己挨打也吃辐射（ECHoannaHazards 读） */
    public static final TagKey<Item> IRRADIATES_ON_HIT = ECTags.modItem("irradiates_on_hit");

    private static final List<RegistryObject<? extends SpecialBaubleItem>> ALL = new ArrayList<>();

    // ---------------------------------------------------------------- 项链（原作 AMULET）

    /** meta 0：把佩戴者的 UBMRU 换成自己存的 MRU（E6：储存物品，10 UBMRU → 1 MRU，同矩阵换能器） */
    public static final RegistryObject<PortableMatrixDestructorItem> PORTABLE_MATRIX_DESTRUCTOR = register(ECItems.register("portable_matrix_destructor",
            () -> new PortableMatrixDestructorItem(properties()),
            spec("portablemd", CuriosSlot.NECKLACE)));
    /** meta 4：击杀 UBMRU −30%，这部分 ÷30 回血 */
    public static final RegistryObject<SpecialBaubleItem> LIFE_PENDANT = bauble("life_pendant", "lifependant", CuriosSlot.NECKLACE,
            s -> s.gain(BaubleEffects::lifePendant));
    /** meta 5：风之好感系数 −0.1；好感事件 15% 给受困之风（ECWind 按 wind_catcher 标签做） */
    public static final RegistryObject<SpecialBaubleItem> WIND_CATCHER = bauble("wind_catcher", "windcatcher", CuriosSlot.NECKLACE,
            s -> s.windAffinity(-0.1F).detail());
    /** meta 6：风之好感系数 +0.3 */
    public static final RegistryObject<SpecialBaubleItem> WIND_MAGES_TALISMAN = bauble("wind_mages_talisman", "windmagestalisman", CuriosSlot.NECKLACE,
            s -> s.windAffinity(0.3F));
    /** meta 13（原作 dzetaAmulet）：每击扣 300 UBMRU，目标无敌帧清零 */
    public static final RegistryObject<SpecialBaubleItem> ZETA_AMULET = bauble("zeta_amulet", "dzetaamulet", CuriosSlot.NECKLACE, s -> s.detail());
    /** meta 14：造成和受到的伤害都 ×2 */
    public static final RegistryObject<SpecialBaubleItem> ETA_AMULET = bauble("eta_amulet", "etaamulet", CuriosSlot.NECKLACE, UnaryOperator.identity());
    /** meta 20：击杀 UBMRU −25%，击杀时打周围敌对生物各 3 点（只连锁一层，每次最多 {@link Limits#nuMaxTargets()} 只） */
    public static final RegistryObject<SpecialBaubleItem> NU_AMULET = bauble("nu_amulet", "nuamulet", CuriosSlot.NECKLACE,
            s -> s.gain(BaubleEffects::nu).detail());
    /** meta 22：挖方块时非方块掉落 +1，每次 500 UBMRU（全局战利品修改器） */
    public static final RegistryObject<SpecialBaubleItem> OMICRON_AMULET = bauble("omicron_amulet", "omicronamulet", CuriosSlot.NECKLACE, s -> s.detail());

    // ---------------------------------------------------------------- 腰带（原作 BELT）

    /** meta 7：击杀不得 UBMRU；八种负面效果时长每 tick 额外 −1（每 20 tick 结算一次） */
    public static final RegistryObject<SpecialBaubleItem> DOCTORS_BELT = bauble("doctors_belt", "doctorsbelt", CuriosSlot.BELT,
            s -> s.gain(BaubleEffects::doctorsBelt).tick(BaubleEffects::doctorsBeltTick).detail());
    /** meta 12：击杀敌对生物掉余烬（{@link #DROPS_EMBERS}，效果在 ECVoid；第十批起只算敌对生物） */
    public static final RegistryObject<SpecialBaubleItem> EPSILON_BELT = bauble("epsilon_belt", "epsilonbelt", CuriosSlot.BELT,
            s -> s.detail().args(() -> new Object[]{percent(Limits.epsilonEmberChance())}));
    /** meta 15：受到致命伤时扣「伤害 × 5000」UBMRU 抵掉这一下；抵掉后冷却 {@link Limits#thetaCooldown()}（第十批加，原作没有冷却） */
    public static final RegistryObject<SpecialBaubleItem> THETA_BELT = bauble("theta_belt", "thetabelt", CuriosSlot.BELT,
            s -> s.detail().args(() -> new Object[]{ticksToSeconds(Limits.thetaCooldown())}));
    /**
     * meta 21：打人给辐射、打怪压最大生命、自己挨打也吃辐射（{@link #IRRADIATES_ON_HIT}，效果在 ECHoannaHazards；第十批：辐射有上限、打怪只压打掉的那部分且不存盘）。
     * 提示里「打玩家」「自己受伤」两段只在 ξ 真的会给玩家加辐射时出现（剂量上限 &gt; 0、辐射开着），否则只剩打怪那一段
     */
    public static final RegistryObject<SpecialBaubleItem> XI_BELT = bauble("xi_belt", "xibelt", CuriosSlot.BELT,
            s -> s.detail().args(ECBaubles::xiArgs));
    /** meta 23：挖矿石的经验 ×{@link Limits#piXpMultiplier()}（原作 ×3，第十批改），本来没经验的矿石 5% 给 1 点（挖下来掉的是矿石自己时不算） */
    public static final RegistryObject<SpecialBaubleItem> PI_BELT = bauble("pi_belt", "pibelt", CuriosSlot.BELT,
            s -> s.detail().args(() -> new Object[]{number(Limits.piXpMultiplier())}));

    // ---------------------------------------------------------------- 戒指（原作 RING）

    /** meta 1：击杀 UBMRU + 0~99 */
    public static final RegistryObject<SpecialBaubleItem> RING_OF_STABILITY = bauble("ring_of_stability", "ringofstability", CuriosSlot.RING,
            s -> s.gain(BaubleEffects::stability));
    /** meta 2：击杀 UBMRU −10%，拿走的部分每 {@link Limits#experienceRingUbmruPerXp()} 点变成 1 点经验（原作每 4 点，第十批改） */
    public static final RegistryObject<SpecialBaubleItem> RING_OF_EXPERIENCE = bauble("ring_of_experience", "ringofexperience", CuriosSlot.RING,
            s -> s.gain(BaubleEffects::experience).detail().args(() -> new Object[]{number(Limits.experienceRingUbmruPerXp())}));
    /** meta 3：不受风的捉弄（wind_resistant 标签，ECWind 读）。原作模型误用了稳定指环的贴图，这里用它自己的 */
    public static final RegistryObject<SpecialBaubleItem> RING_OF_RESISTANCE = bauble("ring_of_resistance", "ringofresistance", CuriosSlot.RING,
            UnaryOperator.identity());
    /** meta 8：喝魔力水 +500 UBMRU（ubmru_from_magic_water 标签，框架 MagicalWaterItem 读） */
    public static final RegistryObject<SpecialBaubleItem> ALPHA_RING = bauble("alpha_ring", "alpharing", CuriosSlot.RING, UnaryOperator.identity());
    /** meta 9：风之好感事件变成元素增益（wind_boon 标签，ECWind 读） */
    public static final RegistryObject<SpecialBaubleItem> BETA_RING = bauble("beta_ring", "betaring", CuriosSlot.RING, UnaryOperator.identity());
    /** meta 10：MRU 腐化改成回血（heals_from_mru_corruption 标签，框架 MRUCorruptionEffect 读） */
    public static final RegistryObject<SpecialBaubleItem> GAMMA_RING = bauble("gamma_ring", "gammaring", CuriosSlot.RING, UnaryOperator.identity());
    /** meta 11：辐射压生命上限时按一半等级算（{@link #HALVES_RADIATION}，效果在 ECHoannaHazards） */
    public static final RegistryObject<SpecialBaubleItem> DELTA_RING = bauble("delta_ring", "deltaring", CuriosSlot.RING, s -> s.detail());
    /** meta 16：打出的伤害 10% 改成无视护甲的魔法伤害 */
    public static final RegistryObject<SpecialBaubleItem> IOTA_RING = bauble("iota_ring", "iotaring", CuriosSlot.RING, s -> s.detail());
    /** meta 17：每 10 tick +1 UBMRU，每 tick 0.01 饥饿度消耗 */
    public static final RegistryObject<SpecialBaubleItem> KAPPA_RING = bauble("kappa_ring", "kapparing", CuriosSlot.RING,
            s -> s.tick(BaubleEffects::kappaTick));
    /**
     * meta 18：离开海纳尔后辐射消退快得多（{@link #PURGES_RADIATION}，效果在 ECHoannaHazards）。
     * 原作提示写「元素半衰期缩短为 1/3」，实际是每 tick 多减 3（离开海纳尔时快约 13 倍），提示按实际写。
     */
    public static final RegistryObject<SpecialBaubleItem> LAMBDA_RING = bauble("lambda_ring", "lambdaring", CuriosSlot.RING, s -> s.detail());
    /** meta 19：在海纳尔多吃辐射，换来不受日阵、毒雾、强震影响（{@link #HOANNA_ATTUNED}，效果在 ECHoannaHazards） */
    public static final RegistryObject<SpecialBaubleItem> MU_RING = bauble("mu_ring", "muring", CuriosSlot.RING, s -> s.detail());
    /** meta 24：挖矿石的经验改成 UBMRU（每点 {@link Limits#rhoUbmruPerXp()}；第十批起只认矿石） */
    public static final RegistryObject<SpecialBaubleItem> RHO_RING = bauble("rho_ring", "rhoring", CuriosSlot.RING,
            s -> s.detail().args(() -> new Object[]{number(Limits.rhoUbmruPerXp())}));
    /** meta 25：受到带来源的伤害时 10% 原样反给来源、自己免伤 */
    public static final RegistryObject<SpecialBaubleItem> SIGMA_RING = bauble("sigma_ring", "sigmaring", CuriosSlot.RING, UnaryOperator.identity());
    /** meta 26：击杀掉落每组 +0~2，每次 1000 UBMRU（全局战利品修改器，只加生物自己的掉落表；第十批起默认不加首领的，提示跟着开关走） */
    public static final RegistryObject<SpecialBaubleItem> TAU_RING = bauble("tau_ring", "tauring", CuriosSlot.RING,
            s -> s.detail().args(() -> new Object[]{bossNote("tau_ring", Limits.tauAffectsBosses())}));
    /** meta 27：离末影人最近、16 格以内的是你时，它不能瞬移 */
    public static final RegistryObject<SpecialBaubleItem> UPSILON_RING = bauble("upsilon_ring", "upsilonring", CuriosSlot.RING, s -> s.detail());
    /** meta 28：拿剪刀右键各种生物剪出材料（1000 UBMRU、剪刀 −32 耐久） */
    public static final RegistryObject<SpecialBaubleItem> PHI_RING = bauble("phi_ring", "phiring", CuriosSlot.RING, s -> s.detail());
    /** meta 29：击杀 UBMRU 减半，拾取经验球另得 {@link Limits#chiXpBonus()} 倍（原作另得一份 = ×2，第十批改） */
    public static final RegistryObject<SpecialBaubleItem> CHI_RING = bauble("chi_ring", "chiring", CuriosSlot.RING,
            s -> s.gain(BaubleEffects::chi).args(() -> new Object[]{percent(Limits.chiXpBonus())}));
    /** meta 30：怪物盯上你时改去打附近别的敌对生物（第十批起默认不管首领；提示跟着开关和换目标的间隔走） */
    public static final RegistryObject<SpecialBaubleItem> PSI_RING = bauble("psi_ring", "psiring", CuriosSlot.RING,
            s -> s.detail().args(() -> new Object[]{bossNote("psi_ring", Limits.psiAffectsBosses()), ticksToSeconds(Limits.psiCooldown())}));
    /** meta 31：击杀 UBMRU ×2（原作另有「免疫混沌 / 冰冻心智」，那两个效果按 D15 不移植） */
    public static final RegistryObject<SpecialBaubleItem> OMEGA_RING = bauble("omega_ring", "omegaring", CuriosSlot.RING,
            s -> s.gain(BaubleEffects::omega));

    private ECBaubles() {
    }

    public static void init() {
        ECConfig.general(Limits::define);
    }

    /** 全部 32 件（注册顺序：项链、腰带、戒指，各组内按原作 meta） */
    public static List<RegistryObject<? extends SpecialBaubleItem>> all() {
        return Collections.unmodifiableList(ALL);
    }

    // ---------------------------------------------------------------- 注册工具

    /** Curios 槽位种类 → 物品标签 */
    public enum CuriosSlot {
        RING(CuriosCompat.RING, ECTags.CURIOS_RING), NECKLACE(CuriosCompat.NECKLACE, ECTags.CURIOS_NECKLACE), BELT(CuriosCompat.BELT, ECTags.CURIOS_BELT);

        final String id;
        final TagKey<Item> tag;

        CuriosSlot(String id, TagKey<Item> tag) {
            this.id = id;
            this.tag = tag;
        }
    }

    /** 原作 setMaxStackSize(1) */
    private static Item.Properties properties() {
        return new Item.Properties().stacksTo(1);
    }

    private static ItemSpec spec(String texture, CuriosSlot slot) {
        return ItemSpec.generated("item/baublemodifiers/" + texture).tags(slot.tag, SPECIAL_BAUBLES);
    }

    private static RegistryObject<SpecialBaubleItem> bauble(String name, String texture, CuriosSlot slot,
                                                            UnaryOperator<SpecialBaubleItem.Settings> settings) {
        SpecialBaubleItem.Settings s = settings.apply(SpecialBaubleItem.Settings.of());
        return register(ECItems.register(name, () -> new SpecialBaubleItem(properties(), slot.id, s), spec(texture, slot)));
    }

    private static <T extends SpecialBaubleItem> RegistryObject<T> register(RegistryObject<T> object) {
        ALL.add(object);
        return object;
    }

    // ---------------------------------------------------------------- 提示里的配置数值（翻译串的 %s；串里的百分号写成 %%）

    /** 数字：整数不带小数点，其余最多两位小数 */
    static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0E15D) {
            return Long.toString((long) value);
        }
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** 0~1 的比例 → 百分数（0.05 → 5%） */
    static String percent(double fraction) {
        return number(fraction * 100D) + "%";
    }

    /** tick → 秒数（600 → 30） */
    static String ticksToSeconds(int ticks) {
        return number(ticks / 20D);
    }

    /** τ / ψ 提示里「首领不算」那半句：对首领也生效时是空的，否则是 tooltip.essentialcraft5.bauble.&lt;名字&gt;.no_bosses */
    public static Component bossNote(String name, boolean affectsBosses) {
        return affectsBosses ? Component.empty() : Component.translatable("tooltip.essentialcraft5.bauble." + name + ".no_bosses");
    }

    /**
     * ξ 提示的两段：打玩家（每下封顶、剂量上限）和自己受伤。剂量上限是 0 或辐射关着时 ξ 一点辐射都不给，这两段换成空的。
     */
    public static Object[] xiArgs() {
        boolean irradiates = Limits.xiDoseCeiling() > 0 && HoannaHazardsConfig.radiationEnabled();
        if (!irradiates) {
            return new Object[]{Component.empty(), Component.empty()};
        }
        return new Object[]{
                Component.translatable("tooltip.essentialcraft5.bauble.xi_belt.players", number(Limits.xiDosePerHit()), number(Limits.xiDoseCeiling())),
                Component.translatable("tooltip.essentialcraft5.bauble.xi_belt.self")};
    }

    // ---------------------------------------------------------------- 配置

    /**
     * SERVER 配置 {@code general.bauble_limits}：policy §4.2 给饰品新定的上限和间隔，以及第十批饰品平衡定的数值（禁用列表在框架的 {@code general.baubles}）。
     * 配置没加载时用默认值。
     * <p>
     * 第十批饰品平衡（batch10 饰品平衡一节）改了的：经验指环的换算、π 的倍数、π / ρ 只认矿石、χ 的加成、θ 的冷却、
     * ξ 打玩家每一下的剂量与剂量上限、ε 只认敌对生物，τ 和 ψ 默认不对首领（{@code #forge:bosses}）生效；ρ 的换算和 ε 的几率没改、只是做成了配置。
     * 各项的原作值写在常量注释里。
     */
    public static final class Limits {
        public static final int DEFAULT_NU_MAX_TARGETS = 16;
        public static final int DEFAULT_PSI_COOLDOWN = 40;
        public static final int DEFAULT_PHI_COOLDOWN = 10;
        /** 经验指环：拿走的 UBMRU 每多少点变成 1 点经验（原作 4：一只僵尸给 10 点经验，是它本身的两倍） */
        public static final int DEFAULT_EXPERIENCE_RING_UBMRU_PER_XP = 20;
        /** π 腰带：挖矿石掉的经验倍数（原作 3；只认矿石以后没有无限来源，×2 让腰带格有理由戴，和 ρ 叠戴是每点 2000 UBMRU） */
        public static final double DEFAULT_PI_XP_MULTIPLIER = 2D;
        /** χ 指环：捡经验球时另给的比例（原作 1，等于翻倍） */
        public static final double DEFAULT_CHI_XP_BONUS = 0.5D;
        /** π、ρ 只认 {@code #forge:ores} 里的方块（原作没有这条；1.12 原版会掉经验的方块基本只有矿石） */
        public static final boolean DEFAULT_MINING_XP_ORES_ONLY = true;
        /** ρ 指环：每点经验换多少 UBMRU（原作 1000，不变） */
        public static final int DEFAULT_RHO_UBMRU_PER_XP = 1000;
        /** θ 腰带：抵掉一次致命伤后多少 tick 内不再生效（原作没有冷却） */
        public static final int DEFAULT_THETA_COOLDOWN = 600;
        /** ξ 腰带：打玩家每一下最多给对方多少剂量（原作不封顶，第六批封到 10000） */
        public static final int DEFAULT_XI_DOSE_PER_HIT = 5000;
        /** ξ 腰带：打人给的、自己挨打吃的辐射，最多把剂量推到多少（原作不封顶） */
        public static final int DEFAULT_XI_DOSE_CEILING = 15000;
        /** ε 腰带：亲手击杀敌对生物时多掉一个余烬的几率（原作 0.05，不变；原作对所有生物和玩家都算） */
        public static final double DEFAULT_EPSILON_EMBER_CHANCE = 0.05D;
        public static final boolean DEFAULT_TAU_AFFECTS_BOSSES = false;
        public static final boolean DEFAULT_PSI_AFFECTS_BOSSES = false;

        @Nullable
        static ForgeConfigSpec.IntValue nuMaxTargets;
        @Nullable
        static ForgeConfigSpec.IntValue psiCooldown;
        @Nullable
        static ForgeConfigSpec.IntValue phiCooldown;
        @Nullable
        static ForgeConfigSpec.IntValue experienceRingUbmruPerXp;
        @Nullable
        static ForgeConfigSpec.DoubleValue piXpMultiplier;
        @Nullable
        static ForgeConfigSpec.DoubleValue chiXpBonus;
        @Nullable
        static ForgeConfigSpec.BooleanValue miningXpOresOnly;
        @Nullable
        static ForgeConfigSpec.IntValue rhoUbmruPerXp;
        @Nullable
        static ForgeConfigSpec.IntValue thetaCooldown;
        @Nullable
        static ForgeConfigSpec.IntValue xiDosePerHit;
        @Nullable
        static ForgeConfigSpec.IntValue xiDoseCeiling;
        @Nullable
        static ForgeConfigSpec.DoubleValue epsilonEmberChance;
        @Nullable
        static ForgeConfigSpec.BooleanValue tauAffectsBosses;
        @Nullable
        static ForgeConfigSpec.BooleanValue psiAffectsBosses;

        private Limits() {
        }

        static void define(ForgeConfigSpec.Builder builder) {
            builder.comment("Limits and numbers for special baubles (anti-lag / anti-abuse / balance).", "特殊饰品的上限、间隔与数值（防卡服、防滥用、平衡）")
                    .push("bauble_limits");
            nuMaxTargets = builder.comment("Nu Amulet: at most this many hostile mobs are hit per kill (nearest first). Kills made by the Amulet itself never chain.",
                            "ν 护身符：每次击杀最多打几只周围的敌对生物（近的先）；护身符自己打死的不再连锁")
                    .defineInRange("nu_max_targets", DEFAULT_NU_MAX_TARGETS, 0, 64);
            psiCooldown = builder.comment("Psi Ring: a mob is redirected to a new target at most once per this many ticks.",
                            "ψ 指环：同一只怪多少 tick 内最多换一次目标")
                    .defineInRange("psi_cooldown_ticks", DEFAULT_PSI_COOLDOWN, 1, 1200);
            phiCooldown = builder.comment("Phi Ring: minimum ticks between two shearings by the same player.",
                            "φ 指环：同一个玩家两次剪取之间至少隔几 tick")
                    .defineInRange("phi_cooldown_ticks", DEFAULT_PHI_COOLDOWN, 0, 1200);
            experienceRingUbmruPerXp = builder.comment("Ring of Experience: every this many UBMRU it takes from a kill becomes 1 experience point (the original mod used 4).",
                            "经验指环：从击杀里拿走的 UBMRU 每多少点变成 1 点经验（原作 4）")
                    .defineInRange("experience_ring_ubmru_per_xp", DEFAULT_EXPERIENCE_RING_UBMRU_PER_XP, 1, 100000);
            piXpMultiplier = builder.comment("Pi Belt: experience from mining ores (any block when mining_xp_ores_only is off) is multiplied by this (the original mod used 3). Fractions are rounded randomly.",
                            "π 腰带：挖矿石（关掉 mining_xp_ores_only 时为任何方块）掉的经验乘以这个数（原作 3）；小数部分按几率进位")
                    .defineInRange("pi_xp_multiplier", DEFAULT_PI_XP_MULTIPLIER, 1D, 3D);
            chiXpBonus = builder.comment("Chi Ring: picking up an experience orb gives this fraction of its value on top (the original mod used 1, i.e. double). Fractions are rounded randomly.",
                            "χ 指环：捡经验球时另给球上数值的这个比例（原作 1，即翻倍）；小数部分按几率进位")
                    .defineInRange("chi_xp_bonus", DEFAULT_CHI_XP_BONUS, 0D, 1D);
            miningXpOresOnly = builder.comment("Pi Belt and Rho Ring only work on blocks in #forge:ores. Off: any block that drops experience (spawners, sculk) counts, and the Pi Belt's bonus point can come from any block.",
                            "π 腰带、ρ 指环只认 #forge:ores 里的方块。关掉后所有掉经验的方块（刷怪笼、幽匿块）都算，π 的「没经验也给 1 点」对任何方块都掷")
                    .define("mining_xp_ores_only", DEFAULT_MINING_XP_ORES_ONLY);
            rhoUbmruPerXp = builder.comment("Rho Ring: UBMRU given per point of mining experience.",
                            "ρ 指环：挖方块的每点经验换多少 UBMRU")
                    .defineInRange("rho_ubmru_per_xp", DEFAULT_RHO_UBMRU_PER_XP, 0, 100000);
            thetaCooldown = builder.comment("Theta Belt: after it stops a fatal hit, it does nothing for this many ticks (0 = no cooldown, as in the original mod).",
                            "θ 腰带：抵掉一次致命伤以后，这么多 tick 内不再生效（0 = 没有冷却，同原作）")
                    .defineInRange("theta_cooldown_ticks", DEFAULT_THETA_COOLDOWN, 0, 72000);
            xiDosePerHit = builder.comment("Xi Belt: at most this much radiation dose per hit on a player (5000 per point of damage before the cap).",
                            "ξ 腰带：打玩家每一下最多给对方多少辐射剂量（封顶前每点伤害 5000）")
                    .defineInRange("xi_dose_per_hit", DEFAULT_XI_DOSE_PER_HIT, 0, 100000);
            xiDoseCeiling = builder.comment("Xi Belt: radiation from its hits (on others) and from hits taken (by the wearer) never pushes a player's dose above this. 10000 dose = one level. 0 = it never irradiates players.",
                            "ξ 腰带：打人给的、戴着的人挨打吃的辐射，最多把剂量推到这么多（每 10000 一级；已经更高的不再加）。0 = 不给玩家加辐射")
                    .defineInRange("xi_dose_ceiling", DEFAULT_XI_DOSE_CEILING, 0, 1_000_000);
            epsilonEmberChance = builder.comment("Epsilon Belt: chance of one extra random Ember when you kill a hostile mob yourself.",
                            "ε 腰带：亲手击杀敌对生物时多掉一个随机余烬的几率")
                    .defineInRange("epsilon_ember_chance", DEFAULT_EPSILON_EMBER_CHANCE, 0D, 1D);
            tauAffectsBosses = builder.comment("Tau Ring: also adds loot to bosses (#forge:bosses), e.g. extra Nether Stars from the Wither. What counts as a boss is the entity tag #forge:bosses; a datapack can add mobs to it.",
                            "τ 指环：对首领（#forge:bosses）也加掉落，例如凋灵多掉下界之星。哪些算首领以实体标签 #forge:bosses 为准，可以用数据包补")
                    .define("tau_affects_bosses", DEFAULT_TAU_AFFECTS_BOSSES);
            psiAffectsBosses = builder.comment("Psi Ring: also redirects bosses (#forge:bosses) to other hostile mobs, e.g. onto their own minions. What counts as a boss is the entity tag #forge:bosses; a datapack can add mobs to it.",
                            "ψ 指环：首领（#forge:bosses）盯上你时也改去打别的敌对生物（例如它自己召来的小怪）。哪些算首领以实体标签 #forge:bosses 为准，可以用数据包补")
                    .define("psi_affects_bosses", DEFAULT_PSI_AFFECTS_BOSSES);
            builder.pop();
        }

        public static int nuMaxTargets() {
            return nuMaxTargets == null ? DEFAULT_NU_MAX_TARGETS : ECConfig.get(nuMaxTargets);
        }

        public static int psiCooldown() {
            return psiCooldown == null ? DEFAULT_PSI_COOLDOWN : ECConfig.get(psiCooldown);
        }

        public static int phiCooldown() {
            return phiCooldown == null ? DEFAULT_PHI_COOLDOWN : ECConfig.get(phiCooldown);
        }

        public static int experienceRingUbmruPerXp() {
            return experienceRingUbmruPerXp == null ? DEFAULT_EXPERIENCE_RING_UBMRU_PER_XP : ECConfig.get(experienceRingUbmruPerXp);
        }

        public static double piXpMultiplier() {
            return piXpMultiplier == null ? DEFAULT_PI_XP_MULTIPLIER : ECConfig.get(piXpMultiplier);
        }

        public static double chiXpBonus() {
            return chiXpBonus == null ? DEFAULT_CHI_XP_BONUS : ECConfig.get(chiXpBonus);
        }

        public static boolean miningXpOresOnly() {
            return miningXpOresOnly == null ? DEFAULT_MINING_XP_ORES_ONLY : ECConfig.get(miningXpOresOnly);
        }

        public static int rhoUbmruPerXp() {
            return rhoUbmruPerXp == null ? DEFAULT_RHO_UBMRU_PER_XP : ECConfig.get(rhoUbmruPerXp);
        }

        public static int thetaCooldown() {
            return thetaCooldown == null ? DEFAULT_THETA_COOLDOWN : ECConfig.get(thetaCooldown);
        }

        public static int xiDosePerHit() {
            return xiDosePerHit == null ? DEFAULT_XI_DOSE_PER_HIT : ECConfig.get(xiDosePerHit);
        }

        public static int xiDoseCeiling() {
            return xiDoseCeiling == null ? DEFAULT_XI_DOSE_CEILING : ECConfig.get(xiDoseCeiling);
        }

        public static double epsilonEmberChance() {
            return epsilonEmberChance == null ? DEFAULT_EPSILON_EMBER_CHANCE : ECConfig.get(epsilonEmberChance);
        }

        public static boolean tauAffectsBosses() {
            return tauAffectsBosses == null ? DEFAULT_TAU_AFFECTS_BOSSES : ECConfig.get(tauAffectsBosses);
        }

        public static boolean psiAffectsBosses() {
            return psiAffectsBosses == null ? DEFAULT_PSI_AFFECTS_BOSSES : ECConfig.get(psiAffectsBosses);
        }

        /** 小数部分按几率进位：{@code ⌊value⌋ + (random < 小数部分 ? 1 : 0)}，负数和 NaN 当 0（π、χ、经验指环共用；GameTest 直接测） */
        public static int roundRandomly(double value, RandomSource random) {
            if (!(value > 0D)) {
                return 0;
            }
            double floor = Math.floor(value);
            int whole = floor >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) floor;
            double fraction = value - floor;
            return fraction > 0D && whole < Integer.MAX_VALUE && random.nextDouble() < fraction ? whole + 1 : whole;
        }
    }
}
