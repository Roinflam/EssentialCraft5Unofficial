package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.item.ElementalTier;
import com.flameforge.essentialcraft5.item.MagicalDiggerItem;
import com.flameforge.essentialcraft5.item.PaleGemItem;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

/**
 * 魔力挖掘器与淡青宝石模块（第四批 4b，batch4 §3.10）。原计划里的 7 种采石场升级已随采石场去掉（batch4 §0.0，2026-09-28 起机器不自动挖方块、放方块）。
 * <p>
 * 新旧注册名：magicaldigger → magical_digger；genitem meta 40（gemPale）→ pale_gem。
 * <ul>
 *   <li>魔力挖掘器（{@link MagicalDiggerItem}）：玩家手持的 MRU 万能镐，范围挖按玩家本人的权限逐格走真破坏事件，推迟到本 tick 末尾（{@code item/MagicalDiggerEvents}）。
 *       工具材料照原作 elemental（{@link ElementalTier#ELEMENTAL}：攻击加成 5、附魔能力 36），没有耐久；原作 ItemPickaxe → 攻击 1 + 材料加成、速度 −2.8（同 ECGear 的元素镐）。
 *       原作 blockstate 用 essentialcraft:tool 模型 → handheld；物品标签同元素镐（镐、紫水晶簇满掉落）。</li>
 *   <li>淡青宝石（{@link PaleGemItem}）：纯材料。辐射仓的两条配方（淡青块 + 钻石 / 绿宝石 → 4 颗）早已预写在
 *       data/essentialcraft5/recipes/radiating_chamber/pale_gem_from_*.json（带 item_exists），注册即生效；原料仍是 forge 的钻石 / 绿宝石标签（不自循环）。</li>
 * </ul>
 * <h2>淡青宝石当钻石 / 绿宝石（batch4 §3.10，照第二批淡青锭 / 淡青珍珠的先例）</h2>
 * 原作默认把它登记进 gemDiamond / gemEmerald / gemRuby… 矿辞，能在任何模组的配方里当钻石，还能自循环、在米斯莱熔炉里换成真钻石。
 * 这里<b>不进任何 forge 共享标签</b>，只建 EC 内部物品标签 {@link #DIAMONDS}（{@code #forge:gems/diamond} + 淡青宝石）、{@link #EMERALDS}（{@code #forge:gems/emerald} + 淡青宝石），
 * 成员写在 tools/tags/ecdigger.json。只有原作用矿辞 gemDiamond / gemEmerald 的 EC 配方改用它们（钻石板、绿宝石板、MRU 链接器、二阶 MRUCU 移动魔杖、绿宝石之心、
 * MRU 共鸣水晶、灵魂球、鲜红灵魂物质；这些 JSON 归别的模块，由集成的人改）；淡青宝石自己的配方、米斯莱熔炉的钻石 ↔ 绿宝石、注风、矿石加工不换。
 * 不走配方、写死原版物品的两处也不收淡青宝石（照原作）：水晶成型器的钻石格（{@code CrystalFormerBlockEntity.slotRule} 是 {@code Items.DIAMOND}）、
 * 往绿宝石块上丢钻石 / 绿宝石的地面仪式（{@code GroundRituals} 的催化物是 {@code Items.DIAMOND / EMERALD}）；手册「不收它的地方」页列了这些（4b 审查 DIG-2 / INT-3）。
 * <p>
 * 文字 tools/lang/keys/ecdigger.json，标签 tools/tags/ecdigger.json，配方 data/essentialcraft5/recipes/digger/，
 * 研究手册在「装备与防护」章（assets/essentialcraft5/guide/gear.json 的 magical_digger、pale_gem 条目；「采石」章不做了），GameTest gametest/DiggerTests。
 */
public final class ECDigger {

    /** EC 内部「钻石」：{@code #forge:gems/diamond} + 淡青宝石（只给本模组的配方用） */
    public static final TagKey<Item> DIAMONDS = ECTags.modItem("diamonds");
    /** EC 内部「绿宝石」：{@code #forge:gems/emerald} + 淡青宝石（只给本模组的配方用） */
    public static final TagKey<Item> EMERALDS = ECTags.modItem("emeralds");

    /** 原作 ItemMagicalDigger（ItemsCore L67）：叠 1、没有耐久、自带 5000 MRU，创造栏放空 / 满两份 */
    public static final RegistryObject<MagicalDiggerItem> MAGICAL_DIGGER = ECItems.register("magical_digger",
            () -> new MagicalDiggerItem(ElementalTier.ELEMENTAL, 1, -2.8F, new Item.Properties().stacksTo(1)),
            ItemSpec.handheld("item/tools/magicaldigger").tags(ItemTags.PICKAXES, ItemTags.CLUSTER_MAX_HARVESTABLES));

    /** 原作 genitem meta 40（gemPale） */
    public static final RegistryObject<PaleGemItem> PALE_GEM = ECItems.register("pale_gem",
            () -> new PaleGemItem(new Item.Properties()),
            ItemSpec.generated("item/misc/gempale"));

    private ECDigger() {
    }

    /** 由 ECRegistries.register() 调用：类加载即把上面的条目登记进 DeferredRegister（没有配置项） */
    public static void init() {
        ElementalTier.init();
    }
}
