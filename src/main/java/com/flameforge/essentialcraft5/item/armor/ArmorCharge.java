package com.flameforge.essentialcraft5.item.armor;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IMRUHandlerItem;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 护甲电量（第十三批 13b「维持方便」，batch13.md §4.7）：快捷栏旁的电量条（{@code client/ArmorChargeHud}）和 MRU 护盾盔甲提示框里
 * 「满电约挡」那一行共用的算法。只读物品自己存的 MRU（{@code MRU_ITEM} 能力），两端都能调；客户端上读到的就是服务端同步过来的物品 NBT，不另发包。
 * <ul>
 *   <li>{@link #worn}：身上穿在自己部位的 {@link IMRUShieldArmor}（{@link MRUShield#wornPiece}，和挨打时认的是同一批）存的 MRU 合计、容量合计、件数。</li>
 *   <li>{@link #fullChargeHits}：一件充满时，光靠它自己存的电能挡几下 {@value #SAMPLE_DAMAGE} 点物理伤害。份额、单价、价格倍数按这件现在的同调阶问
 *       {@link IMRUShieldArmor#coverage}，花费照 {@link MRUShield#cost} 算，和挨打时扣的一样。样本是一支没有射手的箭（{@link #samplePhysical}）：
 *       各套都挡物理，箭不靠直接实体也算物理，没有攻击者也就不碰 PvP 的限份额和加价。挨打时先扣身上的储存物品和矩阵容量，实际挡得比这个数多，提示框写「约」。</li>
 * </ul>
 */
public final class ArmorCharge {

    /** 「满电约挡」按多大的一下算 */
    public static final float SAMPLE_DAMAGE = 20F;
    /** {@link #fullChargeHits}：这件不挡样本那样的伤害（或者没有电池） */
    public static final int NOT_SHIELDED = -1;
    /** {@link #fullChargeHits}：挡一下花 0 MRU（单价或价格倍数配成 0），只要存着电就一直挡 */
    public static final int FREE = Integer.MAX_VALUE;

    /**
     * 身上 MRU 护盾盔甲的电量合计。
     *
     * @param stored 存着的 MRU 合计
     * @param max    容量合计
     * @param pieces 算进来的件数（0 = 一件都没穿）
     */
    public record Total(long stored, long max, int pieces) {

        public static final Total NONE = new Total(0L, 0L, 0);

        /** 穿着至少一件、容量大于 0 */
        public boolean any() {
            return pieces > 0 && max > 0L;
        }

        /** 0~1 */
        public float fraction() {
            return max <= 0L ? 0F : (float) Math.max(0D, Math.min(1D, (double) stored / max));
        }

        /** 百分比，向下取整（差一点没满写 99，不到 1% 写 0；真空了看 {@link #stored()}） */
        public int percent() {
            return max <= 0L ? 0 : (int) Math.max(0L, Math.min(100L, stored * 100L / max));
        }
    }

    private ArmorCharge() {
    }

    /** 身上穿在自己部位的 MRU 护盾盔甲（头 / 胸 / 腿 / 靴）合计；一件都没有返回 {@link Total#NONE} */
    public static Total worn(LivingEntity entity) {
        long stored = 0L;
        long max = 0L;
        int pieces = 0;
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            ItemStack stack = MRUShield.wornPiece(entity, type);
            if (stack == null) {
                continue;
            }
            IMRUHandlerItem handler = stack.getCapability(ECCapabilities.MRU_ITEM).orElse(null);
            if (handler == null) {
                continue;
            }
            stored += Math.max(0, handler.getMRU());
            max += Math.max(0, handler.getMaxMRU());
            pieces++;
        }
        return pieces == 0 ? Total.NONE : new Total(stored, max, pieces);
    }

    /** 「满电约挡」用的样本伤害：一支没有射手的箭（原版 {@code arrow} 类型，带 {@code is_projectile}）；世界里查不到这个伤害类型时 null */
    @Nullable
    public static DamageSource samplePhysical(Level level) {
        Optional<Holder.Reference<DamageType>> arrow = level.registryAccess().registry(Registries.DAMAGE_TYPE)
                .flatMap(registry -> registry.getHolder(DamageTypes.ARROW));
        return arrow.map(DamageSource::new).orElse(null);
    }

    /**
     * 这件充满时，光靠它自己存的电能挡几下 {@link #SAMPLE_DAMAGE} 点的 sample：容量 ÷ 一下的花费，向下取整（容量不够付一下时是 0）。
     * 不是护盾盔甲、没有电池、不挡这种伤害（含 {@link MRUShield#excluded} 和「新覆盖面」：样本没有攻击者，挨打时也不会挡）返回 {@link #NOT_SHIELDED}；
     * 一下花 0 MRU 返回 {@link #FREE}。
     */
    public static int fullChargeHits(ItemStack stack, DamageSource sample) {
        if (!(stack.getItem() instanceof IMRUShieldArmor armor) || MRUShield.excluded(sample)) {
            return NOT_SHIELDED;
        }
        IMRUShieldArmor.Coverage coverage = armor.coverage(stack, sample);
        if (coverage == null || coverage.extended() || !(coverage.share() > 0D)) {
            return NOT_SHIELDED;
        }
        int max = stack.getCapability(ECCapabilities.MRU_ITEM).map(IMRUHandlerItem::getMaxMRU).orElse(0);
        if (max <= 0) {
            return NOT_SHIELDED;
        }
        int cost = MRUShield.cost(SAMPLE_DAMAGE, coverage.share(), armor.mruPerDamage(stack), coverage.priceMultiplier());
        return cost <= 0 ? FREE : max / cost;
    }
}
