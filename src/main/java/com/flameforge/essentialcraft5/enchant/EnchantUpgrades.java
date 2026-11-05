package com.flameforge.essentialcraft5.enchant;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.config.ECConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 附魔书升级的规则：两台机器（附魔增幅台、附魔升华台）共用，方块实体、界面、手册都从这里取数。
 * 不改原版铁砧：超上限的书在铁砧上照原版砍回上限，附到装备上交给别的专门处理附魔的模组（改原版铁砧和它们打架）。
 * <p>
 * 一本附魔书只收<b>单一附魔</b>的（多条附魔的书没法说清升哪一条），附魔本身要有不止一级、不是诅咒、没被配置排除。
 * 机器把书的这一条附魔一级一级往上推：增幅台管 1 → 25 级，升华台管 25 → 50 级（只收已经有 25 级的书，所以先过增幅台）。
 * 推到第 n 级这一步要付的东西由 {@link #step} 算出：MRU、ESPE、时间、两格材料，升华台再加一笔矩阵损伤（记在机器主人身上）。
 * <p>
 * 价格曲线（默认值，每台机器还有 mru / espe / 时间三个倍率配置）：
 * <ul>
 *   <li>增幅台：MRU = 2000 × n²，ESPE = 50 × (n − 8)（n ≤ 8 不要），耗时 = 300 + 60 × n 刻；</li>
 *   <li>升华台：MRU = 4500 × n²，ESPE = 300 × (n − 20)，耗时 = 900 + 180 × (n − 25) 刻，矩阵损伤 = 8000 + 2000 × (n − 26)。</li>
 * </ul>
 * 材料按档次分段（{@link #materialA}、{@link #materialB}），越往上越稀有，具体见手册。
 */
public final class EnchantUpgrades {

    /** 两台机器：注册名、书至少要有几级才收、最高升到几级 */
    public enum Tier {
        AMPLIFIER("enchant_amplifier", 1, 25),
        ASCENDER("enchant_ascender", 25, 50);

        public static final Tier[] VALUES = values();

        /** 注册名，也是配置小节名 */
        public final String key;
        /** 书上这条附魔至少几级才收（升华台只收增幅台的成品） */
        public final int minLevel;
        /** 这台机器能把附魔推到的最高等级 */
        public final int cap;

        Tier(String key, int minLevel, int cap) {
            this.key = key;
            this.minLevel = minLevel;
            this.cap = cap;
        }

        @Nullable
        public static Tier byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : null;
        }
    }

    /** 一本书上的那条附魔 */
    public record Entry(Enchantment enchantment, int level) {
    }

    /** 一格材料的要求：数量，和可以用哪几种物品（任选一种，数量算在同一格里） */
    public record Need(int count, List<Item> any) {

        public boolean accepts(ItemStack stack) {
            return !stack.isEmpty() && any.contains(stack.getItem());
        }

        /** 这格里的东西够不够 */
        public boolean satisfiedBy(ItemStack stack) {
            return accepts(stack) && stack.getCount() >= count;
        }
    }

    /** 推到第 {@link #level} 级这一步的全部价格（已经乘过配置里的倍率） */
    public record Step(int level, int mru, int espe, int ticks, int matrix, Need a, Need b) {

        /** 每刻最多要多少 MRU（向上取整） */
        public int mruPerTick() {
            return perTick(mru, ticks);
        }

        public int espePerTick() {
            return perTick(espe, ticks);
        }

        private static int perTick(int total, int ticks) {
            return ticks <= 0 ? total : (int) ((total + (long) ticks - 1L) / ticks);
        }
    }

    /** 不收这种附魔时的上限 */
    public static final int NOT_ACCEPTED = 0;

    private static final Map<String, Item> ITEM_CACHE = new ConcurrentHashMap<>();
    private static final String[] ELEMENTS = {"fire", "water", "earth", "air"};

    private EnchantUpgrades() {
    }

    // ------------------------------------------------------------------
    // 配置
    // ------------------------------------------------------------------

    /** 默认的「附魔=上限」：不收的写 0，机器上限之下再压低的写数字。冰霜行者按等级铺冰、迅捷潜行和激流按等级加速，到 50 级会动到整片世界；时运、抢夺到 50 级掉落会翻几十倍 */
    public static final List<String> DEFAULT_CAPS = List.of(
            "minecraft:frost_walker=0", "minecraft:swift_sneak=0", "minecraft:riptide=0",
            "minecraft:fortune=10", "minecraft:looting=10");

    /** 机器各自的配置（max_mru / max_espe / 三个倍率；升华台多三项矩阵门槛） */
    public static final class MachineConfig {
        public final ForgeConfigSpec.IntValue maxMRU;
        public final ForgeConfigSpec.DoubleValue maxESPE;
        public final ForgeConfigSpec.DoubleValue mruMultiplier;
        public final ForgeConfigSpec.DoubleValue espeMultiplier;
        public final ForgeConfigSpec.DoubleValue timeMultiplier;
        @Nullable
        public final ForgeConfigSpec.BooleanValue matrixGate;
        @Nullable
        public final ForgeConfigSpec.IntValue matrixBase;
        @Nullable
        public final ForgeConfigSpec.IntValue matrixPerLevel;
        @Nullable
        public final ForgeConfigSpec.IntValue requiredPentacleTier;

        MachineConfig(ForgeConfigSpec.Builder builder, Tier tier) {
            builder.push(tier.key);
            boolean late = tier == Tier.ASCENDER;
            maxMRU = builder.comment("MRU storage. It must hold at least one tick's worth of the costliest step, or that step is refused.",
                            "MRU 容量。至少要装得下最贵那一步每刻的用量，否则那一步不做")
                    .defineInRange("max_mru", late ? 200_000 : 50_000, 1, Integer.MAX_VALUE);
            maxESPE = builder.comment("ESPE storage (pulled from the eight crystal pillar spots around it).",
                            "ESPE 容量（从周围八个水晶柱位置抽）")
                    .defineInRange("max_espe", late ? 8_000D : 1_000D, 1D, 21_000_000D);
            mruMultiplier = builder.comment("Multiplier on the MRU price of every step.", "每一步 MRU 价格的倍率")
                    .defineInRange("mru_multiplier", 1D, 0D, 1000D);
            espeMultiplier = builder.comment("Multiplier on the ESPE price of every step.", "每一步 ESPE 价格的倍率")
                    .defineInRange("espe_multiplier", 1D, 0D, 1000D);
            timeMultiplier = builder.comment("Multiplier on the time of every step.", "每一步用时的倍率")
                    .defineInRange("time_multiplier", 1D, 0.01D, 100D);
            if (late) {
                matrixGate = builder.comment("Whether each step also costs the owner matrix damage (owner must be online and have room for it).",
                                "每一步是否还要机器主人承受矩阵损伤（主人要在线、损伤上限里还放得下）")
                        .define("matrix_gate", true);
                matrixBase = builder.comment("Matrix damage of the first step (level 26).", "第一步（26 级）的矩阵损伤")
                        .defineInRange("matrix_damage_base", 8000, 0, 100_000_000);
                matrixPerLevel = builder.comment("Extra matrix damage for every level above 26.", "每高一级多加的矩阵损伤")
                        .defineInRange("matrix_damage_per_level", 2000, 0, 100_000_000);
                requiredPentacleTier = builder.comment("The owner must have opened a Soul Pentacle of at least this tier (0-4).",
                                "主人至少打开过这一档的灵魂五芒星（0~4）")
                        .defineInRange("required_pentacle_tier", 2, 0, 4);
            } else {
                matrixGate = null;
                matrixBase = null;
                matrixPerLevel = null;
                requiredPentacleTier = null;
            }
            builder.pop();
        }
    }

    public static MachineConfig amplifier;
    public static MachineConfig ascender;
    public static ForgeConfigSpec.ConfigValue<List<? extends String>> enchantmentCaps;

    public static void define(ForgeConfigSpec.Builder builder) {
        builder.push("enchant_upgrade");
        enchantmentCaps = builder.comment("Per-enchantment ceilings for both machines, as \"id=level\"; 0 means the enchantment is refused.",
                        "两台机器对个别附魔的上限，写成「附魔id=最高等级」；0 表示不收这种附魔")
                .defineList("enchantment_caps", DEFAULT_CAPS, o -> o instanceof String s && s.contains("="));
        builder.pop();
        amplifier = new MachineConfig(builder, Tier.AMPLIFIER);
        ascender = new MachineConfig(builder, Tier.ASCENDER);
    }

    public static MachineConfig config(Tier tier) {
        return tier == Tier.AMPLIFIER ? amplifier : ascender;
    }

    /** 配置里给这种附魔压的上限；没写返回 Integer.MAX_VALUE */
    public static int configuredCap(Enchantment enchantment) {
        ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
        if (id == null || enchantmentCaps == null) {
            return Integer.MAX_VALUE;
        }
        int best = Integer.MAX_VALUE;
        for (String line : ECConfig.getValue(enchantmentCaps)) {
            int eq = line.indexOf('=');
            if (eq <= 0 || !id.toString().equals(line.substring(0, eq).trim())) {
                continue;
            }
            try {
                best = Math.min(best, Math.max(0, Integer.parseInt(line.substring(eq + 1).trim())));
            } catch (NumberFormatException ignored) {
                // 写坏的一行当没写
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // 书和附魔
    // ------------------------------------------------------------------

    /** 书上唯一的那条附魔；不是附魔书、没有附魔、有不止一条、附魔不认识，返回 null */
    @Nullable
    public static Entry read(ItemStack book) {
        if (!book.is(Items.ENCHANTED_BOOK)) {
            return null;
        }
        ListTag list = EnchantedBookItem.getEnchantments(book);
        if (list.size() != 1) {
            return null;
        }
        CompoundTag tag = list.getCompound(0);
        ResourceLocation id = EnchantmentHelper.getEnchantmentId(tag);
        Enchantment enchantment = id == null ? null : ForgeRegistries.ENCHANTMENTS.getValue(id);
        return enchantment == null ? null : new Entry(enchantment, EnchantmentHelper.getEnchantmentLevel(tag));
    }

    /** 这种附魔在这台机器上最高能升到几级：机器上限、配置里的压低取小；本身只有一级、是诅咒、配置不收的返回 {@link #NOT_ACCEPTED} */
    public static int cap(Enchantment enchantment, Tier tier) {
        if (enchantment.getMaxLevel() <= 1 || enchantment.isCurse()) {
            return NOT_ACCEPTED;
        }
        return Math.min(tier.cap, configuredCap(enchantment));
    }

    /** 书现在能不能在这台机器上再升一级（等级在 [minLevel, 上限) 之间） */
    public static boolean canUpgrade(Entry entry, Tier tier) {
        int cap = cap(entry.enchantment(), tier);
        return entry.level() >= tier.minLevel && entry.level() < cap;
    }

    /** 这本书对这台机器来说是不是「能放进来」：单一附魔、没被排除（等级够不够另说，界面状态会写原因） */
    public static boolean acceptsBook(ItemStack book, Tier tier) {
        Entry entry = read(book);
        return entry != null && cap(entry.enchantment(), tier) > 0;
    }

    /** 把书上那条附魔设成 level 级，返回新书（数量 1，别的 NBT 照旧） */
    public static ItemStack withLevel(ItemStack book, int level) {
        ItemStack out = book.copyWithCount(1);
        Entry entry = read(book);
        if (entry == null) {
            return out;
        }
        CompoundTag tag = out.getOrCreateTag();
        ListTag list = new ListTag();
        ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(entry.enchantment());
        if (id != null) {
            list.add(EnchantmentHelper.storeEnchantment(id, level));
        }
        tag.put(EnchantedBookItem.TAG_STORED_ENCHANTMENTS, list);
        return out;
    }

    // ------------------------------------------------------------------
    // 价格
    // ------------------------------------------------------------------

    /** 推到第 n 级这一步的价格；n 超出这台机器的范围时也照公式算（界面不会问，GameTest 会） */
    public static Step step(Tier tier, int n) {
        MachineConfig cfg = config(tier);
        double mruBase;
        double espeBase;
        double ticksBase;
        int matrix = 0;
        if (tier == Tier.AMPLIFIER) {
            mruBase = 2000D * n * n;
            espeBase = n > 8 ? 50D * (n - 8) : 0D;
            ticksBase = 300D + 60D * n;
        } else {
            mruBase = 4500D * n * n;
            espeBase = 300D * Math.max(0, n - 20);
            ticksBase = 900D + 180D * (n - 25);
            if (cfg.matrixGate != null && ECConfig.get(cfg.matrixGate)) {
                matrix = (int) Math.min(Integer.MAX_VALUE,
                        (long) ECConfig.get(cfg.matrixBase) + (long) ECConfig.get(cfg.matrixPerLevel) * Math.max(0, n - 26));
            }
        }
        int mru = clampInt(mruBase * ECConfig.get(cfg.mruMultiplier));
        int espe = clampInt(espeBase * ECConfig.get(cfg.espeMultiplier));
        int ticks = Math.max(1, clampInt(ticksBase * ECConfig.get(cfg.timeMultiplier)));
        return new Step(n, mru, espe, ticks, matrix, materialA(tier, n), materialB(tier, n));
    }

    private static int clampInt(double value) {
        return (int) Math.max(0D, Math.min(Integer.MAX_VALUE, Math.round(value)));
    }

    /**
     * 第一格材料：基材（青金石、钻石、米斯莱锭，升华台是恶魔核心、永恒物质、下界之星）。
     * <p>
     * 每格的数量不能超过那件物品一组的上限：材料格就是一格，永恒物质是 MRU 储存物品、一组只有 1 个，
     * 以前写的「永恒物质 ×2 / ×4」永远放不够，书卡在 30 级上不去。现在永恒物质每步 1 个，
     * 少掉的价钱补在同一步的另一格上（31~40 恶魔板 6 → 8，41~50 下界之星 2 → 4）。GameTest 逐级核对数量不超过一组。
     */
    public static Need materialA(Tier tier, int n) {
        if (tier == Tier.AMPLIFIER) {
            if (n <= 5) {
                return one(8, "minecraft", "lapis_lazuli");
            } else if (n <= 10) {
                return one(2, "minecraft", "lapis_block");
            } else if (n <= 15) {
                return one(2, "minecraft", "diamond");
            } else if (n <= 20) {
                return one(3, EssentialCraft5.MODID, "mithriline_ingot");
            }
            return one(5, EssentialCraft5.MODID, "mithriline_ingot");
        }
        if (n <= 30) {
            return one(2, EssentialCraft5.MODID, "demonic_core");
        } else if (n <= 40) {
            return one(1, EssentialCraft5.MODID, "matter_of_eternity");
        }
        return one(4, "minecraft", "nether_star");
    }

    /** 第二格材料：精华（四种元素任选一种，品质随等级升）；升华台是精华、恶魔护板、永恒物质 */
    public static Need materialB(Tier tier, int n) {
        if (tier == Tier.AMPLIFIER) {
            if (n <= 5) {
                return essence("common", 2);
            } else if (n <= 10) {
                return essence("uncommon", 3);
            } else if (n <= 15) {
                return essence("rare", 3);
            } else if (n <= 20) {
                return essence("exceptional", 3);
            }
            return essence("exceptional", 6);
        }
        if (n <= 30) {
            return essence("exceptional", 12);
        } else if (n <= 40) {
            return one(8, EssentialCraft5.MODID, "demonic_plate");
        }
        return one(1, EssentialCraft5.MODID, "matter_of_eternity");
    }

    private static Need one(int count, String namespace, String path) {
        return new Need(count, List.of(item(namespace, path)));
    }

    private static Need essence(String quality, int count) {
        List<Item> items = new ArrayList<>();
        for (String element : ELEMENTS) {
            items.add(item(EssentialCraft5.MODID, quality + "_" + element + "_essence"));
        }
        return new Need(count, List.copyOf(items));
    }

    private static Item item(String namespace, String path) {
        return ITEM_CACHE.computeIfAbsent(namespace + ":" + path, key -> {
            Item found = ForgeRegistries.ITEMS.getValue(new ResourceLocation(namespace, path));
            return found == null ? Items.AIR : found;
        });
    }

    /** 这台机器所有步骤里用得到的第一格 / 第二格材料（漏斗、管道只往对应格里送这些） */
    public static List<Item> allMaterialA(Tier tier) {
        return allMaterials(tier, true);
    }

    public static List<Item> allMaterialB(Tier tier) {
        return allMaterials(tier, false);
    }

    private static List<Item> allMaterials(Tier tier, boolean first) {
        Map<Item, Boolean> seen = new HashMap<>();
        for (int n = tier.minLevel + 1; n <= tier.cap; n++) {
            Need need = first ? materialA(tier, n) : materialB(tier, n);
            for (Item item : need.any()) {
                seen.put(item, Boolean.TRUE);
            }
        }
        return List.copyOf(seen.keySet());
    }
}
