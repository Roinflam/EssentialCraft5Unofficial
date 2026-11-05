package com.flameforge.essentialcraft5.registry;

import com.flameforge.essentialcraft5.item.BoundGemItem;
import com.flameforge.essentialcraft5.item.MRUStorageItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 物品注册入口（方块物品归 ECBlocks）。各模块用 {@link #register} 登记物品和它的 {@link ItemSpec}。
 * <p>
 * 这里放的是框架验证时移植的几样：bound_gem；storage:0~4 → soul_shard / soul_sphere / dark_soul_matter /
 * red_soul_matter / matter_of_eternity。新旧注册名总表见 tools/id_map_items.json。
 */
public final class ECItems {

    public record Entry(RegistryObject<? extends Item> item, ItemSpec spec) {
    }

    private static final List<Entry> ALL = new ArrayList<>();

    public static final RegistryObject<BoundGemItem> BOUND_GEM = register("bound_gem",
            () -> new BoundGemItem(new Item.Properties()),
            ItemSpec.of((provider, item) -> provider.boundGem(item)));

    /** 原作 ItemMRUStorageEC 的 5 个 meta，上限 {500, 5000, 30000, 120000, 1000000}，Baubles 戒指 → Curios 戒指位 */
    public static final RegistryObject<MRUStorageItem> SOUL_SHARD = storage("soul_shard", 500, "soulshard");
    public static final RegistryObject<MRUStorageItem> SOUL_SPHERE = storage("soul_sphere", 5000, "soulsphere");
    public static final RegistryObject<MRUStorageItem> DARK_SOUL_MATTER = storage("dark_soul_matter", 30000, "darksoulmatter");
    public static final RegistryObject<MRUStorageItem> RED_SOUL_MATTER = storage("red_soul_matter", 120000, "redsoulmatter");
    public static final RegistryObject<MRUStorageItem> MATTER_OF_ETERNITY = storage("matter_of_eternity", 1000000, "eternitymatter");

    private ECItems() {
    }

    static void init() {
    }

    public static List<Entry> entries() {
        return Collections.unmodifiableList(ALL);
    }

    public static <I extends Item> RegistryObject<I> register(String name, Supplier<I> item, ItemSpec spec) {
        RegistryObject<I> object = ECRegistries.ITEMS.register(name, item);
        ALL.add(new Entry(object, spec));
        return object;
    }

    private static RegistryObject<MRUStorageItem> storage(String name, int maxMRU, String texture) {
        return register(name, () -> new MRUStorageItem(new Item.Properties().stacksTo(1), maxMRU),
                ItemSpec.generated("item/storages/" + texture).tags(ECTags.CURIOS_RING));
    }
}
