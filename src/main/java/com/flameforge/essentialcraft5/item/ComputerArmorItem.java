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
import com.flameforge.essentialcraft5.item.attunement.AttunementStageLines;
import com.flameforge.essentialcraft5.item.module.ArmorModules;
import com.flameforge.essentialcraft5.item.module.IModularArmor;
import com.flameforge.essentialcraft5.item.module.ModuleConfig;
import com.flameforge.essentialcraft5.item.module.ModuleType;
import com.flameforge.essentialcraft5.mru.MRUItemStorage;
import com.flameforge.essentialcraft5.registry.ECComputer;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 宅男套装的一件（原作 ItemComputerArmor，222 行）。注册与配置见 {@link ECComputer}，四件各自的效果、反伤、夜视与冲刺见 {@link ComputerArmorEvents}。
 *
 * <h2>属性（不看电量）</h2>
 * 护甲值、韧性、击退抗性、最大生命、护腿移速按服务端配置 {@code general.computer_armor} 给（{@link ECComputer.Config}），修饰符表由
 * {@link ConfiguredArmorStats} 建（套装名 {@value #STATS_KEY}，和虚空套装的生命 / 移速 UUID 不重）；靴子的登高是 {@link ArmorBonus}。
 * 默认护甲 6 / 16 / 12 / 6、每件韧性 6、击退抗性 0.2（同虚空套装，全套顶到原版上限 30 / 20），每件 +20 生命、胸甲 +40，护腿移速 +0.075。
 * <p>
 * 原作属性是 0、盔甲条按材料画成 70，只有胸甲 +40 生命、护腿 +0.15 移速（L64~76），防护全靠 ISpecialArmor 吸收。这里护甲是真的原版属性，
 * 盔甲条、保护附魔、别的模组的穿甲照常算。
 *
 * <h2>有电挡伤害</h2>
 * 实现 {@link IMRUShieldArmor}，挨打时的处理在 {@link MRUShield}（和虚空套装共用一个监听器）：这件自己存的 MRU &gt; 0 才挡它那一份
 * （{@link #share}：头 3、胸 8、腿 6、靴 3，二十分之几），每点花 {@code mru_per_damage}（默认 250），先扣身上的储存物品；<b>扣不起这件就不挡</b>；
 * 挡哪些看 {@code shield_scope}（默认物理和元素，{@link ECComputer.ShieldScope}），带 {@code bypasses_armor} 的不挡。
 * 原作任意一件有电就把四件的比例都算上、按 {@code damageReduceAmount / 10} 吸收（合计 100%），扣不起电照样吸收（L184~187、L198~204），
 * 只要剩 1 MRU 就永远免疫；原作数组的护腿份额比胸甲高，照魔力防护（batch2 D13）把胸、腿换过来。
 *
 * <h2>MRU、耐久、抗性</h2>
 * 每件存 {@code max_mru}（默认 1 000 000，原作 maxMRU），不是储存物品（同虚空套装，免得被别的东西当电池抽），创造栏放空、满两份。
 * 耐久 16 384（原作 setMaxDamage），穿在玩家身上永远不掉（扣电代替耐久，同虚空套装），穿在别的生物身上照常掉；附魔能力 127、没有修复材料，
 * 不可修理（{@link #isRepairable}：修理配方、砂轮合并两件会吞掉装着的模块）。
 * MRU 抗性每件 1 / 1 / 1（原作 ItemsCore L265~277），四件穿齐对腐化和辐射全免。头盔进 {@code essentialcraft5:mru_vision}（tools/tags/eccomputer.json）。
 * 原作还对接了别的魔法模组的护目镜、减耗接口，不做联动，不移植。
 *
 * <h2>模块槽（第十批 10c，batch10.md §4.3）</h2>
 * 实现 {@link IModularArmor}：槽数读 {@code general.computer_modules.slots_*}（默认头 2 / 胸 2 / 腿 1 / 靴 1，D10 A）；模块存在这件的 NBT 里
 * （{@link ArmorModules#TAG}），在工作台里装、宅男终端里拆和开关，提示框列出装着的模块。生效与否见 {@code item/module/ModuleCache}。
 *
 * <h2>同调（第十三批 13a，batch13.md §4.4）</h2>
 * 同调家族 {@code computer_armor}（最高 5 阶），阶效果在 {@link ComputerAttunement}：槽数按这件的阶加（满 5 阶 3 / 3 / 2 / 2 = 10），
 * 护盾单价按这件的阶打折、2 阶起挡魔法、4 阶起挡一半无视护甲（{@link #coverage}），全套 3 阶矩阵修复、5 阶过载屏障。0 阶和以前一样，
 * 只是攻击者是别的玩家时最多挡一半、单价 ×2（D12 A）。升阶配方在 {@code data/essentialcraft5/recipes/attunement/computer_*.json}。
 */
public class ComputerArmorItem extends ECArmorItem implements IMRUShieldArmor, IMRUResistanceItem, CreativeVariants, IModularArmor,
        AttunementStageLines {

    /** 原版人形盔甲的形状（原作没覆盖盔甲模型）；贴图 128×64，是原版布局的两倍分辨率 */
    public static final ArmorLook LOOK = ArmorLook.vanilla("special/armor/computer_layer_1", "special/armor/computer_layer_2");
    /** 生命 / 移速修饰符 UUID 用的套装名（{@link ConfiguredArmorStats#healthModifierId}） */
    public static final String STATS_KEY = "computer_armor";

    private final ConfiguredArmorStats stats;

    public ComputerArmorItem(Type type, Properties properties, ArmorBonus bonus) {
        super(ECComputer.MATERIAL, type, properties, LOOK, bonus);
        this.stats = new ConfiguredArmorStats(type, STATS_KEY, true, bonus);
    }

    // ------------------------------------------------------------------
    // 挡伤害（MRUShield）
    // ------------------------------------------------------------------

    /** 这一件管的份额（二十分之几）：头 3、胸 8、腿 6、靴 3，合计 20 */
    public static int share(Type type) {
        return switch (type) {
            case HELMET, BOOTS -> 3;
            case CHESTPLATE -> 8;
            case LEGGINGS -> 6;
        };
    }

    /** 0 阶挡哪些：按 {@code shield_scope}（{@code bypasses_armor} 不在这里判，见 {@link #coverage}） */
    @Override
    public boolean shields(ItemStack stack, DamageSource source) {
        return ECComputer.Config.values().shieldScope().covers(source);
    }

    @Override
    public int shieldShare(Type type) {
        return share(type);
    }

    /** 0 阶原价（护甲破坏者按它扣）；同调阶的降价、新覆盖面的加价在 {@link #coverage} 里 */
    @Override
    public int mruPerDamage(ItemStack stack) {
        return ECComputer.Config.values().mruPerDamage();
    }

    /** 按这件的同调阶挡（第十三批 13a，见 {@link ComputerAttunement#coverage}）；0 阶时和原来一样，只多了 PvP 限份额（D12 A） */
    @Override
    public @Nullable Coverage coverage(ItemStack stack, DamageSource source) {
        return ComputerAttunement.coverage(stack, getType(), source);
    }

    // ------------------------------------------------------------------
    // 属性
    // ------------------------------------------------------------------

    /** 这件的属性修饰符（只在自己的部位）：配置给的护甲 / 韧性 / 击退抗性 / 最大生命 / 移速，加上靴子的登高。不看电量 */
    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != getEquipmentSlot()) {
            return super.getDefaultAttributeModifiers(slot);
        }
        ECComputer.Config.Values values = ECComputer.Config.values();
        Type type = getType();
        // 生命、移速配成 0 也照样放修饰符（数额 0），热改成 0 后脱装不留残余，见 ConfiguredArmorStats；没有移速的部位放 0
        return stats.modifiers(new ConfiguredArmorStats.Piece(values.armor(type), values.toughness(), values.knockbackResistance(),
                values.maxHealth(type), values.movementSpeed(type)));
    }

    /** 最大生命修饰符的 UUID（四件各一个） */
    public static UUID healthModifierId(Type type) {
        return ConfiguredArmorStats.healthModifierId(type, STATS_KEY);
    }

    /** 可配置移速修饰符的 UUID（四件都放，只有护腿的数额不是 0） */
    public static UUID speedModifierId(Type type) {
        return ConfiguredArmorStats.speedModifierId(type, STATS_KEY);
    }

    // ------------------------------------------------------------------
    // 耐久、MRU、抗性
    // ------------------------------------------------------------------

    /** 穿在玩家身上不掉耐久（扣电代替耐久）；别的生物照常掉 */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<T> onBroken) {
        return entity instanceof Player ? 0 : amount;
    }

    /**
     * 不可修理。原版修理配方 {@code minecraft:repair_item} 认两件同部位的宅男盔甲，产物是新建的一件（{@code RepairItemRecipe.assemble}），
     * 两件上装着的模块、存的 MRU、非诅咒附魔全没了；砂轮合并两件时也只留上面那件的附魔，下面那件的模块被吞掉。
     * 返回 false 以后修理配方不匹配、砂轮不合并两件、魔力修复器不修它；穿在玩家身上本来就不掉耐久（{@link #damageItem}），没有损失。
     * 铁砧照原版：右边放的那件会被当材料吃掉，这是铁砧本来的规矩。
     */
    @Override
    public boolean isRepairable(ItemStack stack) {
        return false;
    }

    /** 自带 max_mru；固定不是储存物品 */
    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new MRUItemStorage(stack, ECComputer.Config.values().maxMru(), false);
    }

    @Override
    public float getResistance(ItemStack stack, MRUResistance.Type type) {
        return 1F;
    }

    @Override
    public List<ItemStack> creativeVariants() {
        return MRUStaffItem.variants(this);
    }

    /** 模块槽：general.computer_modules.slots_* 加上同调阶给的（胸甲 1 阶、头盔 / 护腿 3 阶、靴子 5 阶各 +1，见 {@link ComputerAttunement#slotBonus}） */
    @Override
    public int moduleSlots(ItemStack stack) {
        Type type = getType();
        return ModuleConfig.values().slots(type) + ComputerAttunement.slotBonus(type, Attunement.stage(stack));
    }

    // ------------------------------------------------------------------
    // 提示
    // ------------------------------------------------------------------

    /** 同调提示框里各阶那一行，数按配置填（{@link ComputerAttunement#stageLine}） */
    @Override
    @Nullable
    public Component attunementStageLine(ItemStack stack, int stage) {
        return ComputerAttunement.stageLine(stage);
    }

    /** 这件的说明键（原作 essentialcraft.txt.computer_*.props，按移植后的效果重写） */
    public static String descriptionKey(Type type) {
        return "tooltip.essentialcraft5.computer_" + type.getName();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        ECComputer.Config.Values values = ECComputer.Config.values();
        Type type = getType();
        if (type == Type.HELMET) {
            tooltip.add(Component.translatable(descriptionKey(type), Component.keybind(ComputerArmorEvents.KEY_NIGHT_VISION))
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        } else {
            tooltip.add(Component.translatable(descriptionKey(type)).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        int stage = Attunement.stage(stack);
        ECComputer.Config.Attuned attuned = ECComputer.Config.attuned();
        int price = attuned.price(values.mruPerDamage(), stage);
        tooltip.add(Component.translatable("tooltip.essentialcraft5.computer_armor.shield." + values.shieldScope().name().toLowerCase(Locale.ROOT),
                price).withStyle(ChatFormatting.DARK_PURPLE));
        if (stage >= ComputerAttunement.MAGIC_STAGE) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.computer_armor.shield.magic",
                    Math.round(price * attuned.magicPriceMultiplier())).withStyle(ChatFormatting.DARK_PURPLE));
        }
        if (stage >= ComputerAttunement.PIERCING_STAGE) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.computer_armor.shield.piercing",
                    Math.round(attuned.piercingShare() * 100D) + "%", Math.round(price * attuned.piercingPriceMultiplier())).withStyle(ChatFormatting.DARK_PURPLE));
        }
        MRUStaffItem.mruTooltip(stack, tooltip);
        moduleTooltip(stack, tooltip);
        tooltip.add(Component.translatable("tooltip.essentialcraft5.computer_armor.fullset").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.computer_armor.fullset.thorns", Math.round(values.thornsRatio() * 100D) + "%")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.essentialcraft5.computer_armor.fullset.boost", Component.keybind(ComputerArmorEvents.KEY_BOOST))
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        if (MRUVision.grantsVision(stack)) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.mru_vision.worn").withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    /** 模块槽「已装 / 格数」，下面每块一行（关着的标「关」） */
    private void moduleTooltip(ItemStack stack, List<Component> tooltip) {
        int slots = ArmorModules.slots(stack);
        List<ArmorModules.Installed> installed = ArmorModules.installed(stack);
        if (slots <= 0 && installed.isEmpty()) {
            return;
        }
        tooltip.add(Component.translatable("tooltip.essentialcraft5.module.slots", installed.size(), slots).withStyle(ChatFormatting.DARK_PURPLE));
        for (ArmorModules.Installed module : installed) {
            ModuleType type = module.type();
            Component name = type != null ? type.displayName() : Component.literal(module.id().toString());
            tooltip.add(Component.translatable(module.on() ? "tooltip.essentialcraft5.module.line" : "tooltip.essentialcraft5.module.line.off", name)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
