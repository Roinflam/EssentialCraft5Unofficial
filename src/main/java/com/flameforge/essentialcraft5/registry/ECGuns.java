package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.client.GunsClient;
import com.flameforge.essentialcraft5.client.model.GunModelBuilder;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.gun.GunConfig;
import com.flameforge.essentialcraft5.gun.GunType;
import com.flameforge.essentialcraft5.item.GunItem;
import com.flameforge.essentialcraft5.network.ECNetwork;
import com.flameforge.essentialcraft5.network.S2CGunBeamPacket;
import com.flameforge.essentialcraft5.network.S2CGunMatrixHitPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

/**
 * 第五批「枪与射线」模块的注册类（计划 tools/plan/batch5.md §3.1、§4.5）。<b>框架（F5-5）建的骨架</b>：伤害类型键、射线音效、
 * 配置 {@code general.guns}（{@link GunConfig}）、光束包 {@link S2CGunBeamPacket}；四把枪（{@code pistol}、{@code rifle}、{@code sniper}、{@code gatling}）
 * 由 ECGuns 模块加在下面（{@link #PISTOL} 等），写法：
 * <pre>
 * public static final RegistryObject&lt;GunItem&gt; PISTOL = ECItems.register("pistol",
 *         () -&gt; new GunItem(GunType.PISTOL, new Item.Properties().stacksTo(1)), ItemSpec.of(...));
 * </pre>
 * 共享核心在 {@code gun/} 包：{@code GunType}（枪种、每发 MRU 系数、开火方式、工作台布局）、{@code GunMaterials}（材料表）、
 * {@code GunParts}（NBT 子标签 gun）、{@code GunStats}（属性现算）、{@code GunConfig}。武器工作台是另一个注册类（ECWeaponMaker），
 * 按 {@code GunType.itemId()} 从注册表取枪物品，不 import 本模块的类。
 *
 * <h2>伤害类型 {@code essentialcraft5:mru_ray}</h2>
 * 定义是静态 JSON {@code data/essentialcraft5/damage_type/mru_ray.json}：{@code message_id essentialcraft5.mru_ray}、
 * {@code scaling when_caused_by_living_non_player}、{@code exhaustion 0.1}。<b>不带 {@code minecraft:is_projectile}</b>（D1 取 A）：
 * 伤害源写成 {@code new DamageSource(ECDamageTypes.holder(level, ECGuns.MRU_RAY), null, 射手)}——直接实体为空、causing = 射手。
 * PvP 开关、队伍友伤、Forge 端领地模组（{@code LivingAttackEvent}）、混合端插件（{@code EntityDamageByEntityEvent}，攻击者取 {@code getEntity()}）
 * 都照攻击者管；伤害源没有位置，盾挡不住、末影人打得到（带 is_projectile 的话末影人会瞬移躲开，{@code EnderMan.hurt}）、弹射物保护不减伤，同原作。
 * 不在 {@code bypasses_armor}、{@code no_death_loot} 里：护甲照算，打死的怪照常掉落（玩家击杀）。
 * 死亡消息（1.20.1 有攻击者时只用这两个键，ECGuns 模块写）：{@code death.attack.essentialcraft5.mru_ray}、{@code ….item}。
 *
 * <h2>音效 {@code sound.beam}</h2>
 * 原作 {@code sound/beam_start.ogg}（Vorbis 注释只有编码器，没有 freesfx 这类第三方标记；和第七批收下的恶魔音效同一次提交加入，编码器版本也和两个 freesfx 音效不同），
 * 定义在 {@code tools/sounds/ecguns.json}，字幕键 {@code subtitles.essentialcraft5.beam}（{@code tools/lang/keys/framework5.json}）。
 */
public final class ECGuns {

    /** 射线伤害类型（见类注释） */
    public static final ResourceKey<DamageType> MRU_RAY = ResourceKey.create(Registries.DAMAGE_TYPE, EssentialCraft5.id("mru_ray"));

    /** 射线开火声（原作 SoundRegistry.gunBeam；音量 0.1 + 伤害 / 100、音高 2 − 伤害 / 50） */
    public static final RegistryObject<SoundEvent> BEAM = ECSounds.register("sound.beam");

    /**
     * 矩阵受损（GUNS-4 的损坏特效，客户端 {@code client/render/MatrixHitEffects} 在被打的人身上本地播）：
     * 定义在 {@code tools/sounds/ecguns.json}，引用原版紫水晶簇碎裂的音效事件，字幕键 {@code subtitles.essentialcraft5.matrix_damage}（{@code tools/lang/keys/ecguns.json}）
     */
    public static final RegistryObject<SoundEvent> MATRIX_DAMAGE = ECSounds.register("sound.matrix_damage");

    // ------------------------------------------------------------------
    // 四把枪（ECGuns 模块，batch5.md §3.1）。原作 gun.pistol / gun.rifle / gun.sniper / gun.gatling，叠 1。
    // 物品模型：父模型 forge:item/default-tool（= handheld 的变换）+ 自定义 loader essentialcraft5:gun（client/model/GunModel），底图 item/guns/<枪>/<枪>_generic，
    // 部件图层按 NBT 现拼（client/model/GunModel 的覆盖表）。创造栏每种放空 / 满两把没组装的（MRUStaffItem），右键随机组装。
    // ------------------------------------------------------------------

    public static final RegistryObject<GunItem> PISTOL = gun(GunType.PISTOL);
    public static final RegistryObject<GunItem> RIFLE = gun(GunType.RIFLE);
    public static final RegistryObject<GunItem> SNIPER = gun(GunType.SNIPER);
    public static final RegistryObject<GunItem> GATLING = gun(GunType.GATLING);

    private ECGuns() {
    }

    /** 注册一把枪；模型 lambda 只在 datagen 时执行（GunModelBuilder 在 client 包，专用服不会加载） */
    private static RegistryObject<GunItem> gun(GunType type) {
        return ECItems.register(type.id(), () -> new GunItem(type, new Item.Properties().stacksTo(1)),
                ItemSpec.of((provider, item) -> GunModelBuilder.gunItem(provider, item, type)));
    }

    public static void init() {
        ECConfig.general(GunConfig::define);
        // 处理器只在客户端调（registerS2C 已套 DistExecutor）；lambda 体里的 GunsClient 到执行时才加载，专用服碰不到。
        // GunsClient 按射手是不是自己定起点（自己的从枪口、别人的从眼睛往前让开），再交给 BeamEffects 画
        ECNetwork.registerS2C(S2CGunBeamPacket.class, S2CGunBeamPacket::encode, S2CGunBeamPacket::decode,
                packet -> GunsClient.onBeamPacket(packet));
        // 矩阵受损特效（GUNS-4）：排在光束包后面；协议号仍是 4（第五批还没发布）
        ECNetwork.registerS2C(S2CGunMatrixHitPacket.class, S2CGunMatrixHitPacket::encode, S2CGunMatrixHitPacket::decode,
                packet -> GunsClient.onMatrixHitPacket(packet));
    }
}
