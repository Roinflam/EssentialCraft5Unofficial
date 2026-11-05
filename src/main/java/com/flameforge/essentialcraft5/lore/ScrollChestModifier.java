package com.flameforge.essentialcraft5.lore;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.AncientScrollItem;
import com.flameforge.essentialcraft5.registry.ECScrolls;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 全局战利品修改器 {@code essentialcraft5:ancient_scroll_chests}：主世界、下界、末地的箱子里也能开出源质残卷（序和卷一～卷四）。
 * 上古遗迹一个世界里没几座，前五卷光靠它要找很久，到海纳尔都还没读完；这几卷讲的是前期的事，放进普通箱子里早点拿到。
 * <p>
 * 实例 JSON：{@code data/essentialcraft5/loot_modifiers/ancient_scroll_chests.json}（几率、卷号范围、维度都写在那里，数据包能改），
 * 登记在 {@code data/forge/loot_modifiers/global_loot_modifiers.json}。
 * <ul>
 *   <li>只管箱子表：路径以 {@code chests/} 开头的表，原版和别的模组的结构箱子都算；本模组自己的表（遗迹、海纳尔，表里另有残卷池）和出生奖励箱不算。
 *       只看最外层那张表，被别的表引用的不会重复加。</li>
 *   <li>要有开箱的玩家（{@code this_entity} 是服务端玩家，假玩家不算）：漏斗、矿车拉开的箱子不出。</li>
 *   <li>只出这名玩家<b>没有</b>的卷——没读过，也没从任何地方掉给过他（{@link ScrollVolumes#has}）；范围里都有了就什么都不加。
 *       出了就记成掉给过他，同一卷不会在箱子里出第二次。</li>
 *   <li>和 Lootr 这类「每人一份」的箱子模组兼容：它们给每名玩家各掷一次箱子表，参数里带着开箱的人，这里照常按人判；
 *       没装时箱子第一次被玩家打开时掷一次。</li>
 *   <li>{@code general.ancient_scrolls.enabled} 关掉时不加。</li>
 * </ul>
 */
public final class ScrollChestModifier extends LootModifier {

    public static final ResourceLocation ID = EssentialCraft5.id("ancient_scroll_chests");

    /**
     * 校验要做在 MapCodec 上再 {@code .codec()}：Forge 按 type 分发时只把 MapCodec 摊平在同一层，
     * 直接对 Codec 做 flatXmap 会变成去读 {@code value} 字段（加载时报「Not a JSON object: null」，修改器整个不生效）。
     */
    public static final Codec<ScrollChestModifier> CODEC = RecordCodecBuilder.<ScrollChestModifier>mapCodec(inst -> inst.group(
                    LOOT_CONDITIONS_CODEC.fieldOf("conditions").forGetter(m -> m.conditions),
                    Codec.floatRange(0F, 1F).fieldOf("chance").forGetter(m -> m.chance),
                    Codec.intRange(0, ScrollVolumes.COUNT - 1).fieldOf("min").forGetter(m -> m.min),
                    Codec.intRange(0, ScrollVolumes.COUNT - 1).fieldOf("max").forGetter(m -> m.max),
                    Level.RESOURCE_KEY_CODEC.listOf().fieldOf("dimensions").forGetter(m -> m.dimensions))
            .apply(inst, ScrollChestModifier::new))
            .flatXmap(ScrollChestModifier::checked, ScrollChestModifier::checked)
            .codec();

    private final float chance;
    private final int min;
    private final int max;
    private final List<ResourceKey<Level>> dimensions;

    public ScrollChestModifier(LootItemCondition[] conditions, float chance, int min, int max, List<ResourceKey<Level>> dimensions) {
        super(conditions);
        this.chance = chance;
        this.min = min;
        this.max = max;
        this.dimensions = List.copyOf(dimensions);
    }

    private static DataResult<ScrollChestModifier> checked(ScrollChestModifier modifier) {
        return modifier.min <= modifier.max ? DataResult.success(modifier)
                : DataResult.error(() -> "ancient_scroll_chests 的 min 不能大于 max：" + modifier.min + " / " + modifier.max);
    }

    /** 是不是要管的箱子表：路径 chests/ 开头，不是本模组自己的表，不是出生奖励箱 */
    public static boolean isChestTable(@Nullable ResourceLocation table) {
        return table != null && table.getPath().startsWith("chests/")
                && !table.getNamespace().equals(EssentialCraft5.MODID)
                && !table.equals(BuiltInLootTables.SPAWN_BONUS_CHEST);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!isChestTable(context.getQueriedLootTableId()) || !dimensions.contains(context.getLevel().dimension())
                || !ECScrolls.Config.values().enabled()) {
            return generatedLoot;
        }
        Entity opener = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (!(opener instanceof ServerPlayer player) || player instanceof FakePlayer) {
            return generatedLoot;
        }
        List<Integer> missing = new ArrayList<>();
        for (int v = min; v <= max; v++) {
            if (!ScrollVolumes.has(player, v)) {
                missing.add(v);
            }
        }
        if (missing.isEmpty() || context.getRandom().nextFloat() >= chance) {
            return generatedLoot;
        }
        int volume = missing.get(context.getRandom().nextInt(missing.size()));
        generatedLoot.add(AncientScrollItem.of(volume, 1));
        ScrollVolumes.markGiven(player, volume);
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    /** 把编解码器登记进 Forge 的全局战利品修改器注册表（Forge 自动发现，不用接线；类名、方法名带模块前缀，同包的事件处理器不能撞名） */
    @Mod.EventBusSubscriber(modid = EssentialCraft5.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ScrollChestRegistration {

        private ScrollChestRegistration() {
        }

        @SubscribeEvent
        public static void registerScrollChestModifier(RegisterEvent event) {
            event.register(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, helper -> helper.register(ID, CODEC));
        }
    }
}
