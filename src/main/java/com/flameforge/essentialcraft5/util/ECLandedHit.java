package com.flameforge.essentialcraft5.util;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 「这一下打实了没有」的统一判定（第十三批 13c 预备，batch13.md 文末「13b 审查修复记录」B4 补 2；policy §4.17）：
 * <b>打实 = {@code LivingDamageEvent} 没被取消，并且最终伤害 &gt; 0 或者打掉了目标的伤害吸收（金苹果的黄心）</b>。
 * 用它的地方：审判庭定罪与壁垒、虚空回击、余烬嵌刻、相性的凝滞反冻与矩阵特性、同调点、矩阵图谱的首领参战记账，新代码同样调这里，不再各写一份。
 *
 * <h2>为什么要连伤害吸收一起看</h2>
 * 原版 {@code actuallyHurt} 先过护甲、再扣伤害吸收，扣剩的才发 {@code LivingDamageEvent}：黄心整下吃掉的一下，事件里是 0，可它确实打中了
 * （原版统计也把它记成造成了伤害）。所以在 {@code LivingHurtEvent} 最低优先级记下那时目标的伤害吸收（{@link #onLandedHitHurtNote}），
 * 到 {@code LivingDamageEvent} 时按「同一个目标、同一个伤害源对象」找到这条比一比。不改成「{@code LivingHurtEvent} 没被取消、伤害 &gt; 0」：
 * 混合端插件不取消、只把伤害改成 0 的那一下也满足它；而插件把伤害改成 0、别的模组取消原伤害（混合端上照样按 0 发 {@code LivingDamageEvent}）时，
 * 伤害吸收都不会少，这里也就不算打实。混合端插件拦下的那一下根本不发 {@code LivingDamageEvent}，问都问不到。
 *
 * <h2>同一下只认一次</h2>
 * 一下伤害第一次被问到时（不论哪个监听器、哪个优先级）把记录取走、算出吃掉的伤害吸收，按事件对象记住结果，同一个事件后面再问都是这个数。
 * 这样中途有人改了伤害吸收（审判庭壁垒在 HIGH 把超出的部分退回黄心）也不影响后面的监听器认它打实。
 * 记录按「目标 + 伤害源对象」存：这一下的两个事件之间又冒出一下打同一个目标的（某个监听器顺手又打了它一下），各认各的，不互相顶掉；
 * 同一个目标被同一个伤害源对象在一刻里打两下时后一下顶掉前一下（前一下的伤害事件早已发完）。
 * 只记挨打时身上有伤害吸收的（没有就不可能吃掉），两张表本刻末清空，每张最多 {@value #MAX_ENTRIES} 条（满了就按没吃黄心算）。只在服务端线程读写。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID)
public final class ECLandedHit {

    /** 每张表一刻最多记这么多条 */
    public static final int MAX_ENTRIES = 4096;
    /** 伤害吸收少了多少才算吃掉了（浮点误差以上） */
    private static final float ABSORPTION_EPS = 1.0E-4F;

    /** 记录的键：目标和伤害源都按对象比（实体、伤害源都不该按内容相等） */
    private record NoteKey(LivingEntity target, DamageSource source) {
        @Override
        public boolean equals(Object other) {
            return other instanceof NoteKey key && key.target == target && key.source == source;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(target) + System.identityHashCode(source);
        }
    }

    /** （目标, 伤害源）→ 挨这一下时目标的伤害吸收 */
    private static final Map<NoteKey, Float> NOTES = new HashMap<>();
    /** 已经问过的伤害事件 → 吃掉的伤害吸收 */
    private static final Map<LivingDamageEvent, Float> RESOLVED = new IdentityHashMap<>();

    private ECLandedHit() {
    }

    /** 这一下打实了：没被取消，最终伤害 &gt; 0 或者打掉了伤害吸收（见类注释）。在 {@code LivingDamageEvent} 的监听器里调 */
    public static boolean landed(LivingDamageEvent event) {
        return !event.isCanceled() && (event.getAmount() > 0F || absorbed(event) > 0F);
    }

    /**
     * 这一下吃掉了目标多少伤害吸收（没吃、没有记录是 0）。同一个事件第一次问时算出来记住，之后再问照给这个数（见类注释）。
     * 不看事件取消没有，要连取消一起判断用 {@link #landed}。
     */
    public static float absorbed(LivingDamageEvent event) {
        Float known = RESOLVED.get(event);
        if (known != null) {
            return known;
        }
        LivingEntity target = event.getEntity();
        float eaten = 0F;
        Float before = NOTES.isEmpty() ? null : NOTES.remove(new NoteKey(target, event.getSource()));
        if (before != null) {
            float diff = before - target.getAbsorptionAmount();
            eaten = diff > ABSORPTION_EPS && Float.isFinite(diff) ? diff : 0F;
        }
        if (RESOLVED.size() < MAX_ENTRIES) {
            RESOLVED.put(event, eaten);
        }
        return eaten;
    }

    /** 记下挨打时的伤害吸收（最低优先级、不收已取消的、伤害 ≤ 0 不记：原版见到 ≤ 0 不再往下走） */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLandedHitHurtNote(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || !(event.getAmount() > 0F)) {
            return;
        }
        NoteKey key = new NoteKey(target, event.getSource());
        float absorption = target.getAbsorptionAmount();
        if (!(absorption > 0F)) {
            if (!NOTES.isEmpty()) {
                NOTES.remove(key);
            }
            return;
        }
        if (NOTES.size() < MAX_ENTRIES || NOTES.containsKey(key)) {
            NOTES.put(key, absorption);
        }
    }

    @SubscribeEvent
    public static void onLandedHitServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            clear();
        }
    }

    @SubscribeEvent
    public static void onLandedHitServerStopped(ServerStoppedEvent event) {
        clear();
    }

    private static void clear() {
        if (!NOTES.isEmpty()) {
            NOTES.clear();
        }
        if (!RESOLVED.isEmpty()) {
            RESOLVED.clear();
        }
    }
}
