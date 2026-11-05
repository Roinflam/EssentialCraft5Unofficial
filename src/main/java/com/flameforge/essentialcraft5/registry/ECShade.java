package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.MobMaterialItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.RegistryObject;

/**
 * 第十三批 13e-C5「暗影线」的注册类（batch13.md §2.2 第二十二章、§3.4 挚契、§3.5 暗影核心、§9.5）。预备先注册了两个物品空壳
 * （八 / 九阶数据 {@code matrix_ranks/rank_8.json}、{@code rank_9.json} 带 {@code forge:item_exists} 条件引用暗影核心，
 * 天灾信标的配方、手册、流程工具都要按注册名取）：
 * <ul>
 *   <li>{@link #SHADE_CORE} 暗影核心：富集仓坍缩的第三分支（暗影地点 + 外壳里 ≥ 4 个影晶）产出；暗渎水晶柱、天灾信标、八 / 九阶祭品要它；</li>
 *   <li>{@link #DEMON_OATH} 恶魔之誓：契约之誉 70「挚契」时潜行拿影晶右键恶魔立誓得到；天灾信标要它。</li>
 * </ul>
 * 13e-C5 往这里加：暗渎水晶柱的生存配方、4 档五芒星、立誓、坍缩分支的接线；音效（如果有）写 {@code tools/sounds/ecshade.json}。
 * 文字在 {@code tools/lang/keys/ecshade.json}。服务端配置 {@code general.shade}（{@link Config}；坍缩分支的数值照 C5 细则放
 * {@code machines.enrichment_chamber.shade.*}，不在这一节）。两个物品现在是普通材料（{@link MobMaterialItem}：提示框一行灰字），C5 要换类就换。
 */
public final class ECShade {

    /** 暗影核心：一组 64 */
    public static final RegistryObject<MobMaterialItem> SHADE_CORE = ECItems.register("shade_core",
            () -> new MobMaterialItem(new Item.Properties().rarity(Rarity.RARE), "shade_core"), ItemSpec.generated("item/shade_core"));

    /** 恶魔之誓：一组 16 */
    public static final RegistryObject<MobMaterialItem> DEMON_OATH = ECItems.register("demon_oath",
            () -> new MobMaterialItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), "demon_oath"), ItemSpec.generated("item/demon_oath"));

    private ECShade() {
    }

    /** 由 ECRegistries.register() 调（模组构造阶段）：类加载即登记静态字段里的条目；配置在这里登记 */
    public static void init() {
        ECConfig.general(Config::define);
    }

    /** 服务端配置 {@code general.shade}（立誓花费、挚契的提示这类），13e-C5 填 */
    public static final class Config {

        private Config() {
        }

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("shade");
            builder.pop();
        }
    }
}
