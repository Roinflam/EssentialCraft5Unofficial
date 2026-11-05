package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECBossFramework;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 首领打倒以后的分账（第十三批 13c-Fb，batch13.md §5.3「共同」、D25 B）：
 * <ul>
 *   <li><b>谁分得到</b>（{@link #winners}）：伤害账里打出不少于最大生命 × {@code general.mobs.boss.loot_share}（默认 10%）有效伤害、
 *       此刻在线的玩家，按伤害从多到少。中途死了、脱战了的照样算（伤害是他打的）；不在线的那份不发。站着不出手的小号拿不到。</li>
 *   <li><b>掉落</b>（{@link #distribute}）：首领的战利品表给每名分得到的人各掷一次，参数里「最后一下的玩家」填他自己（表里每个池都带
 *       {@code killed_by_player}，{@code /kill} 和没有玩家参与的死亡什么都不掉），掷出来的直接进他的背包（{@code ECItemEntities.giveOrDrop}：
 *       放不下掉在他脚下，人死了掉在他所在的位置），不会掉进岩浆或虚空，多人合打不抢。走的是 {@code LootTable.getRandomItems(LootParams)}，
 *       全局战利品修改器照常生效。</li>
 *   <li><b>首杀与异兽录</b>（{@link #recordVictory}）：每名分得到的人记一次首杀（玩家数据 {@code boss_first_kills}，实体类型 id），
 *       并发异兽录进度 {@code essentialcraft5:guide/bestiary/<实体名>}（存在才发，补完所有条件）。</li>
 * </ul>
 * 经验照原版在首领位置掉（谁捡算谁的）。只在服务端调。
 */
public final class BossLoot {

    private BossLoot() {
    }

    /** 分得到掉落、算首杀的人（见类注释；按伤害从多到少） */
    public static List<ServerPlayer> winners(ECBoss boss) {
        LivingEntity entity = boss.bossEntity();
        if (!(entity.level() instanceof ServerLevel server)) {
            return List.of();
        }
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : boss.bossRoster().qualifiers(ECBossFramework.Config.values().lootShare())) {
            ServerPlayer player = ECProtection.onlinePlayer(server.getServer(), id);
            if (player != null) {
                out.add(player);
            }
        }
        return out;
    }

    /**
     * 给每名分得到的人各掷一次 table（见类注释）。byPlayer 为假（没有玩家参与的死亡）时「最后一下的玩家」不填，
     * 带 {@code killed_by_player} 的池子都不出。
     *
     * @param source 致死的伤害源
     */
    public static void distribute(ECBoss boss, LootTable table, DamageSource source, boolean byPlayer, List<ServerPlayer> winners) {
        LivingEntity entity = boss.bossEntity();
        if (!(entity.level() instanceof ServerLevel server) || table == LootTable.EMPTY) {
            return;
        }
        for (ServerPlayer player : winners) {
            LootParams.Builder builder = new LootParams.Builder(server)
                    .withParameter(LootContextParams.THIS_ENTITY, entity)
                    .withParameter(LootContextParams.ORIGIN, entity.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                    .withOptionalParameter(LootContextParams.KILLER_ENTITY, source.getEntity())
                    .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, source.getDirectEntity());
            if (byPlayer) {
                builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player).withLuck(player.getLuck());
            }
            LootParams params = builder.create(LootContextParamSets.ENTITY);
            for (ItemStack stack : table.getRandomItems(params)) {
                ECItemEntities.giveOrDrop(player, stack);
            }
        }
    }

    /**
     * 记首杀、发异兽录进度（见类注释）。返回这一次是第一次打倒这种首领的人。
     */
    public static List<ServerPlayer> recordVictory(ECBoss boss, List<ServerPlayer> winners) {
        EntityType<?> type = boss.bossEntity().getType();
        String typeId = EntityType.getKey(type).toString();
        ResourceLocation bestiary = bestiaryAdvancement(type);
        List<ServerPlayer> first = new ArrayList<>();
        for (ServerPlayer player : winners) {
            IPlayerData data = PlayerDataEvents.get(player);
            if (data != null && data.addBossFirstKill(typeId)) {
                first.add(player);
            }
            if (bestiary != null) {
                award(player, bestiary);
            }
        }
        return first;
    }

    /** 异兽录里这种首领的可选步骤：本模组的实体是 {@code essentialcraft5:guide/bestiary/<实体名>}，别的模组的返回 null */
    @Nullable
    public static ResourceLocation bestiaryAdvancement(EntityType<?> type) {
        ResourceLocation key = EntityType.getKey(type);
        return EssentialCraft5.MODID.equals(key.getNamespace()) ? EssentialCraft5.id("guide/bestiary/" + key.getPath()) : null;
    }

    /** 给玩家补完一个进度的所有条件（进度不存在就什么都不做；返回这次有没有新补上的条件） */
    public static boolean award(ServerPlayer player, ResourceLocation id) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        if (advancement == null) {
            return false;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        boolean changed = false;
        for (String criterion : progress.getRemainingCriteria()) {
            changed |= player.getAdvancements().award(advancement, criterion);
        }
        return changed;
    }
}
