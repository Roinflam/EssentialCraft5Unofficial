package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.advancement.StructureFormedTrigger;
import com.flameforge.essentialcraft5.ambience.AmbienceParticles;
import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.ambience.MachineAmbiences;
import com.flameforge.essentialcraft5.block.RedstonePulse;
import com.flameforge.essentialcraft5.client.render.SpellFx;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.ember.EmberKind;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData;
import com.flameforge.essentialcraft5.registry.ECEmberForge;
import com.flameforge.essentialcraft5.structure.StructureCache;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.LongPredicate;
import java.util.function.Predicate;

/**
 * 余烬锻炉（第七批 M2，batch7.md §4.2）。对应原作 TileEmberForge。
 * <p>
 * 玩家视角：搭好结构（{@link ECEmberForge#STRUCTURE}：下一层 5×5 虚空石；锻炉这一层四角虚空石、正四向距离 2 的淡青护板；上一层四角魔力护板），
 * 往四块淡青护板上各扔一个余烬、四块魔力护板上各扔一个元素焦点，天黑后等 {@code ticks_required} 刻（默认 500 刻 = 25 秒），
 * 锻炉上方蹦出一把余烬元素剑（{@link ForgedSwordData}：焦点定主元素，余烬两两配对定附加伤害和命中效果）。
 *
 * <h2>每刻（只在服务端）</h2>
 * <ol>
 *   <li>结构没成形：进度清零、不再托着物品（结构缓存 {@value #STRUCTURE_RECHECK} 刻复查一次，正下方那块变了立即标脏）。</li>
 *   <li>潜行空手右键放开过：{@value #RELEASE_TICKS} 刻内不托物品，进度清零。放开的掉落物打上标记（{@link ECItemEntities#markForgeReleased}），
 *       标记到期前所有锻炉都不托它：相邻两台共用边上的格子时，另一台不会在下一轮又把它托回去。</li>
 *   <li>每 {@value #SCAN_INTERVAL} 刻一轮：一次 5×2×5 实体查询，按格分配：余烬格（淡青护板上方）只认 8 种余烬、
 *       焦点格（魔力护板上方）只认 4 种焦点，都按物品本身认（焦点不看耐久）；跳过永远捡不起来的（展示品）、指定了别人拾取的
 *       （按 {@link ECProtection#actingOwner} 问，没有主人时这类一律不碰，同 6b 魔力熔炉）和刚被潜行放开的。一格里有几件时优先上一轮托着的那件。
 *       找到的都托住（下一条），然后：
 *       <ul>
 *         <li>有红石：只托不推进，进度留着（红石是玩家按下的暂停，暂停中的东西照样托着、不会过期，去掉红石接着走）。</li>
 *         <li>有槽位的实体区段没在跑、或者在跑但实体还没读完（{@link #slotsReady}，同原版 {@code isPositionTickingWithEntitiesLoaded}）：
 *             托住查得到的那几格，不推进，进度留着。槽位落在相邻区块时，那边的实体是异步读的，区块进了实体 tick 范围以后还要等一会儿才加进世界；
 *             原作这时查空就清零。</li>
 *       </ul></li>
 *   <li>托住：摆到格子水平正中、水平速度清零（偏离超过 {@value #SNAP_DISTANCE} 格或还在横着动时才摆；竖直方向靠重力落在护板顶面上），拾取延迟续到 {@value #HOLD_PICKUP_DELAY} 刻，
 *       寿命续到「年龄 + {@value #LIFESPAN_EXTENSION}」。不设无重力、不设永不可拾取：结构坏了、锻炉被拆了、区块卸载了，最多 {@value #HOLD_PICKUP_DELAY} 刻就能捡。</li>
 *   <li>八样齐、并且「不要求夜里 / 维度没有天光 / 不是白天」（原作 L115；雷暴的白天 {@code isDay()} 为假，也算）：进度 +{@value #SCAN_INTERVAL}，
 *       每 {@value #SOUND_INTERVAL} 刻一次传送门声；否则进度清零。</li>
 *   <li>到点：再核一遍 8 个掉落物都活着、东西没换（相邻两台共用边上的格子时，先到点的那台可能刚在这一刻扣掉），
 *       <b>先记下 8 件的注册名再扣</b>，每格扣 1（{@code setItem} 缩过的副本，空了 {@code discard}），在 (x+0.5, y+2.1, z+0.5) 生成剑、竖直速度 0.2。
 *       核不过就不出剑、不扣。</li>
 * </ol>
 *
 * <h2>和原作不同</h2>
 * <ul>
 *   <li>原作先扣后读名字：焦点一组 1 个，扣完是空栈、读出来是空气，剑永远是「复合 / 复合」，单个扔进去的余烬也记不下（改玩法：主副元素和余烬效果第一次真正生效）。</li>
 *   <li>原作两端每刻 8 次实体查询、37 格结构判定，每刻设位置和寿命，服务端每刻最多 4 个火焰声包，客户端每秒约 2 000 个粒子；
 *       这里只在服务端、结构缓存、每 10 刻一次查询，摆一次就不再摆，火焰声客户端本地播，粒子抽稀。</li>
 *   <li>原作人在结构上走、夜里的怪路过都能把漂着的东西捡走（进度归零）；这里托住的东西拾取延迟续期，另加潜行右键取回（改玩法）。</li>
 *   <li>原作 {@code TicksRequired} 最小 0（每刻一把）；这里 10~72 000。</li>
 * </ul>
 * 客户端只收「成形 / 8 格占用掩码 / 锻造中」（变了才发，同一台至少隔 10 刻），进度在锻造中自己累加，只用来决定岩浆粒子多少。
 */
