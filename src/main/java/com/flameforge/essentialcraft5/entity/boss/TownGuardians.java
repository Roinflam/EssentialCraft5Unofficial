package com.flameforge.essentialcraft5.entity.boss;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 各座城镇的废城守望者醒过没有（第十三批 13e-T，batch13.md §5.3.3、§12.2 13e 行）：存档 {@value #NAME}，挂在主世界的 data 目录，
 * 键 = 维度 + 城镇起点所在区块（{@link ChunkPos#toLong}）。
 * <ul>
 *   <li>没有条目 = 还没醒（{@code PENDING}）：老存档里更新前生成的城镇也是这样，第一次有人走近时醒（batch13.md「13e 已定事项」第 3 条定为 B），
 *       不给城镇拼件加字段；</li>
 *   <li>{@link State#SPAWNED}：真的进了世界才记（混合端插件、别的模组取消生成就保持没有条目）；</li>
 *   <li>{@link State#DEFEATED}：死了（谁打的都算，这座城的守望者没了）；{@link State#ABANDONED}：被指令或别的模组直接删掉；</li>
 *   <li>和平难度散去时删掉条目，回到「还没醒」（照聚能魔像 C3-05），难度调回来以后有人走近再醒。</li>
 * </ul>
 * 一座城镇一条，最多也就几千条，不清理。只在服务端主线程读写。
 */
public final class TownGuardians extends SavedData {

    public static final String NAME = "essentialcraft5_town_guardians";

    private static final String TAG_TOWNS = "towns";
    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_CHUNK = "chunk";
    private static final String TAG_STATE = "state";

    /** 一座城的守望者的状态（没有条目 = 还没醒） */
    public enum State {
        SPAWNED,
        DEFEATED,
        ABANDONED;

        @Nullable
        static State byName(String name) {
            for (State state : values()) {
                if (state.name().equalsIgnoreCase(name)) {
                    return state;
                }
            }
            return null;
        }
    }

    /** 一座城镇：维度 + 起点所在区块 */
    public record Town(ResourceKey<Level> dimension, long startChunk) {

        public ChunkPos chunk() {
            return new ChunkPos(startChunk);
        }
    }

    private final Map<Town, State> towns = new LinkedHashMap<>();

    public TownGuardians() {
    }

    /** 这个服务器的表（挂在主世界） */
    public static TownGuardians get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TownGuardians::load, TownGuardians::new, NAME);
    }

    /** 这座城的状态；没有条目（还没醒）返回 null */
    @Nullable
    public State state(Town town) {
        return towns.get(town);
    }

    public void set(Town town, State state) {
        if (towns.put(town, state) != state) {
            setDirty();
        }
    }

    /** 删掉条目（回到还没醒） */
    public void clear(Town town) {
        if (towns.remove(town) != null) {
            setDirty();
        }
    }

    public int size() {
        return towns.size();
    }

    public static TownGuardians load(CompoundTag tag) {
        TownGuardians data = new TownGuardians();
        for (Tag entry : tag.getList(TAG_TOWNS, Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) entry;
            ResourceLocation dimension = ResourceLocation.tryParse(row.getString(TAG_DIMENSION));
            State state = State.byName(row.getString(TAG_STATE));
            if (dimension == null || state == null || !row.contains(TAG_CHUNK, Tag.TAG_LONG)) {
                continue;
            }
            data.towns.put(new Town(ResourceKey.create(Registries.DIMENSION, dimension), row.getLong(TAG_CHUNK)), state);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Town, State> entry : towns.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString(TAG_DIMENSION, entry.getKey().dimension().location().toString());
            row.putLong(TAG_CHUNK, entry.getKey().startChunk());
            row.putString(TAG_STATE, entry.getValue().name().toLowerCase(java.util.Locale.ROOT));
            list.add(row);
        }
        tag.put(TAG_TOWNS, list);
        return tag;
    }
}
