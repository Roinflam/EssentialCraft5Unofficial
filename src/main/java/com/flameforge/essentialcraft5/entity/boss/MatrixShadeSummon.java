package com.flameforge.essentialcraft5.entity.boss;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.blockentity.PlayerPentacleBlockEntity;
import com.flameforge.essentialcraft5.registry.ECMatrixShade;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 用矩阵导流符在灵魂五芒星上召矩阵之影（batch13.md §5.3.2、D21 A；policy §4.18 13c）。只在服务端调。
 * <p>
 * 顺序：真玩家 → 生存 / 冒险 → 非和平 → 点的是灵魂五芒星 → 当场重判结构 ≥ 1 档 → 那一格召唤者能动（{@link ECProtection#canEdit}，C66）→
 * 矩阵损伤 ≥ {@code min_matrix_damage} → {@code exclusive_radius}（128 格）内没有别的矩阵之影 / 天灾之心 → 柱子里的有效 ESPE 够 a × {@code espe_per_damage}
 * → 五芒星上方（或旁边 3~6 格）站得下 → 生成。不满足时什么都不扣，提示原因（{@link Result#messageKey}）。
 * <p>
 * 生成成功以后才扣：从五芒星的柱子抽 ⌈a × 0.5⌉ 有效 ESPE（{@link PlayerPentacleBlockEntity#consumeESPE}，和治腐化同一套折算；抽不出来就把刚生成的删掉、按生成失败算）、
 * 召唤者的矩阵损伤减 a、导流符减一张，召唤者进名单，记一份契约（{@link MatrixShadeContracts}）。a = min(矩阵损伤, {@code max_absorbed})。
 * 生成来源记成首领（{@link SpawnOrigin.Origin#BOSS}）。
 */
public final class MatrixShadeSummon {

    /** 天灾之心（13e）的注册名：128 格内有它也召不了 */
    public static final ResourceLocation CALAMITY_HEART = EssentialCraft5.id("calamity_heart");
    /** 五芒星正上方站不下时，在旁边这么远的一圈里找 */
    public static final int RING_MIN = 3;
    public static final int RING_MAX = 6;

    public enum Result {
        OK,
        NOT_REAL_PLAYER,
        GAME_MODE,
        PEACEFUL,
        NOT_PENTACLE,
        TIER,
        DENIED,
        NOT_ENOUGH_DAMAGE,
        OCCUPIED,
        NO_ESPE,
        BLOCKED,
        SPAWN_FAILED;

        /** 提示的键：message.essentialcraft5.matrix_conduit.&lt;小写名&gt;（NOT_REAL_PLAYER 不提示） */
        public String messageKey() {
            return "message.essentialcraft5.matrix_conduit." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * 召唤的结果。
     *
     * @param absorbed 转进去的矩阵损伤 a（失败时是本来要转的量）
     * @param espeCost 要花的有效 ESPE
     * @param espeHave 柱子里现有的有效 ESPE
     */
    public record Outcome(Result result, @Nullable MatrixShadeEntity shade, int absorbed, int espeCost, int espeHave) {

        public boolean ok() {
            return result == Result.OK;
        }

        /** 给召唤者看的提示（OK 也有一句） */
        public Component message() {
            ECMatrixShade.Config.Summon summon = ECMatrixShade.Config.values().summon();
            return switch (result) {
                case NOT_ENOUGH_DAMAGE -> Component.translatable(result.messageKey(), absorbed, summon.minDamage());
                case OCCUPIED -> Component.translatable(result.messageKey(), summon.exclusiveRadius());
                case NO_ESPE -> Component.translatable(result.messageKey(), Math.max(0, espeCost - espeHave), espeCost);
                case OK -> Component.translatable(result.messageKey(), absorbed);
                default -> Component.translatable(result.messageKey());
            };
        }
    }

    private MatrixShadeSummon() {
    }

    /** 这名玩家的矩阵损伤能转多少（min(损伤, max_absorbed)，最少 0） */
    public static int absorbable(int matrixDamage) {
        return Math.max(0, Math.min(matrixDamage, ECMatrixShade.Config.values().summon().maxAbsorbed()));
    }

    /** 转 a 点要花的有效 ESPE：⌈a × espe_per_damage⌉ */
    public static int espeCost(int absorbed) {
        double per = ECMatrixShade.Config.values().summon().espePerDamage();
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(Math.max(0, absorbed) * Math.max(0D, per)));
    }

    /**
     * 用 conduit（玩家手里那叠导流符）在 pos 的五芒星上召唤；成功时导流符减一张。
     */
    public static Outcome summon(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack conduit) {
        if (!ECProtection.isRealPlayer(player)) {
            return fail(Result.NOT_REAL_PLAYER);
        }
        if (player.isCreative() || player.isSpectator()) {
            return fail(Result.GAME_MODE);
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return fail(Result.PEACEFUL);
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof PlayerPentacleBlockEntity pentacle)) {
            return fail(Result.NOT_PENTACLE);
        }
        int tier = pentacle.recheckTier();
        if (tier < 1) {
            return fail(Result.TIER);
        }
        if (!ECProtection.canEdit(player, level, pos)) {
            return fail(Result.DENIED);
        }
        IPlayerData data = player.getCapability(ECCapabilities.PLAYER_DATA).orElse(null);
        if (data == null) {
            return fail(Result.SPAWN_FAILED);
        }
        ECMatrixShade.Config.Summon config = ECMatrixShade.Config.values().summon();
        int damage = data.getMatrixDamage();
        if (damage < config.minDamage()) {
            return new Outcome(Result.NOT_ENOUGH_DAMAGE, null, damage, 0, 0);
        }
        int absorbed = absorbable(damage);
        if (occupied(level, Vec3.atCenterOf(pos), config.exclusiveRadius())) {
            return new Outcome(Result.OCCUPIED, null, absorbed, 0, 0);
        }
        int cost = espeCost(absorbed);
        int have = pentacle.effectiveESPE();
        if (have < cost) {
            return new Outcome(Result.NO_ESPE, null, absorbed, cost, have);
        }
        MatrixShadeEntity shade = ECMatrixShade.MATRIX_SHADE.get().create(level);
        if (shade == null) {
            return fail(Result.SPAWN_FAILED);
        }
        Vec3 spawn = spawnPoint(level, shade, pos);
        if (spawn == null) {
            return new Outcome(Result.BLOCKED, null, absorbed, cost, have);
        }
        float yaw = (float) (Mth.atan2(player.getZ() - spawn.z, player.getX() - spawn.x) * Mth.RAD_TO_DEG) - 90F;
        shade.moveTo(spawn.x, spawn.y, spawn.z, yaw, 0F);
        shade.setYHeadRot(yaw);
        shade.setYBodyRot(yaw);
        shade.prepareSummon(player, pos, absorbed);
        SpawnOrigin.set(shade, SpawnOrigin.Origin.BOSS);
        // 加入事件被取消时 addFreshEntity 返回 false；有的模组在加入事件里直接 discard 而不取消，返回值仍是 true，一并算失败
        if (!level.addFreshEntity(shade) || shade.isRemoved()) {
            return new Outcome(Result.SPAWN_FAILED, null, absorbed, cost, have);
        }
        int mult = PlayerPentacleBlockEntity.multiplier(tier);
        if (!pentacle.consumeESPE(cost)) {
            shade.discard();
            return new Outcome(Result.SPAWN_FAILED, null, absorbed, cost, have);
        }
        double rawEspe = PlayerPentacleBlockEntity.rawCost(cost) / (double) mult;
        data.setMatrixDamage(Math.max(0, data.getMatrixDamage() - absorbed));
        conduit.shrink(1);
        shade.bossRoster().add(player);
        shade.setTarget(player);
        MatrixShadeContracts.open(level, shade, player, absorbed, pos, rawEspe);
        level.playSound(null, spawn.x, spawn.y, spawn.z, ECMatrixShade.SOUND_SUMMON.get(), SoundSource.HOSTILE, 2F, 1F);
        CriteriaTriggers.SUMMONED_ENTITY.trigger(player, shade);
        return new Outcome(Result.OK, shade, absorbed, cost, have);
    }

    /** radius 格内有没有别的矩阵之影 / 天灾之心（活着的） */
    public static boolean occupied(ServerLevel level, Vec3 center, double radius) {
        if (radius <= 0D) {
            return false;
        }
        EntityType<?> heart = ForgeRegistries.ENTITY_TYPES.getValue(CALAMITY_HEART);
        boolean heartRegistered = heart != null && CALAMITY_HEART.equals(ForgeRegistries.ENTITY_TYPES.getKey(heart));
        AABB box = new AABB(center, center).inflate(radius);
        double max = radius * radius;
        List<Entity> found = level.getEntities((Entity) null, box, entity -> entity.isAlive()
                && entity.distanceToSqr(center) <= max
                && (entity instanceof MatrixShadeEntity || heartRegistered && entity.getType() == heart));
        return !found.isEmpty();
    }

    /** 它出现的位置：五芒星顶面正中；站不下就在旁边 3~6 格一圈里找第一个站得下、脚下有地面的点；都没有返回 null */
    @Nullable
    public static Vec3 spawnPoint(ServerLevel level, MatrixShadeEntity shade, BlockPos pentacle) {
        List<Vec3> candidates = new ArrayList<>();
        candidates.add(new Vec3(pentacle.getX() + 0.5D, pentacle.getY() + 0.0625D, pentacle.getZ() + 0.5D));
        for (int r = RING_MIN; r <= RING_MAX; r++) {
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI / 4D * i;
                candidates.add(new Vec3(pentacle.getX() + 0.5D + Math.round(Math.cos(angle) * r), pentacle.getY(),
                        pentacle.getZ() + 0.5D + Math.round(Math.sin(angle) * r)));
            }
        }
        for (Vec3 at : candidates) {
            AABB box = shade.getType().getDimensions().makeBoundingBox(at);
            if (!level.noCollision(box)) {
                continue;
            }
            BlockPos below = BlockPos.containing(at.x, at.y - 0.01D, at.z);
            if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                continue;
            }
            return at;
        }
        return null;
    }

    private static Outcome fail(Result result) {
        return new Outcome(result, null, 0, 0, 0);
    }
}
