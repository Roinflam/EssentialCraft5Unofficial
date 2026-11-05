package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.entity.LeyStarfallEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.RegistryObject;

/**
 * 第十三批 13d-H2「环境机制」的注册类（batch13.md §6.2、§9.4；HS §2.2）：
 * <ul>
 *   <li>地脉落晶实体 {@code ley_starfall}（{@link LeyStarfallEntity}，不是 Mob、不存盘、追踪 4 区块、10 刻同步一次位置）；</li>
 *   <li>音效（定义 tools/sounds/echoannastorm.json，都指原版声音改音高）：风暴开始 / 结束（只给在海纳尔的人各放一声）、落晶生成（固定 48 格听得见，
 *       落点在 12~40 格外）和收取。</li>
 * </ul>
 * 第五种天灾 {@code HoannaEvent.LEY_STORM} 的掷骰、时长、落晶、露天辐射在 {@code hoanna/event/LeyStorm} 和 {@code HoannaEvents}；
 * 配置挂在 {@code general.hoanna_events} 下（{@code HoannaHazardsConfig}）；指令沿用 {@code /ec5 hoanna_event start ley_storm}。
 * 潮汐接线：MRU 光幕（{@code client/hoanna/HoannaSkyRenderer}）、富集仓坍缩（{@code EnrichmentControllerBlockEntity.collapseRate}）。
 * 辐射热点的细尘、风暴的光点在 {@code client/hoanna/HoannaStormVisuals}，客户端开关 {@code [hoanna] hotspot_particles}。
 * 文字进 tools/lang/keys/echoannastorm.json；客户端注册在 {@code client/HoannaStormClient}。
 */
public final class ECHoannaStorm {

    /** 0.5 × 0.5；不存盘（noSave）；追踪 4 区块、每 10 刻同步一次（下落两端各自算，位置包只做校正） */
    public static final RegistryObject<EntityType<LeyStarfallEntity>> LEY_STARFALL = ECEntities.register("ley_starfall",
            EntityType.Builder.<LeyStarfallEntity>of(LeyStarfallEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).noSave().fireImmune().clientTrackingRange(4).updateInterval(10));

    public static final RegistryObject<SoundEvent> LEY_STORM_START = ECSounds.register("sound.ley_storm.start");
    public static final RegistryObject<SoundEvent> LEY_STORM_END = ECSounds.register("sound.ley_storm.end");
    /** 落点在 12~40 格外，按原版随距离衰减（16 格）就听不见了 */
    public static final RegistryObject<SoundEvent> STARFALL_SPAWN = ECSounds.register("sound.ley_starfall.spawn", 48F);
    public static final RegistryObject<SoundEvent> STARFALL_COLLECT = ECSounds.register("sound.ley_starfall.collect");

    private ECHoannaStorm() {
    }

    /** 由 ECRegistries.register() 调（模组构造阶段）：类加载即登记静态字段里的条目 */
    public static void init() {
    }
}
