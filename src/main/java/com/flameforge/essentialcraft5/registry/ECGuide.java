package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.client.guide.GuideScaleConfig;
import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.item.ResearchBookItem;
import com.flameforge.essentialcraft5.player.ResearchBookGift;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

/**
 * 指南模块（ECGuide）：研究手册物品，第一次进服送一本。界面、内容、查阅都在客户端，见 client/guide（自带事件订阅，不用接线；
 * 界面引擎是通用的指南引擎，2026-09-28 换上）。
 * <p>
 * 新旧名：research_book ← research_book（ItemKnowledgeBook）。原作按 NBT tier 分 5 个等级，这里只有一本（见 ResearchBookItem）。
 * 配方：data/essentialcraft5/recipes/guide/research_book.json（原作 research_book_0：书 + 青 / 品红 / 黄 / 黑染料 + 羽毛）。
 * 手册内容：assets/essentialcraft5/guide/<语言>/<分类>.json（文字直接写在里面），格式说明 tools/guide/FORMAT.md；
 * 界面字样 tools/lang/keys/guide_ui.json（guide.essentialcraft5.ui.*）。
 * 流程教程的「完成」进度：data/essentialcraft5/advancements/guide/*.json（没有 display，不出现在进度界面）。
 * 送书：player/ResearchBookGift（服务端配置 general.give_research_book_on_first_join，在 init 里登记）。
 * 手册的界面大小：CLIENT 配置 [guide] gui_scale（client/guide/GuideScaleConfig，common 类，在 init 里登记；2026-10-13 起手册用自己的界面倍数）。
 */
public final class ECGuide {

    public static final RegistryObject<ResearchBookItem> RESEARCH_BOOK = ECItems.register("research_book",
            () -> new ResearchBookItem(new Item.Properties().stacksTo(1)),
            ItemSpec.generated("item/book_knowledge"));

    private ECGuide() {
    }

    public static void init() {
        ECConfig.general(ResearchBookGift.Config::define);
        ECConfig.client(GuideScaleConfig::define);
    }
}
