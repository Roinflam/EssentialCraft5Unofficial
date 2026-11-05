package com.flameforge.essentialcraft5.player;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.WindAffinity;
import com.flameforge.essentialcraft5.effect.ParanormalLightnessEffect;
import com.flameforge.essentialcraft5.registry.ECWind;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * 风之好感的「什么时候加」（原作 WindRelations.playerTick、PlayerTracker.onPlayerJump、捕风挂坠的 getModifier）。
 * 加多少、系数由框架的 {@link WindAffinity} 算；这里所有加好感都走 {@link #grant}（多了捕风挂坠的 15%）。
 *
 * <h2>风之眷顾以后的被动事件（服务端，原作 PlayerTickHandler 每 20 tick 才往下走，所以都是「每秒」判一次）</h2>
 * 等级系数 k = 身上轻盈的等级（没有轻盈时 1）：
 * <ul>
 *   <li>每秒 0.0002 × k：好感 +1000（× 系数），聊天栏「风怯生生地拂过你的头发……」；戴着 β 指环（{@link WindAffinity#WIND_BOON}）时换成四选一的元素增益
 *       （力量 II / 生命恢复 I / 饱和 I / 急迫 II，各 6 分钟）。平均约 83 分钟一次。</li>
 *   <li>每秒 0.001 × k 且正在疾跑：「风在背后推着你……」，好感 +1000，速度 XXXII 1 秒（戴抗性指环 {@link WindAffinity#WIND_RESISTANT} 免疫）。</li>
 *   <li>每次起跳 0.0003：「风将你向上托起……」，好感 +1000，跳跃提升 XIII 5 秒（同样可免疫）。</li>
 * </ul>
 * 捕风挂坠（{@link WindAffinity#WIND_CATCHER}）：每次加好感（上面这些、瓶中之风、风之药水）15% 塞给你一个受困之风，聊天栏「捕风挂坠捕获了一缕风……」。
 *
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li>β 指环的四选一：原作 {@code rndID == 2} 写了两遍，抽到 2 同时给饱和和急迫、抽到 3 什么都没有（「风」那支永远不触发）；现在 0~3 各一种（batch3.md §3.3 修复 4）。</li>
 *   <li>风的话原作写死英文，现在是翻译键（hardcoded_strings.md 已列）。</li>
 *   <li>原作起跳事件里对盔甲格调抗风接口时传错了物品（传的是同下标的饰品格）；现在按标签查盔甲四格和饰品，不会传错。</li>
 *   <li>轻盈的跳跃加成也在这里的起跳 / 摔落事件里做（见 {@link ParanormalLightnessEffect}）。</li>
 * </ul>
 * 开销：每名玩家每秒几次随机数；戴没戴某类饰品走 CuriosCompat 的每玩家缓存（只在真的触发时才查）。
 */
public final class WindRelations {

    /** 被动事件：每秒的基础几率（原作 0.0002F × mod） */
    public static final double PASSIVE_CHANCE = 0.0002D;
    /** 疾跑事件：每秒的基础几率（原作 0.001F × mod，还要正在疾跑） */
    public static final double SPRINT_CHANCE = 0.001D;
    /** 起跳事件：每次起跳的几率（原作 0.0003F，不乘轻盈） */
    public static final float JUMP_CHANCE = 0.0003F;
    /** 事件给的原始好感（× 系数，0.1 时实得 100） */
    public static final int EVENT_POINTS = 1000;
    /** 捕风挂坠每次加好感时给受困之风的几率（原作 nextFloat() <= 0.15F） */
    public static final float CATCHER_CHANCE = 0.15F;
    /** β 指环增益的时长（原作 7200 tick = 6 分钟） */
    public static final int BOON_TICKS = 7200;
    /** 被风推一把：速度 XXXII（amplifier 31）1 秒 */
    public static final int PUSH_AMPLIFIER = 31;
    public static final int PUSH_TICKS = 20;
    /** 被风托起：跳跃提升 XIII（amplifier 12）5 秒 */
    public static final int LIFT_AMPLIFIER = 12;
    public static final int LIFT_TICKS = 100;

    private WindRelations() {
    }

    // ---------------------------------------------------------------- 加好感

    /**
     * 加好感（原作 increasePlayerWindRelations）：框架的 WindAffinity.add（乘系数、给风之触），再按捕风挂坠 15% 给一个受困之风。只在服务端调。
     *
     * @return 实得多少
     */
    public static int grant(Player player, int raw, RandomSource random) {
        if (player instanceof FakePlayer || player.level().isClientSide) {
            return 0;
        }
        int gained = WindAffinity.add(player, raw);
        if (random.nextFloat() <= CATCHER_CHANCE && WindAffinity.isWearing(player, WindAffinity.WIND_CATCHER)) {
            catchWind(player);
        }
        return gained;
    }

    /** 捕风挂坠：塞一个受困之风，放不下掉在脚下（原作 addItemStackToInventory，否则 dropItem） */
    public static void catchWind(Player player) {
        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(ECWind.WIND_KEEPER.get()));
        player.sendSystemMessage(Component.translatable("message.essentialcraft5.wind_catcher")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
    }

    // ---------------------------------------------------------------- 被动事件

    /** 几率系数：身上轻盈的等级（amplifier + 1），没有时 1（原作 mod） */
    public static int lightnessFactor(LivingEntity entity) {
        MobEffectInstance lightness = entity.getEffect(ECWind.PARANORMAL_LIGHTNESS.get());
        return lightness == null ? 1 : lightness.getAmplifier() + 1;
    }

    /** 每秒一次（原作 WindRelations.playerTick）：只对受到风之眷顾的玩家 */
    public static void secondTick(Player player, RandomSource random) {
        if (player instanceof FakePlayer || player.level().isClientSide || !WindAffinity.isWindbound(player)) {
            return;
        }
        int factor = lightnessFactor(player);
        if (random.nextDouble() < PASSIVE_CHANCE * factor) {
            passiveEvent(player, random);
        }
        if (random.nextDouble() < SPRINT_CHANCE * factor && player.isSprinting()) {
            sprintEvent(player, random);
        }
    }

    /** 被动事件：好感 +1000；没戴 β 指环时风拂过头发，戴着时四选一的元素增益 */
    public static void passiveEvent(Player player, RandomSource random) {
        grant(player, EVENT_POINTS, random);
        if (!WindAffinity.isWearing(player, WindAffinity.WIND_BOON)) {
            say(player, "message.essentialcraft5.wind.timid");
        } else {
            applyBoon(player, random.nextInt(4));
        }
    }

    /**
     * β 指环的元素增益（原作 rndID 0~3，修了 2 写两遍的 bug）：0 火 力量 II、1 水 生命恢复 I、2 地 饱和 I、3 风 急迫 II，各 6 分钟，环境效果（原作 true, true）。
     */
    public static void applyBoon(Player player, int index) {
        switch (index) {
            case 0 -> boon(player, "message.essentialcraft5.wind.warm", MobEffects.DAMAGE_BOOST, 1);
            case 1 -> boon(player, "message.essentialcraft5.wind.chilling", MobEffects.REGENERATION, 0);
            case 2 -> boon(player, "message.essentialcraft5.wind.push", MobEffects.SATURATION, 0);
            case 3 -> boon(player, "message.essentialcraft5.wind.blow", MobEffects.DIG_SPEED, 1);
            default -> {
            }
        }
    }

    private static void boon(Player player, String message, MobEffect effect, int amplifier) {
        say(player, message);
        player.addEffect(new MobEffectInstance(effect, BOON_TICKS, amplifier, true, true));
    }

    /** 疾跑时被风从背后推一把 */
    public static void sprintEvent(Player player, RandomSource random) {
        say(player, "message.essentialcraft5.wind.back");
        grant(player, EVENT_POINTS, random);
        if (!WindAffinity.isWearing(player, WindAffinity.WIND_RESISTANT)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, PUSH_TICKS, PUSH_AMPLIFIER));
        }
    }

    /** 起跳时被风托起（原作 PlayerTracker.onPlayerJump）：只对受眷顾的玩家 */
    public static void jumpEvent(Player player, RandomSource random) {
        if (player instanceof FakePlayer || player.level().isClientSide || !WindAffinity.isWindbound(player)) {
            return;
        }
        if (!WindAffinity.isWearing(player, WindAffinity.WIND_RESISTANT)) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP, LIFT_TICKS, LIFT_AMPLIFIER));
        }
        say(player, "message.essentialcraft5.wind.lift");
        grant(player, EVENT_POINTS, random);
    }

    /** 风的话：聊天栏，深青色斜体（原作 DARK_AQUA + italic） */
    private static void say(Player player, String key) {
        player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
    }

    // ---------------------------------------------------------------- 事件

    /** Forge 总线（类名带模块前缀：Forge 按「简单类名 + 方法名 + 事件类型」认监听器，撞名会静默失效） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class WindRelationsEvents {

        private WindRelationsEvents() {
        }

        /** 每 20 tick（原作 PlayerTickHandler 的 ticksExisted % 20） */
        @SubscribeEvent
        public static void windSecondTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER || event.player.tickCount % 20 != 0) {
                return;
            }
            secondTick(event.player, event.player.getRandom());
        }

        /** 起跳：轻盈的跳跃加成（两端，玩家的移动在客户端算）；受眷顾的玩家 0.0003 被风托起（服务端） */
        @SubscribeEvent
        public static void windJump(LivingEvent.LivingJumpEvent event) {
            LivingEntity entity = event.getEntity();
            ParanormalLightnessEffect.applyJump(entity);
            if (!entity.level().isClientSide && entity instanceof Player player && !(player instanceof FakePlayer)
                    && player.getRandom().nextFloat() < JUMP_CHANCE) {
                jumpEvent(player, player.getRandom());
            }
        }

        /** 摔落：轻盈按同级跳跃提升少算距离 */
        @SubscribeEvent
        public static void windFall(LivingFallEvent event) {
            float distance = ParanormalLightnessEffect.reduceFall(event.getEntity(), event.getDistance());
            if (distance != event.getDistance()) {
                event.setDistance(distance);
            }
        }
    }
}
