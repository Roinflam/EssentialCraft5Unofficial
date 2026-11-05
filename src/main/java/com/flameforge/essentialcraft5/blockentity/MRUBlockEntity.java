package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.ambience.MachineAmbience;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IESPEHandler;
import com.flameforge.essentialcraft5.api.IMRUHandler;
import com.flameforge.essentialcraft5.espe.ESPEAccess;
import com.flameforge.essentialcraft5.espe.ESPEStorage;
import com.flameforge.essentialcraft5.espe.SidedESPEHandler;
import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.mru.LeakFeedRecord;
import com.flameforge.essentialcraft5.mru.MRULink;
import com.flameforge.essentialcraft5.mru.MRUStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Clearable;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

/**
 * 带 MRU 存储的机器方块实体基类。对应原作 TileMRUGeneric（49 个机器直接继承它）。
 *
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li>逻辑只在服务端跑（{@link #serverTick()}）。原作很多机器两端都推进度、扣 MRU，再靠每秒一次的整包 NBT 纠正。</li>
 *   <li>同步：原作每 20 tick 把整份 NBT 写两遍比对（TileStatTracker），变了就发给 32 格内所有人。
 *       这里只在「会影响客户端显示」的东西变了时发原版方块实体更新包，最快每 {@link #SYNC_INTERVAL} tick 一次：
 *       物品栏、平衡值（按界面精度比较）默认会触发；MRU 存量只有 {@link #syncMRUToClient()} 为 true 的机器才触发
 *       （MRU 每 tick 都在变，而界面数值走菜单的 ContainerData，不靠这个）。
 *       渲染用不到物品栏 / 平衡值的机器可以把 {@link #syncInventoryToClient()} / {@link #syncBalanceToClient()} 关掉（第四批框架 F4-15），
 *       关掉的那样东西既不触发更新包、也不写进更新包；别的要让客户端刷新的时候调 {@link #markClientDirty()}（同样节流）。</li>
 *   <li>存盘标记：MRU 变化只标记区块需要保存，不触发比较器更新（原版 setChanged 每次都会通知邻居）。
 *       别的机器从这里抽 MRU（绑定宝石）时立即标记，因为本机所在区块可能已加载但不 tick，等不到自己的 tick。</li>
 *   <li>物品栏：原作 ISidedInventory + SidedInvWrapper，任何面都能插能取。这里内部用 ItemStackHandler，
 *       对外（漏斗、管道）按原作规则包一层：只能插入 {@link #isItemValid} 允许的，只能取出输出槽。</li>
 * </ul>
 * 0 号槽默认是绑定宝石槽（原作 slot0IsBoundGem），有宝石就从它指向的源头拉满 MRU（自己满了时每秒才核对一次状态）。
 * <p>
 * 槽位规则（{@link #isItemValid}）要写成两端共用的静态方法，客户端菜单用 {@code MachineMenu.clientHandler} 套同一份规则，
 * 否则客户端预测和服务端结果不一致，会出现物品闪一下又弹回来。
 *
 * <h2>ESPE（可选）</h2>
 * 用带 ESPE 的构造器声明上限和档位，基类就建一个 {@link ESPEStorage}：存读档（NBT 键 {@code espe_storage}）、
 * 变化时和 MRU 一样标记存盘、按面对外暴露 {@link ECCapabilities#ESPE}（{@link #espeAccess}，默认能进能出）。
 * 不用 ESPE 的机器什么都不用改：不存这个键、不暴露 capability、菜单同步的格数也不变。
 * <pre>{@code
 * public MithrilineFurnaceBlockEntity(BlockPos pos, BlockState state) {
 *     // 原作熔炉没有 MRU：上限给 0；原作 new ESPEStorage(maxEnergy, 0)
 *     super(ECMithriline.MITHRILINE_FURNACE_BE.get(), pos, state, () -> 0, SLOTS, () -> Config.maxESPE.get(), 0);
 * }
 * protected boolean hasBoundGemSlot() { return false; }
 * protected ESPEAccess espeAccess(@Nullable Direction side) { return ESPEAccess.INSERT; } // 只进不出
 * protected void serverTick() { ESPETransfer.pullFromAround(level, worldPosition, getESPEStorage(), SOURCES); ... }
 * }</pre>
 * 界面同步用 {@code MachineData.serverWithESPE / clientWithESPE}，界面元素用 {@code MachineScreen.espeStorage(x, y)}。
 *
 * <h2>主人（policy §2.3）</h2>
 * {@code MachineBlock.setPlacedBy} 在放置者是真玩家（不是 FakePlayer 的 ServerPlayer）时记下 UUID 和名字，别的途径放下的没有主人。
 * 只存盘（NBT 键 {@code owner}、{@code owner_name}，没有主人就不写，一、二批的存档格式不变），<b>不发给客户端</b>（{@link #getUpdateTag} 里去掉）。
 * 会动世界的机器照 {@code util/ECProtection} 的主人规则用：{@code ECProtection.ownerState / onlineOwner / checkAsOwner}。原作的机器没有主人。
 */
