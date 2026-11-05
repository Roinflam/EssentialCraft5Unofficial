package com.flameforge.essentialcraft5.corruption;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import com.flameforge.essentialcraft5.util.ECLightning;
import com.flameforge.essentialcraft5.util.ECProtection;
import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ForgeMod;

import java.util.ArrayList;
import java.util.List;

/**
 * 25 种腐化效果发作时做什么（原作 CorruptionEffectECNBTBased.onPlayerTick L152~363）。
 * {@link CorruptionTicker} 每 20 tick 对玩家身上每个开着的效果调一次 {@link #tick}：先按 {@link CorruptionEffects.Effect#chance()} 掷骰（原作
 * {@code rand.nextFloat() <= p}，都是「每秒」的概率），再看条件（在末地、在下雨……），满足就发一句话（原作 desc_N，暗红字）然后发作。
 * GameTest 用 {@link #trigger} 跳过掷骰直接发作一次。
 *
 * <h2>和原作不同的地方（batch3.md §3.6、policy §4.2）</h2>
 * <ul>
 *   <li>#0「双手迟钝」：原作给缓慢 II（和 #3 重复，名字和注释都是「挖得慢」）→ 挖得慢、挥得慢 20 秒。</li>
 *   <li>#0、#1、#3、#5 原作给的是药水效果（挖掘疲劳 / 虚弱 / 缓慢 II、心智悖论），牛奶一喝、图腾一响就没了 → 矩阵后遗症
 *       （{@link Afflictions}，存在玩家数据里，只能等它过去，死亡时清掉）。#1 原作虚弱 II 是固定 −8 攻击力，改成攻击伤害打五折。</li>
 *   <li>#8「物品栏一团糟」：原作打乱整个物品栏，石头能进头盔槽、还绕过绑定诅咒 → 只打乱主物品栏 36 格（含快捷栏），盔甲栏、副手、饰品不动。</li>
 *   <li>#9「嫌弃手中物」：原作 dropAllItems 丢光全部（含盔甲）→ 只把主手那一组扔出去，走原版「Ctrl+Q」的路（{@code onDroppedByPlayer}、
 *       ItemTossEvent，混合端转成 PlayerDropItemEvent）。主手是空的就不发作（原作照样发话）。</li>
 *   <li>#10「管不住手」：原作直接调 useItemRightClick、客户端不同步 → 造一个真的「使用物品」包交给 {@code player.connection.handleUseItem}
 *       （和玩家自己右键同一段代码：混合端的睡觉 / 死亡检查、限频、banitem、按准星发给领地插件的 PlayerInteractEvent，
 *       再往下是 RightClickItem 事件、冷却、物品栏同步）。不直接调 {@code ServerPlayerGameMode.useItem}：那样混合端的检查全被跳过，
 *       见 {@link #useMainHand}（上线前要在混合端上实测）。主手是空的就不发作。</li>
 *   <li>#13「自发爆炸」：原作 {@code createExplosion(null, …, 3, true)} 炸方块 → {@link ECProtection#safeExplode}：不破坏方块、不放火、
 *       不伤展示框 / 画 / 盔甲架 / 掉落物，周围的生物照伤、伤害记在本人头上（PvP 和领地规则照常管）。原版爆炸伤不到「爆炸本体」，
 *       本人那一份另算（{@link #selfBlast}，按最大生命的比例，见下面「扣血」一节），伤害源是不带攻击者的普通爆炸（只伤自己，不牵涉 PvP；死亡信息「爆炸了」）。
 *       本人伤不到的玩家（PvP 关着、同队）不在这次爆炸里，连推都不推（第十批权限修补 A8，{@link #blastMayHit(ServerPlayer, LivingEntity, boolean)}）。</li>
 *   <li>#15「招雷体质」：原作真闪电（点火、把村民 / 猪 / 苦力怕变掉、伤旁人）→ 只做视觉的闪电 + 本人 {@code thunderHit} + 着火 8 秒。
 *       视觉闪电<b>不进世界</b>，只把「生成闪电」的包发给看得见那个区块的玩家（{@link ECLightning#visual}）：
 *       {@code setVisualOnly(true)} 的闪电在服务端照样会让铜块脱氧、给避雷针充能（原版 LightningBolt.tick 不查 visualOnly）。</li>
 *   <li>#17「月光刺痛」：原作条件写成 {@code !isDaytime() && isDaytime()} 永远为假 → 夜里、没下雨、头顶露天时每秒 1%，按月相打魔法伤害（E8）；
 *       新月那一夜伤害是 0，不发作（原作即使修好也只会发一句话、打 0 点）。</li>
 *   <li>#21「酸雨」：原作只看「世界在下雨 + 头顶露天」，沙漠、雪地、玻璃底下也照扣 → {@code isRainingAt(眼睛)}：雨真的落在头上才算。</li>
 *   <li>#23「水皆成酸」：原作 isInsideOfMaterial(WATER) → {@code isEyeInFluidType(水)}。</li>
 *   <li>维度：原作按数字 id（1 末地、−1 下界、0 主世界）→ {@code Level.END / NETHER / OVERWORLD}；模组维度（海纳尔）三个都不算，照原作。</li>
 *   <li>伤害源：仙人掌 / 溺水 / 魔法 / 凋零 / 闪电，照原作的类型；都是打自己，不带攻击者。</li>
 * </ul>
 * 原作不发话的：#4、#16、#18、#21（原作把 #21 的那句注释掉了），见 {@link #speaks}。#6「你看见了死人」、#7「视野昏暗」原作就只有一句话，照原作。
 *
 * <h2>扣血按最大生命的比例（2026-10-13 起）</h2>
 * 原作直接扣血的几种都是固定点数（#2 仙人掌 3 点、#12 / #19 / #20 溺水 1 点、#15 闪电 5 点、#17 魔法 0~5 点、#18 魔法 4 点、#21 / #22 / #24 凋零 1 点、
 * #23 凋零 3 点、#13 本人贴脸普通难度 43 点），整合包里玩家的生命常常是几百上千，这些点数不疼不痒。现在都按受害者<b>当前最大生命</b>的比例扣
 * （{@link CorruptionEffects.HealthCost}，每种一个配置，算法在 {@link #damageOf}），#14 原作只着火、现在同时扣一次着火伤害（抗火挡得住）。
 * 伤害类型照旧（仙人掌 / 溺水 / 魔法 / 凋零 / 闪电 / 着火 / 爆炸），护甲、抗性提升、附魔、爆炸的难度缩放都照原版的规则起作用。
 * 原版 20 点生命时和原作差不多（7.5% = 1.5 点），血越多扣得越多。
 *
 * <h2>温和的后果（{@code general.corruption.gentle_effects}，默认开，第十批）</h2>
 * 上面四个修复之后还剩几处会毁东西、能把人反复炸死的后果，开着这个配置时换成：
 * <ul>
 *   <li>#8：只打乱快捷栏 9 格（{@link #shuffleHotbar}）。整个主物品栏洗一遍的话，背着几十样东西的人要找半天。</li>
 *   <li>#9：主手那一组收进背包里别的格子（{@link #stowMainHand}），不扔在地上。扔在地上会掉进岩浆、虚空、水里，被别人捡走，五分钟后消失。</li>
 *   <li>#10：只挥一下手，正在用的东西（吃、喝、拉弓、举盾）被打断（{@link #twitch}），不替你用手里的东西。
 *       替你右键的话，岩浆桶会倒在准星指着的地方、附魔金苹果会被吃掉、别的模组的破坏类道具会在你自己的领地里动手（领地挡不住主人自己）。</li>
 *   <li>#13：爆炸照旧，本人最多掉<b>当前</b>生命的一半（{@link CappedHurt} 在伤害真正落下那一刻夹住，别的模组加成了也不超过），
 *       掀起来的力道减半；驯服的动物、村民这些「谁都不许碰」的生物不受这次爆炸影响（{@link ECEntityRules}）。
 *       原样的话普通难度下一下就是最大生命的 60%（原作贴脸 43 点），残血时、困难难度下基本必死，平均 28 分钟一次，死了效果还在。</li>
 * </ul>
 * 关掉这个配置就是上面「和原作不同的地方」那一版（#10 的混合端自检 C14 直接调 {@link #useMainHand}，两种都能测）。
 */
