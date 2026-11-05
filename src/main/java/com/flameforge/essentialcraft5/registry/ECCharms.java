package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.item.CharmItem;
import com.flameforge.essentialcraft5.item.MRUConsumableItem;
import com.flameforge.essentialcraft5.item.MagicalWingsItem;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;

/**
 * 护符与随身物品模块（第三批 §3.1）：十种护符、绿宝石之心、魔力猪排、魔力水瓶、魔力之翼。全是原作 ItemMRUGeneric 系（自带 5000 MRU，创造栏放空 / 满两份）。
 * <p>
 * 新旧注册名（总表见 tools/id_map_items.json）：
 * <ul>
 *   <li>charm:0~9（Fire、Water、Earth、Air、Steam、Magma、Lightning、Life、Rain、Dust）→ fire_charm、water_charm、earth_charm、air_charm、steam_charm、
 *       magma_charm、lightning_charm、life_charm、rain_charm、dust_charm（{@link CharmItem}，Curios 项链槽）</li>
 *   <li>emeraldHeart → emerald_heart；magicalPorkchop → magical_porkchop；magicWaterBottle → magic_water_bottle（{@link MRUConsumableItem}）</li>
 *   <li>magicalWings → magical_wings（{@link MagicalWingsItem}，Curios 腰带槽；按住跳跃的开关包用框架的 network/C2SWingsPacket + WingsFlight）</li>
 * </ul>
 * 进哪个饰品槽靠物品标签（这里 ItemSpec 带 curios:necklace / curios:belt）。标签 {@link #CHARMS}（十种护符，手册进度用）和
 * {@link #DUST_CHARM_GROUND}（沙尘护符认的地面）写在 tools/tags/eccharms.json。
 * 文字 tools/lang/keys/eccharms.json，配方 data/essentialcraft5/recipes/charms/，研究手册「护符与随身物品」assets/essentialcraft5/guide/charms.json，
 * 客户端（戴着翅膀按住跳跃往上飞）client/CharmsClient。
 */
public final class ECCharms {

    /** 十种护符（物品标签，研究手册的「拿到任意一种护符」用） */
    public static final TagKey<Item> CHARMS = ECTags.modItem("charms");
    /** 沙尘护符认的地面（方块标签）：原作 1.12 的 Material.SAND——沙子、红沙、沙砾、灵魂沙、混凝土粉末，另加可疑的沙子 / 沙砾 */
    public static final TagKey<Block> DUST_CHARM_GROUND = ECTags.modBlock("dust_charm_ground");

    public static final RegistryObject<CharmItem> FIRE_CHARM = charm(CharmItem.Kind.FIRE);
    public static final RegistryObject<CharmItem> WATER_CHARM = charm(CharmItem.Kind.WATER);
    public static final RegistryObject<CharmItem> EARTH_CHARM = charm(CharmItem.Kind.EARTH);
    public static final RegistryObject<CharmItem> AIR_CHARM = charm(CharmItem.Kind.AIR);
    public static final RegistryObject<CharmItem> STEAM_CHARM = charm(CharmItem.Kind.STEAM);
    public static final RegistryObject<CharmItem> MAGMA_CHARM = charm(CharmItem.Kind.MAGMA);
    public static final RegistryObject<CharmItem> LIGHTNING_CHARM = charm(CharmItem.Kind.LIGHTNING);
    public static final RegistryObject<CharmItem> LIFE_CHARM = charm(CharmItem.Kind.LIFE);
    public static final RegistryObject<CharmItem> RAIN_CHARM = charm(CharmItem.Kind.RAIN);
    public static final RegistryObject<CharmItem> DUST_CHARM = charm(CharmItem.Kind.DUST);

    /** 原作 bFull3D = true（模型 essentialcraft:tool）→ handheld */
    public static final RegistryObject<MRUConsumableItem> EMERALD_HEART = ECItems.register("emerald_heart",
            () -> new MRUConsumableItem(new Item.Properties().stacksTo(1), MRUConsumableItem.Kind.EMERALD_HEART),
            ItemSpec.handheld("item/tools/emeraldheart"));
    public static final RegistryObject<MRUConsumableItem> MAGICAL_PORKCHOP = ECItems.register("magical_porkchop",
            () -> new MRUConsumableItem(new Item.Properties().stacksTo(1), MRUConsumableItem.Kind.MAGICAL_PORKCHOP),
            ItemSpec.generated("item/tools/magicalporkchop"));
    public static final RegistryObject<MRUConsumableItem> MAGIC_WATER_BOTTLE = ECItems.register("magic_water_bottle",
            () -> new MRUConsumableItem(new Item.Properties().stacksTo(1), MRUConsumableItem.Kind.MAGIC_WATER_BOTTLE),
            ItemSpec.generated("item/tools/magicwaterbottle"));

    /** 贴图是 4 帧动画（magicalwings.png.mcmeta，已导入） */
    public static final RegistryObject<MagicalWingsItem> MAGICAL_WINGS = ECItems.register("magical_wings",
            () -> new MagicalWingsItem(new Item.Properties().stacksTo(1)),
            ItemSpec.generated("item/tools/magicalwings").tags(ECTags.CURIOS_BELT));

    private ECCharms() {
    }

    /** 本模块没有配置和方块，登记全在静态字段里；init() 只是让类被加载 */
    public static void init() {
    }

    private static RegistryObject<CharmItem> charm(CharmItem.Kind kind) {
        return ECItems.register(kind.registryName(),
                () -> new CharmItem(new Item.Properties().stacksTo(1), kind),
                ItemSpec.generated("item/tools/charm" + kind.id()).tags(ECTags.CURIOS_NECKLACE));
    }
}
