package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.effect.MindfoldParadoxEffect;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * 腐化效果模块（第三批 batch3.md §3.6）：25 种腐化效果的<b>行为</b>、心智悖论效果、耳鸣和心跳两个音效、研究手册资料章「腐化效果」。
 * <p>
 * 分工：效果的数据表（id、类别、成本、概率、图标）、玩家身上的效果列表、{@code general.corruption} 配置、{@code /ec5 player … corruption} 指令
 * 和 25 个效果名字的翻译键都是框架的（{@code api/CorruptionEffects}、{@code player/PlayerSystems}、{@code command/CorruptionCommands}）；本模块只写：
 * <ul>
 *   <li>{@code corruption/CorruptionTicker}：服务端玩家 tick，每 1 200 tick 抽一次效果、每 20 tick 跑一遍身上的效果、给手册步骤打勾；</li>
 *   <li>{@code corruption/CorruptionBehaviors}：每种效果发作时做什么（原作 CorruptionEffectECNBTBased.onPlayerTick L152~363，按 policy 改过的见类注释）；</li>
 *   <li>{@code effect/MindfoldParadoxEffect} + {@code client/CorruptionClient}：心智悖论（原作 PotionMindfoldParadox + RenderHandlerEC 的客户端特效，自定义天空按 E9 砍掉）。</li>
 * </ul>
 * 新旧注册名：原作 {@code essentialcraft:potion.paradox} → {@code essentialcraft5:mindfold_paradox}；音效 {@code essentialcraft:sound.tinnitus}、
 * {@code sound.heartbeat} → {@code essentialcraft5:sound.tinnitus}、{@code sound.heartbeat}（原作 sounds.json 同名，.ogg 用 git show 拷出来）。
 * 效果和音效不在命名表的范围里（batch3.md §9）。
 * <p>
 * 文字在 tools/lang/keys/eccorruption.json（每种效果发作时的话 {@code corruption_effect.essentialcraft5.<id>.message}、「得到效果」那句、手册），
 * 音效定义在 tools/sounds/eccorruption.json，手册在 assets/essentialcraft5/guide/corruption.json，手册步骤的进度在 data/essentialcraft5/advancements/guide/corruption/。
 * <p>
 * 注意和 {@code mru/ECCorruption}（机器泄漏 MRU，第一批 ECMRUCU 实现）同名不同包：那个是泄漏，这个是腐化效果模块的注册类。
 */
public final class ECCorruption {

    /** 心智悖论（原作 PotionMindfoldParadox(true, 0xFFFFFF)，图标取原作 textures/special/potions.png 的 (7, 2) 格） */
    public static final RegistryObject<MindfoldParadoxEffect> MINDFOLD_PARADOX =
            ECEffects.register("mindfold_paradox", MindfoldParadoxEffect::new);

    /** 耳鸣：心智悖论开始时（原作 PotionMindfoldParadox.performEffect） */
    public static final RegistryObject<SoundEvent> TINNITUS = ECSounds.register("sound.tinnitus");

    /** 心跳：心智悖论期间每 5 秒一次（原作 RenderHandlerEC.onPlayerTick） */
    public static final RegistryObject<SoundEvent> HEARTBEAT = ECSounds.register("sound.heartbeat");

    private ECCorruption() {
    }

    /** 由 ECRegistries.register() 调；静态字段在类加载时已经登记进 DeferredRegister，这里没有别的要登记（配置和指令是框架 PlayerSystems 的） */
    public static void init() {
    }
}
