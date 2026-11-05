package com.flameforge.essentialcraft5.item.creative;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import com.flameforge.essentialcraft5.compat.CuriosCompat;
import com.flameforge.essentialcraft5.mru.MRUBalance;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 无尽 MRU 晶体（只放创造栏，registry/ECCreative）：无底的 MRU 储存物品。
 * <p>
 * 和灵魂碎片一样是「储存物品」（{@code isStorage} 恒为 true）：放在背包、副手、盔甲位或饰品栏指环位，
 * 玩家花 MRU 的东西（法杖、护符、魔力之翼、建筑魔杖……都走 {@code mru/MRUDrain}）都从它扣。
 * 它的存量和上限都是 {@link Integer#MAX_VALUE}，扣多少都不减；{@code MRUDrain} 认它（{@link IMRUHandlerItem#isInfinite()}），
 * 带着它时由它全付，身上别的储存物品一点不动。别的东西往里充 MRU 一律退回（已经满了），充能仓不会白耗。
 * 物品不存任何 NBT，平衡值恒为纯净。
 * 被 {@code general.baubles.disabled} 列进去时整件不生效（不论放在哪），{@code MRUDrain} 不把它算作储存物品，提示里写「已禁用」。
 */
public class InfiniteMRUCrystalItem extends Item {

    public static final String MRU_KEY = "tooltip.essentialcraft5.creative.infinite_mru_crystal.mru";
    public static final String USAGE_KEY = "tooltip.essentialcraft5.creative.infinite_mru_crystal";

    /** 耐久条颜色转一圈的毫秒数（和贴图的彩虹同一个节奏：32 帧 × 2 刻 = 3.2 秒，见 tools/gen/creative_textures.py） */
    private static final long BAR_CYCLE_MS = 3200L;

    public InfiniteMRUCrystalItem(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new InfiniteMRUItemStorage();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(MRU_KEY).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable(USAGE_KEY).withStyle(ChatFormatting.GRAY));
        if (CuriosCompat.isDisabled(stack)) {
            tooltip.add(CuriosCompat.disabledTooltip());
        }
        tooltip.add(CreativeOnly.tooltipLine());
    }

    /** 永远显示满格的 MRU 条，颜色按时间转彩虹 */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return 13;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float hue = (Util.getMillis() % BAR_CYCLE_MS) / (float) BAR_CYCLE_MS;
        return Mth.hsvToRgb(hue, 0.55F, 1F);
    }

    /**
     * 无底的物品 MRU：存量 = 上限 = {@link Integer#MAX_VALUE}；{@code extractMRU} 要多少给多少（不扣）、{@code addMRU} 全部退回、
     * {@code setMRU} / {@code setStorage} / {@code setBalance} 什么都不做。
     */
    public static final class InfiniteMRUItemStorage implements IMRUHandlerItem, ICapabilityProvider {
        private final LazyOptional<IMRUHandlerItem> holder = LazyOptional.of(() -> this);

        @Override
        public int getMaxMRU() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getMRU() {
            return Integer.MAX_VALUE;
        }

        @Override
        public void setMRU(int amount) {
            // 永远是满的
        }

        @Override
        public int addMRU(int amount, boolean doAdd) {
            return amount;
        }

        @Override
        public int extractMRU(int amount, boolean doExtract) {
            return Math.max(0, amount);
        }

        @Override
        public float getBalance() {
            return MRUBalance.PURE;
        }

        @Override
        public void setBalance(float balance) {
            // 恒为纯净
        }

        @Override
        public boolean isStorage() {
            return true;
        }

        @Override
        public void setStorage(boolean storage) {
            // 恒为储存物品
        }

        @Override
        public boolean isInfinite() {
            return true;
        }

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return ECCapabilities.MRU_ITEM.orEmpty(cap, holder);
        }
    }
}
