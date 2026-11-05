package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.registry.ECStaffs;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.google.common.collect.Lists;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TorchflowerCropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 生命法杖（原作 ItemLifeStaff）。
 * <ul>
 *   <li><b>右键方块</b>：对点中的那一格施一次骨粉（{@value #CELL_COST} MRU）；成了以后，同一高度周围 11×11（{@code general.staffs.staff_of_life_radius}，默认半径 5）
 *       里的<b>作物</b>各长一次（每格 {@value #CELL_COST} MRU），之后冷却 {@code general.staffs.staff_of_life_cooldown}（默认 10 刻）。
 *       骨粉粒子和声音只在点中的那一格放一次，范围里长了的格子合成一个粒子包（{@link #grow}）。</li>
 *   <li><b>右键僵尸村民</b>（{@value #CURE_COST} MRU）：走原版的治愈转化，{@value #CURE_TICKS} 刻后变回村民，交易、治愈折扣、「僵尸医生」进度都照原版。</li>
 *   <li><b>右键幼年动物</b>（含幼年村民，凡是 {@link AgeableMob}；{@value #GROW_UP_COST} MRU）：立刻长大。别人驯服的动物不行。</li>
 * </ul>
 * 对生物的两条由 {@link StaffOfLifeInteractEvents} 在 {@code PlayerInteractEvent.EntityInteract}（生物自己的右键交互之前）调 {@link #interactLivingEntity}，
 * 不走原版「生物交互返回 PASS 才轮到物品」的顺序：幼年村民（摇头）、野生小马 / 驴 / 骡 / 羊驼（发怒）、自己的小狗（坐下）会先把右键吃掉，长不大。
 * <h2>权限（policy §4.2、§7 裁定 3、4）</h2>
 * <ul>
 *   <li>点中那一格照原版骨粉：草方块、树苗、苔藓这类会连带改别的格子的也照做，但<b>这一格连同蔓延出去的所有格子打包成一次（多格）放置事件</b>，被拒就全部还原、不扣 MRU。
 *       ForgeHooks 本来会在 useOn 外面替我们做同样的事，但它被拒时只还原法杖自己的 NBT、不还原身上储存物品里扣掉的 MRU；
 *       所以这里暂停外层捕获（{@link ECProtection#withoutCapture}），自己开捕获、自己发事件（写法同 ECProtection.placeGuarded，带点击面和手），结果定了再扣费。</li>
 *   <li>周围 11×11 <b>只对只改自己那一格的作物</b>生效：{@link CropBlock}（小麦、胡萝卜、马铃薯、甜菜根、火把花……）、{@link StemBlock}（南瓜 / 西瓜茎）、
 *       {@link SweetBerryBushBlock}、{@link CocoaBlock}。每格先算好长一次以后的状态，再用 {@link ECProtection#placeGuarded} 放，被拒的格子跳过、不扣 MRU。
 *       草方块、苔藓、菌岩、海草、树苗、蘑菇不做范围催熟（它们改的不止一格，逐格快照罩不住）；茎在范围里只长高、不结瓜（结瓜会往旁边放方块）。</li>
 *   <li>治疗、催熟生物改成右键：右键实体本来就归领地插件管（混合端先发 PlayerInteractEntityEvent，在 Forge 的 EntityInteract 之前）。</li>
 *   <li>假玩家（机械手之类）一律不工作。</li>
 * </ul>
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li>原作先扣 100 MRU 再施骨粉（没施成也白扣），范围里每格照骨粉逻辑什么都催（草方块长草、树苗长树），范围循环包含中心格（中心被催两次），
 *       范围只看法杖自己存的 MRU；现在施成了才扣，范围只催作物、跳过中心，MRU 按 MRUDrain 的总量判断。</li>
 *   <li>原作左键僵尸村民直接删掉僵尸、新建一个村民（交易、职业全丢）；现在右键、走原版转化（1 秒），职业和交易都保留。</li>
 *   <li>原作左键幼年动物直接长大；现在右键（抢在生物自己的右键之前，所以小马、小狗、幼年村民也照样长大），别人驯服的（狗、猫、马……）不行。</li>
 *   <li>原作范围催熟没有粒子和声音；现在点中的格子照原版骨粉放一次粒子和声音，范围里长了的格子一起撒一把快乐村民粒子（一个包）。</li>
 *   <li>新加使用后冷却 10 刻（policy §4.2）。</li>
 * </ul>
 */
public class StaffOfLifeItem extends MRUStaffItem {

    /** 骨粉一格的花费（原作 playerUseMRU(…, 100)） */
    public static final int CELL_COST = 100;
    /** 治愈僵尸村民（原作 500） */
    public static final int CURE_COST = 500;
    /** 幼年动物长大（原作 100） */
    public static final int GROW_UP_COST = 100;
    /** 治愈转化的时长（刻）。原作当场变成村民；这里走原版转化（金苹果是 3 600~6 000 刻），给 1 秒让治愈的声音放完 */
    public static final int CURE_TICKS = 20;
    /** 骨粉粒子和声音（BoneMealItem.useOn 用的同一个世界事件）；只给点中的那一格放，范围里的格子不逐格放（每个都是一个包、一声骨粉音效） */
    private static final int GROWTH_PARTICLES_EVENT = 1505;
    /** 范围催熟那一把粒子最多几个（每长一格 2 个） */
    private static final int AREA_PARTICLES_MAX = 60;

    /** ZombieVillager#startConverting(UUID, int) 是私有的；SRG 名 m_34383_（ObfuscationReflectionHelper 在开发环境自动换成官方名）。找不到时退回写 NBT */
    @Nullable
    private static final Method START_CONVERTING = findStartConverting();
    /** 混合端给 ForgeEventFactory 加的带手重载（同 ECProtection / MagicalLanternItem），纯 Forge 没有，为 null */
    @Nullable
    private static final MethodHandle PLACE_WITH_HAND = findWithHand("onBlockPlace", BlockSnapshot.class);
    @Nullable
    private static final MethodHandle MULTI_PLACE_WITH_HAND = findWithHand("onMultiBlockPlace", List.class);

    public StaffOfLifeItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------------
    // 右键方块
    // ------------------------------------------------------------------

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        ItemStack stack = context.getItemInHand();
        if (player == null || !(state.getBlock() instanceof BonemealableBlock bonemealable)
                || !bonemealable.isValidBonemealTarget(level, pos, state, level.isClientSide)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return canAfford(player, stack, CELL_COST) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !ECProtection.isRealPlayer(player)) {
            return InteractionResult.FAIL;
        }
        InteractionResult[] result = {InteractionResult.FAIL};
        ECProtection.withoutCapture(level, () -> result[0] = grow(serverLevel, player, stack, context.getHand(), pos, context.getClickedFace()));
        return result[0];
    }

    /** 服务端、已暂停外层捕获：中心施骨粉 → 放行才扣费 → 范围逐格催熟 → 冷却 */
    private InteractionResult grow(ServerLevel level, Player player, ItemStack stack, InteractionHand hand, BlockPos pos, Direction face) {
        if (!canAfford(player, stack, CELL_COST)) {
            tellNoMRU(player, CELL_COST);
            return InteractionResult.FAIL;
        }
        CenterResult center = bonemealCenter(level, player, pos, face, hand);
        if (center == CenterResult.DENIED) {
            tellNoPermission(player);
            return InteractionResult.FAIL;
        }
        if (center == CenterResult.NOTHING) {
            return InteractionResult.PASS;
        }
        MRUDrain.playerUseMRU(player, stack, CELL_COST);
        level.levelEvent(GROWTH_PARTICLES_EVENT, pos, 0);

        int radius = ECStaffs.Config.lifeRadius();
        boolean outOfMRU = false;
        int grown = 0;
        area:
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;   // 原作循环含中心格，中心被催两次
                }
                BlockPos cell = pos.offset(dx, 0, dz);
                if (!level.isLoaded(cell)) {
                    continue;
                }
                BlockState next = grownOnce(level, cell, level.getBlockState(cell), level.random);
                if (next == null) {
                    continue;
                }
                if (!canAfford(player, stack, CELL_COST)) {
                    outOfMRU = true;
                    break area;
                }
                if (ECProtection.placeGuarded(player, level, cell, next, Direction.UP, hand, Block.UPDATE_CLIENTS)) {
                    MRUDrain.playerUseMRU(player, stack, CELL_COST);
                    grown++;
                }
            }
        }
        if (grown > 0) {
            // 范围里长了的格子不逐格发 1505（满 11×11 是 120 个包、120 声同时响的骨粉音效）；整片一个粒子包，客户端按高斯分布撒在田上
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    Math.min(grown * 2, AREA_PARTICLES_MAX), radius / 2D, 0.25D, radius / 2D, 0D);
        }
        if (outOfMRU) {
            tellNoMRU(player, CELL_COST);
        }
        int cooldown = ECStaffs.Config.lifeCooldown();
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(this, cooldown);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * 周围格子里的作物长一次以后的状态；不是这四类作物、已经长满、这次没长（甜菜根的骨粉有时 +0）时返回 null。
     * 数值照原版骨粉：作物 +2~5 级（甜菜根 ÷3，火把花 +1：0 级要两次才开花）、茎 +2~5 级、甜浆果丛 +1、可可豆 +1。只算状态，不改世界。
     */
    @Nullable
    public static BlockState grownOnce(Level level, BlockPos pos, BlockState state, RandomSource random) {
        Block block = state.getBlock();
        if (!(block instanceof BonemealableBlock bonemealable) || !bonemealable.isValidBonemealTarget(level, pos, state, false)) {
            return null;
        }
        if (block instanceof CropBlock crop) {
            int age = crop.getAge(state);
            // 同 CropBlock#getBonemealAgeIncrease 和两个原版子类的覆盖（受保护方法，这里照抄数值）
            int increase = crop instanceof TorchflowerCropBlock ? 1 : Mth.nextInt(random, 2, 5);
            if (crop instanceof BeetrootBlock) {
                increase /= 3;   // BeetrootBlock#getBonemealAgeIncrease
            }
            int next = Math.min(crop.getMaxAge(), age + increase);
            return next == age ? null : crop.getStateForAge(next);
        }
        if (block instanceof StemBlock) {
            return state.setValue(StemBlock.AGE, Math.min(StemBlock.MAX_AGE, state.getValue(StemBlock.AGE) + Mth.nextInt(random, 2, 5)));
        }
        if (block instanceof SweetBerryBushBlock) {
            return state.setValue(SweetBerryBushBlock.AGE, Math.min(SweetBerryBushBlock.MAX_AGE, state.getValue(SweetBerryBushBlock.AGE) + 1));
        }
        if (block instanceof CocoaBlock) {
            return state.setValue(CocoaBlock.AGE, Math.min(CocoaBlock.MAX_AGE, state.getValue(CocoaBlock.AGE) + 1));
        }
        return null;
    }

    private enum CenterResult {
        /** 不是能施骨粉的目标，什么都没发生 */
        NOTHING,
        /** 施成了（包括原版那种「骨粉用掉了但这次没长」，比如树苗） */
        GROWN,
        /** 领地插件拒绝，已全部还原 */
        DENIED
    }

    /**
     * 对点中的格子施一次原版骨粉（含 Forge 的 BonemealEvent），期间开快照捕获；捕获到的改动（这一格和蔓延出去的格子）打包成一次放置事件
     * （一格用 onBlockPlace、多格用 onMultiBlockPlace，带点击面和手），被拒按快照倒序还原，放行才补 onPlace 和通知。
     * 调用前外层捕获已经暂停（{@link #useOn}），这里自己开、自己关；发事件、还原时捕获是关着的，还原不会被别的捕获收进去。
     */
    private static CenterResult bonemealCenter(ServerLevel level, Player player, BlockPos pos, Direction face, InteractionHand hand) {
        boolean wasCapturing = level.captureBlockSnapshots;
        ArrayList<BlockSnapshot> shared = level.capturedBlockSnapshots;
        List<BlockSnapshot> outer = shared.isEmpty() ? List.of() : new ArrayList<>(shared);
        shared.clear();
        boolean applied;
        List<BlockSnapshot> snapshots;
        level.captureBlockSnapshots = true;
        try {
            applied = BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL), level, pos, player);
            snapshots = new ArrayList<>(shared);
        } finally {
            level.captureBlockSnapshots = wasCapturing;
            shared.clear();
            shared.addAll(outer);
        }
        if (!applied) {
            restore(level, snapshots);   // 按说没施成就没有改动；有（别的模组的骨粉事件改了方块）也还原，免得漏过权限检查
            return CenterResult.NOTHING;
        }
        if (snapshots.isEmpty()) {
            return CenterResult.GROWN;
        }
        if (postPlaceEvent(player, snapshots, face, hand)) {
            restore(level, snapshots);
            return CenterResult.DENIED;
        }
        for (BlockSnapshot snapshot : snapshots) {
            BlockPos at = snapshot.getPos();
            BlockState replaced = snapshot.getReplacedBlock();
            BlockState placed = level.getBlockState(at);
            placed.onPlace(level, at, replaced, false);
            level.markAndNotifyBlock(at, level.getChunkAt(at), replaced, placed, snapshot.getFlag(), 512);
        }
        return CenterResult.GROWN;
    }

    private static void restore(Level level, List<BlockSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return;
        }
        boolean capturing = level.captureBlockSnapshots;
        boolean restoring = level.restoringBlockSnapshots;
        level.captureBlockSnapshots = false;
        level.restoringBlockSnapshots = true;
        try {
            for (BlockSnapshot snapshot : Lists.reverse(snapshots)) {
                snapshot.restore(true, false);
            }
        } finally {
            level.restoringBlockSnapshots = restoring;
            level.captureBlockSnapshots = capturing;
        }
    }

    /** 发放置事件，返回是否被取消（写法同 ECProtection.postPlaceEvent） */
    private static boolean postPlaceEvent(Player player, List<BlockSnapshot> snapshots, Direction face, InteractionHand hand) {
        boolean multi = snapshots.size() > 1;
        MethodHandle withHand = multi ? MULTI_PLACE_WITH_HAND : PLACE_WITH_HAND;
        if (withHand != null) {
            try {
                return (boolean) withHand.invoke((Entity) player, multi ? snapshots : snapshots.get(0), face, hand);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new IllegalStateException(e);
            }
        }
        return multi ? ForgeEventFactory.onMultiBlockPlace(player, snapshots, face)
                : ForgeEventFactory.onBlockPlace(player, snapshots.get(0), face);
    }

    @Nullable
    private static MethodHandle findWithHand(String name, Class<?> snapshotType) {
        try {
            return MethodHandles.publicLookup().findStatic(ForgeEventFactory.class, name,
                            MethodType.methodType(boolean.class, Entity.class, snapshotType, Direction.class, InteractionHand.class))
                    .asType(MethodType.methodType(boolean.class, Entity.class, Object.class, Direction.class, InteractionHand.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 右键生物
    // ------------------------------------------------------------------

    /**
     * 右键僵尸村民 / 幼年生物。实际由 {@link StaffOfLifeInteractEvents} 在生物自己的右键交互之前调（两端都调），返回非 PASS 时那次右键到此为止；
     * 原版顺序（生物交互 PASS 以后）也还会调到，那时对象不是这两类、或正在转化，返回 PASS、什么都不做。
     * 客户端只预判：够 MRU 返回 SUCCESS（挥手），不够返回 FAIL（和服务端一样拦下，不让客户端先演一遍马发怒、狗坐下）。
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        Level level = player.level();
        if (target instanceof ZombieVillager zombie) {
            if (zombie.isConverting()) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide) {
                return canAfford(player, stack, CURE_COST) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
            if (!ECProtection.isRealPlayer(player)) {
                return InteractionResult.FAIL;
            }
            if (!MRUDrain.playerUseMRU(player, stack, CURE_COST)) {
                tellNoMRU(player, CURE_COST);
                return InteractionResult.FAIL;
            }
            startCuring(zombie, player.getUUID());
            return InteractionResult.CONSUME;
        }
        if (target instanceof AgeableMob ageable && ageable.isBaby()) {
            if (target instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null && !ownable.getOwnerUUID().equals(player.getUUID())) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable("message.essentialcraft5.staff_of_life.not_yours").withStyle(ChatFormatting.RED), true);
                }
                return InteractionResult.FAIL;
            }
            if (level.isClientSide) {
                return canAfford(player, stack, GROW_UP_COST) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
            if (!ECProtection.isRealPlayer(player)) {
                return InteractionResult.FAIL;
            }
            if (!MRUDrain.playerUseMRU(player, stack, GROW_UP_COST)) {
                tellNoMRU(player, GROW_UP_COST);
                return InteractionResult.FAIL;
            }
            ageable.setAge(0);
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + target.getBbHeight() / 2D, target.getZ(),
                        8, target.getBbWidth() / 2D, target.getBbHeight() / 2D, target.getBbWidth() / 2D, 0D);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /**
     * 让僵尸村民开始原版的治愈转化（同金苹果：记下治疗者、去掉虚弱、给力量、放治愈的声音），{@value #CURE_TICKS} 刻后变回村民，
     * 转化时原版会带上职业、交易、经验，并按治疗者记治愈折扣、触发「僵尸医生」进度。
     */
    public static void startCuring(ZombieVillager zombie, UUID healer) {
        if (START_CONVERTING != null) {
            try {
                START_CONVERTING.invoke(zombie, healer, CURE_TICKS);
                return;
            } catch (ReflectiveOperationException | RuntimeException e) {
                EssentialCraft5.LOGGER.warn("生命法杖：调用 ZombieVillager#startConverting 失败，改为写 NBT", e);
            }
        }
        // 退路：整份存档数据写进转化时间和治疗者再读回（readAdditionalSaveData 读到 ConversionTime > -1 时自己调 startConverting）
        CompoundTag tag = new CompoundTag();
        zombie.addAdditionalSaveData(tag);
        tag.putInt("ConversionTime", CURE_TICKS);
        tag.putUUID("ConversionPlayer", healer);
        zombie.readAdditionalSaveData(tag);
    }

    @Nullable
    private static Method findStartConverting() {
        try {
            return ObfuscationReflectionHelper.findMethod(ZombieVillager.class, "m_34383_", UUID.class, int.class);
        } catch (RuntimeException e) {
            EssentialCraft5.LOGGER.warn("生命法杖：找不到 ZombieVillager#startConverting（m_34383_），治愈改为写 NBT", e);
            return null;
        }
    }

    @Override
    protected void addUsage(ItemStack stack, List<Component> tooltip) {
        // 范围边长跟服务端配置走（SERVER 配置登录时同步给客户端），半径 0 时是 1×1
        int side = 2 * ECStaffs.Config.lifeRadius() + 1;
        tooltip.add(usage("tooltip.essentialcraft5.staff_of_life.crops", side, side, CELL_COST));
        tooltip.add(usage("tooltip.essentialcraft5.staff_of_life.mobs", CURE_COST, GROW_UP_COST));
    }
}
