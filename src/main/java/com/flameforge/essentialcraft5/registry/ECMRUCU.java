package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.effect.MRUCorruptionEffect;
import com.flameforge.essentialcraft5.entity.MRUPresenceEntity;
import com.flameforge.essentialcraft5.item.MRUMoverItem;
import com.flameforge.essentialcraft5.worldgen.ElderMRUCUFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;

/**
 * MRU 聚集体模块：MRU 聚集体实体、MRU 腐化效果、MRUCU 移动魔杖、上古 MRU 聚集体遗迹。
 * 机器的腐化泄漏（{@link com.flameforge.essentialcraft5.mru.ECCorruption}）也由本模块实现。
 * <p>
 * 新旧名对照：实体 EntityMRUPresence → mru_presence；药水 potion.mrucorruption → 效果 mru_corruption；
 * 物品 mruMover1 → mru_mover（二阶 mruMover_t2 在 ECGear）；世界生成 WorldGenElderMRUCU → 特征 elder_mrucu
 * （数据文件 worldgen/configured_feature|placed_feature/elder_mrucu.json、forge/biome_modifier/elder_mrucu.json）。
 * <p>
 * ECRegistries 目前没有实体类型、药水效果、世界生成特征的 DeferredRegister（共用文件不能改），
 * 所以这三样在本类监听模组总线的 {@link RegisterEvent} 自己注册，字段用 {@link RegistryObject#create} 按名字取。
 * 本类带 {@code @Mod.EventBusSubscriber}，模组构造完成后就会被加载，物品也随之登记，不依赖 init() 的调用时机；
 * init() 只负责登记配置（必须在 ECConfig.register 之前）。框架以后加了对应的 DeferredRegister，把 {@link #register} 换掉即可。
 */
@Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ECMRUCU {

    private static final ResourceLocation MRU_PRESENCE_ID = EssentialCraft5.id("mru_presence");
    private static final ResourceLocation MRU_CORRUPTION_ID = EssentialCraft5.id("mru_corruption");
    private static final ResourceLocation ELDER_MRUCU_ID = EssentialCraft5.id("elder_mrucu");

    /** 原作 EntitiesCore.registerEntity(EntityMRUPresence.class, 64, 1, true)：追踪 64 格、每 tick 同步位置 */
    public static final RegistryObject<EntityType<MRUPresenceEntity>> MRU_PRESENCE =
            RegistryObject.create(MRU_PRESENCE_ID, Registries.ENTITY_TYPE, EssentialCraft5.MODID);

    /** 原作 PotionMRUCorruption（potion.mrucorruption） */
    public static final RegistryObject<MRUCorruptionEffect> MRU_CORRUPTION =
            RegistryObject.create(MRU_CORRUPTION_ID, Registries.MOB_EFFECT, EssentialCraft5.MODID);

    /** 原作 WorldGenElderMRUCU */
    public static final RegistryObject<ElderMRUCUFeature> ELDER_MRUCU =
            RegistryObject.create(ELDER_MRUCU_ID, Registries.FEATURE, EssentialCraft5.MODID);

    /**
     * 原作 mruMover1：setMaxStackSize(1)，没有 setMaxDamage，所以一阶魔杖不会坏（原作里的 damageItem 对它不起作用）。
     * 模型：原作 active=true/false 两张贴图 → 物品属性 essentialcraft5:active（正在使用时为 1），客户端在 MRUCUClient 注册。
     */
    public static final RegistryObject<MRUMoverItem> MRU_MOVER = ECItems.register("mru_mover",
            () -> new MRUMoverItem(new Item.Properties().stacksTo(1)),
            ItemSpec.of((provider, item) -> {
                ItemModelBuilder active = provider.withExistingParent("mru_mover_active", provider.mcLoc("item/handheld"))
                        .texture("layer0", provider.tex("item/tools/mru_mover_t1_active"));
                provider.handheld(item, "item/tools/mru_mover_t1")
                        .override().predicate(MRUMoverItem.ACTIVE_PREDICATE, 1F).model(active).end();
            }));

    private ECMRUCU() {
    }

    public static void init() {
        ECConfig.machine(MRUPresenceEntity.Config::define);
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, helper -> helper.register(MRU_PRESENCE_ID,
                EntityType.Builder.<MRUPresenceEntity>of(MRUPresenceEntity::new, MobCategory.MISC)
                        .sized(0.3F, 0.3F)
                        .fireImmune()
                        .clientTrackingRange(4)
                        .updateInterval(1)
                        .build(MRU_PRESENCE_ID.toString())));
        event.register(Registries.MOB_EFFECT, helper -> helper.register(MRU_CORRUPTION_ID, new MRUCorruptionEffect()));
        event.register(Registries.SOUND_EVENT, helper -> helper.register(MRUPresenceEntity.NOISE.getLocation(), MRUPresenceEntity.NOISE));
        event.register(Registries.FEATURE, helper -> helper.register(ELDER_MRUCU_ID,
                new ElderMRUCUFeature(NoneFeatureConfiguration.CODEC)));
    }
}