public final class CorruptionBehaviors {

    /** 原作 PotionEffect(…, 400, 1, true, true)：20 秒 II 级，现在给成同样长、同样等级的后遗症 */
    public static final int DEBUFF_TICKS = 400;
    public static final int DEBUFF_LEVEL = 1;
    /** #13 的爆炸威力（原作 3，和苦力怕一样） */
    public static final float BLAST_POWER = 3F;
    /** #14、#15 着火几秒（原作 setFire(8)、onStruckByLightning） */
    static final int FIRE_SECONDS = 8;
    private CorruptionBehaviors() {
    }

    /**
     * 每秒一次：掷骰（原作 {@code rand.nextFloat() <= p}），中了再看条件、发作。
     *
     * @return 这次发作了没有
     */
    public static boolean tick(ServerPlayer player, IPlayerData data, CorruptionEffects.Effect effect, RandomSource random) {
        return random.nextFloat() <= CorruptionEffects.effectChance(effect) && trigger(player, data, effect);
    }

    /**
     * 跳过掷骰，只看条件、满足就发作一次（发话 + 后果）。不看配置开关（调用方看）。
     *
     * @return 条件满足、发作了返回 true；条件不满足（不在末地、主手是空的……）返回 false，什么都不做
     */
    public static boolean trigger(ServerPlayer player, IPlayerData data, CorruptionEffects.Effect effect) {
        if (!player.isAlive() || !condition(player, effect.index())) {
            return false;
        }
        if (speaks(effect.index())) {
            player.sendSystemMessage(Component.translatable(messageKey(effect)).withStyle(ChatFormatting.DARK_RED));
        }
        act(player, data, effect.index());
        return true;
    }

