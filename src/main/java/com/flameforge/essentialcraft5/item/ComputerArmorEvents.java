package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.gun.RayHitGate;
import com.flameforge.essentialcraft5.item.armor.MRUShield;
import com.flameforge.essentialcraft5.item.bauble.BaubleEffects;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.network.C2SComputerArmorPacket;
import com.flameforge.essentialcraft5.network.S2CBoardFlyingPacket;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 宅男套装四件各自的效果、全套的反伤 / 游泳 / 喷射推进、头盔夜视（第八批 M2，batch8.md §4.2；定法 D5 = B、D6 = A）。都只对玩家；
 * 起跳加速两端都算（同风之护腿），其余只在服务端。挡伤害不在这里，在 {@link MRUShield}（{@link ComputerArmorItem} 实现 IMRUShieldArmor）。
 * 四件的效果都不看电量（原作如此），只有挡伤害要电。
 *
 * <h2>挨打</h2>
 * <ul>
 *   <li><b>胸甲免疫</b>（{@link LivingAttackEvent} HIGH）：{@code magic}、{@code wither}、{@code starve} 三种原版伤害整下取消（原作 PlayerTracker L111~116
 *       按这三个伤害源取消）。不含 {@code indirect_magic}：别人扔的喷溅伤害药水照样打得到；自己喝下的伤害药水、中毒是 {@code magic}，免疫。
 *       放在攻击事件里取消：不红闪、不出受击声、不设无敌帧（原作在 LivingHurtEvent 里取消，凋零效果每跳一下都闪一次）。</li>
 *   <li><b>靴子免摔</b>（同上）：{@code #minecraft:is_fall}（摔落、落到朝上的滴水石锥上，以及别的模组带这个标签的摔伤）整下取消
 *       （原作 PlayerTracker L122~127 设 0，另外每刻清摔落距离；1.12 没有滴水石锥）。</li>
 *   <li><b>全套反伤</b>（{@link LivingHurtEvent} NORMAL，不收已取消的）：有攻击者（打出这一下的实体，弹射物算射手）、<b>有直接实体</b>、不是自己、不是荆棘类伤害、
 *       不是饰品 / 反伤自己打出的（{@link BaubleEffects#isBaubleDamage}），就用 {@code damageSources().thorns(穿戴者)} 打攻击者
 *       「这一下 × {@code thorns_ratio}」（默认 0.5，不到 0.5 点不反，原作 ItemComputerArmor L160~181）。
 *       近战、弹射物、TNT、火球、苦力怕这类有直接实体的照反；轨道打击、射线枪（{@code mru_ray}）只记了攻击者、直接实体是空的，不反——
 *       原作只认真源头，这两种伤害在原作里没有真源头，从来不反；补上攻击者是为了让 PvP、队伍、领地照攻击者管，不是为了让它们吃反伤
 *       （不然遥控器打一下穿全套的人，自己挨回 64 点荆棘伤害）。反伤在 {@link BaubleEffects#asReflectDamage}
 *       里打出（同时算饰品伤害）：对方的 σ 不会再弹回来，对方也穿全套时不会再反（原作两个穿全套的人互打会来回弹）；
 *       交手记录不把它当成穿戴者动过手，削减模块也不给它叠破甲。
 *       <br>只在攻击被放行以后反：纯 Forge 的领地模组在 LivingAttackEvent 就把攻击拦了，到不了这里（在 LivingHurtEvent 更低的优先级才取消这一下的模组
 *       很少见，那种情况反伤照样发生）；这里排在 {@link MRUShield}（LOW）之前，套装把这一下整下挡掉（取消事件）时照样反
 *       （原作挡伤害和反伤都在 ISpecialArmor 里，挡下的照样反）。反的数按这里看到的伤害算：同在 NORMAL 的 η 翻倍谁先谁后看监听器登记顺序。
 *       Mohist 系混合端上领地插件的伤害事件夹在 LivingHurtEvent 和 LivingDamageEvent 之间，就把反伤挂到 {@link MRUShield#defer} 的 onPaid 上，
 *       插件放行、LivingDamageEvent 到了才反；插件拦下就什么都不发生。原作不看电、不看攻击拦没拦下。</li>
 * </ul>
 *
 * <h2>每 {@value #CHECK_INTERVAL} 刻（{@link TickEvent.PlayerTickEvent}，服务端）</h2>
 * 都是「有变化才写」：
 * <ul>
 *   <li>头盔：氧气补满；饥饿值保持满（原作每刻 {@code addStats(1, 1)}，饱和度也一直顶满 = 一直每秒回 2 点；默认只保持饥饿值满，
 *       回血按原版满饥饿的慢速，{@code helmet_saturation} 开着时饱和度也顶满）；夜视按开关维持（见下）。</li>
 *   <li>护腿：着火时给 5 秒抗火（原作 PlayerTickHandler L436~440，每 20 刻）。</li>
 *   <li>全套：游泳速度修饰符和配置对账（换装时已经加上 / 去掉，这里只补配置热改）。</li>
 *   <li>反重力板：本模组给着它的飞行、身上却没有能用的板子时撤掉（{@link ComputerBoardItem#refresh}；正常摘下时 Curios 已经调过 onUnequip）。</li>
 * </ul>
 *
 * <h2>夜视（{@link C2SComputerArmorPacket#NIGHT_VISION}）</h2>
 * 戴着头盔按键切换开关（开关记在玩家的 Forge 持久数据 PlayerPersisted 下的 {@value #NIGHT_VISION_TAG}，是个人偏好：重登、死亡、从末地回来都还在）。开着时服务端给一个隐藏图标、不出粒子的
 * 夜视 {@value #NIGHT_VISION_TICKS} 刻，剩不到 {@value #NIGHT_VISION_RENEW_BELOW} 刻才续（夜视剩 200 刻以下画面会闪；客户端自己也在倒数，留出余量，见常量注释）；
 * 身上已有的夜视是无限时长的、放大等级 &gt; 0 的，或者还剩得多的，一律不碰（续的时候 {@code addEffect} 会把它们的图标改成隐藏）。
 * 给的时候在持久数据<b>根上</b>记一个「给过」的标记 {@value #NIGHT_VISION_GIVEN_TAG}（不放 PlayerPersisted 下：死亡时效果清掉，标记也不该跟过去）。
 * 关掉或摘下头盔时只删自己给的那份：有这个标记，并且身上的夜视是隐藏图标、不出粒子、有限时长、不超过 {@value #NIGHT_VISION_TICKS} 刻的，才删；删的同时清掉标记。
 * 从没给过的玩家（没有标记）一律不碰：{@code /effect give … night_vision 15 0 true}、别的模组的夜视镜给的隐藏夜视长得和这份一模一样，只按特征认会被每秒删一次。
 * 喝的夜视药水、信标给的都显示图标，不动。原作是客户端本地的永久效果，喝奶清不掉、换维度残留。
 *
 * <h2>喷射推进（{@link C2SComputerArmorPacket#DASH}）</h2>
 * 穿全套、没在骑乘 / 睡觉、不是旁观者、冷却 {@code dash_cooldown} 刻已过、付得起 {@code dash_mru}（默认 0，先扣身上的储存物品、再扣胸甲自己的）时，
 * 把速度<b>设</b>成视线方向 × {@code dash_strength}（默认 3 格 / 刻）、标记同步给客户端，放一声烟花爆炸。
 * 原作在客户端把视线方向 × 3 <b>加</b>到原速度上、一次放 10 声（RenderHandlerEC L746~760），和下落、鞘翅的速度叠出更大的值；改过的客户端还能无冷却连冲。
 * 包本身不带别的东西；切换夜视的包同一个玩家 {@value #TOGGLE_MIN_INTERVAL} 刻内只认一次。
 *
 * <h2>反重力板的飞行状态（{@link S2CBoardFlyingPacket}）</h2>
 * 原版不把别人的 {@code abilities.flying} 发给旁人，客户端以前只能按「不在地上、没在往下掉」估，起跳、爬梯子、缓降时脚下都会闪出一块板。
 * 现在服务端每刻看一眼（{@link ComputerBoardItem#isFlyingOnBoard}：在飞、并且反重力板给着飞行），变了才发给看得到这名玩家的客户端；
 * 有人开始追踪这名玩家时（{@link PlayerEvent.StartTracking}）单独补发一次现在的状态。本人不发，客户端自己看能力。
 *
 * <h2>换装</h2>
 * 玩家换装时重算全套游泳加成；摘下头盔（换成别的物品，不算同一件的 MRU 变化）时删掉自己给的夜视。
 * 脱下以后夹生命（含换成同种低阶的一件）由 {@link com.flameforge.essentialcraft5.item.armor.ArmorTakeoffClamp} 统一管。
 * <p>
 * 原作用两张 {@code HashMap<EntityPlayer, …>} 记「穿没穿」，从不清理（B11）；这里全套与否每次现查四个盔甲槽，不留表。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class ComputerArmorEvents {

    /** 按键名（客户端登记同名的 KeyMapping；提示框用 {@code Component.keybind} 显示玩家现在绑的键） */
    public static final String KEY_BOOST = "key.essentialcraft5.computer_armor_boost";
    public static final String KEY_NIGHT_VISION = "key.essentialcraft5.computer_armor_night_vision";

    /** 头盔、护腿、配置对账的检查间隔（刻） */
    public static final int CHECK_INTERVAL = 20;
    /** 夜视一次给多少刻 */
    public static final int NIGHT_VISION_TICKS = 400;
    /**
     * 夜视剩不到这么多刻就续。400 起每 20 刻查一次，剩 280 时续；原版剩 200 刻以下开始闪（GameRenderer 里 endsWithin(200)），
     * 客户端自己也在倒数、要等续期包到了才回到 400，留 80 刻余量：服务端卡到 12 TPS 左右（客户端照样每秒倒数 20 刻）也不会降到 200 以下
     */
    public static final int NIGHT_VISION_RENEW_BELOW = 300;
    /** 护腿着火时给的抗火（原作 PotionEffect(FIRE_RESISTANCE, 100, 0, true, true)） */
    public static final int FIRE_RESISTANCE_TICKS = 100;
    /** 夜视开关包同一个玩家最短间隔（刻） */
    public static final int TOGGLE_MIN_INTERVAL = 5;
    /** 反伤算出来不到这么多就不反（原作 newDamage &lt; 0.5 时设 0） */
    public static final float MIN_THORNS = 0.5F;
    /** 夜视开关在玩家持久数据里的键（PlayerPersisted 下） */
    public static final String NIGHT_VISION_TAG = "essentialcraft5:computer_night_vision";
    /** 「本模组给过夜视」的标记在玩家持久数据里的键（根上，死亡不跟过去） */
    public static final String NIGHT_VISION_GIVEN_TAG = "essentialcraft5:computer_nv_given";
    /** 全套游泳加成的修饰符 UUID（固定，换装时按它去掉） */
    public static final UUID SWIM_MODIFIER_ID = UUID.nameUUIDFromBytes("essentialcraft5:computer_armor/swim_speed".getBytes(StandardCharsets.UTF_8));

    /** 上次冲刺、上次切换夜视的游戏时间（按玩家对象弱引用，下线、重生就清掉；只在服务端线程读写） */
    private static final Map<Player, Long> LAST_DASH = new WeakHashMap<>();
    private static final Map<Player, Long> LAST_TOGGLE = new WeakHashMap<>();

    private ComputerArmorEvents() {
    }

    // ------------------------------------------------------------------
    // 穿没穿
    // ------------------------------------------------------------------

    /** 这一格是不是这个部位的宅男盔甲 */
    public static boolean isPiece(ItemStack stack, ArmorItem.Type type) {
        return stack.getItem() instanceof ComputerArmorItem armor && armor.getType() == type;
    }

    /** 这个部位穿着宅男盔甲 */
    public static boolean wears(LivingEntity entity, ArmorItem.Type type) {
        return isPiece(entity.getItemBySlot(type.getSlot()), type);
    }

    /** 四件都穿着（原作 hasFullset，不看电量） */
    public static boolean hasFullSet(LivingEntity entity) {
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            if (!wears(entity, type)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 挨打
    // ------------------------------------------------------------------

    /** 胸甲免疫 magic / wither / starve，靴子免摔（攻击事件里整下取消） */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onComputerArmorAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (isImmune(player, event.getSource())) {
            event.setCanceled(true);
        }
    }

    /** 穿着的宅男盔甲让这一下整下免疫（胸甲：magic / wither / starve；靴子：#is_fall，摔落和落到滴水石锥上） */
    public static boolean isImmune(Player player, DamageSource source) {
        if (wears(player, ArmorItem.Type.CHESTPLATE)
                && (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.WITHER) || source.is(DamageTypes.STARVE))) {
            return true;
        }
        return wears(player, ArmorItem.Type.BOOTS) && source.is(DamageTypeTags.IS_FALL);
    }

    /** 全套反伤（见类注释：NORMAL、不收已取消的，排在 MRUShield 之前；混合端挂到 MRUShield.defer 上） */
    @SubscribeEvent
    public static void onComputerArmorThorns(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide || BaubleEffects.isBaubleDamage()) {
            return;
        }
        DamageSource source = event.getSource();
        if (!reflects(source) || !(source.getEntity() instanceof LivingEntity attacker) || attacker == player || !hasFullSet(player)) {
            return;
        }
        float amount = thornsAmount(event.getAmount(), ECComputer.Config.values().thornsRatio());
        if (amount <= 0F) {
            return;
        }
        if (RayHitGate.isBukkitHybrid()) {
            MRUShield.defer(player, source, List.of(), () -> reflect(player, attacker, amount));
        } else {
            reflect(player, attacker, amount);
        }
    }

    /**
     * 这种伤害反不反（不看攻击者是谁）：荆棘类不反；没有直接实体的不反（轨道打击、射线枪只记了攻击者，原作没有真源头、从来不反，见类注释）
     */
    public static boolean reflects(DamageSource source) {
        return !source.is(DamageTypes.THORNS) && source.getDirectEntity() != null;
    }

    /** 反给攻击者多少：伤害 × 比例，不到 {@value #MIN_THORNS} 算 0 */
    public static float thornsAmount(float damage, double ratio) {
        if (!(damage > 0F) || !Float.isFinite(damage) || !(ratio > 0D)) {
            return 0F;
        }
        float amount = (float) (damage * ratio);
        return amount < MIN_THORNS || !Float.isFinite(amount) ? 0F : amount;
    }

    /** 用荆棘伤害（攻击者 = 穿戴者）打攻击者；对方已经死了、移除了、不在同一个世界就算了 */
    private static void reflect(Player wearer, LivingEntity attacker, float amount) {
        if (!attacker.isAlive() || attacker.isRemoved() || attacker.level() != wearer.level()) {
            return;
        }
        boolean hurt = BaubleEffects.asReflectDamage(() -> attacker.hurt(wearer.damageSources().thorns(wearer), amount));
        if (hurt) {
            // 原作放一声 0.2 音量的 lightning_hit（ECSounds.LIGHTNING_HIT 指原版声音，第四批 F4-14）
            wearer.level().playSound(null, wearer.getX(), wearer.getY(), wearer.getZ(), ECSounds.LIGHTNING_HIT.get(), SoundSource.PLAYERS, 0.2F, 1F);
        }
    }

    // ------------------------------------------------------------------
    // 护腿起跳（两端）
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onComputerArmorJump(LivingEvent.LivingJumpEvent event) {
        // 按类判断，不碰 ECComputer 的字段（同风之护腿的写法）
        if (event.getEntity() instanceof Player player && wears(player, ArmorItem.Type.LEGGINGS)) {
            double boost = ECComputer.Config.values().leggingsJump();
            if (boost > 0D) {
                Vec3 motion = player.getDeltaMovement();
                player.setDeltaMovement(motion.x, motion.y + boost, motion.z);
            }
        }
    }

    // ------------------------------------------------------------------
    // 每 20 刻
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onComputerArmorPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        periodic(player);
    }

    /** 每 {@value #CHECK_INTERVAL} 刻的检查（GameTest 里的玩家不 tick，直接调） */
    public static void periodic(ServerPlayer player) {
        ECComputer.Config.Values values = ECComputer.Config.values();
        boolean helmet = wears(player, ArmorItem.Type.HELMET);
        if (helmet) {
            if (player.getAirSupply() < player.getMaxAirSupply()) {
                player.setAirSupply(player.getMaxAirSupply());
            }
            FoodData food = player.getFoodData();
            if (food.getFoodLevel() < 20) {
                food.setFoodLevel(20);
            }
            if (values.helmetSaturation() && food.getSaturationLevel() < food.getFoodLevel()) {
                food.setSaturation(food.getFoodLevel());
            }
        }
        if (helmet && nightVisionOn(player)) {
            keepNightVision(player);
        } else {
            removeOwnNightVision(player);
        }
        if (wears(player, ArmorItem.Type.LEGGINGS) && player.isOnFire()) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, FIRE_RESISTANCE_TICKS, 0, true, true));
        }
        refreshSwimSpeed(player);
        ComputerBoardItem.refreshIfGranted(player);
    }

    // ------------------------------------------------------------------
    // 夜视
    // ------------------------------------------------------------------

    /** 夜视开关（玩家持久数据的 PlayerPersisted 下，死亡复制玩家时 Forge 会带过去） */
    public static boolean nightVisionOn(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean(NIGHT_VISION_TAG);
    }

    private static void setNightVision(Player player, boolean on) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (on) {
            persisted.putBoolean(NIGHT_VISION_TAG, true);
        } else {
            persisted.remove(NIGHT_VISION_TAG);
        }
        root.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /**
     * 这个夜视长得像不像本模组给的：隐藏图标、不出粒子、有限时长、不超过一次给的长度（喝的、信标的都显示图标；本模组从不给无限时长的）。
     * 只看特征，删之前还要看「给过」的标记（{@link #nightVisionGiven}）
     */
    public static boolean isOwnNightVision(@Nullable MobEffectInstance effect) {
        return effect != null && effect.getEffect() == MobEffects.NIGHT_VISION && !effect.isVisible() && !effect.showIcon()
                && !effect.isInfiniteDuration() && effect.getDuration() <= NIGHT_VISION_TICKS;
    }

    /** 本模组给过这名玩家夜视、还没收回（玩家持久数据根上的标记） */
    public static boolean nightVisionGiven(Player player) {
        return player.getPersistentData().getBoolean(NIGHT_VISION_GIVEN_TAG);
    }

    /**
     * 开着时维持：没有夜视、或剩不到 {@value #NIGHT_VISION_RENEW_BELOW} 刻才给。身上的夜视是无限时长的、放大等级 &gt; 0 的、还剩得多的都不碰：
     * {@code addEffect} 合并时不管时长变没变都会把旧效果的图标改成新给的隐藏图标，无限时长的随后还会被当成自己那份删掉
     */
    private static void keepNightVision(Player player) {
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (current != null && (current.isInfiniteDuration() || current.getAmplifier() > 0 || current.getDuration() >= NIGHT_VISION_RENEW_BELOW)) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, false, false, false));
        player.getPersistentData().putBoolean(NIGHT_VISION_GIVEN_TAG, true);
    }

    /** 只删自己给的那份：没给过就不碰；给过就清掉标记，身上那份还像自己给的才删 */
    private static void removeOwnNightVision(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.getBoolean(NIGHT_VISION_GIVEN_TAG)) {
            return;
        }
        root.remove(NIGHT_VISION_GIVEN_TAG);
        if (isOwnNightVision(player.getEffect(MobEffects.NIGHT_VISION))) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    // ------------------------------------------------------------------
    // 包
    // ------------------------------------------------------------------

    /** C2SComputerArmorPacket 的服务端处理（ECComputer 登记）：只认两种意图，别的忽略 */
    public static void handlePacket(ServerPlayer player, byte action) {
        if (action == C2SComputerArmorPacket.NIGHT_VISION) {
            toggleNightVision(player);
        } else if (action == C2SComputerArmorPacket.DASH) {
            tryDash(player);
        }
    }

    /**
     * 切换夜视：要戴着头盔、不是旁观者，同一个玩家 {@value #TOGGLE_MIN_INTERVAL} 刻内只认一次。切换后立刻给上或删掉，动作栏提示一行。
     *
     * @return 是否切换了
     */
    public static boolean toggleNightVision(ServerPlayer player) {
        if (player.isSpectator() || !wears(player, ArmorItem.Type.HELMET)) {
            return false;
        }
        long now = player.level().getGameTime();
        Long last = LAST_TOGGLE.get(player);
        if (last != null && now - last >= 0 && now - last < TOGGLE_MIN_INTERVAL) {
            return false;
        }
        LAST_TOGGLE.put(player, now);
        boolean on = !nightVisionOn(player);
        setNightVision(player, on);
        if (on) {
            keepNightVision(player);
        } else {
            removeOwnNightVision(player);
        }
        player.displayClientMessage(Component.translatable(on ? "message.essentialcraft5.computer_armor.night_vision_on"
                : "message.essentialcraft5.computer_armor.night_vision_off").withStyle(ChatFormatting.DARK_AQUA), true);
        return true;
    }

    /**
     * 喷射推进（见类注释）。
     *
     * @return 是否冲出去了
     */
    public static boolean tryDash(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.isPassenger() || player.isSleeping() || !hasFullSet(player)) {
            return false;
        }
        ECComputer.Config.Values values = ECComputer.Config.values();
        long now = player.level().getGameTime();
        Long last = LAST_DASH.get(player);
        if (last != null && now - last >= 0 && now - last < values.dashCooldown()) {
            return false;
        }
        if (values.dashMru() > 0 && !MRUDrain.playerUseMRU(player, player.getItemBySlot(EquipmentSlot.CHEST), values.dashMru())) {
            MRUStaffItem.tellNoMRU(player, values.dashMru());
            return false;
        }
        LAST_DASH.put(player, now);
        player.setDeltaMovement(player.getLookAngle().scale(values.dashStrength()));
        player.hurtMarked = true;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS,
                1F, 0.5F + player.getRandom().nextFloat());
        return true;
    }

    // ------------------------------------------------------------------
    // 换装：游泳加成、摘头盔删夜视
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onComputerArmorChanged(LivingEquipmentChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || event.getSlot().getType() != EquipmentSlot.Type.ARMOR) {
            return;
        }
        ItemStack from = event.getFrom();
        // 同一种物品（只是 MRU、阶之类的 NBT 变了）不算摘下
        boolean removed = from.getItem() instanceof ComputerArmorItem && !event.getTo().is(from.getItem());
        if (entity instanceof Player player) {
            refreshSwimSpeed(player);
            if (removed && event.getSlot() == EquipmentSlot.HEAD) {
                removeOwnNightVision(player);
            }
        }
    }

    /** 全套游泳加成：穿全套且配置 &gt; 0 时有一条固定 UUID 的修饰符，数额和配置一致；否则去掉。有变化才动 */
    public static void refreshSwimSpeed(Player player) {
        AttributeInstance attribute = player.getAttribute(ForgeMod.SWIM_SPEED.get());
        if (attribute == null) {
            return;
        }
        double amount = hasFullSet(player) ? ECComputer.Config.values().swimSpeed() : 0D;
        AttributeModifier current = attribute.getModifier(SWIM_MODIFIER_ID);
        if (current != null && current.getAmount() == amount) {
            return;
        }
        if (current != null) {
            attribute.removeModifier(SWIM_MODIFIER_ID);
        }
        if (amount > 0D) {
            attribute.addTransientModifier(new AttributeModifier(SWIM_MODIFIER_ID, "essentialcraft5 computer_armor swim", amount,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    // ------------------------------------------------------------------
    // 反重力板：飞行状态同步给旁人（见类注释）
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onComputerBoardSyncTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            ComputerBoardItem.syncFlying(player);
        }
    }

    @SubscribeEvent
    public static void onComputerBoardStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer tracker && event.getTarget() instanceof ServerPlayer target) {
            ComputerBoardItem.sendFlyingState(tracker, target);
        }
    }

    // ------------------------------------------------------------------
    // 收尾
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onComputerArmorServerStopped(ServerStoppedEvent event) {
        LAST_DASH.clear();
        LAST_TOGGLE.clear();
        ComputerBoardItem.clearFlyingState();
    }
}
