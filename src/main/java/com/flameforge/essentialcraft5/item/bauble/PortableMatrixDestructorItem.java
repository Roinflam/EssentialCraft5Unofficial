package com.flameforge.essentialcraft5.item.bauble;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.blockentity.MatrixAbsorberBlockEntity;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.mru.MRUItemStorage;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 便携矩阵换能器（原作 baublesCore meta 0，portableMD）：戴在项链槽里，把佩戴者的 UBMRU 换成自己存的 MRU（最多 {@value #MAX_MRU}）。
 * <p>
 * 移植改动（batch3 E6）：
 * <ul>
 *   <li>原作它的 MRU 存储不是「储存物品」（{@code isStorage} 为 false），法杖、工具扣 MRU 时根本不从它扣，存进去的 MRU 用不了。
 *       这里做成储存物品：{@code MRUDrain} 会先从饰品栏里的储存物品扣，它排在最前面。</li>
 *   <li>原作每 tick 1 UBMRU → 10 MRU，一只僵尸（约 400 UBMRU）就是 4000 MRU，会压过所有产能器。改成和矩阵换能器一样的比例：
 *       直接读矩阵换能器的配置（{@code machines.matrix_absorber.ubmru_usage} / {@code mru_generated}，默认 10 UBMRU → 1 MRU）。</li>
 *   <li>原作每 tick 换一次、每次改物品 NBT；Curios 每次 NBT 变化都会给周围玩家同步这一格、发 CurioChangeEvent。
 *       这里每 {@value #INTERVAL} tick 结算一次（一次最多换 {@value #INTERVAL} 次），速度不变（默认每秒 200 UBMRU → 20 MRU），包少 20 倍。</li>
 * </ul>
 * 平衡值不写（读出来是默认的 1.0 纯净，同矩阵换能器的默认值）。
 */
public class PortableMatrixDestructorItem extends SpecialBaubleItem {

    /** 原作 maxMRU = 5000 */
    public static final int MAX_MRU = 5000;
    /** 多少 tick 结算一次 */
    public static final int INTERVAL = 20;

    public PortableMatrixDestructorItem(Properties properties) {
        super(properties, CuriosCompat.NECKLACE, Settings.of().tick(PortableMatrixDestructorItem::wornTick).detail());
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new MRUItemStorage(stack, MAX_MRU, true);
    }

    private static void wornTick(Player player, ItemStack stack) {
        if (player.tickCount % INTERVAL == 0) {
            convert(player, stack, INTERVAL);
        }
    }

    /**
     * 最多换 conversions 次：每次扣 usage UBMRU、加 generated MRU；UBMRU 不够一次或存满就停。
     *
     * @return 实际换了几次（GameTest 用）
     */
    public static int convert(Player player, ItemStack stack, int conversions) {
        IPlayerData data = PlayerDataEvents.get(player);
        IMRUHandlerItem storage = stack.getCapability(ECCapabilities.MRU_ITEM).orElse(null);
        if (data == null || storage == null || conversions <= 0) {
            return 0;
        }
        int usage = MatrixAbsorberBlockEntity.Config.ubmruUsage == null ? 10 : ECConfig.get(MatrixAbsorberBlockEntity.Config.ubmruUsage);
        int generated = MatrixAbsorberBlockEntity.Config.mruGenerated == null ? 1 : ECConfig.get(MatrixAbsorberBlockEntity.Config.mruGenerated);
        if (generated <= 0) {
            return 0;
        }
        int space = storage.getMaxMRU() - storage.getMRU();
        // 存满为止：还差 space，每次 generated，最后一次可以溢出一点（addMRU 夹到上限），同矩阵换能器
        int bySpace = space <= 0 ? 0 : (space + generated - 1) / generated;
        int byUBMRU = usage <= 0 ? conversions : data.getUBMRU() / usage;
        int times = Math.min(conversions, Math.min(bySpace, byUBMRU));
        if (times <= 0) {
            return 0;
        }
        data.setUBMRU((int) Math.max(0L, data.getUBMRU() - (long) times * usage));
        storage.addMRU((int) Math.min(Integer.MAX_VALUE, (long) times * generated), true);
        return times;
    }

    @Override
    protected void appendExtraLines(ItemStack stack, List<Component> tooltip) {
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler ->
                tooltip.add(Component.translatable("tooltip.essentialcraft5.mru", handler.getMRU(), handler.getMaxMRU()).withStyle(ChatFormatting.GRAY)));
    }
}
