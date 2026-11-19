package com.flameforge.essentialcraft5.item.attunement;

import com.flameforge.essentialcraft5.network.GearFx;
import com.flameforge.essentialcraft5.network.S2CGearFxPacket;
import com.flameforge.essentialcraft5.registry.ECGearSounds;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 升阶演出（tools/plan/gear_growth.md 第 7 节）：魔力工作台出升阶 / 升格产物时（{@code blockentity/MagicianTableBlockEntity} 出货那里调），
 * 在工作台位置发 {@link GearFx#ASCEND}（param = 新阶）、放音效；出货时没有玩家，动作栏「裁决之刃 升到 4 阶」发给 {@value #NOTIFY_RANGE} 格内最近的玩家，没有就不发。
 * 指令改阶不走这里。
 */
public final class AttunementShowcase {

    /** 告诉多少格内最近的玩家 */
    public static final double NOTIFY_RANGE = 8D;

    private AttunementShowcase() {
    }

    public static void ascended(ServerLevel level, BlockPos table, ItemStack result) {
        if (result.isEmpty() || AttunementFamily.of(result) == null) {
            return;
        }
        int stage = Attunement.stage(result);
        Vec3 center = Vec3.atCenterOf(table);
        S2CGearFxPacket.sendAround(level, GearFx.ASCEND, null, center, stage);
        ECGearSounds.play(level, center, ECGearSounds.ASCEND, SoundSource.BLOCKS, 1F, 1F);
        ServerPlayer nearest = null;
        double best = NOTIFY_RANGE * NOTIFY_RANGE;
        for (ServerPlayer player : level.players()) {
            double d = player.distanceToSqr(center);
            if (d <= best && ECProtection.isRealPlayer(player)) {
                best = d;
                nearest = player;
            }
        }
        if (nearest != null) {
            nearest.displayClientMessage(Component.translatable("message.essentialcraft5.gear.ascended", result.getHoverName(), stage)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }
}
