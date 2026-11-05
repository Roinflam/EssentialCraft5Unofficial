package com.flameforge.essentialcraft5.item.armor;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.attunement.AttunementGains;
import com.flameforge.essentialcraft5.item.bauble.BaubleEffects;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.util.ECShieldBlocks;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * MRU 盔甲挡伤害（第八批框架 8-Fb，batch8.md §3.2 第 6 条）：从第七批虚空套装的 VoidArmorEvents 抽出来，
 * 虚空套装、宅男套装（以及以后任何实现 {@link IMRUShieldArmor} 的盔甲）共用这一个监听器，混穿时四件的份额也照样按同一下伤害算。只在服务端、只对玩家。
 *
 * <h2>挨打（{@link LivingHurtEvent} LOW，不收已取消的，伤害 &gt; 0 且有限）</h2>
 * 带 {@code bypasses_invulnerability}（{@code /kill}、掉出世界）和在 {@code essentialcraft5:unshieldable} 里的整下不挡。
 * 头 / 胸 / 腿 / 靴依次看：穿在自己部位的 {@link IMRUShieldArmor}、这件自己存的 MRU &gt; 0、{@link IMRUShieldArmor#coverage} 给了挡法，
 * 才挡它那一份，花 {@link #cost}（{@link #pay}，即 {@code MRUDrain.payShield}：身上储存物品 → 矩阵容量 → 这件自己的）；<b>扣不起这件就不挡</b>，MRU 一点不动。
 * 每件的份额都按进来的伤害算（原作四件同一优先级）。挡下的份额从伤害里减掉；合计到 {@value #TOTAL_SHARES} 就取消这次事件
 * （{@code actuallyHurt} 不扣血、不发 {@code LivingDamageEvent}），没挡下的照常走原版护甲。击退、受伤变红照常（原作也只吸收伤害、不免击退）。
 * 纯 Forge 上挡满、玩家打玩家的那一下，交手记录（util/ECCombatTags，{@code LivingHurtEvent} 最低优先级、收已取消的）照样收到、照样记，这里不用另记。
 * <br>放在 LOW：原作的 ISpecialArmor 在整个 LivingHurtEvent 之后才算，η（伤害 ×2）、σ、ζ、ι 这些 NORMAL 的饰品先处理，花费按它们改过的伤害算；
 * 虚空胸甲的闪避在 HIGH，闪掉的那一下这里收不到。带原版 {@code bypasses_armor} 的伤害由 {@link IMRUShieldArmor#coverage} 的默认实现排除
 * （原作 ISpecialArmor 不处理无视护甲的伤害），同调阶的新覆盖面可以挡（宅男 4 阶）。
 *
 * <h2>第十三批的几条（batch13.md §4.4，policy §4.16）</h2>
 * <ul>
 *   <li><b>新覆盖面</b>（{@link IMRUShieldArmor.Coverage#extended()}：魔法、无视护甲这类原来挡不到的）只盖有攻击者的、攻击者不是自己也不是玩家：
 *       腐化 #17 / #18、MRU 腐化、中毒这类 {@code magic()} 自伤没有攻击者，照扣血（第十一批按最大生命比例扣血、10-17 腐化加重不被绕开）；
 *       对玩家攻击者只给原来的挡法。新覆盖面扣电不认无尽 MRU 晶体（{@link #pay} 的 finiteOnly）：无尽晶体在生存里流通，靠它付就成了物理、元素、魔法全免；
 *       付不起照「扣不起这件就不挡」。</li>
 *   <li><b>η 护身符「受伤 ×2」多出来的一半不让护盾吸收</b>（{@link #etaExtra}）：η 在 NORMAL 把伤害翻倍，到这里时这一下的一半是 η 加的，
 *       护盾只按另一半结算、只挡另一半，多出的那一半原样落到护甲和生命上。以前 η 加物理护盾也是这样被电抵掉的，一并改，η 的代价回来。</li>
 *   <li><b>扣费顺序</b>：身上储存物品 → 矩阵容量（玩家数据 {@code matrix_pool}）→ 这件自己存的。不打魔力相性的折扣（护盾价钱另有同调阶的降价）。</li>
 *   <li><b>挡下以后</b>：扣过电就在 Forge 总线上发 {@link MRUShieldAbsorbedEvent}；整下吃掉时还替同调记一次盔甲点
 *       （{@link AttunementGains#armorHitAbsorbed}：那一下没有 {@code LivingDamageEvent}，同调的监听器看不到）。</li>
 *   <li><b>整下吃掉的记号</b>（{@link #tookWholeHit}）：矩阵分流模块在 NORMAL 先分走一部分魔法伤害、等这一下真的落下才记矩阵损伤；
 *       剩下的被护盾整下吃掉、事件被取消时，它凭这个记号照样记（宅男 2 阶「分流先分一半、护盾挡剩下的」）。</li>
 * </ul>
 *
 * <h2>混合端：等伤害真的生效再扣</h2>
 * Mohist 系混合端（{@link RayHitGate#isBukkitHybrid}）把 Bukkit 的 {@code EntityDamageByEntityEvent} 发在 LivingHurtEvent 和 LivingDamageEvent 之间
 * （LivingHurtEvent 被取消时按 0 照样往下走；插件取消就直接返回、不设无敌帧；放行时哪怕伤害是 0 也发 LivingDamageEvent）。
 * 领地插件拦下的攻击要是在 LivingHurtEvent 里就扣了 MRU，人站在禁 PvP 的地方一滴血不掉，电却被一下下抽空。
 * 所以混合端上 LOW 只按同样的规则<b>算</b>（{@link #plan}：身上储存物品、矩阵容量的合计当预算一笔笔减，结论和依次扣一样；新覆盖面不认无尽晶体），
 * 照常改伤害 / 取消，记一条待付（{@link #defer}：这个玩家、这个伤害源对象、这一刻）；到了 LivingDamageEvent（HIGHEST，收已取消的）认出同一下再真的扣，
 * 扣完再发 {@link MRUShieldAbsorbedEvent}、记同调点。插件拦下就到不了那里，记录下一次记账时按刻数清掉，什么都不扣。
 * 真扣时偶尔扣不起（两个事件之间储存物品被别的东西花掉）就算了，这一下的伤害不回头改。
 * 混合端举盾也在 LivingHurtEvent 之后才算，盾牌整下挡掉时照样发 {@code LivingDamageEvent(0)}：这种只删记录、不扣电、不做挂上的副作用（{@link ECShieldBlocks}）。
 * 纯 Forge 上 Forge 端的领地模组在 LivingAttackEvent 就把攻击拦了，到不了这里，当场扣（{@link #absorb}）。只按名字找 Bukkit 类，不引用它们（policy §2.3a）。
 *
 * <h2>别的模块挂「放行以后」的副作用</h2>
 * {@link #defer} 的 {@code onPaid} 在同一下伤害生效时调用（同一条记录里先扣它自己的 charges 再调）；同一下的几条记录按登记先后执行，
 * 所以宅男反伤（NORMAL 登记、charges 是空表）先于同一下的 MRU 花费（LOW 登记），先反伤再扣电。charges 可以是空表：宅男胸甲的反伤在混合端就这样挂，
 * 纯 Forge 上直接在自己的 LivingHurtEvent 监听器里做。注意本类在 LOW 取消了事件以后，同优先级或更低、不收已取消的监听器收不到这一下。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class MRUShield {

    /** 四件的份额加起来是这么多（原作 damageReduceAmount / 20） */
    public static final int TOTAL_SHARES = 20;
    /** 挡伤害时四件的顺序（头 / 胸 / 腿 / 靴）：身上储存物品不够付全部时，排前面的先付 */
    private static final ArmorItem.Type[] ORDER = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};
    /** 混合端等 LivingDamageEvent 的记录超过这么多条就先丢最早的（正常只有 0~2 条） */
    private static final int MAX_PENDING = 32;

    /**
     * 混合端上挡下的一件：哪个部位、花多少、是不是新覆盖面（扣的时候不认无尽晶体）。
     */
    public record Charge(ArmorItem.Type type, int cost, boolean finiteOnly) {

        /** 原来的挡法（无尽晶体照付） */
        public Charge(ArmorItem.Type type, int cost) {
            this(type, cost, false);
        }
    }

    /**
     * 一下伤害挡完的结果（只算护盾那部分）：剩下多少、吃掉多少。
     *
     * @param remaining 留给原版护甲和生命的伤害
     * @param absorbed  护盾吃掉的伤害
     */
    public record Outcome(float remaining, float absorbed) {
    }

    /** 混合端上算好、等伤害生效再扣的一下：玩家、伤害源（按对象比）、记账那一刻的 gameTime、各件花费、扣完以后要做的事 */
    private record Pending(Player player, DamageSource source, long tick, List<Charge> charges, @Nullable Runnable onPaid) {
    }

    /** 这一刻被护盾整下吃掉的一下（矩阵分流用，见 {@link #tookWholeHit}） */
    private record Whole(Player player, DamageSource source, long tick) {
    }

    /** 混合端等着扣的（只在服务端线程读写） */
    private static final List<Pending> PENDING = new ArrayList<>();
    /** 这一刻整下吃掉的（只在服务端线程读写；不是这一刻的下次登记时清掉） */
    private static final List<Whole> WHOLE = new ArrayList<>();

    private MRUShield() {
    }

    // ------------------------------------------------------------------
    // 份额与花费
    // ------------------------------------------------------------------

    /**
     * 挡下 damage 里一件（份额 share / 20）要花的 MRU：max(1, ⌊damage × share / 20⌋) × perPoint。
     * 原作 Forge 1.12 的 ISpecialArmor 按 {@code (int) max(1, 吸收量)} 调 damageArmor，所以每件至少按 1 点算。
     * 极大的伤害夹到 int 上限（扣不起就不挡，带着无尽 MRU 晶体时照付）。
     */
    public static int cost(float damage, int share, int perPoint) {
        return cost(damage, share, perPoint, 1D);
    }

    /**
     * 同上，份额可以是小数（PvP 限份额、无视护甲挡一半），单价再乘 priceMultiplier（同调阶的降价、新覆盖面的加价）：
     * max(1, ⌊damage × share / 20⌋) × round(perPoint × priceMultiplier)。
     */
    public static int cost(float damage, double share, int perPoint, double priceMultiplier) {
        double points = Math.max(1D, Math.floor((double) damage * share / TOTAL_SHARES));
        double price = Math.max(0L, Math.round(perPoint * priceMultiplier));
        return (int) Math.max(0D, Math.min(Integer.MAX_VALUE, points * price));
    }

    /** 这个部位穿着的 MRU 盔甲（实现了 {@link IMRUShieldArmor}、是这个部位的盔甲），没有就 null */
    @Nullable
    public static ItemStack wornPiece(LivingEntity entity, ArmorItem.Type type) {
        ItemStack stack = entity.getItemBySlot(type.getSlot());
        return stack.getItem() instanceof IMRUShieldArmor && stack.getItem() instanceof ArmorItem armor && armor.getType() == type ? stack : null;
    }

    /** 整下不挡的伤害：带 {@code bypasses_invulnerability}（{@code /kill}、掉出世界），或在 {@code essentialcraft5:unshieldable} 里 */
    public static boolean excluded(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(ECTags.UNSHIELDABLE);
    }

    /**
     * 这件这一下的挡法（{@link IMRUShieldArmor#coverage}），再替新覆盖面守「有攻击者、不是自己、不是玩家」；份额 ≤ 0 的当不挡。不看电。
     */
    @Nullable
    public static IMRUShieldArmor.Coverage coverageFor(Player player, ItemStack stack, DamageSource source) {
        if (!(stack.getItem() instanceof IMRUShieldArmor armor) || excluded(source)) {
            return null;
        }
        IMRUShieldArmor.Coverage coverage = armor.coverage(stack, source);
        if (coverage == null || !(coverage.share() > 0D)) {
            return null;
        }
        if (coverage.extended()) {
            Entity attacker = source.getEntity();
            if (attacker == null || attacker == player || attacker instanceof Player) {
                return null;
            }
        }
        return coverage;
    }

    /**
     * η 护身符翻倍多出来的部分（护盾不吸收，见类注释）：受害者戴着生效的 η、这一下不是饰品自己打出的，就是到这里时伤害的一半，否则 0。
     * 条件和 {@code item/bauble/BaubleCombatEvents.onBaubleLivingHurt} 里「受到」那一支一致（同在 LivingHurtEvent、它在 NORMAL），两处要一起改。
     * η 之后别的 NORMAL 监听器按比例改伤害（矩阵分流、相性减伤）时，多出来的仍是一半。
     */
    public static float etaExtra(Player player, float amount) {
        if (BaubleEffects.isBaubleDamage() || !BaubleEffects.has(player, ECBaubles.ETA_AMULET.get())) {
            return 0F;
        }
        return amount / 2F;
    }

    // ------------------------------------------------------------------
    // 挨打
    // ------------------------------------------------------------------

    /** 有电就挡（LOW，不收已取消的；纯 Forge 当场扣，混合端只算、等 LivingDamageEvent 再扣） */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onMRUShieldAbsorb(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        float amount = event.getAmount();
        if (!(amount > 0F) || !Float.isFinite(amount)) {
            return;
        }
        DamageSource source = event.getSource();
        if (excluded(source)) {
            return;
        }
        float exempt = etaExtra(player, amount);
        float shieldable = amount - exempt;
        boolean hybrid = RayHitGate.isBukkitHybrid();
        List<Charge> charges = hybrid ? new ArrayList<>(ORDER.length) : null;
        Outcome outcome = settle(player, source, shieldable, charges);
        if (!(outcome.absorbed() > 0F)) {
            return;
        }
        float remaining = exempt + outcome.remaining();
        float absorbed = outcome.absorbed();
        if (hybrid) {
            defer(player, source, charges, () -> afterAbsorbed(player, source, amount, absorbed, remaining));
        }
        if (remaining <= 0F) {
            markWhole(player, source);
            event.setCanceled(true);
        } else {
            event.setAmount(remaining);
        }
        if (!hybrid) {
            afterAbsorbed(player, source, amount, absorbed, remaining);
        }
    }

    /** 扣过电以后：整下吃掉的替同调记盔甲点，再发 {@link MRUShieldAbsorbedEvent} */
    private static void afterAbsorbed(Player player, DamageSource source, float incoming, float absorbed, float remaining) {
        if (remaining <= 0F) {
            AttunementGains.armorHitAbsorbed(player, source);
        }
        MinecraftForge.EVENT_BUS.post(new MRUShieldAbsorbedEvent(player, source, incoming, absorbed, remaining));
    }

    /**
     * 纯 Forge：有电的几件挡下 damage 里各自那一份、当场扣 MRU，返回剩下的伤害（挡满时 0）。
     * 扣不起的那件不挡也不扣；{@link #excluded} 的整下不挡。damage 整个都当可挡的（不看 η，η 的扣除在 {@link #onMRUShieldAbsorb} 里）。
     */
    public static float absorb(Player player, DamageSource source, float damage) {
        return settle(player, source, damage, null).remaining();
    }

    /**
     * 混合端：同 {@link #absorb}，但只算不扣，哪几件挡、各花多少放进 out，返回剩下的伤害。
     * 身上储存物品、矩阵容量的合计当预算一笔笔减，凑不够的那件看它自己存的够不够——和依次调 {@link #pay} 的结论一样，
     * 不会几件重复算同一份储存物品。
     */
    public static float plan(Player player, DamageSource source, float damage, List<Charge> out) {
        return settle(player, source, damage, out).remaining();
    }

    /**
     * 挡一下：out 为 null 时当场扣（纯 Forge），不为 null 时只算、把各件花费放进去（混合端）。
     */
    public static Outcome settle(Player player, DamageSource source, float damage, @Nullable List<Charge> out) {
        if (excluded(source) || !(damage > 0F)) {
            return new Outcome(Math.max(0F, damage), 0F);
        }
        Budget budget = out != null ? Budget.of(player) : null;
        double blocked = 0D;
        for (ArmorItem.Type type : ORDER) {
            ItemStack stack = wornPiece(player, type);
            // 原作 getProperties：这件自己没电时吸收比例是 0，不会去扣身上的储存物品
            if (stack == null) {
                continue;
            }
            int own = MRUStaffItem.storedMRU(stack);
            if (own <= 0) {
                continue;
            }
            IMRUShieldArmor.Coverage coverage = coverageFor(player, stack, source);
            if (coverage == null) {
                continue;
            }
            IMRUShieldArmor armor = (IMRUShieldArmor) stack.getItem();
            int cost = cost(damage, coverage.share(), armor.mruPerDamage(stack), coverage.priceMultiplier());
            boolean finiteOnly = coverage.extended();
            if (budget == null) {
                if (pay(player, stack, cost, finiteOnly)) {
                    blocked += coverage.share();
                }
            } else if (budget.take(cost, finiteOnly, own)) {
                out.add(new Charge(type, cost, finiteOnly));
                blocked += coverage.share();
            }
        }
        float remaining = remainingAfter(damage, blocked);
        return new Outcome(remaining, damage - remaining);
    }

    /** 挡下 blocked 份（二十分之几）以后剩下的伤害 */
    private static float remainingAfter(float damage, double blocked) {
        if (blocked >= TOTAL_SHARES) {
            return 0F;
        }
        return (float) (damage * (TOTAL_SHARES - blocked) / TOTAL_SHARES);
    }

    // ------------------------------------------------------------------
    // 扣电：身上储存物品 → 矩阵容量 → 这件自己的
    // ------------------------------------------------------------------

    /**
     * 替护盾扣 cost MRU：创造模式不扣；身上带着（没被禁用的）无尽 MRU 晶体时全由它付（finiteOnly 时不认它）；
     * 否则身上别的储存物品加矩阵容量凑得够就按「饰品栏 → 盔甲 → 背包 → 副手 → 容量」扣；再不够看 own 自己存的够不够。
     * 实现在 {@code mru/MRUDrain.payShield}（和别的花费共用一份收集储存物品的代码），这里不打魔力相性的折扣（护盾价钱另有同调阶的降价）。
     *
     * @return 扣成了
     */
    public static boolean pay(Player player, ItemStack own, int cost, boolean finiteOnly) {
        return MRUDrain.payShield(player, own, cost, finiteOnly);
    }

    /** 混合端「只算」用的预算：创造不限、无尽晶体（只付原来的挡法）、别的储存物品加矩阵容量的合计 */
    private static final class Budget {
        private final boolean unlimited;
        private final boolean infinite;
        private long finite;

        private Budget(boolean unlimited, boolean infinite, long finite) {
            this.unlimited = unlimited;
            this.infinite = infinite;
            this.finite = finite;
        }

        static Budget of(Player player) {
            if (player.getAbilities().instabuild) {
                return new Budget(true, true, Long.MAX_VALUE);
            }
            return new Budget(false, MRUDrain.hasInfiniteStorage(player), MRUDrain.finiteFromStorages(player));
        }

        /** 这一笔付不付得起（和依次调 {@link #pay} 的结论一样）；付得起时从合计里减掉 */
        boolean take(int cost, boolean finiteOnly, int own) {
            if (cost <= 0 || unlimited || (infinite && !finiteOnly)) {
                return true;
            }
            if (finite >= cost) {
                finite -= cost;
                return true;
            }
            return own >= cost;
        }
    }

    // ------------------------------------------------------------------
    // 整下吃掉的记号（矩阵分流用）
    // ------------------------------------------------------------------

    private static void markWhole(Player player, DamageSource source) {
        long now = player.level().getGameTime();
        if (!WHOLE.isEmpty()) {
            WHOLE.removeIf(whole -> whole.tick() != now);
            if (WHOLE.size() >= MAX_PENDING) {
                WHOLE.remove(0);
            }
        }
        WHOLE.add(new Whole(player, source, now));
    }

    /**
     * 这一刻这一下（同一个玩家、同一个伤害源对象）被护盾整下吃掉了（LOW 里取消了事件）。纯 Forge 上这时已经扣过电；
     * 混合端上只是算好了，等 {@code LivingDamageEvent} 到了才扣，插件拦下就不扣。
     */
    public static boolean tookWholeHit(Player player, DamageSource source) {
        if (WHOLE.isEmpty()) {
            return false;
        }
        long now = player.level().getGameTime();
        for (Whole whole : WHOLE) {
            if (whole.player() == player && whole.source() == source && whole.tick() == now) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 混合端：待付
    // ------------------------------------------------------------------

    /**
     * 混合端：记一条待付，等这一下（同一个玩家、同一个伤害源对象、同一刻）的 {@code LivingDamageEvent} 到了再扣 charges、再调 onPaid。
     * 插件拦下的攻击到不了 LivingDamageEvent，记录下一次记账时清掉，什么都不做。charges 可以是空表（只挂 onPaid）。
     */
    public static void defer(Player player, DamageSource source, List<Charge> charges, @Nullable Runnable onPaid) {
        long now = player.level().getGameTime();
        if (!PENDING.isEmpty()) {
            // 不是这一刻的都是被插件拦下、走不到 LivingDamageEvent 的，丢掉不扣
            PENDING.removeIf(pending -> pending.tick() != now);
            if (PENDING.size() >= MAX_PENDING) {
                PENDING.remove(0);
            }
        }
        PENDING.add(new Pending(player, source, now, List.copyOf(charges), onPaid));
    }

    /**
     * 混合端：这一下的伤害生效了（插件放行），按 LivingHurtEvent 时算好的扣，再做挂上的副作用。
     * 被盾牌整下挡掉的（{@link ECShieldBlocks#fullyShielded}）只删记录、不扣不做：混合端在 LivingHurtEvent 之后才算举盾，挡满照样发
     * {@code LivingDamageEvent(0)}；纯 Forge 举盾在 LivingHurtEvent 之前就把伤害扣成 0，盔甲不挡不扣、宅男反伤不反，两边对齐。
     * <p>
     * 不看这一下的 LivingHurtEvent 有没有被取消（和 ζ / ι / ξ 不同）：挡满时取消它的就是 {@link #onMRUShieldAbsorb} 自己，看了就成了挡满白挡、
     * 不扣电，宅男反伤也会在挡满时不反；而纯 Forge 在 LOW / NORMAL 里当场扣电、当场反伤，排在后面的模组再取消也已经扣了、反了，混合端现在和它一致。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onMRUShieldPayDeferred(LivingDamageEvent event) {
        if (PENDING.isEmpty() || !(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        DamageSource source = event.getSource();
        boolean shielded = ECShieldBlocks.fullyShielded(player, source, event.getAmount());
        long now = player.level().getGameTime();
        List<Pending> matched = null;
        for (int i = 0; i < PENDING.size(); i++) {
            Pending pending = PENDING.get(i);
            if (pending.player() == player && pending.source() == source) {
                if (matched == null) {
                    matched = new ArrayList<>(2);
                }
                matched.add(pending);
                PENDING.remove(i--);
            }
        }
        if (matched == null) {
            return;
        }
        for (Pending pending : matched) {
            if (pending.tick() != now || shielded) {
                continue;
            }
            for (Charge charge : pending.charges()) {
                ItemStack stack = wornPiece(player, charge.type());
                if (stack != null) {
                    pay(player, stack, charge.cost(), charge.finiteOnly());
                }
            }
            if (pending.onPaid() != null) {
                pending.onPaid().run();
            }
        }
    }

    /** GameTest 用：混合端上这一下（同一个玩家、同一个伤害源对象）算好了、还在等 LivingDamageEvent 的花费（几条记录合在一起）；没有就空表 */
    public static List<Charge> pendingCharges(Player player, DamageSource source) {
        List<Charge> out = new ArrayList<>();
        for (Pending pending : PENDING) {
            if (pending.player() == player && pending.source() == source) {
                out.addAll(pending.charges());
            }
        }
        return out;
    }

    /** GameTest 用：这一下还有没有待付记录（包括只挂了 onPaid 的） */
    public static boolean hasPending(Player player, DamageSource source) {
        for (Pending pending : PENDING) {
            if (pending.player() == player && pending.source() == source) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onMRUShieldServerStopping(ServerStoppingEvent event) {
        PENDING.clear();
        WHOLE.clear();
    }
}