public class EmberForgeBlockEntity extends MRUBlockEntity {

    /** 服务端配置 machines.ember_forge（原作 tileentities.emberforge，默认值照原作） */
    public static final class Config {
        public static ForgeConfigSpec.BooleanValue nightRequired;
        public static ForgeConfigSpec.IntValue ticksRequired;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("ember_forge");
            nightRequired = builder.comment("Forge only at night. Dimensions without skylight (the Nether, the End) always count as night, and so does a thunderstorm.",
                            "只在夜里锻造。下界、末地这类没有天光的维度随时都算夜里，雷暴的白天也算")
                    .define("night_required", true);
            ticksRequired = builder.comment("Ticks to forge one sword (progress moves in steps of 10 ticks).", "锻一把剑要多少刻（进度按 10 刻一步走）")
                    .defineInRange("ticks_required", 500, 10, 72_000);
            builder.pop();
        }
    }

    /** 状态（右键报状态、测试用） */
    public enum Status {
        INCOMPLETE,
        /** 有红石信号：照常托住，不推进，进度留着 */
        POWERED,
        /** 潜行右键放开过，还没到时间 */
        RELEASED,
        /** 有槽位的实体区段没在跑、或者实体还没读完：托住查得到的那几格，不推进，进度留着 */
        UNLOADED,
        /** 八样没齐 */
        MISSING,
        /** 八样齐了，等天黑 */
        WAITING_NIGHT,
        FORGING
    }

    /** 余烬格（相对锻炉，淡青护板正上方），顺序照原作 ember_0~3 */
    public static final List<BlockPos> EMBER_SLOTS = List.of(new BlockPos(2, 1, 0), new BlockPos(-2, 1, 0), new BlockPos(0, 1, 2), new BlockPos(0, 1, -2));
    /** 焦点格（魔力护板正上方），顺序照原作 focus_0~3 */
    public static final List<BlockPos> FOCUS_SLOTS = List.of(new BlockPos(2, 2, 2), new BlockPos(-2, 2, 2), new BlockPos(2, 2, -2), new BlockPos(-2, 2, -2));
    /** 8 格：0~3 余烬、4~7 焦点 */
    public static final int SLOT_COUNT = 8;
    private static final int ALL_SLOTS = (1 << SLOT_COUNT) - 1;

    /** 结构复查间隔（刻）：正下方那块靠邻居通知，别的格子离得远，靠复查 */
    public static final int STRUCTURE_RECHECK = 40;
    /** 每隔几刻查一次槽位里的掉落物、进度每轮走几刻 */
    public static final int SCAN_INTERVAL = 10;
    /** 托住的东西拾取延迟续到多少刻（比查询间隔长，托着的时候一直捡不起来） */
    public static final int HOLD_PICKUP_DELAY = 20;
    /** 寿命续到「年龄 + 这么多」 */
    public static final int LIFESPAN_EXTENSION = 6_000;
    /** 偏离格子中心超过这么多格才重新摆 */
    public static final double SNAP_DISTANCE = 0.05D;
    /** 潜行右键放开后多少刻不再托住 */
    public static final int RELEASE_TICKS = 200;
    /** 锻造中多少刻一次传送门声（原作 soundArray 50） */
    public static final int SOUND_INTERVAL = 50;
    /** 剑生成的高度与竖直速度（原作 pos.y + 2.1、motionY 0.2） */
    public static final double SWORD_HEIGHT = 2.1D;
    public static final double SWORD_UP_SPEED = 0.2D;
    /** 镜头离锻炉多远以外不放粒子 */
    public static final double PARTICLE_RANGE = 32D;

    private static final String TAG_PROGRESS = "progress";
    private static final String TAG_FORMED = "formed";
    private static final String TAG_MASK = "slots";
    private static final String TAG_FORGING = "forging";

    private static final DustParticleOptions EMBER_DUST = new DustParticleOptions(new Vector3f(1F, 0.2F, 0.1F), 1F);
    private static final DustParticleOptions FOCUS_DUST = new DustParticleOptions(new Vector3f(0.2F, 0.3F, 1F), 1F);

    private final StructureCache structure = new StructureCache(ECEmberForge.STRUCTURE, STRUCTURE_RECHECK);

    // ---- 服务端状态 ----
    private int progress;
    private Status status = Status.INCOMPLETE;
    /** 还有几刻才查下一轮（0 = 这一刻查） */
    private int scanCountdown;
    private int soundCooldown;
    /** 潜行右键放开到这个游戏时间为止（不存盘） */
    private long releaseUntil = Long.MIN_VALUE;
    /** 上一轮每格托着的掉落物（一格里有几件时优先它，免得来回换） */
    private final UUID[] lastClaimed = new UUID[SLOT_COUNT];
    /** 实体查询次数（只给测试看） */
    private long scans;
    /** 出了几把剑（只给测试看） */
    private int forgedCount;

    // ---- 发给客户端的 ----
    private boolean formed;
    /** 第 i 位 = 第 i 格托着东西 */
    private int mask;
    private boolean forging;
    /** 客户端：锻造中自己累加的刻数（只决定岩浆粒子多少） */
    private int clientProgress;

    // ---- 只给 GameTest / 展示厅的这一台自己的覆盖（null = 照常） ----
    @Nullable
    private Boolean testNight;
    @Nullable
    private Integer testTicks;
    @Nullable
    private Boolean testNightRequired;
    @Nullable
    private Boolean testEntitiesLoaded;

    public EmberForgeBlockEntity(BlockPos pos, BlockState state) {
        // 原作 TileMRUGeneric(0) + setSlotsNum(0)：没有 MRU、没有格子
        super(ECEmberForge.EMBER_FORGE_BE.get(), pos, state, () -> 0, 0);
    }

    // ------------------------------------------------------------------
    // 槽位
    // ------------------------------------------------------------------

    /** 第 i 格相对锻炉的偏移 */
    public static BlockPos slot(int index) {
        return index < EMBER_SLOTS.size() ? EMBER_SLOTS.get(index) : FOCUS_SLOTS.get(index - EMBER_SLOTS.size());
    }

    public static boolean isEmberSlot(int index) {
        return index < EMBER_SLOTS.size();
    }

    /** 第 i 格能不能放这件东西：余烬格只认 8 种余烬、焦点格只认 4 种焦点（按物品本身认，不看耐久、NBT） */
    public static boolean accepts(int index, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (isEmberSlot(index)) {
            return EmberKind.of(stack) != null;
        }
        return ForgedSwordData.Element.ofFocus(ForgeRegistries.ITEMS.getKey(stack.getItem())) != null;
    }

    /** 第 i 格的世界坐标 */
    public BlockPos cell(int index) {
        return worldPosition.offset(slot(index));
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return null;
    }

    @Override
    protected boolean syncInventoryToClient() {
        return false;
    }

    @Override
    protected boolean syncBalanceToClient() {
        return false;
    }

    // ------------------------------------------------------------------
    // 配置与覆盖
    // ------------------------------------------------------------------

    public int ticksRequired() {
        return testTicks != null ? testTicks : ECConfig.get(Config.ticksRequired);
    }

    public boolean nightRequired() {
        return testNightRequired != null ? testNightRequired : ECConfig.get(Config.nightRequired);
    }

    /**
     * 只给 GameTest / 展示厅：这一台「现在算不算夜里」和锻一把要几刻（null = 照常）。只改这一台，不碰配置文件；
     * GameTest 各测试共用一个世界，改世界时间会影响别的测试。
     */
    public void setTestOverrides(@Nullable Boolean night, @Nullable Integer ticks) {
        testNight = night;
        testTicks = ticks;
    }

    /** 只给 GameTest：这一台要不要求夜里（null = 用配置 night_required） */
    public void setTestNightRequired(@Nullable Boolean required) {
        testNightRequired = required;
    }

    /** 只给 GameTest：当槽位的实体区段在不在跑、实体读完没有（null = 照实查） */
    public void setTestEntitiesLoaded(@Nullable Boolean loaded) {
        testEntitiesLoaded = loaded;
    }

    /** 展示厅：直接设进度（服务端是真进度；客户端只影响岩浆粒子多少） */
    public void setShowroomProgress(int value) {
        if (level != null && level.isClientSide) {
            clientProgress = Math.max(0, value);
        } else {
            progress = Math.max(0, value);
            markSaveDirty();
        }
    }

    /**
     * 能不能推进（原作 L115 {@code !nightRequired || isNether || !hasSkyLight || !isDaytime}；1.20 的下界本来就没有天光）。
     * 纯函数，GameTest 直接测「没有天光的维度随时都行」。
     */
    public static boolean canForge(boolean nightRequired, boolean hasSkyLight, boolean isDay) {
        return !nightRequired || !hasSkyLight || !isDay;
    }

    private boolean canForgeNow(ServerLevel server) {
        boolean day = testNight != null ? !testNight : server.isDay();
        return canForge(nightRequired(), server.dimensionType().hasSkyLight(), day);
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        updateStructure(server, false);
        // 顺序：结构 → 放开 → 红石。红石放在放开后面：有红石时潜行放开的东西不能在下一轮又被托回去；结构坏了也不托
        if (!structure.formed()) {
            resetProgress();
            Arrays.fill(lastClaimed, null);
            status = Status.INCOMPLETE;
            setClientState(0, false);
            return;
        }
        if (server.getGameTime() < releaseUntil) {
            resetProgress();
            status = Status.RELEASED;
            setClientState(0, false);
            return;
        }
        boolean powered = RedstonePulse.isPowered(server, worldPosition);
        if (powered && status != Status.POWERED) {
            // 一来红石就停（锻造的画面跟着停）；托住照常在下面每轮做
            status = Status.POWERED;
            setClientState(mask, false);
        }
        if (scanCountdown > 0) {
            scanCountdown--;
            return;
        }
        scanCountdown = SCAN_INTERVAL - 1;
        scan(server, powered);
    }

    /** 一轮：查槽位、托住；没有红石、实体区段都好了才推进、到点出剑 */
    private void scan(ServerLevel server, boolean powered) {
        boolean loaded = slotsLoaded(server);
        ItemEntity[] found = findSlotItems(server);
        int present = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemEntity entity = found[i];
            if (entity != null) {
                hold(entity, i);
                present |= 1 << i;
                lastClaimed[i] = entity.getUUID();
            } else {
                lastClaimed[i] = null;
            }
        }
        if (powered || !loaded) {
            // 只托不推进，进度留着：红石是玩家按下的暂停；区段没好时查不到的格子可能只是实体还没读进来，不能当缺件清零
            status = powered ? Status.POWERED : Status.UNLOADED;
            setClientState(present, false);
            return;
        }
        boolean nowForging = false;
        if (present != ALL_SLOTS) {
            resetProgress();
            status = Status.MISSING;
        } else if (!canForgeNow(server)) {
            resetProgress();
            status = Status.WAITING_NIGHT;
        } else {
            status = Status.FORGING;
            nowForging = true;
            soundCooldown -= SCAN_INTERVAL;
            if (soundCooldown <= 0) {
                server.playSound(null, worldPosition, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 1F, 0.2F);
                soundCooldown = SOUND_INTERVAL;
            }
            progress += SCAN_INTERVAL;
            markSaveDirty();
            if (progress >= ticksRequired()) {
                progress = 0;
                markSaveDirty();
                if (finish(server, found)) {
                    present = 0;
                    for (int i = 0; i < SLOT_COUNT; i++) {
                        if (found[i].isAlive()) {
                            present |= 1 << i;
                        }
                    }
                }
            }
        }
        setClientState(present, nowForging);
    }

    /**
     * 到点出剑（见类注释第 7 条）。
     *
     * @return 出了剑（核不过返回 false：不出剑、不扣）
     */
    private boolean finish(ServerLevel server, ItemEntity[] found) {
        List<ResourceLocation> embers = new ArrayList<>(EMBER_SLOTS.size());
        List<ResourceLocation> foci = new ArrayList<>(FOCUS_SLOTS.size());
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemEntity entity = found[i];
            if (entity == null || !entity.isAlive() || !accepts(i, entity.getItem())) {
                return false;
            }
            for (int j = 0; j < i; j++) {
                if (found[j] == entity) {
                    return false;
                }
            }
            // 先记下注册名（原作先扣后读，焦点扣完是空栈，读到的是空气）
            (isEmberSlot(i) ? embers : foci).add(ForgeRegistries.ITEMS.getKey(entity.getItem().getItem()));
        }
        for (ItemEntity entity : found) {
            ItemStack rest = entity.getItem().copy();
            rest.shrink(1);
            if (rest.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(rest);
            }
        }
        ForgedSwordData data = ForgedSwordData.forge(embers, foci, server.random);
        ItemStack sword = ECEmberForge.FORGED_ELEMENTAL_SWORD.get().forged(data);
        ItemEntity drop = new ItemEntity(server, worldPosition.getX() + 0.5D, worldPosition.getY() + SWORD_HEIGHT, worldPosition.getZ() + 0.5D,
                sword, 0D, SWORD_UP_SPEED, 0D);
        server.addFreshEntity(drop);
        forgedCount++;
        return true;
    }

    /**
     * 每格挑一件能托的掉落物（一次查询，按格分配）；没有的格子为 null。
     * 只认这一格该放的东西、跳过永远捡不起来的、指定了别人拾取的和刚被潜行放开的；一格里有几件时优先上一轮托着的那件。
     */
    private ItemEntity[] findSlotItems(ServerLevel server) {
        ItemEntity[] found = new ItemEntity[SLOT_COUNT];
        scans++;
        AABB area = new AABB(worldPosition.offset(-2, 1, -2), worldPosition.offset(3, 3, 3));
        List<ItemEntity> items = server.getEntitiesOfClass(ItemEntity.class, area, entity -> entity.isAlive() && !entity.getItem().isEmpty());
        if (items.isEmpty()) {
            return found;
        }
        UUID owner = ECProtection.actingOwner(this);
        long now = server.getGameTime();
        for (int i = 0; i < SLOT_COUNT; i++) {
            AABB cell = new AABB(cell(i));
            ItemEntity pick = null;
            for (ItemEntity entity : items) {
                if (!entity.getBoundingBox().intersects(cell) || !accepts(i, entity.getItem()) || !usable(entity, owner, now)) {
                    continue;
                }
                if (entity.getUUID().equals(lastClaimed[i])) {
                    pick = entity;
                    break;
                }
                if (pick == null) {
                    pick = entity;
                }
            }
            found[i] = pick;
        }
        return found;
    }

    /**
     * 能不能托这件：不是永不可拾取的，没指定拾取人或者指定的就是锻炉替谁干活的那个人，也不是刚被哪一台潜行放开的。
     * 放开标记放在最后查：读它要用 {@code getPersistentData()}，没有数据的实体会因此建一个空的 ForgeData，只让落在槽位上、东西也对的几件走到这一步。
     */
    private static boolean usable(ItemEntity entity, @Nullable UUID owner, long now) {
        return !ECItemEntities.neverPickUp(entity) && ECItemEntities.pickableBy(entity, owner) && !ECItemEntities.forgeReleased(entity, now);
    }

    /** 托住第 i 格的掉落物（见类注释第 5 条） */
    private void hold(ItemEntity entity, int index) {
        BlockPos cell = cell(index);
        double x = cell.getX() + 0.5D;
        double z = cell.getZ() + 0.5D;
        double dx = entity.getX() - x;
        double dz = entity.getZ() - z;
        Vec3 motion = entity.getDeltaMovement();
        // 只摆水平位置、清水平速度：竖直方向交给重力，落在护板顶面上（格子里铺了地毯之类的也照样落在它上面，不会被硬塞进方块里来回弹）
        if (dx * dx + dz * dz > SNAP_DISTANCE * SNAP_DISTANCE || motion.horizontalDistanceSqr() > 1.0E-4D) {
            entity.setPos(x, entity.getY(), z);
            entity.setDeltaMovement(0D, Math.min(0D, motion.y), 0D);
        }
        if (ECItemEntities.pickupDelay(entity) < HOLD_PICKUP_DELAY) {
            entity.setPickUpDelay(HOLD_PICKUP_DELAY);
        }
        // 年龄 -32768 是永不消失，不动
        if (entity.getAge() != Short.MIN_VALUE) {
            entity.lifespan = Math.max(entity.lifespan, entity.getAge() + LIFESPAN_EXTENSION);
        }
    }

    private boolean slotsLoaded(ServerLevel server) {
        if (testEntitiesLoaded != null) {
            return testEntitiesLoaded;
        }
        return slotsReady(worldPosition, server::isPositionEntityTicking, server::areEntitiesLoaded);
    }

    /**
     * 锻炉在 origin 时，8 个槽位是不是都「实体区段在跑、并且实体已经读完」（同原版 {@code ServerLevel.isPositionTickingWithEntitiesLoaded}）。
     * {@code isPositionEntityTicking} 只看区段可见性和距离：相邻区块一进实体 tick 范围就算在跑，实体却是异步从硬盘读的，
     * 要等之后某一刻 {@code entityManager.tick} 才加进世界（还排在方块实体 tick 后面），这期间槽位查出来是空的。
     * 锻炉自己那个区块没有这个问题：本区块实体没读完时方块实体根本不 tick。纯函数，GameTest 直接传 lambda 测。
     *
     * @param entityTicking  这一格的实体区段在不在跑（{@code ServerLevel::isPositionEntityTicking}）
     * @param entitiesLoaded 按区块坐标（{@link ChunkPos#asLong(BlockPos)}）问实体读完没有（{@code ServerLevel::areEntitiesLoaded}）
     */
    public static boolean slotsReady(BlockPos origin, Predicate<BlockPos> entityTicking, LongPredicate entitiesLoaded) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            BlockPos cell = origin.offset(slot(i));
            if (!entityTicking.test(cell) || !entitiesLoaded.test(ChunkPos.asLong(cell))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 放开托着的东西：拾取延迟立即清零。玩家潜行右键时（{@code byPlayer}）另外 {@value #RELEASE_TICKS} 刻内不再托住、进度清零，
     * 并给放开的每件打上标记（{@link ECItemEntities#markForgeReleased}），同样这么久之内别的锻炉也不托；方块被拆时只放开、不打标记（共用格子的另一台接着托是对的）。
     *
     * @return 给玩家看的一行字
     */
    public Component releaseHeld(ServerLevel server, boolean byPlayer) {
        int released = 0;
        long until = server.getGameTime() + RELEASE_TICKS;
        for (ItemEntity entity : findSlotItems(server)) {
            if (entity != null) {
                entity.setNoPickUpDelay();
                if (byPlayer) {
                    ECItemEntities.markForgeReleased(entity, until);
                }
                released++;
            }
        }
        Arrays.fill(lastClaimed, null);
        if (byPlayer) {
            releaseUntil = until;
            resetProgress();
            status = Status.RELEASED;
            setClientState(0, false);
        }
        return Component.translatable("message.essentialcraft5.ember_forge.released", released, RELEASE_TICKS / 20);
    }

    /** 右键报状态（服务端）：先立即判一次结构，再按「结构 → 放开 → 红石 → 区段 → 缺件 → 天黑 → 进度」的顺序挑一句（同每刻判断的顺序） */
    public Component statusMessage(ServerLevel server) {
        updateStructure(server, true);
        if (!structure.formed()) {
            StructurePattern.Mismatch mismatch = structure.mismatch();
            return (mismatch != null ? mismatch.message().copy() : Component.translatable("message.essentialcraft5.ember_forge.incomplete"))
                    .withStyle(ChatFormatting.RED);
        }
        long now = server.getGameTime();
        if (now < releaseUntil) {
            return Component.translatable("message.essentialcraft5.ember_forge.releasing", Mth.ceil((releaseUntil - now) / 20F))
                    .withStyle(ChatFormatting.GOLD);
        }
        if (RedstonePulse.isPowered(server, worldPosition)) {
            return Component.translatable("message.essentialcraft5.ember_forge.powered", percent()).withStyle(ChatFormatting.GOLD);
        }
        if (!slotsLoaded(server)) {
            return Component.translatable("message.essentialcraft5.ember_forge.unloaded").withStyle(ChatFormatting.GOLD);
        }
        ItemEntity[] found = findSlotItems(server);
        int embers = 0;
        int foci = 0;
        BlockPos firstEmpty = null;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (found[i] == null) {
                if (isEmberSlot(i)) {
                    embers++;
                } else {
                    foci++;
                }
                if (firstEmpty == null) {
                    firstEmpty = cell(i);
                }
            }
        }
        if (firstEmpty != null) {
            return Component.translatable("message.essentialcraft5.ember_forge.missing", embers, foci,
                    firstEmpty.getX(), firstEmpty.getY(), firstEmpty.getZ()).withStyle(ChatFormatting.GOLD);
        }
        if (!canForgeNow(server)) {
            return Component.translatable("message.essentialcraft5.ember_forge.waiting_night").withStyle(ChatFormatting.GOLD);
        }
        return Component.translatable("message.essentialcraft5.ember_forge.forging", percent()).withStyle(ChatFormatting.GREEN);
    }

    private int percent() {
        int ticks = Math.max(1, ticksRequired());
        return Math.min(100, progress * 100 / ticks);
    }

    private void updateStructure(ServerLevel server, boolean now) {
        boolean was = structure.formed();
        if (now) {
            structure.checkNow(server, worldPosition, Direction.NORTH);
        } else {
            structure.check(server, worldPosition, Direction.NORTH);
        }
        boolean formedNow = structure.formed();
        if (!was && formedNow) {
            StructureFormedTrigger.triggerNearby(server, worldPosition, ECEmberForge.STRUCTURE_ID, 0);
        }
        if (formed != formedNow) {
            formed = formedNow;
            markClientDirty();
        }
    }

    /** 客户端要的两样（占用掩码、锻造中）变了才标记，发包由基类节流 */
    private void setClientState(int newMask, boolean newForging) {
        if (newMask != mask || newForging != forging) {
            mask = newMask;
            forging = newForging;
            markClientDirty();
        }
    }

    /** 进度变了只标记区块要存盘，不像 setChanged 那样通知比较器 */
    private void markSaveDirty() {
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    private void resetProgress() {
        soundCooldown = 0;
        if (progress != 0) {
            progress = 0;
            markSaveDirty();
        }
    }

    // ------------------------------------------------------------------
    // 客户端
    // ------------------------------------------------------------------

    /**
     * 粒子和火焰声（只在客户端）：成形时每 4 刻一团法术粒子（原作每刻一团）；每 4 刻给托着东西的格子各画一条连线
     * （余烬：从锻炉到格子一串红色粉尘、格子上方一朵火；焦点：一颗传送门粒子从焦点飞向锻炉、焦点上方一点蓝色粉尘）；
     * 锻造中每刻 ≤ 进度 / 100 个岩浆粒子（最多 5 个）；不在锻造时每个托着余烬的格子每刻 1/40 几率本地播一次火焰声。
     * 镜头 32 格以外不放；原版粒子 32 格外、粒子设置「最少」本来就不生成。
     */
    @Override
    protected void clientTick() {
        if (level == null) {
            return;
        }
        clientProgress = forging ? clientProgress + 1 : 0;
        if (!formed) {
            return;
        }
        double cx = worldPosition.getX() + 0.5D;
        double cy = worldPosition.getY();
        double cz = worldPosition.getZ() + 0.5D;
        if (!SpellFx.nearCamera(cx, cy, cz, PARTICLE_RANGE)) {
            return;
        }
        RandomSource random = level.random;
        boolean everyFourth = innerRotation % 4 == 0;
        if (everyFourth) {
            SpellFx.spawn(level, cx + (random.nextFloat() - random.nextFloat()) * 3F, cy, cz + (random.nextFloat() - random.nextFloat()) * 3F, 0D, 2D, 0D);
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            if ((mask & (1 << i)) == 0) {
                continue;
            }
            BlockPos offset = slot(i);
            if (isEmberSlot(i)) {
                if (everyFourth) {
                    for (int step = 0; step < 10; step++) {
                        double t = step / 10D;
                        AmbienceParticles.add(level, EMBER_DUST, cx + offset.getX() * t, cy + 0.9D + step / 12.5D, cz + offset.getZ() * t, 0D, 0D, 0D);
                    }
                    AmbienceParticles.add(level, ParticleTypes.FLAME, cx + offset.getX(), cy + 2D, cz + offset.getZ(), 0D, 0D, 0D);
                }
                if (!forging && random.nextInt(40) == 0) {
                    level.playLocalSound(cx + offset.getX(), cy + offset.getY(), cz + offset.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.2F, 2F, false);
                }
            } else if (everyFourth) {
                // 传送门粒子从「起点 + 速度」飞回起点：放在锻炉上方、速度指向焦点，看起来是从焦点流进锻炉
                AmbienceParticles.add(level, ParticleTypes.PORTAL, cx, cy + 0.9D, cz, offset.getX(), 1D, offset.getZ());
                AmbienceParticles.add(level, FOCUS_DUST, cx + offset.getX(), cy + 2.7D, cz + offset.getZ(), 0D, 0D, 0D);
            }
        }
        if (forging) {
            int lava = Math.min(5, clientProgress / 100);
            for (int i = 0; i < lava; i++) {
                AmbienceParticles.add(level, ParticleTypes.LAVA, worldPosition.getX() + random.nextDouble(), cy + 1D, worldPosition.getZ() + random.nextDouble(), 0D, 0D, 0D);
            }
        }
    }

    // ------------------------------------------------------------------
    // 访问
    // ------------------------------------------------------------------

    public StructureCache structure() {
        return structure;
    }

    /** 服务端：结构是否成形（最近一次确定的结论）；客户端：更新包里的成形标记 */
    public boolean isFormed() {
        return formed;
    }

    public int progress() {
        return progress;
    }

    public Status status() {
        return status;
    }

    /** 托着东西的格子（第 i 位 = 第 i 格）：服务端是上一轮的结果，客户端是更新包里的 */
    public int heldMask() {
        return mask;
    }

    public boolean isForging() {
        return forging;
    }

    @Nullable
    public BlockPos mismatchOffset() {
        return structure.mismatch() == null ? null : structure.mismatch().offset();
    }

    /** 只给测试：实体查询的次数 */
    public long scansForTest() {
        return scans;
    }

    /** 只给测试：出了几把剑 */
    public int forgedCountForTest() {
        return forgedCount;
    }

    // ------------------------------------------------------------------
    // 存盘与同步
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        structure.save(tag);
        if (progress > 0) {
            tag.putInt(TAG_PROGRESS, progress);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        structure.load(tag);
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        // 下面三个只在更新包里（客户端）；存档里没有
        if (tag.contains(TAG_FORMED, Tag.TAG_BYTE)) {
            formed = tag.getBoolean(TAG_FORMED);
        }
        if (tag.contains(TAG_MASK, Tag.TAG_BYTE)) {
            mask = tag.getByte(TAG_MASK) & ALL_SLOTS;
        }
        if (tag.contains(TAG_FORGING, Tag.TAG_BYTE)) {
            forging = tag.getBoolean(TAG_FORGING);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        // 进度只有服务端要；附近的人只要知道成形、哪几格托着东西、在不在锻造
        tag.remove(TAG_PROGRESS);
        tag.putBoolean(TAG_FORMED, formed);
        tag.putByte(TAG_MASK, (byte) mask);
        tag.putBoolean(TAG_FORGING, forging);
        return tag;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "EmberForge[%s %s progress=%d mask=%s]", worldPosition.toShortString(), status, progress, Integer.toBinaryString(mask));
    }

    @Override
    protected boolean ambientWorking() {
        return status == Status.FORGING;
    }

    @Override
    protected MachineAmbience.Profile ambience() {
        return MachineAmbiences.EMBER_FORGE;
    }
}
