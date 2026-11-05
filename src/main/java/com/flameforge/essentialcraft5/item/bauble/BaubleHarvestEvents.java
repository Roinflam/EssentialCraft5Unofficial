package com.flameforge.essentialcraft5.item.bauble;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.registry.ECBaubles;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.GlowSquid;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 挖掘、剪取、经验类饰品（原作 ECEventHandler.breakBlock / shearEvent / xpEvent）：
 * <ul>
 *   <li>π 腰带：挖矿石掉的经验 ×{@code bauble_limits.pi_xp_multiplier}（默认 2，小数按几率进位），本来没经验的矿石 5% 给 1 点
 *       （只在挖下来掉的是别的东西时，见 {@link #piBonusEligible}）。</li>
 *   <li>ρ 指环：挖矿石掉的经验不掉了，每点换 {@code bauble_limits.rho_ubmru_per_xp}（默认 1000）UBMRU（π、ρ 一起戴时先乘再换）。</li>
 *   <li>φ 指环：拿剪刀右键牛、鸡、鱿鱼、蜘蛛、苦力怕、骷髅、岩浆怪、史莱姆、僵尸、雪傀儡，剪出 1~3 个对应的材料（1000 UBMRU、剪刀 −32 耐久）。</li>
 *   <li>χ 指环：拾取经验球时另得球上数值的 {@code bauble_limits.chi_xp_bonus}（默认 50%，小数按几率进位；击杀 UBMRU 减半在物品的 UBMRU 修正里）。</li>
 * </ul>
 *
 * <h2>第十批饰品平衡</h2>
 * <ul>
 *   <li>π：原作 ×3。1.20 能挖出经验的方块比 1.12 多了幽匿块一族（幽匿催发体靠怪死在旁边就能无限长），×3 再加上 χ 就是六倍；改成 ×2。
 *       只认矿石以后没有无限来源了，×2 让这一格腰带有理由戴；和 ρ 一起戴是每点矿石经验 2000 UBMRU，仍在原作的 3000 以下。</li>
 *   <li>π、ρ 默认只认 {@code #forge:ores}（{@code bauble_limits.mining_xp_ores_only}）：1.12 原版挖了掉经验的方块基本只有矿石，原作的数值是照矿石定的。
 *       1.20 的幽匿块每块 1 点经验、能无限刷，ρ 每点 1000 UBMRU 等于一台 UBMRU 印钞机（一只僵尸死在催发体旁边铺出的幽匿块换来的 UBMRU 是杀它本身的十几倍）；
 *       π 的「没经验也有 5% 给 1 点」对石头也掷，用连锁挖掘 / 范围镐挖石头就成了经验农场。只认矿石以后，铁、铜、金矿（1.20 不掉经验）照样有 5%。</li>
 *   <li>π 的 5% 只在「挖下来掉的不是方块自己」时掷（{@link #piBonusEligible}）：用精准采集挖、远古残骸、别的模组里掉自己的矿，挖下来的方块能放回去再挖，
 *       不管就能无限掷 5%，叠 ρ 每次平均 50 UBMRU、挂宏刷；用不对的工具（空手挖铁矿，什么都不掉）也不掷。</li>
 *   <li>χ：原作另给一份（×2），对所有经验球都算（刷怪塔、附魔之瓶、交易），是大型整合包里最值钱的东西之一；改成另给 50%。</li>
 * </ul>
 * ο、τ 改掉落，在全局战利品修改器里（BaubleLootModifiers）。
 *
 * <h2>和原作不同的地方</h2>
 * <ul>
 *   <li>π / ρ：LOW 优先级、不收已取消的事件（被领地拒绝的破坏不算）。不用 LOWEST：有的工具模组在 LOWEST 记下事件的经验值、之后自己按记下的值掉经验，
 *       同优先级谁先跑看模组并行构造的顺序，排在它后面时 ρ 会既给 UBMRU 又照样掉经验、π 的 ×3 被丢掉；LOW 一定在所有 LOWEST 之前，
 *       又在 Curios 重算经验（HIGHEST）、领地插件（混合端转发在 NORMAL）之后。
 *       ECProtection 的权限探测（{@code isProbing}）不算（不然探测一次就白拿 UBMRU）；本模组的法杖代挖（{@code isGuardedBreak}：岩浆法杖）
 *       不算，离得多近都不算（代挖不掉经验，Curios 照样会重算一份，不挡 ρ 就能拿它换 UBMRU）；方块不在玩家触及距离内的不算
 *       （别的模组用玩家身份远程发的破坏事件）；创造模式不算（本来就不掉经验）。</li>
 *   <li>φ：原作先扣剪刀耐久再看这种生物剪不剪得出东西（右键猪也掉 32 耐久），这里剪得出才扣；原作靠「没在挥手」限频，
 *       这里改成每人两次之间至少 {@code bauble_limits.phi_cooldown_ticks}（默认 10）tick。发光鱿鱼剪出荧光墨囊（1.12 没有这种生物）。</li>
 *   <li>χ：原作把经验球的数值翻倍；1.20 的经验球会合并（一个球捡好几次），翻倍会越捡越多，所以改成捡的时候另给一份同样多的经验（这一份不修经验修补的装备）。
 *       分两段：HIGH 记下这次拾取和球的数值，LOWEST（收已取消的）确认球真的被捡走了才给——没被取消，或者被取消了但球已经没了
 *       （Curios 身上有损坏的经验修补饰品时在 NORMAL 取消拾取、自己吃掉球修饰品）。被取消、球还在的不给：原版这时不设拾取间隔，
 *       球下一 tick 还会再碰一次，照给就是无限经验。给的是 HIGH 时记下的数值（Curios 修完会把球的数值扣掉）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class BaubleHarvestEvents {

    /** π：没有经验的方块给 1 点的几率 */
    public static final float PI_BONUS_CHANCE = 0.05F;

    private static final Map<Player, Long> LAST_SHEAR = new WeakHashMap<>();

    private BaubleHarvestEvents() {
    }

    // ---------------------------------------------------------------- π ρ

    /** LOW 而不是 LOWEST：见类注释（有的模组在 LOWEST 记经验值） */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBaubleBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide || ECProtection.isProbing() || ECProtection.isGuardedBreak() || player.isCreative()
                || !SpecialBaubleItem.isWearer(player) || !BaubleEffects.isMiningInReach(player, event.getPos())) {
            return;
        }
        applyBreakExperience(player, event);
    }

    /** π、ρ 认不认这个方块：{@code mining_xp_ores_only} 开着时只认 {@code #forge:ores}（纯函数，GameTest 测） */
    public static boolean countsForMiningXp(BlockState state) {
        return !ECBaubles.Limits.miningXpOresOnly() || state.is(Tags.Blocks.ORES);
    }

    /** π 再 ρ（原作同一个处理器里的顺序）；方块不算数（{@link #countsForMiningXp}）时两件都不管，经验照原样掉 */
    public static void applyBreakExperience(Player player, BlockEvent.BreakEvent event) {
        if (!countsForMiningXp(event.getState())) {
            return;
        }
        if (BaubleEffects.has(player, ECBaubles.PI_BELT.get())) {
            int xp = ECBaubles.Limits.roundRandomly(event.getExpToDrop() * ECBaubles.Limits.piXpMultiplier(), player.getRandom());
            // 先掷骰再看掉落：只有掷中的那 5% 才去算一次掉落表
            if (xp <= 0 && piRoll(player) < PI_BONUS_CHANCE && piBonusEligible(player, event)) {
                xp = 1;
            }
            event.setExpToDrop(xp);
        }
        if (BaubleEffects.has(player, ECBaubles.RHO_RING.get())) {
            int xp = event.getExpToDrop();
            if (xp > 0) {
                BaubleEffects.addUBMRU(player, (long) xp * ECBaubles.Limits.rhoUbmruPerXp());
                event.setExpToDrop(0);
            }
        }
    }

    /**
     * π 的「没经验也 5% 给 1 点」这次算不算：工具对（这一下会掉东西），而且按主手工具算出来的掉落里没有方块自己。
     * 精准采集、远古残骸（下界合金镐挖下来就是它自己）、别的模组里掉自己的矿都不算——挖下来的方块能放回去再挖，等于无限掷骰；
     * 铁、铜、金矿用普通镐挖掉的是粗矿，照样算。掉落按真正的掉落表算（含全局战利品修改器），不传玩家：ο 的修改器看「挖的人」，传了会在这里多扣一次 UBMRU。
     * 只在掷中以后调（每 20 次挖掘算一次掉落表）。
     */
    public static boolean piBonusEligible(Player player, BlockEvent.BreakEvent event) {
        BlockState state = event.getState();
        if (!(event.getLevel() instanceof ServerLevel level) || !ForgeHooks.isCorrectToolForDrops(state, player)) {
            return false;
        }
        Item self = state.getBlock().asItem();
        List<ItemStack> drops = Block.getDrops(state, level, event.getPos(), level.getBlockEntity(event.getPos()), null, player.getMainHandItem());
        return !drops.isEmpty() && drops.stream().noneMatch(stack -> stack.is(self));
    }

    /** GameTest 用：π 的掷骰（null = 真随机） */
    @Nullable
    private static volatile Float testPiRoll;

    private static float piRoll(Player player) {
        Float forced = testPiRoll;
        return forced != null ? forced : player.getRandom().nextFloat();
    }

    /** GameTest 用：π 的 5% 掷骰固定成 roll（小于 {@link #PI_BONUS_CHANCE} 一定掷中；null 恢复真随机），只在同一段同步代码里设、测、还原 */
    public static void setPiRollForTest(@Nullable Float roll) {
        testPiRoll = roll;
    }

    // ---------------------------------------------------------------- φ

    /**
     * LOW、不收已取消的：领地模组在更高的优先级取消这次交互（别人领地里的牛羊）时不剪。Mohist 系混合端的插件在发 Forge 事件之前
     * 就拦（PlayerInteractEntityEvent 被取消时整个交互不进行），这里收不到。
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBaubleEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !isShears(event.getItemStack()) || !BaubleEffects.has(player, ECBaubles.PHI_RING.get())) {
            return;
        }
        shear(player, event.getHand(), event.getItemStack(), event.getTarget());
    }

    public static boolean isShears(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof ShearsItem || stack.is(Tags.Items.SHEARS));
    }

    /**
     * φ：剪一次。剪得出东西、UBMRU 够、不在冷却里才剪：扣 1000 UBMRU、剪刀 −32 耐久，在生物身上掉 1~3 个材料。
     *
     * @return 是否剪了（GameTest 直接调）
     */
    public static boolean shear(Player player, InteractionHand hand, ItemStack shears, Entity target) {
        Level level = player.level();
        if (level.isClientSide || !target.isAlive()) {
            return false;
        }
        long now = level.getGameTime();
        Long last = LAST_SHEAR.get(player);
        if (last != null && now >= last && now - last < ECBaubles.Limits.phiCooldown()) {
            return false;
        }
        ItemStack product = shearProduct(target, player.getRandom());
        if (product.isEmpty() || !BaubleEffects.spendUBMRU(player, BaubleEffects.PHI_COST)) {
            return false;
        }
        LAST_SHEAR.put(player, now);
        shears.hurtAndBreak(BaubleEffects.PHI_SHEARS_DAMAGE, player, p -> p.broadcastBreakEvent(hand));
        ItemEntity drop = new ItemEntity(level, target.getX(), target.getY(), target.getZ(), product);
        RandomSource random = level.getRandom();
        // 原作：竖直 +0~0.05，水平 ±0.1
        drop.setDeltaMovement(drop.getDeltaMovement().add((random.nextFloat() - random.nextFloat()) * 0.1F, random.nextFloat() * 0.05F,
                (random.nextFloat() - random.nextFloat()) * 0.1F));
        level.addFreshEntity(drop);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1F, 1F);
        player.swing(hand, true);
        return true;
    }

    /** 原作 findShearItem：1~3 个；子类跟着父类走（哞菇算牛、洞穴蜘蛛算蜘蛛、尸壳 / 溺尸 / 僵尸猪灵算僵尸），岩浆怪先于史莱姆判断 */
    public static ItemStack shearProduct(Entity entity, RandomSource random) {
        int count = 1 + random.nextInt(3);
        if (entity instanceof Cow) {
            return new ItemStack(Items.LEATHER, count);
        }
        if (entity instanceof Chicken) {
            return new ItemStack(Items.FEATHER, count);
        }
        if (entity instanceof Squid squid) {
            return new ItemStack(squid instanceof GlowSquid ? Items.GLOW_INK_SAC : Items.INK_SAC, count);
        }
        if (entity instanceof Spider) {
            return new ItemStack(Items.STRING, count);
        }
        if (entity instanceof Creeper) {
            return new ItemStack(Items.GUNPOWDER, count);
        }
        if (entity instanceof Skeleton) {
            return new ItemStack(Items.BONE_MEAL, count);
        }
        if (entity instanceof MagmaCube) {
            return new ItemStack(Items.MAGMA_CREAM, count);
        }
        if (entity instanceof Slime) {
            return new ItemStack(Items.SLIME_BALL, count);
        }
        if (entity instanceof Zombie) {
            return new ItemStack(Items.ROTTEN_FLESH, count);
        }
        if (entity instanceof SnowGolem) {
            // 1.12 Blocks.SNOW 是雪块
            return new ItemStack(Items.SNOW_BLOCK, count);
        }
        return ItemStack.EMPTY;
    }

    // ---------------------------------------------------------------- χ

    /** HIGH 时记下的这次拾取（只在服务端线程读写；LOWEST 用完就清） */
    @Nullable
    private static PlayerXpEvent.PickupXp chiPending;
    private static int chiPendingValue;

    /** χ 第一段：在 Curios（NORMAL）改球的数值之前记下这次拾取和球的数值，不发经验 */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBaublePickupXpBefore(PlayerXpEvent.PickupXp event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !BaubleEffects.has(player, ECBaubles.CHI_RING.get())) {
            return;
        }
        chiPending = event;
        chiPendingValue = event.getOrb().value;
    }

    /** χ 第二段：球确实被捡走了（没被取消，或被取消了但球已经没了）才按记下的数值另给一份（{@link #chiBonus}），见类注释 */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onBaublePickupXp(PlayerXpEvent.PickupXp event) {
        if (chiPending != event) {
            return;
        }
        int value = chiPendingValue;
        chiPending = null;
        chiPendingValue = 0;
        if (event.isCanceled() && !event.getOrb().isRemoved()) {
            return;
        }
        int bonus = chiBonus(value, event.getEntity().getRandom());
        if (bonus > 0) {
            event.getEntity().giveExperiencePoints(bonus);
        }
    }

    /** χ 另给的经验：{@code 球的数值 × chi_xp_bonus}，小数按几率进位 */
    public static int chiBonus(int orbValue, RandomSource random) {
        return orbValue <= 0 ? 0 : ECBaubles.Limits.roundRandomly(orbValue * ECBaubles.Limits.chiXpBonus(), random);
    }

    /** 登出时忘掉剪取冷却 */
    @SubscribeEvent
    public static void onBaubleLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SHEAR.remove(event.getEntity());
    }
}
