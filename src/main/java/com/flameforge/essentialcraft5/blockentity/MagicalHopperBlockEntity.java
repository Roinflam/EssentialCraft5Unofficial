package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.api.ItemFilters;
import com.flameforge.essentialcraft5.block.FacingMachineBlock;
import com.flameforge.essentialcraft5.block.RedstonePulse;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.menu.MagicalHopperMenu;
import com.flameforge.essentialcraft5.registry.ECLogistics;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import com.flameforge.essentialcraft5.util.ECLevels;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 魔力漏斗（原作 {@code TileMagicalHopper}，97 行）：每 {@code interval} 刻（默认 20）把周围 {@code radius} 格（默认 3，即 7×7×7）里的掉落物
 * 搬到正前方那一格；周围有红石信号时停工（电平开关，不是上升沿，原作 L46）；0 号格放物品过滤器就只搬它放行的（原作 L65~75）。不耗 MRU（原作容量 0）。
 *
 * <h2>和原作不同（batch4 §3.6、policy §4.3）</h2>
 * <ul>
 *   <li><b>只搬主人能动的格子里的掉落物</b>（原作隔墙吸 7³ 内一切，连别人领地里的、别人的死亡掉落都搬）：每个有候选掉落物的格子
 *       {@code ECProtection.checkAsOwner}，放行才搬；只探测真有东西要搬的格子，不对 343 格挨个探测。</li>
 *   <li><b>前方那一格也要主人能动</b>（原作能放在自家边界、脸朝邻居，每秒往邻居家扔东西），不放行整次不搬。</li>
 *   <li><b>前方是容器就直接装进去</b>（物品 capability，面 = 朝向的反面）；前方空着（碰撞箱为空：空气、花草、水）就瞬移过去并清掉速度；
 *       前方实心又不是容器就不搬（原作照样瞬移，东西被塞进方块、四面封死时卡在墙里 5 分钟后消失）。前方是另一台魔力漏斗时当实心处理（免得把地上的过滤器塞进它的过滤器格）。</li>
 *   <li>主人规则（policy §2.3）：没有主人不搬（单人里算房主的）、主人不在线时停工；指定了别人拾取的掉落物（{@code ItemEntity} 的 target）不碰，指定给主人的照搬；
 *       永远捡不起来的（{@code setNeverPickUp}，别的模组放的展示品）不碰。丢出者（thrower）不看：领地里访客丢的东西本来归领地管，原版漏斗也收。</li>
 *   <li>每轮最多搬 {@code max_items_per_cycle} 个掉落物（默认 64，原作不限）；半径配置 1~8（原作无上限）、间隔 10~200。</li>
 *   <li>过滤器按对象与 NBT 身份缓存编译结果（原作每个物品每次都重新解析过滤器 NBT）。</li>
 *   <li>界面同步主人状态和红石状态；更新包不带物品栏（过滤器可能有几 KB 的 NBT，渲染也用不到，F4-15）。</li>
 * </ul>
 * 一轮里没探完的格子（每台机器每刻最多新探测 16 格、同一主人合计 64 格，见 {@code ECProtection}）留到下一轮再搬，这是预期行为。
 * 两台漏斗前方格互在对方范围内会来回倒腾，每台每轮有上限，不处理。
 */
public class MagicalHopperBlockEntity extends MRUBlockEntity {

    /** 服务端配置 {@code machines.magical_hopper.*} */
    public static final class Config {
        public static ForgeConfigSpec.IntValue radius;
        public static ForgeConfigSpec.IntValue interval;
        public static ForgeConfigSpec.IntValue maxItems;

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("magical_hopper");
            radius = builder.comment("How far (blocks, in every direction) the hopper collects dropped items. 3 = a 7x7x7 cube.",
                            "魔力漏斗收集掉落物的范围（向六个方向各多少格），3 即 7×7×7")
                    .defineInRange("radius", 3, 1, 8);
            interval = builder.comment("Ticks between two collection rounds.",
                            "两轮搬运之间隔多少刻")
                    .defineInRange("interval", 20, 10, 200);
            maxItems = builder.comment("At most this many dropped item entities are moved per round.",
                            "每轮最多搬多少个掉落物（一个掉落物实体算一个，不论堆了多少）")
                    .defineInRange("max_items_per_cycle", 64, 1, 256);
            builder.pop();
        }

        public static int radius() {
            return radius == null ? 3 : ECConfig.get(radius);
        }

        public static int interval() {
            return interval == null ? 20 : ECConfig.get(interval);
        }

