package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.vault.VaultElement;
import com.flameforge.essentialcraft5.block.vault.VaultKeyholeBlock;
import com.flameforge.essentialcraft5.block.vault.VaultPedestalBlock;
import com.flameforge.essentialcraft5.block.vault.VaultSealBlock;
import com.flameforge.essentialcraft5.entity.mob.hoanna.CatacombRevenantEntity;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECHoannaMaterials;
import com.flameforge.essentialcraft5.registry.ECHoannaNativesC;
import com.flameforge.essentialcraft5.registry.ECSealedVault;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import com.flameforge.essentialcraft5.worldgen.hoanna.vault.VaultLayout;
import com.flameforge.essentialcraft5.worldgen.hoanna.vault.VaultPuzzle;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 宝库核心的方块实体（第十三批 13e-S1，batch13.md §6.4；HS §2.4.1）：一座封印宝库的全部状态都在这里，祭座、锁孔、碑、封印石只是入口
 * （它们按 {@link VaultLayout} 里写死的偏移找到核心，不扫方块）。不 tick：只在有人右键时算，门的自动关用封印石的计划刻。
 * <p>
 * <b>谜题</b>（{@link VaultPuzzle}，按结构种子定，生成时由宝库室拼件写进来）：四座祭座各刻一种元素、正确顺序、碑文四行的排列。
 * <p>
 * <b>一次尝试</b>：先往锁孔放 1 个地脉结晶（{@link #useKeyhole}，这一次的钥匙，答对答错都不退）；再按顺序往祭座放对应元素的元素魔晶
 * （{@link #usePedestal}，每座 {@code crystals_per_pedestal} 个，默认 1）。四座按顺序放满 = 答对；在还没轮到的祭座上放 = 答错。
 * 拿错元素的魔晶、没放结晶就放晶石，只提示、不扣东西、不算错。
 * <ul>
 *   <li><b>答错</b>：这一次放进去的晶石退一半（向下取整，先退顺序靠前的，交给放错的人，背包满了掉脚下）、祭座和锁孔清空；
 *       封印反噬只打放错的人：当前最大生命 × {@code backlash_fraction}（20%）的 {@code mru_discharge}，没有攻击者；
 *       这座宝库距上次召守卫满 {@code guard_cooldown_ticks}（60 秒，按主世界 gameTime）才召 {@code guard_count}（2）只墓穴守灵
 *       （来源 {@code SUMMONED}，不掉关键物品；和平难度不召），之后再错只反噬。每人连错 {@code lockout_failures}（3）次，这座宝库对他封锁
 *       {@code lockout_ticks}（24 000 刻 = 1 个游戏日），按两个钟算、哪个先到期就解封：主世界日内时间走满 lockout_ticks（睡觉跳过的夜也算过去了），
 *       或者主世界 gameTime 走满 lockout_ticks（关了昼夜循环、或者 /time set 把日内时间往回拨了，照样按实际时长解封；gameTime 不受这两样影响）。
 *       所以剩余最多按满额算，而且一直在倒数。封锁中右键祭座 / 锁孔只提示还剩多久。答对清零。</li>
 *   <li><b>答对</b>：封印门九块切到「开」{@code door_open_ticks}（600 刻）；<b>这一刻</b>站在前厅里的生存 / 冒险真玩家记进解开名单
 *       （≤ {@value #MAX_NAMES} 人，满了挤掉最早的并打警告）；名单上的人以后再来，右键核心照样能领、右键封印石能把门再打开。
 *       人在宝库室里（挖墙进来的）右键封印石也能开门出去，免得被关在里面。</li>
 *   <li><b>领奖</b>（{@link #claim}）：在名单上、还没领过的真玩家右键核心 → 一份 {@code chests/sealed_vault_reward}（{@code ECItemEntities.giveOrDrop}），
 *       记进已领名单。挖穿墙绕进来的人不在名单上，领不到。</li>
 * </ul>
 * 解开名单、已领名单、连错次数都存在这里（不进玩家数据）：每人每座一次。所有入口只认真玩家（{@code ECProtection.isRealPlayer}），
 * 右键走原版交互事件，领地插件取消了就到不了这里。
 * <p>
 * NBT 键（<b>上线后只能加、不能改含义</b>）：{@value #TAG_PUZZLE}（{@code seed}、{@code pedestals}、{@code order}、{@code riddle}）、
 * {@value #TAG_ATTEMPT}（{@code key}、{@code step}、{@code filled}、{@code spent}）、{@value #TAG_GUARD}、{@value #TAG_OPEN_UNTIL}、
 * {@value #TAG_UNLOCKED}、{@value #TAG_CLAIMED}、{@value #TAG_FAILS}（{@code id}、{@code count}、{@code until}、{@code start}；
 * {@code start} 后加，没有这个键的老记录只按日内时间到期）。
 */
public class VaultCoreBlockEntity extends BlockEntity {

    /** 解开名单、已领名单、连错记录各自的人数上限 */
    public static final int MAX_NAMES = 1024;
    /** 宝库核心发的战利品表 */
    public static final ResourceLocation REWARD_TABLE = EssentialCraft5.id("chests/sealed_vault_reward");
    /** 读碑文的间隔（刻）：按住右键时别刷屏 */
    public static final int RIDDLE_COOLDOWN = 40;
    /** 守卫在祭座周围几格内找落点 */
    public static final int GUARD_RADIUS = 2;

    public static final String TAG_PUZZLE = "puzzle";
    public static final String TAG_ATTEMPT = "attempt";
    public static final String TAG_GUARD = "guard_time";
    public static final String TAG_OPEN_UNTIL = "open_until";
    public static final String TAG_UNLOCKED = "unlocked";
    public static final String TAG_CLAIMED = "claimed";
    public static final String TAG_FAILS = "fails";

    /** 往祭座放晶石的结果 */
    public enum PedestalResult {
        /** 不是真玩家 / 不在服务端 */
        IGNORED,
        /** 封锁中 */
        LOCKED,
        /** 锁孔里还没放结晶 */
        NO_KEY,
        /** 空手（只看了一眼祭座的纹样） */
        INSPECTED,
        /** 手里的不是这座祭座的元素魔晶 */
        WRONG_ITEM,
        /** 这座已经放满 */
        ALREADY_FILLED,
        /** 放进去了，这座还没满 */
        PARTIAL,
        /** 这座放满了，还没解开 */
        FILLED,
        /** 四座都放满，答对 */
        SOLVED,
        /** 放错了 */
        FAILED
    }

    /** 往锁孔放结晶的结果 */
    public enum KeyResult { IGNORED, LOCKED, ALREADY, NEED_CRYSTAL, ACCEPTED }

    /** 右键核心的结果 */
    public enum ClaimResult { IGNORED, NOT_LISTED, ALREADY_CLAIMED, REWARDED }

    /**
     * 一个人在这座宝库的连错次数和封锁。
     *
     * @param until 封锁到期的主世界日内时间（0 = 没封锁）
     * @param start 封锁开始时的主世界 gameTime（{@link #NO_START} = 没记，老记录）
     */
    public record Fails(int count, long until, long start) {

        public static final long NO_START = -1L;

        public Fails(int count, long until) {
            this(count, until, NO_START);
        }
    }

    // ---------------------------------------------------------------- 测试覆盖（null = 用真值）
    @Nullable
    private static volatile Long testGameTime;
    @Nullable
    private static volatile Long testDayTime;
    @Nullable
    private static volatile Boolean testPeaceful;
    @Nullable
    private static volatile List<ServerPlayer> testPlayers;

    private VaultPuzzle puzzle;
    private long puzzleSeed;
    private boolean hasPuzzle;

    private boolean keyed;
    private int step;
    private int filled;
    /** 这一次尝试里按元素放进去了几个（退一半时用） */
    private final int[] spent = new int[4];
    private long guardTime = Long.MIN_VALUE;
    private long openUntil;

    private final LinkedHashSet<UUID> unlocked = new LinkedHashSet<>();
    private final LinkedHashSet<UUID> claimed = new LinkedHashSet<>();
    private final LinkedHashMap<UUID, Fails> fails = new LinkedHashMap<>();
    /** 读碑文的冷却（不存盘） */
    private final Map<UUID, Long> lastRiddle = new HashMap<>();
    /** 最近一次召出来的守卫（不存盘，测试读） */
    private final List<CatacombRevenantEntity> lastGuards = new ArrayList<>();

    public VaultCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ECSealedVault.VAULT_CORE_BE.get(), pos, state);
    }

    // ================================================================== 谜题与位置

    /** 结构生成时由宝库室拼件调：写入这座宝库的谜题（已经写过的不改） */
    public void initPuzzle(VaultPuzzle puzzle, long seed) {
        if (hasPuzzle) {
            return;
        }
        this.puzzle = puzzle;
        this.puzzleSeed = seed;
        this.hasPuzzle = true;
        setChanged();
    }

    /** 这座宝库的谜题；没有经过结构生成的核心（创造模式摆的）按位置和世界种子补一份 */
    public VaultPuzzle puzzle() {
        if (!hasPuzzle) {
            long worldSeed = level instanceof ServerLevel server ? server.getSeed() : 0L;
            long seed = worldPosition.asLong() * 0x9E3779B97F4A7C15L ^ worldSeed;
            initPuzzle(VaultPuzzle.of(seed), seed);
        }
        return puzzle;
    }

    public long puzzleSeed() {
        puzzle();
        return puzzleSeed;
    }

    public BlockPos keyholePos() {
        return worldPosition.subtract(VaultLayout.CORE_FROM_KEYHOLE);
    }

    public BlockPos pedestalPos(Direction side) {
        return keyholePos().relative(side, VaultLayout.PEDESTAL_DISTANCE);
    }

    public BoundingBox antechamber() {
        return VaultLayout.antechamberInterior(keyholePos());
    }

    public BoundingBox chamber() {
        return VaultLayout.chamberInterior(worldPosition);
    }

    // ================================================================== 碑文

    /** 把碑文四行发给 player（聊天栏）；{@value #RIDDLE_COOLDOWN} 刻内重复右键不再发 */
    public boolean readRiddle(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        Long last = lastRiddle.get(player.getUUID());
        if (last != null && now - last >= 0 && now - last < RIDDLE_COOLDOWN) {
            return false;
        }
        lastRiddle.put(player.getUUID(), now);
        VaultPuzzle data = puzzle();
        for (int line = 0; line < 4; line++) {
            player.sendSystemMessage(Component.translatable(data.riddleKey(line)).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        }
        return true;
    }

    // ================================================================== 锁孔

    public KeyResult useKeyhole(ServerPlayer player, ItemStack held) {
        if (!(level instanceof ServerLevel server) || !ECProtection.isRealPlayer(player)) {
            return KeyResult.IGNORED;
        }
        if (lockedNotice(server, player)) {
            return KeyResult.LOCKED;
        }
        if (keyed) {
            tell(player, "keyhole.already");
            return KeyResult.ALREADY;
        }
        if (!held.is(ECHoannaMaterials.LEY_CRYSTAL.get())) {
            tell(player, "keyhole.need_crystal");
            return KeyResult.NEED_CRYSTAL;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        keyed = true;
        setKeyholeLit(server, true);
        setChanged();
        server.playSound(null, keyholePos(), ECSealedVault.INSERT.get(), SoundSource.BLOCKS, 0.9F, 0.8F);
        tell(player, "keyhole.accepted");
        return KeyResult.ACCEPTED;
    }

    public boolean keyed() {
        return keyed;
    }

    /** 已经按顺序放满了几座 */
    public int step() {
        return step;
    }

    /** 这一次尝试一共放进去了几个晶石 */
    public int spentTotal() {
        return Arrays.stream(spent).sum();
    }

    // ================================================================== 祭座

    /** 真玩家拿着 held 右键 side 那一侧的祭座（side = 祭座在锁孔的哪一侧） */
    public PedestalResult usePedestal(ServerPlayer player, ItemStack held, Direction side) {
        if (!(level instanceof ServerLevel server) || !ECProtection.isRealPlayer(player)) {
            return PedestalResult.IGNORED;
        }
        VaultPuzzle data = puzzle();
        VaultElement element = data.elementAt(side);
        if (lockedNotice(server, player)) {
            return PedestalResult.LOCKED;
        }
        VaultElement holding = VaultElement.ofCrystal(held);
        if (holding != element) {
            String what = holding == null && held.isEmpty() ? "pedestal.info" : "pedestal.wrong_item";
            player.displayClientMessage(Component.translatable(key(what), elementName(element), element.crystal().getDescription()), true);
            return holding == null && held.isEmpty() ? PedestalResult.INSPECTED : PedestalResult.WRONG_ITEM;
        }
        if (!keyed) {
            tell(player, "pedestal.no_key");
            return PedestalResult.NO_KEY;
        }
        if (isDone(data, element)) {
            tell(player, "pedestal.done");
            return PedestalResult.ALREADY_FILLED;
        }
        int need = ECSealedVault.Config.crystalsPerPedestal();
        if (data.expected(step) != element) {
            // 没轮到这座：放进去的照样算这一次的花费（退一半时一起算）
            take(player, held, element, need);
            fail(server, player, side);
            return PedestalResult.FAILED;
        }
        filled += take(player, held, element, need - filled);
        setChanged();
        server.playSound(null, pedestalPos(side), ECSealedVault.INSERT.get(), SoundSource.BLOCKS, 0.9F, 1.0F + 0.15F * step);
        if (filled < need) {
            player.displayClientMessage(Component.translatable(key("pedestal.progress"), need - filled, element.crystal().getDescription()), true);
            return PedestalResult.PARTIAL;
        }
        filled = 0;
        step++;
        setPedestalLit(server, side, true);
        if (step >= 4) {
            solve(server, player);
            return PedestalResult.SOLVED;
        }
        tell(player, "pedestal.filled");
        return PedestalResult.FILLED;
    }

    /** 这一次尝试里 element 那座是不是已经放满 */
    private boolean isDone(VaultPuzzle data, VaultElement element) {
        for (int k = 0; k < step; k++) {
            if (data.expected(k) == element) {
                return true;
            }
        }
        return false;
    }

    /** 从手里拿 count 个（创造模式不扣），记进这一次的花费；返回拿了几个 */
    private int take(ServerPlayer player, ItemStack held, VaultElement element, int count) {
        count = Math.max(0, Math.min(count, held.getCount()));
        if (!player.getAbilities().instabuild) {
            held.shrink(count);
        }
        spent[element.ordinal()] += count;
        return count;
    }

    /** 答错：退一半、清空、反噬、（冷却到了）召守卫、记连错 */
    private void fail(ServerLevel server, ServerPlayer player, Direction wrongSide) {
        int refund = spentTotal() / 2;
        int refunded = refund;
        VaultPuzzle data = puzzle();
        for (int k = 0; k < 4 && refund > 0; k++) {
            VaultElement element = data.expected(k);
            int n = Math.min(refund, spent[element.ordinal()]);
            if (n > 0) {
                ECItemEntities.giveOrDrop(player, new ItemStack(element.crystal(), n));
                refund -= n;
            }
        }
        resetAttempt(server);
        float damage = player.getMaxHealth() * (float) ECSealedVault.Config.backlashFraction();
        if (damage > 0F) {
            player.hurt(ECDamageTypes.mruDischarge(server), damage);
        }
        server.playSound(null, player.blockPosition(), ECSealedVault.BACKLASH.get(), SoundSource.BLOCKS, 1.0F, 0.8F);
        lastGuards.clear();
        long now = gameTime(server);
        int guards = ECSealedVault.Config.guardCount();
        boolean cooled = guardTime == Long.MIN_VALUE || now < guardTime || now - guardTime >= ECSealedVault.Config.guardCooldownTicks();
        if (guards > 0 && cooled) {
            guardTime = now;
            if (!peaceful(server)) {
                summonGuards(server, player, wrongSide, guards);
            }
        }
        player.displayClientMessage(Component.translatable(key("failed"), refunded), true);
        int limit = ECSealedVault.Config.lockoutFailures();
        Fails before = fails.get(player.getUUID());
        int count = (before == null ? 0 : before.count()) + 1;
        if (limit > 0 && count >= limit && ECSealedVault.Config.lockoutTicks() > 0) {
            remember(player.getUUID(), new Fails(0, dayTime(server) + ECSealedVault.Config.lockoutTicks(), gameTime(server)));
            player.sendSystemMessage(Component.translatable(key("locked_now"), count, minutes(ECSealedVault.Config.lockoutTicks()))
                    .withStyle(ChatFormatting.RED));
        } else {
            remember(player.getUUID(), new Fails(count, 0L, 0L));
        }
        setChanged();
    }

    /** 召守卫：在没放错的祭座里轮着挑，各在旁边找落点放一只墓穴守灵，盯着放错的人；返回放出几只 */
    private int summonGuards(ServerLevel server, ServerPlayer target, Direction wrongSide, int count) {
        List<Direction> sides = new ArrayList<>();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (side != wrongSide) {
                sides.add(side);
            }
        }
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            BlockPos near = pedestalPos(sides.get(i % sides.size()));
            Vec3 spot = NestCoreBlockEntity.findSpot(server, near, ECHoannaNativesC.CATACOMB_REVENANT.get(), GUARD_RADIUS, server.random);
            if (spot == null) {
                continue;
            }
            CatacombRevenantEntity guard = ECHoannaNativesC.CATACOMB_REVENANT.get().create(server);
            if (guard == null) {
                continue;
            }
            guard.moveTo(spot.x, spot.y, spot.z, server.random.nextFloat() * 360F, 0F);
            guard.setYHeadRot(guard.getYRot());
            SpawnOrigin.set(guard, SpawnOrigin.Origin.SUMMONED);
            ForgeEventFactory.onFinalizeSpawn(guard, server, server.getCurrentDifficultyAt(guard.blockPosition()), MobSpawnType.MOB_SUMMONED, null, null);
            if (guard.isSpawnCancelled()) {
                continue;
            }
            guard.setTarget(target);
            if (server.addFreshEntity(guard)) {
                spawned++;
                lastGuards.add(guard);
            }
        }
        return spawned;
    }

    /** 最近一次答错召出来的守卫实例（不存盘，只给测试看；远处没人的区块里按 UUID 查不到刚加进去的实体，所以记实例） */
    public List<CatacombRevenantEntity> lastGuards() {
        return List.copyOf(lastGuards);
    }

    /** 答对：开门、记名单、清空这一次 */
    private void solve(ServerLevel server, ServerPlayer solver) {
        resetAttempt(server);
        openDoor(server);
        BoundingBox room = antechamber();
        int seconds = Math.max(1, ECSealedVault.Config.doorOpenTicks() / 20);
        for (ServerPlayer player : candidates(server)) {
            if (player.level() != server || !player.isAlive() || player.isSpectator() || player.isCreative() || !ECProtection.isRealPlayer(player)
                    || !room.isInside(player.blockPosition())) {
                continue;
            }
            unlock(player.getUUID());
            fails.remove(player.getUUID());
            player.sendSystemMessage(Component.translatable(key("solved"), seconds).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        fails.remove(solver.getUUID());
        server.playSound(null, keyholePos(), ECSealedVault.UNLOCK.get(), SoundSource.BLOCKS, 1.2F, 0.8F);
        setChanged();
    }

    private void unlock(UUID id) {
        unlocked.remove(id);
        unlocked.add(id);
        trim(unlocked, "解开名单");
    }

    /** 开门 {@code door_open_ticks} 刻（已经开着的从现在起重新算） */
    public void openDoor(ServerLevel server) {
        int ticks = ECSealedVault.Config.doorOpenTicks();
        openUntil = gameTime(server) + ticks;
        for (BlockPos cell : VaultLayout.doorCells(worldPosition)) {
            BlockState state = server.getBlockState(cell);
            if (state.is(ECSealedVault.VAULT_SEAL.get())) {
                if (!state.getValue(VaultSealBlock.OPEN)) {
                    server.setBlock(cell, state.setValue(VaultSealBlock.OPEN, true), Block.UPDATE_ALL);
                }
                server.scheduleTick(cell, state.getBlock(), ticks);
            }
        }
        server.playSound(null, worldPosition.offset(VaultLayout.DOOR_FROM_CORE).above(), ECSealedVault.DOOR.get(), SoundSource.BLOCKS, 1.0F, 0.6F);
        setChanged();
    }

    /** 门还要开多少刻（按主世界 gameTime；封印石的计划刻到了先问这个，没到就再排一次；时间被往回拨时最多按满额） */
    public long openRemaining(ServerLevel server) {
        long remaining = openUntil - gameTime(server);
        return Math.max(0L, Math.min(remaining, ECSealedVault.Config.doorOpenTicks()));
    }

    /** 名单上的人、或者人在宝库室里面（挖墙进来出不去的）右键关着的封印石：门再开一次 */
    public boolean openFromSeal(ServerPlayer player) {
        if (!(level instanceof ServerLevel server) || !ECProtection.isRealPlayer(player)) {
            return false;
        }
        if (unlocked.contains(player.getUUID()) || chamber().isInside(player.blockPosition())) {
            openDoor(server);
            tell(player, "seal.opened");
            return true;
        }
        tell(player, "seal.closed");
        return false;
    }

    // ================================================================== 领奖

    public ClaimResult claim(ServerPlayer player) {
        if (!(level instanceof ServerLevel server) || !ECProtection.isRealPlayer(player)) {
            return ClaimResult.IGNORED;
        }
        UUID id = player.getUUID();
        if (!unlocked.contains(id)) {
            tell(player, "core.not_listed");
            return ClaimResult.NOT_LISTED;
        }
        if (claimed.contains(id)) {
            tell(player, "core.claimed");
            return ClaimResult.ALREADY_CLAIMED;
        }
        claimed.add(id);
        trim(claimed, "已领名单");
        setChanged();
        LootTable table = server.getServer().getLootData().getLootTable(REWARD_TABLE);
        LootParams params = new LootParams.Builder(server)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.CHEST);
        table.getRandomItems(params, stack -> ECItemEntities.giveOrDrop(player, stack));
        server.playSound(null, worldPosition, ECSealedVault.CLAIM.get(), SoundSource.BLOCKS, 1.0F, 0.8F);
        tell(player, "core.reward");
        return ClaimResult.REWARDED;
    }

    public boolean isUnlocked(UUID id) {
        return unlocked.contains(id);
    }

    public boolean hasClaimed(UUID id) {
        return claimed.contains(id);
    }

    public int unlockedCount() {
        return unlocked.size();
    }

    /**
     * 这个人还被封锁多少刻（0 = 没封锁）：日内时间和 gameTime 两个钟里剩得少的那个，最多满额。
     * gameTime 比封锁开始时还小（存档被换过）时那个钟不算，只看日内时间
     */
    public long lockRemaining(ServerLevel server, UUID id) {
        Fails entry = fails.get(id);
        if (entry == null || entry.until() <= 0L) {
            return 0L;
        }
        long lockout = ECSealedVault.Config.lockoutTicks();
        long remaining = entry.until() - dayTime(server);
        long now = gameTime(server);
        if (entry.start() != Fails.NO_START && now >= entry.start()) {
            remaining = Math.min(remaining, entry.start() + lockout - now);
        }
        return remaining <= 0L ? 0L : Math.min(remaining, lockout);
    }

    /** 这个人现在的连错次数（封锁时清成 0） */
    public int failCount(UUID id) {
        Fails entry = fails.get(id);
        return entry == null ? 0 : entry.count();
    }

    private boolean lockedNotice(ServerLevel server, ServerPlayer player) {
        long remaining = lockRemaining(server, player.getUUID());
        if (remaining <= 0L) {
            return false;
        }
        player.displayClientMessage(Component.translatable(key("locked"), ECSealedVault.Config.lockoutFailures(), minutes(remaining)), true);
        return true;
    }

    // ================================================================== 状态切换

    private void resetAttempt(ServerLevel server) {
        keyed = false;
        step = 0;
        filled = 0;
        Arrays.fill(spent, 0);
        setKeyholeLit(server, false);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            setPedestalLit(server, side, false);
        }
        setChanged();
    }

    private void setKeyholeLit(ServerLevel server, boolean lit) {
        BlockPos pos = keyholePos();
        BlockState state = server.getBlockState(pos);
        if (state.is(ECSealedVault.VAULT_KEYHOLE.get()) && state.getValue(VaultKeyholeBlock.KEYED) != lit) {
            server.setBlock(pos, state.setValue(VaultKeyholeBlock.KEYED, lit), Block.UPDATE_ALL);
        }
    }

    private void setPedestalLit(ServerLevel server, Direction side, boolean lit) {
        BlockPos pos = pedestalPos(side);
        BlockState state = server.getBlockState(pos);
        if (state.is(ECSealedVault.VAULT_PEDESTAL.get()) && state.getValue(VaultPedestalBlock.FILLED) != lit) {
            server.setBlock(pos, state.setValue(VaultPedestalBlock.FILLED, lit), Block.UPDATE_ALL);
        }
    }

    // ================================================================== 找核心（入口方块用；只看已加载的区块，不扫方块）

    @Nullable
    public static VaultCoreBlockEntity at(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof VaultCoreBlockEntity core ? core : null;
    }

    /** 锁孔 → 核心 */
    @Nullable
    public static VaultCoreBlockEntity fromKeyhole(Level level, BlockPos keyhole) {
        return at(level, keyhole.offset(VaultLayout.CORE_FROM_KEYHOLE));
    }

    /** 祭座 → 它在锁孔哪一侧（锁孔在它东南西北 3 格中的哪一格）；找不到返回 null */
    @Nullable
    public static Direction pedestalSide(Level level, BlockPos pedestal) {
        for (Direction toward : Direction.Plane.HORIZONTAL) {
            BlockPos keyhole = pedestal.relative(toward, VaultLayout.PEDESTAL_DISTANCE);
            if (level.isLoaded(keyhole) && level.getBlockState(keyhole).is(ECSealedVault.VAULT_KEYHOLE.get())) {
                return toward.getOpposite();
            }
        }
        return null;
    }

    /** 封印石 → 核心（九个候选） */
    @Nullable
    public static VaultCoreBlockEntity fromSeal(Level level, BlockPos seal) {
        for (BlockPos candidate : VaultLayout.coreCandidatesFromSeal(seal)) {
            VaultCoreBlockEntity core = at(level, candidate);
            if (core != null) {
                return core;
            }
        }
        return null;
    }

    /** 先辈碑 → 核心：先找到碑的最下面一块，再在核心那一列往下找 {@link VaultLayout#STELE_PROBE_DEPTH} 格 */
    @Nullable
    public static VaultCoreBlockEntity fromStele(Level level, BlockPos stele) {
        BlockPos base = stele;
        for (int i = 0; i < VaultLayout.STELE_HEIGHT && level.getBlockState(base.below()).is(ECSealedVault.ELDER_STELE.get()); i++) {
            base = base.below();
        }
        BlockPos column = base.offset(VaultLayout.STELE_TO_CORE_DX, 0, VaultLayout.STELE_TO_CORE_DZ);
        if (!level.isLoaded(column)) {
            return null;
        }
        for (int dy = 1; dy <= VaultLayout.STELE_PROBE_DEPTH; dy++) {
            BlockPos pos = column.below(dy);
            if (level.getBlockState(pos).is(ECSealedVault.VAULT_CORE.get())) {
                return at(level, pos);
            }
        }
        return null;
    }

    // ================================================================== 时间、玩家（测试覆盖优先）

    public static long gameTime(ServerLevel level) {
        Long test = testGameTime;
        return test != null ? test : level.getServer().overworld().getGameTime();
    }

    public static long dayTime(ServerLevel level) {
        Long test = testDayTime;
        return test != null ? test : level.getServer().overworld().getDayTime();
    }

    public static boolean peaceful(ServerLevel level) {
        Boolean test = testPeaceful;
        return test != null ? test : level.getDifficulty() == Difficulty.PEACEFUL;
    }

    public static List<? extends ServerPlayer> candidates(ServerLevel level) {
        List<ServerPlayer> test = testPlayers;
        return test != null ? test : level.players();
    }

    /** 测试用：覆盖主世界 gameTime / 日内时间 / 和平难度 / 在场玩家（null 用真值） */
    public static void setTestOverrides(@Nullable Long gameTime, @Nullable Long dayTime, @Nullable Boolean peaceful, @Nullable List<ServerPlayer> players) {
        testGameTime = gameTime;
        testDayTime = dayTime;
        testPeaceful = peaceful;
        testPlayers = players;
    }

    public static void clearTestOverrides() {
        setTestOverrides(null, null, null, null);
    }

    // ================================================================== 文字

    private static String key(String suffix) {
        return "message.essentialcraft5.sealed_vault." + suffix;
    }

    private static void tell(ServerPlayer player, String suffix) {
        player.displayClientMessage(Component.translatable(key(suffix)), true);
    }

    public static Component elementName(VaultElement element) {
        return Component.translatable(key("element." + element.getSerializedName()));
    }

    /** 刻数 → 分钟（向上取整，至少 1） */
    private static long minutes(long ticks) {
        return Math.max(1L, (ticks + 1199L) / 1200L);
    }

    // ================================================================== 名单上限

    private static void trim(LinkedHashSet<UUID> set, String what) {
        boolean warned = false;
        Iterator<UUID> it = set.iterator();
        while (set.size() > MAX_NAMES && it.hasNext()) {
            it.next();
            it.remove();
            if (!warned) {
                EssentialCraft5.LOGGER.warn("[封印宝库] {} 超过 {} 人，挤掉了最早的记录", what, MAX_NAMES);
                warned = true;
            }
        }
    }

    private void remember(UUID id, Fails value) {
        fails.remove(id);
        fails.put(id, value);
        Iterator<UUID> it = fails.keySet().iterator();
        while (fails.size() > MAX_NAMES && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    // ================================================================== 存读档

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (hasPuzzle) {
            CompoundTag p = new CompoundTag();
            p.putLong("seed", puzzleSeed);
            p.putIntArray("pedestals", puzzle.pedestals().clone());
            p.putIntArray("order", puzzle.order().clone());
            p.putIntArray("riddle", puzzle.riddle().clone());
            tag.put(TAG_PUZZLE, p);
        }
        CompoundTag attempt = new CompoundTag();
        attempt.putBoolean("key", keyed);
        attempt.putInt("step", step);
        attempt.putInt("filled", filled);
        attempt.putIntArray("spent", spent.clone());
        tag.put(TAG_ATTEMPT, attempt);
        if (guardTime != Long.MIN_VALUE) {
            tag.putLong(TAG_GUARD, guardTime);
        }
        if (openUntil != 0L) {
            tag.putLong(TAG_OPEN_UNTIL, openUntil);
        }
        tag.put(TAG_UNLOCKED, uuidList(unlocked));
        tag.put(TAG_CLAIMED, uuidList(claimed));
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Fails> entry : fails.entrySet()) {
            CompoundTag f = new CompoundTag();
            f.putUUID("id", entry.getKey());
            f.putInt("count", entry.getValue().count());
            f.putLong("until", entry.getValue().until());
            if (entry.getValue().start() != Fails.NO_START) {
                f.putLong("start", entry.getValue().start());
            }
            list.add(f);
        }
        tag.put(TAG_FAILS, list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        hasPuzzle = tag.contains(TAG_PUZZLE, Tag.TAG_COMPOUND);
        if (hasPuzzle) {
            CompoundTag p = tag.getCompound(TAG_PUZZLE);
            puzzleSeed = p.getLong("seed");
            puzzle = VaultPuzzle.read(p.getIntArray("pedestals"), p.getIntArray("order"), p.getIntArray("riddle"));
        }
        CompoundTag attempt = tag.getCompound(TAG_ATTEMPT);
        keyed = attempt.getBoolean("key");
        step = Math.max(0, Math.min(3, attempt.getInt("step")));
        filled = Math.max(0, attempt.getInt("filled"));
        int[] saved = attempt.getIntArray("spent");
        for (int i = 0; i < spent.length; i++) {
            spent[i] = i < saved.length ? Math.max(0, saved[i]) : 0;
        }
        guardTime = tag.contains(TAG_GUARD, Tag.TAG_LONG) ? tag.getLong(TAG_GUARD) : Long.MIN_VALUE;
        openUntil = tag.getLong(TAG_OPEN_UNTIL);
        readUuids(tag.getList(TAG_UNLOCKED, Tag.TAG_INT_ARRAY), unlocked);
        readUuids(tag.getList(TAG_CLAIMED, Tag.TAG_INT_ARRAY), claimed);
        fails.clear();
        ListTag list = tag.getList(TAG_FAILS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && fails.size() < MAX_NAMES; i++) {
            CompoundTag f = list.getCompound(i);
            if (f.hasUUID("id")) {
                fails.put(f.getUUID("id"), new Fails(Math.max(0, f.getInt("count")), f.getLong("until"),
                        f.contains("start", Tag.TAG_LONG) ? f.getLong("start") : Fails.NO_START));
            }
        }
    }

    private static ListTag uuidList(LinkedHashSet<UUID> ids) {
        ListTag list = new ListTag();
        for (UUID id : ids) {
            list.add(NbtUtils.createUUID(id));
        }
        return list;
    }

    private static void readUuids(ListTag list, LinkedHashSet<UUID> into) {
        into.clear();
        for (Tag entry : list) {
            if (into.size() >= MAX_NAMES) {
                break;
            }
            try {
                into.add(NbtUtils.loadUUID(entry));
            } catch (IllegalArgumentException e) {
                EssentialCraft5.LOGGER.warn("[封印宝库] 名单里有一条读不出来，跳过");
            }
        }
    }
}
