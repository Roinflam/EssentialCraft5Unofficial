package com.flameforge.essentialcraft5.item.armor;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.Event;

/**
 * MRU 盔甲的护盾挡下了一下（Forge 总线，只在服务端发，不能取消；第十三批 13a，batch13.md §4.4）。
 * 纯 Forge 上在 {@link MRUShield} 的 {@code LivingHurtEvent} LOW 里扣过电以后当场发；Mohist 系混合端等这一下的 {@code LivingDamageEvent} 到了、
 * 真的扣过电再发（领地插件拦下的攻击不发，被盾牌整下挡掉的也不发）。各套盔甲「挡下以后」的效果听它（宅男的过载屏障、魔力防护的余流），
 * 不用改 {@link MRUShield}。
 * <p>
 * 数都是 {@code LivingHurtEvent} LOW 那一刻的：{@link #getIncoming()} 是护盾之前的伤害（η 翻倍以后），{@link #getAbsorbed()} 是护盾吃掉的部分，
 * {@link #getRemaining()} 是留给原版护甲和生命的部分（η 多出来的那一半全在这里）。{@link #isWhole()} = 这一下被整下吃掉、事件被取消了。
 */
public class MRUShieldAbsorbedEvent extends Event {

    private final Player player;
    private final DamageSource source;
    private final float incoming;
    private final float absorbed;
    private final float remaining;

    public MRUShieldAbsorbedEvent(Player player, DamageSource source, float incoming, float absorbed, float remaining) {
        this.player = player;
        this.source = source;
        this.incoming = incoming;
        this.absorbed = absorbed;
        this.remaining = remaining;
    }

    public Player getPlayer() {
        return player;
    }

    public DamageSource getSource() {
        return source;
    }

    /** 护盾之前的伤害 */
    public float getIncoming() {
        return incoming;
    }

    /** 护盾吃掉的伤害 */
    public float getAbsorbed() {
        return absorbed;
    }

    /** 护盾以后剩下的伤害（整下吃掉时是 0） */
    public float getRemaining() {
        return remaining;
    }

    /** 整下吃掉了 */
    public boolean isWhole() {
        return remaining <= 0F;
    }
}
