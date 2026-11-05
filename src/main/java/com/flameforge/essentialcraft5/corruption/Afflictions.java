package com.flameforge.essentialcraft5.corruption;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.api.IPlayerData;
import com.flameforge.essentialcraft5.player.PlayerDataEvents;
import com.flameforge.essentialcraft5.registry.ECCorruption;
import com.flameforge.essentialcraft5.registry.ECMRUCU;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 矩阵后遗症（{@link Affliction}）：腐化效果和机器泄漏留在人身上的状态。不是药水效果——牛奶、不死图腾、净化道具、
 * 「免疫负面效果」的饰品和首领式的效果免疫都碰不到它，只能等时间走完；死亡时清掉（{@link PlayerDataEvents} 的 clone）。
 * 管理员用 {@code /ec5 player <targets> corruption afflictions clear}。
 *
 * <h2>怎么走</h2>
 * 剩余时间和等级存在玩家数据里（{@link IPlayerData#setAffliction}），整份同步给本人。每个玩家 tick（END）两边各减 1：
 * 服务端是权威，有一项走完时标记同步；客户端自己跟着减（暂停时不走），挖掘速度、心智悖论的特效和 HUD 读的是客户端这份。
 * <ul>
 *   <li>双手迟钝：挖掘速度乘挖掘疲劳同级的系数（II 级 ×0.09，{@link PlayerEvent.BreakSpeed}，两边都算，客户端的挖掘进度才对得上），
 *       攻击速度 −10% / 级；</li>
 *   <li>双手无力：攻击伤害 −25% / 级（II 级打五折）。原作是虚弱 II（固定 −8），整合包里武器动辄几百伤害，固定值等于没有，改成按比例；</li>
 *   <li>双腿迟缓：移动速度 −15% / 级（同缓慢）；</li>
 *   <li>三个属性修饰符都是临时的（不存盘），每 tick 对着后遗症补上或拿掉，UUID 固定；移速、攻速原版会同步给客户端。</li>
 *   <li>MRU 腐化：剩余时间是 {@value MRUCorruption#PULSE_INTERVAL} 的倍数时掷一次骰（{@link MRUCorruption#pulse}，和原来药水效果的节奏一样）。</li>
 * </ul>
 *
 * <h2>旧存档</h2>
 * MRU 腐化和心智悖论原来是药水效果，注册名留着（删掉的话 Forge 读档会弹「缺少注册项」）。身上还挂着的、或者别的东西
 * （{@code /effect}、别的模组）加上来的，下一 tick 就折算成后遗症（剩余时间、等级照搬，无限时长按 {@value #LEGACY_INFINITE_TICKS} tick）并去掉效果。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class Afflictions {

    /** 心智悖论多长（原作 new PotionEffect(PotionRegistry.paradox, 2000, 1, true, true)） */
    public static final int PARADOX_TICKS = 2000;
    /** 旧药水效果是无限时长时折算成多少 tick（一小时） */
    public static final int LEGACY_INFINITE_TICKS = 72_000;

    private static final UUID HEAVY_HANDS_ATTACK_SPEED = UUID.fromString("5d3f8a21-7c64-4e0b-9b1a-2f6c0e8d4a71");
    private static final UUID WEAK_GRIP_ATTACK_DAMAGE = UUID.fromString("a84e1c09-3f27-4d5b-8e6a-1b9d7c2f0e53");
    private static final UUID LEADEN_LEGS_SPEED = UUID.fromString("c2b7e5d4-9a18-4f63-a0c1-7e3d5b8f2a96");

    private Afflictions() {
    }

    // ---------------------------------------------------------------- 查询与增减

    /** 还剩多少 tick；没有时 0 */
    public static int remaining(Player player, Affliction affliction) {
        IPlayerData data = PlayerDataEvents.get(player);
        return data == null ? 0 : data.getAfflictionTicks(affliction.id());
    }

    /** 等级（0 = I 级）；没有时 0 */
    public static int level(Player player, Affliction affliction) {
        IPlayerData data = PlayerDataEvents.get(player);
        return data == null ? 0 : data.getAfflictionLevel(affliction.id());
    }

    public static boolean has(Player player, Affliction affliction) {
        return remaining(player, affliction) > 0;
    }

    /**
     * 照原版状态效果叠加的规矩给上：身上没有、或新来的等级更高 → 整个换成新的；同级 → 只在更长时延长；更低 → 不动。
     *
     * @return 改了没有
     */
    public static boolean give(Player player, Affliction affliction, int ticks, int level) {
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null || ticks <= 0) {
            return false;
        }
        String id = affliction.id();
        int current = data.getAfflictionTicks(id);
        int currentLevel = data.getAfflictionLevel(id);
        if (current > 0 && (level < currentLevel || level == currentLevel && ticks <= current)) {
            return false;
        }
        data.setAffliction(id, ticks, Math.max(0, level));
        return true;
    }

    /** 清掉全部后遗症，返回清掉了几项 */
    public static int clear(Player player) {
        IPlayerData data = PlayerDataEvents.get(player);
        return data == null ? 0 : data.clearAfflictions();
    }

    // ---------------------------------------------------------------- 每 tick

    @SubscribeEvent
    public static void onAfflictionPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        IPlayerData data = PlayerDataEvents.get(player);
        if (data == null) {
            return;
        }
        if (player.level().isClientSide) {
            // 客户端只有本人的数据（别人的是空的，减了也没事）
            data.tickAfflictions();
            return;
        }
        convertLegacy(player);
        int corruption = data.getAfflictionTicks(Affliction.MRU_CORRUPTION.id());
        if (corruption > 0 && corruption % MRUCorruption.PULSE_INTERVAL == 0 && player.isAlive()) {
            MRUCorruption.pulse(player, data.getAfflictionLevel(Affliction.MRU_CORRUPTION.id()));
        }
        if (data.tickAfflictions()) {
            data.markDirty();
        }
        updateModifiers(player, data);
    }

    /** 双手迟钝：挖掘速度乘挖掘疲劳同级的系数（原版 Player.getDigSpeed：I 0.3、II 0.09、III 0.0027、再往上 0.00081） */
    @SubscribeEvent
    public static void onAfflictionBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (has(player, Affliction.HEAVY_HANDS)) {
            event.setNewSpeed(event.getNewSpeed() * digFactor(level(player, Affliction.HEAVY_HANDS)));
        }
    }

    /** 双手迟钝 level 级时挖掘速度乘多少 */
    public static float digFactor(int level) {
        return switch (level) {
            case 0 -> 0.3F;
            case 1 -> 0.09F;
            case 2 -> 0.0027F;
            default -> 8.1E-4F;
        };
    }

    /** 按现在的后遗症补上 / 拿掉三个属性修饰符（数值随等级变时换一个） */
    public static void updateModifiers(Player player, IPlayerData data) {
        modifier(player, Attributes.ATTACK_SPEED, HEAVY_HANDS_ATTACK_SPEED, "heavy_hands",
                amount(data, Affliction.HEAVY_HANDS, 0.10D));
        modifier(player, Attributes.ATTACK_DAMAGE, WEAK_GRIP_ATTACK_DAMAGE, "weak_grip",
                amount(data, Affliction.WEAK_GRIP, 0.25D));
        modifier(player, Attributes.MOVEMENT_SPEED, LEADEN_LEGS_SPEED, "leaden_legs",
                amount(data, Affliction.LEADEN_LEGS, 0.15D));
    }

    /** 每级扣 perLevel（乘到总值上），最多扣 90%；没有这项后遗症时 0 */
    private static double amount(IPlayerData data, Affliction affliction, double perLevel) {
        if (data.getAfflictionTicks(affliction.id()) <= 0) {
            return 0D;
        }
        return -Math.min(0.9D, perLevel * (data.getAfflictionLevel(affliction.id()) + 1));
    }

    private static void modifier(Player player, Attribute attribute, UUID id, String name, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (current != null && current.getAmount() != amount) {
            instance.removeModifier(id);
            current = null;
        }
        if (current == null && amount != 0D) {
            instance.addTransientModifier(new AttributeModifier(id, EssentialCraft5.MODID + ":" + name, amount,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    /** 旧的两个药水效果折算成后遗症（见类注释「旧存档」） */
    public static void convertLegacy(Player player) {
        convert(player, ECMRUCU.MRU_CORRUPTION.get(), Affliction.MRU_CORRUPTION);
        convert(player, ECCorruption.MINDFOLD_PARADOX.get(), Affliction.PARADOX);
    }

    private static void convert(Player player, MobEffect effect, Affliction affliction) {
        MobEffectInstance instance = player.getEffect(effect);
        if (instance == null) {
            return;
        }
        int ticks = instance.isInfiniteDuration() ? LEGACY_INFINITE_TICKS : instance.getDuration();
        player.removeEffect(effect);
        give(player, affliction, ticks, instance.getAmplifier());
    }
}
