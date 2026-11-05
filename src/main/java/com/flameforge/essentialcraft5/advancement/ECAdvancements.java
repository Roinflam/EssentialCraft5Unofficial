package com.flameforge.essentialcraft5.advancement;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * 代码里发「只靠代码完成」的手册进度（第十三批 13d 预备：巢穴、落晶、勘测图到点这几处都要发，batch13.md §9.4）。
 * 进度 JSON 的条件写 {@code minecraft:impossible}，这里把剩下的条件全部补齐。和 {@code CorruptionTicker.award} 同一个做法，
 * 多了两条：假玩家不发（原版对假玩家也不会存进度，免得空跑），进度不存在（数据包删了、手册那一路还没合进来）时安静地返回 false。
 */
public final class ECAdvancements {

    private ECAdvancements() {
    }

    /**
     * 给这名玩家发进度 id（条件全部补齐）。
     *
     * @return 这一次真的发出去了（进度不存在、已经完成、假玩家、null 都返回 false）
     */
    public static boolean awardIfPresent(@Nullable ServerPlayer player, ResourceLocation id) {
        if (player == null || player instanceof FakePlayer || id == null) {
            return false;
        }
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        if (advancement == null) {
            return false;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) {
            return false;
        }
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
        return true;
    }
}
