package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.entity.MRUArrowEntity;
import com.flameforge.essentialcraft5.entity.WindMageEntity;
import com.flameforge.essentialcraft5.item.WindMageArmorItem;
import com.flameforge.essentialcraft5.item.armor.ECArmorItem;
import com.flameforge.essentialcraft5.player.WindPact;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.RegistryObject;

/**
 * 风法师模块（第三批 §3.5）：实体风法师与 MRU 箭、风法师刷怪蛋、风法师套装 ×4、三张掉落表、主世界自然生成。
 * <p>
 * 新旧名（总表 tools/id_map_items.json；实体不在命名表里，名字按 batch3.md 定）：
 * <ul>
 *   <li>实体 essentialcraft:entitywindmage → {@code essentialcraft5:wind_mage}（{@link WindMageEntity}）；entitymruarrow → {@code mru_arrow}（{@link MRUArrowEntity}）</li>
 *   <li>entityEgg:3（ItemSpawnEggEC）→ wind_mage_spawn_egg（框架 {@link ECEntities#spawnEgg}：原版式，只在创造栏，右键刷怪笼不改类型）</li>
 *   <li>helm_wind / chest_wind / legs_wind / boots_wind → wind_mage_hood / robe / leggings / boots（{@link WindMageArmorItem}；改名免得和注风的 wind_helmet 等混淆）</li>
 *   <li>掉落表 entities/windmage_apprentice / windmage_normal / windmage_archmage → entities/wind_mage_apprentice / wind_mage / wind_mage_archmage</li>
 * </ul>
 * 数据：掉落表 {@code data/essentialcraft5/loot_tables/entities/wind_mage*.json}（瓶中之风三件是 ECWind 的物品，按注册名引用）；
 * 刷怪表 {@code data/essentialcraft5/forge/biome_modifier/wind_mage_spawns.json}（主世界，权重 2、每群 1~6，照原作 EntitiesCore L39；
 * 海纳尔批另加一份给海纳尔群系，不要改这份）。兜帽进 {@code essentialcraft5:mru_vision}：tools/tags/ecwindmage.json。
 * 文字 tools/lang/keys/ecwindmage.json；客户端 client/WindMageClient；手册 assets/essentialcraft5/guide/wind_mage.json；测试 gametest/WindMageTests。
 * 配置：服务端 {@code general.wind_mage}（附近数量上限，{@link WindMageEntity.Config}）；风之契约的三项 {@code general.wind.steadfast_points} /
 * {@code deflect_points} / {@code deflect_cooldown_ticks}（{@link WindPact.Config}，第十批 10b，也在这里登记）。
 */
public final class ECWindMage {

    /** 原作 registerEntity(EntityWindMage.class, 64, 1, true)：追踪 64 格照原作；同步间隔用原版怪物的 3（原作 1） */
    public static final RegistryObject<EntityType<WindMageEntity>> WIND_MAGE = ECEntities.register("wind_mage",
            EntityType.Builder.<WindMageEntity>of(WindMageEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F).clientTrackingRange(4).updateInterval(3));

    /** 原作 registerEntity(EntityMRUArrow.class, 64, 1, true)：追踪 64 格照原作；同步间隔用原版箭的 20（客户端自己模拟弹道） */
    public static final RegistryObject<EntityType<MRUArrowEntity>> MRU_ARROW = ECEntities.register("mru_arrow",
            EntityType.Builder.<MRUArrowEntity>of(MRUArrowEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(4).updateInterval(20));

    /** 蛋壳取法师长袍的深蓝，斑点取袍边的青色（原作刷怪蛋是纯白的） */
    public static final int SPAWN_EGG_BACKGROUND = 0x101D31;
    public static final int SPAWN_EGG_HIGHLIGHT = 0x64CBCB;
    public static final RegistryObject<ECEntities.SpawnEgg> WIND_MAGE_SPAWN_EGG =
            ECEntities.spawnEgg("wind_mage_spawn_egg", WIND_MAGE, SPAWN_EGG_BACKGROUND, SPAWN_EGG_HIGHLIGHT);

    // ---------------- 风法师套装（原作 ItemsCore L171~182） ----------------

    public static final RegistryObject<WindMageArmorItem> WIND_MAGE_HOOD = armor("wind_mage_hood", ArmorItem.Type.HELMET, 128, Tags.Items.ARMORS_HELMETS);
    public static final RegistryObject<WindMageArmorItem> WIND_MAGE_ROBE = armor("wind_mage_robe", ArmorItem.Type.CHESTPLATE, 384, Tags.Items.ARMORS_CHESTPLATES);
    public static final RegistryObject<WindMageArmorItem> WIND_MAGE_LEGGINGS = armor("wind_mage_leggings", ArmorItem.Type.LEGGINGS, 256, Tags.Items.ARMORS_LEGGINGS);
    public static final RegistryObject<WindMageArmorItem> WIND_MAGE_BOOTS = armor("wind_mage_boots", ArmorItem.Type.BOOTS, 128, Tags.Items.ARMORS_BOOTS);

    private ECWindMage() {
    }

    public static void init() {
        ECEntities.attributes(WIND_MAGE, WindMageEntity::createAttributes);
        ECEntities.spawnPlacement(WIND_MAGE, SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                WindMageEntity.SPAWN_RULES);
        ECConfig.general(WindMageEntity.Config::define);
        // 风之契约（第十批 10b，风法师支线两步）：三项写在 general.wind 里，排在 ECWind 那几项后面
        ECConfig.general(WindPact.Config::define);
    }

    /** 3D 图标照框架的写法（同二批魔力防护套装）；粒子贴图借魔力板（盔甲贴图不在图集里） */
    private static RegistryObject<WindMageArmorItem> armor(String name, ArmorItem.Type type, int durability, TagKey<Item> forgeTag) {
        return ECItems.register(name, () -> new WindMageArmorItem(type, new Item.Properties().durability(durability)),
                ECArmorItem.icon3d("item/misc/magicplate").tags(forgeTag));
    }
}
