package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.IMRUResistanceItem;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.api.MRUVision;
import com.flameforge.essentialcraft5.item.armor.ArmorBonus;
import com.flameforge.essentialcraft5.item.armor.ArmorLook;
import com.flameforge.essentialcraft5.item.armor.ConfiguredArmorStats;
import com.flameforge.essentialcraft5.item.armor.ECArmorItem;
import com.flameforge.essentialcraft5.item.armor.IMRUShieldArmor;
import com.flameforge.essentialcraft5.item.armor.MRUShield;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.mru.MRUItemStorage;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.registry.ECVoidArmor;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 虚空套装的一件（原作 ItemArmorEC，aType 1：magicArmorItems[4~7]）。注册与配置见 {@link ECVoidArmor}，挨打时的处理见 {@link VoidArmorEvents}。
 *
 * <h2>属性（不看电量）</h2>
 * 护甲值、韧性、击退抗性、最大生命按服务端配置 {@code general.void_armor} 给（{@link ECVoidArmor.Config}，默认下界合金套的两倍、每件 +20 生命）。
 * 修饰符表由 {@link ConfiguredArmorStats} 建（第八批从这里抽出去，宅男、审判庭套装共用）：护甲 / 韧性 / 击退抗性用原版同部位的修饰符 UUID，
 * 别的模组按 UUID 认得出这是盔甲本身的数值；生命用按部位固定生成的 UUID（{@link #healthModifierId}，和第七批一样）。
 * 靴子的移速、登高是 {@link ArmorBonus}。同一套数值只建一次修饰符表，配置变了才重建。
 * <p>
 * 原作 getAttributeModifiers 不调父类，护甲值属性是 0，盔甲条却按材料画出 20（L86~107、L210~213）；只有胸甲 +20 生命。
 * 这里护甲是真的原版属性，盔甲条如实。
 *
 * <h2>MRU</h2>
 * 每件自带的 MRU 按服务端配置 {@code general.void_armor.max_mru}（默认 5 万，原作 5 000，batch13.md D13），同调 1 阶起翻倍（{@link #capacity}）；
 * 不是储存物品（不会被别的物品当电池抽）；创造栏放空、满两份，提示框写存量。容量每次现读（{@link Storage}），升阶、改配置当场生效，不用换一件新的。
 * 有电时挡物理伤害：这件自己存的 MRU &gt; 0 就挡它那一份（{@link #share}），每点花 {@code mru_per_damage}（默认 400，原作 800），0 阶花费 {@link #blockCost}。
 * 挨打时的处理在 {@link MRUShield}（本类实现 {@link IMRUShieldArmor}，第八批起和宅男套装共用一个监听器），
 * 闪避、减摔、回击、相位这些虚空套装自己的效果在 {@link VoidArmorEvents}。
 *
 * <h2>同调阶（第十三批 13b，batch13.md §4.4；按这一件自己的阶算的几条在本类）</h2>
 * <ul>
 *   <li>1 阶起：这件能存的 MRU ×{@value #CAPACITY_MULTIPLIER}（默认 10 万）。</li>
 *   <li>全套 2 阶起（四件最低的阶）：海纳尔辐射的易伤在算完全部抗性以后再 ×{@value #RADIATION_SET_MULTIPLIER}（{@link #radiationMultiplier}，
 *       登记成 {@link MRUResistance} 的易伤倍率）。每件的抗性照旧 0.75，不往加法里加：矩阵五阶、集齐古卷、神秘饰品都是在同一个减项里相加的，
 *       每件加到 0.875 的话和它们一叠就夹到 0，2 阶全套就成了全免；乘在最后，虚空本身永远不会把易伤压到 0（batch13.md §4.4「不全免」）。</li>
 *   <li>3 阶起：每点单价 ×{@value #DISCOUNT}（默认 400 → 350）；护盾也挡元素伤害（火、爆炸、闪电，魔法照旧不挡），这种每点再 ×{@value #ELEMENTAL_PRICE_MULTIPLIER}。
 *       元素不算「新覆盖面」（{@link IMRUShieldArmor.Coverage#extended()} 为假）：宅男套装 0 阶就挡元素，口径和它一样。
 *       原版的着火持续伤害（{@code on_fire}）和细雪冰冻（{@code freeze}，原版 {@code is_freezing} 里唯一的一种）都带 {@code bypasses_armor}，照旧不挡；
 *       别的模组不带 {@code bypasses_armor} 的冰冻伤害才挡得到。</li>
 * </ul>
 * 护甲破坏者按 0 阶原价扣（{@link #mruPerDamage}）。闪避几率、回击、滞涩、相位、摔落、海纳尔环境抗性见 {@link VoidArmorEvents}。
 *
 * <h2>耐久</h2>
 * 穿在玩家身上永远不掉（原作玩家穿着时 damageArmor 只扣 MRU、从不扣耐久；没挡下的那部分伤害走原版护甲时也不扣）；穿在别的生物身上照常掉。
 * 耐久本身得留着：{@code ItemStack.hurtAndBreak} 只在物品有耐久时才问 {@link #damageItem}。
 * <p>
 * MRU 抗性每件 1 / 0.5 / 0.75（MRU 腐化 / 腐化辐射 / 海纳尔辐射，原作 ItemsCore L136~148），四件穿齐 MRU 腐化易伤 0、海纳尔辐射 0.25，不看电量；
 * 各阶每件的数都一样，全套 2 阶的减半是乘在最后的倍率（见上）。
 * 护腿原作写着「能相位偏移穿过怪物」，代码里没有任何实现，不做，也不写这行提示。
 */
public class VoidArmorItem extends ECArmorItem implements IMRUShieldArmor, IMRUResistanceItem, CreativeVariants {

    /** 四件的份额加起来是这么多（原作 damageReduceAmount / 20） */
    public static final int TOTAL_SHARES = MRUShield.TOTAL_SHARES;
    public static final ArmorLook LOOK = ArmorLook.modelArmorEC("special/armor/armorec3void_0", "special/armor/armorec3void_1");
    /** 生命修饰符 UUID 用的套装名（{@link ConfiguredArmorStats#healthModifierId}） */
    public static final String STATS_KEY = "void_armor";

    /** 几阶起能存的 MRU 翻倍 */
    public static final int CAPACITY_STAGE = 1;
    /** 1 阶起容量乘几（默认 5 万 → 10 万） */
    public static final int CAPACITY_MULTIPLIER = 2;
    /** 全套几阶起海纳尔辐射的易伤再乘 {@value #RADIATION_SET_MULTIPLIER} */
    public static final int RADIATION_STAGE = 2;
    /** 每件的海纳尔辐射抗性：各阶都照原作 0.75 */
    public static final float HOANNA_RADIATION = 0.75F;
    /** 全套 2 阶起海纳尔辐射易伤最后乘几（只穿这一套时 0.25 → 0.125） */
    public static final float RADIATION_SET_MULTIPLIER = 0.5F;
    /** 几阶起单价打折 */
    public static final int DISCOUNT_STAGE = 3;
    /** 几阶起护盾也挡元素 */
    public static final int ELEMENTAL_STAGE = 3;
    /** 3 阶起每点单价乘几（400 → 350） */
    public static final double DISCOUNT = 0.875D;
    /** 元素伤害每点的价钱再乘几 */
    public static final double ELEMENTAL_PRICE_MULTIPLIER = 2D;

    private final ConfiguredArmorStats stats;

    public VoidArmorItem(Type type, Properties properties, ArmorBonus bonus) {
        super(ECVoidArmor.MATERIAL, type, properties, LOOK, bonus);
        this.stats = new ConfiguredArmorStats(type, STATS_KEY, false, bonus);
    }

    // ------------------------------------------------------------------
    // 份额与花费（原作 ISpecialArmor）
    // ------------------------------------------------------------------

    /**
     * 这一件挡物理伤害时管的份额（二十分之几）：头 3、胸 8、腿 6、靴 3，合计 20。
     * 原作数组按 1.12 的槽位下标是靴 3 / 腿 8 / 胸 6 / 头 3（护腿比胸甲高），照魔力防护（batch2 D13）把胸、腿换过来。
     */
    public static int share(Type type) {
        return switch (type) {
            case HELMET, BOOTS -> 3;
            case CHESTPLATE -> 8;
            case LEGGINGS -> 6;
        };
    }

    /**
     * 0 阶挡下 damage 里这一件那一份要花的 MRU：max(1, ⌊damage × 份额⌋) × {@code mru_per_damage}（默认 400）。
     * 原作 Forge 1.12 的 ISpecialArmor 按 {@code (int) max(1, 吸收量)} 调 damageArmor，再乘单价，所以每件至少按 1 点算（默认 1 点伤害全套 1 600）。
     * 极大的伤害夹到 int 上限（扣不起就不挡，带着无尽 MRU 晶体时照付）。
     */
    public static int blockCost(float damage, Type type) {
        return MRUShield.cost(damage, share(type), ECVoidArmor.Config.shield().mruPerDamage());
    }

    /** 这一件能存多少 MRU：{@code max_mru}，同调 1 阶起 ×{@value #CAPACITY_MULTIPLIER}（夹到 int 上限） */
    public static int capacity(ItemStack stack) {
        long base = ECVoidArmor.Config.shield().maxMru();
        long capacity = Attunement.stage(stack) >= CAPACITY_STAGE ? base * CAPACITY_MULTIPLIER : base;
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    /** 这一阶的单价倍数：3 阶起 {@value #DISCOUNT}，之前 1 */
    public static double priceMultiplier(int stage) {
        return stage >= DISCOUNT_STAGE ? DISCOUNT : 1D;
    }

    /** 这一件挡物理伤害时每点的单价（0 阶原价 × 阶的倍数） */
    public static int price(ItemStack stack) {
        return roundPrice(priceMultiplier(Attunement.stage(stack)));
    }

    /**
     * 这一件 3 阶起挡元素伤害时每点的单价：0 阶原价 × 阶的倍数 × {@value #ELEMENTAL_PRICE_MULTIPLIER}，只取整一次，
     * 和 {@link MRUShield#cost} 实际扣费的算式一样（先把物理单价取整再翻倍，单价配成奇数时会比实扣多 1）。
     */
    public static int elementalPrice(ItemStack stack) {
        return roundPrice(priceMultiplier(Attunement.stage(stack)) * ELEMENTAL_PRICE_MULTIPLIER);
    }

    private static int roundPrice(double multiplier) {
        long price = Math.round(ECVoidArmor.Config.shield().mruPerDamage() * multiplier);
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, price));
    }

    /** 只挡物理伤害（第七批 D1：{@link ECDamageKinds#isPhysical}；{@code bypasses_armor} 在 {@link #coverage} 里排除） */
    @Override
    public boolean shields(ItemStack stack, DamageSource source) {
        return ECDamageKinds.isPhysical(source);
    }

    @Override
    public int shieldShare(Type type) {
        return share(type);
    }

    /** 0 阶原价（护甲破坏者按它扣）；同调 3 阶的折扣、元素的加价在 {@link #coverage} 里 */
    @Override
    public int mruPerDamage(ItemStack stack) {
        return ECVoidArmor.Config.shield().mruPerDamage();
    }

    /**
     * 按这一件的同调阶挡：带 {@code bypasses_armor} 的不挡；物理伤害照原份额挡，单价按阶打折（0~2 阶原价，和第十三批以前一样）；
     * 3 阶起元素伤害（不算魔法的火、爆炸、闪电、冰冻）也照原份额挡，单价再 ×{@value #ELEMENTAL_PRICE_MULTIPLIER}。
     * 都不是「新覆盖面」：照原来的规矩，不要求攻击者、无尽 MRU 晶体照付。
     */
    @Override
    public @Nullable Coverage coverage(ItemStack stack, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return null;
        }
        int stage = Attunement.stage(stack);
        double price = priceMultiplier(stage);
        int share = share(getType());
        if (ECDamageKinds.isPhysical(source)) {
            return new Coverage(share, price, false);
        }
        if (stage >= ELEMENTAL_STAGE && ECDamageKinds.isElemental(source) && !ECDamageKinds.isMagic(source)) {
            return new Coverage(share, price * ELEMENTAL_PRICE_MULTIPLIER, false);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 属性
    // ------------------------------------------------------------------

    /** 这件的属性修饰符（只在自己的部位）：配置给的护甲 / 韧性 / 击退抗性 / 最大生命，加上靴子的移速、登高。不看电量，空、满两份一样 */
    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != getEquipmentSlot()) {
            return super.getDefaultAttributeModifiers(slot);
        }
        ECVoidArmor.Config.Values values = ECVoidArmor.Config.values();
        // 击退抗性、生命配成 0 也照样放修饰符（数额 0），热改成 0 后脱装不留残余，见 ConfiguredArmorStats
        return stats.modifiers(ConfiguredArmorStats.Piece.of(values.armor(getType()), values.toughness(), values.knockbackResistance(),
                values.maxHealth()));
    }

    /** 最大生命修饰符的 UUID：按部位固定（四件各一个，能叠加；换装时原版按 UUID 去掉旧的） */
    public static UUID healthModifierId(Type type) {
        return ConfiguredArmorStats.healthModifierId(type, STATS_KEY);
    }

    // ------------------------------------------------------------------
    // 耐久、MRU、抗性
    // ------------------------------------------------------------------

    /** 穿在玩家身上不掉耐久（原作只扣 MRU）；别的生物照常掉 */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<T> onBroken) {
        return entity instanceof Player ? 0 : amount;
    }

    /** 不可修理：工作台修理配方、砂轮合并两件会丢掉同调和存着的 MRU；魔力修复器照样修（{@link ECTags#MRU_REPAIRABLE}） */
    @Override
    public boolean isRepairable(ItemStack stack) {
        return false;
    }

    /** 自带 MRU（{@link #capacity}）；固定不是储存物品，免得被当成电池给别的东西付钱 */
    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new Storage(stack);
    }

    @Override
    public float getResistance(ItemStack stack, MRUResistance.Type type) {
        return switch (type) {
            case MRU_CORRUPTION -> 1F;
            case CORRUPTION_RADIATION -> 0.5F;
            case HOANNA_RADIATION -> HOANNA_RADIATION;
        };
    }

    /**
     * {@link MRUResistance} 的易伤倍率来源（{@code registry/ECVoidArmor.init} 登记）：四件虚空都穿着、最低的那件到了 {@value #RADIATION_STAGE} 阶时，
     * 海纳尔辐射的易伤 ×{@value #RADIATION_SET_MULTIPLIER}；别的种类、没穿齐、阶不够都是 1。
     */
    public static float radiationMultiplier(Player player, MRUResistance.Type type) {
        if (type != MRUResistance.Type.HOANNA_RADIATION) {
            return 1F;
        }
        return VoidArmorEvents.fullSetStage(player) >= RADIATION_STAGE ? RADIATION_SET_MULTIPLIER : 1F;
    }

    @Override
    public List<ItemStack> creativeVariants() {
        return MRUStaffItem.variants(this);
    }

    /**
     * 容量跟着这件的同调阶和配置走的 MRU 存储（NBT 键和 {@link MRUItemStorage} 一样）。
     * 能力在物品建出来时就挂上，那时候拿不准阶（{@code ItemStack.copy} 先建能力、后抄 NBT；工作台升阶的产物就是复制出来的），
     * 所以容量每次现读；读存量时按现在的容量夹，配置改小以后多出来的读不到，下次写入时截断。
     */
    static final class Storage extends MRUItemStorage {

        private final ItemStack stack;

        Storage(ItemStack stack) {
            super(stack, Integer.MAX_VALUE, false);
            this.stack = stack;
        }

        @Override
        public int getMaxMRU() {
            return capacity(stack);
        }

        @Override
        public int getMRU() {
            return Math.min(super.getMRU(), getMaxMRU());
        }

        @Override
        public void setMRU(int amount) {
            super.setMRU(Mth.clamp(amount, 0, getMaxMRU()));
        }

        @Override
        public int addMRU(int amount, boolean doAdd) {
            if (amount <= 0) {
                return amount;
            }
            int max = getMaxMRU();
            long total = (long) getMRU() + amount;
            if (total >= max) {
                if (doAdd) {
                    setMRU(max);
                }
                return (int) (total - max);
            }
            if (doAdd) {
                setMRU((int) total);
            }
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // 提示
    // ------------------------------------------------------------------

    /** 这件的说明键（原作 setDescription）；护腿没有（原作写的「穿过怪物」没有实现）。胸甲带闪避几率、靴子带减摔比例当参数 */
    @Nullable
    public static String descriptionKey(Type type) {
        return switch (type) {
            case HELMET -> "tooltip.essentialcraft5.void_helmet";
            case CHESTPLATE -> "tooltip.essentialcraft5.void_chestplate";
            case LEGGINGS -> null;
            case BOOTS -> "tooltip.essentialcraft5.void_boots";
        };
    }

    /** 0.25 → 「25%」 */
    static String percent(double value) {
        return Math.round(value * 100D) + "%";
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        Type type = getType();
        int stage = Attunement.stage(stack);
        String description = descriptionKey(type);
        if (description != null) {
            Component line = switch (type) {
                case CHESTPLATE -> Component.translatable(description, percent(VoidArmorEvents.dodgeChance(stage)));
                case BOOTS -> Component.translatable(description, percent(VoidArmorEvents.fallReduction(stage)));
                default -> Component.translatable(description);
            };
            tooltip.add(line.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        int price = price(stack);
        tooltip.add(Component.translatable("tooltip.essentialcraft5.void_armor.shield", price).withStyle(ChatFormatting.DARK_PURPLE));
        if (stage >= ELEMENTAL_STAGE) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.void_armor.shield.elemental", elementalPrice(stack))
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        MRUStaffItem.mruTooltip(stack, tooltip);
        // 全套那几行写 0 阶的数：全套的阶要看另外三件，这里拿不到玩家；各阶加强后的数在同调那几行（AttunementTooltips）里逐阶列出
        tooltip.add(Component.translatable("tooltip.essentialcraft5.void_armor.fullset").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.void_armor.fullset.riposte",
                percent(VoidArmorEvents.RIPOSTE_BONUS), VoidArmorEvents.RIPOSTE_WINDOW / 20).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.void_armor.fullset.ward", percent(VoidArmorEvents.WARD_BASE))
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        if (MRUVision.grantsVision(stack)) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.mru_vision.worn").withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
