package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.entity.mob.ECMonster;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.flameforge.essentialcraft5.util.SpawnOrigin;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 灵魂刻刀（原作 ItemSoulScriber，第七批 M3，batch7.md §4.3）：把村民、末影人、风法师的灵魂收成 {@link CapturedSoulItem}，拿去和恶魔交易。
 * <p>
 * 属性照原作 {@code registerItemSimple(…, 32, true, 1)} + 木剑材质：耐久 32，攻击 +1（显示 2）、攻速 −2.4（显示 1.6）；在 {@code minecraft:swords} 里。
 * 命中不掉耐久（原作覆盖了 hitEntity、不调 damageItem，返回 false）；挖方块照剑的规矩掉耐久。
 *
 * <h2>收魂（{@link #hurtEnemy}，伤害生效以后才会被调到）</h2>
 * 只在服务端、攻击者是真玩家（{@link ECProtection#isRealPlayer}；原作假玩家也算）、目标类型在 {@link #SOUL_CAPTURABLE} 里（默认村民、末影人、风法师，
 * 同原作 DemonTrade.ALL_MOBS，按实体类型精确匹配，僵尸村民、流浪商人不算）、不是第十三批新怪的精英（{@link ECMonster#isElite(net.minecraft.world.entity.Entity)}），
 * 也不是第十三批新怪里被别的怪叫来的帮手（{@link ECMonster} 且来源是召唤，比如小鬼头目叫来的小鬼：叫来的什么都不掉，不然困住头目就能一直收魂；
 * 风之号角召来的风法师不是 {@link ECMonster}，照收）：
 * <ul>
 *   <li>这一下已经打死了：在它的位置多掉一个灵魂，战利品照常（原作手册「能捕获它杀死的生物的灵魂」；原作的 hitEntity 在伤害之后调，打死时生命 0 也满足下一条）。</li>
 *   <li>还活着、⌊生命⌋ ≤ 2：用 {@code soul_capture} 伤害（记在玩家头上）再打它一下，数额 = 生命 + 伤害吸收 + 1，之后真死了才掉灵魂。
 *       这是一次正常死亡：村民在 {@code die} 里放开床、工作站、聚集点（原作 setDead 直接删，那些位置会一直被占着），
 *       领地插件、别的模组都看得到；{@code soul_capture} 在 {@code no_death_loot} 里，不掉东西、不给经验（怪捡到的装备照掉）。
 *       被别的模组救活、被领地拦下就什么都不给。数额不用 {@code Float.MAX_VALUE}（别的模组乘倍率会乘出无穷）。</li>
 * </ul>
 * 横扫打到的不收：横扫只调 {@code hurt}，不经过 {@code hurtEnemy}。
 */
public class SoulScriberItem extends SwordItem {

    /** 能收魂的实体（data/essentialcraft5/tags/entity_types/soul_capturable.json）；恶魔交易池里的灵魂条目也按它核对 */
    public static final TagKey<EntityType<?>> SOUL_CAPTURABLE = TagKey.create(Registries.ENTITY_TYPE,
            EssentialCraft5.id("soul_capturable"));
    /** 原作 registerItemSimple 的耐久 */
    public static final int DURABILITY = 32;
    /** ⌊生命⌋ 不超过这个数就收魂（原作 {@code MathHelper.floor(getHealth()) <= 2}） */
    public static final int CAPTURE_HEALTH = 2;

    public SoulScriberItem(Properties properties) {
        super(Tiers.WOOD, 1, -2.4F, properties.durability(DURABILITY));
    }

    /** 命中不掉耐久（不调父类），满足条件时收魂；返回 false 同原作 */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // 第十三批新怪的精英不能收魂（batch13.md §0.4 第 20 条）：精英和普通的是同一种实体，标签分不开。
        // 别的新怪叫来的帮手也不收：和「叫来的什么都不掉、不给经验」一个口径
        if (attacker.level().isClientSide || !ECProtection.isRealPlayer(attacker) || !target.getType().is(SOUL_CAPTURABLE) || ECMonster.isElite(target)
                || target instanceof ECMonster && SpawnOrigin.of(target) == SpawnOrigin.Origin.SUMMONED) {
            return false;
        }
        if (target.isDeadOrDying()) {
            dropSoul(target);
            return false;
        }
        if (Mth.floor(target.getHealth()) <= CAPTURE_HEALTH) {
            float finish = target.getHealth() + target.getAbsorptionAmount() + 1F;
            target.hurt(ECDamageTypes.soulCapture(attacker), finish);
            if (target.isDeadOrDying()) {
                dropSoul(target);
            }
        }
        return false;
    }

    /** 在目标位置掉一个这种生物的灵魂（在死亡结算之后生成，不受「击杀不掉落」影响） */
    private static void dropSoul(LivingEntity target) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        ItemEntity soul = new ItemEntity(level, target.getX(), target.getY(), target.getZ(), CapturedSoulItem.of(target.getType(), 1));
        soul.setDefaultPickUpDelay();
        level.addFreshEntity(soul);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.essentialcraft5.soul_scriber").withStyle(ChatFormatting.GRAY));
    }
}
