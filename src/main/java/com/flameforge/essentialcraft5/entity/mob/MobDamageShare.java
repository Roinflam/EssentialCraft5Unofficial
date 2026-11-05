package com.flameforge.essentialcraft5.entity.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.boss.BossDamageBudget;
import com.flameforge.essentialcraft5.entity.boss.ECBoss;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.util.ECLandedHit;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 怪受的伤害里有多少是玩家打的（13c-Fa，batch13.md §5.4「公平击杀」；policy §4.18「来源、公平击杀、按人递减」）。
 * <p>
 * <b>记什么</b>：在 {@code LivingDamageEvent} LOWEST、这一下打实了（{@link ECLandedHit#landed}）时，记这一下的<b>有效伤害</b>
 * = min(事件里的伤害, 挨打前的生命) + 吃掉的伤害吸收（{@link ECLandedHit#absorbed}）。按挨打前的生命封顶：溺水塔、岩浆、摔落把怪磨到 1 血、
 * 玩家再补一刀打出 100 的，只算 1 点，占比上不去。
 * <ul>
 *   <li>伤害记在真玩家头上（{@link ECProtection#isRealPlayer}）或真玩家的宠物头上（{@link OwnableEntity} 的主人在线、是真玩家，算主人的）：
 *       加进「玩家」那一份，并按 UUID 记进名单（最多 {@value #MAX_CONTRIBUTORS} 人，多出来的只进合计）；</li>
 *   <li>没有攻击者的着火（{@code on_fire}）、中毒（{@code magic}）、凋零（{@code wither}）：多半是玩家点的火、下的毒留下的，
 *       100 刻内被真玩家（或他的宠物）打过的记在他头上（{@link LivingEntity#getKillCredit}，和原版 {@code killed_by_player} 同一个窗口），否则算环境；</li>
 *   <li>其余（摔落、岩浆、火方块、溺水、冰冻、别的模组的炮塔、假玩家、别的没有攻击者的）只进合计。</li>
 * </ul>
 * 占比 = 玩家那一份 / 合计。被取消的、混合端插件拦下的（不发 {@code LivingDamageEvent}）都不记。
 * <p>
 * <b>首领</b>（{@link ECBoss}）：单次封顶 / 每秒桶也在 {@code LivingDamageEvent} LOWEST 夹，谁先跑看注册顺序，所以这里按同一个事件的结算数
 * （{@link BossDamageBudget#resolve}，同一个事件只算一次、只扣一次桶）记，封顶以前的一大下不会把占比抬上去。小 Boss 的公平击杀就读这份。
 * <p>
 * <b>记在谁身上</b>：所有 {@link Enemy}（含 {@link ECMonster}，风法师这类不是本模组新基类的敌对生物也照记）以及
 * {@link #TRACKED} 标签里的别的生物（数据包可加）。玩家不记。存在实体的持久数据 {@value #KEY} 里（随实体存盘，区块卸载再加载照样接着算）：
 * {@code {p: 玩家份, t: 合计, w: [{id: UUID, d: 伤害}…]}}。
 * <p>
 * 用的地方：公平击杀条件（{@code loot/FairKillCondition}，占比 ≥ {@code fair_kill_min_share}）；13d 巢穴「只给打出过有效伤害的人」读 {@link #damageBy}。
 * 首领的参战名单另有一份（{@code BossRoster} 只记生存 / 冒险真玩家、宠物不算；图谱的 {@code MatrixCodex.bossDamage}），这里不替它们记 10% 门槛。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class MobDamageShare {

    /** 持久数据键 */
    public static final String KEY = EssentialCraft5.MODID + ":damage_share";
    /** 除了 {@link Enemy} 以外还要记占比的生物 */
    public static final TagKey<EntityType<?>> TRACKED = ECTags.modEntity("damage_share_tracked");
    /** 名单最多记几人 */
    public static final int MAX_CONTRIBUTORS = 16;

    private static final String TAG_PLAYERS = "p";
    private static final String TAG_TOTAL = "t";
    private static final String TAG_WHO = "w";
    private static final String TAG_ID = "id";
    private static final String TAG_DAMAGE = "d";

    private MobDamageShare() {
    }

    /** 这只生物要不要记 */
    public static boolean tracked(LivingEntity entity) {
        return !(entity instanceof Player) && (entity instanceof Enemy || entity.getType().is(TRACKED));
    }

    /**
     * 这一下伤害记在哪名玩家头上：攻击者是真玩家本人，或者真玩家的宠物（主人在线）。别的都是 null（算环境）。
     * 不看受害者，没有攻击者的着火、中毒、凋零要记给最近打过它的人用 {@link #creditedPlayer(LivingEntity, DamageSource)}。
     */
    @Nullable
    public static UUID creditedPlayer(DamageSource source) {
        return creditedPlayer(source.getEntity());
    }

    /**
     * victim 挨的这一下记在哪名玩家头上：有攻击者的照 {@link #creditedPlayer(DamageSource)}；没有攻击者（连直接造成伤害的实体也没有）的
     * 着火、中毒、凋零（{@link #lingering}），记在 100 刻内打过它的真玩家（或真玩家的宠物）头上，过了这 100 刻算环境。
     * 岩浆、火方块、热地板、冰冻、摔落、溺水、仙人掌、窒息、挤压不在其列，照旧算环境。
     */
    @Nullable
    public static UUID creditedPlayer(LivingEntity victim, DamageSource source) {
        if (source.getEntity() != null || source.getDirectEntity() != null || !lingering(source)) {
            return creditedPlayer(source);
        }
        return creditedPlayer(victim.getKillCredit());
    }

    /** 玩家点的火、下的毒会留下的持续伤害：着火、中毒（{@code magic}）、凋零 */
    public static boolean lingering(DamageSource source) {
        return source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.WITHER);
    }

    /** 记在哪名玩家头上：attacker 是真玩家本人，或者主人在线的真玩家的宠物；别的（含 null）都是 null */
    @Nullable
    public static UUID creditedPlayer(@Nullable Entity attacker) {
        if (ECProtection.isRealPlayer(attacker)) {
            return attacker.getUUID();
        }
        if (attacker instanceof OwnableEntity pet && !(attacker instanceof Player)) {
            LivingEntity owner = pet.getOwner();
            if (ECProtection.isRealPlayer(owner)) {
                return owner.getUUID();
            }
        }
        return null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMobDamageShare(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !tracked(victim) || !ECLandedHit.landed(event)) {
            return;
        }
        float amount = event.getAmount();
        if (victim instanceof ECBoss) {
            amount = Math.min(amount, BossDamageBudget.resolve(event));
        }
        float dealt = (Float.isFinite(amount) ? Math.max(0F, Math.min(amount, victim.getHealth())) : 0F) + ECLandedHit.absorbed(event);
        if (!(dealt > 0F)) {
            return;
        }
        record(victim, creditedPlayer(victim, event.getSource()), dealt);
    }

    /** 记一下（player 为 null 算环境）。GameTest 也直接调 */
    public static void record(LivingEntity victim, @Nullable UUID player, float dealt) {
        if (!(dealt > 0F) || !Float.isFinite(dealt)) {
            return;
        }
        CompoundTag data = victim.getPersistentData();
        CompoundTag share = data.getCompound(KEY);
        share.putFloat(TAG_TOTAL, share.getFloat(TAG_TOTAL) + dealt);
        if (player != null) {
            share.putFloat(TAG_PLAYERS, share.getFloat(TAG_PLAYERS) + dealt);
            ListTag who = share.getList(TAG_WHO, Tag.TAG_COMPOUND);
            boolean found = false;
            for (int i = 0; i < who.size(); i++) {
                CompoundTag entry = who.getCompound(i);
                if (entry.hasUUID(TAG_ID) && player.equals(entry.getUUID(TAG_ID))) {
                    entry.putFloat(TAG_DAMAGE, entry.getFloat(TAG_DAMAGE) + dealt);
                    found = true;
                    break;
                }
            }
            if (!found && who.size() < MAX_CONTRIBUTORS) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID(TAG_ID, player);
                entry.putFloat(TAG_DAMAGE, dealt);
                who.add(entry);
            }
            share.put(TAG_WHO, who);
        }
        data.put(KEY, share);
    }

    /** 玩家（含宠物）打的有效伤害合计 */
    public static float playerDamage(LivingEntity victim) {
        return victim.getPersistentData().getCompound(KEY).getFloat(TAG_PLAYERS);
    }

    /** 受的有效伤害合计 */
    public static float totalDamage(LivingEntity victim) {
        return victim.getPersistentData().getCompound(KEY).getFloat(TAG_TOTAL);
    }

    /** 玩家（含宠物）打的占比，0~1；没挨过打是 0 */
    public static float share(LivingEntity victim) {
        CompoundTag share = victim.getPersistentData().getCompound(KEY);
        float total = share.getFloat(TAG_TOTAL);
        return total > 0F ? Math.min(1F, share.getFloat(TAG_PLAYERS) / total) : 0F;
    }

    /** 这名玩家（含他的宠物）打出的有效伤害（不在名单里是 0） */
    public static float damageBy(LivingEntity victim, UUID player) {
        ListTag who = victim.getPersistentData().getCompound(KEY).getList(TAG_WHO, Tag.TAG_COMPOUND);
        for (int i = 0; i < who.size(); i++) {
            CompoundTag entry = who.getCompound(i);
            if (entry.hasUUID(TAG_ID) && player.equals(entry.getUUID(TAG_ID))) {
                return entry.getFloat(TAG_DAMAGE);
            }
        }
        return 0F;
    }

    /** 名单：玩家 UUID → 打出的有效伤害（按第一次出手的先后） */
    public static Map<UUID, Float> contributors(LivingEntity victim) {
        ListTag who = victim.getPersistentData().getCompound(KEY).getList(TAG_WHO, Tag.TAG_COMPOUND);
        Map<UUID, Float> out = new LinkedHashMap<>();
        for (int i = 0; i < who.size(); i++) {
            CompoundTag entry = who.getCompound(i);
            if (entry.hasUUID(TAG_ID)) {
                out.put(entry.getUUID(TAG_ID), entry.getFloat(TAG_DAMAGE));
            }
        }
        return out;
    }

    /** 清掉记录（测试、巢穴换下一波时用） */
    public static void clear(LivingEntity victim) {
        victim.getPersistentData().remove(KEY);
    }
}