public abstract class MRUBlockEntity extends BlockEntity implements MenuProvider, Clearable {

    /** 两次更新包之间至少隔多少 tick */
    public static final int SYNC_INTERVAL = 10;
    /** 主人 UUID 的 NBT 键（只存盘，不同步） */
    public static final String OWNER_KEY = "owner";
    /** 主人名字的 NBT 键（只存盘，不同步） */
    public static final String OWNER_NAME_KEY = "owner_name";
    /** 自己满了时，多少 tick 核对一次绑定宝石状态 */
    private static final int FULL_LINK_REFRESH = 20;
    /** 「在干活」的 NBT 键（只进更新包，不存盘） */
    private static final String AMBIENT_KEY = "ambient";

    protected final MRUStorage mru;
    protected final ItemStackHandler inventory;
    /** 没开 ESPE 的机器为 null */
    @Nullable
    private final ESPEStorage espe;

    private final LazyOptional<IMRUHandler> mruCap;
    private final LazyOptional<IItemHandler> automationCap;

    /** 客户端动画计数（原作 innerRotation），只在客户端递增 */
    public int innerRotation;

    private MRULink.Status linkStatus = MRULink.Status.NO_GEM;
    private boolean ticking;
    private boolean changedThisTick;
    private boolean inventoryChanged;
    /** {@link #markClientDirty()} 请求的一次同步，下一次 afterServerTick 发（同样受 SYNC_INTERVAL 节流） */
    private boolean clientDirty;
    private int lastSentMRU = -1;
    private int lastSentBalance = Integer.MIN_VALUE;
    private long lastSentESPE = -1;
    private int syncCooldown;
    private int linkRefresh;
    /** 服务端：{@link #ambientWorking()} 为真后还要继续算「在干活」多少刻（防止忽停忽走时不停发包） */
    private int ambientGrace;
    /** 服务端：上次告诉客户端的「在干活」；客户端：收到的值 */
    private boolean ambientOn;
    /** 客户端：声音的播放进度，第一次需要时才建 */
    @Nullable
    private MachineAmbience.State ambienceState;

    // 宝石目标的解析缓存：宝石或它的 NBT 换了才重新解析
    private ItemStack cachedGem = ItemStack.EMPTY;
    @Nullable
    private CompoundTag cachedGemTag;
    @Nullable
    private GlobalPos cachedTarget;

    /** 读档时放不下的物品（机器减少了槽位数），第一次服务端 tick 时掉在地上 */
    private final List<ItemStack> overflow = new ArrayList<>();

    /** 主人（见类注释）；没有主人为 null。客户端上永远是 null */
    @Nullable
    private UUID owner;
    private String ownerName = "";
    /** 泄漏往哪团聚集体里汇时以主人身份问过的结论（mru/ECCorruption 的领地那一关）；只存盘、不同步，换主人清空 */
    private final LeakFeedRecord leakFeed = new LeakFeedRecord();

