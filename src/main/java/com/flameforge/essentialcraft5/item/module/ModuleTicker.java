package com.flameforge.essentialcraft5.item.module;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 模块的电费与手册打勾（batch10.md §4.3 第 4、9 条），只在服务端。
 *
 * <h2>电费</h2>
 * 每名玩家每 {@value #INTERVAL} 刻一次（{@link TickEvent.PlayerTickEvent} 末尾，按 {@code (tickCount + 实体 id) % 20} 错开）：
 * 格子状态生效、{@link ModuleType#mruPerSecond()} &gt; 0 的模块按头 → 靴的顺序一块块记账，<b>先算后扣</b>——
 * 加上这一块还付得起（{@link MRUDrain#canAfford}，先看身上的储存物品、再看胸甲自存，创造模式不扣）才算进去，付不起的这一秒不生效（终端标「缺电」）；
 * 记完账一次扣清。扣成的这一秒（加 {@value #GRACE} 刻余量）{@link ModuleCache#active} 为真。刚打开、刚装上的按秒耗电模块要等下一次记账（最多 1 秒）才生效，
 * 这一秒终端写「等待扣电」（{@link ModuleCache.Status#PENDING}）。不在打开的当刻补记一次账：相位锚的扫描本来就按自己的整秒跑，当刻扣了电也不会更早生效，
 * 一秒里连着开关、换装还会重复收费。
 * <p>
 * 一次性花费的模块（回溯、矩阵过滤）不经过这里：触发时自己 {@code MRUDrain.canAfford} → 放行 → {@code playerUseMRU}（照 {@code ComputerSealField} 的先算后扣）。
 *
 * <h2>手册</h2>
 * 记账后身上生效的模块有两种以上就发 {@code guide/terminal/modules}（已完成就不再看）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class ModuleTicker {

    /** 记账间隔（刻） */
    public static final int INTERVAL = 20;
    /** 扣成以后多认几刻（错开的记账、服务端掉刻不至于让模块闪一下） */
    public static final int GRACE = 5;
    /** 手册步骤「装上两块模块」 */
    public static final ResourceLocation GUIDE_MODULES = EssentialCraft5.id("guide/terminal/modules");
    /** 生效几种算打勾 */
    public static final int GUIDE_MODULES_COUNT = 2;

    /**
     * 一次记账的结果。
     *
     * @param at     哪一刻（世界 gameTime）
     * @param paid   扣成的模块
     * @param failed 付不起的模块
     */
    private record Power(long at, Set<ResourceLocation> paid, Set<ResourceLocation> failed) {
    }

    private static final Map<Player, Power> POWER = new WeakHashMap<>();

    private ModuleTicker() {
    }

    @SubscribeEvent
    public static void onModulePlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        if (Math.floorMod(player.tickCount + player.getId(), INTERVAL) != 0) {
            return;
        }
        tick(player);
    }

    /** 一次完整的每秒处理：记账、手册（GameTest 直接调） */
    public static void tick(ServerPlayer player) {
        pay(player);
        checkGuide(player);
    }

    /**
     * 记一次账（见类注释）。
     *
     * @return 这次扣了多少 MRU
     */
    public static int pay(ServerPlayer player) {
        ModuleCache.Snapshot snapshot = ModuleCache.get(player);
        long now = player.level().getGameTime();
        if (snapshot.eligible().isEmpty()) {
            POWER.remove(player);
            return 0;
        }
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        Set<ResourceLocation> paid = new HashSet<>();
        Set<ResourceLocation> failed = new HashSet<>();
        long total = 0L;
        for (ModuleType type : snapshot.eligible()) {
            int cost = type.mruPerSecond();
            if (cost <= 0) {
                continue;
            }
            long next = total + cost;
            if (next <= Integer.MAX_VALUE && MRUDrain.canAfford(player, chest, (int) next)) {
                total = next;
                paid.add(type.id());
            } else {
                failed.add(type.id());
            }
        }
        if (total > 0L && !MRUDrain.playerUseMRU(player, chest, (int) total)) {
            // 判得起却扣不成（只会在储存物品和胸甲各付一部分的边界上出现）：这一秒都算付不起
            failed.addAll(paid);
            paid.clear();
            total = 0L;
        }
        POWER.put(player, new Power(now, Set.copyOf(paid), Set.copyOf(failed)));
        return (int) total;
    }

    /** 这一秒扣成了（按秒耗电的模块） */
    public static boolean powered(Player player, ModuleType type) {
        Power power = fresh(player);
        return power != null && power.paid().contains(type.id());
    }

    /** 这一秒付不起（终端标「缺电」） */
    public static boolean unpaid(Player player, ModuleType type) {
        Power power = fresh(player);
        return power != null && power.failed().contains(type.id());
    }

    /** 这一秒扣了多少（终端显示用） */
    public static int paidPerSecond(Player player) {
        Power power = fresh(player);
        if (power == null) {
            return 0;
        }
        int sum = 0;
        for (ResourceLocation id : power.paid()) {
            ModuleType type = ArmorModules.get(id);
            if (type != null) {
                sum += type.mruPerSecond();
            }
        }
        return sum;
    }

    @Nullable
    private static Power fresh(Player player) {
        Power power = POWER.get(player);
        if (power == null) {
            return null;
        }
        long age = player.level().getGameTime() - power.at();
        return age >= 0 && age <= INTERVAL + GRACE ? power : null;
    }

    /**
     * 身上能付的 MRU（终端判「一次性花费付不付得起」用，和 {@link MRUDrain#canAfford} 的口径一致）：储存物品合计、胸甲自存，取大的那个；
     * 创造模式、带着无尽 MRU 晶体时 {@link Long#MAX_VALUE}
     */
    public static long availableMru(Player player) {
        long storages = MRUDrain.availableFromStorages(player);
        if (storages == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        IMRUHandlerItem own = player.getItemBySlot(EquipmentSlot.CHEST).getCapability(ECCapabilities.MRU_ITEM).orElse(null);
        return Math.max(storages, own != null ? Math.max(0, own.getMRU()) : 0);
    }

    /** 身上生效的模块够两种就发手册进度（进度不存在、已完成时什么都不做；条件名不写死） */
    public static void checkGuide(ServerPlayer player) {
        if (ModuleCache.get(player).eligible().size() < GUIDE_MODULES_COUNT || ModuleCache.activeCount(player) < GUIDE_MODULES_COUNT) {
            return;
        }
        Advancement advancement = player.server.getAdvancements().getAdvancement(GUIDE_MODULES);
        if (advancement == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) {
            return;
        }
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    /** 下线时清掉（关服见 {@link #clear}；换维度不用清：记账按 gameTime，各维度共用一份） */
    static void forget(Player player) {
        POWER.remove(player);
    }

    static void clear() {
        POWER.clear();
    }

    /** GameTest 用：丢掉这名玩家的记账 */
    public static void forgetForTest(Player player) {
        POWER.remove(player);
    }
}
