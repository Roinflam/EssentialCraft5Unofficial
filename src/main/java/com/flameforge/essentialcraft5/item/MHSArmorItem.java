package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IMRUResistanceItem;
import com.flameforge.essentialcraft5.api.MRUResistance;
import com.flameforge.essentialcraft5.api.MRUVision;
import com.flameforge.essentialcraft5.client.render.MHSArmorRenderProperties;
import com.flameforge.essentialcraft5.item.armor.ArmorBonus;
import com.flameforge.essentialcraft5.item.armor.ConfiguredArmorStats;
import com.flameforge.essentialcraft5.item.armor.IMRUShieldArmor;
import com.flameforge.essentialcraft5.item.armor.MRUShield;
import com.flameforge.essentialcraft5.item.attunement.Attunement;
import com.flameforge.essentialcraft5.mru.MRUItemStorage;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECGear;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 魔力防护套装（Magical Hazmat Suit，MHS）。对应原作 ItemArmorEC 的 magicArmorItems[0~3]（helm/chest/legs/boots_magic，aType 0）。
 * <p>
 * 原作数值（ItemsCore L113-129、L466）：
 * <ul>
 *   <li>耐久 512 / 1536 / 1024 / 512（原作 setMaxDamage 覆盖了材料的耐久系数 33）；附魔能力 25；没有修复材料（原作没设）。</li>
 *   <li>MRU 抗性每件 0.25 / 0.125 / 0.10（MRU 腐化 / 腐化辐射 / 海纳尔辐射，原作 ApiCore.setItemResistances），经 {@link IMRUResistanceItem} 接入框架；
 *       提示行由框架自动加，这里不写。穿齐四件 MRU 腐化易伤 0.75。</li>
 *   <li>头盔能看见 MRU（原作 allowItemToSeeMRU）：进 {@code essentialcraft5:mru_vision} 标签（tools/tags/ecgear.json），不用写代码。</li>
 * </ul>
 * 移植改动：
 * <ul>
 *   <li>防护（第二批 D13）：原作靠 1.12 的 ISpecialArmor（每件按护甲值 / 25 吸收，1.20 没有这个接口），改成原版护甲值；
 *       护甲值原作数组按 1.12 槽位下标是 靴 3 / 护腿 8 / 胸甲 6 / 头盔 3（护腿比胸甲高，写反了），照原版钻石改成胸甲 8、护腿 6，总量 20 不变。</li>
 *   <li>原作还对接了别的魔法模组的护目镜、减耗接口，不做联动，不移植。</li>
 *   <li>自定义盔甲模型（原作 ModelArmorEC）改写成 LayerDefinition，见 client/render/MHSArmorModel；
 *       原作靠 CodeChickenLib 在物品栏里画 3D 盔甲当图标，这里用 BEWLR 画（{@link MHSArmorRenderProperties}）。</li>
 *   <li>不进 minecraft:trimmable_armor：纹饰贴图是按原版 64×32 的 UV 排的，套在这个 128×64 的模型上会错位。</li>
 * </ul>
 *
 * <h2>第十三批 13b：矩阵缓冲（batch13.md §4.3、§4.4）</h2>
 * 原来每档都不如钻石套，第十三批补回报，配方一个材料不减：
 * <ul>
 *   <li><b>属性</b>由服务端配置 {@code general.mhs_armor}（{@link ECGear.MHSConfig}）给，修饰符表由 {@link ConfiguredArmorStats} 建（套装名 {@value #STATS_KEY}）：
 *       护甲 3 / 8 / 6 / 3 不变，韧性 0 → 2 / 件，每件 +2 生命。护甲 / 韧性用原版同部位的修饰符 UUID（和以前材料给的是同一个），老存档登录时按新值重加。</li>
 *   <li><b>自带 MRU</b>：0 / 1 / 2 阶每件 2 000 / 4 000 / 8 000（{@code max_mru}），不是储存物品（不会被别的东西当电池抽）。容量按物品上现在的阶现算
 *       （{@link Storage}），升阶时整件复制（{@code ItemStack.copy} 先建能力、后放 NBT），容量照样跟着阶走；老存档从 0 MRU 起，要充一次电。</li>
 *   <li><b>矩阵缓冲</b>（实现 {@link IMRUShieldArmor}，挨打时的处理在 {@link MRUShield}，和虚空、宅男共用）：这件自己存的 MRU &gt; 0 时挡下物理、元素伤害
 *       （同宅男的 {@code PHYSICAL_ELEMENTAL}：元素里不含魔法）里它那一份，份额按这件的阶（二十分之几：0 阶 1 / 2 / 1 / 1 = 25%、1 阶 2 / 3 / 1 / 1 = 35%、
 *       2 阶 2 / 4 / 2 / 1 = 45%），每点 100 MRU（先扣身上储存物品、再扣矩阵容量、最后扣这件自己的）。带 {@code bypasses_armor} 的不挡。
 *       四件合计不到 20 份，所以永远挡不下整下，剩下的照常走原版护甲和韧性（先挡后减：§4.1 表里 D = 20 带电 0 阶实伤 5.2、2 阶 3.4）。</li>
 *   <li><b>2 阶</b>：这件 MRU 腐化抗性 0.25 → 0.5；护盾也挡<b>带攻击者</b>的 {@code mru_discharge}（聚能魔像电弧鞭这类，每点 × {@code discharge_price_multiplier}）。
 *       这是新覆盖面（{@link IMRUShieldArmor.Coverage#extended()}）：{@link MRUShield} 替它守「有攻击者、不是自己、不是玩家」和「不从无尽晶体扣」，
 *       聚集体放电、特斯拉线圈这类没有攻击者的放电照旧打穿。</li>
 *   <li><b>余流</b>（四件都到 1 阶）、脱下夹回生命：见 {@link MHSArmorEvents}。</li>
 *   <li>PvP：份额最多 45%，本来就不到一半，不另限份额、不加价；新覆盖面（放电）对玩家攻击者不生效（{@link MRUShield} 统一守）。</li>
 * </ul>
 * 同调家族 {@code mhs_armor}（最高 2 阶，升阶配方 {@code data/essentialcraft5/recipes/attunement/mhs_*.json}）；每阶 +1 生命由同调框架给（{@code AttunementBonuses}）。
 */
public class MHSArmorItem extends ArmorItem implements IMRUResistanceItem, IMRUShieldArmor, CreativeVariants {

    /** 原作 armorEC3Magic_0（头盔、胸甲、靴子）/ _1（护腿） */
    private static final ResourceLocation TEXTURE_OUTER = EssentialCraft5.id("textures/special/armor/armorec3magic_0.png");
    private static final ResourceLocation TEXTURE_LEGS = EssentialCraft5.id("textures/special/armor/armorec3magic_1.png");

    /** 生命修饰符 UUID 用的套装名（{@link ConfiguredArmorStats#healthModifierId}），和虚空、宅男、审判庭的不重 */
    public static final String STATS_KEY = "mhs_armor";
    /** 四件都到这一阶有余流 */
    public static final int AFTERFLOW_STAGE = 1;
    /** 这一阶起这件挡带攻击者的 MRU 放电、MRU 腐化抗性翻倍 */
    public static final int DISCHARGE_STAGE = 2;
    /** MRU 腐化抗性：原作每件 0.25，2 阶起 0.5 */
    public static final float MRU_CORRUPTION_RESISTANCE = 0.25F;
    public static final float MRU_CORRUPTION_RESISTANCE_ATTUNED = 0.5F;

    private final ConfiguredArmorStats stats;

    public MHSArmorItem(Type type, Properties properties) {
        super(Material.INSTANCE, type, properties);
        this.stats = new ConfiguredArmorStats(type, STATS_KEY, false, ArmorBonus.none());
    }

    /** 盔甲贴图（128×64，UV 照 ModelArmorEC 排）：护腿用 _1，其余用 _0，同原作 getArmorTexture */
    public static ResourceLocation texture(EquipmentSlot slot) {
        return slot == EquipmentSlot.LEGS ? TEXTURE_LEGS : TEXTURE_OUTER;
    }

    // ------------------------------------------------------------------
    // 属性
    // ------------------------------------------------------------------

    /** 这件的属性修饰符（只在自己的部位）：配置给的护甲 / 韧性 / 击退抗性 / 最大生命。不看电量，空、满一样 */
    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != getEquipmentSlot()) {
            return super.getDefaultAttributeModifiers(slot);
        }
        ECGear.MHSConfig.Values values = ECGear.MHSConfig.values();
        // 击退抗性、生命配成 0 也照样放修饰符（数额 0），热改成 0 后脱装不留残余，见 ConfiguredArmorStats
        return stats.modifiers(ConfiguredArmorStats.Piece.of(values.armor(getType()), values.toughness(), values.knockbackResistance(), values.maxHealth()));
    }

    /** 最大生命修饰符的 UUID（四件各一个） */
    public static UUID healthModifierId(Type type) {
        return ConfiguredArmorStats.healthModifierId(type, STATS_KEY);
    }

    // ------------------------------------------------------------------
    // 矩阵缓冲（MRUShield）
    // ------------------------------------------------------------------

    /** 这件这一阶挡的份额（二十分之几） */
    public static int share(Type type, int stage) {
        return ECGear.MHSConfig.values().share(type, stage);
    }

    /** 0 阶挡哪些：物理和元素（元素里不含魔法），同宅男的 {@code PHYSICAL_ELEMENTAL}；{@code bypasses_armor} 不在这里判 */
    @Override
    public boolean shields(ItemStack stack, DamageSource source) {
        return ECComputer.ShieldScope.PHYSICAL_ELEMENTAL.covers(source);
    }

    /** 0 阶的份额（接口要的；按阶的份额在 {@link #coverage} 里） */
    @Override
    public int shieldShare(Type type) {
        return share(type, 0);
    }

    /** 每挡 1 点的单价（护甲破坏者也按它扣）；不随阶变 */
    @Override
    public int mruPerDamage(ItemStack stack) {
        return ECGear.MHSConfig.values().mruPerDamage();
    }

    /**
     * 按这件的阶挡：物理、元素（不带 {@code bypasses_armor}）按这一阶的份额、原价；2 阶起 {@code mru_discharge} 也挡（新覆盖面，见类注释），
     * 别的都不挡。份额配成 0 时返回 0 份，{@link MRUShield} 当不挡。{@code bypasses_invulnerability}、{@code unshieldable} 由 MRUShield 先排除。
     */
    @Override
    public @Nullable Coverage coverage(ItemStack stack, DamageSource source) {
        int stage = Attunement.stage(stack);
        ECGear.MHSConfig.Values values = ECGear.MHSConfig.values();
        int share = values.share(getType(), stage);
        if (!source.is(DamageTypeTags.BYPASSES_ARMOR) && shields(stack, source)) {
            return new Coverage(share, 1D, false);
        }
        if (stage >= DISCHARGE_STAGE && source.is(ECDamageTypes.MRU_DISCHARGE)) {
            return new Coverage(share, values.dischargePriceMultiplier(), true);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // MRU、抗性
    // ------------------------------------------------------------------

    /** 这件现在能存多少 MRU（按物品上的同调阶） */
    public static int capacity(ItemStack stack) {
        return ECGear.MHSConfig.maxMru(Attunement.stage(stack));
    }

    /** 自带 MRU，容量随阶；固定不是储存物品，免得被当成电池给别的东西付钱 */
    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new Storage(stack);
    }

    /**
     * 不可修理：工作台修理配方、砂轮合并两件会丢掉同调和存着的 MRU。磨损了用魔力修复器修（{@link ECTags#MRU_REPAIRABLE}），
     * 或者在铁砧上两件合修（右边那件当材料吃掉）、靠经验修补。
     */
    @Override
    public boolean isRepairable(ItemStack stack) {
        return false;
    }

    @Override
    public float getResistance(ItemStack stack, MRUResistance.Type type) {
        return switch (type) {
            case MRU_CORRUPTION -> Attunement.stage(stack) >= DISCHARGE_STAGE ? MRU_CORRUPTION_RESISTANCE_ATTUNED : MRU_CORRUPTION_RESISTANCE;
            case CORRUPTION_RADIATION -> 0.125F;
            case HOANNA_RADIATION -> 0.10F;
        };
    }

    /** 创造栏放空、满两份（同虚空、宅男套装） */
    @Override
    public List<ItemStack> creativeVariants() {
        return MRUStaffItem.variants(this);
    }

    /** 同调 3 阶起带附魔光泽（batch13.md §3.3.5；这一家族最高 2 阶，实际不会亮） */
    @Override
    public boolean isFoil(ItemStack stack) {
        return super.isFoil(stack) || Attunement.hasGlint(stack);
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return texture(slot).toString();
    }

    /**
     * 注意：这个方法在 Item 的构造器里就被调用（只在客户端、非 datagen），这时 ArmorItem 的字段还没赋值，
     * 所以渲染属性里一律按物品栈现查部位，不读本类字段。
     */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(MHSArmorRenderProperties.INSTANCE);
    }

    // ------------------------------------------------------------------
    // 提示
    // ------------------------------------------------------------------

    /** 份额（二十分之几）写成百分比 */
    public static String percent(int share) {
        return share * 100 / MRUShield.TOTAL_SHARES + "%";
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ECGear.MHSConfig.Values values = ECGear.MHSConfig.values();
        int stage = Attunement.stage(stack);
        int share = values.share(getType(), stage);
        tooltip.add(Component.translatable("tooltip.essentialcraft5.mhs_armor.shield", percent(share), values.mruPerDamage())
                .withStyle(ChatFormatting.DARK_PURPLE));
        if (stage >= DISCHARGE_STAGE) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.mhs_armor.shield.discharge",
                    Math.round(values.mruPerDamage() * values.dischargePriceMultiplier())).withStyle(ChatFormatting.DARK_PURPLE));
        }
        MRUStaffItem.mruTooltip(stack, tooltip);
        if (MRUVision.grantsVision(stack)) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.mru_vision.worn").withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    // ------------------------------------------------------------------
    // 自带 MRU 的能力
    // ------------------------------------------------------------------

    /**
     * 容量按物品上现在的同调阶现算的 MRU 存储（键名同 {@link MRUItemStorage}：mru / balance）。父类的容量传 int 上限只当「不夹」用，
     * 读、写、加都按 {@link #capacity} 夹；取用（{@code extractMRU}）走父类，经这里的 getMRU / setMRU。
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
                return (int) Math.min(Integer.MAX_VALUE, total - max);
            }
            if (doAdd) {
                setMRU((int) total);
            }
            return 0;
        }
    }

    // ------------------------------------------------------------------
    // 材料
    // ------------------------------------------------------------------

    /**
     * 原作 ArmorMaterial「MRUFortified」。第十三批起穿在身上的护甲、韧性由 {@link #getDefaultAttributeModifiers} 按配置给，
     * 这里的护甲值、韧性只剩 {@code ArmorItem.getDefense / getToughness} 读（生物挑盔甲时比较用），取默认配置的数。
     */
    public enum Material implements ArmorMaterial {
        INSTANCE;

        @Override
        public int getDurabilityForType(Type type) {
            return switch (type) {
                case HELMET, BOOTS -> 512;
                case CHESTPLATE -> 1536;
                case LEGGINGS -> 1024;
            };
        }

        @Override
        public int getDefenseForType(Type type) {
            return switch (type) {
                case HELMET, BOOTS -> 3;
                case CHESTPLATE -> 8;
                case LEGGINGS -> 6;
            };
        }

        @Override
        public int getEnchantmentValue() {
            return 25;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_GENERIC;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        /** 只在原版的默认贴图路径和纹饰里用到；贴图由 {@link #getArmorTexture} 给，也不进纹饰标签 */
        @Override
        public String getName() {
            return EssentialCraft5.MODID + ":mhs";
        }

        /** 原作 0；第十三批起每件 2（默认配置的数，穿上时的属性按配置给） */
        @Override
        public float getToughness() {
            return 2F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0F;
        }
    }
}