    /**
     * 原作不发话的四个：#4 下落加摔落距离（每秒都会触发，原作没写 message）、#16 矩阵损伤 +1、#18 MRU 腐化加伤（这两个 desc 是空字符串）、
     * #21 酸雨（那句被注释掉了）。它们的 .message 键照样写了（五芒星之类的界面想拿来当说明时不会露出键名），只是不发。
     */
    static boolean speaks(int index) {
        return index != 4 && index != 16 && index != 18 && index != 21;
    }

    /** #10「温和」时说的话（原作那句「你突然用了一下手里的东西」对不上「只挥一下手」） */
    public static final String TWITCH_MESSAGE_KEY = "corruption_effect." + EssentialCraft5.MODID + ".mind_10.message_gentle";

    /** 发作时说哪一句：#10 温和时换一句，其余照效果自己的 */
    public static String messageKey(CorruptionEffects.Effect effect) {
        return effect.index() == 10 && CorruptionEffects.gentleEffects() ? TWITCH_MESSAGE_KEY : effect.messageKey();
    }

    // ---------------------------------------------------------------- 条件

    private static boolean condition(ServerPlayer player, int index) {
        Level level = player.level();
        return switch (index) {
            case 2 -> player.swinging;                                        // 正在挥手
            case 4 -> player.fallDistance > 0F;                               // 正在往下掉
            case 5 -> !Afflictions.has(player, Affliction.PARADOX);           // 已经在悖论里就不再给
            case 9, 10 -> !player.getMainHandItem().isEmpty();
            case 11 -> player.hurtTime > 0;                                   // 刚挨过打（半秒内）
            case 12 -> level.dimension() == Level.END;
            case 17 -> level.isNight() && !level.isRaining() && level.canSeeSky(eyeBlock(player))
                    && moonDamage(level.getMoonPhase()) > 0;
            case 18 -> Afflictions.has(player, Affliction.MRU_CORRUPTION);
            case 19 -> level.dimension() == Level.NETHER;
            case 20 -> level.dimension() == Level.OVERWORLD;
            case 21 -> level.isDay() && level.isRainingAt(eyeBlock(player));
            case 22 -> level.isDay() && !level.isRaining() && level.canSeeSky(eyeBlock(player));
            case 23 -> player.isEyeInFluidType(ForgeMod.WATER_TYPE.get());
            default -> index >= 0 && index < CorruptionEffects.ALL.size();
        };
    }

    // ---------------------------------------------------------------- 后果

