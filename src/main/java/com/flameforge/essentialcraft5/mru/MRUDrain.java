package com.flameforge.essentialcraft5.mru;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.compat.curio.CurioSeal;
import com.flameforge.essentialcraft5.matrix.MatrixPool;
import com.flameforge.essentialcraft5.matrix.MatrixResonance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 玩家消耗 MRU（法杖、工具、饰品等）。对应原作 ECUtils.playerUseMRU / tryToDecreaseMRUInStorage。
 * <p>
 * 先从玩家身上所有「储存」物品（isStorage 为 true，如灵魂碎片）里凑：饰品栏 → 盔甲 → 背包 → 副手，
 * 凑够才扣，按这个顺序扣；凑不够再看物品自己存的 MRU。创造模式不扣。
 * <p>
 * 只判断不扣用 {@link #canAfford}（第四批框架 F4-15）：和 {@link #playerUseMRU} 用同一套收集顺序和判断，
 * 「先问够不够、放行了再扣」的逐格操作（魔力挖掘器每格 9 MRU）先调它。
 * 第三批的 {@code CharmsMRU.canAfford}、{@code MRUStaffItem.canAfford} 都转调这里，判断只有这一份。
 * <p>
 * 无尽 MRU 晶体（只放创造栏，registry/ECCreative）被 {@code general.baubles.disabled} 列进去时整件不算储存物品
 * （不论放在背包还是指环位），同无尽护佑护符「被禁用就不论放在哪都不生效」。别的储存物品照旧不看这个配置。
 * <p>
 * <b>第十三批 13a 的两个钩子</b>（batch13.md §3.1.4、§3.2，限制见 policy §4.16）：
 * <ul>
 *   <li><b>矩阵容量</b>（{@link MatrixPool}）：身上储存物品之后、物品自己之前付。「储存物品」一侧的合计、扣费都把容量算进去
 *       （{@link #tryDrainFromStorages}、{@link #canAfford}、{@link #availableFromStorages}），所以凡是走这里的花费，包括护盾盔甲
 *       （item/armor/MRUShield，经 {@link #payShield}）的扣费，顺序都是储存物品 → 容量 → 物品自己。容量被花掉时标记同步。</li>
 *   <li><b>魔力相性的折扣</b>（{@code MatrixResonance.costMultiplier(player)}，矩阵属性是魔力、相性 ≥ I 时小于 1）：只打在本模组随身物品的花费上，
 *       即 {@link #playerUseMRU} 和 {@link #canAfford} 先按 {@link #discounted} 折一次再判、再扣，两者结论一致。
 *       护盾盔甲的护盾扣费不打折（免得和同调阶的降价叠到每点 160），走 {@link #payShield}；拿 {@link #availableFromStorages}
 *       当预算一笔笔减的地方按原价比，结论只会偏保守（预算里差一点时宁可不放行）。</li>
 * </ul>
 * 护盾的同调新覆盖面（宅男 2 阶的魔法、4 阶的无视护甲）扣电不认无尽 MRU 晶体（batch13.md §4.4），{@link #payShield} 的 finiteOnly；
 * 混合端「先算后扣」用 {@link #finiteFromStorages} / {@link #hasInfiniteStorage} 当预算。收集储存物品只有 {@link #storages} 这一份，
 * 以后给它加跳过规则，护盾和别的花费一起生效。
 */
public final class MRUDrain {

    private MRUDrain() {
    }

    /** @return 是否扣成功（创造模式恒为 true） */
    public static boolean playerUseMRU(Player player, ItemStack stack, int amount) {
        return playerUseMRU(player, stack, amount, false);
    }

    /**
     * 同 {@link #playerUseMRU(Player, ItemStack, int)}；skipCurios = true 时饰品栏里的储存物品不付（豁免标签 {@code curio_seal_exempt} 里的照付）。
     * 给「付了钱就会把自己的饰品栏封住」的花费用（饰品封禁场 {@code affects_self}）：封住以后那些储存本来就不算，
     * 从第一次就不算，才不会「这一秒用饰品栏的电付了、下一秒被封了付不起」来回跳。
     */
    public static boolean playerUseMRU(Player player, ItemStack stack, int amount, boolean skipCurios) {
        return pay(player, stack, discounted(player, amount), skipCurios, false);
    }

    /**
     * 按原价扣、不打魔力相性的折扣（护盾盔甲的护盾扣费用：batch13.md §3.2「不含护盾盔甲的护盾扣费」）。顺序同 {@link #playerUseMRU}。
     */
    public static boolean playerUseMRUExact(Player player, ItemStack stack, int amount) {
        return pay(player, stack, amount, false, false);
    }

    /**
     * 护盾盔甲替护盾扣 amount MRU（item/armor/MRUShield）：按原价（不打魔力相性的折扣），顺序同 {@link #playerUseMRU}；
     * finiteOnly 时身上的无尽 MRU 晶体不算（同调新覆盖面不从它扣），只认别的储存物品、矩阵容量和这件自己的。amount ≤ 0 恒为 true。
     */
    public static boolean payShield(Player player, ItemStack own, int amount, boolean finiteOnly) {
        return amount <= 0 || pay(player, own, amount, false, finiteOnly);
    }

    private static boolean pay(Player player, ItemStack stack, int amount, boolean skipCurios, boolean finiteOnly) {
        if (tryDrainFromStorages(player, amount, skipCurios, finiteOnly)) {
            return true;
        }
        IMRUHandlerItem own = stack.getCapability(ECCapabilities.MRU_ITEM).orElse(null);
        if (own != null && own.getMRU() >= amount) {
            own.extractMRU(amount, true);
            return true;
        }
        return false;
    }

    /**
     * 打过魔力相性折扣的花费：amount × {@code MatrixResonance.costMultiplier(player)}，向上取整（小额花费不会折成 0）。
     * amount ≤ 0 原样返回；倍数不是 (0, 1] 里的数时按 1 算。
     */
    public static int discounted(Player player, int amount) {
        if (amount <= 0) {
            return amount;
        }
        double multiplier = MatrixResonance.costMultiplier(player);
        if (!(multiplier > 0D) || multiplier >= 1D) {
            return amount;
        }
        return (int) Math.max(1L, (long) Math.ceil(amount * multiplier));
    }

    /**
     * 只判断够不够、不扣（{@link #playerUseMRU} 的只读版本，结论和它一致）：创造模式恒为 true；
     * 身上储存物品合计 ≥ amount，或者 own 自己存的 ≥ amount。amount ≤ 0 恒为 true（同 playerUseMRU）。
     *
     * @param own 花 MRU 的那件物品（它自己存的 MRU 是第二来源）；没有就传 {@link ItemStack#EMPTY}
     */
    public static boolean canAfford(Player player, ItemStack own, int amount) {
        return canAfford(player, own, amount, false);
    }

    /** 同 {@link #canAfford(Player, ItemStack, int)}；skipCurios 见 {@link #playerUseMRU(Player, ItemStack, int, boolean)} */
    public static boolean canAfford(Player player, ItemStack own, int amount, boolean skipCurios) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        int cost = discounted(player, amount);
        if (totalStored(storages(player, skipCurios)) + MatrixPool.available(player) >= Math.abs(cost)) {
            return true;
        }
        IMRUHandlerItem handler = own.getCapability(ECCapabilities.MRU_ITEM).orElse(null);
        return handler != null && handler.getMRU() >= cost;
    }

    /**
     * 身上储存物品（加上矩阵容量）一共能付多少（只读）：创造模式、或者带着无尽 MRU 晶体时是 {@link Long#MAX_VALUE}；
     * 其余是 {@link #tryDrainFromStorages} 判「凑不凑得够」用的同一个合计。
     * 要「先排好几笔、晚一点再一起扣」的地方（MRU 盔甲在混合端等伤害生效再扣，见 item/armor/MRUShield）拿它当预算一笔笔减，
     * 结论和依次调 {@link #playerUseMRU} 一样。
     */
    public static long availableFromStorages(Player player) {
        return availableFromStorages(player, false);
    }

    /** 同 {@link #availableFromStorages(Player)}；skipCurios 见 {@link #playerUseMRU(Player, ItemStack, int, boolean)} */
    public static long availableFromStorages(Player player, boolean skipCurios) {
        if (player.getAbilities().instabuild) {
            return Long.MAX_VALUE;
        }
        List<IMRUHandlerItem> storages = storages(player, skipCurios);
        for (IMRUHandlerItem storage : storages) {
            if (storage.isInfinite()) {
                return Long.MAX_VALUE;
            }
        }
        return totalStored(storages) + MatrixPool.available(player);
    }

    public static boolean tryDrainFromStorages(Player player, int amount) {
        return tryDrainFromStorages(player, amount, false);
    }

    /** 同 {@link #tryDrainFromStorages(Player, int)}；skipCurios 见 {@link #playerUseMRU(Player, ItemStack, int, boolean)} */
    public static boolean tryDrainFromStorages(Player player, int amount, boolean skipCurios) {
        return tryDrainFromStorages(player, amount, skipCurios, false);
    }

    /** 同 {@link #tryDrainFromStorages(Player, int, boolean)}；finiteOnly 见 {@link #payShield} */
    private static boolean tryDrainFromStorages(Player player, int amount, boolean skipCurios, boolean finiteOnly) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        int need = Math.abs(amount);

        List<IMRUHandlerItem> storages = storages(player, skipCurios);
        // 带着无尽 MRU 晶体（只放创造栏，registry/ECCreative）时全由它付：它扣多少都不减，身上别的储存物品一点不动。
        // finiteOnly 时不认它，按别的储存物品和容量算
        if (storages.removeIf(IMRUHandlerItem::isInfinite) && !finiteOnly) {
            return true;
        }
        if (totalStored(storages) + MatrixPool.available(player) < need) {
            return false;
        }
        int remaining = need;
        for (IMRUHandlerItem storage : storages) {
            remaining -= storage.extractMRU(remaining, true);
            if (remaining <= 0) {
                break;
            }
        }
        // 储存物品凑不够的部分由矩阵容量付（上面已经核过合计够）
        if (remaining > 0) {
            MatrixPool.drain(player, remaining);
        }
        return true;
    }

    /**
     * 身上储存物品（不算无尽晶体）加矩阵容量的合计（只读，不看创造模式）：护盾在混合端「先算后扣」时当新覆盖面的预算，
     * 和依次调 {@link #payShield}（finiteOnly）的结论一样
     */
    public static long finiteFromStorages(Player player) {
        long total = 0L;
        for (IMRUHandlerItem storage : storages(player, false)) {
            if (!storage.isInfinite()) {
                total += Math.max(0, storage.getMRU());
            }
        }
        return total + Math.max(0, MatrixPool.available(player));
    }

    /** 身上带着能用的无尽 MRU 晶体（被 {@code general.baubles.disabled} 列进去的不算） */
    public static boolean hasInfiniteStorage(Player player) {
        for (IMRUHandlerItem storage : storages(player, false)) {
            if (storage.isInfinite()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 玩家身上的储存物品，按扣的顺序：饰品栏 → 盔甲 → 背包 → 副手（这个类里所有判断和扣费共用，护盾也走这里）。
     * 被饰品封禁场压制的饰品本来就不算（{@link CuriosCompat#forEachEquipped}）；skipCurios 时没被压制的也不算，豁免标签里的除外
     */
    private static List<IMRUHandlerItem> storages(Player player, boolean skipCurios) {
        List<IMRUHandlerItem> storages = new ArrayList<>();
        Consumer<ItemStack> visit = stack -> collect(stack, storages);
        CuriosCompat.forEachEquipped(player, stack -> {
            if (!skipCurios || CurioSeal.isExempt(stack)) {
                visit.accept(stack);
            }
        });
        player.getInventory().armor.forEach(visit);
        player.getInventory().items.forEach(visit);
        player.getInventory().offhand.forEach(visit);
        return storages;
    }

    private static long totalStored(List<IMRUHandlerItem> storages) {
        long available = 0;
        for (IMRUHandlerItem storage : storages) {
            available += storage.getMRU();
        }
        return available;
    }

    private static void collect(ItemStack stack, List<IMRUHandlerItem> out) {
        if (stack.isEmpty()) {
            return;
        }
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> {
            // 被配置禁用的无尽晶体不算：否则它按 Integer.MAX_VALUE 算进总量，禁用了照样全付
            if (handler.isStorage() && !(handler.isInfinite() && CuriosCompat.isDisabled(stack))) {
                out.add(handler);
            }
        });
    }
}