        public static int maxItems() {
            return maxItems == null ? 64 : ECConfig.get(maxItems);
        }
    }

    public static final int SLOT_FILTER = 0;
    public static final int SLOTS = 1;

    private final ItemFilters.Cache filterCache = new ItemFilters.Cache();
    /** 离下一轮还有几刻（不存盘：重启后第一刻就搬一轮，同原作 delay 初值 0） */
    private int cooldown;

    public MagicalHopperBlockEntity(BlockPos pos, BlockState state) {
        super(ECLogistics.MAGICAL_HOPPER_BE.get(), pos, state, () -> 0, SLOTS);
    }

    /** 槽位规则（两端共用）：唯一的格子只放物品过滤器（原作 isItemValidForSlot） */
    public static boolean slotRule(int slot, ItemStack stack) {
        return slot == SLOT_FILTER && ItemFilters.isFilter(stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slotRule(slot, stack);
    }

    @Override
    protected boolean hasBoundGemSlot() {
        return false;
    }

    @Override
    protected boolean syncInventoryToClient() {
        return false;
    }

    @Override
    protected void serverTick() {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        // 电平开关（原作 L46）：有信号期间每刻看一眼，信号一断下一刻就开工
        if (level == null || RedstonePulse.isPowered(level, worldPosition)) {
            return;
        }
        cooldown = Math.max(0, Config.interval() - 1);
        if (level instanceof ServerLevel serverLevel) {
            transfer(serverLevel);
        }
    }

    /**
     * 立刻搬一轮（不看冷却；有红石信号时什么都不做），返回搬动了几个掉落物。只在服务端有用；GameTest 直接调它。
     */
    public int transferOnce() {
        if (!(level instanceof ServerLevel serverLevel) || RedstonePulse.isPowered(serverLevel, worldPosition)) {
            return 0;
        }
        return transfer(serverLevel);
    }

    private int transfer(ServerLevel level) {
        Direction facing = FacingMachineBlock.facing(getBlockState());
        BlockPos front = worldPosition.relative(facing);
        // 读前方方块不许触发区块加载（前方可能在相邻区块里）
        if (!ECLevels.isFullyLoaded(level, front)) {
            return 0;
        }
        IItemHandler destination = destination(level, front, facing);
        if (destination == null && !level.getBlockState(front).getCollisionShape(level, front).isEmpty()) {
            return 0;
        }
        ItemFilters.Compiled filter = filterCache.get(inventory.getStackInSlot(SLOT_FILTER));
        UUID owner = ECProtection.actingOwner(this);
        AABB frontBox = new AABB(front);
        int radius = Config.radius();
        // 先做不花探测的筛选（前方格里的不动，同原作 doNotTouch；永远捡不起来的、指定给别人的、过滤器不放行的都不要）
        List<ItemEntity> candidates = level.getEntitiesOfClass(ItemEntity.class, new AABB(worldPosition).inflate(radius),
                item -> item.isAlive() && !item.getItem().isEmpty() && !item.getBoundingBox().intersects(frontBox)
                        && !ECItemEntities.neverPickUp(item) && ECItemEntities.pickableBy(item, owner) && filter.test(item.getItem()));
        if (candidates.isEmpty()) {
            return 0;
        }
        // 真有东西要搬才探测目的格：不放行（被拒、主人不在线、这刻探测预算用完）整轮不搬
        if (ECProtection.checkAsOwner(this, front) != ECProtection.Verdict.ALLOW) {
            return 0;
        }
        Vec3 center = Vec3.atCenterOf(worldPosition);
        candidates.sort(Comparator.comparingDouble(item -> item.distanceToSqr(center)));
        int max = Config.maxItems();
        int moved = 0;
        Map<BlockPos, ECProtection.Verdict> verdicts = new HashMap<>();
        for (ItemEntity item : candidates) {
            if (moved >= max) {
                break;
            }
            if (!item.isAlive()) {
                continue;
            }
            // 一格一问（同一格里有好几堆只探测一次）；UNKNOWN（预算用完）的留到下一轮
            ECProtection.Verdict verdict = verdicts.computeIfAbsent(item.blockPosition(), cell -> ECProtection.checkAsOwner(this, cell));
            if (verdict != ECProtection.Verdict.ALLOW) {
                continue;
            }
            if (destination != null ? insert(item, destination) : teleport(item, front)) {
                moved++;
            }
        }
        return moved;
    }

    /** 前方方块的物品栏（面 = 朝向的反面）；不是容器、或是另一台魔力漏斗时返回 null */
    @Nullable
    private static IItemHandler destination(Level level, BlockPos front, Direction facing) {
        BlockEntity target = ECLevels.loadedBlockEntity(level, front);
        if (target == null || target instanceof MagicalHopperBlockEntity) {
            return null;
        }
        return target.getCapability(ForgeCapabilities.ITEM_HANDLER, facing.getOpposite()).resolve().orElse(null);
    }

    /**
     * 装进前方容器：插入的是副本，按剩下的数量回写掉落物（全进去了就删掉掉落物），任何时刻都不会同时存在两份。
     * 一个都没装进去返回 false（容器满了、不收这种东西）。
     */
    private static boolean insert(ItemEntity item, IItemHandler destination) {
        ItemStack stack = item.getItem();
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(destination, stack.copy(), false);
        if (remainder.getCount() >= stack.getCount()) {
            return false;
        }
        if (remainder.isEmpty()) {
            item.discard();
        } else {
            item.setItem(remainder);
        }
        return true;
    }

    /** 瞬移到前方那一格的中心（原作 L56），清掉速度（原作带着原来的速度飞出去），并让客户端立刻看到新位置 */
    private static boolean teleport(ItemEntity item, BlockPos front) {
        item.teleportTo(front.getX() + 0.5D, front.getY() + 0.5D, front.getZ() + 0.5D);
        item.setDeltaMovement(Vec3.ZERO);
        item.hasImpulse = true;
        return true;
    }

    /**
     * 界面用：主人状态的序号 + 1（0 = 还没同步到）。主人不在线、但服主关了「主人在线才工作」时漏斗照常搬，报成在线（界面不画那条金字）
     */
    public int ownerStateCode() {
        ECProtection.OwnerState state = ECProtection.ownerState(this);
        if (state == ECProtection.OwnerState.OFFLINE && !ECProtection.Config.requireOwnerOnline()) {
            state = ECProtection.OwnerState.ONLINE;
        }
        return state.ordinal() + 1;
    }

    /** 界面用：有红石信号（停工）为 1 */
    public int redstoneCode() {
        return level != null && RedstonePulse.isPowered(level, worldPosition) ? 1 : 0;
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MagicalHopperMenu(id, inventory, this);
    }
}
