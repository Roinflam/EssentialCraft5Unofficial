package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.network.WingsFlight;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 魔力之翼（原作 ItemMagicalWings，100 行）：戴在 Curios 腰带槽（原作 Baubles BELT），也能拿在手上用，放在背包里也有用。自带 {@value CharmsMRU#MAX_MRU} MRU。
 * <ul>
 *   <li><b>戴着</b>：按住跳跃往上飞（每刻竖直速度 +{@value #LIFT}）；不潜行时下落速度超过 {@value #SLOW_FALL_SPEED} 格 / 刻就压到这个速度（缓降，不摔伤）；
 *       移速 +{@value #SPEED_BONUS}（加法，玩家基础 0.1，等于走路快一倍）。飞、缓降各 {@value #COST_PER_TICK} MRU / 刻。</li>
 *   <li><b>拿在主手或副手</b>：不潜行时一直往上飞（每刻 +{@value #LIFT}），潜行时以 {@value #SLOW_FALL_SPEED} 格 / 刻往下降，{@value #COST_PER_TICK} MRU / 刻。</li>
 *   <li><b>放在背包里</b>（不拿也不戴；原作 onUpdate 的第二个分支，对物品栏每一格都生效）：按住潜行、下落速度超过 {@value #SLOW_FALL_SPEED} 格 / 刻时
 *       压到这个速度（缓降，不摔伤），{@value #COST_PER_TICK} MRU / 刻。放了几对时只有第一对付得起的结算。</li>
 * </ul>
 * <b>谁算什么</b>（玩家移动本来就是客户端说了算）：
 * <ul>
 *   <li>客户端（本地玩家）：改速度、出粒子。戴着时由 client/CharmsClient 每刻（移动之前）调 {@link #clientWornTick}，
 *       按住跳跃的开关走框架的 {@link WingsFlight#clientTick}（C2SWingsPacket 只带一个布尔）；手持、背包里的在 {@link #inventoryTick} 里做
 *       （Player.aiStep 先 tick 物品栏再移动）。够不够 MRU 用 {@link CharmsMRU#canAfford} 预判，不扣。</li>
 *   <li>服务端：只扣 MRU、清摔落距离（摔伤由服务端按它算）。戴着时在 {@link #curioTick}：{@code WingsFlight.isActive} 的玩家每刻扣，扣不动就
 *       {@link WingsFlight#stop}（通知客户端停）；没在往上飞时，摔落距离涨到 {@value #SLOW_FALL_TRIGGER} 以上（每刻下落 ≥ 约 0.2 格）就扣一次并清零。
 *       手持时在 {@link #inventoryTick} 每刻扣；背包里的也在 {@link #inventoryTick}，按摔落距离判断，和戴着的缓降一样。</li>
 * </ul>
 * <b>按秒预扣</b>（P2）：MRU 存在物品 NBT 里，每刻扣 1 会让 Curios 每刻重发整件饰品、重算一遍移速修饰符（各发一个包给周围的人），
 * 手持时每刻重发一次物品栏格子。所以每 {@value #PREPAY_TICKS} 刻才真扣一次：先扣 {@value #PREPAY_TICKS} MRU，接下来 {@value #PREPAY_TICKS} 刻不再扣；
 * 付不起 {@value #PREPAY_TICKS} 时退回按刻扣 1（最后那点 MRU 也能用完）。平均仍是 1 MRU / 刻。预付的余数按玩家记在服务端内存里（几份共用），下线清空（最多损失 19 MRU）。
 * 一点都付不起时 {@value #RETRY_TICKS} 刻内不再重试（拿着没电的翅膀时不每刻翻两遍物品栏）；这个等待按 {@link Use}（手持、戴着、背包）分开记，
 * 手里那对没电不会连带戴着的那对。
 * <p>
 * 移植改动：
 * <ul>
 *   <li>原作戴着往上飞<b>不扣 MRU</b>（扣费写在一个永远匹配不上的收包分支里），也只看翅膀自己的 MRU；现在 1 MRU / 刻，和手持一致，也会用身上灵魂储存里的 MRU。</li>
 *   <li>原作那个 C2S 包带「坐标 + 任意玩家 UUID」，服务端直接 setPosition（伪造就能传送任何人），不移植（policy §3.3）；改用只带布尔的 C2SWingsPacket。</li>
 *   <li>移速：原作每刻重加一次属性修饰符；现在走 Curios 槽位属性修饰符，戴上加、摘下去；运行中改了禁用配置由 {@link #syncSpeedModifier} 对账。</li>
 *   <li>粒子只在客户端生成（原作服务端也调 spawnParticle，没有效果）。</li>
 *   <li>手里拿着能用的翅膀（{@link #heldWingsUsable}：它自己有 MRU，或身上灵魂储存够付）时只按手持算，戴着的、背包里的都不管（原作几份都扣、都加速度）；
 *       手里那对没电时，戴着的、背包里的照常起作用。两只手都拿着只算主手那对。</li>
 *   <li>背包里放了几对时只扣一对（原作每对都扣）。</li>
 *   <li>创造 / 旁观飞行、鞘翅滑翔、骑乘时戴着的、背包里的翅膀不起作用（原作没判断，会和这些移动方式打架）。</li>
 * </ul>
 */
public class MagicalWingsItem extends Item implements ICurioItem, CreativeVariants {

    public static final int COST_PER_TICK = 1;
    /** 一次预扣几刻的 MRU（见类注释） */
    public static final int PREPAY_TICKS = 20;
    /** 往上飞：每刻竖直速度加这么多（原作 motionY += 0.1） */
    public static final double LIFT = 0.1D;
    /** 缓降：下落速度压到这个值（原作 motionY = -0.2） */
    public static final double SLOW_FALL_SPEED = -0.2D;
    /** 服务端：摔落距离到这个值就算在缓降，扣 MRU 并清零（客户端缓降时每刻下落 0.2 格；留一点浮点余量） */
    public static final float SLOW_FALL_TRIGGER = 0.19F;
    /** 戴着时的移速加成（原作 applyPlayerModifier(MOVEMENT_SPEED, "EC300", 0.1)，加法） */
    public static final double SPEED_BONUS = 0.1D;
    public static final String SPEED_MODIFIER_NAME = "essentialcraft5:magical_wings";

    /** 原作 REDSTONE 粒子带参数 (0, 1, 1)：1.12 红石粒子红色为 0 时当 1，实际是白色 */

    /** 付不起以后隔多少刻再试（拿着没电的翅膀时不每刻翻一遍物品栏） */
    public static final int RETRY_TICKS = 20;

    /** 这一刻是哪一份翅膀在付：手持、戴着、放在背包里。付不起以后的重试等待三份分开记，一份没电不连带别的 */
    public enum Use {
        HELD, WORN, PACK
    }

    /** 服务端每个玩家的结算状态：预付了还没用掉的刻数（几份共用）、每一份付不起以后下次再试的游戏时间（按 {@link Use} 的序号） */
    private static final class Budget {
        int prepaid;
        final long[] retryAt = new long[Use.values().length];
    }

    /** 只在服务端主线程读写；按玩家对象弱引用，下线、重生（换了新的玩家对象）就清掉 */
    private static final Map<Player, Budget> BUDGETS = new WeakHashMap<>();

    public MagicalWingsItem(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return CharmsMRU.storage(stack);
    }

    // ---------------------------------------------------------------- 扣费（服务端）

    /**
     * 服务端：付这一刻的 1 MRU（按秒预扣，见类注释）。创造模式免费。付不起返回 false，之后 {@value #RETRY_TICKS} 刻内这一份（use）直接返回 false、
     * 不再翻物品栏；别的几份不受影响（手里拿着没电的翅膀时，戴着的那对照常付）。
     * wings 是正在用的那对翅膀（身上的灵魂储存不够时扣它自己的）。
     */
    public static boolean payTick(Player player, ItemStack wings, Use use) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        Budget budget = BUDGETS.computeIfAbsent(player, p -> new Budget());
        if (budget.prepaid > 0) {
            budget.prepaid--;
            return true;
        }
        long now = player.level().getGameTime();
        if (now < budget.retryAt[use.ordinal()]) {
            return false;
        }
        if (MRUDrain.playerUseMRU(player, wings, COST_PER_TICK * PREPAY_TICKS)) {
            budget.prepaid = PREPAY_TICKS - 1;
            return true;
        }
        if (MRUDrain.playerUseMRU(player, wings, COST_PER_TICK)) {
            return true;
        }
        budget.retryAt[use.ordinal()] = now + RETRY_TICKS;
        return false;
    }

    /** 服务端：这个玩家还预付着几刻（GameTest 用） */
    public static int prepaidTicks(Player player) {
        Budget budget = BUDGETS.get(player);
        return budget == null ? 0 : budget.prepaid;
    }

    // ---------------------------------------------------------------- 手持、背包里

    /** 这对手里的翅膀现在算不算在用：主手，或者副手而主手不是翅膀；旁观者和被配置关掉的不算 */
    public static boolean isActiveHeld(Player player, ItemStack stack) {
        if (player.isSpectator() || CuriosCompat.isDisabled(stack)) {
            return false;
        }
        if (player.getMainHandItem() == stack) {
            return true;
        }
        return player.getOffhandItem() == stack && !(player.getMainHandItem().getItem() instanceof MagicalWingsItem);
    }

    /** 手里正在起作用的那一对：主手是翅膀就是主手，否则副手是翅膀就是副手（和 {@link #isActiveHeld} 同一个规则），都不是给空栈 */
    private static ItemStack heldWings(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof MagicalWingsItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        return off.getItem() instanceof MagicalWingsItem ? off : ItemStack.EMPTY;
    }

    /**
     * 手里那对翅膀现在能不能用：在起作用（{@link #isActiveHeld}）而且付得起这一刻（{@link CharmsMRU#canAfford}，不扣）。
     * 能用时戴着的、背包里的都让给手持；手里那对没电（自己没 MRU、身上灵魂储存也不够）时它们照常起作用，不会拿着一对空翅膀就两对都失灵。
     * 客户端戴着的每刻判断（先看手里是不是翅膀、再看它自己的 MRU，一般不用翻物品栏），服务端只在真的在下落时才判断。
     */
    public static boolean heldWingsUsable(Player player) {
        ItemStack held = heldWings(player);
        return !held.isEmpty() && isActiveHeld(player, held) && CharmsMRU.canAfford(player, held, COST_PER_TICK);
    }

    /**
     * 手持飞行（原作 onUpdate 第一个分支）和背包里的潜行缓降（第二个分支）。
     * Curios 也会给饰品槽里的翅膀调这个方法（slot = -1）：它不是手里那一个栈，不算手持；slot &lt; 0 也不算背包。
     * 物品栏（主背包、盔甲、副手）调进来的 slot 都 &gt;= 0（Forge 换成各自列表里的下标）。
     * 客户端只处理本地玩家（别人的移动由服务端同步过来）。
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(entity instanceof Player player)) {
            return;
        }
        if (isActiveHeld(player, stack)) {
            tickHeld(player, stack);
        } else if (slot >= 0) {
            tickInPack(player, stack);
        }
    }

    private static void tickHeld(Player player, ItemStack stack) {
        if (player.level().isClientSide) {
            if (player.isLocalPlayer() && CharmsMRU.canAfford(player, stack, COST_PER_TICK)) {
                Vec3 motion = player.getDeltaMovement();
                player.setDeltaMovement(motion.x, player.isShiftKeyDown() ? SLOW_FALL_SPEED : motion.y + LIFT, motion.z);
                player.resetFallDistance();
                particles(player);
            }
            return;
        }
        if (payTick(player, stack, Use.HELD)) {
            player.resetFallDistance();
        }
    }

    /**
     * 放在背包里（不拿不戴）：按住潜行往下掉时缓降，{@value #COST_PER_TICK} MRU / 刻（原作 onUpdate 第二个分支：
     * motionY &lt; -0.2 而且在潜行就扣 1 MRU、压到 -0.2、清摔落距离）。手里拿着能用的翅膀时让给手持（它潜行时本来就往下降）。
     * <ul>
     *   <li>客户端（本地玩家）：下落速度超过 {@value #SLOW_FALL_SPEED} 格 / 刻、这对付得起时压到这个速度、出粒子。</li>
     *   <li>服务端：摔落距离到 {@value #SLOW_FALL_TRIGGER} 时扣一次并清零（和戴着的缓降同一个判断）。</li>
     * </ul>
     * 放了几对时每对各判断一次，但第一对付得起的结算以后速度已经压住、摔落距离已经清零，后面几对的条件不再成立，不会重复扣。
     * 服务端也先用 canAfford 看这一对付不付得起再扣（只在潜行下落时才走到这里，开销可以忽略）：前面一对没电不会触发重试等待、挡住后面有电的，
     * 也和客户端的判断一致。
     */
    private static void tickInPack(Player player, ItemStack stack) {
        if (!player.isShiftKeyDown() || player.onGround() || !canUseWorn(player) || CuriosCompat.isDisabled(stack)) {
            return;
        }
        if (player.level().isClientSide) {
            if (!player.isLocalPlayer()) {
                return;
            }
            Vec3 motion = player.getDeltaMovement();
            if (motion.y < SLOW_FALL_SPEED && !heldWingsUsable(player) && CharmsMRU.canAfford(player, stack, COST_PER_TICK)) {
                player.setDeltaMovement(motion.x, SLOW_FALL_SPEED, motion.z);
                player.resetFallDistance();
                particles(player);
            }
            return;
        }
        if (player.fallDistance >= SLOW_FALL_TRIGGER && !heldWingsUsable(player)
                && CharmsMRU.canAfford(player, stack, COST_PER_TICK) && payTick(player, stack, Use.PACK)) {
            player.resetFallDistance();
        }
    }

    // ---------------------------------------------------------------- 戴着（Curios）

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player && !player.level().isClientSide) {
            syncSpeedModifier(player, slotContext, stack);
            tickWornServer(player, stack);
        }
    }

    /**
     * 服务端：戴着的翅膀每刻一次。往上飞（WingsFlight 开着）时扣费，扣不动就叫停；没在飞时按摔落距离判断缓降。
     * 手里拿着能用的翅膀时不缓降（让给手持）；手里那对没电时照常缓降。返回这一刻扣没扣（GameTest 直接调）。
     */
    public static boolean tickWornServer(Player player, ItemStack stack) {
        if (CuriosCompat.isDisabled(stack) || player.isSpectator()) {
            if (WingsFlight.isActive(player)) {
                WingsFlight.stop(player);
            }
            return false;
        }
        if (WingsFlight.isActive(player)) {
            if (payTick(player, stack, Use.WORN)) {
                player.resetFallDistance();
                return true;
            }
            WingsFlight.stop(player);
            return false;
        }
        // 便宜的判断在前：只有真的在下落时才去看手里那对能不能用（可能要翻物品栏）
        if (!canSlowFall(player) || player.fallDistance < SLOW_FALL_TRIGGER || heldWingsUsable(player)) {
            return false;
        }
        if (payTick(player, stack, Use.WORN)) {
            player.resetFallDistance();
            return true;
        }
        return false;
    }

    /**
     * 服务端：把 Curios 已经给这个槽加上的移速修饰符对到现在应有的值（被配置关掉时 0，否则 +{@value #SPEED_BONUS}）。
     * Curios 只在槽里的物品变了（{@code ItemStack.matches} 不相等）时才重算槽位属性修饰符；运行中把翅膀加进 / 移出 {@code general.baubles.disabled} 以后，
     * 被关掉的翅膀不再扣 MRU、NBT 不变，Curios 就不会重算，旧的 +0.1（或解禁后的 0）会一直留到摘下、重登。
     * 只在这个槽的 UUID 下已经有<b>本物品的</b>修饰符（名字对得上）而且数值不对时才换，平时只多一次查表；Curios 还没加过的不自己补
     * （刚戴上那一刻 curioTick 先于 Curios 加修饰符）。换成同一个 UUID，摘下时 Curios 照样按 UUID 去掉。返回换没换。
     */
    public static boolean syncSpeedModifier(Player player, SlotContext slotContext, ItemStack stack) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return false;
        }
        UUID id = CuriosApi.getSlotUuid(slotContext);
        AttributeModifier current = speed.getModifier(id);
        double want = speedBonus(stack);
        if (current == null || !SPEED_MODIFIER_NAME.equals(current.getName()) || current.getAmount() == want) {
            return false;
        }
        speed.removeModifier(id);
        speed.addTransientModifier(new AttributeModifier(id, SPEED_MODIFIER_NAME, want, AttributeModifier.Operation.ADDITION));
        return true;
    }

    /** 戴着时应有的移速加成：被配置关掉时 0，否则 +{@value #SPEED_BONUS} */
    private static double speedBonus(ItemStack stack) {
        return CuriosCompat.isDisabled(stack) ? 0D : SPEED_BONUS;
    }

    /** 戴着的翅膀能不能缓降 / 往上飞：不在地上、没潜行、不是在创造飞行、鞘翅滑翔或骑乘 */
    private static boolean canSlowFall(Player player) {
        return !player.onGround() && !player.isShiftKeyDown() && canUseWorn(player);
    }

    /** 活着、不是旁观者、不是在创造飞行、鞘翅滑翔或骑乘（戴着的、背包里的翅膀共用；手持的不看这些，照原作） */
    private static boolean canUseWorn(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.getAbilities().flying && !player.isFallFlying() && !player.isPassenger();
    }

    /**
     * 客户端：本地玩家每刻（移动之前）调一次，由 client/CharmsClient 调。jumpHeld = 按住跳跃键而且没开界面。
     * 不戴翅膀也要调（让 WingsFlight 把「关」发出去）。手里拿着能用的翅膀时戴着的不起作用（让给手持），手里那对没电时照常。
     */
    public static void clientWornTick(Player player, Item wingsItem, boolean jumpHeld) {
        ItemStack worn = CuriosCompat.find(player, wingsItem);
        boolean usable = !worn.isEmpty() && canUseWorn(player) && !heldWingsUsable(player)
                && CharmsMRU.canAfford(player, worn, COST_PER_TICK);
        boolean fly = WingsFlight.clientTick(usable && jumpHeld);
        Vec3 motion = player.getDeltaMovement();
        if (fly) {
            player.setDeltaMovement(motion.x, motion.y + LIFT, motion.z);
            player.resetFallDistance();
            particles(player);
        } else if (usable && !player.onGround() && !player.isShiftKeyDown() && motion.y < SLOW_FALL_SPEED) {
            player.setDeltaMovement(motion.x, SLOW_FALL_SPEED, motion.z);
            player.resetFallDistance();
            particles(player);
        }
    }

    /** 本地玩家最近一次用翅膀缓降 / 往上飞的那一刻（客户端；翅膀的画面 client/vfx/gear/WingsFx 看它） */
    public static int clientFlightTick = Integer.MIN_VALUE;

    private static void particles(Player player) {
        clientFlightTick = player.tickCount;
    }

    /** 摘下（换成别的东西）时叫停往上飞。Curios 在翅膀的 NBT 变了（扣 MRU）时也会调这里，newStack 还是翅膀，不叫停 */
    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player && !player.level().isClientSide && !newStack.is(this)) {
            WingsFlight.stop(player);
        }
    }

    /** 同种只能戴一件 */
    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return CuriosCompat.onlyOneEquipped(slotContext, stack);
    }

    /**
     * 移速 +0.1（加法）。必须用 Curios 传进来的 uuid。被配置关掉时给 0（不是不给）：Curios 摘旧的时按这里返回的 UUID 去掉，
     * 返回空表的话配置中途关掉会留下一个摘不掉的 +0.1。戴着期间中途改配置时 Curios 不会重算，由 curioTick 里的 {@link #syncSpeedModifier} 对账。
     */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid, ItemStack stack) {
        return ImmutableMultimap.of(Attributes.MOVEMENT_SPEED,
                new AttributeModifier(uuid, SPEED_MODIFIER_NAME, speedBonus(stack), AttributeModifier.Operation.ADDITION));
    }

    // ---------------------------------------------------------------- 物品

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CharmsMRU.appendMRU(stack, tooltip);
        tooltip.add(Component.translatable("tooltip.essentialcraft5.magical_wings.worn", COST_PER_TICK).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.magical_wings.held", COST_PER_TICK).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.magical_wings.pack", COST_PER_TICK).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(CuriosCompat.onlyOneTooltip());
        if (CuriosCompat.isDisabled(stack)) {
            tooltip.add(CuriosCompat.disabledTooltip());
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return CharmsMRU.reequip(oldStack, newStack, slotChanged);
    }

    @Override
    public List<ItemStack> creativeVariants() {
        return CharmsMRU.emptyAndFull(new ItemStack(this));
    }
}