    private static void act(ServerPlayer player, IPlayerData data, int index) {
        switch (index) {
            case 0 -> Afflictions.give(player, Affliction.HEAVY_HANDS, DEBUFF_TICKS, DEBUFF_LEVEL);
            case 1 -> Afflictions.give(player, Affliction.WEAK_GRIP, DEBUFF_TICKS, DEBUFF_LEVEL);
            case 2 -> hurt(player, player.damageSources().cactus(), damageOf(player, index, 0));
            case 3 -> Afflictions.give(player, Affliction.LEADEN_LEGS, DEBUFF_TICKS, DEBUFF_LEVEL);
            case 4 -> player.fallDistance += 1F;
            case 5 -> {
                Afflictions.give(player, Affliction.PARADOX, Afflictions.PARADOX_TICKS, 0);
                CorruptionTicker.award(player, CorruptionTicker.STEP_PARADOX);
            }
            case 8 -> {
                if (CorruptionEffects.gentleEffects()) {
                    shuffleHotbar(player);
                } else {
                    shuffleMainInventory(player);
                }
            }
            case 9 -> {
                if (CorruptionEffects.gentleEffects()) {
                    stowMainHand(player);
                } else {
                    dropMainHand(player);
                }
            }
            case 10 -> {
                if (CorruptionEffects.gentleEffects()) {
                    twitch(player);
                } else {
                    useMainHand(player);
                }
            }
            case 11 -> {
                player.hurtTime = 0;
                player.invulnerableTime = 0;
            }
            case 12, 19, 20 -> hurt(player, player.damageSources().drown(), damageOf(player, index, 0));
            case 13 -> explode(player);
            case 14 -> burn(player);
            case 15 -> lightning(player);
            case 16 -> CorruptionEffects.addMatrixDamage(data, 1);
            case 17 -> hurt(player, player.damageSources().magic(), damageOf(player, index, player.level().getMoonPhase()));
            case 18 -> hurt(player, player.damageSources().magic(), damageOf(player, index, 0));
            case 21, 22, 23, 24 -> hurt(player, player.damageSources().wither(), damageOf(player, index, 0));
            default -> {
                // #6「你看见了死人」、#7「视野昏暗」：原作就只发一句话
            }
        }
    }

    /**
     * 发作时直接扣多少血：受害者当前最大生命 × 这一种的比例（{@link CorruptionEffects.HealthCost}），还没算护甲、抗性提升、附魔和难度。
     * #2 仙人掌、#12 / #19 / #20 溺水、#14 着火、#15 闪电、#17 魔法（按 moonPhase，{@link #moonlightDamage}）、#18 魔法、#21 / #22 / #24 凋零、#23 凋零；
     * #13 是站在爆心、没被挡住时本人那一下（{@link #selfBlastDamage} 还会按可见比例打折，爆炸另按难度缩放）。别的效果不直接扣血，返回 0。
     */
    public static float damageOf(LivingEntity entity, int index, int moonPhase) {
        return switch (index) {
            case 2 -> CorruptionEffects.HealthCost.HAND_BURN.of(entity);
            case 12, 19, 20 -> CorruptionEffects.HealthCost.SUFFOCATION.of(entity);
            case 13 -> CorruptionEffects.HealthCost.SELF_BLAST.of(entity);
            case 14 -> CorruptionEffects.HealthCost.BURNING.of(entity);
            case 15 -> CorruptionEffects.HealthCost.LIGHTNING.of(entity);
            case 17 -> moonlightDamage(entity.getMaxHealth(), moonPhase);
            case 18 -> CorruptionEffects.HealthCost.MRU_FAILURE.of(entity);
            case 21, 22, 24 -> CorruptionEffects.HealthCost.WITHERING.of(entity);
            case 23 -> CorruptionEffects.HealthCost.ACID_WATER.of(entity);
            default -> 0F;
        };
    }

    /**
     * 打自己一下。amount ≤ 0（比例配成 0）时不打：{@code hurt(0)} 照样会走一遍受击（受伤动画、把肩上的鹦鹉赶下来），没有意义。
     */
    private static void hurt(ServerPlayer player, DamageSource source, float amount) {
        if (amount > 0F) {
            player.hurt(source, amount);
        }
    }

    /** #17 满月那一夜原作的点数：{@link #moonDamage} ÷ 它 = 这一夜按满月的几分之几扣 */
    public static final int FULL_MOON_POINTS = 5;

