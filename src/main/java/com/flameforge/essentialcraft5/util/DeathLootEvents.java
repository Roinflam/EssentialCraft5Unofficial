package com.flameforge.essentialcraft5.util;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 「击杀不掉落」（第四批框架 F4-13）：被 {@link ECDamageTypes#NO_DEATH_LOOT} 标签里的伤害类型（{@code mru_discharge} 等）打死的
 * <b>非玩家</b>生物不掉物品、不掉经验——特斯拉线圈、反应堆不能当刷怪塔的收割机（原作被它们打死照常掉落，配刷怪笼就是被删掉的怪物收割机，policy §4.3）。
 * 玩家受害者不碰（线圈打死的玩家照常掉落）；{@code #forge:bosses}（凋灵、末影龙）不拦——玩家打凋灵时线圈补了最后一下，下界之星没了是陷阱（batch4 Q4）。
 *
 * <h2>为什么要在死亡事件里打标记</h2>
 * 原版 {@code LivingEntity.die} 的顺序：{@code LivingDeathEvent} → {@code dropAllDeathLoot}，后者里先 {@code dropExperience}
 * （发 {@link LivingExperienceDropEvent}，经验球当场生成），最后才发 {@link LivingDropsEvent}；而 {@code lastDamageSource} 是在 {@code die()} 之后才赋值的
 * （{@code LivingEntity}，混合端里比 Forge 多出几行），经验事件里用 {@code getLastDamageSource()} 拿到的是上一下伤害。
 * 所以 {@link LivingDeathEvent}（LOWEST、不收已取消的——被别的模组救活的不算）里按伤害源记下受害者，经验事件查这个记录。
 *
 * <h2>为什么不整表取消掉落（怪捡走的东西照掉）</h2>
 * 怪从地上捡起的物品（多半是玩家扔的、死掉的玩家掉的）会被 {@code Mob.setGuaranteedDrop} 把那一格的掉落率设成 2.0，
 * {@code Mob.dropCustomDeathLoot} 对掉落率 &gt;1 的装备不论怎么死都掉；这些装备和战利品表的掉落一起收进 {@code captureDrops}、
 * 在 {@link LivingDropsEvent} 里一并交出来（Forge {@code LivingEntity.dropAllDeathLoot} L1360~1378；混合端另在中间发 Bukkit
 * {@code EntityDeathEvent}，并把掉落物按副本重建成新的 ItemEntity）。整表取消 = 怪捡走的玩家装备跟着消失（SAF-6）。
 * 装备格以外还有一条路（4b 审查 REA-1）：猪灵（{@link InventoryCarrier}）把欣赏完、装备不上的金制品（金苹果、金块、钟，拿着弩时捡的金剑、
 * 已经穿着金甲时再捡的金甲，可能带附魔、可能是玩家死后掉的）和金粒收进背包（{@code PiglinAi.pickUpItem} L221~244、
 * {@code stopHoldingOffHandItem} L267~281），死时 {@code Piglin.dropCustomDeathLoot} L119 把背包全部 {@code spawnAtLocation}，同样进 {@code captureDrops}。
 * 所以死亡事件里同时对 {@link Mob} 记下<b>掉落率 &gt;1、没有消失诅咒</b>的装备的副本，再加上 {@link InventoryCarrier} 背包里<b>所有</b>非空格的副本
 * （原版掉背包不看消失诅咒，这里也不排除），掉落事件里只留和它们相同的掉落物（每件只留一次），其余删掉。
 * 背包里的东西都是怪从地上捡的，刷不出物品：配对按物品、数量、NBT 一件对一件，背包不掉出来的生物（掠夺者只收白色旗帜、村民只收食物和种子）
 * 最多让一件和背包里某件完全相同的战利品表掉落留下。
 * 这份「保留表」对<b>所有</b>死掉的 {@link Mob} 都记（只要有这样的装备或背包里有东西；空手的怪连反射都不做），别的地方要在
 * 自己的 {@link LivingDropsEvent} 监听器里调 {@link #keepGuaranteedEquipment} 时共用同一份结果。记录在每个服务端 tick 结束时清空
 * （死亡事件和掉落事件在同一次 {@code die()} 调用里同步发完，不会跨 tick），调用方用什么优先级都行。
 *
 * <h2>读掉落率：反射（不加 AT）</h2>
 * {@code Mob.getEquipmentDropChance(EquipmentSlot)} 和它读的 {@code handDropChances / armorDropChances} 都是 protected。
 * 这里用 {@link ObfuscationReflectionHelper} 按 SRG 名找方法 {@code m_21519_}（= getEquipmentDropChance，开发环境自动换成官方名；
 * 出处：Gradle 缓存 {@code forge_gradle/minecraft_repo/versions/1.20.1/client_mappings.txt} 里官方名 → 混淆名 {@code bgb.f(Lbfo;)F}，
 * 再查同目录 {@code mcp_mappings.tsrg} 的 {@code bgb / f (Lbfo;)F m_21519_}；两个字段是 {@code f_21347_ / f_21348_}，这里不用）。
 * 调方法而不读字段：原版 {@code dropCustomDeathLoot} 就是调这个方法，覆盖了它的模组生物按它们自己的规则算。
 * 不加 AT：工程的 {@code accesstransformer.cfg} 是共用文件，而且混合端已经把这两个字段改成 public，AT 在混合端上是多余的。
 * 反射失败（不该发生）时把所有装备都当必掉——宁可多留一件怪自己的装备，也不吞玩家的东西；只记一次错误日志。
 *
 * <h2>已知漏洞（告知，不修）</h2>
 * 混合端在 Forge 掉落事件之前先发 Bukkit {@code EntityDeathEvent}，{@code getKiller()} = 100 tick 内打过它的玩家：
 * 服上若有「杀怪给钱」类插件，玩家摸一下怪、再让线圈补刀照样拿钱（batch4 §4.13）。
 * 驴和羊驼的箱子、马铠这类既不在装备格、也不在 {@link InventoryCarrier} 背包里的东西不在保留之列；线圈、反应堆只打敌对生物，碰不上它们。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class DeathLootEvents {

    /** 掉落率大于它就是「必掉」（原版 {@code Mob.dropCustomDeathLoot}：{@code f > 1.0F}） */
    public static final float GUARANTEED_DROP_CHANCE = 1.0F;

    /** 本 tick 死掉的生物 → 记录；每个服务端 tick 结束清空，WeakHashMap 兜底（只在服务端线程用） */
    private static final Map<LivingEntity, Record> RECORDS = new WeakHashMap<>();

    @Nullable
    private static final Method GET_EQUIPMENT_DROP_CHANCE = findDropChanceMethod();
    private static boolean reflectionFailureLogged;

    private DeathLootEvents() {
    }

    /**
     * @param suppress   这次死亡不掉物品、不掉经验
     * @param guaranteed 死亡那一刻身上掉落率 &gt;1 的装备和 {@link InventoryCarrier} 背包里的东西（副本），见 {@link #guaranteedEquipment}
     */
    private record Record(boolean suppress, List<ItemStack> guaranteed) {
    }

    // ------------------------------------------------------------------
    // 公开的判定与帮手
    // ------------------------------------------------------------------

    /** 这次死亡要不要「不掉落」：非玩家、伤害类型在 {@code no_death_loot} 标签里、不是 {@code #forge:bosses} */
    public static boolean suppressesLoot(LivingEntity victim, DamageSource source) {
        return !(victim instanceof Player)
                && source.is(ECDamageTypes.NO_DEATH_LOOT)
                && !victim.getType().is(Tags.EntityTypes.BOSSES);
    }

    /**
     * 只留怪捡走的东西：从 drops 里删掉所有掉落物，除了和受害者死亡时的保留表（{@link #guaranteedEquipment}：掉落率 &gt;1 的装备、
     * {@link InventoryCarrier} 背包里的东西）相同的（每件只留一次）。
     * 要在 {@link LivingDropsEvent} 的监听器里调（记录是本类在同一次死亡的 {@link LivingDeathEvent} 里拍的，本 tick 结束前都在，优先级不限）；
     * 找不到记录（不是 {@link Mob}、或身上没有必掉装备、背包也是空的）就全删。重复调用结果不变（本类的两遍删除都靠这一点）。
     */
    public static void keepGuaranteedEquipment(LivingEntity victim, Collection<ItemEntity> drops) {
        Record record = RECORDS.get(victim);
        List<ItemStack> remaining = record == null ? new ArrayList<>() : new ArrayList<>(record.guaranteed());
        Iterator<ItemEntity> it = drops.iterator();
        while (it.hasNext()) {
            ItemStack dropped = it.next().getItem();
            boolean keep = false;
            for (Iterator<ItemStack> candidates = remaining.iterator(); candidates.hasNext(); ) {
                if (sameStack(dropped, candidates.next())) {
                    candidates.remove();
                    keep = true;
                    break;
                }
            }
            if (!keep) {
                it.remove();
            }
        }
    }

    /**
     * 保留表：生物身上掉落率 &gt;1、没有消失诅咒的装备，加上 {@link InventoryCarrier}（猪灵等）背包里所有非空格（不排除消失诅咒，
     * 原版 {@code Piglin.dropCustomDeathLoot} 掉背包也不看），都是副本。不是 {@link Mob} 的返回空表。名字沿用框架最早的版本，背包是 4b 审查 REA-1 加的。
     * 死亡事件里调一次（装备还在身上、背包还没倒空；{@code dropCustomDeathLoot} 掉出去以后那一格就空了）。
     */
    public static List<ItemStack> guaranteedEquipment(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) {
            return List.of();
        }
        List<ItemStack> result = null;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = mob.getItemBySlot(slot);
            // 空格子不反射：绝大多数怪身上什么都没有
            if (stack.isEmpty() || EnchantmentHelper.hasVanishingCurse(stack)) {
                continue;
            }
            if (dropChance(mob, slot) > GUARANTEED_DROP_CHANCE) {
                if (result == null) {
                    result = new ArrayList<>(2);
                }
                result.add(stack.copy());
            }
        }
        // 背包（REA-1）：猪灵收进背包的金制品和金粒，Piglin.dropCustomDeathLoot 死时全部掉出来；这时背包还没倒空
        if (mob instanceof InventoryCarrier carrier) {
            SimpleContainer inventory = carrier.getInventory();
            if (inventory != null) {
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    ItemStack stack = inventory.getItem(i);
                    if (!stack.isEmpty()) {
                        if (result == null) {
                            result = new ArrayList<>(4);
                        }
                        result.add(stack.copy());
                    }
                }
            }
        }
        return result == null ? List.of() : result;
    }

    // ------------------------------------------------------------------
    // 事件
    // ------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || victim instanceof Player) {
            return;
        }
        boolean suppress = suppressesLoot(victim, event.getSource());
        List<ItemStack> guaranteed = guaranteedEquipment(victim);
        if (suppress || !guaranteed.isEmpty()) {
            RECORDS.put(victim, new Record(suppress, guaranteed));
        } else {
            RECORDS.remove(victim);
        }
    }

    /** HIGHEST：取消以后，后面不收已取消事件的监听器不会再改经验 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        Record record = RECORDS.get(event.getEntity());
        if (record != null && record.suppress()) {
            event.setCanceled(true);
        }
    }

    /**
     * 掉落表删两遍（{@link #keepGuaranteedEquipment} 可以重复调，结果一样）：
     * <ul>
     *   <li>HIGHEST：先删，中间优先级「把掉落拿走」的模组（直接塞给玩家、收进墓碑）看到的已经是删过的表；</li>
     *   <li>LOWEST：再删一遍，清掉别的模组在中间优先级往表里<b>加</b>的东西。实测（2026-09-28 本机混合端自检 C13）：
     *       甜蜜魔法（SweetMagic）在默认优先级给打死的怪加「蛋袋」，只删一遍时线圈打死的僵尸照样掉蛋袋，等于能拿线圈刷。</li>
     * </ul>
     * 除了死亡事件的记录，也按这次的伤害源再判一次（记录只在死亡事件没被取消时才有；掉落事件自带的伤害源就是致死的那一下）。
     * 在死亡事件里直接生成物品实体、不经过掉落事件的模组管不到。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        stripDrops(event);
    }

    /** 第二遍，见 {@link #onLivingDrops} */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDropsLate(LivingDropsEvent event) {
        stripDrops(event);
    }

    private static void stripDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        Record record = RECORDS.get(victim);
        if ((record != null && record.suppress()) || suppressesLoot(victim, event.getSource())) {
            keepGuaranteedEquipment(victim, event.getDrops());
        }
    }

    /** 死亡和掉落在同一次 die() 里同步发完，tick 结束时记录都用完了 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !RECORDS.isEmpty()) {
            RECORDS.clear();
        }
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 物品、数量、NBT 相同（空 NBT 和没有 NBT 算相同）。不比 Forge capability：混合端把掉落物按副本重建过，
     * 往宽里判只会多留一件，不会吞东西。
     */
    private static boolean sameStack(ItemStack a, ItemStack b) {
        if (a.getItem() != b.getItem() || a.getCount() != b.getCount()) {
            return false;
        }
        CompoundTag ta = a.getTag();
        CompoundTag tb = b.getTag();
        boolean emptyA = ta == null || ta.isEmpty();
        boolean emptyB = tb == null || tb.isEmpty();
        if (emptyA || emptyB) {
            return emptyA == emptyB;
        }
        return ta.equals(tb);
    }

    private static float dropChance(Mob mob, EquipmentSlot slot) {
        if (GET_EQUIPMENT_DROP_CHANCE != null) {
            try {
                return (Float) GET_EQUIPMENT_DROP_CHANCE.invoke(mob, slot);
            } catch (ReflectiveOperationException | RuntimeException e) {
                logReflectionFailure(e);
            }
        }
        // 拿不到掉落率：当成必掉（宁可多留一件怪自己的装备，也不吞玩家的东西）
        return Float.MAX_VALUE;
    }

    @Nullable
    private static Method findDropChanceMethod() {
        try {
            // m_21519_ = Mob.getEquipmentDropChance(EquipmentSlot)（出处见类注释）
            return ObfuscationReflectionHelper.findMethod(Mob.class, "m_21519_", EquipmentSlot.class);
        } catch (RuntimeException e) {
            logReflectionFailure(e);
            return null;
        }
    }

    private static void logReflectionFailure(Exception e) {
        if (!reflectionFailureLogged) {
            reflectionFailureLogged = true;
            EssentialCraft5.LOGGER.error("读不到生物装备掉落率（Mob.getEquipmentDropChance / m_21519_），「击杀不掉落」改为保留所有装备", e);
        }
    }
}
