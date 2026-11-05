package com.flameforge.essentialcraft5.item.attunement;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 每阶通用加成（batch13.md §3.3.5）：盔甲每阶每件 +{@code health_per_stage} 最大生命（宅男 5 阶四件共 +20），
 * 武器每阶攻击 ×(1 + {@code damage_per_stage} × 阶)。只做小数，主要的成长在各套自己的阶效果里。
 * <p>
 * 走 {@link ItemAttributeModifierEvent}：原版换装时照常加减修饰符、提示框里的「穿在身上时」一栏自动带上这几行，
 * 重登时被夹掉的生命由 {@code item/armor/ArmorHealthRestore} 补回。家族被关掉时照给（不收回已给的东西）。
 * 修饰符按槽位用固定 UUID，同一件的数额随阶变。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class AttunementBonuses {

    private static final UUID[] HEALTH_IDS = {
            UUID.fromString("5b1f3a0e-2c7d-4a61-9d1e-7a3c0e5f1b01"),
            UUID.fromString("5b1f3a0e-2c7d-4a61-9d1e-7a3c0e5f1b02"),
            UUID.fromString("5b1f3a0e-2c7d-4a61-9d1e-7a3c0e5f1b03"),
            UUID.fromString("5b1f3a0e-2c7d-4a61-9d1e-7a3c0e5f1b04")};
    private static final UUID DAMAGE_ID = UUID.fromString("5b1f3a0e-2c7d-4a61-9d1e-7a3c0e5f1b10");

    private AttunementBonuses() {
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
            double health = AttunementConfig.healthPerStage() * stage;
            if (health > 0D) {
                event.addModifier(Attributes.MAX_HEALTH, new AttributeModifier(HEALTH_IDS[slot.getIndex() & 3],
                        "Attunement health", health, AttributeModifier.Operation.ADDITION));
            }
        } else {
            double damage = AttunementConfig.damagePerStage() * stage;
            if (damage > 0D) {
                event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(DAMAGE_ID,
                        "Attunement damage", damage, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
    }
}
