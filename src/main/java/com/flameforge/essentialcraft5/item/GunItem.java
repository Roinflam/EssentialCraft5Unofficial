package com.flameforge.essentialcraft5.item;

import com.flameforge.essentialcraft5.api.ECCapabilities;
import com.flameforge.essentialcraft5.client.GunsClient;
import com.flameforge.essentialcraft5.gun.GunMaterials;
import com.flameforge.essentialcraft5.gun.GunParts;
import com.flameforge.essentialcraft5.gun.GunStats;
import com.flameforge.essentialcraft5.gun.GunType;
import com.flameforge.essentialcraft5.gun.RayShot;
import com.flameforge.essentialcraft5.mru.MRUDrain;
import com.flameforge.essentialcraft5.util.ECProtection;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * MRU 射线枪（原作 {@code ItemGun}，ECGuns，batch5.md §3.1）：手枪 {@code pistol}、突击步枪 {@code rifle}、狙击步枪 {@code sniper}、加特林机枪 {@code gatling}。
 * 继承 {@link MRUStaffItem}：自带 5 000 MRU、MRU 提示行、创造栏空 / 满两份（都是没组装的）、改 NBT 不重举。
 * 部件在 NBT 子标签 {@code gun}（{@link GunParts}），属性每次现算（{@link GunStats}），射线在 {@link RayShot}。
 *
 * <h2>开火</h2>
 * <ul>
 *   <li>没组装的枪（只可能来自创造栏或指令）：右键时服务端从<b>已开放</b>的材料里随机组装（原作 {@code createRandomGun}）。</li>
 *   <li>一匣打完（{@code fired ≥ 弹量}）：右键开始装填，按住 {@code floor(reload × 20)} 刻，装完 {@code fired = 0}；手枪、狙击另加 3 秒冷却。
 *       统一按「{@code fired < 弹量} 才能打」判断（原作右键、按住、动画三处的条件不一致）。</li>
 *   <li>手枪、狙击：右键一发，冷却 {@code ceil(speed × 2)} 刻（原版 {@code ItemCooldowns}，同种枪共用）；客户端预判能打时枪口上抬（后坐）。</li>
 *   <li>步枪：按住每 3 刻一发；加特林：按住先空转 3 秒，之后每刻一发（开火声每 4 发一次，只在真开火时）。</li>
 *   <li>每发 MRU：伤害 × 10（手枪、狙击）/ × 3（步枪）/ × 2（加特林），先扣身上的储存物品，再扣枪自己的；创造模式不扣。
 *       <b>MRU 不够就不打、不计数</b>（原作照样把子弹「打掉」、逼你装填）。</li>
 *   <li>磨损：磨损没满时每发 +1；满了以后每发 1/4 概率卡壳（这一匣直接打完，要装填）。铁砧用枪托那种材料修（{@code gun/GunAnvilEvents}）。</li>
 *   <li>假玩家（机械手之类）拿着不开火（伤害记在假玩家头上，领地插件认不出是谁，policy「假玩家不给权限」）。</li>
 *   <li>按住期间手上换成另一把：只有部件和装填状态都一样才接着算（{@link #canContinueUsing}）；客户端打开任何界面时替玩家松开（{@code client/GunsClientEvents}）。</li>
 * </ul>
 * 加特林拿在主手或副手时移动速度 −60%（属性修饰符，等于缓慢 IV；原作主手每刻上一次缓慢药水，副手不减是审查 GUNS-5 补上的）。
 */
public class GunItem extends MRUStaffItem {

    /** 连发 / 装填以外「按住」的时长上限（1 小时） */
    public static final int USE_DURATION = 72000;
    /** 手枪、狙击装填完以后的冷却（刻，原作 cool = 60；武器工作台的预览也用，定义在 {@link GunStats#RELOAD_COOLDOWN_TICKS}） */
    public static final int RELOAD_COOLDOWN = GunStats.RELOAD_COOLDOWN_TICKS;
    /** 磨损满了以后每发卡壳的概率（原作 1/4） */
    public static final float JAM_CHANCE = 0.25F;
    /** 加特林拿在手上的移动速度修饰（MULTIPLY_TOTAL，等于缓慢 IV） */
    public static final double GATLING_SLOW = -0.6D;
    /** 主手、副手各用一个 UUID：共用一个的话，两只手都拿着时放下一把会把另一把的减速一起摘掉 */
    public static final UUID GATLING_SLOW_ID = UUID.fromString("7c5b3c1e-9a2d-4f61-8e0b-5d6a2f9c3e41");
    public static final UUID GATLING_SLOW_OFFHAND_ID = UUID.fromString("2e8f6d4a-51c7-4b93-a0d2-8c1b7e5f9a63");
    /** 加特林空转时每几刻响一次（原作 count % 5） */
    private static final int SPIN_SOUND_INTERVAL = 5;
    /** 加特林每几发播一次开火声 */
    private static final int GATLING_SOUND_INTERVAL = 4;
    /** 装填时每几刻响一次（原作 count % 20） */
    private static final int RELOAD_SOUND_INTERVAL = 20;

    private final GunType type;
    @Nullable
    private Multimap<Attribute, AttributeModifier> gatlingMainModifiers;
    @Nullable
    private Multimap<Attribute, AttributeModifier> gatlingOffModifiers;

    public GunItem(GunType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public GunType type() {
        return type;
    }

    // ------------------------------------------------------------------
    // 状态
    // ------------------------------------------------------------------

    /** 这把枪的属性（没组装为空） */
    public Optional<GunStats> stats(ItemStack stack) {
        return GunParts.read(stack).map(parts -> GunStats.of(type, parts));
    }

    /** 一匣打完、要装填了 */
    public static boolean needsReload(ItemStack stack, GunStats stats) {
        return GunParts.fired(stack) >= stats.magazine();
    }

    private boolean needsReload(ItemStack stack) {
        return stats(stack).map(stats -> needsReload(stack, stats)).orElse(false);
    }

    // ------------------------------------------------------------------
    // 右键 / 按住
    // ------------------------------------------------------------------

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Optional<GunParts> parts = GunParts.read(stack);
        if (parts.isEmpty()) {
            if (!level.isClientSide && !assembleRandomly(stack, type, player.getRandom())) {
                return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.consume(stack);
        }
        if (!level.isClientSide && !ECProtection.isRealPlayer(player)) {
            return InteractionResultHolder.fail(stack);
        }
        GunStats stats = GunStats.of(type, parts.get());
        if (needsReload(stack, stats) || type.automatic()) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        int cost = type.mruPerShot(stats);
        if (level.isClientSide) {
            // 客户端只预判：够 MRU 就把枪口抬起来（后坐只在客户端生效，服务端的朝向下一个移动包就被客户端覆盖）
            if (!MRUDrain.canAfford(player, stack, cost)) {
                return InteractionResultHolder.fail(stack);
            }
            if (type.hasRecoil() && stats.recoil() > 0F) {
                player.setXRot(Mth.clamp(player.getXRot() - stats.recoil(), -90F, 90F));
            }
            return InteractionResultHolder.consume(stack);
        }
        if (!shoot(player, stack, type, stats, player.getRandom(), true, RayShot.PACKET)) {
            tellNoMRU(player, cost);
            return InteractionResultHolder.fail(stack);
        }
        int cooldown = stats.cooldownTicks();
        if (cooldown > 0) {
            player.getCooldowns().addCooldown(this, cooldown);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        Optional<GunParts> parts = GunParts.read(stack);
        if (parts.isEmpty() || !ECProtection.isRealPlayer(player)) {
            player.releaseUsingItem();
            return;
        }
        GunStats stats = GunStats.of(type, parts.get());
        if (needsReload(stack, stats)) {                         // 装填中
            if (remaining % RELOAD_SOUND_INTERVAL == 0) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS,
                        0.4F, 0.5F + player.getRandom().nextFloat());
            }
            return;
        }
        if (!type.automatic()) {                                  // 手枪、狙击只在装填时「按住」
            player.releaseUsingItem();
            return;
        }
        int elapsed = USE_DURATION - remaining;
        if (elapsed < type.spinUpTicks()) {                       // 加特林空转
            if (elapsed % SPIN_SOUND_INTERVAL == 0) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MINECART_INSIDE, SoundSource.PLAYERS,
                        0.1F, Math.max(0.5F, elapsed / 30F));
            }
            return;
        }
        int since = elapsed - type.spinUpTicks();
        if (since % type.autoInterval() != 0) {
            return;
        }
        boolean sound = type != GunType.GATLING || since % GATLING_SOUND_INTERVAL == 0;
        if (!shoot(player, stack, type, stats, player.getRandom(), sound, RayShot.PACKET)) {
            tellNoMRU(player, type.mruPerShot(stats));
            player.releaseUsingItem();
            return;
        }
        if (needsReload(stack, stats)) {
            player.releaseUsingItem();
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            Optional<GunStats> stats = stats(stack);
            if (stats.isPresent() && needsReload(stack, stats.get())) {
                GunParts.setFired(stack, 0);
                if (!type.automatic() && entity instanceof Player player) {
                    player.getCooldowns().addCooldown(this, RELOAD_COOLDOWN);
                }
            }
        }
        return stack;
    }

    /**
     * 按住期间手上那格换成了另一个对象时，还能不能接着用（Forge 默认只比物品，{@code IForgeItem} L634；{@code LivingEntity.updatingUsingItem} 每刻问一次）。
     * 同一个对象，或<b>同一种枪、部件一样、装填状态一样</b>才接着用：
     * <ul>
     *   <li>服务端开火是原地改 NBT，对象不变；客户端每发收到同步、换成新对象，但部件和装填状态不变，所以不打断连发和预转。</li>
     *   <li>部件不同（装填时长按开始按住那把定）、装填状态不同（空枪装填中换成有弹的）就中止——不然在坐骑物品栏这类服务端开的界面里换枪，
     *       能借别的枪的装填时间，加特林能跳过 3 秒预转直接开火。</li>
     *   <li>不比 fired、MRU、磨损：每发都在变，比了客户端每发都要停一次、重新按。</li>
     * </ul>
     */
    @Override
    public boolean canContinueUsing(ItemStack oldStack, ItemStack newStack) {
        if (oldStack == newStack) {
            return true;
        }
        if (!oldStack.is(this) || !newStack.is(this)) {
            return false;
        }
        Optional<GunParts> parts = GunParts.read(oldStack);
        if (parts.isEmpty() || !parts.equals(GunParts.read(newStack))) {
            return false;
        }
        GunStats stats = GunStats.of(type, parts.get());
        return needsReload(oldStack, stats) == needsReload(newStack, stats);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return stats(stack).filter(stats -> needsReload(stack, stats)).map(GunStats::reloadTicks).orElse(USE_DURATION);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return needsReload(stack) ? UseAnim.BLOCK : UseAnim.BOW;
    }

    /**
     * 开一枪（服务端）：扣 MRU → 这一匣 +1 → 磨损 / 卡壳 → 射线（{@link RayShot#fire}）。不管冷却（冷却由调用方加）。
     * GameTest 用固定种子的随机源和自己的光束出口直接调。
     *
     * @return MRU 不够时 false（什么都没动）
     */
    public static boolean shoot(Player player, ItemStack stack, GunType type, GunStats stats, RandomSource random, boolean sound,
                                RayShot.BeamSink sink) {
        if (!MRUDrain.playerUseMRU(player, stack, type.mruPerShot(stats))) {
            return false;
        }
        int fired = GunParts.fired(stack) + 1;
        int wear = GunParts.wear(stack);
        if (wear < stats.durabilityInt()) {
            GunParts.setWear(stack, wear + 1);
        } else if (random.nextFloat() < JAM_CHANCE) {             // 卡壳：这一匣直接打完
            fired = stats.magazine();
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1F, 1F);
        }
        GunParts.setFired(stack, fired);
        RayShot.fire(player, stats, random, sound, sink);
        return true;
    }

    // ------------------------------------------------------------------
    // 组装（没组装的枪、展示厅、测试）
    // ------------------------------------------------------------------

    /**
     * 从<b>已开放</b>的材料里随机组装（原作 createRandomGun：三个枪身部件、透镜各随机一种；狙击随机一种狙击镜，手枪 / 步枪装普通瞄具，加特林没有瞄具）。
     *
     * @return 一种已开放的枪身材料都没有时 false
     */
    public static boolean assembleRandomly(ItemStack stack, GunType type, RandomSource random) {
        List<GunMaterials.Body> bodies = GunMaterials.availableBodies();
        if (bodies.isEmpty()) {
            return false;
        }
        List<GunMaterials.Lens> lenses = GunMaterials.availableLenses();
        List<GunMaterials.Scope> scopes = GunMaterials.availableScopes(type);
        String lens = lenses.isEmpty() ? null : lenses.get(random.nextInt(lenses.size())).id();
        String scope = scopes.isEmpty() ? null : scopes.get(random.nextInt(scopes.size())).id();
        new GunParts(bodies.get(random.nextInt(bodies.size())).id(), bodies.get(random.nextInt(bodies.size())).id(),
                bodies.get(random.nextInt(bodies.size())).id(), lens, scope).writeTo(stack);
        return true;
    }

    /**
     * 造一把指定部件的枪（展示厅、测试用；枪物品按注册名取，没注册返回空）。
     *
     * @param mru 装多少 MRU（夹到 0 ~ 5 000）
     */
    public static ItemStack create(GunType type, GunParts parts, int mru) {
        Item item = ForgeRegistries.ITEMS.getValue(type.itemId());
        if (item == null || !ForgeRegistries.ITEMS.containsKey(type.itemId())) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        parts.writeTo(stack);
        stack.getCapability(ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        return stack;
    }

    // ------------------------------------------------------------------
    // 其它
    // ------------------------------------------------------------------

    /** 快要打完一匣时，拿在手上的枪口冒烟（只在客户端，不写 NBT；原作 onUpdate） */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide || !selected || !(entity instanceof LivingEntity living) || level.random.nextInt(2) != 0) {
            return;
        }
        Optional<GunStats> stats = stats(stack);
        if (stats.isEmpty() || GunParts.fired(stack) + 1 < stats.get().magazine()) {
            return;
        }
        Vec3 look = living.getLookAngle();
        Vec3 at = living.getEyePosition().add(look.scale(0.8D)).add(0D, -0.35D, 0D);
        level.addParticle(ParticleTypes.SMOKE, at.x, at.y, at.z, 0D, 0.01D, 0D);
    }

    /**
     * 加特林拿在手上：移动速度 −60%（原作主手每刻上一次缓慢 IV）。副手也减（审查 GUNS-5 取 A：不然换到副手就能扛着跑），
     * 主手、副手各一个 UUID；两只手都拿加特林时两个修饰都生效（×0.4 × 0.4）。
     */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (type != GunType.GATLING) {
            return base;
        }
        if (slot == EquipmentSlot.MAINHAND) {
            if (gatlingMainModifiers == null) {
                gatlingMainModifiers = withGatlingSlow(base, GATLING_SLOW_ID);
            }
            return gatlingMainModifiers;
        }
        if (slot == EquipmentSlot.OFFHAND) {
            if (gatlingOffModifiers == null) {
                gatlingOffModifiers = withGatlingSlow(base, GATLING_SLOW_OFFHAND_ID);
            }
            return gatlingOffModifiers;
        }
        return base;
    }

    private static Multimap<Attribute, AttributeModifier> withGatlingSlow(Multimap<Attribute, AttributeModifier> base, UUID id) {
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.putAll(base);
        builder.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(id, "Gatling weight", GATLING_SLOW, AttributeModifier.Operation.MULTIPLY_TOTAL));
        return builder.build();
    }

    /** 耐久条 = 磨损（磨过才显示） */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return GunParts.wear(stack) > 0 && stats(stack).isPresent();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13F - 13F * wornFraction(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(Math.max(0F, 1F - wornFraction(stack)) / 3F, 1F, 1F);
    }

    private float wornFraction(ItemStack stack) {
        int durability = stats(stack).map(GunStats::durabilityInt).orElse(0);
        return durability <= 0 ? 1F : Mth.clamp(GunParts.wear(stack) / (float) durability, 0F, 1F);
    }

    // ------------------------------------------------------------------
    // 提示
    // ------------------------------------------------------------------

    @Override
    protected void addUsage(ItemStack stack, List<Component> tooltip) {
        Optional<GunParts> read = GunParts.read(stack);
        if (read.isEmpty()) {
            tooltip.add(usage("tooltip.essentialcraft5.gun.unassembled"));
            return;
        }
        GunParts parts = read.get();
        GunStats stats = GunStats.of(type, parts);
        tooltip.add(line("tooltip.essentialcraft5.gun.base", materialName(parts.base())));
        tooltip.add(line("tooltip.essentialcraft5.gun.handle", materialName(parts.handle())));
        tooltip.add(line("tooltip.essentialcraft5.gun.device", materialName(parts.device())));
        if (parts.scope() != null) {
            GunMaterials.scope(type, parts.scope()).ifPresent(scope -> tooltip.add(line("tooltip.essentialcraft5.gun.scope",
                    itemName(scope.item(), scope.id()), number(scope.zoom()))));
        }
        tooltip.add(line("tooltip.essentialcraft5.gun.balance", balanceName(stats.balance())));
        int fired = Math.min(GunParts.fired(stack), stats.magazine());
        tooltip.add(line("tooltip.essentialcraft5.gun.fired", fired, stats.magazine()));
        if (GunParts.wear(stack) > 0) {
            tooltip.add(line("tooltip.essentialcraft5.gun.wear", Math.min(GunParts.wear(stack), stats.durabilityInt()), stats.durabilityInt()));
        }
        if (needsReload(stack, stats)) {
            tooltip.add(Component.translatable("tooltip.essentialcraft5.gun.overheated").withStyle(ChatFormatting.RED));
        }
        Boolean shift = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> GunsClient::hasShiftDown);
        if (Boolean.TRUE.equals(shift)) {
            statLines(stats, tooltip);
        } else {
            tooltip.add(usage("tooltip.essentialcraft5.gun.shift"));
        }
        tooltip.add(usage("tooltip.essentialcraft5.gun.use." + type.id()));
    }

    /** 按住 Shift 看的属性（原作 addInformation 的 9 行，改成如实写：射击间隔是实际的刻数、「击退」其实是后坐、装填后的 3 秒冷却写出来） */
    private void statLines(GunStats stats, List<Component> tooltip) {
        tooltip.add(stat("tooltip.essentialcraft5.gun.damage", number(stats.damage())));
        tooltip.add(stat("tooltip.essentialcraft5.gun.mru_per_shot", type.mruPerShot(stats)));
        tooltip.add(stat("tooltip.essentialcraft5.gun.durability", stats.durabilityInt()));
        tooltip.add(stat("tooltip.essentialcraft5.gun.magazine", stats.magazine()));
        if (type.automatic()) {
            tooltip.add(stat("tooltip.essentialcraft5.gun.reload", number(stats.reloadTicks() / 20F)));
            tooltip.add(stat("tooltip.essentialcraft5.gun.interval", type.autoInterval()));
            if (type.spinUpTicks() > 0) {
                tooltip.add(stat("tooltip.essentialcraft5.gun.spin_up", number(type.spinUpTicks() / 20F)));
            }
        } else {
            tooltip.add(stat("tooltip.essentialcraft5.gun.reload_cooldown", number(stats.reloadTicks() / 20F), number(RELOAD_COOLDOWN / 20F)));
            tooltip.add(stat("tooltip.essentialcraft5.gun.interval", stats.cooldownTicks()));
        }
        if (type.hasRecoil()) {
            tooltip.add(stat("tooltip.essentialcraft5.gun.recoil", number(stats.recoil())));
        }
        tooltip.add(stat("tooltip.essentialcraft5.gun.spread", number(stats.spread())));
        if (stats.zoom() > 0F) {
            tooltip.add(stat("tooltip.essentialcraft5.gun.zoom", number(stats.zoom())));
        }
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.GRAY);
    }

    private static Component stat(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.DARK_AQUA);
    }

    /** 枪身材料的名字 = 材料物品的名字（物品没注册时写 id） */
    private static Component materialName(String id) {
        return GunMaterials.body(id).map(body -> itemName(body.item(), id)).orElse(Component.literal(id));
    }

    private static Component itemName(ResourceLocation item, String fallback) {
        return ForgeRegistries.ITEMS.containsKey(item) ? ForgeRegistries.ITEMS.getValue(item).getDescription() : Component.literal(fallback);
    }

    /** 透镜 balance 的名字（复用矩阵属性的五个词） */
    public static Component balanceName(int balance) {
        String key = switch (balance) {
            case 1 -> "chaos";
            case 2 -> "frozen";
            case 3 -> "magic";
            case 4 -> "shade";
            default -> "neutral";
        };
        return Component.translatable("gui.essentialcraft5.matrix_type." + key);
    }

    /** 一位小数，整数不带「.0」 */
    public static String number(float value) {
        String text = String.format(Locale.ROOT, "%.1f", value);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }
}
