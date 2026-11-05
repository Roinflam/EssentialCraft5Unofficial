package com.flameforge.essentialcraft5.demon;

import com.flameforge.essentialcraft5.item.CapturedSoulItem;
import com.flameforge.essentialcraft5.registry.ECDemonic;
import com.flameforge.essentialcraft5.registry.ECTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * 一只恶魔想要的东西（原作 EntityDemon.desiredItem）：一种物品 × 数量，或者某种实体的被俘灵魂 × 数量。召唤时由交易池掷出来（{@link DemonTrades#roll}），
 * 存进恶魔的 NBT，交易界面按它收东西。
 *
 * <h2>怎么算「给对了」（{@link #matches}）</h2>
 * <ul>
 *   <li>物品：同一种物品；<b>会损坏的物品要满耐久</b>（原作 {@code isItemEqual} 比 meta，1.12 的 meta 就是耐久），条目写了 {@code any_damage}
 *       时不看耐久（原作的通配条目：四种枪）。不比 NBT：改过名、带附魔照收，玩家头颅带不带 {@code SkullOwner} 都收，任意已填充地图都收
 *       （原作要求 NBT 完全相同）。</li>
 *   <li>灵魂：只比灵魂里记的实体（{@link CapturedSoulItem#entityId}），改过名的照收。</li>
 *   <li>任意灵魂（第十三批 13a，「熟客」的第二样在整池都乘不下时改要的那种，{@link #anySoul}）：物品是被俘灵魂、不指定实体，
 *       哪种实体的灵魂都收，但要真记着一种实体（没有 {@code entity} 的空灵魂不收）。交易界面只有一个投放格，不同实体的灵魂叠不到一起，
 *       所以实际要交的是同一种实体的灵魂（界面和手册都这样写）。存档照物品需求写 {@code item: essentialcraft5:captured_soul}，
 *       读回来还是任意灵魂。交易池文件里不能这样写（{@link DemonTrades} 跳过并警告），只由代码生成。</li>
 * </ul>
 *
 * @param item      想要的物品；灵魂需求为 null
 * @param soul      想要哪种实体的灵魂；物品需求为 null
 * @param count     数量（交易池加载时已夹到这种物品一组的上限以内，掷出时再夹到 {@link #deliverLimit}）
 * @param anyDamage 物品需求不看耐久
 */
public record DemonWant(@Nullable Item item, @Nullable ResourceLocation soul, int count, boolean anyDamage) {

    private static final String TAG_ITEM = "item";
    private static final String TAG_SOUL = "soul";
    private static final String TAG_COUNT = "count";
    private static final String TAG_ANY_DAMAGE = "any_damage";

    public static DemonWant item(Item item, int count, boolean anyDamage) {
        return new DemonWant(item, null, Math.max(1, count), anyDamage);
    }

    /**
     * 这种物品一次最多交得上几件：一组的上限；{@link ECTags#DEMON_UNSTACKABLE} 里的（已填充地图、玩家头颅这类每件 NBT 都不一样的）只算 1 件，
     * 因为投放格只有一格，两件不同的叠不进去。要等标签绑定之后才准（召唤、读存档、交易时都已绑定；交易池文件读入时还没有，那里只按一组夹）。
     */
    public static int deliverLimit(Item item) {
        ItemStack stack = new ItemStack(item);
        return stack.is(ECTags.DEMON_UNSTACKABLE) ? 1 : stack.getMaxStackSize();
    }

    public static DemonWant soul(ResourceLocation entity, int count) {
        return new DemonWant(null, entity, Math.max(1, count), false);
    }

    /** 任意一种被俘灵魂 × count（见类注释） */
    public static DemonWant anySoul(int count) {
        return new DemonWant(ECDemonic.CAPTURED_SOUL.get(), null, Math.max(1, Math.min(count, DemonTrades.MAX_SOUL_COUNT)), false);
    }

    public boolean isSoul() {
        return soul != null;
    }

    /** 要的是任意一种被俘灵魂（不指定实体） */
    public boolean isAnySoul() {
        return soul == null && item != null && item == ECDemonic.CAPTURED_SOUL.get();
    }

    /** 这一堆东西是不是它要的（不看数量） */
    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (soul != null) {
            return soul.equals(CapturedSoulItem.entityId(stack));
        }
        if (isAnySoul()) {
            return stack.is(item) && CapturedSoulItem.entityId(stack) != null;
        }
        return item != null && stack.is(item) && (anyDamage || !stack.isDamageableItem() || stack.getDamageValue() == 0);
    }

    /** 要的是会损坏、又没写 any_damage 的物品：界面上注明「要满耐久」 */
    public boolean needsFullDurability() {
        return soul == null && !anyDamage && item != null && new ItemStack(item).isDamageableItem();
    }

    /** 界面上画的那一堆（数量 = 要的数量）；灵魂是带实体的被俘灵魂 */
    public ItemStack displayStack() {
        if (soul != null) {
            return CapturedSoulItem.of(soul, count);
        }
        return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (soul != null) {
            tag.putString(TAG_SOUL, soul.toString());
        } else if (item != null) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id != null) {
                tag.putString(TAG_ITEM, id.toString());
            }
            if (anyDamage) {
                tag.putBoolean(TAG_ANY_DAMAGE, true);
            }
        }
        tag.putInt(TAG_COUNT, count);
        return tag;
    }

    /**
     * 读回存档里的需求；物品或实体已经不存在（卸了模组）、写坏了时返回 null（恶魔下一刻重掷一次）。
     * 数量照交易池的规则夹：物品不超过 {@link #deliverLimit}（投放格放不下就永远交不成，超过 127 时同步数据里的数量也会写错），灵魂不超过
     * {@link DemonTrades#MAX_SOUL_COUNT}。改 NBT、命令召出来的，或者召唤之后别的模组调小了堆叠上限的，都按这条夹回来；
     * 早先的版本给已填充地图开过「× 2」的贵价，存档里留着的也在这里夹回 1 件。
     */
    @Nullable
    public static DemonWant load(CompoundTag tag) {
        int count = Math.max(1, tag.getInt(TAG_COUNT));
        if (tag.contains(TAG_SOUL, Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(TAG_SOUL));
            if (id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
                // 灵魂刻刀再也收不到这种灵魂，留着只能打发走
                return null;
            }
            return soul(id, Math.min(count, DemonTrades.MAX_SOUL_COUNT));
        }
        if (tag.contains(TAG_ITEM, Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(TAG_ITEM));
            if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) {
                return null;
            }
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == Items.AIR) {
                return null;
            }
            return item(item, Math.min(count, deliverLimit(item)), tag.getBoolean(TAG_ANY_DAMAGE));
        }
        return null;
    }

    /** 客户端从同步来的那一堆反推需求（界面用）；空堆返回 null */
    @Nullable
    public static DemonWant fromDisplay(ItemStack stack, boolean anyDamage) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.is(ECDemonic.CAPTURED_SOUL.get())) {
            // 同步来的灵魂没写实体 = 任意灵魂（anySoul 的 displayStack 就是一堆空的被俘灵魂）
            ResourceLocation id = CapturedSoulItem.entityId(stack);
            return id == null ? anySoul(stack.getCount()) : soul(id, stack.getCount());
        }
        return item(stack.getItem(), stack.getCount(), anyDamage);
    }
}