    /**
     * 按访问方式缓存的对外 ESPE 视图（最多三种），用到才建。
     * 失效后清空、下次再建：方块实体被移走又放回（clearRemoved）时 capability 还能用，不像 final 的 LazyOptional 那样永久失效。
     */
    private final Map<ESPEAccess, LazyOptional<IESPEHandler>> espeCaps = new EnumMap<>(ESPEAccess.class);

    protected MRUBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, IntSupplier maxMRU, int slots) {
        this(type, pos, state, maxMRU, slots, null, 0);
    }

    /**
     * 带 ESPE 的机器（不用 ESPE 的机器用上面那个构造器）。
     *
     * @param maxESPE  ESPE 上限，和 MRU 上限一样每次现取（一般是服务端配置）；null 表示不用 ESPE
     * @param espeTier ESPE 档位（原作 ESPEStorage 的 tier；米斯莱熔炉 0，创造 ESPE 源 Integer.MAX_VALUE &gt;&gt; 1）
     */
    protected MRUBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, IntSupplier maxMRU, int slots,
                             @Nullable DoubleSupplier maxESPE, int espeTier) {
        super(type, pos, state);
        this.mru = new MRUStorage(maxMRU).onChanged(this::onStorageChanged);
        this.espe = maxESPE == null ? null : new ESPEStorage(maxESPE, espeTier).onChanged(this::onStorageChanged);
        this.inventory = new ItemStackHandler(slots) {
            @Override
            protected void onContentsChanged(int slot) {
                inventoryChanged = true;
                setChanged();
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return MRUBlockEntity.this.isItemValid(slot, stack);
            }
        };
        this.mruCap = LazyOptional.of(() -> mru);
        this.automationCap = LazyOptional.of(() -> new AutomationHandler(inventory));
    }

    // ------------------------------------------------------------------
    // 子类可覆盖
    // ------------------------------------------------------------------

    /** 0 号槽是否是绑定宝石槽。发电机之类不拉 MRU 的机器返回 false。 */
    protected boolean hasBoundGemSlot() {
        return inventory.getSlots() > 0;
    }

    /** 绑定宝石的距离（三个轴各自），见 MRULink */
    protected int linkRange() {
        return MRULink.DEFAULT_RANGE;
    }

    /** 是否允许绑定到别的维度（原作只有维度收发器） */
    protected boolean linkCrossDimension() {
        return false;
    }

    /**
     * 某个槽能放什么（漏斗插入、服务端菜单槽位都按这个判断）。
     * 覆盖时委托给一个静态规则方法，客户端菜单用同一个规则（见类注释）。
     */
    public boolean isItemValid(int slot, ItemStack stack) {
        return !hasBoundGemSlot() || defaultSlotRule(slot, stack);
    }

    /** 默认规则：0 号槽只放绑定宝石，其余随意 */
    public static boolean defaultSlotRule(int slot, ItemStack stack) {
        return slot != 0 || stack.getItem() instanceof BoundGemItem;
    }

    /**
     * 漏斗、管道能往哪个槽插（默认同 {@link #isItemValid}）。和玩家、菜单用的槽位规则分开，
     * 给要单独限制自动化的机器覆盖（例：魔力工作台只许往已有同种物品的原料格补货）。
     */
    protected boolean canAutomationInsert(int slot, ItemStack stack) {
        return isItemValid(slot, stack);
    }

    /** 漏斗、管道能从哪些槽取出（原作 getOutputSlots） */
    protected boolean isOutputSlot(int slot) {
        return false;
    }

    /**
     * 漏斗、管道、魔力镜经物品能力往 slot 里<b>真放进了</b> count 个之后调（模拟插入、被拒的不调）。
     * 界面的槽位直接用内部物品栏、不经过这里，所以能用它区分「自动化送来的」和「手放的」（合成器的产线计数用）。默认什么都不做。
     */
    protected void onAutomationInserted(int slot, int count) {
    }

    /** 渲染要用到 MRU 存量的机器返回 true，MRU 变化时也发更新包 */
    protected boolean syncMRUToClient() {
        return false;
    }

    /** 渲染要用到 ESPE 存量的机器返回 true，ESPE 变化（按界面的 0.01 精度）时也发更新包。只对开了 ESPE 的机器有意义 */
    protected boolean syncESPEToClient() {
        return false;
    }

    /**
     * 物品栏变化时要不要发更新包、更新包里带不带物品栏（第四批框架 F4-15）。默认 true（一~三批的机器行为不变）。
     * 渲染用不到物品栏的机器（魔力漏斗、合成器、分离器、红石传递器、自然熔炉）返回 false：物品栏只走菜单同步给开着界面的人，
     * 不再每次变化都给 32 格内所有人发整份 NBT。要把某样东西给渲染用（例如伪装）就在 {@link #getUpdateTag} 里单独加一个字段、
     * 变化时调 {@link #markClientDirty()}。返回值要稳定（同一台机器每次都一样）。
     */
    protected boolean syncInventoryToClient() {
        return true;
    }

    /**
     * 平衡值变化时要不要发更新包、更新包里带不带平衡值（第四批框架 F4-15）。默认 true（一~三批的机器行为不变）。
     * 平衡值每 tick 都在变又不画出来的机器（特斯拉反应堆：0~2 来回摆）返回 false；界面上的平衡值走菜单的 MachineData，不受影响。
     * 关掉后客户端的方块实体里平衡值恒为默认的纯净值。返回值要稳定。
     */
    protected boolean syncBalanceToClient() {
        return true;
    }

    /**
     * 某一面（null 表示不指定面）对外暴露 ESPE 的方式，只对开了 ESPE 的机器有意义。默认能进能出（原作所有 ESPE 方块都这样）。
     * 米斯莱熔炉返回 {@link ESPEAccess#INSERT}：只进不出，别的机器抽不走它的 ESPE。
     * 返回值要稳定（同一面每次都一样），capability 按它缓存；机器自己用 {@link #getESPEStorage()} 读写，不受这个限制。
     */
    protected ESPEAccess espeAccess(@Nullable Direction side) {
        return ESPEAccess.BOTH;
    }

    /** 机器本身的服务端逻辑，在拉完 MRU 之后调用 */
    protected void serverTick() {
    }

    /** 客户端逻辑（粒子、动画） */
    protected void clientTick() {
    }

    /** 持续这么多刻不干活，才告诉客户端停下声音和粒子 */
    public static final int AMBIENT_GRACE = 40;

    /**
     * 这台机器现在是不是在干活，给客户端放声音和粒子用（默认 false，要有声音的机器覆盖）。
     * 服务端每个 tick 末问一次，变化通过更新包同步（{@link #SYNC_INTERVAL} 节流）；
     * 变成「没干活」要持续 {@value #AMBIENT_GRACE} 刻才会同步，所以判断可以直接用这一刻的状态，不用自己防抖。
     */
    protected boolean ambientWorking() {
        return false;
    }

    /** 干活时放什么声音、冒什么粒子（见 {@link com.flameforge.essentialcraft5.ambience.MachineAmbiences}）；null = 没有 */
    @Nullable
    protected MachineAmbience.Profile ambience() {
        return null;
    }

    /** 客户端：服务端是否说这台机器在干活 */
    public final boolean isAmbientOn() {
        return ambientOn;
    }

    /**
     * 请求给附近的客户端发一次更新包（第四批框架 F4-15）：渲染要用的自有字段变了（例如换了伪装、换了要画的目标）时调。
     * 和物品栏、平衡值的变化一样受 {@link #SYNC_INTERVAL} 节流，在本机下一次服务端 tick 的末尾发；只在服务端有意义，客户端调用什么都不做。
     * 注意：只对有服务端 ticker 的机器生效（发包在 tick 末尾做）；{@code getTicker} 返回 null 的纯脉冲机器要自己
     * {@code level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS)}。
     */
    public void markClientDirty() {
        if (level != null && !level.isClientSide) {
            clientDirty = true;
        }
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    /** 红石信号缓存的兜底重读间隔（平时靠邻居变化作废，{@link #invalidatePower}） */
    public static final int POWER_BACKSTOP = 20;

    private boolean powerDirty = true;
    private boolean powerCached;
    private long powerReadAt = Long.MIN_VALUE;

    /**
     * 六面有没有红石信号，每刻都要问的机器用它代替 {@code level.hasNeighborSignal(worldPosition)}（那个每次要读六个邻居的状态，
     * 空转的机器一刻里它占了大头）。方块那边的 neighborChanged（{@code MachineBlock}）把缓存作废，下一次问就重读；
     * 万一有邻居变化没通知到（别的模组的奇怪方块），每 {@value #POWER_BACKSTOP} 刻也会重读一次，最慢 1 秒跟上。
     */
    protected final boolean neighborPowered() {
        Level here = level;
        if (here == null) {
            return false;
        }
        long now = here.getGameTime();
        if (powerDirty || now < powerReadAt || now - powerReadAt >= POWER_BACKSTOP) {
            powerCached = here.hasNeighborSignal(worldPosition);
            powerDirty = false;
            powerReadAt = now;
        }
        return powerCached;
    }

    /** 邻居变了（{@code MachineBlock.neighborChanged}）：下次问红石信号时重读 */
    public final void invalidatePower() {
        powerDirty = true;
    }

    public static void tickServer(Level level, BlockPos pos, BlockState state, MRUBlockEntity be) {
        be.ticking = true;
        try {
            be.dropOverflow();
            if (be.hasBoundGemSlot()) {
                be.pullFromLink();
            }
            be.serverTick();
            be.afterServerTick();
        } finally {
            be.ticking = false;
        }
    }

    public static void tickClient(Level level, BlockPos pos, BlockState state, MRUBlockEntity be) {
        be.innerRotation++;
        be.clientTick();
        if (be.ambientOn) {
            MachineAmbience.Profile profile = be.ambience();
            if (profile != null) {
                if (be.ambienceState == null) {
                    be.ambienceState = new MachineAmbience.State();
                }
                profile.tick(level, pos, be.ambienceState);
            }
        }
    }

    private void pullFromLink() {
        ItemStack gem = inventory.getStackInSlot(0);
        if (!(gem.getItem() instanceof BoundGemItem)) {
            linkStatus = MRULink.Status.NO_GEM;
            return;
        }
        // 自己满了就不用去源头那边查一圈，状态每秒核对一次就够（界面状态本来就是给人看的）
        if (mru.getMRU() >= mru.getMaxMRU() && linkRefresh-- > 0) {
            return;
        }
        linkRefresh = FULL_LINK_REFRESH;
        if (gem != cachedGem || gem.getTag() != cachedGemTag) {
            cachedGem = gem;
            cachedGemTag = gem.getTag();
            cachedTarget = BoundGemItem.getTarget(gem);
        }
        linkStatus = MRULink.pull(level, worldPosition, mru, cachedTarget, linkRange(), linkCrossDimension());
    }

    /** MRU、ESPE 存量变化时调 */
    private void onStorageChanged() {
        changedThisTick = true;
        // 自己 tick 里的变化留到 afterServerTick 一起标记；tick 外的变化（被别的机器抽走、法杖改动）立即标记，
        // 否则所在区块已加载但不 tick 时，这次变化永远不会存盘，重新加载后 MRU 又回来了
        if (!ticking && level != null && !level.isClientSide) {
            level.blockEntityChanged(worldPosition);
        }
    }

    private void afterServerTick() {
        if (changedThisTick) {
            changedThisTick = false;
            // 只标记区块需要保存，不像 setChanged 那样每 tick 通知比较器
            if (level != null) {
                level.blockEntityChanged(worldPosition);
            }
        }
        if (ambientWorking()) {
            ambientGrace = AMBIENT_GRACE;
        } else if (ambientGrace > 0) {
            ambientGrace--;
        }
        if ((ambientGrace > 0) != ambientOn) {
            ambientOn = !ambientOn;
            clientDirty = true;
        }
        if (syncCooldown > 0) {
            syncCooldown--;
            return;
        }
        // 平衡值按界面精度比较，免得浮点抖动每次都发包
        int balance = Math.round(mru.getBalance() * 10000F);
        boolean balanceChanged = syncBalanceToClient() && balance != lastSentBalance;
        boolean mruChanged = syncMRUToClient() && mru.getMRU() != lastSentMRU;
        // ESPE 是 double，按界面精度（0.01）比较，免得每 tick 的零头都发包；没开 ESPE 的机器恒为 false
        long espeFixed = espe != null ? Math.round(espe.getESPE() * 100D) : -1;
        boolean espeChanged = espe != null && syncESPEToClient() && espeFixed != lastSentESPE;
        // 不同步物品栏的机器：物品栏变化不算数（标记直接清掉，免得一直挂着）
        if (inventoryChanged && !syncInventoryToClient()) {
            inventoryChanged = false;
        }
        if (inventoryChanged || mruChanged || espeChanged || balanceChanged || clientDirty) {
            inventoryChanged = false;
            clientDirty = false;
            lastSentMRU = mru.getMRU();
            lastSentESPE = espeFixed;
            lastSentBalance = balance;
            syncCooldown = SYNC_INTERVAL;
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    // ------------------------------------------------------------------
    // 访问
    // ------------------------------------------------------------------

    public MRUStorage getMRUStorage() {
        return mru;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    /** 机器自己的 ESPE 存储（不受按面规则限制）；没开 ESPE 的机器返回 null */
    @Nullable
    public ESPEStorage getESPEStorage() {
        return espe;
    }

    public boolean hasESPE() {
        return espe != null;
    }

    /** 绑定宝石的当前状态（服务端算，随菜单同步给界面） */
    public MRULink.Status getLinkStatus() {
        return linkStatus;
    }

    /** 主人的 UUID；没有主人（或在客户端上）返回 null。机器逻辑用 {@code ECProtection.owner / onlineOwner} */
    @Nullable
    public UUID getOwner() {
        return owner;
    }

    /** 主人的名字（放下时记的，主人在线时 ECProtection.onlineOwner 会刷新）；没有主人返回空串 */
    public String getOwnerName() {
        return ownerName;
    }

    /** 设置 / 清除主人（{@code ECProtection.recordOwner} 调）；id 为 null 表示没有主人 */
    public void setOwner(@Nullable UUID id, String name) {
        String newName = id == null || name == null ? "" : name;
        // 没变就不标记（假玩家 / 模拟玩家放下时是「清空一个本来就没有的主人」），免得白白通知比较器
        if (Objects.equals(owner, id) && ownerName.equals(newName)) {
            return;
        }
        if (!Objects.equals(owner, id)) {
            leakFeed.clear();   // 以前那个人问出来的结论不算新主人的
        }
        owner = id;
        ownerName = newName;
        setChanged();
    }

    /** 泄漏往哪团聚集体里汇的记录（{@code mru/ECCorruption} 用；只在服务端有内容） */
    public LeakFeedRecord leakFeedRecord() {
        return leakFeed;
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    /**
     * 直接改了槽里物品自身（比如给物品充能改了 NBT）之后调用：ItemStackHandler 感知不到这种修改。
     * 只标记存盘和客户端同步，不像 setChanged 那样每 tick 通知比较器。
     */
    protected void inventoryContentsChanged(int slot) {
        changedThisTick = true;
        inventoryChanged = true;
    }

    /**
     * 方块被破坏时把物品栏掉出来（原作 breakBlock 里的 InventoryHelper.dropInventoryItems）。
     * 直接交出槽里的那一堆（掉落时会被拆空），不能掉副本：否则还开着这台机器界面的玩家能从旧方块实体里再取一份。
     */
    public void dropContents() {
        if (level == null) {
            return;
        }
        double x = worldPosition.getX();
        double y = worldPosition.getY();
        double z = worldPosition.getZ();
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, x, y, z, stack);
            }
        }
        for (ItemStack stack : overflow) {
            Containers.dropItemStack(level, x, y, z, stack);
        }
        overflow.clear();
    }

    private void dropOverflow() {
        if (!overflow.isEmpty() && level != null) {
            EssentialCraft5.LOGGER.warn("{} 在 {} 的槽位数变少了，放不下的 {} 件物品掉在原地",
                    getType(), worldPosition, overflow.size());
            for (ItemStack stack : overflow) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), stack);
            }
            overflow.clear();
            setChanged();
        }
    }

    // ------------------------------------------------------------------
    // 存盘与同步
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("mru_storage", mru.serializeNBT());
        // 只有开了 ESPE 的机器才写这个键，一批机器的存档格式不变
        if (espe != null) {
            tag.put("espe_storage", espe.serializeNBT());
        }
        if (inventory.getSlots() > 0) {
            tag.put("inventory", inventory.serializeNBT());
        }
        if (!overflow.isEmpty()) {
            ListTag list = new ListTag();
            overflow.forEach(stack -> list.add(stack.save(new CompoundTag())));
            tag.put("overflow", list);
        }
        // 没有主人就不写，一、二批的存档格式不变
        if (owner != null) {
            tag.putUUID(OWNER_KEY, owner);
            tag.putString(OWNER_NAME_KEY, ownerName);
        }
        leakFeed.save(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        mru.deserializeNBT(tag.getCompound("mru_storage"));
        // 和 MRU 一样：没有这个键（旧存档里这台机器还没有 ESPE）按 0 读；存档里有、代码里已经不用 ESPE 了就忽略。
        // 存量高于现在的上限（配置调低了）时原样读入，读数和抽取都按新上限夹住
        if (espe != null) {
            espe.deserializeNBT(tag.getCompound("espe_storage"));
        }
        // 先读上次没来得及掉落的，再读物品栏（它会把放不下的追加进来）
        overflow.clear();
        tag.getList("overflow", Tag.TAG_COMPOUND).forEach(entry -> overflow.add(ItemStack.of((CompoundTag) entry)));
        if (inventory.getSlots() > 0 && tag.contains("inventory")) {
            loadInventory(tag.getCompound("inventory"));
        }
        // 旧存档（二批以前放下的机器）没有主人；客户端收到的更新包里也没有（getUpdateTag 去掉了），读成 null
        owner = tag.hasUUID(OWNER_KEY) ? tag.getUUID(OWNER_KEY) : null;
        ownerName = owner != null ? tag.getString(OWNER_NAME_KEY) : "";
        leakFeed.load(tag);
        // 只随更新包来（getUpdateTag 里写），存档里没有，读档读成 false；服务端读档也不会改它，tick 里会重新判
        ambientOn = tag.getBoolean(AMBIENT_KEY);
    }

    /**
     * 按代码里的槽位数读物品栏。存档的槽位数可能不同（以后改了机器的槽位数）：
     * ItemStackHandler.deserializeNBT 会先按存档的 Size 重建列表，事后再 setSize 会把物品全部清空，
     * 所以先把 Size 改成代码的槽位数再读；超出范围的物品收进 overflow，第一次服务端 tick 时掉出来。
     */
    private void loadInventory(CompoundTag saved) {
        int size = inventory.getSlots();
        CompoundTag copy = saved.copy();
        copy.putInt("Size", size);
        inventory.deserializeNBT(copy);
        ListTag items = saved.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag item = items.getCompound(i);
            if (item.getInt("Slot") >= size) {
                ItemStack stack = ItemStack.of(item);
                if (!stack.isEmpty()) {
                    overflow.add(stack);
                }
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        // 注意：物品栏里绑定宝石的坐标也会发给附近的客户端。以后跨维度/无限距离的机器（维度收发器等）要覆盖这里，只发渲染要用的字段
        CompoundTag tag = saveWithoutMetadata();
        if (ambientOn) {
            tag.putBoolean(AMBIENT_KEY, true);
        }
        // 主人只存盘、不发给客户端（policy §2.3 第 1 条）；子类覆盖时先调 super.getUpdateTag() 再往上加，自然也不带
        tag.remove(OWNER_KEY);
        tag.remove(OWNER_NAME_KEY);
        tag.remove(LeakFeedRecord.TAG);
        // F4-15：关了物品栏同步的机器不带物品栏（客户端 load 时没有这个键就不动物品栏）；读档溢出的物品只在服务端有用
        if (!syncInventoryToClient()) {
            tag.remove("inventory");
            tag.remove("overflow");
        }
        // 关了平衡值同步的机器不带平衡值（客户端读成默认的纯净值）；MRU 存量照旧带着，界面数值本来就走菜单
        if (!syncBalanceToClient() && tag.contains("mru_storage", Tag.TAG_COMPOUND)) {
            // "balance" = MRUStorage.TAG_BALANCE（那边是私有常量，MRUStorage 不归这里改）
            tag.getCompound("mru_storage").remove("balance");
        }
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------------------
    // capability
    // ------------------------------------------------------------------

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ECCapabilities.MRU) {
            return mruCap.cast();
        }
        if (cap == ForgeCapabilities.ITEM_HANDLER && inventory.getSlots() > 0) {
            return automationCap.cast();
        }
        // 已移除的方块实体不再新建视图（invalidateCaps 清空了缓存，这里不挡的话还拿着旧方块实体的人能再取到一份能用的）
        if (cap == ECCapabilities.ESPE && espe != null && !isRemoved()) {
            ESPEAccess access = espeAccess(side);
            if (access != null && access != ESPEAccess.NONE) {
                ESPEStorage storage = espe;
                return espeCaps.computeIfAbsent(access, a -> LazyOptional.of(() -> new SidedESPEHandler(storage, a))).cast();
            }
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        mruCap.invalidate();
        automationCap.invalidate();
        espeCaps.values().forEach(LazyOptional::invalidate);
        espeCaps.clear();
    }

    /** 对外的物品栏：只能插入允许的，只能从输出槽取出。 */
    private final class AutomationHandler implements IItemHandler {
        private final ItemStackHandler inner;

        AutomationHandler(ItemStackHandler inner) {
            this.inner = inner;
        }

        @Override
        public int getSlots() {
            return inner.getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return inner.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (!canAutomationInsert(slot, stack)) {
                return stack;
            }
            int offered = stack.getCount();
            ItemStack rest = inner.insertItem(slot, stack, simulate);
            if (!simulate && rest.getCount() < offered) {
                onAutomationInserted(slot, offered - rest.getCount());
            }
            return rest;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return isOutputSlot(slot) ? inner.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inner.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return canAutomationInsert(slot, stack);
        }
    }

    /**
     * 原版在 {@code /clone … move}、{@code /setblock}、{@code /fill}、结构方块覆盖这一格之前调（{@link Clearable#tryClear}）：
     * 先清空物品栏，免得内容被复制一份（move 时原地再撒一份）或撒一地，同原版箱子（第四批审查 STO-2 的同类问题）。
     * MRU、ESPE 不是物品，不清。
     */
    @Override
    public void clearContent() {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }
}
