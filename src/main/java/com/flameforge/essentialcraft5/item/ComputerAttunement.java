package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.item.armor.IMRUShieldArmor;
import com.flameforge.essentialcraft5.item.armor.MRUShieldAbsorbedEvent;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.attunement.AttunementStageEvent;
import com.flameforge.essentialcraft5.item.attunement.AttunementStageLines;
import com.flameforge.essentialcraft5.item.module.ModuleCache;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECAttunement;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 宅男套装的同调阶效果（第十三批 13a，batch13.md §4.4「宅男」；限制见 policy §4.16）。0 阶除了护盾挡玩家的份额和单价（D12，见下面「护盾」）以外和以前一样，
 * D12 是护盾在 PvP 里的规则、不是同调效果，所以 0 阶也生效（不然升阶反而让护盾对玩家变差）；数在 {@code general.computer_armor}
 * （{@link ECComputer.Config#attuned()}），几阶起生效是下面的常量。
 *
 * <table>
 *   <tr><th>阶</th><th>按哪件算</th><th>效果</th></tr>
 *   <tr><td>1</td><td>这一件</td><td>胸甲模块槽 +1；护盾每点 250 → 225</td></tr>
 *   <tr><td>2</td><td>这一件</td><td>护盾也挡有攻击者的魔法伤害（带不带 {@code bypasses_armor} 都算），每点 ×2 价</td></tr>
 *   <tr><td>3</td><td>头盔、护腿各自；修复看全套</td><td>头盔、护腿模块槽各 +1；全套最低一件 ≥ 3 时「矩阵修复」</td></tr>
 *   <tr><td>4</td><td>这一件</td><td>护盾每点 225 → 200；有攻击者的无视护甲伤害（不是魔法）挡自己份额的一半，每点 ×3 价</td></tr>
 *   <tr><td>5</td><td>靴子；屏障看全套</td><td>靴子模块槽 +1；全套最低一件 ≥ 5 时「过载屏障」</td></tr>
 * </table>
 * 按件算的（槽数、单价、覆盖面）各件只看自己的阶，混着阶穿时各挡各的；全套效果用 {@link Attunement#fullSetStage}（四件里最低的阶，没穿齐不算）。
 *
 * <h2>护盾（{@link #coverage}，由 {@link ComputerArmorItem#coverage} 转来）</h2>
 * 原来挡的（{@code shield_scope}，不含 {@code bypasses_armor}）照原来的份额挡，单价按阶打折；攻击者是<b>别的玩家</b>时每件只挡自己份额的
 * {@code pvp_shield_max_share}（默认一半，四件合计就是这一下最多挡一半）、单价 × {@code pvp_shield_cost_multiplier}（D12 A：按电量算光加价打不穿，
 * 限份额以后每下至少一半落到护甲和生命上）。2 阶的魔法、4 阶的无视护甲是新覆盖面（{@link IMRUShieldArmor.Coverage#extended()}）：
 * MRUShield 替它守「有攻击者、不是自己、不是玩家」和「不从无尽晶体扣」，所以腐化 #17 / #18、MRU 腐化这类 {@code magic()} 自伤照扣血。
 * 护甲破坏者按 0 阶原价扣（{@link ComputerArmorItem#mruPerDamage}）。
 *
 * <h2>矩阵修复（全套 3 阶）</h2>
 * {@code repair_delay}（120）刻没受伤（{@code LivingDamageEvent} 真的扣了血才算受伤；护盾整下吃掉的不算）以后，每秒回最大生命的 {@code repair_percent}（1%），
 * 每回 1 点花 {@code repair_mru_per_health}（500）MRU（身上储存物品 → 矩阵容量 → 胸甲自己的）。走 {@link LivingEntity#heal}：挂着腐蚀这类拦回血的效果照样回不了，
 * 按真回上去的量扣电，回不上去不扣。付不起整份就这一秒不回。计时用服务器刻数（各世界的 gameTime 不一样，换维度会错）。
 *
 * <h2>过载屏障（全套 5 阶）</h2>
 * 护盾一下挡下 ≥ 最大生命 × {@code overload_threshold}（25%）、冷却走完时展开：之后 {@code overload_duration}（20）刻里，
 * 除了 {@code bypasses_invulnerability}（{@code /kill}、掉出世界）以外的伤害在 {@code LivingAttackEvent} 里整下取消（是取消伤害事件、不是改生命）；
 * 触发的那一下照常结算。冷却 {@code overload_cooldown}（1 200）刻存在玩家数据 {@code attunement_overload_cd}，退出重进、死亡都不清（玩家数据自己每刻减）。
 * 这一下的攻击者是别的玩家时，免疫时长 × {@code general.attunement.pvp_effect_scale}（对玩家减半）；乘出来不到 1 刻就不展开，也不记冷却。展开时动作栏一句、一声响。
 * 混合端上 MRUShield 等这一下真的生效、扣过电才发 {@link MRUShieldAbsorbedEvent}，插件拦下的攻击不会白白触发。
 *
 * <h2>升阶当刻</h2>
 * 指令改了身上那件的阶（{@link AttunementStageEvent} 带玩家）时，当场作废模块快照；戴着头盔（宅男终端开得了）就马上回一份终端数据，开着的终端立刻看到新的格数。
 * 工作台升阶时没有玩家，穿上那一刻原版的换装事件会作废快照（{@code item/module/ModuleEvents}）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class ComputerAttunement {

    /** 胸甲多一格的阶 */
    public static final int CHEST_SLOT_STAGE = 1;
    /** 2 阶起挡魔法 */
    public static final int MAGIC_STAGE = 2;
    /** 头盔、护腿多一格的阶；全套到这一阶有矩阵修复 */
    public static final int HEAD_LEGS_SLOT_STAGE = 3;
    public static final int REPAIR_STAGE = 3;
    /** 4 阶起挡无视护甲 */
    public static final int PIERCING_STAGE = 4;
    /** 靴子多一格的阶；全套到这一阶有过载屏障 */
    public static final int BOOTS_SLOT_STAGE = 5;
    public static final int OVERLOAD_STAGE = 5;
    /** 矩阵修复每多少刻回一次 */
    public static final int REPAIR_INTERVAL = 20;

    /** 上次真的扣了血的服务器刻（按玩家对象弱引用；只在服务端线程读写） */
    private static final Map<Player, Long> LAST_HURT = new WeakHashMap<>();
    /** 过载屏障到哪一个服务器刻为止（不含） */
    private static final Map<Player, Long> BARRIER_UNTIL = new WeakHashMap<>();

    private ComputerAttunement() {
    }

    // ------------------------------------------------------------------
    // 阶
    // ------------------------------------------------------------------

    /** 全套的阶（四件最低的那件）；没穿齐 -1 */
    public static int fullSetStage(LivingEntity entity) {
        return Attunement.fullSetStage(entity, ECAttunement.COMPUTER_ARMOR);
    }

    /** 这个部位在这一阶多几格模块槽（只升不降：阶只会往上走，不会把装着的模块挤到格数以外） */
    public static int slotBonus(ArmorItem.Type type, int stage) {
        return switch (type) {
            case CHESTPLATE -> stage >= CHEST_SLOT_STAGE ? 1 : 0;
            case HELMET, LEGGINGS -> stage >= HEAD_LEGS_SLOT_STAGE ? 1 : 0;
            case BOOTS -> stage >= BOOTS_SLOT_STAGE ? 1 : 0;
        };
    }

    /** 这件这一阶护盾每点的单价（0 阶原价 × 阶的倍数） */
    public static int price(ItemStack stack) {
        return ECComputer.Config.attuned().price(ECComputer.Config.values().mruPerDamage(), Attunement.stage(stack));
    }

    /**
     * 同调提示框里第 stage 阶那一行（{@link ComputerArmorItem#attunementStageLine}），单价、倍数、矩阵修复和过载屏障的数都按现在的配置填，
     * 和上面挡伤害、回血、展开屏障用的是同一套数；几阶起生效是这里的常量，照写。
     */
    @Nullable
    public static Component stageLine(int stage) {
        ECComputer.Config.Attuned cfg = ECComputer.Config.attuned();
        int base = ECComputer.Config.values().mruPerDamage();
        String prefix = "tooltip.essentialcraft5.attunement.computer_armor.stage_";
        return switch (stage) {
            case CHEST_SLOT_STAGE -> Component.translatable(prefix + stage, cfg.price(base, stage));
            case MAGIC_STAGE -> Component.translatable(prefix + stage, AttunementStageLines.number(cfg.magicPriceMultiplier()));
            case HEAD_LEGS_SLOT_STAGE -> Component.translatable(prefix + stage, AttunementStageLines.seconds(cfg.repairDelay()),
                    AttunementStageLines.percent(cfg.repairPercent()));
            case PIERCING_STAGE -> Component.translatable(prefix + stage, cfg.price(base, stage), AttunementStageLines.percent(cfg.piercingShare()),
                    AttunementStageLines.number(cfg.piercingPriceMultiplier()));
            case BOOTS_SLOT_STAGE -> Component.translatable(prefix + stage, AttunementStageLines.percent(cfg.overloadThreshold()),
                    AttunementStageLines.seconds(cfg.overloadDuration()), AttunementStageLines.seconds(cfg.overloadCooldown()));
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // 护盾覆盖
    // ------------------------------------------------------------------

    /**
     * 一件宅男盔甲这一下怎么挡（见类注释）；不挡返回 null。{@code bypasses_invulnerability}、{@code unshieldable} 由 MRUShield 先排除。
     */
    @Nullable
    public static IMRUShieldArmor.Coverage coverage(ItemStack stack, ArmorItem.Type type, DamageSource source) {
        int stage = Attunement.stage(stack);
        ECComputer.Config.Attuned cfg = ECComputer.Config.attuned();
        double price = cfg.priceMultiplier(stage);
        int share = ComputerArmorItem.share(type);
        boolean magic = ECDamageKinds.isMagic(source);
        if (!source.is(DamageTypeTags.BYPASSES_ARMOR) && ECComputer.Config.values().shieldScope().covers(source)) {
            IMRUShieldArmor.Coverage coverage = new IMRUShieldArmor.Coverage(share, price, false);
            return otherPlayer(source.getEntity(), stack, type) ? coverage.scaled(cfg.pvpShieldMaxShare(), cfg.pvpShieldCostMultiplier()) : coverage;
        }
        if (magic) {
            return stage >= MAGIC_STAGE ? new IMRUShieldArmor.Coverage(share, price * cfg.magicPriceMultiplier(), true) : null;
        }
        if (source.is(DamageTypeTags.BYPASSES_ARMOR) && stage >= PIERCING_STAGE) {
            return new IMRUShieldArmor.Coverage(share * cfg.piercingShare(), price * cfg.piercingPriceMultiplier(), true);
        }
        return null;
    }

    /** 攻击者是别的玩家（不是穿着这一件的人自己：自己点的 TNT、自己射的箭照原样挡） */
    private static boolean otherPlayer(@Nullable Entity attacker, ItemStack stack, ArmorItem.Type type) {
        return attacker instanceof Player player && player.getItemBySlot(type.getSlot()) != stack;
    }

    // ------------------------------------------------------------------
    // 计时（服务器刻）
    // ------------------------------------------------------------------

    private static long now(Player player) {
        MinecraftServer server = player.level().getServer();
        return server != null ? server.getTickCount() : player.level().getGameTime();
    }

    /** 真的扣了血（LOWEST、不收已取消的、&gt; 0）：矩阵修复从这一刻重新计时 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onComputerAttunedDamaged(LivingDamageEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide && event.getAmount() > 0F) {
            LAST_HURT.put(player, now(player));
        }
    }

    // ------------------------------------------------------------------
    // 矩阵修复
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onComputerAttunedTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player && player.tickCount % REPAIR_INTERVAL == 0) {
            repair(player);
        }
    }

    /**
     * 回一次血（每秒一次；GameTest 里的玩家不 tick，直接调）。
     *
     * @return 真回上去的生命（没回是 0）
     */
    public static float repair(Player player) {
        if (player.level().isClientSide || !player.isAlive() || player.isSpectator() || fullSetStage(player) < REPAIR_STAGE) {
            return 0F;
        }
        float max = player.getMaxHealth();
        float health = player.getHealth();
        if (!(health < max)) {
            return 0F;
        }
        ECComputer.Config.Attuned cfg = ECComputer.Config.attuned();
        Long last = LAST_HURT.get(player);
        if (last != null && now(player) - last < cfg.repairDelay()) {
            return 0F;
        }
        float amount = (float) Math.min(max * cfg.repairPercent(), max - health);
        if (!(amount > 0F)) {
            return 0F;
        }
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        int cost = cost(amount, cfg.repairMruPerHealth());
        if (!MRUDrain.canAfford(player, chest, cost)) {
            return 0F;
        }
        player.heal(amount);
        float healed = player.getHealth() - health;
        if (healed > 0F) {
            MRUDrain.playerUseMRU(player, chest, cost(healed, cfg.repairMruPerHealth()));
        }
        return Math.max(0F, healed);
    }

    /**
     * 回 amount 点生命的电费（向上取整）。生命是 float，回 0.1 点算出来是 0.1000004 这类数，先按千分之一点取整再乘，
     * 免得每秒多扣 1 MRU。
     */
    public static int cost(float amount, int perHealth) {
        double health = Math.round(amount * 1000D) / 1000D;
        return (int) Math.max(0D, Math.min(Integer.MAX_VALUE, Math.ceil(health * perHealth - 1.0E-9D)));
    }

    // ------------------------------------------------------------------
    // 过载屏障
    // ------------------------------------------------------------------

    /** 护盾挡下一下以后（MRUShield 扣过电才发） */
    @SubscribeEvent
    public static void onComputerShieldAbsorbed(MRUShieldAbsorbedEvent event) {
        tryOverload(event.getPlayer(), event.getSource(), event.getAbsorbed());
    }

    /**
     * 护盾刚挡下 absorbed 点：全套 5 阶、够最大生命的门槛、冷却走完就展开屏障。
     *
     * @return 展开了
     */
    public static boolean tryOverload(Player player, DamageSource source, float absorbed) {
        if (player.level().isClientSide || fullSetStage(player) < OVERLOAD_STAGE) {
            return false;
        }
        ECComputer.Config.Attuned cfg = ECComputer.Config.attuned();
        if (!(absorbed >= player.getMaxHealth() * cfg.overloadThreshold()) || cfg.overloadDuration() <= 0) {
            return false;
        }
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null || data.getAttunementOverloadCooldown() > 0) {
            return false;
        }
        int ticks = cfg.overloadDuration();
        Entity attacker = source.getEntity();
        if (attacker instanceof Player && attacker != player) {
            ticks = (int) Math.round(ticks * AttunementConfig.pvpEffectScale());
        }
        // 对玩家的倍数配成 0（或小到四舍五入成 0）时屏障根本展不开：这时不记冷却，接下来怪物打出的大伤害照样能展开
        if (ticks <= 0) {
            return false;
        }
        data.setAttunementOverloadCooldown(cfg.overloadCooldown());
        BARRIER_UNTIL.put(player, now(player) + ticks);
        player.displayClientMessage(Component.translatable("message.essentialcraft5.computer_armor.overload").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.6F);
        return true;
    }

    /** 过载屏障还开着 */
    public static boolean barrierActive(Player player) {
        if (BARRIER_UNTIL.isEmpty()) {
            return false;
        }
        Long until = BARRIER_UNTIL.get(player);
        return until != null && now(player) < until;
    }

    /** 屏障开着时整下取消（LivingAttackEvent HIGHEST；只放过 bypasses_invulnerability） */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onComputerOverloadAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide && blocksByBarrier(player, event.getSource())) {
            event.setCanceled(true);
        }
    }

    /** 不经过攻击事件、直接走到 LivingHurtEvent 的伤害（别的模组直接调 actuallyHurt 这类）也挡（HIGHEST） */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onComputerOverloadHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide && blocksByBarrier(player, event.getSource())) {
            event.setCanceled(true);
        }
    }

    private static boolean blocksByBarrier(Player player, DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && barrierActive(player);
    }

    // ------------------------------------------------------------------
    // 升阶当刻
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onComputerStageChanged(AttunementStageEvent event) {
        if (event.getFamily() != ECAttunement.COMPUTER_ARMOR || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ModuleCache.invalidate(player);
        if (ComputerArmorEvents.wears(player, ArmorItem.Type.HELMET)) {
            ComputerSealField.sendTerminal(player);
        }
    }

    // ------------------------------------------------------------------
    // 收尾、测试
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onComputerAttunedServerStopped(ServerStoppedEvent event) {
        LAST_HURT.clear();
        BARRIER_UNTIL.clear();
    }

    /** GameTest 用：把「上次受伤」挪到 ticksAgo 刻以前（null = 从没受过伤） */
    public static void setLastHurtForTest(Player player, @Nullable Integer ticksAgo) {
        if (ticksAgo == null) {
            LAST_HURT.remove(player);
        } else {
            LAST_HURT.put(player, now(player) - ticksAgo);
        }
    }

    /** GameTest 用：清掉屏障和受伤计时 */
    public static void forgetForTest(Player player) {
        LAST_HURT.remove(player);
        BARRIER_UNTIL.remove(player);
    }
}
