package com.flameforge.essentialcraft5.menu;

import com.flameforge.essentialcraft5.blockentity.PotionSpreaderBlockEntity;
import com.flameforge.essentialcraft5.registry.ECDevices;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * 药水扩散器界面。槽位坐标照搬原作 ContainerPotionSpreader：宝石 (155, 23)，药水 4 列 × 2 行从 (26, 23) 起
 * （原作按 列 + 行 × 4 + 1 编号：1 号 (26, 23)、5 号 (26, 41)、2 号 (44, 23)……）。
 * <p>
 * 额外数值：药水颜色（拆两格）、剩余次数、总次数、效果数，再加前 {@value #SYNCED_EFFECTS} 个效果各 3 格
 * （效果的数字 id、等级、时长秒数），给界面的提示用。数字 id 就是原版网络包同步效果用的那个（Forge 把服务端的 id 表同步给客户端）。
 */
public class PotionSpreaderMenu extends MachineMenu {

    public static final int SYNCED_EFFECTS = 4;
    private static final int EXTRA_COLOR = 0;
    private static final int EXTRA_USES = 2;
    private static final int EXTRA_MAX_USES = 3;
    private static final int EXTRA_EFFECT_COUNT = 4;
    private static final int EXTRA_FIRST_EFFECT = 5;
    private static final int EXTRA_COUNT = EXTRA_FIRST_EFFECT + SYNCED_EFFECTS * 3;

    public PotionSpreaderMenu(int id, Inventory playerInventory, PotionSpreaderBlockEntity be) {
        this(id, playerInventory, be.getBlockPos(), be.getInventory(), serverData(be), be);
    }

    public PotionSpreaderMenu(int id, Inventory playerInventory, FriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(),
                clientHandler(PotionSpreaderBlockEntity.SLOTS, PotionSpreaderBlockEntity::slotRule),
                MachineData.client(EXTRA_COUNT), null);
    }

    private PotionSpreaderMenu(int id, Inventory playerInventory, BlockPos pos, IItemHandler handler, MachineData data,
                               @Nullable PotionSpreaderBlockEntity be) {
        super(ECDevices.POTION_SPREADER_MENU.get(), id, playerInventory, pos, data, ECDevices.POTION_SPREADER.get(), be);
        addMachineSlot(FilteredSlot.boundGem(handler, PotionSpreaderBlockEntity.SLOT_GEM, 155, 23));
        // 原作的添加顺序：外层列、内层行，槽号 = 列 + 行 × 4 + 1
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 2; row++) {
                addMachineSlot(new SlotItemHandler(handler, PotionSpreaderBlockEntity.FIRST_POTION + col + row * 4,
                        26 + col * 18, 23 + row * 18));
            }
        }
        addPlayerInventory(playerInventory);
    }

    private static MachineData serverData(PotionSpreaderBlockEntity be) {
        List<IntSupplier> extras = new ArrayList<>(List.of(MachineData.wide(be::potionColor)));
        extras.add(be::usesLeft);
        extras.add(be::maxUses);
        extras.add(() -> be.loadedEffects().size());
        for (int i = 0; i < SYNCED_EFFECTS; i++) {
            int index = i;
            extras.add(() -> {
                MobEffectInstance effect = be.loadedEffect(index);
                return effect == null ? -1 : MobEffect.getId(effect.getEffect());
            });
            extras.add(() -> {
                MobEffectInstance effect = be.loadedEffect(index);
                return effect == null ? 0 : Math.min(effect.getAmplifier(), Short.MAX_VALUE);
            });
            extras.add(() -> {
                MobEffectInstance effect = be.loadedEffect(index);
                return effect == null ? 0 : Math.min(effect.getDuration() / 20, Short.MAX_VALUE);
            });
        }
        return MachineData.server(be, extras.toArray(IntSupplier[]::new));
    }

    /** 装着的药水颜色；没装为 PotionSpreaderBlockEntity.NO_COLOR（-1） */
    public int color() {
        return data.extraWide(EXTRA_COLOR);
    }

    public boolean loaded() {
        return effectCount() > 0;
    }

    public int usesLeft() {
        return data.extra(EXTRA_USES);
    }

    public int maxUses() {
        return data.extra(EXTRA_MAX_USES);
    }

    public int effectCount() {
        return data.extra(EXTRA_EFFECT_COUNT);
    }

    /** 第 i 个效果（只同步了前 {@value #SYNCED_EFFECTS} 个）；没有或客户端不认识时返回 null */
    @Nullable
    public MobEffectInstance effect(int i) {
        if (i >= SYNCED_EFFECTS || i >= effectCount()) {
            return null;
        }
        int base = EXTRA_FIRST_EFFECT + i * 3;
        MobEffect effect = MobEffect.byId(data.extra(base));
        if (effect == null) {
            return null;
        }
        return new MobEffectInstance(effect, data.extra(base + 2) * 20, data.extra(base + 1));
    }
}
