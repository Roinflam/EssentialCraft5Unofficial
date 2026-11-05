package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 本模组的伤害类型（第四批框架 F4-13）。
 * <p>
 * 伤害类型在 1.20.1 是数据包注册表，不走 DeferredRegister：定义是静态 JSON
 * {@code data/essentialcraft5/damage_type/<名字>.json}，这里只放键和取 holder 的帮手。
 * 标签同样是静态 JSON（ECTagProviders 只管方块和物品）：
 * <ul>
 *   <li>{@code data/minecraft/tags/damage_type/bypasses_armor.json}、{@code avoids_guardian_thorns.json}、
 *       {@code always_triggers_silverfish.json}：照原作 {@code DamageSource.MAGIC} 的性质追加（{@code replace: false}）。
 *       <b>不进 {@code witch_resistant_to}</b>（batch4 裁定 6）：有的战斗模组按这个标签把伤害归成魔法、再套自己的魔法层，
 *       线圈对玩家的数值就不是定下的那个了；女巫多吃一点伤害是小事。</li>
 *   <li>{@code data/essentialcraft5/tags/damage_type/no_death_loot.json}：被这些类型打死的非玩家生物不掉物品、不掉经验
 *       （怪捡走的必掉装备照掉），见 {@code util/DeathLootEvents}。第七批的 {@code demon}、{@code soul_capture} 在里面。</li>
 *   <li>{@code data/essentialcraft5/tags/damage_type/physical.json}、{@code not_physical.json}（默认空）：给 {@code util/ECDamageKinds}
 *       的「物理伤害」判定加白名单 / 黑名单，服主或整合包按别的模组的伤害类型调整（虚空套装只挡物理伤害，batch7 D1）。</li>
 *   <li>{@code data/essentialcraft5/tags/damage_type/reflected.json}（默认空）：别的模组的反伤类型，{@code util/ECDamageKinds#isReflected}
 *       连同原版荆棘一起认成反伤——交手记录不算对方动手、削减模块不叠破甲（第十批 10c）。</li>
 * </ul>
 *
 * <h2>第七批（batch7 §3.1 第 4 条）</h2>
 * 三个类型都先于标签存在：标签里引用了不存在的类型，整张标签加载失败，连 {@code mru_discharge} 的不掉落也一起没了，所以放在框架里一次建好。
 * <ul>
 *   <li>{@code demon}：恶魔清怪（近战一下 + 同一伤害源的过滤爆炸），伤害源带攻击者（恶魔）；{@code scaling = never}；进 {@code no_death_loot}。</li>
 *   <li>{@code soul_capture}：灵魂刻刀收魂那一下（剩 ≤ 2 血时补上的致死伤害），伤害源带攻击者（玩家本人），PvP、领地、混合端插件照样问得到；
 *       进 {@code no_death_loot}，另进原版 {@code bypasses_armor}、{@code bypasses_cooldown}（刚打过一下、目标还在无敌帧里）、
 *       {@code bypasses_effects}、{@code bypasses_enchantments}、{@code bypasses_resistance}（数额按目标剩余生命 + 伤害吸收 + 1 算好，不该再被减免）。</li>
 *   <li>{@code ember_backlash}：余烬元素剑的反噬，<b>不带攻击者</b>、只打持剑人自己（PvP 关着也照样反噬）；不进任何标签。</li>
 * </ul>
 * 死亡消息键和 {@code mru_discharge} 写在同一个键文件（{@code tools/lang/keys/framework4_d.json}）：带攻击者的用基础键和 {@code .item}，
 * 没有攻击者时原版按 {@code getKillCredit()} 选基础键或 {@code .player}。
 *
 * <h2>{@code mru_discharge}：MRU 电弧（特斯拉线圈、反应堆）</h2>
 * {@code message_id = essentialcraft5.mru_discharge}、{@code scaling = never}（原版 magic 是 when_caused_by_living_non_player，
 * 没有攻击者时本来就不缩放，写 never 意思更清楚）、{@code exhaustion = 0}（同原版 magic）。
 * {@link #mruDischarge} 造的伤害源<b>不带攻击者、不带位置</b>（同原作 {@code DamageSource.MAGIC}）：
 * <ul>
 *   <li>盾挡不住（原版 {@code bypasses_shield} 标签包含 {@code #bypasses_armor}；格挡本来也要伤害源有位置）、没有击退（原版击退只看攻击者）；
 *       护甲点数无视（bypasses_armor），保护附魔、抗性提升、伤害吸收照算。</li>
 *   <li><b>PvP 关了也打得到玩家</b>：原版 {@code ServerPlayer.hurt} 只在攻击者是玩家 / 箭的主人是玩家时查 {@code canHarmPlayer}；
 *       混合端对没攻击者的伤害发普通 {@code EntityDamageEvent(CUSTOM)}，不是 {@code EntityDamageByEntityEvent}，
 *       领地插件的 PvP 旗帜管不到，「禁止一切伤害」类旗帜照样能拦（policy §2.1 ⑤；这是 policy §1.1「伤害带攻击者」的唯一例外，只给线圈打玩家用）。</li>
 *   <li>玩家被它打死不算玩家击杀：{@code UBMRUEvents.handleDeath} 只认 {@code source.getEntity()} 是玩家，没人拿到 UBMRU，受害者照旧清零。</li>
 * </ul>
 * 死亡消息 {@code death.attack.essentialcraft5.mru_discharge}（没有最后伤害它的生物时）与 {@code ….player}
 * （原版对没攻击者的伤害源按 {@code getKillCredit()} 选带不带 .player）；线圈自己的伤害源另有 {@code .coil}、{@code .coil.player}（ECTesla 写）。
 * 线圈要在死亡消息里写主人名，就自己继承 {@link DamageSource}，构造时传 {@link #holder(Level, ResourceKey)} 取到的 holder、不传实体和位置。
 *
 * <h2>第八批（batch8 §3.1 第 1 条）：轨道打击与二分场</h2>
 * 两个技能实体（{@code entity/OrbitalStrikeEntity}、{@code entity/DividerFieldEntity}）由全息投影和两件武器共用，伤害源一律带攻击者、
 * <b>不带直接实体和位置</b>（{@code new DamageSource(holder, null, 攻击者)}）：PvP、队伍、领地模组、混合端插件都照攻击者管。
 * <ul>
 *   <li>{@code orbital_strike}：{@code scaling = never}（原作匿名伤害源不随难度放大）、{@code exhaustion = 0}；进原版 {@code bypasses_effects}
 *       （跳过抗性提升和保护附魔，护甲照算——原作 {@code setDamageIsAbsolute()} 在 1.12 就是这个效果，不是无视护甲）和 {@code bypasses_shield}
 *       （伤害源本来就没有位置、盾挡不住，标签让意图清楚）。死亡消息有基础键、{@code .item}、{@code .player} 三个。</li>
 *   <li>{@code divider}：二分场对每个目标先走一次 0 伤害的探测（领地插件放行才减半），{@code scaling = never}；进 {@code bypasses_cooldown}
 *       （目标在无敌帧里也问得到插件）和 {@code no_anger}（探测不让目标记仇；原作直接改生命，本来也不会激怒目标）。
 *       0 伤害打不死人，只写基础键。</li>
 * </ul>
 * 死亡消息键在 {@code tools/lang/keys/framework8a.json}。
 *
 * <h2>第十三批 13c（batch13.md §5.1）：新怪物的三种伤害</h2>
 * 一律 {@code scaling = when_caused_by_living_non_player}（同原版怪物攻击：打玩家时随难度缩放），伤害源一律带攻击者（领地、混合端插件照常管）。
 * 新怪的远程一律不用 {@code mru_ray}（任何护盾都不挡它，留给 PvP 枪械）。护盾、审判庭、虚空对它们的口径都靠标签和已有判定得出，没有按类型写死的分支：
 * <table>
 *   <tr><th>类型</th><th>标签</th><th>挡法</th></tr>
 *   <tr><td>{@code mob_bolt}（弹射物、光束）</td><td>{@code minecraft:is_projectile}</td>
 *       <td>算物理伤害（{@code util/ECDamageKinds#isPhysical}）：魔力防护、虚空、宅男照挡；审判庭不减（首领打来的照吃首领减伤）；虚空照常闪避</td></tr>
 *   <tr><td>{@code aberrant_strike}（有预警的大招）</td><td>{@code minecraft:bypasses_armor}、{@link #UNDODGEABLE}</td>
 *       <td>不是魔法：宅男 4 阶起挡一半、×3 价（{@code ComputerAttunement#coverage} 的无视护甲一支）；审判庭 4 阶 ×0.7（{@code piercing_resistance_stage_4}）；
 *       虚空闪避、相位都不作用；盾牌挡不住（原版 {@code bypasses_shield} 含 {@code #bypasses_armor}）</td></tr>
 *   <tr><td>{@code matrix_rend}（矩阵之影的矩阵相）</td><td>{@code minecraft:bypasses_armor}、{@code forge:is_magic}、{@link #UNDODGEABLE}</td>
 *       <td>算魔法（{@code ECDamageKinds#isMagic} 认 {@code forge:is_magic}）：宅男 2 阶起按魔法挡、×2 价，4 阶也照魔法整份挡；审判庭抗魔（0 阶 35%、1 阶起 45%）；
 *       虚空闪避、相位都不作用</td></tr>
 * </table>
 * {@link #matrixRend} 的直接实体就是攻击者本身：直接实体是另一个活物（比如一根「柱子」实体）时 {@code ECDamageKinds} 会把它当成召唤物替主人挥砍、
 * 不算魔法，宅男和审判庭就不按魔法挡了，所以不给它留「另传直接实体」的重载。
 * 死亡消息（基础键、{@code .item}、{@code .player}）在 {@code tools/lang/keys/framework13c.json}。
 *
 * <h2>第十三批 13e（batch13.md §5.1、§5.3.5）：天灾之心的两种</h2>
 * 同上，{@code scaling = when_caused_by_living_non_player}、伤害源带攻击者；挡法和 {@code aberrant_strike} 完全一样（同样只靠标签得出，没有新分支）：
 * <table>
 *   <tr><th>类型</th><th>标签</th><th>挡法</th></tr>
 *   <tr><td>{@code calamity}（天灾之心有预警的招：毒雾、黑暗、地震三相，崩解脉冲）</td><td>{@code minecraft:bypasses_armor}、{@link #UNDODGEABLE}</td>
 *       <td>同 {@code aberrant_strike}：宅男 4 阶起挡一半、×3 价；审判庭 4 阶 ×0.7；虚空闪避、相位都不作用；盾牌挡不住</td></tr>
 *   <tr><td>{@code calamity_solar}（日阵相：天光棱线、烈日标记）</td><td>同上，另加 {@code minecraft:is_fire}</td>
 *       <td>同 {@code calamity}；另外算火（火焰保护减、免疫火的生物不吃），但不点燃目标（点不点火由招式自己决定）</td></tr>
 * </table>
 * 死亡消息在 {@code tools/lang/keys/eccalamityheart.json}。
 */
public final class ECDamageTypes {

    /** MRU 电弧 */
    public static final ResourceKey<DamageType> MRU_DISCHARGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, EssentialCraft5.id("mru_discharge"));

    /** 恶魔清怪（batch7 §4.1） */
    public static final ResourceKey<DamageType> DEMON =
            ResourceKey.create(Registries.DAMAGE_TYPE, EssentialCraft5.id("demon"));

    /** 灵魂刻刀收魂（batch7 §4.3） */
    public static final ResourceKey<DamageType> SOUL_CAPTURE =
            ResourceKey.create(Registries.DAMAGE_TYPE, EssentialCraft5.id("soul_capture"));

    /** 余烬元素剑反噬（batch7 §4.2） */
    public static final ResourceKey<DamageType> EMBER_BACKLASH =
            ResourceKey.create(Registries.DAMAGE_TYPE, EssentialCraft5.id("ember_backlash"));

    /** 轨道打击（batch8 §3.1） */
    public static final ResourceKey<DamageType> ORBITAL_STRIKE =
            ResourceKey.create(Registries.DAMAGE_TYPE, EssentialCraft5.id("orbital_strike"));

    /** 二分场的 0 伤害探测（batch8 §3.1） */
    public static final ResourceKey<DamageType> DIVIDER =
            ResourceKey.create(Registries.DAMAGE_TYPE, EssentialCraft5.id("divider"));

    /** 新怪物的弹射物、光束（第十三批 13c，batch13.md §5.1） */
    public static final ResourceKey<DamageType> MOB_BOLT =
            ResourceKey.create(Registries.DAMAGE_TYPE, EssentialCraft5.id("mob_bolt"));

    /** 新怪物有预警的大招：无视护甲、闪不掉（第十三批 13c） */
    public static final ResourceKey<DamageType> ABERRANT_STRIKE =
            ResourceKey.create(Registries.DAMAGE_TYPE, EssentialCraft5.id("aberrant_strike"));

    /** 矩阵之影矩阵相的招式：无视护甲、魔法、闪不掉（第十三批 13c） */
    public static final ResourceKey<DamageType> MATRIX_REND =
            ResourceKey.create(Registries.DAMAGE_TYPE, EssentialCraft5.id("matrix_rend"));

    /** 天灾之心有预警的招：无视护甲、闪不掉（第十三批 13e，batch13.md §5.3.5） */
    public static final ResourceKey<DamageType> CALAMITY =
            ResourceKey.create(Registries.DAMAGE_TYPE, EssentialCraft5.id("calamity"));

    /** 天灾之心日阵相的招：同 {@link #CALAMITY}，另算火（第十三批 13e） */
    public static final ResourceKey<DamageType> CALAMITY_SOLAR =
            ResourceKey.create(Registries.DAMAGE_TYPE, EssentialCraft5.id("calamity_solar"));

    /** 被这些伤害类型打死的非玩家生物不掉物品、不掉经验（{@code util/DeathLootEvents}） */
    public static final TagKey<DamageType> NO_DEATH_LOOT =
            TagKey.create(Registries.DAMAGE_TYPE, EssentialCraft5.id("no_death_loot"));

    /** 一律算物理伤害（{@code util/ECDamageKinds} 的白名单，默认空） */
    public static final TagKey<DamageType> PHYSICAL =
            TagKey.create(Registries.DAMAGE_TYPE, EssentialCraft5.id("physical"));

    /** 一律不算物理伤害（黑名单，优先于白名单和其它规则，默认空） */
    public static final TagKey<DamageType> NOT_PHYSICAL =
            TagKey.create(Registries.DAMAGE_TYPE, EssentialCraft5.id("not_physical"));

    /** 别的模组的反伤类型（挨打以后自动打回去的伤害，默认空；原版荆棘不用登记），给 {@code util/ECDamageKinds#isReflected} 用 */
    public static final TagKey<DamageType> REFLECTED =
            TagKey.create(Registries.DAMAGE_TYPE, EssentialCraft5.id("reflected"));

    /**
     * 虚空套装的闪避、相位都不作用的伤害（batch13.md §0.4 第 21 条、§4.4：有预警的大招要靠躲，不靠掷骰）。
     * 13c 放进 {@code aberrant_strike}、{@code matrix_rend}，13e 加天灾之心的 {@code calamity}、{@code calamity_solar}；数据包可以再加。
     */
    public static final TagKey<DamageType> UNDODGEABLE =
            TagKey.create(Registries.DAMAGE_TYPE, EssentialCraft5.id("undodgeable"));

    private ECDamageTypes() {
    }

    /** 从世界的注册表取伤害类型的 holder（数据包注册表，每个世界 / 服务器一份）。键不存在（数据包缺了定义）时抛异常 */
    public static Holder<DamageType> holder(Level level, ResourceKey<DamageType> key) {
        return holder(level.registryAccess(), key);
    }

    public static Holder<DamageType> holder(RegistryAccess access, ResourceKey<DamageType> key) {
        return access.registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
    }

    /** MRU 电弧伤害：不带攻击者、不带位置（见类注释） */
    public static DamageSource mruDischarge(Level level) {
        return new DamageSource(holder(level, MRU_DISCHARGE));
    }

    /** 恶魔清怪的伤害源：直接实体和攻击者都是恶魔本身 */
    public static DamageSource demon(Entity demon) {
        return new DamageSource(holder(demon.level(), DEMON), demon);
    }

    /** 灵魂刻刀收魂的伤害源：直接实体和攻击者都是玩家本人 */
    public static DamageSource soulCapture(Entity player) {
        return new DamageSource(holder(player.level(), SOUL_CAPTURE), player);
    }

    /** 余烬元素剑反噬：不带攻击者、不带位置（只打持剑人自己） */
    public static DamageSource emberBacklash(Level level) {
        return new DamageSource(holder(level, EMBER_BACKLASH));
    }

    /** 轨道打击：攻击者 = 召下这一击的生物（全息投影、遥控器的使用者），没有直接实体、没有位置 */
    public static DamageSource orbitalStrike(Level level, @Nullable Entity attacker) {
        return new DamageSource(holder(level, ORBITAL_STRIKE), null, attacker);
    }

    /** 二分场的 0 伤害探测：攻击者 = 放出这个场的生物（二分枪的射手、全息投影），没有直接实体、没有位置 */
    public static DamageSource divider(Level level, @Nullable Entity attacker) {
        return new DamageSource(holder(level, DIVIDER), null, attacker);
    }

    // ---------------------------------------------------------------- 第十三批 13c：新怪物（见类注释）

    /**
     * 怪物招式里的 MRU 电弧（聚能魔像的电弧鞭这类，batch13.md §5.3.1）：带攻击者、没有直接实体和位置。
     * 和 {@link #mruDischarge(Level)} 同一个类型，区别只在有攻击者——魔力防护 2 阶起的「挡带攻击者的电弧」、宅男 4 阶的无视护甲一支、
     * 审判庭 4 阶都只认有攻击者的伤害；领地、混合端插件也照攻击者管。
     */
    public static DamageSource mruDischarge(Entity attacker) {
        return new DamageSource(holder(attacker.level(), MRU_DISCHARGE), null, attacker);
    }

    /** 弹射物打中：直接实体 = 那颗弹射物，攻击者 = 射它的怪（射手已经没了时为 null） */
    public static DamageSource mobBolt(Entity projectile, @Nullable Entity shooter) {
        return new DamageSource(holder(projectile.level(), MOB_BOLT), projectile, shooter);
    }

    /**
     * 不生成实体的光束、射线：攻击者 = 发它的怪，没有直接实体（{@code is_projectile} 标签照样让它算物理伤害），来源位置是射手的眼睛。
     * 不把射手当直接实体：那样会被当成近战，凝滞反冻、宅男反伤这类「只认挥过来的那只」的都会去打远处的射手；
     * 给位置是为了让原版盾牌照样能挡（{@code isDamageSourceBlocked} 看来源位置在不在身前，没有位置就挡不住）。
     */
    public static DamageSource mobBolt(Entity shooter) {
        return new DamageSource(holder(shooter.level(), MOB_BOLT), null, shooter, shooter.getEyePosition());
    }

    /** 有预警的大招：直接实体和攻击者都是这只怪 */
    public static DamageSource aberrantStrike(Entity attacker) {
        return new DamageSource(holder(attacker.level(), ABERRANT_STRIKE), attacker);
    }

    /** 有预警的大招，由技能实体打出（落石、冲击波这类）：直接实体 = 技能实体，攻击者 = 放它的怪 */
    public static DamageSource aberrantStrike(@Nullable Entity direct, Entity attacker) {
        return new DamageSource(holder(attacker.level(), ABERRANT_STRIKE), direct, attacker);
    }

    /** 矩阵之影矩阵相的招式：直接实体和攻击者都是矩阵之影本身（技能实体打出的也传矩阵之影，见类注释） */
    public static DamageSource matrixRend(Entity attacker) {
        return new DamageSource(holder(attacker.level(), MATRIX_REND), attacker);
    }

    // ---------------------------------------------------------------- 第十三批 13e：天灾之心（见类注释）

    /** 天灾之心有预警的招：直接实体和攻击者都是天灾之心 */
    public static DamageSource calamity(Entity attacker) {
        return new DamageSource(holder(attacker.level(), CALAMITY), attacker);
    }

    /** 由技能实体打出（落石、毒雾、灾柱这类）：直接实体 = 技能实体，攻击者 = 天灾之心 */
    public static DamageSource calamity(@Nullable Entity direct, Entity attacker) {
        return new DamageSource(holder(attacker.level(), CALAMITY), direct, attacker);
    }

    /** 日阵相的招：直接实体和攻击者都是天灾之心 */
    public static DamageSource calamitySolar(Entity attacker) {
        return new DamageSource(holder(attacker.level(), CALAMITY_SOLAR), attacker);
    }

    /** 日阵相的招，由技能实体打出（天光棱线这类）：直接实体 = 技能实体，攻击者 = 天灾之心 */
    public static DamageSource calamitySolar(@Nullable Entity direct, Entity attacker) {
        return new DamageSource(holder(attacker.level(), CALAMITY_SOLAR), direct, attacker);
    }
}
