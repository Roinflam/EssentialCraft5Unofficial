package com.flameforge.essentialcraft5.item.attunement;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.ForgedElementalSwordItem;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Multimap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.ToDoubleBiFunction;

/**
 * 每阶通用加成：都是属性修饰符，提示框「穿在身上时 / 在主手时」一栏照原版显示。
 * <ul>
 *   <li><b>盔甲</b>：每阶每件加最大生命（每套的 {@code base_stats.health_per_stage}，−1 沿用 {@code armor.health_per_stage}，默认魔力防护、风之 2，
 *       虚空、审判庭、宅男 3）；护甲、韧性、击退抗性按 {@link ArmorBaseStats} 的阶表涨；魔力防护的移速、风之的攻速词条每件给四分之一，
 *       四件合计是词条表里的数（{@link ArmorGrowth#pieceSpeed}）。</li>
 *   <li><b>刀剑</b>（{@link AttunementFamily.Line#BLADE}，主手）：每阶的攻击伤害直接加在物品本体的攻击那一条上，提示框还是一行「N 攻击伤害」。
 *       数额见 {@link #bladeAttackBonus}。这一份不再进增伤池；总闸改成管「属性栏这份 × 池子」的乘积（{@code GearCombatEvents.factor}），
 *       所以打首领、打玩家的上限和搬过来之前一样。枪的属性栏没有伤害这一行，每阶增伤照旧在池里（{@code attunement_stage}）。</li>
 * </ul>
 * 走 {@link ItemAttributeModifierEvent}：原版换装时照常加减修饰符，重登时被夹掉的生命由 {@code item/armor/ArmorHealthRestore} 补回。家族被关掉时照给（不收回已给的东西）。
 * <p>
 * <b>并进物品自己的那一条</b>：物品本来就有同一属性、同一运算的修饰符时，把那一条换成数额相加的一条，UUID 和名字照用物品自己的，
 * 提示框里只列一行合计。物品没有这一条时（比如数据包往家族标签里加了别的装备）才用下面按槽位固定的 UUID 另加一条，会另起一行。
 * 用哪个 UUID 只看物品种类（配置给的数额为 0 的那几条也照放），同一件升阶、换装前后都是同一个 UUID，原版脱装时按 UUID 去掉就不会留下残余；
 * 改配置以后下一次取属性就是新数，同样不留残余；已经穿在身上、拿在手里的，服务端配置重载时由 {@link ConfigRefresh} 摘下重挂一遍。
 * <p>
 * 盔甲那几种只在盔甲槽给（数据包往盔甲家族里加的非盔甲物品拿在手里不给）。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class AttunementBonuses {

    private static final UUID[] HEALTH_IDS = ids(0x01);
    private static final UUID[] MOVE_IDS = ids(0x11);
    private static final UUID[] ATTACK_SPEED_IDS = ids(0x21);
    private static final UUID[] ARMOR_IDS = ids(0x31);
    private static final UUID[] TOUGHNESS_IDS = ids(0x41);
    private static final UUID[] KNOCKBACK_IDS = ids(0x51);
    /** 刀剑物品自己没有攻击那一条时另加的那一条 */
    private static final UUID BLADE_ATTACK_ID = UUID.fromString("5b1f3a0e-2c7d-4a61-9d1e-7a3c0e5f1b61");
    /**
     * 刀剑那条攻击修饰符（合并后的那个对象）里同调加了多少。按对象身份比较的弱键表（修饰符按 UUID 判等，不能用 WeakHashMap）：
     * 原版换装时挂到玩家身上的就是事件里造出来的这个对象，总闸据此按「身上实际挂着的」折算（{@link #appliedBladeFactor}）。
     */
    private static final Map<AttributeModifier, Double> BLADE_SHARE = new MapMaker().weakKeys().makeMap();

    /**
     * 一种同调加成：属性、运算、物品自己没有同类修饰符时用的 UUID（盔甲按槽位 0~3 各一个）和名字、这一件这一阶给多少（stack, stage）。
     * 数额不大于 0 时不给。
     */
    record Bonus(Supplier<Attribute> attribute, AttributeModifier.Operation operation, UUID[] ownIds, String name,
                 ToDoubleBiFunction<ItemStack, Integer> amount) {
    }

    /** 盔甲的几种（按这个顺序给） */
    static final List<Bonus> ARMOR_BONUSES = List.of(
            new Bonus(() -> Attributes.MAX_HEALTH, AttributeModifier.Operation.ADDITION, HEALTH_IDS, "Attunement health",
                    (stack, stage) -> armorFamily(stack) == null ? 0D : ArmorBaseStats.healthPerStage(armorFamily(stack)) * stage),
            new Bonus(() -> Attributes.ARMOR, AttributeModifier.Operation.ADDITION, ARMOR_IDS, "Attunement armor",
                    (stack, stage) -> armorFamily(stack) == null ? 0D
                            : ArmorBaseStats.armor(armorFamily(stack), AttunementFamily.slotOf(stack, AttunementFamily.Kind.ARMOR), stage)),
            new Bonus(() -> Attributes.ARMOR_TOUGHNESS, AttributeModifier.Operation.ADDITION, TOUGHNESS_IDS, "Attunement armor toughness",
                    (stack, stage) -> armorFamily(stack) == null ? 0D : ArmorBaseStats.toughness(armorFamily(stack), stage)),
            new Bonus(() -> Attributes.KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADDITION, KNOCKBACK_IDS, "Attunement knockback resistance",
                    (stack, stage) -> armorFamily(stack) == null ? 0D : ArmorBaseStats.knockback(armorFamily(stack), stage)),
            // 移速、攻速词条每件给四分之一（MULTIPLY_BASE 相加，四件合计 = 词条表里的数），见 ArmorGrowth
            new Bonus(() -> Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.MULTIPLY_BASE, MOVE_IDS, "Attunement movement speed",
                    (stack, stage) -> ArmorGrowth.pieceSpeed(stack, GearStat.MOVE_SPEED)),
            new Bonus(() -> Attributes.ATTACK_SPEED, AttributeModifier.Operation.MULTIPLY_BASE, ATTACK_SPEED_IDS, "Attunement attack speed",
                    (stack, stage) -> ArmorGrowth.pieceSpeed(stack, GearStat.ATTACK_SPEED)));

    private AttunementBonuses() {
    }

    private static UUID[] ids(int first) {
        UUID[] out = new UUID[4];
        for (int i = 0; i < 4; i++) {
            out[i] = UUID.fromString(String.format("5b1f3a0e-2c7d-4a61-9d1e-7a3c0e5f1b%02x", first + i));
        }
        return out;
    }

    @Nullable
    private static AttunementFamily armorFamily(ItemStack stack) {
        AttunementFamily family = AttunementFamily.of(stack);
        return family != null && family.kind() == AttunementFamily.Kind.ARMOR ? family : null;
    }

    @SubscribeEvent
    public static void onAttunementAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        int stage = Attunement.stage(stack);
        if (stage <= 0) {
            return;
        }
        AttunementFamily family = AttunementFamily.of(stack);
        if (family == null) {
            return;
        }
        EquipmentSlot slot = event.getSlotType();
        if (AttunementFamily.slotOf(stack, family.kind()) != slot) {
            return;
        }
        if (family.kind() == AttunementFamily.Kind.ARMOR) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) {
                return;
            }
            int index = slot.getIndex() & 3;
            for (Bonus bonus : ARMOR_BONUSES) {
                grant(event, bonus.attribute().get(), bonus.operation(), bonus.ownIds()[index], bonus.name(), bonus.amount().applyAsDouble(stack, stage), null);
            }
        } else if (family.line() == AttunementFamily.Line.BLADE && slot == EquipmentSlot.MAINHAND) {
            double base = ownAttack(event.getOriginalModifiers().get(Attributes.ATTACK_DAMAGE));
            double bonus = bladeAttackBonus(family, stage, base);
            AttributeModifier merged = grant(event, Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADDITION, BLADE_ATTACK_ID, "Attunement attack damage",
                    bonus, ForgedElementalSwordItem.ATTACK_MODIFIER_ID);
            if (merged != null) {
                BLADE_SHARE.put(merged, bonus);
            }
        }
    }

    // ------------------------------------------------------------------
    // 刀剑的攻击
    // ------------------------------------------------------------------

    /**
     * 刀剑 stage 阶时属性栏多出的攻击伤害：每阶 = max(1, (1 + base) × 比例)，乘阶数后取到 0.5。
     * base 是物品自己那条攻击修饰符的数额（不含玩家基础的 1 点），1 + base 就是提示框原本显示的「N 攻击伤害」；
     * 比例是 {@code verdict_blade.attack_per_stage} / {@code ember_sword.attack_per_stage}。每阶保底 1 点，低攻击的剑升阶也看得出来。
     * 取到 0.5 放在这里（不改物品按整数攻击缓存的属性表）。
     */
    public static double bladeAttackBonus(AttunementFamily family, int stage, double base) {
        if (stage <= 0) {
            return 0D;
        }
        double ratio = Math.max(0D, GearGrowthConfig.bladeAttackPerStage(family));
        if (!(ratio > 0D)) {
            return 0D;
        }
        double perStage = Math.max(1D, (1D + Math.max(0D, base)) * ratio);
        return Math.round(perStage * stage * 2D) / 2D;
    }

    /** 这把刀剑现在属性栏多出的攻击伤害（不是同调刀剑、0 阶是 0） */
    public static double bladeAttackBonus(ItemStack stack) {
        AttunementFamily family = AttunementFamily.of(stack);
        if (family == null || family.kind() != AttunementFamily.Kind.WEAPON || family.line() != AttunementFamily.Line.BLADE) {
            return 0D;
        }
        return bladeAttackBonus(family, Attunement.stage(stack), bladeBaseAttack(stack));
    }

    /** 物品自己（不经过属性事件）的主手攻击那一条的数额 */
    public static double bladeBaseAttack(ItemStack stack) {
        return ownAttack(stack.getItem().getAttributeModifiers(EquipmentSlot.MAINHAND, stack).get(Attributes.ATTACK_DAMAGE));
    }

    /**
     * 属性栏这份同调攻击是几倍：g = (1 + base + 加成) ÷ (1 + base)，不是同调刀剑是 1。总闸按它把池子换成管乘积。
     * 按物品自己算：玩家身上别的加法攻击（力量药水、别的饰品）会让真实占比比 g 小，总闸按 g 折算时偏保守（被夹时略少打一点），可以接受。
     */
    public static double bladeAttackFactor(ItemStack stack) {
        double bonus = bladeAttackBonus(stack);
        if (!(bonus > 0D)) {
            return 1D;
        }
        double base = 1D + Math.max(0D, bladeBaseAttack(stack));
        return (base + bonus) / base;
    }

    /**
     * 按玩家身上现在挂着的攻击修饰符算的 g：本体那条（或另加的那条）数额 A 里同调占 b 时 g = (1 + A) ÷ (1 + A − b)，
     * 另加的那条 A = b。身上没挂同调刀剑的攻击是 1。和 {@link #bladeAttackFactor} 同一个口径（玩家基础攻击 1 点 + 物品那条）。
     */
    public static double appliedBladeFactor(Player player) {
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) {
            return 1D;
        }
        double g = 1D;
        for (UUID id : new UUID[]{ForgedElementalSwordItem.ATTACK_MODIFIER_ID, BLADE_ATTACK_ID}) {
            AttributeModifier modifier = attack.getModifier(id);
            Double share = modifier == null ? null : BLADE_SHARE.get(modifier);
            if (share == null || !(share > 0D)) {
                continue;
            }
            double rest = 1D + modifier.getAmount() - share;
            if (rest > 0D) {
                g = Math.max(g, (1D + modifier.getAmount()) / rest);
            }
        }
        return g;
    }

    /** 攻击修饰符里物品本体那一条（{@link ForgedElementalSwordItem#ATTACK_MODIFIER_ID}，没有就取第一条加法）的数额；都没有是 0 */
    private static double ownAttack(Collection<AttributeModifier> modifiers) {
        AttributeModifier own = null;
        for (AttributeModifier modifier : modifiers) {
            if (modifier.getOperation() != AttributeModifier.Operation.ADDITION) {
                continue;
            }
            if (modifier.getId().equals(ForgedElementalSwordItem.ATTACK_MODIFIER_ID)) {
                return modifier.getAmount();
            }
            if (own == null) {
                own = modifier;
            }
        }
        return own == null ? 0D : own.getAmount();
    }

    // ------------------------------------------------------------------
    // 并进去
    // ------------------------------------------------------------------

    /**
     * 给一条加成：物品自己有同一属性、同一运算的修饰符就并进去（{@link #mergeTarget}），否则用 ownId 另加一条。
     * amount 不大于 0 时什么都不做。返回加进表里的那一条（没加是 null）。
     */
    @Nullable
    static AttributeModifier grant(ItemAttributeModifierEvent event, Attribute attribute, AttributeModifier.Operation operation, UUID ownId, String name, double amount,
                      @Nullable UUID preferred) {
        if (!(amount > 0D)) {
            return null;
        }
        AttributeModifier base = mergeTarget(event, attribute, operation, preferred);
        if (base == null) {
            AttributeModifier added = new AttributeModifier(ownId, name, amount, operation);
            event.addModifier(attribute, added);
            return added;
        }
        // 修饰符按 UUID 判等：先去掉旧的再加回同一个 UUID 的合计。
        // 事件改过的表是按哈希排的，提示框里的行序会和 0 阶时不同（比如攻速跑到攻击伤害上面），1 阶起各阶一致，没法从这个事件里控制
        event.removeModifier(attribute, base);
        AttributeModifier merged = new AttributeModifier(base.getId(), base.getName(), base.getAmount() + amount, operation);
        event.addModifier(attribute, merged);
        return merged;
    }

    /**
     * 物品本来（事件发出时原表里）就有的、同一属性同一运算的修饰符，取它现在在表里的样子（前面的监听器可能已经按同一个 UUID 换过数）；
     * 被前面的监听器去掉了、或者原表里没有，返回 null。有几条时优先 UUID 等于 preferred 的那条，否则并进第一条。
     */
    @Nullable
    static AttributeModifier mergeTarget(ItemAttributeModifierEvent event, Attribute attribute, AttributeModifier.Operation operation, @Nullable UUID preferred) {
        AttributeModifier first = null;
        for (AttributeModifier original : event.getOriginalModifiers().get(attribute)) {
            if (original.getOperation() != operation) {
                continue;
            }
            AttributeModifier current = current(event, attribute, operation, original.getId());
            if (current == null) {
                continue;
            }
            if (preferred != null && preferred.equals(original.getId())) {
                return current;
            }
            if (first == null) {
                first = current;
            }
        }
        return first;
    }

    @Nullable
    private static AttributeModifier current(ItemAttributeModifierEvent event, Attribute attribute, AttributeModifier.Operation operation, UUID id) {
        Multimap<Attribute, AttributeModifier> modifiers = event.getModifiers();
        for (AttributeModifier current : modifiers.get(attribute)) {
            if (current.getId().equals(id) && current.getOperation() == operation) {
                return current;
            }
        }
        return null;
    }

    /**
     * 服务端配置重载后，把在线玩家身上每个槽位的修饰符摘下重挂一遍（原版只在物品变了时才换，改配置不改物品，不然要脱了再穿才生效）。
     * 重载事件可能在文件监视线程上发，回主线程做；生命上限变小时把当前生命夹到上限。
     */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ConfigRefresh {
        private ConfigRefresh() {
        }

        @SubscribeEvent
        public static void onAttunementBonusConfigReloading(ModConfigEvent.Reloading event) {
            ModConfig config = event.getConfig();
            if (config.getType() != ModConfig.Type.SERVER || !EssentialCraft5.MODID.equals(config.getModId())) {
                return;
            }
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.execute(() -> server.getPlayerList().getPlayers().forEach(AttunementBonuses::reapply));
            }
        }
    }

    /** 把玩家身上已同调的装备的修饰符重挂一遍（{@link ConfigRefresh}，测试也调） */
    public static void reapply(ServerPlayer player) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || Attunement.stage(stack) <= 0 || AttunementFamily.of(stack) == null) {
                continue;
            }
            Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(slot);
            player.getAttributes().removeAttributeModifiers(modifiers);
            player.getAttributes().addTransientAttributeModifiers(modifiers);
        }
        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }
}