    /**
     * #17 这一夜扣多少：最大生命 × {@link CorruptionEffects.HealthCost#MOONLIGHT}（满月那一份）× {@link #moonDamage} ÷ {@value #FULL_MOON_POINTS}。
     * 默认 37.5%：满月 37.5%、凸月 22.5%、弦月 15%、蛾眉月 7.5%、新月 0（原作 5 / 3 / 2 / 1 / 0 点，每点按 7.5% 换算）。
     */
    public static float moonlightDamage(float maxHealth, int moonPhase) {
        return CorruptionEffects.HealthCost.MOONLIGHT.amount(maxHealth) * moonDamage(moonPhase) / FULL_MOON_POINTS;
    }

    /**
     * #17 按月相的点数（原作 L298~311 的伤害；现在只当比例的权重用，见 {@link #moonlightDamage}）：月相 0 满月 5；1、7 凸月 3；2、6 弦月 2；3、5 蛾眉月 1；4 新月 0。
     * 月相取 {@code Level.getMoonPhase()}，和原作 {@code (worldTime / 24000 % 8 + 8) % 8} 同一个公式。
     */
    public static int moonDamage(int moonPhase) {
        return switch (Math.floorMod(moonPhase, 8)) {
            case 0 -> 5;
            case 1, 7 -> 3;
            case 2, 6 -> 2;
            case 3, 5 -> 1;
            default -> 0;
        };
    }

    private static BlockPos eyeBlock(ServerPlayer player) {
        return BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
    }

    /** #8：主物品栏 36 格（Inventory.items，含快捷栏）洗牌；盔甲栏、副手、饰品槽、光标上的物品不动。改动随菜单的 broadcastChanges 同步 */
    static void shuffleMainInventory(ServerPlayer player) {
        if (player.isUsingItem()) {
            player.stopUsingItem();   // 正在吃的东西被换走了（原版下一 tick 也会自己停，这里先停，免得拿错东西）
        }
        NonNullList<ItemStack> items = player.getInventory().items;
        RandomSource random = player.getRandom();
        for (int i = items.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            ItemStack swap = items.get(i);
            items.set(i, items.get(j));
            items.set(j, swap);
        }
        player.getInventory().setChanged();
    }

    /** #8 温和版：只把快捷栏 9 格洗一遍；主物品栏其余 27 格、盔甲、副手、饰品不动 */
    static void shuffleHotbar(ServerPlayer player) {
        if (player.isUsingItem()) {
            player.stopUsingItem();
        }
        NonNullList<ItemStack> items = player.getInventory().items;
        RandomSource random = player.getRandom();
        int size = Inventory.getSelectionSize();
        for (int i = size - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            ItemStack swap = items.get(i);
            items.set(i, items.get(j));
            items.set(j, swap);
        }
        player.getInventory().setChanged();
    }

