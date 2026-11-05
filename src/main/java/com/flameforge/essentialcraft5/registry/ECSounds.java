package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * 公共音效注册入口（第三批框架 G2），登记进 {@link ECRegistries#SOUND_EVENTS}。
 * <p>
 * 注册名照原作 SoundRegistry 的写法带点：原作 {@code essentialcraft:sound.tinnitus} → {@code essentialcraft5:sound.tinnitus}。
 * 一个音效要三样东西对上：
 * <ol>
 *   <li>代码：{@code public static final RegistryObject<SoundEvent> TINNITUS = ECSounds.register("sound.tinnitus");}</li>
 *   <li>定义：模块自己的 {@code tools/sounds/<模块>.json}，键 = 注册名的路径，格式就是 sounds.json 的条目
 *       （datagen 的 ECSoundsProvider 合并成 assets/essentialcraft5/sounds.json，同名定义两次直接报错）：
 *       <pre>{ "sound.tinnitus": { "sounds": [ { "name": "essentialcraft5:sound/tinnitus", "stream": false } ] } }</pre></li>
 *   <li>文件：{@code assets/essentialcraft5/sounds/sound/tinnitus.ogg}（原作在 assets/essentialcraft/sounds/sound/，用 git show 拷出来）。</li>
 * </ol>
 * RegistryFrameworkTests 会查：本命名空间注册了的每个音效在合并后的 sounds.json 里都有定义，定义里引用的 .ogg 都存在（要先跑 runData）。
 * <p>
 * 播放：只给一个人听用 {@code player.playNotifySound(X.get(), …)}（服务端发给本人）；给周围的人听用 {@code level.playSound(null, …)}；
 * 纯客户端特效（心智悖论的耳鸣）在客户端 {@code level.playLocalSound} / {@code SoundManager}。
 * ECMRUCU 的 sound.mrucu_noise 仍在 ECMRUCU 里用 RegisterEvent 注册（不迁移）。
 */
public final class ECSounds {

    /**
     * 电弧击中（第四批框架 F4-14；特斯拉线圈放电命中时播，原作音量 2、音高 2，{@code TileMRUCoil} L219）。
     * <b>没有拷原作的 lightning_hit.ogg</b>：它的 Vorbis 注释写着 {@code title=http://www.freesfx.co.uk}，FreeSFX 的条款禁止再分发、
     * 和 CC BY-SA 不兼容，CREDITS.md 已写明「不收录」。定义（{@code tools/sounds/framework4.json}）先指向原版事件
     * {@code minecraft:entity.lightning_bolt.impact}（{@code "type": "event"}），以后有了能用的录音再换成
     * {@code essentialcraft5:sound/lightning_hit} 文件，代码不用改。
     */
    public static final RegistryObject<SoundEvent> LIGHTNING_HIT = register("sound.lightning_hit");

    /**
     * 电流声（第四批框架 F4-14；线圈基座冒火花、反应堆运转时播，原作基座音量 0.1、反应堆 5% 概率音量 1）。
     * <b>没有用原作的 gen_electricity.ogg</b>（第八批 D9）：它和两个 freesfx 音效同一次提交加入、编码器标记逐字相同，来源说不清。
     * 定义（{@code tools/sounds/framework4.json}）指向原版 {@code minecraft:entity.lightning_bolt.impact}，压低音量、升调，听着像一下电火花。
     */
    public static final RegistryObject<SoundEvent> GEN_ELECTRICITY = register("sound.gen_electricity");

    private ECSounds() {
    }

    /** 由 ECRegistries.register() 调，只为触发类加载 */
    static void init() {
    }

    /** 随距离衰减的普通音效（原版默认，16 格外听不见，音量大于 1 时按比例变远） */
    public static RegistryObject<SoundEvent> register(String name) {
        return ECRegistries.SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(EssentialCraft5.id(name)));
    }

    /** 固定听得见距离的音效（格） */
    public static RegistryObject<SoundEvent> register(String name, float range) {
        return ECRegistries.SOUND_EVENTS.register(name, () -> SoundEvent.createFixedRangeEvent(EssentialCraft5.id(name), range));
    }
}
