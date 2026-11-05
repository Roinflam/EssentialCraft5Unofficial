package com.flameforge.essentialcraft5.entity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.blockentity.DemonicPentacleBlockEntity;
import com.flameforge.essentialcraft5.demon.DemonStanding;
import com.flameforge.essentialcraft5.demon.DemonTrades;
import com.flameforge.essentialcraft5.demon.DemonWant;
import com.flameforge.essentialcraft5.item.CapturedSoulItem;
import com.flameforge.essentialcraft5.menu.DemonTradeMenu;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECDemon;
import com.flameforge.essentialcraft5.registry.ECDemonic;
import com.flameforge.essentialcraft5.registry.ECHoannaMaterials;
import com.flameforge.essentialcraft5.registry.ECShade;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import com.flameforge.essentialcraft5.util.ECLevels;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.OwnerCellMemory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.Tags;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 恶魔（原作 EntityDemon）：恶魔五芒星召出来，站在阵上不动，帮人清周围的怪；右键和它交易，放够它要的东西就给阿克罗尼特锭，然后离开。
 *
 * <h2>伤害</h2>
 * 免疫一切伤害（{@link #hurt} 一律返回 false），两种例外：
 * <ul>
 *   <li>带 {@code minecraft:bypasses_invulnerability} 的（{@code /kill}、掉出世界）照常算，会死；</li>
 *   <li>召唤者本人、创造模式玩家<b>直接</b>打它一下（近战的主目标，{@link #skipAttackInteraction}）→ 它离开（打发走），ESPE 不退。
 *       横扫打到的不算：召唤者在阵边上砍怪时不会把它误打走。</li>
 * </ul>
 * 原作任何伤害都让它消失（别人一拳、一支箭、苦力怕、隔壁恶魔的爆炸都能让召唤者的 666 ESPE 作废）。不怕火、不吃药水、不被爆炸推、不随水流走、
 * 推不动、拴不住、不会自然消失（原作 canDespawn = false）。
 *
 * <h2>守阵</h2>
 * 每 {@link #GUARD_INTERVAL}（40）刻看一次脚下那层 3×3 有没有成形的恶魔五芒星（原作 L119~132），没有就离开；
 * 五芒星的结论是「还不知道」、或者那几格有的在没加载的区块里时不算坏。刷怪蛋出来、不在阵上的照样 40 刻内离开（原作行为）。
 * 原作是打自己一下 OUT_OF_WORLD，这里直接调 {@link #depart}，不走伤害。
 *
 * <h2>清怪（原作 L133~143）</h2>
 * 每 {@code general.demon.clear_interval}（20）刻在自己周围 25³（1×1×1 外扩 {@code clear_radius} 12）里查一次，筛完随机挑一只
 * （{@link #isClearTarget}：敌对生物、活着、不是 Boss、不是谁都不许碰的、没起名字、不是捡过东西的、不在 {@code essentialcraft5:demon_immune} 标签里），
 * 挥手，用 {@code essentialcraft5:demon} 伤害源打它最大生命 × 1.6，再在它脚下来一次 {@code safeExplode}（威力 {@code explosion_power}，同一个伤害源、
 * 同一个目标判定：不破坏方块，玩家、宠物、村民、被动生物、装饰实体、掉落物都不受伤也不被推）。被它打死、炸死的不掉物品和经验（{@code no_death_loot}）。
 * <b>只清召唤者能动的格子里的怪</b>（第十批权限修补 A6，{@link #clearPermission}）：挑目标、爆炸波及的都要过这一关，12 格的范围伸进邻居领地时不清邻居刷怪塔里的怪；
 * 召唤者离线时只清他在线时探测过、能动的格子（{@link OwnerCellMemory}），没探测过的不清；刷怪蛋出来、没有召唤者的在多人服里不清怪。
 * 探过的结论都记下来（不能动的 5 分钟内不再探测），一次清怪的新探测最多 {@link #PROBES_PER_CLEAR} 格。
 * 原作打任何 EntityMob：凋灵挨 1.6 倍最大生命必死、下界之星不怕爆炸，等于凋灵农场；爆炸溅到旁边的玩家、宠物、村民，炸死的附带怪照常掉落。
 *
 * <h2>交易</h2>
 * 需求（{@link DemonWant}）召唤时由服务端掷一次（{@link DemonTrades#roll}），存盘，用同步数据带给客户端画界面（原作 DESIRED，不另加网络包）；
 * 刷怪蛋、/summon 出来的在第一刻掷。右键：召唤者本人、创造模式玩家能交易，没有召唤者的（刷怪蛋出来的）谁都能交易（batch7 D2 = A），都要是真玩家；
 * 假玩家不开界面（混合端给假玩家发界面包会一直攒在内存，policy §2.1）。不实现容器（原作实现了 IInventory，漏斗能往里塞），界面见 {@link DemonTradeMenu}。
 * 成交（{@link #completeTrade}）：放 doom 声、冒烟，报酬 {@code reward_min}~{@code reward_max} 个阿克罗尼特锭直接给交易的人（背包满了掉脚下；
 * 原作掉在地上谁都能捡），然后离开。
 *
 * <h2>契约之誉（第十三批 13a，batch13.md §3.4）</h2>
 * 要价从一条改成列表（{@link #wants}，一样或两样）：召唤者是「熟客」时五芒星给它两样（{@link DemonTrades#rollFor}），交哪样都算；
 * 成交记一笔契约之誉（{@link DemonStanding#recordTrade}，契主多给经验）。「血契」：能交易的人潜行、手持被俘灵魂右键它（主手、副手都行），
 * 交一个灵魂重抽要价，每只恶魔一次（{@link #bloodReroll}）；没到血契、或者没潜行时照常开交易界面。重抽开几样跟着召唤者走。
 * 存档：{@code wants}（列表）、{@code rerolled}；这之前召出来的恶魔存的是单条 {@code want}，读成只有一项的列表。
 * <p>
 * 「挚契」（13e，batch13.md §3.4）：召唤者本人潜行、主手或副手拿着影晶右键它立誓（{@link #swearOath}），交 {@code oath_cost} 个影晶换一个恶魔之誓，
 * 然后它照打发走那样离开。一只手拿影晶、另一只手拿被俘灵魂时，这次交互那只手里的那样优先，那只手是影晶而契约之誉不到挚契时改走血契（{@link #prefersOath}）；契约之誉关掉时不认立誓，照常开交易界面。
 *
 * <h2>声音</h2>
 * 环境音只用说话声，间隔 {@link #AMBIENT_SOUND_INTERVAL}（240）刻起算，平均约 14 秒一句、约两成时间在出声；召唤声只在真召唤成功时放
 * （五芒星那边放）。原作说话声和召唤声各一半、用原版默认的 80 刻，平均约 6 秒一句，加上片段长（说话约 3 秒、召唤约 4.6 秒），
 * 六成多的时间都在出声，召唤声当环境音还会让人以为又召了一只。打开交易界面的交易声同一只恶魔至少隔 {@link #TRADE_SOUND_COOLDOWN}（40）刻，
 * 反复开关界面时不叠在一起。
 *
 * <h2>存档</h2>
 * {@code wants}（需求列表；旧档的单条 {@code want} 照读）、{@code rerolled}（血契换过了）、{@code summoner}（召唤者 UUID）、{@code pentacle}（五芒星位置）。
 */
public class DemonEntity extends Mob {

    /** 守阵检查间隔（原作 ticksExisted % 40） */
    public static final int GUARD_INTERVAL = 40;
    /** 交易界面有效距离（原作 isUsableByPlayer：≤ 8 格） */
    public static final double TRADE_RANGE = 8D;
    /** 清怪那一下打目标最大生命的几倍（原作 × 1.6） */
    public static final float CLEAR_DAMAGE_FACTOR = 1.6F;
    /** 清怪不碰的实体（数据包可加，默认空） */
    public static final TagKey<EntityType<?>> IMMUNE = TagKey.create(Registries.ENTITY_TYPE, EssentialCraft5.id("demon_immune"));
    /** 两句环境音之间的最短间隔（原版默认 80）；Mob.baseTick 每刻按 1/1000 递增的几率出声，期望间隔约 240 + 40 刻 */
    public static final int AMBIENT_SOUND_INTERVAL = 240;
    /** 同一只恶魔两次交易声之间至少隔几刻 */
    public static final int TRADE_SOUND_COOLDOWN = 40;
    /** 一次清怪（挑目标加爆炸预检）以召唤者身份的新探测最多几格（记录命中的不算），超出的这次当成不能打 */
    public static final int PROBES_PER_CLEAR = 4;

    private static final EntityDataAccessor<ItemStack> DATA_WANT = SynchedEntityData.defineId(DemonEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> DATA_ANY_DAMAGE = SynchedEntityData.defineId(DemonEntity.class, EntityDataSerializers.BOOLEAN);
    /** 第二样要价（熟客；没有就是空堆）和它的 any_damage、血契换过没有（交易界面画给人看） */
    private static final EntityDataAccessor<ItemStack> DATA_WANT_2 = SynchedEntityData.defineId(DemonEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> DATA_ANY_DAMAGE_2 = SynchedEntityData.defineId(DemonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_REROLLED = SynchedEntityData.defineId(DemonEntity.class, EntityDataSerializers.BOOLEAN);
    /** 一只恶魔最多开几样要价（同步数据只留了两格） */
    public static final int MAX_WANTS = 2;
    /** 第十三批以前的存档：单条需求 */
    private static final String TAG_WANT = "want";
    private static final String TAG_WANTS = "wants";
    private static final String TAG_REROLLED = "rerolled";
    private static final String TAG_SUMMONER = "summoner";
    private static final String TAG_PENTACLE = "pentacle";
    /** 雨天头上冒烟：每刻最多几个（原作 20 个） */
    private static final int RAIN_SMOKE_PER_TICK = 2;

    /** GameTest 用：当作在 demon_immune 标签里的实体类型（标签是数据包，测试里改不了） */
    private static final Set<EntityType<?>> TEST_IMMUNE = new HashSet<>();

    private List<DemonWant> wants = List.of();
    /** 血契已经换过一次要价 */
    private boolean rerolled;
    @Nullable
    private UUID summoner;
    @Nullable
    private BlockPos pentacle;
    /** 已经在离开（成交、打发走、结构坏了）：不再交易、不再清怪 */
    private boolean leaving;
    /** 清怪前问「怪脚下那格召唤者能不能动」，召唤者离线时认它记下的格子（A6，见 {@link #clearPermission}；不存盘） */
    private final OwnerCellMemory ownerCells = new OwnerCellMemory();
    @Nullable
    private Integer testClearRadius;
    @Nullable
    private Integer testClearInterval;
    /** 上次放交易声的游戏时间（不存盘，读档后第一次照常放） */
    private long lastTradeSound = -TRADE_SOUND_COOLDOWN;

    public DemonEntity(EntityType<? extends DemonEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20D)
                .add(Attributes.MOVEMENT_SPEED, 0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1D);
    }

    /** 只转头看人（观感），不走动 */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new LookAtPlayerGoal(this, Player.class, 8F));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_WANT, ItemStack.EMPTY);
        entityData.define(DATA_ANY_DAMAGE, false);
        entityData.define(DATA_WANT_2, ItemStack.EMPTY);
        entityData.define(DATA_ANY_DAMAGE_2, false);
        entityData.define(DATA_REROLLED, false);
    }

    // ------------------------------------------------------------------
    // 访问
    // ------------------------------------------------------------------

    /** 它要的第一样；客户端上从同步数据反推，还没掷（或交易池是空的）时为 null */
    @Nullable
    public DemonWant want() {
        List<DemonWant> list = wants();
        return list.isEmpty() ? null : list.get(0);
    }

    /** 它开的全部要价（一样或两样，交哪样都算）；客户端上从同步数据反推，还没掷时为空 */
    public List<DemonWant> wants() {
        if (!level().isClientSide) {
            return wants;
        }
        List<DemonWant> out = new ArrayList<>(MAX_WANTS);
        DemonWant first = DemonWant.fromDisplay(entityData.get(DATA_WANT), entityData.get(DATA_ANY_DAMAGE));
        if (first != null) {
            out.add(first);
        }
        DemonWant second = DemonWant.fromDisplay(entityData.get(DATA_WANT_2), entityData.get(DATA_ANY_DAMAGE_2));
        if (second != null) {
            out.add(second);
        }
        return out;
    }

    /** 只开一样要价（null = 什么都不要，下一刻重掷） */
    public void setWant(@Nullable DemonWant want) {
        setWants(want == null ? List.of() : List.of(want));
    }

    /** 换掉全部要价（多于 {@link #MAX_WANTS} 样的只留前面的） */
    public void setWants(List<DemonWant> list) {
        List<DemonWant> kept = new ArrayList<>(MAX_WANTS);
        for (DemonWant want : list) {
            if (want != null && kept.size() < MAX_WANTS) {
                kept.add(want);
            }
        }
        this.wants = List.copyOf(kept);
        DemonWant first = kept.isEmpty() ? null : kept.get(0);
        DemonWant second = kept.size() < 2 ? null : kept.get(1);
        entityData.set(DATA_WANT, first == null ? ItemStack.EMPTY : first.displayStack());
        entityData.set(DATA_ANY_DAMAGE, first != null && first.anyDamage());
        entityData.set(DATA_WANT_2, second == null ? ItemStack.EMPTY : second.displayStack());
        entityData.set(DATA_ANY_DAMAGE_2, second != null && second.anyDamage());
    }

    /** 血契已经换过一次要价（两端都能读） */
    public boolean hasRerolled() {
        return level().isClientSide ? entityData.get(DATA_REROLLED) : rerolled;
    }

    private void setRerolled(boolean value) {
        rerolled = value;
        entityData.set(DATA_REROLLED, value);
    }

    @Nullable
    public UUID summoner() {
        return summoner;
    }

    public void setSummoner(@Nullable UUID summoner) {
        this.summoner = summoner;
    }

    @Nullable
    public BlockPos pentacle() {
        return pentacle;
    }

    public void setPentacle(@Nullable BlockPos pentacle) {
        this.pentacle = pentacle == null ? null : pentacle.immutable();
    }

    /** 还在、没在离开：能交易、能清怪 */
    public boolean isAvailable() {
        return isAlive() && !isRemoved() && !leaving;
    }

    /** 这名玩家能不能和它交易（D2 = A）：真玩家，并且是召唤者本人、创造模式，或者它没有召唤者 */
    public boolean canTrade(Player player) {
        return ECProtection.isRealPlayer(player) && (summoner == null || summoner.equals(player.getUUID()) || player.isCreative());
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (!isAvailable()) {
            return;
        }
        if (wants.isEmpty()) {
            // 刷怪蛋、指令召出来的，或者存档里的要价读不回来：召唤者在线就按他的契约之誉掷，不在线只开一样
            setWants(DemonTrades.rollFor(onlineSummoner(), random));
        }
        if (tickCount % GUARD_INTERVAL == 0 && !guardHolds()) {
            depart();
            return;
        }
        int interval = clearInterval();
        int radius = clearRadius();
        if (radius > 0 && interval > 0 && tickCount % interval == 0) {
            clearOnce(radius);
        }
    }

    /** 雨天头上冒烟（原作每刻 20 个，这里最多 2 个，只在客户端） */
    private void clientTick() {
        if (level().isRaining() && level().canSeeSky(blockPosition().above())) {
            for (int i = 0; i < RAIN_SMOKE_PER_TICK; i++) {
                level().addParticle(ParticleTypes.SMOKE, getX() + (random.nextDouble() - 0.5D), getY() + 1.3D + random.nextDouble() * 2D,
                        getZ() + (random.nextDouble() - 0.5D), 0D, 0.1D, 0D);
            }
        }
    }

    @Nullable
    private ServerPlayer onlineSummoner() {
        if (summoner == null || level().getServer() == null) {
            return null;
        }
        return level().getServer().getPlayerList().getPlayer(summoner);
    }

    /** 脚下那层 3×3 里有成形（或还不知道）的恶魔五芒星，或者有格子没加载 */
    private boolean guardHolds() {
        BlockPos feet = BlockPos.containing(getX(), getY(), getZ());
        boolean unknown = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos at = feet.offset(dx, 0, dz);
                if (!ECLevels.isFullyLoaded(level(), at)) {
                    unknown = true;
                    continue;
                }
                if (level().getBlockEntity(at) instanceof DemonicPentacleBlockEntity be
                        && be.guardState() != DemonicPentacleBlockEntity.GuardState.BROKEN) {
                    return true;
                }
            }
        }
        return unknown;
    }

    // ------------------------------------------------------------------
    // 清怪
    // ------------------------------------------------------------------

    /** 立即清一次（展示厅也用它）：范围里随机挑一只合格的怪，打它、在它脚下爆炸；没有合格的就什么都不做。返回被打的那只 */
    @Nullable
    public LivingEntity clearOnce(int radius) {
        if (!(level() instanceof ServerLevel server) || !isAvailable()) {
            return null;
        }
        AABB area = new AABB(getX() - 0.5D, getY() - 0.5D, getZ() - 0.5D, getX() + 0.5D, getY() + 0.5D, getZ() + 0.5D).inflate(radius);
        List<LivingEntity> targets = server.getEntitiesOfClass(LivingEntity.class, area, DemonEntity::isClearTarget);
        if (targets.isEmpty()) {
            return null;
        }
        // 从随机的一只起挨个问权限（A6），第一只召唤者能动的格子里的就是这次的目标：照样是在能打的里随机挑一只。
        // 先只看记录，已知不能动的直接跳过；要新探测的（挑目标加爆炸预检）一次清怪合计最多 PROBES_PER_CLEAR 格，用完的这次当不能打
        int[] probes = {PROBES_PER_CLEAR};
        LivingEntity target = null;
        int start = random.nextInt(targets.size());
        for (int i = 0; i < targets.size() && target == null; i++) {
            LivingEntity candidate = targets.get((start + i) % targets.size());
            if (mayClear(candidate, probes)) {
                target = candidate;
            }
        }
        if (target == null) {
            return null;
        }
        swing(InteractionHand.MAIN_HAND);
        DamageSource source = ECDamageTypes.demon(this);
        target.hurt(source, target.getMaxHealth() * CLEAR_DAMAGE_FACTOR);
        float power = ECDemon.Config.explosionPower();
        if (power > 0F) {
            // 爆炸只伤同一类怪里、召唤者能动的格子里的（原版爆炸对 2 × 威力以内的实体算伤害）；先问完再炸，不在爆炸事件里发探测
            Set<LivingEntity> affected = Collections.newSetFromMap(new IdentityHashMap<>());
            AABB blast = target.getBoundingBox().inflate(power * 2D + 1D);
            for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, blast, DemonEntity::isClearTarget)) {
                if (mayClear(near, probes)) {
                    affected.add(near);
                }
            }
            Predicate<LivingEntity> affects = affected::contains;
            ECProtection.safeExplode(server, this, source, target.position(), power, affects);
        }
        return target;
    }

    /**
     * A6：这只怪脚下那格召唤者能不能动（{@link OwnerCellMemory}，预算按这只恶魔算）。召唤者在线用他的真身探测，能动的格子记下来；
     * 召唤者离线只认记下的格子，没记过的不清；没有召唤者的（刷怪蛋出来的）在多人服里不清怪，单人里按房主算。只有 ALLOW 才清。
     */
    public ECProtection.Verdict clearPermission(LivingEntity entity) {
        return ownerCells.check(level(), summoner, entity.blockPosition(), this);
    }

    /**
     * 清怪时用的 {@link #clearPermission}：先只看记录（{@link OwnerCellMemory#peek}，已知不能动的、召唤者离线又没记过的不探测），
     * 要新探测时花一格本次清怪的上限 {@code probes[0]}，用完了当成不能打。混合端上被拒的探测会让召唤者收到领地插件的「没有权限」，
     * 邻居刷怪塔在范围里时不能每秒刷他十几条
     */
    private boolean mayClear(LivingEntity entity, int[] probes) {
        ECProtection.Verdict known = ownerCells.peek(level(), summoner, entity.blockPosition());
        if (known != ECProtection.Verdict.UNKNOWN) {
            return known == ECProtection.Verdict.ALLOW;
        }
        if (probes[0] <= 0) {
            return false;
        }
        probes[0]--;
        return clearPermission(entity) == ECProtection.Verdict.ALLOW;
    }

    /** 清怪用的「召唤者能动的格子」记录（GameTest 看） */
    public OwnerCellMemory ownerCells() {
        return ownerCells;
    }

    /**
     * 清怪会打的生物：敌对生物（{@link Enemy}）、活着、不是恶魔、不在 {@code #forge:bosses}（被它打死的不掉落对 Boss 不拦，不排除就是下界之星农场）、
     * 不是谁都不许碰的（{@link ECEntityRules#isSpared}：有名字、驯服、拴着……）、没有自定义名字、{@code isPersistenceRequired()} 为假
     * （1.20 的怪捡起东西后会设，捡了玩家的东西的怪不打）、不在 {@code essentialcraft5:demon_immune} 里。爆炸的过滤用同一个判定。
     */
    public static boolean isClearTarget(LivingEntity entity) {
        if (!(entity instanceof Enemy) || entity instanceof DemonEntity || entity instanceof Player || !entity.isAlive()) {
            return false;
        }
        EntityType<?> type = entity.getType();
        if (type.is(Tags.EntityTypes.BOSSES) || type.is(IMMUNE) || TEST_IMMUNE.contains(type)) {
            return false;
        }
        if (entity.hasCustomName() || ECEntityRules.isSpared(entity)) {
            return false;
        }
        return !(entity instanceof Mob mob && mob.isPersistenceRequired());
    }

    private int clearRadius() {
        return testClearRadius != null ? testClearRadius : ECDemon.Config.clearRadius();
    }

    private int clearInterval() {
        return testClearInterval != null ? testClearInterval : ECDemon.Config.clearInterval();
    }

    /**
     * GameTest 用：这一只恶魔的清怪范围和间隔（null = 用配置）。同一批测试的结构挨得很近，默认 12 格会打到隔壁测试里的怪，
     * 测试里生成的恶魔都要先设成 0 或很小的范围。不存盘。
     */
    public void setTestClearOverrides(@Nullable Integer radius, @Nullable Integer interval) {
        testClearRadius = radius;
        testClearInterval = interval;
    }

    /** GameTest 用：把一种实体当成在 demon_immune 标签里（null 清空） */
    public static void setTestImmune(@Nullable EntityType<?> type) {
        TEST_IMMUNE.clear();
        if (type != null) {
            TEST_IMMUNE.add(type);
        }
    }

    // ------------------------------------------------------------------
    // 伤害与离开
    // ------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(source);
    }

    /**
     * 玩家近战的主目标是它时（Player.attack 在伤害之前调，横扫打到的不调）：召唤者本人、创造模式玩家打一下 = 打发走。
     * 返回 true 时原版不再往下算这次攻击（不挥空、不扣耐久）。
     */
    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (!(attacker instanceof Player player)) {
            return false;
        }
        boolean dismisser = player.isCreative() || (summoner != null && summoner.equals(player.getUUID()));
        if (!dismisser) {
            return false;
        }
        if (!level().isClientSide && ECProtection.isRealPlayer(player)) {
            depart();
        }
        return true;
    }

    /** 离开：放离开声、冒烟、消失（被打发走、结构坏了）。重复调用只算一次 */
    public void depart() {
        if (leaving || isRemoved()) {
            return;
        }
        leaving = true;
        playSound(ECDemon.SOUND_DEPART.get(), getSoundVolume(), getVoicePitch());
        smoke();
        discard();
    }

    /**
     * 成交（交易界面在扣掉玩家的东西之后调）：放 doom 声、冒烟，报酬直接给 player，然后消失。
     * 已经在离开时返回 false、什么都不给（界面在扣东西之前先查 {@link #isAvailable}）。
     */
    public boolean completeTrade(ServerPlayer player) {
        if (!isAvailable()) {
            return false;
        }
        leaving = true;
        level().playSound(null, getX(), getY(), getZ(), ECDemon.SOUND_DOOM.get(), getSoundSource(), getSoundVolume(), getVoicePitch());
        smoke();
        int min = ECDemon.Config.rewardMin();
        int max = ECDemon.Config.rewardMax();
        int reward = min + random.nextInt(max - min + 1);
        ECItemEntities.giveOrDrop(player, new ItemStack(ECDemonic.ACKRONITE_INGOT.get(), reward));
        // 契约之誉 +1（契主另给经验，阿克罗尼特不多给）
        DemonStanding.recordTrade(player);
        discard();
        return true;
    }

    /** 原作逐个生成 400 个烟雾粒子，这里一次发一包 */
    private void smoke() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() / 2D, getZ(), 40,
                    getBbWidth() / 2D, getBbHeight() / 3D, getBbWidth() / 2D, 0.02D);
        }
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !ECProtection.isRealPlayer(serverPlayer) || !isAvailable()) {
            return InteractionResult.PASS;
        }
        if (!canTrade(serverPlayer)) {
            serverPlayer.displayClientMessage(Component.translatable("message.essentialcraft5.demon.not_summoner")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        if (serverPlayer.isShiftKeyDown()) {
            ItemStack crystal = DemonStanding.enabled() ? oathCrystal(serverPlayer, hand) : null;
            ItemStack soul = DemonStanding.atLeast(serverPlayer, DemonStanding.Tier.BLOOD) ? bloodSoul(serverPlayer, hand) : null;
            if (crystal != null && soul != null && !prefersOath(serverPlayer, hand, crystal)) {
                crystal = null;
            }
            if (crystal != null) {
                swearOath(serverPlayer);
                return InteractionResult.CONSUME;
            }
            if (soul != null) {
                bloodReroll(serverPlayer, soul);
                return InteractionResult.CONSUME;
            }
        }
        if (wants.isEmpty()) {
            serverPlayer.displayClientMessage(Component.translatable("message.essentialcraft5.demon.wants_nothing")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        long now = level().getGameTime();
        if (now - lastTradeSound >= TRADE_SOUND_COOLDOWN) {
            lastTradeSound = now;
            playSound(ECDemon.SOUND_TRADE.get(), getSoundVolume(), getVoicePitch());
        }
        NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider((id, inventory, p) -> new DemonTradeMenu(id, inventory, this),
                getDisplayName()), buf -> buf.writeVarInt(getId()));
        return InteractionResult.CONSUME;
    }

    /** 记着一种实体的被俘灵魂（空灵魂不算） */
    private static boolean isRealSoul(ItemStack stack) {
        return stack.is(ECDemonic.CAPTURED_SOUL.get()) && CapturedSoulItem.entityId(stack) != null;
    }

    /**
     * 血契交哪一堆灵魂：这只手拿着的；主手那次主手不是灵魂时看副手。原版先用主手交互，主手这次返回 CONSUME（开了界面）副手就轮不到，
     * 所以副手拿灵魂、主手拿剑的人只能在主手这次认出来。都不是返回 null。
     */
    @Nullable
    private static ItemStack bloodSoul(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (isRealSoul(held)) {
            return held;
        }
        ItemStack offhand = player.getOffhandItem();
        return hand == InteractionHand.MAIN_HAND && isRealSoul(offhand) ? offhand : null;
    }

    /** 立誓用哪一只手里的影晶：同 {@link #bloodSoul}，这只手；主手那次主手不是影晶时看副手。都不是返回 null */
    @Nullable
    private static ItemStack oathCrystal(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ECHoannaMaterials.UMBRAL_CRYSTAL.get())) {
            return held;
        }
        ItemStack offhand = player.getOffhandItem();
        return hand == InteractionHand.MAIN_HAND && offhand.is(ECHoannaMaterials.UMBRAL_CRYSTAL.get()) ? offhand : null;
    }

    /**
     * 两只手一只拿影晶、一只拿被俘灵魂时走哪条（调用方已查过两样都能用）：这次交互那只手里的那样优先；
     * 这只手拿的是影晶、可契约之誉还不到挚契时改走血契（立誓反正立不成，灵魂就在另一只手上）。
     */
    private static boolean prefersOath(Player player, InteractionHand hand, ItemStack crystal) {
        return crystal == player.getItemInHand(hand) && DemonStanding.atLeast(player, DemonStanding.Tier.OATH);
    }

    /** 背包里（含副手、盔甲格）一共有几个影晶 */
    public static int umbralCount(Player player) {
        return player.getInventory().countItem(ECHoannaMaterials.UMBRAL_CRYSTAL.get());
    }

    /**
     * 挚契立誓（服务端；调用方已查过能交易、潜行、手里拿着影晶）：只有召唤者本人能立誓（第七批 D2；没有召唤者的、创造模式的旁人都不行），
     * 契约之誉要到挚契（{@code general.demon.standing.oath}，70），背包里影晶一共要有 {@code oath_cost}（16）个——分在几格都行，从背包里逐格扣。
     * 都满足时：扣影晶（创造模式不扣）、给 1 个恶魔之誓（背包满了掉脚下），然后照打发走那样离开（{@link #depart}）。
     * 不算一笔交易：不加契约之誉、不给阿克罗尼特和契主经验。哪一条不满足都什么都不扣，动作栏说差什么。
     *
     * @return 立成了没有
     */
    public boolean swearOath(ServerPlayer player) {
        if (!isAvailable()) {
            return false;
        }
        if (summoner == null || !summoner.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.essentialcraft5.demon_oath.not_summoner")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        int standing = DemonStanding.get(player);
        if (!DemonStanding.atLeast(standing, DemonStanding.Tier.OATH)) {
            player.displayClientMessage(Component.translatable("message.essentialcraft5.demon_oath.standing",
                    DemonStanding.Tier.OATH.threshold(), standing).withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        int cost = Math.max(1, DemonStanding.Config.oathCost());
        boolean free = player.getAbilities().instabuild;
        int have = umbralCount(player);
        if (!free && have < cost) {
            player.displayClientMessage(Component.translatable("message.essentialcraft5.demon_oath.crystals", cost, have)
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (!free) {
            takeUmbral(player, cost);
        }
        ECItemEntities.giveOrDrop(player, new ItemStack(ECShade.DEMON_OATH.get()));
        level().playSound(null, getX(), getY(), getZ(), ECDemon.SOUND_DOOM.get(), getSoundSource(), getSoundVolume(), 0.7F);
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + getBbHeight() * 0.6D, getZ(), 24,
                    getBbWidth() / 2D, getBbHeight() / 4D, getBbWidth() / 2D, 0.02D);
        }
        player.displayClientMessage(Component.translatable("message.essentialcraft5.demon_oath.done", cost)
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        depart();
        return true;
    }

    /** 从背包里逐格扣 count 个影晶（调用方已数过够） */
    private static void takeUmbral(Player player, int count) {
        Inventory inventory = player.getInventory();
        int left = count;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ECHoannaMaterials.UMBRAL_CRYSTAL.get())) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        inventory.setChanged();
    }

    /**
     * 血契（服务端；调用方已查过能交易、到了血契、手里是灵魂）：每只恶魔一次，交一个灵魂（创造模式不扣）重抽要价。
     * 开几样跟着召唤者走（{@link #rerollsAsRegular}）：召唤者本人重抽按他现在的契约之誉（熟客照样两样）；
     * 别人（只可能是创造模式的玩家）替他重抽时不能让他多出或丢掉熟客的待遇。已经换过、或者交易池空了掷不出东西时什么都不扣，动作栏说明。
     *
     * @return 换成了没有
     */
    public boolean bloodReroll(ServerPlayer player, ItemStack soul) {
        if (!isAvailable()) {
            return false;
        }
        if (rerolled) {
            player.displayClientMessage(Component.translatable("message.essentialcraft5.demon_standing.blood.used")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        List<DemonWant> fresh = DemonTrades.rollFor(rerollsAsRegular(player), random);
        if (fresh.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.essentialcraft5.demon.wants_nothing")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (!player.getAbilities().instabuild) {
            soul.shrink(1);
        }
        setRerolled(true);
        setWants(fresh);
        playSound(ECDemon.SOUND_SAY.get(), getSoundVolume(), getVoicePitch());
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SOUL, getX(), getY() + getBbHeight() * 0.6D, getZ(), 12,
                    getBbWidth() / 2D, getBbHeight() / 4D, getBbWidth() / 2D, 0.02D);
        }
        player.displayClientMessage(Component.translatable("message.essentialcraft5.demon_standing.blood.done")
                .withStyle(ChatFormatting.DARK_RED), true);
        return true;
    }

    /**
     * 血契重抽开不开第二样：没有召唤者、或者召唤者自己重抽时按重抽的人；召唤者在线时按召唤者；召唤者不在线时照现在开几样就开几样
     * （召出来时就是按他的契约之誉开的），契约之誉关掉了一律一样。
     */
    private boolean rerollsAsRegular(ServerPlayer player) {
        if (summoner == null || summoner.equals(player.getUUID())) {
            return DemonStanding.atLeast(player, DemonStanding.Tier.REGULAR);
        }
        ServerPlayer owner = onlineSummoner();
        if (owner != null) {
            return DemonStanding.atLeast(owner, DemonStanding.Tier.REGULAR);
        }
        return DemonStanding.enabled() && wants.size() >= 2;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    // ------------------------------------------------------------------
    // 声音
    // ------------------------------------------------------------------

    /** 环境音只用说话声；召唤声留给真召唤（见类注释「声音」） */
    @Override
    protected SoundEvent getAmbientSound() {
        return ECDemon.SOUND_SAY.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return AMBIENT_SOUND_INTERVAL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ECDemon.SOUND_DEPART.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ECDemon.SOUND_DEPART.get();
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!wants.isEmpty()) {
            ListTag list = new ListTag();
            for (DemonWant want : wants) {
                list.add(want.save());
            }
            tag.put(TAG_WANTS, list);
        }
        if (rerolled) {
            tag.putBoolean(TAG_REROLLED, true);
        }
        if (summoner != null) {
            tag.putUUID(TAG_SUMMONER, summoner);
        }
        if (pentacle != null) {
            tag.put(TAG_PENTACLE, NbtUtils.writeBlockPos(pentacle));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // 需求里的物品或实体已经不存在的那一样丢掉，一样都不剩时下一刻重掷；第十三批以前的存档只有单条 want，读成只有一项的列表
        List<DemonWant> loaded = new ArrayList<>(MAX_WANTS);
        if (tag.contains(TAG_WANTS, Tag.TAG_LIST)) {
            ListTag list = tag.getList(TAG_WANTS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                DemonWant want = DemonWant.load(list.getCompound(i));
                if (want != null) {
                    loaded.add(want);
                }
            }
        } else if (tag.contains(TAG_WANT, Tag.TAG_COMPOUND)) {
            DemonWant want = DemonWant.load(tag.getCompound(TAG_WANT));
            if (want != null) {
                loaded.add(want);
            }
        }
        setWants(loaded);
        setRerolled(tag.getBoolean(TAG_REROLLED));
        summoner = tag.hasUUID(TAG_SUMMONER) ? tag.getUUID(TAG_SUMMONER) : null;
        pentacle = tag.contains(TAG_PENTACLE, Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(tag.getCompound(TAG_PENTACLE)) : null;
    }
}
