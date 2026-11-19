package com.flameforge.essentialcraft5.network;

import com.flameforge.essentialcraft5.client.vfx.weapon.VerdictClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * 服务端 → 看得见施放者的人：裁决之刃「断罪」松手那一刻，施放者、范围和这一下打实了的目标（客户端画刀上炸开、劈向每个目标的电弧）。
 * 只在真打成（至少打实一下）时发一次；蓄力的画面客户端看施放者在不在用刀自己画，不发包。
 */
public record S2CVerdictJudgmentPacket(int caster, float radius, int[] targets) {

    /** 一次最多带几个目标（同断罪的 max_targets 上限） */
    public static final int MAX_TARGETS = 64;

    /** GameTest 用：不为 null 时每次发包先交给它（测试玩家不在玩家列表里，收不到真包） */
    @Nullable
    public static volatile Consumer<S2CVerdictJudgmentPacket> testSink;

    public static void sendAround(ServerPlayer caster, double radius, List<LivingEntity> landed) {
        int n = Math.min(MAX_TARGETS, landed.size());
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = landed.get(i).getId();
        }
        S2CVerdictJudgmentPacket packet = new S2CVerdictJudgmentPacket(caster.getId(), (float) radius, ids);
        Consumer<S2CVerdictJudgmentPacket> sink = testSink;
        if (sink != null) {
            sink.accept(packet);
        }
        ECNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> caster), packet);
    }

    public static void encode(S2CVerdictJudgmentPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.caster);
        buf.writeFloat(p.radius);
        buf.writeVarInt(p.targets.length);
        for (int id : p.targets) {
            buf.writeVarInt(id);
        }
    }

    public static S2CVerdictJudgmentPacket decode(FriendlyByteBuf buf) {
        int caster = buf.readVarInt();
        float radius = buf.readFloat();
        int n = Math.min(MAX_TARGETS, Math.max(0, buf.readVarInt()));
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = buf.readVarInt();
        }
        return new S2CVerdictJudgmentPacket(caster, Float.isFinite(radius) ? Math.max(0F, Math.min(radius, 64F)) : 6F, ids);
    }

    /** 客户端主线程调（{@code ECNetwork.registerS2C} 已经套了只在客户端跑） */
    public static void handleClient(S2CVerdictJudgmentPacket packet) {
        VerdictClient.onJudgment(packet);
    }
}
