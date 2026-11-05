package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.util.ECLevels;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 魔力镜（第四批 4b ECMirror，batch4 §3.8）：放在一个容器正上方，每 {@code transfer_ticks}（默认 60）刻从下面的容器取 1 件东西，
 * 送进用控制棒链接的、{@code max_distance}（默认 8）格内的另一个容器。不耗 MRU（原作 {@code TileMRUGeneric(0)}，这里 MRU 上限 0、0 格物品栏）。
 * 对应原作 TileMagicalMirror（149 行）。
 *
 * <h2>和原作的区别</h2>
 * <ul>
 *   <li><b>不吞物品</b>（原作 L51、L74~75：先取再放、插入的返回值没接，找货用不比 NBT 的 isItemEqual，目标格 NBT 不同或拒收时那 1 件就没了）：
 *       开始前先模拟「取 1 件」和「整个目标物品栏能不能放下」；结束时再模拟一次，还行才真取真放；万一真放时放不下（同一刻被别的东西塞满），
 *       先塞回源容器原来那格、再塞源容器任意格，还不行就掉在镜子上方——绝不销毁。</li>
 *   <li><b>不崩服</b>（原作 L43~49：目标先用 {@code hasCapability(null)} 判断、再用 UP 面取，UP 面没有物品栏的方块实体在 tick 里空指针）：两边都按 UP 面取，取不到就不送。</li>
 *   <li><b>状态机代替每 tick 扫描</b>（原作每 tick「源每一格 × 目标每一格」比较，传送中每 tick 发整包更新）：空闲时每 {@code idle_check_ticks}（默认 20）刻看一次，
 *       从游标起最多看源的 {@code scan_slots}（默认 64）格、最多对 {@code insert_checks}（默认 8）种不同的东西模拟插入（同一种放不下的不再试），
 *       找到能送的就开始；传送中不看、不发包，只在开始和结束各请求一次同步，客户端按游戏时间自己插值画动画。
 *       前一件送完的同一刻就接着找下一件，节奏和原作一样是每 60 刻 1 件。</li>
 *   <li><b>链接只在控制棒那一下查权限</b>（policy §4.3 规划补 ②，同绑定宝石）：镜子格、镜子正下方的源容器格、目标格三格都要链接的人能动
 *       （{@code ControlRodItem}），之后不再查，也不需要主人在线（镜子不改方块、不碰实体）。</li>
 *   <li><b>链接记下当时镜子自己的位置</b>（{@link #LINK_ORIGIN_KEY}，原作没有）：镜子带着存档数据挪了地方（搬运类模组把方块连数据搬走、
 *       创造模式带数据复制、{@code /clone}），位置对不上就断开链接。否则挪到别人箱子上方的镜子会接着按旧链接从别人箱子里抽东西。</li>
 *   <li>运行时也按 {@code max_distance} 核对距离（配置调小以后，超出的链接不送）；目标方块实体没了（区块已加载）清掉链接（原作 M-11 永远留着）；
 *       目标区块没加载就等，不加载区块（{@link ECLevels#loadedBlockEntity}）；源在镜子正下方，和镜子同一个区块。</li>
 *   <li>逻辑只在服务端（原作取出、插入客户端也跑）；渲染包围盒只包住镜子到目标（原作全局渲染 + 无限渲染范围）。</li>
 * </ul>
 * 更新包只带渲染要用的：目标、是否在送（总是写，更新包不会是空的，见 {@link #getUpdateTag()}）、开始时间、这次的耗时、
 * 送的那件东西（NBT 超过 {@link #MAX_SYNC_SAMPLE_BYTES} 只发 id 和数量）。
 * 空手右键的状态提示（{@link #status()}）除了链接本身的问题，还会说没在送的原因：下面的容器没有能取的东西、目标放不下（新加，原作右键什么都不显示）。
 * 主人照框架记下（放下时），但镜子不用它。
 */
public class MagicalMirrorBlockEntity extends MRUBlockEntity {

    /** 送的那件东西发给客户端时，NBT 超过这么多字节就只发 id 和数量（渲染用不到大 NBT） */
    public static final int MAX_SYNC_SAMPLE_BYTES = 1024;

    public static final String TARGET_KEY = "target";
    /** 链接时镜子自己的位置（见类注释） */
    public static final String LINK_ORIGIN_KEY = "link_origin";
    public static final String TRANSFERRING_KEY = "transferring";
    public static final String SOURCE_SLOT_KEY = "source_slot";
    public static final String SAMPLE_KEY = "sample";
    public static final String START_KEY = "start_tick";
    public static final String DURATION_KEY = "duration";
    public static final String CURSOR_KEY = "scan_cursor";

    /** 原作 tileentities.magicalmirror（原作只有 Radius 一项，其余是本批新加的上限） */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxDistance;
        public static ForgeConfigSpec.IntValue transferTicks;
        public static ForgeConfigSpec.IntValue idleCheckTicks;
        public static ForgeConfigSpec.IntValue scanSlots;
        public static ForgeConfigSpec.IntValue insertChecks;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("magical_mirror");
            maxDistance = builder.comment("How far (straight-line blocks) the linked container may be from the mirror.",
                            "链接的目标容器离魔力镜最远多少格（直线距离）")
                    .defineInRange("max_distance", 8, 1, 16);
            transferTicks = builder.comment("Ticks one item takes to travel from the mirror to the target.",
                            "送一件东西要多少刻")
                    .defineInRange("transfer_ticks", 60, 20, 1200);
            idleCheckTicks = builder.comment("While idle, ticks between two looks for something to send.",
                            "空闲时隔多少刻看一次有没有能送的东西")
                    .defineInRange("idle_check_ticks", 20, 5, 200);
            scanSlots = builder.comment("At most this many slots of the container below are looked at per look (a cursor continues next time).",
                            "每次最多看下面容器的几格（下次从停下的地方接着看）")
                    .defineInRange("scan_slots", 64, 1, 1024);
            insertChecks = builder.comment("At most this many different items are test-inserted into the target per look.",
                            "Each test goes over every slot of the target, so this keeps huge target inventories cheap.",
                            "每次最多拿几种不同的东西去试目标放不放得下（每试一次要把目标每一格看一遍，目标物品栏很大时靠它压住开销）")
                    .defineInRange("insert_checks", 8, 1, 64);
            builder.pop();
        }

        public static int maxDistance() {
            return maxDistance == null ? 8 : ECConfig.get(maxDistance);
        }

        public static int transferTicks() {
            return transferTicks == null ? 60 : ECConfig.get(transferTicks);
        }

        public static int idleCheckTicks() {
            return idleCheckTicks == null ? 20 : ECConfig.get(idleCheckTicks);
        }

        public static int scanSlots() {
            return scanSlots == null ? 64 : ECConfig.get(scanSlots);
        }

        public static int insertChecks() {
            return insertChecks == null ? 8 : ECConfig.get(insertChecks);
        }
    }

    /** 空手右键镜子时显示的状态（服务端算） */
    public enum Status {
        /** 还没有链接 */
        UNLINKED,
        /** 正下方没有能取东西的容器 */
        NO_SOURCE,
        /** 目标超出了现在的最远距离（配置调小了） */
        TOO_FAR,
        /** 目标所在的区块没加载，等它加载 */
        TARGET_UNLOADED,
        /** 目标方块还在，但顶面没有物品栏 */
        TARGET_NOT_CONTAINER,
        /**
         * 链接好了、没在送：上次找货时下面的容器里没有能取出来的东西（从顶面取；比如熔炉顶面只露原料格，成品格里有东西也算空）。
         * 源容器比 {@code scan_slots} 大时，连着看过的空格够一整圈才算。
         */
        SOURCE_EMPTY,
        /** 链接好了、没在送：下面的容器里有东西，但上次找货时目标一件都放不下（满了，或者按它顶面的规矩不收这些东西） */
        TARGET_FULL,
        /** 正常：链接好了，在送（或者刚链接 / 刚送完、还没找过货） */
        LINKED
    }

    @Nullable
    private BlockPos target;
    @Nullable
    private BlockPos linkOrigin;
    private boolean transferring;
    private int sourceSlot;
    private ItemStack sample = ItemStack.EMPTY;
    private long startTick;
    private int duration;
    private int scanCursor;
    /** 下一次空闲检查的游戏时间（不存盘：读档后第一刻就看一次） */
    private long nextCheck;
    /**
     * 上次找货没开始送的原因（{@link Status#SOURCE_EMPTY} / {@link Status#TARGET_FULL}），null = 还没找过、在送、刚送完。
     * 只给空手右键的状态提示用，不存盘（读档后第一刻就会重新找一次）；是上一次找货的结论，最多晚 {@code idle_check_ticks} 刻。
     */
    @Nullable
    private Status idleReason;
    /** 从上次看到东西起，源容器连着看过多少个空格（跨次累计，看到东西就清零）；够一整圈才报 {@link Status#SOURCE_EMPTY}（不存盘） */
    private int emptyRun;
    /** 请求同步的次数（GameTest 用来断言「传送中不发包」） */
    private int syncRequests;

    public MagicalMirrorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, () -> 0, 0);
    }

    // ------------------------------------------------------------------
    // 框架钩子
    // ------------------------------------------------------------------

    @Override
    protected boolean hasBoundGemSlot() {
        return false;
    }

    /** 没有物品栏 */
    @Override
    protected boolean syncInventoryToClient() {
        return false;
    }

    /** 平衡值不画出来 */
    @Override
    protected boolean syncBalanceToClient() {
        return false;
    }

    /** 没有界面（原作右键「开界面」但没注册界面，M-12） */
    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return null;
    }

    // ------------------------------------------------------------------
    // 读取（两端）
    // ------------------------------------------------------------------

    @Nullable
    public BlockPos getTarget() {
        return target;
    }

    public boolean isTransferring() {
        return transferring;
    }

    /** 正在送的那件东西（1 个的副本；真东西送完之前一直在源容器里） */
    public ItemStack getSample() {
        return sample;
    }

    /** 这次传送开始时的游戏时间 */
    public long getStartTick() {
        return startTick;
    }

    /** 这次传送要多少刻（开始时按配置记下，客户端画动画用） */
    public int getDuration() {
        return duration;
    }

    public int syncRequests() {
        return syncRequests;
    }

    // ------------------------------------------------------------------
    // 链接（控制棒调；权限、距离、维度由控制棒先查好）
    // ------------------------------------------------------------------

    /** 链接到 target（服务端）：记下镜子自己现在的位置，正在送的那件作废（东西还在源容器里），下一刻就开始找 */
    public void link(BlockPos target) {
        this.target = target.immutable();
        this.linkOrigin = worldPosition.immutable();
        resetTransfer();
        nextCheck = 0L;
        scanCursor = 0;
        clearIdleReason();
        changed();
        requestSync();
    }

    /** 断开链接（服务端） */
    public void unlink() {
        if (target == null && !transferring) {
            return;
        }
        target = null;
        linkOrigin = null;
        resetTransfer();
        clearIdleReason();
        changed();
        requestSync();
    }

    // ------------------------------------------------------------------
    // tick
    // ------------------------------------------------------------------

    @Override
    protected void serverTick() {
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        if (transferring) {
            if (now < startTick) {
                // 游戏时间倒回去了（拷来的存档之类）：从现在重新计时
                startTick = now;
            }
            if (now - startTick < duration) {
                return;
            }
            finishTransfer();
            // 送完的同一刻接着找下一件：节奏和原作一样每 transfer_ticks 刻 1 件
            nextCheck = 0L;
        }
        if (now < nextCheck) {
            return;
        }
        nextCheck = now + Config.idleCheckTicks();
        tryStartTransfer();
    }

    /**
     * 找一件能送的东西，找到就开始送（服务端；GameTest 直接调它跳过等待）。
     * 从游标起最多看源容器 {@code scan_slots} 格，每格模拟取 1 件，对最多 {@code insert_checks} 种不同的东西模拟整个目标物品栏的插入
     * （{@link ItemHandlerHelper#insertItemStacked}：先叠到同种物品上、再找空格，NBT 不同不叠），放得下才开始。
     * <p>
     * 没开始时顺手记下原因给状态提示（{@link #status()}）：看到了东西但目标都不收 → {@link Status#TARGET_FULL}；
     * 看的格子全是空的 → 累计空格数，够源容器一整圈 → {@link Status#SOURCE_EMPTY}（源容器比 {@code scan_slots} 大时，看了一段空格不算空）。
     * 目标、源容器本身有问题（没链接、太远、没加载、不是容器、下面没容器）由 status() 当场查，不记在这里。
     *
     * @return 是否开始了一次传送
     */
    public boolean tryStartTransfer() {
        if (!(level instanceof ServerLevel) || transferring) {
            return false;
        }
        IItemHandler destination = targetHandler();
        if (destination == null) {
            return false;
        }
        IItemHandler source = sourceHandler();
        if (source == null) {
            return false;
        }
        int size = source.getSlots();
        if (size <= 0) {
            idleReason = Status.SOURCE_EMPTY;
            return false;
        }
        int limit = Math.min(size, Config.scanSlots());
        int tries = Config.insertChecks();
        int start = Math.floorMod(scanCursor, size);
        List<ItemStack> refused = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            int slot = (start + i) % size;
            ItemStack one = source.extractItem(slot, 1, true);
            if (one.isEmpty() || contains(refused, one)) {
                continue;
            }
            if (tries <= 0) {
                // 这次试够了，下次从这一格接着看（不这样的话，前面几格堆着放不下的东西时，后面能送的永远轮不到）
                scanCursor = slot;
                // 试过的那几种目标都不收（剩下的下次接着试，送得出去就会开始）
                idleReason = Status.TARGET_FULL;
                emptyRun = 0;
                return false;
            }
            tries--;
            if (ItemHandlerHelper.insertItemStacked(destination, one, true).isEmpty()) {
                begin(slot, one);
                // 下次还从这一格看：同一格的东西一件件送完再往后（原作总是送第一个能送的格子）
                scanCursor = slot;
                return true;
            }
            refused.add(one);
        }
        scanCursor = (start + limit) % size;
        if (!refused.isEmpty()) {
            idleReason = Status.TARGET_FULL;
            emptyRun = 0;
        } else {
            emptyRun = Math.min(size, emptyRun + limit);
            if (emptyRun >= size) {
                idleReason = Status.SOURCE_EMPTY;
            }
        }
        return false;
    }

    /**
     * 送完这一件（服务端；到时间时 serverTick 调，GameTest 直接调它跳过等待）：再模拟一次，源格还是同一种东西、目标还放得下，才真取真放。
     *
     * @return 是否送过去了 1 件
     */
    public boolean finishTransfer() {
        if (!(level instanceof ServerLevel) || !transferring) {
            return false;
        }
        boolean moved = false;
        IItemHandler destination = targetHandler();
        IItemHandler source = sourceHandler();
        if (transferring && destination != null && source != null && sourceSlot >= 0 && sourceSlot < source.getSlots()) {
            ItemStack simulated = source.extractItem(sourceSlot, 1, true);
            if (!simulated.isEmpty() && ItemStack.isSameItemSameTags(simulated, sample)
                    && ItemHandlerHelper.insertItemStacked(destination, simulated, true).isEmpty()) {
                ItemStack real = source.extractItem(sourceSlot, 1, false);
                if (!real.isEmpty()) {
                    ItemStack rest = ItemHandlerHelper.insertItemStacked(destination, real, false);
                    if (rest.isEmpty()) {
                        moved = true;
                    } else {
                        giveBack(source, rest);
                    }
                }
            }
        }
        resetTransfer();
        changed();
        requestSync();
        return moved;
    }

    /** 真放时没放下的：先回源容器原来那格，再回源容器任意格，还不行掉在镜子上方。绝不销毁 */
    private void giveBack(IItemHandler source, ItemStack rest) {
        if (sourceSlot >= 0 && sourceSlot < source.getSlots()) {
            rest = source.insertItem(sourceSlot, rest, false);
        }
        if (!rest.isEmpty()) {
            rest = ItemHandlerHelper.insertItemStacked(source, rest, false);
        }
        if (!rest.isEmpty() && level != null) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5D, worldPosition.getY() + 1.1D, worldPosition.getZ() + 0.5D, rest);
        }
    }

    private void begin(int slot, ItemStack one) {
        transferring = true;
        sourceSlot = slot;
        sample = one.copyWithCount(1);
        startTick = level == null ? 0L : level.getGameTime();
        duration = Config.transferTicks();
        clearIdleReason();
        changed();
        requestSync();
    }

    private void resetTransfer() {
        transferring = false;
        sourceSlot = 0;
        sample = ItemStack.EMPTY;
        startTick = 0L;
        duration = 0;
    }

    /** 忘掉上次找货没开始的原因（开始送、重新链接、断开时）：状态提示回到「链接到……」，下次找货再记 */
    private void clearIdleReason() {
        idleReason = null;
        emptyRun = 0;
    }

    private static boolean contains(List<ItemStack> list, ItemStack stack) {
        for (ItemStack other : list) {
            if (ItemStack.isSameItemSameTags(other, stack)) {
                return true;
            }
        }
        return false;
    }

    /** 正下方方块实体顶面的物品栏（和镜子同一个区块，镜子在 tick 说明区块已加载） */
    @Nullable
    private IItemHandler sourceHandler() {
        if (level == null) {
            return null;
        }
        BlockPos below = worldPosition.below();
        if (level.isOutsideBuildHeight(below)) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(below);
        if (be == null || be.isRemoved()) {
            return null;
        }
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
    }

    /**
     * 目标顶面的物品栏；送不了时返回 null。顺手维护链接：镜子挪过地方、目标方块实体没了（区块已加载）就断开；
     * 目标超出现在的最远距离、区块没加载、顶面没有物品栏时只是这次不送。
     */
    @Nullable
    private IItemHandler targetHandler() {
        if (level == null || target == null) {
            return null;
        }
        if (!worldPosition.equals(linkOrigin)) {
            unlink();
            return null;
        }
        if (!withinRange(worldPosition, target)) {
            return null;
        }
        BlockEntity be = ECLevels.loadedBlockEntity(level, target);
        if (be == null || be.isRemoved()) {
            if (ECLevels.isFullyLoaded(level, target)) {
                unlink();
            }
            return null;
        }
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
    }

    /** 两格之间的直线距离是否在 {@code max_distance} 以内（原作按两个方块坐标算，L26；控制棒链接时也用它） */
    public static boolean withinRange(BlockPos mirror, BlockPos target) {
        int max = Config.maxDistance();
        return mirror.distSqr(target) <= (double) max * max;
    }

    /**
     * 服务端：空手右键镜子时的状态。链接、源容器、距离、目标这几样当场查；都没问题又没在送时，
     * 报上次找货没开始的原因（{@link Status#SOURCE_EMPTY} / {@link Status#TARGET_FULL}，见 {@link #tryStartTransfer()}）。
     */
    public Status status() {
        if (level == null || target == null || !worldPosition.equals(linkOrigin)) {
            return Status.UNLINKED;
        }
        if (sourceHandler() == null) {
            return Status.NO_SOURCE;
        }
        if (!withinRange(worldPosition, target)) {
            return Status.TOO_FAR;
        }
        BlockEntity be = ECLevels.loadedBlockEntity(level, target);
        if (be == null) {
            return ECLevels.isFullyLoaded(level, target) ? Status.UNLINKED : Status.TARGET_UNLOADED;
        }
        if (!be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).isPresent()) {
            return Status.TARGET_NOT_CONTAINER;
        }
        if (!transferring && idleReason != null) {
            return idleReason;
        }
        return Status.LINKED;
    }

    /** 服务端：空手右键镜子时动作栏里的文字（键在 tools/lang/keys/ecmirror.json） */
    public Component statusMessage() {
        Status status = status();
        if (status == Status.UNLINKED) {
            return Component.translatable("message.essentialcraft5.magical_mirror.unlinked").withStyle(ChatFormatting.GOLD);
        }
        BlockPos pos = target;
        Object x = pos.getX();
        Object y = pos.getY();
        Object z = pos.getZ();
        return switch (status) {
            case NO_SOURCE -> Component.translatable("message.essentialcraft5.magical_mirror.no_source", x, y, z).withStyle(ChatFormatting.GOLD);
            case TOO_FAR -> Component.translatable("message.essentialcraft5.magical_mirror.too_far", x, y, z, Config.maxDistance())
                    .withStyle(ChatFormatting.RED);
            case TARGET_UNLOADED -> Component.translatable("message.essentialcraft5.magical_mirror.target_unloaded", x, y, z)
                    .withStyle(ChatFormatting.GOLD);
            case TARGET_NOT_CONTAINER -> Component.translatable("message.essentialcraft5.magical_mirror.target_not_container", x, y, z)
                    .withStyle(ChatFormatting.RED);
            case SOURCE_EMPTY -> Component.translatable("message.essentialcraft5.magical_mirror.source_empty", x, y, z)
                    .withStyle(ChatFormatting.GOLD);
            case TARGET_FULL -> Component.translatable("message.essentialcraft5.magical_mirror.target_full", x, y, z)
                    .withStyle(ChatFormatting.GOLD);
            default -> Component.translatable("message.essentialcraft5.magical_mirror.linked", x, y, z);
        };
    }

    private void changed() {
        if (level != null && !level.isClientSide) {
            level.blockEntityChanged(worldPosition);
        }
    }

    /** 请求给附近的客户端发一次更新包（框架节流，本机下一次 tick 末尾发） */
    private void requestSync() {
        syncRequests++;
        markClientDirty();
    }

    // ------------------------------------------------------------------
    // 客户端：粒子（渲染在 client/render/MagicalMirrorRenderer）
    // ------------------------------------------------------------------

    /**
     * 传送中每 5 刻一颗附魔台那种符文粒子：前三分之一从下面的容器飘进镜子，之后从镜子飘向目标（原作每 5 刻一颗物品粒子 ECUtils.spawnItemFX，
     * 那是原作自己的粒子，EC5 还没有，用原版的附魔符文代替）。
     */
    @Override
    protected void clientTick() {
        if (level == null || !transferring || target == null || level.getGameTime() % 5 != 0) {
            return;
        }
        long elapsed = level.getGameTime() - startTick;
        Vec3 mirror = Vec3.atCenterOf(worldPosition);
        Vec3 from;
        Vec3 to;
        if (elapsed < Math.max(1, duration) / 3) {
            from = mirror.subtract(0D, 1D, 0D);
            to = mirror;
        } else {
            from = mirror;
            to = Vec3.atCenterOf(target);
        }
        RandomSource random = level.random;
        // 附魔符文粒子从「出生点 + 速度」飞回出生点下方 1.2 格：出生点抬高 1.2，终点正好落在 to
        double x = to.x + (random.nextDouble() - 0.5D) / 3D;
        double y = to.y + 1.2D + (random.nextDouble() - 0.5D) / 3D;
        double z = to.z + (random.nextDouble() - 0.5D) / 3D;
        level.addParticle(ParticleTypes.ENCHANT, x, y, z, from.x - x, from.y - y, from.z - z);
    }

    /** 包住镜子（镜框会浮到方块下面一点）和目标（原作全局渲染 + 无限渲染范围，M-7） */
    @Override
    public AABB getRenderBoundingBox() {
        AABB box = new AABB(worldPosition).inflate(1D);
        if (target != null) {
            box = box.minmax(new AABB(target).inflate(1D));
        }
        return box;
    }

    // ------------------------------------------------------------------
    // 存盘与同步
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (target != null) {
            tag.put(TARGET_KEY, NbtUtils.writeBlockPos(target));
        }
        if (linkOrigin != null) {
            tag.put(LINK_ORIGIN_KEY, NbtUtils.writeBlockPos(linkOrigin));
        }
        if (transferring) {
            tag.putBoolean(TRANSFERRING_KEY, true);
            tag.putInt(SOURCE_SLOT_KEY, sourceSlot);
            tag.put(SAMPLE_KEY, sample.save(new CompoundTag()));
            tag.putLong(START_KEY, startTick);
            tag.putInt(DURATION_KEY, duration);
        }
        tag.putInt(CURSOR_KEY, scanCursor);
    }

    /** 存档和更新包共用这一套键（更新包里没有的键读成默认值：没有 transferring 就是不在送） */
    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        target = tag.contains(TARGET_KEY, Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(tag.getCompound(TARGET_KEY)) : null;
        linkOrigin = tag.contains(LINK_ORIGIN_KEY, Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(tag.getCompound(LINK_ORIGIN_KEY)) : null;
        transferring = tag.getBoolean(TRANSFERRING_KEY);
        if (transferring) {
            sourceSlot = Math.max(0, tag.getInt(SOURCE_SLOT_KEY));
            sample = ItemStack.of(tag.getCompound(SAMPLE_KEY));
            startTick = tag.getLong(START_KEY);
            duration = Mth.clamp(tag.getInt(DURATION_KEY), 1, 1200);
            if (sample.isEmpty()) {
                resetTransfer();
            }
        } else {
            resetTransfer();
        }
        scanCursor = Math.max(0, tag.getInt(CURSOR_KEY));
    }

    /**
     * 只带渲染要用的（不带 MRU、主人、游标、链接原点）。
     * <p>
     * {@code transferring} 键不管真假都写，保证标签永远不空：原版 ClientboundBlockEntityDataPacket 把空标签当 null 发，
     * Forge 默认的 onDataPacket 见 null 就不调 load。以前「断开链接又没在送」时发的是空标签，客户端一直留着旧目标和「在送」
     * （幽灵物品停在原目标格、粒子不停、镜框朝着没了的目标），直到区块重新发下来。
     */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        if (target != null) {
            tag.put(TARGET_KEY, NbtUtils.writeBlockPos(target));
        }
        tag.putBoolean(TRANSFERRING_KEY, transferring);
        if (transferring) {
            tag.putLong(START_KEY, startTick);
            tag.putInt(DURATION_KEY, duration);
            tag.put(SAMPLE_KEY, syncedSample(sample));
        }
        return tag;
    }

    /** 发给客户端的样本：NBT 太大时只留 id 和数量 */
    public static CompoundTag syncedSample(ItemStack stack) {
        CompoundTag full = stack.save(new CompoundTag());
        if (!stack.hasTag() || full.sizeInBytes() <= MAX_SYNC_SAMPLE_BYTES) {
            return full;
        }
        return new ItemStack(stack.getItem(), stack.getCount()).save(new CompoundTag());
    }
}
