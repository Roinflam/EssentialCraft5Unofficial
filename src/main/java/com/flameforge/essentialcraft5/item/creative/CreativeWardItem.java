package com.flameforge.essentialcraft5.item.creative;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECCreative;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;

/**
 * 无尽护佑护符（注册名 creative_ward；没有配方、只放创造栏，registry/ECCreative）：带在身上（背包、副手、盔甲位，或饰品栏项链位）就保持「干净」，
 * 生存里照常生效，带着它用饰品、进海纳尔都不用善后：
 * <ul>
 *   <li><b>UBMRU 保持满</b>：每 {@value #TOP_UP_INTERVAL} 刻把 UBMRU 补到 {@value #WARD_UBMRU}（已经更多就不动），
 *       吃 UBMRU 的饰品、矩阵吸收器随便用。第一次补之前先把原来的数记下（{@link #BASE_KEY}）：护符一离身（或被配置禁用），
 *       下一次检查就把 UBMRU 退回到不超过这个数（{@link #settle}）；被人击杀时对方最多拿走这个数（{@code player/UBMRUEvents}），
 *       护符补出来的那份不会流到别人手里。</li>
 *   <li><b>不积累腐化</b>：矩阵损伤保持 0（{@code corruption/CorruptionTicker}），抽不到新的腐化效果；身上已有的效果不动，
 *       要清用 {@code /ec5 player … corruption} 或灵魂五芒星。</li>
 *   <li><b>不积累海纳尔辐射</b>：剂量保持 0（{@code hoanna/Radiation}），毒雾、ξ 命中、海纳尔的环境辐射都加不上去。</li>
 * </ul>
 * 判定（{@link #protects}）看背包 41 格加饰品栏缓存，每名玩家每刻至多几次，开销可以忽略。
 * 被 {@code general.baubles.disabled} 列进去时整件不生效（不论放在哪）。
 */
public class CreativeWardItem extends Item implements ICurioItem {

    /** 护符把 UBMRU 补到多少 */
    public static final int WARD_UBMRU = 1_000_000_000;
    /** 补 UBMRU 的间隔（刻） */
    public static final int TOP_UP_INTERVAL = 20;

    public static final String USAGE_KEY = "tooltip.essentialcraft5.creative.creative_ward";

    /**
     * 戴上护符之前的 UBMRU，存在玩家持久数据的 {@link Player#PERSISTED_NBT_TAG} 子标签下：
     * 死亡重生、从末地回主世界都会新建玩家对象，Forge 只把这个子标签复制过去（顶层的键会丢），
     * 而 UBMRU 本身随玩家数据整份复制，记录丢了就退不回去了。
     */
    public static final String BASE_KEY = "essentialcraft5:ward_ubmru_base";

    public CreativeWardItem(Properties properties) {
        super(properties);
    }

    /** 这名玩家身上（背包、盔甲、副手、饰品栏）带着无尽护佑护符，而且没被配置禁用。假玩家一律 false */
    public static boolean protects(@Nullable Player player) {
        if (player == null || player instanceof FakePlayer || !ECCreative.CREATIVE_WARD.isPresent()) {
            return false;
        }
        Item ward = ECCreative.CREATIVE_WARD.get();
        if (CuriosCompat.isDisabled(ward)) {
            return false;
        }
        // 背包、盔甲、副手逐格看：Inventory.hasAnyMatching 被 Curios 接到饰品栏上，还不受饰品封禁场的压制，
        // 戴在饰品栏里的只认 CuriosCompat（被压制时它当不存在）
        return CuriosCompat.has(player, ward) || inVanillaInventory(player, ward);
    }

    private static boolean inVanillaInventory(Player player, Item ward) {
        for (List<ItemStack> compartment : List.of(player.getInventory().items, player.getInventory().armor, player.getInventory().offhand)) {
            for (ItemStack stack : compartment) {
                if (stack.is(ward)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 把 UBMRU 补到 {@link #WARD_UBMRU}（由 {@link #settle} 调；GameTest 直接调）。
     * 还没有记录时先记下现在的 UBMRU：不管这次补没补都记，原来就有 10 亿以上的人摘下护符时也照原样退回。
     *
     * @return 这次有没有补
     */
    public static boolean topUp(Player player) {
        if (!protects(player)) {
            return false;
        }
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            return false;
        }
        rememberBase(player, data.getUBMRU());
        if (data.getUBMRU() >= WARD_UBMRU) {
            return false;
        }
        data.setUBMRU(WARD_UBMRU);
        return true;
    }

    /**
     * 服务端玩家 tick 每 {@value #TOP_UP_INTERVAL} 刻调一次：护符还生效就补（{@link #topUp}）；
     * 已经不生效（摘下、丢掉、被配置禁用）而且有记录的，UBMRU 退回到不超过记下的数，删掉记录。
     */
    public static void settle(Player player) {
        if (protects(player)) {
            topUp(player);
            return;
        }
        int base = recordedBase(player);
        if (base < 0) {
            return;
        }
        IPlayerData data = PlayerDataEvents.get(player);
        if (data != null) {
            data.setUBMRU(Math.min(data.getUBMRU(), base));
            clearBase(player);
        }
    }

    /** 记下的戴上护符之前的 UBMRU（≥ 0）；没有记录时 −1 */
    public static int recordedBase(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return persisted.contains(BASE_KEY, Tag.TAG_INT) ? Math.max(0, persisted.getInt(BASE_KEY)) : -1;
    }

    /** 删掉记录（退回之后、死亡清零 UBMRU 时） */
    public static void clearBase(Player player) {
        CompoundTag data = player.getPersistentData();
        if (data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            data.getCompound(Player.PERSISTED_NBT_TAG).remove(BASE_KEY);
        }
    }

    private static void rememberBase(Player player, int ubmru) {
        CompoundTag data = player.getPersistentData();
        // getCompound 在没有这个子标签时返回一个新的空标签，没挂在 data 上，改完要 put 回去
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(BASE_KEY, Tag.TAG_INT)) {
            persisted.putInt(BASE_KEY, Math.max(0, ubmru));
            data.put(Player.PERSISTED_NBT_TAG, persisted);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(USAGE_KEY).withStyle(ChatFormatting.GRAY));
        if (CuriosCompat.isDisabled(stack)) {
            tooltip.add(CuriosCompat.disabledTooltip());
        }
        tooltip.add(CreativeOnly.tooltipLine());
    }

    /** 饰品栏里只算一件（多戴也没有额外效果，省得占槽） */
    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return CuriosCompat.onlyOneEquipped(slotContext, stack);
    }

    /** 服务端每 {@value #TOP_UP_INTERVAL} 刻给带着护符的玩家补 UBMRU，护符离身的退回原来的数 */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class CreativeWardEvents {

        private CreativeWardEvents() {
        }

        @SubscribeEvent
        public static void onCreativeWardPlayerTick(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER
                    || event.player.tickCount % TOP_UP_INTERVAL != 0) {
                return;
            }
            settle(event.player);
        }
    }
}
