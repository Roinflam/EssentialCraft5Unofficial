package com.flameforge.essentialcraft5.entity.mob;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.CorruptionEffects;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECWildMobs;
import com.flameforge.essentialcraft5.util.ECLandedHit;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * 矩阵游魂的三条规矩（13c-M1，batch13.md §5.2、§6.5；policy §4.18）：
 * <ol>
 *   <li><b>追加刷出</b>（{@link #matrixWraithPotentialSpawns}）：原版自然刷怪问「这一格能刷哪些怪物」时（Forge {@code LevelEvent.PotentialSpawns}，
 *       所有维度），附近 {@code presence_radius} 格内有够格的聚集体（≥ {@code min_presence_mru}、没被稳定器压着，{@link MatrixWraithEntity#anchorFor}）
 *       就往表里加一条矩阵游魂（权重 {@code spawn_weight}，每群 1 只）。原版刷怪时同一格问两次（挑怪、核对），核对按对象比对，
 *       所以这一条用同一个对象（{@link #spawnerData}）。一团附近满 3 只由刷怪判定 {@link MatrixWraithEntity#checkWraithSpawnRules} 拦。
 *       查询只翻周围几块区块的索引格子，世界里没有聚集体时几乎不花时间；不加载区块。</li>
 *   <li><b>进世界才抽聚集体</b>（{@link #matrixWraithJoin}）：最低优先级、不收被取消的，生成被插件或别的模组取消就什么都不留。</li>
 *   <li><b>抓实加矩阵损伤</b>（{@link #matrixWraithHitLanded}）：游魂亲手（直接实体就是它）抓中真玩家、这一下打实了（{@link ECLandedHit#landed}），
 *       给<b>被抓的那个人</b>加 {@code matrix_damage_per_hit}（带来源标记，吃矩阵阶的耐受和稳固；创造 / 旁观、开了无敌的、假玩家不加）。
 *       被混合端插件或别的模组拦下的那一下不加（自检 C68）。</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class MatrixWraithEvents {

    private static final Vector3f MATRIX_PURPLE = new Vector3f(0.72F, 0.36F, 1F);

    @Nullable
    private static MobSpawnSettings.SpawnerData spawnerData;

    private MatrixWraithEvents() {
    }

    /** 追加进刷怪表的那一条（权重读配置；改了配置换一个对象）；权重 0 返回 null */
    @Nullable
    public static MobSpawnSettings.SpawnerData spawnerData() {
        int weight = ECWildMobs.Config.wraithSpawnWeight();
        if (weight <= 0) {
            return null;
        }
        MobSpawnSettings.SpawnerData data = spawnerData;
        if (data == null || data.getWeight().asInt() != weight) {
            data = new MobSpawnSettings.SpawnerData(ECWildMobs.MATRIX_WRAITH.get(), weight, 1, 1);
            spawnerData = data;
        }
        return data;
    }

    @SubscribeEvent
    public static void matrixWraithPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() != MobCategory.MONSTER || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MobSpawnSettings.SpawnerData data = spawnerData();
        if (data == null || event.getSpawnerDataList().contains(data) || MatrixWraithEntity.anchorFor(level, event.getPos()) == null) {
            return;
        }
        event.addSpawnerData(data);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void matrixWraithJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof MatrixWraithEntity wraith && event.getLevel() instanceof ServerLevel level && !event.loadedFromDisk()) {
            wraith.drainAnchorOnJoin(level);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void matrixWraithHitLanded(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return;
        }
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof MatrixWraithEntity wraith) || source.getDirectEntity() != wraith || !ECLandedHit.landed(event)) {
            return;
        }
        int added = addMatrixDamage(player, ECWildMobs.Config.wraithMatrixDamage());
        if (added > 0) {
            player.serverLevel().sendParticles(new DustParticleOptions(MATRIX_PURPLE, 1F), player.getX(), player.getY(0.6D), player.getZ(),
                    8, 0.3D, 0.4D, 0.3D, 0.01D);
        }
    }

    /** 给 player 加矩阵损伤（第十三批新来源，按矩阵阶打折、夹上限）；创造 / 旁观、开了无敌、死了的不加。返回实际加上的量 */
    public static int addMatrixDamage(ServerPlayer player, int amount) {
        if (amount <= 0 || player instanceof FakePlayer || !player.gameMode.isSurvival() || player.getAbilities().invulnerable || !player.isAlive()) {
            return 0;
        }
        IPlayerData data = PlayerDataEvents.get(player);
        return data == null ? 0 : CorruptionEffects.addMatrixDamage(data, amount, CorruptionEffects.Source.BATCH13);
    }
}
