package com.flameforge.essentialcraft5.matrix;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.api.MatrixRanks;
import com.flameforge.essentialcraft5.item.attunement.AttunementConfig;
import com.flameforge.essentialcraft5.item.bauble.BaubleEffects;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECTags;
import com.flameforge.essentialcraft5.util.ECDamageKinds;
import com.flameforge.essentialcraft5.util.ECProtection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * 相性（第十三批 13a，batch13.md §3.2，限制见 policy §4.16）：矩阵三 / 六 / 九阶时，按你<b>当前</b>的矩阵属性（{@link IPlayerData#getMatrixType()}）给
 * I / II / III 级被动。换属性用另一种矩阵投影，换完相性级不丢（级只看阶）。中性没有相性。暗影投影七阶以下也能用（照旧变属性），
 * 但暗影相性八阶起才有、直接从 II 开始（{@value #SHADE_RANK} 阶 II、九阶 III）。玩家看到的叫「相性」，四种叫躁动、凝滞、澄澈、吞噬；
 * 代码、配置沿用 resonance。
 *
 * <table>
 *   <tr><th>属性</th><th>相性被动（玩家层面）</th></tr>
 *   <tr><td>混沌「躁动」</td><td>你打出的近战、弹射物伤害 +5 / +10 / +15%</td></tr>
 *   <tr><td>冰冻「凝滞」</td><td>你受到的近战伤害 −5 / −10 / −15%（魔法、无视护甲的不算近战，{@link #frozenMeleeHit}）；用近战打实你的活物冰冻刻 +30 / +45 / +60（结算在 {@code item/MatrixTraits}）</td></tr>
 *   <tr><td>魔力「澄澈」</td><td>本模组随身物品耗 MRU −10 / −15 / −20%（{@link #costMultiplier}，护盾扣费不打折）；虹吸单次上限 +25 / +50 / +100%（{@link #siphonMultiplier}）</td></tr>
 *   <tr><td>暗影「吞噬」</td><td>你受到的、有攻击者的魔法伤害 −10 / −15%（只有 II、III）；没有攻击者的自伤（腐化、MRU 腐化）、标签 {@code essentialcraft5:unshieldable} 里的不减</td></tr>
 * </table>
 * 数值都在配置 {@code general.matrix_rank.resonance.*}（{@link MatrixResonanceConfig}）。武器层面的「矩阵特性」挂在相性上（相性 ≥ I 才触发），在 {@code item/MatrixTraits}。
 *
 * <h2>时序（{@link MatrixResonanceEvents}）</h2>
 * 增伤、减伤都在 {@code LivingHurtEvent} NORMAL（不收已取消的）：护盾盔甲在 LOW，按减过的数结算；乘法叠加，和矩阵特性的增伤、神圣重锤的亡灵加成谁先谁后结果一样。
 * 对玩家时（受击的是玩家）躁动的增伤百分比乘 {@code general.attunement.pvp_effect_scale}（{@link #pvpScale}，全批「对玩家减半」都读它）；
 * 减伤是挨打那人自己的被动，不乘。只认真玩家（不认假玩家），只在服务端。
 * <p>
 * 服主关掉矩阵阶（{@code general.matrix_rank.enabled = false}）时已有的阶照给回报，相性照常。
 */
public final class MatrixResonance {

    /** 矩阵属性序号（{@link IPlayerData#getMatrixType()}，原作 EnumMatrixType） */
    public static final int NEUTRAL = 0;
    public static final int CHAOS = 1;
    public static final int FROZEN = 2;
    public static final int MAGIC = 3;
    public static final int SHADE = 4;

    /** 相性 I / II / III 要的阶 */
    public static final int RANK_I = 3;
    public static final int RANK_II = 6;
    public static final int RANK_III = 9;
    /** 暗影相性从几阶起（直接 II） */
    public static final int SHADE_RANK = 8;
    public static final int MAX_LEVEL = 3;

    /** 配置、语言键里四种属性的名字（下标 = 矩阵属性序号；中性没有） */
    private static final List<String> KEYS = List.of("neutral", "chaos", "frozen", "magic", "shade");

    private MatrixResonance() {
    }

    // ------------------------------------------------------------------ 级

    /**
     * 这一阶、这种属性的相性级：0 = 没有。中性、未知属性恒 0；暗影 {@value #SHADE_RANK} 阶 II、九阶 III；其余三 / 六 / 九阶 I / II / III。
     */
    public static int level(int rank, int type) {
        if (type <= NEUTRAL || type > SHADE) {
            return 0;
        }
        if (type == SHADE) {
            return rank >= RANK_III ? 3 : rank >= SHADE_RANK ? 2 : 0;
        }
        return rank >= RANK_III ? 3 : rank >= RANK_II ? 2 : rank >= RANK_I ? 1 : 0;
    }

    /** 玩家当前的矩阵属性（没有玩家数据时中性） */
    public static int type(@Nullable Player player) {
        IPlayerData data = player == null ? null : PlayerDataEvents.get(player);
        return data == null ? NEUTRAL : data.getMatrixType();
    }

    /** 玩家当前属性的相性级（两端都能用：阶和属性随普通同步包发给本人） */
    public static int level(@Nullable Player player) {
        IPlayerData data = player == null ? null : PlayerDataEvents.get(player);
        return data == null ? 0 : level(data.getMatrixRank(), data.getMatrixType());
    }

    /** 玩家当前属性是 type 时的相性级，不是这种属性时 0 */
    public static int level(@Nullable Player player, int type) {
        IPlayerData data = player == null ? null : PlayerDataEvents.get(player);
        return data == null || data.getMatrixType() != type ? 0 : level(data.getMatrixRank(), type);
    }

    /** 配置、语言键用的属性名（chaos / frozen / magic / shade；中性和未知是 neutral） */
    public static String key(int type) {
        return type > NEUTRAL && type < KEYS.size() ? KEYS.get(type) : KEYS.get(NEUTRAL);
    }

    // ------------------------------------------------------------------ 数值

    /**
     * 本模组随身物品耗 MRU 的倍数（魔力相性 I / II / III 时 0.9 / 0.85 / 0.8，其余 1）。{@code MRUDrain.playerUseMRU} / {@code canAfford}
     * 先乘它再扣；护盾盔甲的护盾扣费不乘（{@code MRUDrain.playerUseMRUExact}）。每次花 MRU 都调，只读玩家数据和一项配置。
     */
    public static double costMultiplier(Player player) {
        int level = level(player, MAGIC);
        return level <= 0 ? 1D : 1D - MatrixResonanceConfig.magicCostDiscount(level);
    }

    /** 虹吸单次上限的倍数（魔力相性 I / II / III 时 1.25 / 1.5 / 2，其余 1） */
    public static double siphonMultiplier(Player player) {
        int level = level(player, MAGIC);
        return 1D + MatrixResonanceConfig.magicSiphonBonus(level);
    }

    /** 矩阵特性按相性级的强度倍数（I ×1、II ×1.25、III ×1.5；没有相性是 0） */
    public static double traitMultiplier(int level) {
        return MatrixResonanceConfig.traitLevelMultiplier(level);
    }

    /**
     * 枪械透镜的倍数（batch13.md §3.2「透镜同型」）：射手是玩家、透镜和他的矩阵属性相同、相性 ≥ I 时乘
     * {@code same_type_lens_multiplier}（1.5），其余 1。目标是玩家时多出来的那部分乘 {@link #pvpScale}（默认 1.25）。
     *
     * @param lens 透镜（同矩阵属性序号：1 混沌、2 冰冻、3 魔力、4 暗影）
     */
    public static double lensMultiplier(@Nullable LivingEntity shooter, @Nullable LivingEntity target, int lens) {
        if (!(shooter instanceof Player player) || lens <= NEUTRAL || level(player, lens) <= 0) {
            return 1D;
        }
        double extra = MatrixResonanceConfig.sameTypeLensMultiplier() - 1D;
        if (!(extra > 0D)) {
            return 1D;
        }
        return 1D + extra * pvpScale(target);
    }

    /** 目标是玩家时的效果倍率（{@code general.attunement.pvp_effect_scale}，默认 0.5），否则 1 */
    public static double pvpScale(@Nullable Entity target) {
        return target instanceof Player ? AttunementConfig.pvpEffectScale() : 1D;
    }

    // ------------------------------------------------------------------ 显示

    /** 「躁动」「Agitation」：matrix_alignment.essentialcraft5.&lt;属性&gt; */
    public static MutableComponent alignmentName(int type) {
        return Component.translatable("matrix_alignment." + EssentialCraft5.MODID + "." + key(type));
    }

    /** 「躁动 II」 */
    public static MutableComponent alignmentName(int type, int level) {
        return Component.translatable("matrix_alignment." + EssentialCraft5.MODID + ".named", alignmentName(type),
                Component.translatable("enchantment.level." + Math.max(1, Math.min(MAX_LEVEL, level))));
    }

    /**
     * 灵魂石提示里的相性几行（{@link MatrixTooltip} 的拼接点，由 {@code item/MatrixTraits.init} 登记）：三阶起才写。
     * 有相性时写「相性：躁动 II（混沌）」和一行效果；中性写怎么换属性；暗影没到八阶写要几阶；四阶起再写一行虹吸的用法。
     */
    public static void appendTooltip(Player viewer, IPlayerData data, List<Component> tooltip) {
        int rank = data.getMatrixRank();
        if (rank < RANK_I) {
            return;
        }
        int type = data.getMatrixType();
        int level = level(rank, type);
        if (level > 0) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.soul_stone.alignment",
                            alignmentName(type, level).withStyle(ChatFormatting.LIGHT_PURPLE),
                            Component.translatable("gui.essentialcraft5.matrix_type." + key(type)))
                    .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(effectLine(type, level).withStyle(ChatFormatting.DARK_GRAY));
        } else if (type == SHADE) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.soul_stone.alignment_shade_locked", MatrixRank.rankName(SHADE_RANK))
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.soul_stone.alignment_neutral").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (rank >= MatrixSiphon.RANK) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.soul_stone.siphon",
                    MatrixTooltip.number(MatrixSiphon.cap(viewer)), (int) MatrixSiphon.REACH).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** 相性被动那一行（按当前配置的数） */
    public static MutableComponent effectLine(int type, int level) {
        String key = "tooltip.essentialcraft5.alignment." + key(type);
        return switch (type) {
            case CHAOS -> Component.translatable(key, percent(MatrixResonanceConfig.chaosDamageBonus(level)));
            case FROZEN -> Component.translatable(key, percent(MatrixResonanceConfig.frozenMeleeReduction(level)),
                    MatrixResonanceConfig.frozenRetaliationTicks(level));
            case MAGIC -> Component.translatable(key, percent(MatrixResonanceConfig.magicCostDiscount(level)),
                    percent(MatrixResonanceConfig.magicSiphonBonus(level)));
            case SHADE -> Component.translatable(key, percent(MatrixResonanceConfig.shadeMagicReduction(level)));
            default -> Component.empty();
        };
    }

    /** 0.15 → 「15」，0.125 → 「12.5」（百分号写在语言键里） */
    public static String percent(double fraction) {
        return decimal(fraction * 100D);
    }

    /** 最多一位小数，整数不带小数点：2 → 「2」，1.25 → 「1.3」 */
    public static String decimal(double value) {
        double rounded = Math.round(value * 10D) / 10D;
        return rounded == Math.rint(rounded) ? Long.toString((long) rounded) : String.format(Locale.ROOT, "%.1f", rounded);
    }

    // ------------------------------------------------------------------ 事件

    /** 躁动这一下加多少（0 = 不加）：攻击者是真玩家，这一下是他亲手的近战或他射出的弹射物 */
    public static double chaosBonus(DamageSource source, LivingEntity target) {
        if (!(source.getEntity() instanceof Player player) || player == target || !ECProtection.isRealPlayer(player)
                || BaubleEffects.isBaubleDamage() || ECDamageKinds.isReflected(source) || !ownHit(source, player)) {
            return 0D;
        }
        int level = level(player, CHAOS);
        return level <= 0 ? 0D : MatrixResonanceConfig.chaosDamageBonus(level) * pvpScale(target);
    }

    /** 近战（直接来源是他本人、不是魔法）或弹射物（伤害记在他头上） */
    private static boolean ownHit(DamageSource source, Player player) {
        if (source.getDirectEntity() == player) {
            return !ECDamageKinds.isMagic(source) && !source.is(DamageTypeTags.IS_PROJECTILE);
        }
        return ECDamageKinds.isProjectile(source);
    }

    /**
     * 凝滞认的「近战打实你」：有攻击者（不是自己）、直接实体是活物、不是魔法、不无视护甲。监守者音爆、守卫者激光这类直接实体是怪本身的
     * 无视护甲 / 魔法伤害都不算近战。减伤（{@link #frozenReduction}）和反冻（{@code item/MatrixTraits}）共用这一条，口径一致。
     */
    public static boolean frozenMeleeHit(DamageSource source, LivingEntity victim) {
        Entity attacker = source.getEntity();
        return attacker != null && attacker != victim && ECDamageKinds.isMelee(source) && !ECDamageKinds.isMagic(source)
                && !source.is(DamageTypeTags.BYPASSES_ARMOR) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /** 凝滞对这一下减多少（0 = 不减）：挨打的是真玩家，这一下是近战打实（{@link #frozenMeleeHit}） */
    public static double frozenReduction(DamageSource source, LivingEntity victim) {
        if (!(victim instanceof Player player) || !ECProtection.isRealPlayer(player) || !frozenMeleeHit(source, victim)) {
            return 0D;
        }
        int level = level(player, FROZEN);
        return level <= 0 ? 0D : MatrixResonanceConfig.frozenMeleeReduction(level);
    }

    /** 吞噬对这一下减多少（0 = 不减）：挨打的是真玩家，这一下是有攻击者的魔法伤害、不在 unshieldable 里 */
    public static double shadeReduction(DamageSource source, LivingEntity victim) {
        if (!(victim instanceof Player player) || !ECProtection.isRealPlayer(player) || source.getEntity() == null
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(ECTags.UNSHIELDABLE) || !ECDamageKinds.isMagic(source)) {
            return 0D;
        }
        int level = level(player, SHADE);
        return level <= 0 ? 0D : MatrixResonanceConfig.shadeMagicReduction(level);
    }

    /** Forge 总线（类名带模块前缀，见 tools/check_event_handlers.py） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
    public static final class MatrixResonanceEvents {

        private MatrixResonanceEvents() {
        }

        /** 躁动增伤、凝滞 / 吞噬减伤（NORMAL，不收已取消的） */
        @SubscribeEvent
        public static void matrixResonanceHurt(LivingHurtEvent event) {
            LivingEntity victim = event.getEntity();
            float amount = event.getAmount();
            if (victim.level().isClientSide() || !(amount > 0F)) {
                return;
            }
            DamageSource source = event.getSource();
            double factor = 1D + chaosBonus(source, victim);
            factor *= 1D - frozenReduction(source, victim);
            factor *= 1D - shadeReduction(source, victim);
            if (factor != 1D) {
                event.setAmount((float) Math.max(0D, amount * factor));
            }
        }
    }
}