    /**
     * #9 温和版：把主手那一组挪到背包里随机一个空格（先找主物品栏 27 格，没有再找快捷栏别的格子），一格都不空就和主物品栏随机一格对调。
     * 东西一直在背包里，不经过掉落物，所以不发 ItemTossEvent（不是「扔」）。
     *
     * @return 挪了没有（主手是空的就没挪）
     */
    static boolean stowMainHand(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        int selected = inventory.selected;
        if (!Inventory.isHotbarSlot(selected)) {
            return false;
        }
        ItemStack held = inventory.items.get(selected);
        if (held.isEmpty()) {
            return false;
        }
        if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            player.stopUsingItem();
        }
        int hotbar = Inventory.getSelectionSize();
        List<Integer> empty = new ArrayList<>();
        for (int i = hotbar; i < inventory.items.size(); i++) {
            if (inventory.items.get(i).isEmpty()) {
                empty.add(i);
            }
        }
        if (empty.isEmpty()) {
            for (int i = 0; i < hotbar; i++) {
                if (i != selected && inventory.items.get(i).isEmpty()) {
                    empty.add(i);
                }
            }
        }
        RandomSource random = player.getRandom();
        int target = empty.isEmpty()
                ? hotbar + random.nextInt(inventory.items.size() - hotbar)
                : empty.get(random.nextInt(empty.size()));
        ItemStack other = inventory.items.get(target);
        inventory.items.set(target, held);
        inventory.items.set(selected, other);
        inventory.setChanged();
        return true;
    }

    /**
     * #10 温和版：挥一下主手（周围的人看得见），正在用的东西被打断（吃到一半的不算吃完、拉到一半的弓不射、举着的盾放下）。
     * 用 {@code stopUsingItem} 而不是 {@code releaseUsingItem}：后者会走「松手」那一段，弓会把箭射出去、三叉戟会扔出去。
     * 打断随实体数据同步给客户端；手一直按着右键的话，客户端过几刻会自己重新开始用。
     */
    static void twitch(ServerPlayer player) {
        if (player.isUsingItem()) {
            player.stopUsingItem();
        }
        player.swing(InteractionHand.MAIN_HAND, true);
    }

    /**
     * #9：把主手那一组扔出去，照 ServerPlayer.drop(true)（Ctrl+Q）走 {@code onDroppedByPlayer} 和 {@link ForgeHooks#onPlayerTossEvent}。
     * 不直接调 drop(true)：它会把菜单里那一格的「客户端副本」设成已经扔掉（Q 键是客户端先扔、服务端跟上），服务端主动扔时客户端就不会收到更新。
     */
    static void dropMainHand(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        ItemStack selected = inventory.getSelected();
        if (selected.isEmpty() || !selected.onDroppedByPlayer(player)) {
            return;
        }
        if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            player.stopUsingItem();
        }
        ItemStack removed = inventory.removeFromSelected(true);
        ForgeHooks.onPlayerTossEvent(player, removed, true);
    }

    /**
     * #10：替玩家右键一下主手的东西（不碰方块本身，相当于对着空气）。<b>不直接调 {@code gameMode.useItem}</b>，而是造一个真的
     * {@link ServerboundUseItemPacket}（主手、序号 0）交给 {@code player.connection.handleUseItem}，和玩家自己右键时服务端跑的是同一段代码。
     * <p>
     * 为什么（第三批审查 COR-1，混合端对 handleUseItem 的处理）：混合端在调
     * gameMode.useItem 之前还要做这几件事，直接调 gameMode.useItem 会全部跳过——睡觉、死亡时（isImmobile）不用；Spigot 限频；
     * Mohist banitem 的「禁止使用」；按准星射线发 Bukkit {@code PlayerInteractEvent}（打中方块 RIGHT_CLICK_BLOCK，否则 RIGHT_CLICK_AIR），
     * 领地插件拒绝就不用。最后一条是「在 use() 里自己射线去改准星所指方块、又不发专门事件」的模组物品唯一的领地保护
     * （ServerPlayerGameMode.useItem 里只有 banitem 的另一处检查和 Forge 的 RightClickItem；桶、船、方块放置另有物品级的 Bukkit 事件）。
     * <ul>
     *   <li>包必须用 {@link FriendlyByteBuf} 构造器造：Spigot 只在这个构造器里给 {@code timestamp} 赋值，(hand, seq) 构造器的 timestamp 是 0，会打乱限频计数。</li>
     *   <li>序号 0：ackBlockChangesUpTo 取最大值；客户端的方块预测序号从 1 起，确认到 0 什么都不结束。</li>
     *   <li>在服务端线程上调，ensureRunningOnSameThread 什么都不做；混合端在连接已断开（processedDisconnect）时会抛 RunningOnDifferentThreadException，接住就算没发作。</li>
     *   <li>原版 / Forge / 单人下 handleUseItem 比以前多做的只有 ackBlockChangesUpTo、resetLastActionTime，挥手（shouldSwing 时）以前也有，行为不变。</li>
     *   <li>副作用：resetLastActionTime 会刷新空闲计时。手里拿着东西挂机的人平均每 17 分钟被「动」一下，服务器的空闲踢出、AFK 类插件会晚一点认出挂机。</li>
     * </ul>
     * <b>上线前在混合端上实测一次</b>：别人领地里对着方块、对着空气各等一次发作，应被拒（物品不动，客户端物品栏被刷回）；
     * banitem 禁止使用的物品不被用；睡觉时不用（那句红字照样会发，和原作一样先发话后发作）。
     */
    static void useMainHand(ServerPlayer player) {
        if (player.connection == null) {
            return;   // 还没连上 / 已经拆掉的玩家对象：没有「自己右键」这条路
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeEnum(InteractionHand.MAIN_HAND);
            buf.writeVarInt(0);
            player.connection.handleUseItem(new ServerboundUseItemPacket(buf));
        } catch (RunningOnDifferentThreadException ignored) {
            // 混合端：连接已断开（或服务器正在停）时 ensureRunningOnSameThread 抛这个，这次不用
        } finally {
            buf.release();
        }
    }

    /**
     * #13：以自己脚下为中心的 3 级爆炸。周围的生物走 {@link ECProtection#safeExplode}（不破坏方块、不放火、不伤装饰实体，攻击者是本人）；
     * 原版爆炸不伤「爆炸本体」，本人那一份由 {@link #selfBlast} 补上（脚下正中：普通难度下最大生命的 60%，外加往上掀；原作是贴脸苦力怕那样的 43 点）。
     */
    static void explode(ServerPlayer player) {
        Vec3 center = player.position();
        ServerLevel level = player.serverLevel();
        boolean gentle = CorruptionEffects.gentleEffects();
        // 温和：驯服的动物、村民、起了名字 / 拴着的生物不在这次爆炸里（宠物死了就没了）；别的照常，攻击者照旧是本人。
        // 不管温和不温和，本人伤不到的玩家都不在这次爆炸里（blastMayHit）
        ECProtection.safeExplode(level, player, level.damageSources().explosion(player, player), center, BLAST_POWER,
                entity -> blastMayHit(player, entity, gentle));
        selfBlast(player, center, BLAST_POWER, gentle);
    }

    /** #13 温和时爆炸能伤到谁：「谁都不许碰」的生物（驯服的、起了名字的、拴着的、村民等，{@link ECEntityRules#isSpared}）不算；玩家照 PvP 规则 */
    static boolean blastMayHit(LivingEntity entity) {
        return !ECEntityRules.isSpared(entity);
    }

    /**
     * #13 的爆炸能碰到谁（第十批权限修补 A8）：本人伤不到的玩家（{@code canHarmPlayer}：服务器 / 世界关了 PvP、同队不许友伤）不算——
     * 原版爆炸不管伤害成没成都把人推开，纯 Forge 上 PvP 关着时别的玩家虽然不掉血，照样被推出去（混合端被插件拦下的本来就跳过击退）。
     * 温和时另排除「谁都不许碰」的生物（{@link #blastMayHit(LivingEntity)}）。
     */
    public static boolean blastMayHit(ServerPlayer self, LivingEntity entity, boolean gentle) {
        if (entity instanceof Player other && other != self && !self.canHarmPlayer(other)) {
            return false;
        }
        return !gentle || blastMayHit(entity);
    }

    /**
     * 原版 Explosion.explode() 里对单个实体的那一段（k = (1 − 距离 / 2r) × 可见比例；击退按爆炸保护衰减），只作用在本人身上，
     * 伤害改按最大生命的比例（{@link #selfBlastDamage}）。伤害源是不带攻击者的普通爆炸：只伤自己，PvP 插件不会把它当成「玩家打玩家」拦下来；
     * 爆炸伤害照原版随难度缩放（简单约一半、困难 1.5 倍），护甲、爆炸保护照常减免。
     *
     * @return 算出来的伤害（不在范围内、正好在眼睛上、比例配成 0 时是 0，什么都不做）
     */
    static float selfBlast(ServerPlayer player, Vec3 center, float power) {
        return selfBlast(player, center, power, false);
    }

    /**
     * 同上；gentle = true 时本人这一下最多掉当前生命的一半（{@link CappedHurt} 在 {@code LivingDamageEvent} 最后一道夹住），掀起来的力道减半
     * （贴脸那一下原本会把人掀起五六格，摔下来又是一下，残血时就摔死了）。
     * 这一下被拦下（{@code hurt} 返回 false：纯 Forge 上 {@code LivingAttackEvent} 被取消，混合端上插件取消了伤害事件、/god 等）时不掀人：
     * 被拦下的伤害不该有别的后果。这里的击退是自己加的，混合端爆炸「伤害被取消就跳过击退」那一段管不到它。
     *
     * @return 算出来的伤害（夹之前的数）
     */
    static float selfBlast(ServerPlayer player, Vec3 center, float power, boolean gentle) {
        if (player.ignoreExplosion()) {
            return 0F;
        }
        double diameter = power * 2D;
        double distance = Math.sqrt(player.distanceToSqr(center)) / diameter;
        if (distance > 1D) {
            return 0F;
        }
        double dx = player.getX() - center.x;
        double dy = player.getEyeY() - center.y;
        double dz = player.getZ() - center.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length == 0D) {
            return 0F;   // 原版同样跳过
        }
        double impact = (1D - distance) * Explosion.getSeenPercent(center, player);
        float damage = selfBlastDamage(player.getMaxHealth(), impact, diameter);
        if (!(damage > 0F)) {
            return 0F;
        }
        DamageSource source = player.damageSources().explosion(null, null);
        boolean landed = gentle ? CappedHurt.hurtAtMostHalf(player, source, damage) : player.hurt(source, damage);
        if (landed && !(player.isCreative() && player.getAbilities().flying)) {
            double knockback = ProtectionEnchantment.getExplosionKnockbackAfterDampener(player, impact) * (gentle ? 0.5D : 1D);
            player.setDeltaMovement(player.getDeltaMovement().add(dx / length * knockback, dy / length * knockback, dz / length * knockback));
            player.hurtMarked = true;   // 把速度发给客户端（玩家的移动在客户端算）
        }
        return damage;
    }

    /**
     * #13 本人那一下（难度缩放、护甲之前）：最大生命 × {@link CorruptionEffects.HealthCost#SELF_BLAST}，再按原版爆炸公式的衰减打折——
     * 原版伤害 ⌊(k² + k) / 2 × 7 × 直径 + 1⌋ 除以 k = 1 时的值。站在爆心、中间没东西挡着时 k = 1，正好是满额（默认 60%）。
     *
     * @param impact   k（夹在 0~1）
     * @param diameter 爆炸威力 × 2
     */
    public static float selfBlastDamage(float maxHealth, double impact, double diameter) {
        double k = Math.max(0D, Math.min(1D, impact));
        double full = Math.floor(7D * diameter + 1D);
        double vanilla = Math.floor((k * k + k) / 2D * 7D * diameter + 1D);
        double damage = CorruptionEffects.HealthCost.SELF_BLAST.amount(maxHealth) * (vanilla / full);
        return damage > 0D && Double.isFinite(damage) ? (float) damage : 0F;
    }

    /**
     * #14：着火 8 秒，同时扣一次着火伤害（{@code on_fire}，最大生命 × {@link CorruptionEffects.HealthCost#BURNING}）。
     * 原作只着火，每秒 1 点在高血量下不疼不痒；抗火时这一下整个挡掉（原版 {@code LivingEntity.hurt} 对 #is_fire 的判断），火焰保护照常减免。
     */
    static void burn(ServerPlayer player) {
        player.setSecondsOnFire(FIRE_SECONDS);
        hurt(player, player.damageSources().onFire(), CorruptionEffects.HealthCost.BURNING.of(player));
    }

    /**
     * #15：视觉闪电劈在自己身上 + thunderHit + 着火 8 秒（thunderHit 只在「刚好没着火」时点火，原作靠真闪电落的火）。
     * thunderHit 扣的是闪电实体身上的伤害（Forge 给 LightningBolt 加的 {@code getDamage}，默认 5 点），这里先改成最大生命 × {@link CorruptionEffects.HealthCost#LIGHTNING}；
     * 闪电实体不进世界，改它只影响这一下。
     */
    static void lightning(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        LightningBolt bolt = ECLightning.visual(level, player.position());
        if (bolt == null) {
            return;
        }
        float damage = CorruptionEffects.HealthCost.LIGHTNING.of(player);
        // 比例配成 0 时不打（同 hurt）：thunderHit 里照样 hurt(闪电, 0)，会走一遍受击、把肩上的鹦鹉赶下来
        if (damage > 0F) {
            bolt.setDamage(damage);
            player.thunderHit(level, bolt);
        }
        player.setSecondsOnFire(FIRE_SECONDS);
    }
}
