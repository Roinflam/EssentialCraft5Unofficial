package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.registry.ECTags;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * 魔力水：合成材料，也可以喝。对应原作 ItemGenericEC 的 meta 6（magicalWater）里 onItemUseFinish 那段。
 * （原作另有一个 magicWaterBottle / ItemMagicalWater，是别的东西，不在这里。）
 * <p>
 * 原作喝一口 32 刻，喝完还一个玻璃瓶；UBMRU 的写法是 {@code modifyUBMRU(当前 + 加成)}，加成只在戴着 α 指环
 * （ItemBaublesSpecial meta 8）时是 500，没戴就是 0，也就是说不戴指环喝了只是个空动作。
 * <p>
 * 第三批接上：服务端喝完时，任意饰品槽里戴着物品标签 {@link #UBMRU_FROM_MAGIC_WATER}（{@code essentialcraft5:ubmru_from_magic_water}）
 * 里的物品就 +{@value #ALPHA_UBMRU} UBMRU（按 long 加、夹到 int 上限，同 UBMRUEvents；戴几件都只加一次，同原作）。
 * 查饰品走 {@code CuriosCompat.has(player, 标签)}（每玩家缓存，被配置 general.baubles.disabled 关掉的饰品不算）。
 * 标签由框架建（tools/tags/framework3c.json），α 指环由 ECBaubles 注册并加进标签；不经过 IUBMRUGainModifyHandler（原作直接改数值）。
 * <p>
 * 修掉的原作 bug：原作只在整组喝完（数量变成 0）时才返回玻璃瓶，一组 64 瓶喝掉 63 瓶的瓶子都凭空没了。
 * 这里照原版蜂蜜瓶的做法，没喝完就把瓶子塞进物品栏（塞不下扔地上）。创造模式不消耗也不给瓶子，同原作。
 */
public class MagicalWaterItem extends Item {

    /** 原作 getMaxItemUseDuration */
    public static final int DRINK_TICKS = 32;
    /** 原作戴 α 指环喝一瓶加的 UBMRU */
    public static final int ALPHA_UBMRU = 500;
    /** 戴着（饰品槽）这个标签里的物品喝魔力水加 UBMRU（α 指环） */
    public static final TagKey<Item> UBMRU_FROM_MAGIC_WATER = ECTags.modItem("ubmru_from_magic_water");

    public MagicalWaterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return DRINK_TICKS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.CONSUME_ITEM.trigger(serverPlayer, stack);
            serverPlayer.awardStat(Stats.ITEM_USED.get(this));
        }
        entity.gameEvent(GameEvent.DRINK);
        // 原作这里给 UBMRU：+0，戴 α 指环 +500（只在服务端）
        if (!level.isClientSide && entity instanceof Player drinker) {
            grantAlphaUBMRU(drinker);
        }

        Player player = entity instanceof Player p ? p : null;
        boolean consumes = player != null && !player.getAbilities().instabuild;
        if (consumes) {
            stack.shrink(1);
        }
        if (stack.isEmpty()) {
            return new ItemStack(Items.GLASS_BOTTLE);
        }
        if (consumes) {
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            if (!player.getInventory().add(bottle)) {
                player.drop(bottle, false);
            }
        }
        return stack;
    }

    /** 戴着 {@link #UBMRU_FROM_MAGIC_WATER} 标签的饰品时加 {@value #ALPHA_UBMRU} UBMRU，返回加了多少（没戴、没数据时 0） */
    public static int grantAlphaUBMRU(Player player) {
        if (!CuriosCompat.has(player, UBMRU_FROM_MAGIC_WATER)) {
            return 0;
        }
        IPlayerData data = player.getCapability(ECCapabilities.PLAYER_DATA).orElse(null);
        if (data == null) {
            return 0;
        }
        data.setUBMRU((int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, data.getUBMRU()) + ALPHA_UBMRU));
        return ALPHA_UBMRU;
    }
}
