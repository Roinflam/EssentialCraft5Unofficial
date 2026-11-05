package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.block.DyeableBlock;
import com.flameforge.essentialcraft5.blockentity.EmberForgeBlockEntity;
import com.flameforge.essentialcraft5.blockentity.EmberForgeBlockEntity.Status;
import com.flameforge.essentialcraft5.item.ForgedElementalSwordItem;
import com.flameforge.essentialcraft5.item.MRUStaffItem;
import com.flameforge.essentialcraft5.item.ember.EmberEffect;
import com.flameforge.essentialcraft5.item.ember.EmberKind;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData;
import com.flameforge.essentialcraft5.item.ember.ForgedSwordData.Element;
import com.flameforge.essentialcraft5.registry.ECDamageTypes;
import com.flameforge.essentialcraft5.registry.ECEmberForge;
import com.flameforge.essentialcraft5.registry.ECEssence;
import com.flameforge.essentialcraft5.registry.ECVoid;
import com.flameforge.essentialcraft5.structure.StructurePattern;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.flameforge.essentialcraft5.item.ember.EmberEffect.CORROSION;
import static com.flameforge.essentialcraft5.item.ember.EmberEffect.DAMAGE_SELF;
import static com.flameforge.essentialcraft5.item.ember.EmberEffect.DISPEL;
import static com.flameforge.essentialcraft5.item.ember.EmberEffect.GREATER_HUNGER;
import static com.flameforge.essentialcraft5.item.ember.EmberEffect.GREATER_LIFESTEAL;
import static com.flameforge.essentialcraft5.item.ember.EmberEffect.GREATER_POISON;
import static com.flameforge.essentialcraft5.item.ember.EmberEffect.GREATER_SLOWNESS;
import static com.flameforge.essentialcraft5.item.ember.EmberEffect.GREATER_SPEED;
import static com.flameforge.essentialcraft5.item.ember.EmberEffect.LIFESTEAL;
import static com.flameforge.essentialcraft5.item.ember.EmberEffect.LIGHTNING;
import static com.flameforge.essentialcraft5.item.ember.EmberEffect.POISON;
import static com.flameforge.essentialcraft5.item.ember.EmberEffect.SLOWNESS;
import static com.flameforge.essentialcraft5.item.ember.EmberEffect.SPEED;
import static com.flameforge.essentialcraft5.item.ember.EmberEffect.STRENGTH;
import static com.flameforge.essentialcraft5.item.ember.EmberEffect.WEAKNESS;
import static com.flameforge.essentialcraft5.item.ember.EmberKind.ACIDIC;
import static com.flameforge.essentialcraft5.item.ember.EmberKind.CHAOS;
import static com.flameforge.essentialcraft5.item.ember.EmberKind.COMMON;
import static com.flameforge.essentialcraft5.item.ember.EmberKind.CORRUPTED;
import static com.flameforge.essentialcraft5.item.ember.EmberKind.CRYSTAL;
import static com.flameforge.essentialcraft5.item.ember.EmberKind.DIVINE;
import static com.flameforge.essentialcraft5.item.ember.EmberKind.FLAME;
import static com.flameforge.essentialcraft5.item.ember.EmberKind.MAGICAL;

/**
 * 余烬锻炉与余烬元素剑（第七批 M2，batch7.md §4.2）。
 * <ul>
 *   <li>锻炉在模板 empty_large（11×8×11）的 (5, 2, 5)：下一层虚空石在 y = 1，余烬格在 y = 3（淡青护板上方），焦点格在 y = 4（魔力护板上方）。
 *       夜里、锻一把要几刻用方块实体自己的测试覆盖值（{@code setTestOverrides}），不改世界时间（各测试共用一个世界）。</li>
 *   <li>剑的命中用真玩家（{@code ProtectionFrameworkTests.realPlayer}）真 {@code attack}：先空跑 61 次 tick 去掉出生无敌，
 *       再清掉攻击冷却（蓄力不满：不横扫、不暴击）、把剑放进主手（不再 tick，属性不刷新，近战只打 0.2 点，目标不会被一下打死）。目标用尸壳：白天不自燃，测「着火」不会被太阳干扰。</li>
 * </ul>
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class EmberForgeTests {

    private static final String EMPTY = "empty";
    private static final String EMPTY_LARGE = "empty_large";
    private static final BlockPos FORGE = new BlockPos(5, 2, 5);
    private static final int FULL = MRUStaffItem.MAX_MRU;

    private EmberForgeTests() {
    }

    // ================================================================ 工具

    /** 照结构表摆出全部格子（虚空石、护板随便染几种颜色，证明颜色不影响），立即判一次，返回锻炉 */
    private static EmberForgeBlockEntity buildForge(GameTestHelper helper, BlockPos anchor) {
        int index = 0;
        for (Map.Entry<BlockPos, StructurePattern.Rule> entry : ECEmberForge.STRUCTURE.rules().entrySet()) {
            BlockState state = entry.getValue().displayState();
            if (state.hasProperty(DyeableBlock.COLOR)) {
                state = state.setValue(DyeableBlock.COLOR, DyeColor.values()[index++ % DyeColor.values().length]);
            }
            helper.setBlock(anchor.offset(entry.getKey()), state);
        }
        BlockEntity be = helper.getBlockEntity(anchor);
        helper.assertTrue(be instanceof EmberForgeBlockEntity, "锻炉那一格应是余烬锻炉的方块实体，实际 " + be);
        EmberForgeBlockEntity forge = (EmberForgeBlockEntity) be;
        forge.structure().checkNow(helper.getLevel(), forge.getBlockPos(), Direction.NORTH);
        return forge;
    }

    private static ItemStack ember(EmberKind kind, int count) {
        return new ItemStack(kind.item(), count);
    }

    private static ItemStack focus(Element element) {
        Item item = switch (element) {
            case FIRE -> ECEssence.FIRE_FOCUS.get();
            case WATER -> ECEssence.WATER_FOCUS.get();
            case EARTH -> ECEssence.EARTH_FOCUS.get();
            case AIR -> ECEssence.AIR_FOCUS.get();
            case COMBINED -> throw new IllegalArgumentException("没有复合焦点");
        };
        return new ItemStack(item);
    }

    /** 在第 slot 格的底面正中放一件（速度为零） */
    private static ItemEntity put(GameTestHelper helper, EmberForgeBlockEntity forge, int slot, ItemStack stack) {
        BlockPos cell = forge.cell(slot);
        return spawnItem(helper.getLevel(), new Vec3(cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D), stack);
    }

    private static ItemEntity spawnItem(ServerLevel level, Vec3 at, ItemStack stack) {
        ItemEntity entity = new ItemEntity(level, at.x, at.y, at.z, stack, 0D, 0D, 0D);
        level.addFreshEntity(entity);
        return entity;
    }

    /** 8 格放满：余烬格按 embers、焦点格按 foci，每件 count 个（焦点一组只有 1 个，count 只管余烬） */
    private static ItemEntity[] fill(GameTestHelper helper, EmberForgeBlockEntity forge, EmberKind[] embers, Element[] foci, int count) {
        ItemEntity[] out = new ItemEntity[EmberForgeBlockEntity.SLOT_COUNT];
        for (int i = 0; i < 4; i++) {
            out[i] = put(helper, forge, i, ember(embers[i], count));
            out[4 + i] = put(helper, forge, 4 + i, focus(foci[i]));
        }
        return out;
    }

    private static ItemEntity[] fillDefault(GameTestHelper helper, EmberForgeBlockEntity forge) {
        return fill(helper, forge, new EmberKind[]{ACIDIC, CHAOS, COMMON, FLAME}, new Element[]{Element.FIRE, Element.FIRE, Element.FIRE, Element.WATER}, 1);
    }

    /** 锻炉周围的剑（掉落物） */
    private static List<ItemEntity> swords(GameTestHelper helper, AABB area) {
        Item sword = ECEmberForge.FORGED_ELEMENTAL_SWORD.get();
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area, entity -> entity.isAlive() && entity.getItem().is(sword));
    }

    private static AABB around(GameTestHelper helper, BlockPos relative, double radius) {
        return new AABB(helper.absolutePos(relative)).inflate(radius);
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getKey() : component.getString();
    }

    /** 挂一个只在测试期间有效的事件监听器，返回摘掉它的动作 */
    private static <E extends net.minecraftforge.eventbus.api.Event> Runnable listen(Class<E> type, Consumer<E> listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, type, listener);
        return () -> MinecraftForge.EVENT_BUS.unregister(listener);
    }

    /** 能挨打的真玩家（出生无敌只在 ServerPlayer.tick 里减，先空跑 61 次） */
    private static ServerPlayer fighter(GameTestHelper helper, Vec3 relativeFeet) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        for (int i = 0; i <= 60; i++) {
            player.tick();
        }
        Vec3 at = helper.absoluteVec(relativeFeet);
        player.moveTo(at.x, at.y, at.z, 0F, 0F);
        player.setHealth(player.getMaxHealth());
        // 攻击冷却清零：蓄力不满就不横扫、不暴击，砍一下只打 0.2 点，旁边的生物不会被横扫波及
        player.resetAttackStrengthTicker();
        return player;
    }

    private static ItemStack sword(ForgedSwordData data, int mru) {
        ItemStack stack = MRUStaffItem.full(ECEmberForge.FORGED_ELEMENTAL_SWORD.get());
        stack.getCapability(com.flameforge.essentialcraft5.api.ECCapabilities.MRU_ITEM).ifPresent(handler -> handler.setMRU(mru));
        data.writeTo(stack);
        return stack;
    }

    /** 主元素 primary、副元素 secondary、余烬 kinds（不足 4 个循环补齐） */
    private static ForgedSwordData data(Element primary, Element secondary, EmberKind... kinds) {
        List<ResourceLocation> embers = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            embers.add(kinds[i % kinds.length].id());
        }
        List<ResourceLocation> foci = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            foci.add(Element.FIRE.focus());
        }
        return new ForgedSwordData(embers, foci, primary, secondary);
    }

    private static int mru(ItemStack stack) {
        return MRUStaffItem.storedMRU(stack);
    }

    private static double attackBonus(ItemStack stack, EquipmentSlot slot) {
        double sum = 0D;
        for (AttributeModifier modifier : stack.getAttributeModifiers(slot).get(Attributes.ATTACK_DAMAGE)) {
            sum += modifier.getAmount();
        }
        return sum;
    }

    private static int amplifier(net.minecraft.world.entity.LivingEntity entity, MobEffect effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance == null ? -1 : instance.getAmplifier();
    }

    private static Husk husk(GameTestHelper helper, BlockPos relative) {
        Husk husk = helper.spawn(EntityType.HUSK, relative);
        husk.setNoAi(true);
        return husk;
    }

    // ================================================================ 锻炉：结构

    /** 结构：38 格（虚空石 29、淡青护板 4、魔力护板 4、锻炉本身），颜色随意都成形；少一块淡青护板判为不成形、右键报那一格 */
    @GameTest(template = EMPTY_LARGE)
    public static void emberForgeStructure(GameTestHelper helper) {
        EmberForgeBlockEntity forge = buildForge(helper, FORGE);
        helper.assertTrue(forge.structure().formed(), "摆全了应成形，不符：" + forge.mismatchOffset());
        StructurePattern pattern = ECEmberForge.STRUCTURE;
        helper.assertTrue(pattern.rules().size() == 38, "结构应有 38 格，实际 " + pattern.rules().size());
        helper.assertTrue(pattern.offsets(ECEmberForge.VOID_STONE).size() == 29 && pattern.offsets(ECEmberForge.PALE_PLATING).size() == 4
                        && pattern.offsets(ECEmberForge.MAGIC_PLATING).size() == 4,
                "虚空石 29 / 淡青护板 4 / 魔力护板 4，实际 " + pattern.offsets(ECEmberForge.VOID_STONE).size() + " / "
                        + pattern.offsets(ECEmberForge.PALE_PLATING).size() + " / " + pattern.offsets(ECEmberForge.MAGIC_PLATING).size());
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(pattern.rules().get(EmberForgeBlockEntity.slot(i).below()) == ECEmberForge.PALE_PLATING, "余烬格 " + i + " 下面应是淡青护板");
            helper.assertTrue(pattern.rules().get(EmberForgeBlockEntity.slot(4 + i).below()) == ECEmberForge.MAGIC_PLATING, "焦点格 " + i + " 下面应是魔力护板");
        }
        BlockPos pale = new BlockPos(2, 0, 0);
        helper.setBlock(FORGE.offset(pale), Blocks.STONE);
        Component message = forge.statusMessage(helper.getLevel());
        helper.assertTrue(!forge.structure().formed() && pale.equals(forge.mismatchOffset()), "少一块淡青护板应不成形、报 (2, 0, 0)，实际 " + forge.mismatchOffset());
        helper.assertTrue(StructurePattern.KEY_MISMATCH.equals(key(message)), "右键应报缺哪一格，实际 " + message.getString());
        helper.succeed();
    }

    /** 配方：工作台有序配方 SRI / PCP / VEV，原料里有虚空核心、魔力纯化末影鳞合金、永恒物质 */
    @GameTest(template = EMPTY)
    public static void emberForgeRecipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item forgeItem = ECEmberForge.EMBER_FORGE.get().asItem();
        CraftingRecipe found = null;
        for (CraftingRecipe recipe : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (recipe.getResultItem(level.registryAccess()).is(forgeItem)) {
                found = recipe;
            }
        }
        helper.assertTrue(found != null, "应有产出余烬锻炉的工作台配方");
        Set<Item> inputs = new HashSet<>();
        for (Ingredient ingredient : found.getIngredients()) {
            for (ItemStack stack : ingredient.getItems()) {
                inputs.add(stack.getItem());
            }
        }
        helper.assertTrue(inputs.contains(ECVoid.VOID_CORE.get()) && inputs.contains(ECVoid.VOID_MRU_REACTOR.get()) && inputs.contains(ECVoid.VOID_PLATING.get()),
                "原料应有虚空核心、MRU 反应核、虚空板：" + inputs);
        helper.assertTrue(inputs.contains(ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("magic_purified_ender_scale_alloy")))
                && inputs.contains(ForgeRegistries.ITEMS.getValue(EssentialCraft5.id("matter_of_eternity"))), "原料应有魔力纯化末影鳞合金、永恒物质：" + inputs);
        helper.assertTrue(new ItemStack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get()).is(ItemTags.SWORDS), "余烬元素剑应在 minecraft:swords 里");
        helper.succeed();
    }

    // ================================================================ 锻炉：锻造

    /** 夜里（覆盖）、锻一把 20 刻：余烬各 2 个、焦点各 1 个（一组只有 1 个）→ 出 1 把剑、每格各少 1、锻好的剑带锻造数据 */
    @GameTest(template = EMPTY_LARGE)
    public static void emberForgeForgesAtNight(GameTestHelper helper) {
        EmberForgeBlockEntity forge = buildForge(helper, FORGE);
        forge.setTestOverrides(true, 20);
        EmberKind[] embers = {CRYSTAL, CRYSTAL, CRYSTAL, CRYSTAL};
        ItemEntity[] items = fill(helper, forge, embers, new Element[]{Element.EARTH, Element.EARTH, Element.AIR, Element.EARTH}, 2);
        helper.runAtTickTime(40, () -> {
            List<ItemEntity> made = swords(helper, around(helper, FORGE, 3D));
            helper.assertTrue(made.size() == 1, "应正好出 1 把剑，实际 " + made.size());
            for (int i = 0; i < 4; i++) {
                helper.assertTrue(items[i].isAlive() && items[i].getItem().getCount() == 1, "余烬格 " + i + " 应剩 1 个，实际 " + items[i].getItem().getCount());
                helper.assertTrue(!items[4 + i].isAlive(), "焦点格 " + i + " 的焦点应用掉");
            }
            ForgedSwordData data = ForgedSwordData.read(made.get(0).getItem()).orElse(null);
            helper.assertTrue(data != null && data.embers().size() == 4 && data.foci().size() == 4, "剑应记下 4 个余烬、4 个焦点：" + data);
            helper.assertTrue(data.primary() == Element.EARTH, "3 地 1 风 → 主元素地，实际 " + data.primary());
            helper.assertTrue(data.emberDamage() == 0 && data.effects().isEmpty(), "4 个同种余烬不配对");
            helper.assertTrue(forge.forgedCountForTest() == 1 && forge.status() == Status.MISSING, "只出过一把，焦点用完后缺件：" + forge.status());
            helper.succeed();
        });
    }

    /** 原作先扣后读：每格只有 1 个时也要记全 8 个注册名（守「先记后扣」）；3 火 1 水 → 主元素火、副元素只能是火或水；8 件都用完 */
    @GameTest(template = EMPTY_LARGE)
    public static void emberForgeRecordsSingleItems(GameTestHelper helper) {
        EmberForgeBlockEntity forge = buildForge(helper, FORGE);
        forge.setTestOverrides(true, 20);
        ItemEntity[] items = fillDefault(helper, forge);
        helper.runAtTickTime(30, () -> {
            List<ItemEntity> made = swords(helper, around(helper, FORGE, 3D));
            helper.assertTrue(made.size() == 1, "应出 1 把剑，实际 " + made.size());
            for (ItemEntity item : items) {
                helper.assertTrue(!item.isAlive(), "每格只有 1 个，应全部用完");
            }
            ForgedSwordData data = ForgedSwordData.read(made.get(0).getItem()).orElseThrow();
            helper.assertTrue(data.embers().equals(List.of(ACIDIC.id(), CHAOS.id(), COMMON.id(), FLAME.id())), "余烬按格的顺序记下：" + data.embers());
            helper.assertTrue(data.foci().equals(List.of(Element.FIRE.focus(), Element.FIRE.focus(), Element.FIRE.focus(), Element.WATER.focus())),
                    "焦点按格的顺序记下：" + data.foci());
            helper.assertTrue(data.primary() == Element.FIRE, "3 火 1 水 → 火，实际 " + data.primary());
            helper.assertTrue(data.secondary() == Element.FIRE || data.secondary() == Element.WATER, "副元素只从出现过的元素挑，实际 " + data.secondary());
            helper.assertTrue(data.emberDamage() == 36, "酸蚀 + 混沌 + 阴燃 + 燃烧 = 4 + 2 + 8 + 7 + 4 + 11 = 36，实际 " + data.emberDamage());
            helper.succeed();
        });
    }

    /** 白天（要求夜里）：八样齐也不推进、东西照样托着；不要求夜里时白天也行；没有天光的维度随时都行（纯函数） */
    @GameTest(template = EMPTY_LARGE)
    public static void emberForgeNightRule(GameTestHelper helper) {
        EmberForgeBlockEntity forge = buildForge(helper, FORGE);
        forge.setTestOverrides(false, 20);
        forge.setTestNightRequired(true);
        ItemEntity[] items = fillDefault(helper, forge);
        helper.assertTrue(EmberForgeBlockEntity.canForge(true, false, true), "没有天光的维度（下界、末地）白天也算");
        helper.assertTrue(!EmberForgeBlockEntity.canForge(true, true, true), "有天光、白天、要求夜里 → 不行");
        helper.assertTrue(EmberForgeBlockEntity.canForge(true, true, false) && EmberForgeBlockEntity.canForge(false, true, true), "夜里、或不要求夜里 → 行");
        helper.runAtTickTime(35, () -> {
            helper.assertTrue(forge.progress() == 0 && forge.status() == Status.WAITING_NIGHT, "白天应保持 0：进度 " + forge.progress() + "，状态 " + forge.status());
            helper.assertTrue(swords(helper, around(helper, FORGE, 3D)).isEmpty(), "白天不出剑");
            helper.assertTrue(ECItemEntities.pickupDelay(items[0]) > 0, "白天东西照样托着");
            helper.assertTrue(key(forge.statusMessage(helper.getLevel())).equals("message.essentialcraft5.ember_forge.waiting_night"), "右键报「等天黑」");
            forge.setTestNightRequired(false);
        });
        helper.runAtTickTime(65, () -> {
            helper.assertTrue(swords(helper, around(helper, FORGE, 3D)).size() == 1, "不要求夜里后白天也出剑");
            helper.succeed();
        });
    }

    /** 缺一件进度清零；有红石只停进度、东西照常托着（拾取延迟、寿命都续着），去掉红石接着走 */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 140)
    public static void emberForgeMissingAndRedstone(GameTestHelper helper) {
        EmberForgeBlockEntity forge = buildForge(helper, FORGE);
        forge.setTestOverrides(true, 10_000);
        ItemEntity[] items = fillDefault(helper, forge);
        int[] saved = new int[1];
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(forge.progress() >= 20 && forge.status() == Status.FORGING, "应在锻造：进度 " + forge.progress() + "，状态 " + forge.status());
            helper.setBlock(FORGE.east(), Blocks.REDSTONE_BLOCK);
            saved[0] = forge.progress();
        });
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(forge.status() == Status.POWERED && forge.progress() == saved[0], "有红石应暂停、进度不变：" + forge.progress() + " / " + saved[0]);
            // 有红石 35 刻：以前整轮跳过，拾取延迟 20 刻就走完了、寿命也不再续
            for (ItemEntity item : items) {
                helper.assertTrue(ECItemEntities.pickupDelay(item) > 0, "有红石时照样托着，拾取延迟应 > 0，实际 " + ECItemEntities.pickupDelay(item));
                helper.assertTrue(item.lifespan >= item.getAge() + EmberForgeBlockEntity.LIFESPAN_EXTENSION - EmberForgeBlockEntity.SCAN_INTERVAL,
                        "有红石时寿命照样续，实际 " + item.lifespan + " / 年龄 " + item.getAge());
            }
            helper.assertTrue(forge.heldMask() == 0xFF, "8 格都托着：" + Integer.toBinaryString(forge.heldMask()));
            helper.setBlock(FORGE.east(), Blocks.AIR);
        });
        helper.runAtTickTime(85, () -> {
            helper.assertTrue(forge.progress() > saved[0], "去掉红石接着走：" + forge.progress());
            items[5].discard();
        });
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(forge.progress() == 0 && forge.status() == Status.MISSING, "缺一件应清零：进度 " + forge.progress() + "，状态 " + forge.status());
            Component message = forge.statusMessage(helper.getLevel());
            helper.assertTrue(key(message).equals("message.essentialcraft5.ember_forge.missing"), "右键报缺件：" + message.getString());
            helper.succeed();
        });
    }

    /** 有红石时潜行放开：放开照样生效（判断顺序是结构 → 放开 → 红石），30 刻后东西仍然能捡，不会被托回去；有红石不出剑 */
    @GameTest(template = EMPTY_LARGE)
    public static void emberForgeRedstoneRelease(GameTestHelper helper) {
        helper.setBlock(FORGE.east(), Blocks.REDSTONE_BLOCK);
        EmberForgeBlockEntity forge = buildForge(helper, FORGE);
        forge.setTestOverrides(true, 20);
        ItemEntity[] items = fillDefault(helper, forge);
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(forge.status() == Status.POWERED && forge.progress() == 0, "有红石应只托不推进：" + forge.status() + " / " + forge.progress());
            for (ItemEntity item : items) {
                helper.assertTrue(ECItemEntities.pickupDelay(item) > 0, "有红石时照样托着");
            }
            forge.releaseHeld(helper.getLevel(), true);
        });
        helper.runAtTickTime(55, () -> {
            for (ItemEntity item : items) {
                helper.assertTrue(ECItemEntities.pickupDelay(item) == 0, "放开 30 刻后拾取延迟应仍为 0，实际 " + ECItemEntities.pickupDelay(item));
            }
            helper.assertTrue(forge.status() == Status.RELEASED, "放开期间状态应是 RELEASED，实际 " + forge.status());
            helper.assertTrue(key(forge.statusMessage(helper.getLevel())).equals("message.essentialcraft5.ember_forge.releasing"), "放开期间右键先报「已放开」，再轮到红石");
            helper.assertTrue(swords(helper, around(helper, FORGE, 3D)).isEmpty(), "有红石不出剑");
            helper.succeed();
        });
    }

    /** 指定了别人拾取的、永远捡不起来的、放错格的都不托、不消耗；指定给锻炉主人的照托 */
    @GameTest(template = EMPTY_LARGE)
    public static void emberForgeSkipsForeignItems(GameTestHelper helper) {
        EmberForgeBlockEntity forge = buildForge(helper, FORGE);
        forge.setTestOverrides(true, 20);
        UUID owner = UUID.randomUUID();
        forge.setOwner(owner, "ec5-owner");
        ItemEntity[] items = fillDefault(helper, forge);
        items[0].setTarget(UUID.randomUUID());
        items[4].setNeverPickUp();
        items[1].setTarget(owner);
        // 余烬格里另放一个焦点（放错格）
        ItemEntity wrong = put(helper, forge, 2, focus(Element.AIR));
        helper.runAtTickTime(35, () -> {
            helper.assertTrue(swords(helper, around(helper, FORGE, 3D)).isEmpty(), "两格的东西不能用，不该出剑");
            helper.assertTrue(forge.status() == Status.MISSING && (forge.heldMask() & 0b10001) == 0, "别人的、展示品不托：掩码 " + Integer.toBinaryString(forge.heldMask()));
            helper.assertTrue((forge.heldMask() & 0b10) != 0, "指定给锻炉主人的照托");
            helper.assertTrue(ECItemEntities.pickupDelay(items[0]) == 0 && ECItemEntities.neverPickUp(items[4]), "不托的不改拾取延迟");
            helper.assertTrue(ECItemEntities.pickupDelay(wrong) == 0, "放错格的焦点不托");
            for (ItemEntity item : items) {
                helper.assertTrue(item.isAlive() && item.getItem().getCount() == 1, "什么都不该被消耗");
            }
            helper.succeed();
        });
    }

    /** 托住：偏到格子边上、还在横着动的东西被摆回水平正中、落在护板顶面上；拾取延迟续着、寿命续到年龄 + 6000；拆掉正下方的虚空石后 20 多刻内就能捡 */
    @GameTest(template = EMPTY_LARGE)
    public static void emberForgeHoldsAndLets(GameTestHelper helper) {
        EmberForgeBlockEntity forge = buildForge(helper, FORGE);
        forge.setTestOverrides(false, 10_000);
        forge.setTestNightRequired(true);
        BlockPos cell = forge.cell(0);
        ItemEntity loose = spawnItem(helper.getLevel(), new Vec3(cell.getX() + 0.7D, cell.getY() + 0.5D, cell.getZ() + 0.35D), ember(DIVINE, 1));
        loose.setDeltaMovement(0.02D, 0D, 0.02D);
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(Math.abs(loose.getX() - (cell.getX() + 0.5D)) < 0.06D && Math.abs(loose.getZ() - (cell.getZ() + 0.5D)) < 0.06D
                    && Math.abs(loose.getY() - cell.getY()) < 0.06D, "应摆到格子正中、落在护板顶面上，实际 " + loose.position());
            int delay = ECItemEntities.pickupDelay(loose);
            helper.assertTrue(delay > 0 && delay <= EmberForgeBlockEntity.HOLD_PICKUP_DELAY, "托着时拾取延迟应在 1~20，实际 " + delay);
            helper.assertTrue(loose.lifespan >= loose.getAge() + EmberForgeBlockEntity.LIFESPAN_EXTENSION - EmberForgeBlockEntity.SCAN_INTERVAL,
                    "寿命应续到年龄 + 6000，实际 " + loose.lifespan + " / 年龄 " + loose.getAge());
            helper.assertTrue((forge.heldMask() & 1) != 0, "第 0 格应托着");
            helper.setBlock(FORGE.below(), Blocks.AIR);
        });
        helper.runAtTickTime(75, () -> {
            helper.assertTrue(forge.status() == Status.INCOMPLETE, "拆了一块应不成形，状态 " + forge.status());
            helper.assertTrue(ECItemEntities.pickupDelay(loose) == 0, "锻炉停了以后拾取延迟应走完，实际 " + ECItemEntities.pickupDelay(loose));
            helper.succeed();
        });
    }

    /** 锻炉被拆：onRemove 当场放开，同一刻 8 件的拾取延迟都是 0（不等托住的 20 刻自然走完） */
    @GameTest(template = EMPTY_LARGE)
    public static void emberForgeRemoveReleases(GameTestHelper helper) {
        EmberForgeBlockEntity forge = buildForge(helper, FORGE);
        forge.setTestOverrides(true, 10_000);
        ItemEntity[] items = fillDefault(helper, forge);
        helper.runAtTickTime(15, () -> {
            for (ItemEntity item : items) {
                helper.assertTrue(ECItemEntities.pickupDelay(item) > 0, "拆之前应托着");
            }
            helper.setBlock(FORGE, Blocks.AIR);
            for (ItemEntity item : items) {
                helper.assertTrue(ECItemEntities.pickupDelay(item) == 0, "拆掉的同一刻应放开，实际拾取延迟 " + ECItemEntities.pickupDelay(item));
            }
            helper.succeed();
        });
    }

    /**
     * 潜行空手右键（走服务端真的交互路径 {@code ServerPlayerGameMode.useItemOn}，副手拿着盾）：放开所有东西（拾取延迟立即清零）、进度清零，10 秒内不再托住。
     * 副手非空时原版潜行不调方块的 use，靠 {@code EmberForgeBlock.EmberForgeSneakRelease} 放行；别的模组在更高优先级设了 DENY 时不放行。
     */
    @GameTest(template = EMPTY_LARGE)
    public static void emberForgeSneakReleases(GameTestHelper helper) {
        EmberForgeBlockEntity forge = buildForge(helper, FORGE);
        forge.setTestOverrides(true, 10_000);
        ItemEntity[] items = fillDefault(helper, forge);
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(ECItemEntities.pickupDelay(items[0]) > 0 && forge.progress() > 0, "放开前应托着、在锻造");
            ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            player.setShiftKeyDown(true);
            BlockPos abs = forge.getBlockPos();
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
            // 领地之类的模组在更高优先级设了 useBlock DENY：不改回 ALLOW、不放开
            Consumer<PlayerInteractEvent.RightClickBlock> otherMod = event -> {
                if (event.getPos().equals(abs)) {
                    event.setUseBlock(Event.Result.DENY);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, PlayerInteractEvent.RightClickBlock.class, otherMod);
            InteractionResult denied;
            try {
                denied = player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            } finally {
                MinecraftForge.EVENT_BUS.unregister(otherMod);
            }
            helper.assertTrue(!denied.consumesAction() && forge.status() != Status.RELEASED && ECItemEntities.pickupDelay(items[0]) > 0,
                    "被拦下时不该放开：" + denied + "，状态 " + forge.status());
            InteractionResult result = player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(result.consumesAction(), "副手拿着盾、潜行空手右键应接管，实际 " + result);
            helper.assertTrue(!player.isUsingItem(), "不该接着去举副手的盾");
            for (ItemEntity item : items) {
                helper.assertTrue(ECItemEntities.pickupDelay(item) == 0, "放开后拾取延迟应立即清零");
            }
            helper.assertTrue(forge.status() == Status.RELEASED && forge.progress() == 0, "放开后进度清零，状态 " + forge.status());
        });
        helper.runAtTickTime(60, () -> {
            for (ItemEntity item : items) {
                helper.assertTrue(ECItemEntities.pickupDelay(item) == 0, "10 秒内不再托住");
            }
            helper.assertTrue(forge.progress() == 0 && forge.status() == Status.RELEASED, "10 秒内不推进，状态 " + forge.status());
            helper.assertTrue(key(forge.statusMessage(helper.getLevel())).equals("message.essentialcraft5.ember_forge.releasing"), "右键报「已放开」");
            helper.succeed();
        });
    }

    /** 相邻两台共用边上的余烬格和两个焦点格、那三件各 1 个：先到点的那台扣掉，另一台查不到就不出剑，一共只出 1 把、不复制 */
    @GameTest(template = EMPTY_LARGE)
    public static void emberForgeSharedSlotsOnce(GameTestHelper helper) {
        BlockPos westAnchor = new BlockPos(3, 2, 5);
        BlockPos eastAnchor = new BlockPos(7, 2, 5);
        EmberForgeBlockEntity west = buildForge(helper, westAnchor);
        EmberForgeBlockEntity east = buildForge(helper, eastAnchor);
        helper.assertTrue(west.structure().formed() && east.structure().formed(), "两台都应成形：" + west.mismatchOffset() + " / " + east.mismatchOffset());
        helper.assertTrue(west.cell(0).equals(east.cell(1)) && west.cell(4).equals(east.cell(5)) && west.cell(6).equals(east.cell(7)), "应共用 1 个余烬格、2 个焦点格");
        west.setTestOverrides(true, 20);
        east.setTestOverrides(true, 20);
        // 西边那台放满 8 格（含共用的 3 格），东边那台只补它自己的 5 格
        fillDefault(helper, west);
        put(helper, east, 0, ember(CRYSTAL, 1));
        put(helper, east, 2, ember(CRYSTAL, 1));
        put(helper, east, 3, ember(CRYSTAL, 1));
        put(helper, east, 4, focus(Element.EARTH));
        put(helper, east, 6, focus(Element.EARTH));
        AABB both = new AABB(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(11, 8, 11)));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(swords(helper, both).size() == 1, "共用的三件各 1 个，只能出 1 把剑，实际 " + swords(helper, both).size());
            helper.assertTrue(west.forgedCountForTest() + east.forgedCountForTest() == 1, "两台合计只出过一把");
            int left = 0;
            for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, both, ItemEntity::isAlive)) {
                if (!entity.getItem().is(ECEmberForge.FORGED_ELEMENTAL_SWORD.get())) {
                    left += entity.getItem().getCount();
                }
            }
            helper.assertTrue(left == 13 - 8, "13 件里应只用掉 8 件，剩 " + left);
            helper.succeed();
        });
    }

    /**
     * 相邻两台共用格子时潜行放开西边那台：共用的 3 件打了放开标记，东边那台也不再托它们，30 刻后拾取延迟仍为 0；
     * 东边那台自己的 5 件照常托着
     */
    @GameTest(template = EMPTY_LARGE)
    public static void emberForgeSharedRelease(GameTestHelper helper) {
        BlockPos westAnchor = new BlockPos(3, 2, 5);
        BlockPos eastAnchor = new BlockPos(7, 2, 5);
        EmberForgeBlockEntity west = buildForge(helper, westAnchor);
        EmberForgeBlockEntity east = buildForge(helper, eastAnchor);
        // 白天、要求夜里：只托不锻，免得出剑把东西用掉
        west.setTestOverrides(false, 20);
        east.setTestOverrides(false, 20);
        west.setTestNightRequired(true);
        east.setTestNightRequired(true);
        ItemEntity[] westItems = fillDefault(helper, west);
        List<ItemEntity> shared = List.of(westItems[0], westItems[4], westItems[6]);
        List<ItemEntity> eastOwn = List.of(put(helper, east, 0, ember(CRYSTAL, 1)), put(helper, east, 2, ember(CRYSTAL, 1)),
                put(helper, east, 3, ember(CRYSTAL, 1)), put(helper, east, 4, focus(Element.EARTH)), put(helper, east, 6, focus(Element.EARTH)));
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(west.status() == Status.WAITING_NIGHT && east.status() == Status.WAITING_NIGHT,
                    "两台都应八样齐、等天黑：" + west.status() + " / " + east.status());
            west.releaseHeld(helper.getLevel(), true);
        });
        helper.runAtTickTime(45, () -> {
            for (ItemEntity item : shared) {
                helper.assertTrue(ECItemEntities.pickupDelay(item) == 0, "共用格里放开的东西不该被东边那台托回去，拾取延迟 " + ECItemEntities.pickupDelay(item));
            }
            for (ItemEntity item : eastOwn) {
                helper.assertTrue(ECItemEntities.pickupDelay(item) > 0, "东边那台自己的东西照常托着");
            }
            helper.assertTrue((east.heldMask() & 0b1010_0010) == 0 && east.status() == Status.MISSING,
                    "东边那台的共用 3 格应算空：掩码 " + Integer.toBinaryString(east.heldMask()) + "，状态 " + east.status());
            helper.succeed();
        });
    }

    /** 到点前拿走一个焦点：不出剑、别的东西一件不少 */
    @GameTest(template = EMPTY_LARGE)
    public static void emberForgeFocusRemovedBeforeDone(GameTestHelper helper) {
        EmberForgeBlockEntity forge = buildForge(helper, FORGE);
        forge.setTestOverrides(true, 40);
        ItemEntity[] items = fillDefault(helper, forge);
        helper.runAtTickTime(15, () -> items[6].discard());
        helper.runAtTickTime(70, () -> {
            helper.assertTrue(swords(helper, around(helper, FORGE, 3D)).isEmpty(), "缺了焦点不该出剑");
            for (int i = 0; i < items.length; i++) {
                if (i != 6) {
                    helper.assertTrue(items[i].isAlive() && items[i].getItem().getCount() == 1, "第 " + i + " 格的东西不该被扣");
                }
            }
            helper.assertTrue(forge.progress() == 0, "进度应清零，实际 " + forge.progress());
            helper.succeed();
        });
    }

    /** 实体查询每 10 刻一次；槽位的实体区段没在跑时照样托着查得到的东西、不推进，进度留着不清零，恢复后接着走 */
    @GameTest(template = EMPTY_LARGE, timeoutTicks = 140)
    public static void emberForgeScanIntervalAndUnloaded(GameTestHelper helper) {
        EmberForgeBlockEntity forge = buildForge(helper, FORGE);
        forge.setTestOverrides(true, 10_000);
        ItemEntity[] items = fillDefault(helper, forge);
        long[] scans = new long[1];
        int[] saved = new int[1];
        helper.runAtTickTime(51, () -> {
            long count = forge.scansForTest();
            helper.assertTrue(count >= 5 && count <= 6, "51 刻应查 5~6 次，实际 " + count);
            forge.setTestEntitiesLoaded(false);
            scans[0] = count;
            saved[0] = forge.progress();
        });
        helper.runAtTickTime(85, () -> {
            helper.assertTrue(forge.status() == Status.UNLOADED && forge.progress() == saved[0], "区段没在跑：进度不动不清零，" + forge.progress() + " / " + saved[0]);
            helper.assertTrue(forge.scansForTest() > scans[0], "区段没在跑时照样每轮查一次、托住查得到的");
            for (ItemEntity item : items) {
                helper.assertTrue(ECItemEntities.pickupDelay(item) > 0, "区段没在跑 34 刻，查得到的东西照样托着，拾取延迟 " + ECItemEntities.pickupDelay(item));
            }
            forge.setTestEntitiesLoaded(null);
        });
        helper.runAtTickTime(110, () -> {
            helper.assertTrue(forge.status() == Status.FORGING && forge.progress() > saved[0], "恢复后接着走：" + forge.progress());
            helper.succeed();
        });
    }

    /**
     * 槽位就绪（{@code slotsReady}）：每格都要「实体区段在跑」并且「所在区块的实体已经读完」。
     * 邻区块进了实体 tick 范围、实体还在异步读的那段时间判为没就绪（以前只看前一条，查空了就把进度清零）。GameTest 造不出异步读取，测纯函数
     */
    @GameTest(template = EMPTY)
    public static void emberForgeSlotsReady(GameTestHelper helper) {
        // 锻炉离区块东边界 1 格：x + 2 的余烬格落进东边的区块
        BlockPos origin = new BlockPos(14, 64, 7);
        long home = ChunkPos.asLong(origin);
        long east = ChunkPos.asLong(origin.offset(EmberForgeBlockEntity.slot(0)));
        helper.assertTrue(home != east, "第 0 格应在相邻区块");
        helper.assertTrue(EmberForgeBlockEntity.slotsReady(origin, pos -> true, chunk -> true), "都在跑、实体都读完 → 就绪");
        helper.assertTrue(!EmberForgeBlockEntity.slotsReady(origin, pos -> true, chunk -> chunk != east), "邻区块在跑、实体还没读完 → 没就绪");
        BlockPos focus = origin.offset(EmberForgeBlockEntity.slot(7));
        helper.assertTrue(!EmberForgeBlockEntity.slotsReady(origin, pos -> !pos.equals(focus), chunk -> true), "有一格区段没在跑 → 没就绪");
        ServerLevel level = helper.getLevel();
        helper.assertTrue(EmberForgeBlockEntity.slotsReady(helper.absolutePos(FORGE), level::isPositionEntityTicking, level::areEntitiesLoaded),
                "测试世界里的锻炉位置应就绪");
        helper.succeed();
    }

    // ================================================================ 剑：属性与锻造规则

    /** 攻击：主元素 火 7 / 水 3 / 地 8 / 风 8 / 复合 5，加余烬加成；没锻造的 0；攻速 −2.4；只在主手；不掉耐久、能附魔、能格挡；创造栏空 / 满两份 */
    @GameTest(template = EMPTY)
    public static void forgedSwordAttributes(GameTestHelper helper) {
        int[] expected = {7, 3, 8, 8, 5};
        for (Element element : Element.values()) {
            ItemStack stack = sword(data(element, element, COMMON), 0);
            double bonus = attackBonus(stack, EquipmentSlot.MAINHAND);
            helper.assertTrue(bonus == expected[element.ordinal()], element + " 主元素加成应为 " + expected[element.ordinal()] + "，实际 " + bonus);
        }
        ItemStack best = sword(data(Element.EARTH, Element.FIRE, ACIDIC, CHAOS, COMMON, FLAME), 0);
        helper.assertTrue(attackBonus(best, EquipmentSlot.MAINHAND) == 8 + 36, "地 + 余烬 36 = 44，实际 " + attackBonus(best, EquipmentSlot.MAINHAND));
        ItemStack plain = new ItemStack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get());
        helper.assertTrue(attackBonus(plain, EquipmentSlot.MAINHAND) == 0D, "没锻造的攻击加成应为 0");
        double speed = 0D;
        for (AttributeModifier modifier : best.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_SPEED)) {
            speed += modifier.getAmount();
        }
        helper.assertTrue(Math.abs(speed - ForgedElementalSwordItem.ATTACK_SPEED) < 1.0E-4D, "攻速修正应为 −2.4，实际 " + speed);
        helper.assertTrue(best.getAttributeModifiers(EquipmentSlot.OFFHAND).isEmpty(), "副手不给属性");
        helper.assertTrue(!best.isDamageableItem() && best.isEnchantable(), "不掉耐久、能附魔");
        helper.assertTrue(best.canPerformAction(ToolActions.SHIELD_BLOCK) && best.canPerformAction(ToolActions.SWORD_SWEEP), "能格挡、能横扫");
        helper.assertTrue(best.getUseAnimation() == UseAnim.BLOCK && best.getUseDuration() == ForgedElementalSwordItem.USE_DURATION, "右键按住格挡");
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        best.hurtAndBreak(10, player, p -> {
        });
        helper.assertTrue(best.getDamageValue() == 0, "格挡、挨打都不掉耐久");
        List<ItemStack> variants = ECEmberForge.FORGED_ELEMENTAL_SWORD.get().creativeVariants();
        helper.assertTrue(variants.size() == 2 && mru(variants.get(0)) == 0 && mru(variants.get(1)) == FULL
                && !ForgedSwordData.isForged(variants.get(0)) && !ForgedSwordData.isForged(variants.get(1)), "创造栏应是空 / 满两份、都没锻造");
        helper.succeed();
    }

    /**
     * 余烬加成封顶（{@code general.ember_sword.max_ember_damage}，用测试覆盖值，不碰真配置）：默认 36 等于不封顶（最好的剑 8 + 36）；
     * 设成 16 时最好的剑是 8 + 16、提示行跟着写 16，锻造数据里的合计仍是 36；没到上限的剑不受影响；设成 0 只剩主元素
     */
    @GameTest(template = EMPTY)
    public static void forgedSwordEmberCap(GameTestHelper helper) {
        ItemStack best = sword(data(Element.EARTH, Element.FIRE, ACIDIC, CHAOS, COMMON, FLAME), 0);
        ItemStack small = sword(data(Element.FIRE, Element.FIRE, COMMON, CRYSTAL), 0);
        ForgedSwordData bestData = ForgedSwordData.read(best).orElseThrow();
        helper.assertTrue(ForgedElementalSwordItem.Config.maxEmberDamage() == ForgedElementalSwordItem.MAX_EMBER_DAMAGE
                && ForgedElementalSwordItem.MAX_EMBER_DAMAGE == bestData.emberDamage(), "默认上限应等于最好的组合的合计 36");
        helper.assertTrue(attackBonus(best, EquipmentSlot.MAINHAND) == 8 + 36, "默认不封顶：44，实际 " + attackBonus(best, EquipmentSlot.MAINHAND));
        ForgedElementalSwordItem.Config.setTestMaxEmberDamage(16);
        try {
            helper.assertTrue(attackBonus(best, EquipmentSlot.MAINHAND) == 8 + 16, "上限 16：地 8 + 16 = 24，实际 " + attackBonus(best, EquipmentSlot.MAINHAND));
            helper.assertTrue(ForgedElementalSwordItem.emberBonus(bestData) == 16 && bestData.emberDamage() == 36, "锻造数据里的合计不变，生效的是 16");
            helper.assertTrue(attackBonus(small, EquipmentSlot.MAINHAND) == 7 + 3, "阴燃 + 水晶 3 没到上限，不受影响：" + attackBonus(small, EquipmentSlot.MAINHAND));
            List<Component> tooltip = new ArrayList<>();
            best.getItem().appendHoverText(best, helper.getLevel(), tooltip, TooltipFlag.NORMAL);
            Object shown = null;
            for (Component line : tooltip) {
                if (line.getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals("tooltip.essentialcraft5.forged_elemental_sword.ember_damage")) {
                    shown = contents.getArgs()[0];
                }
            }
            helper.assertTrue(Integer.valueOf(16).equals(shown), "提示行应写封顶后的 16，实际 " + shown);
            ForgedElementalSwordItem.Config.setTestMaxEmberDamage(0);
            helper.assertTrue(attackBonus(best, EquipmentSlot.MAINHAND) == 8, "上限 0：只剩主元素 8，实际 " + attackBonus(best, EquipmentSlot.MAINHAND));
        } finally {
            ForgedElementalSwordItem.Config.setTestMaxEmberDamage(null);
        }
        helper.succeed();
    }

    /** 主元素：某种焦点严格多于其余三种才是它，否则复合；副元素只从出现过的元素挑（固定种子打散后几种都挑得到） */
    @GameTest(template = EMPTY)
    public static void forgedSwordElementRules(GameTestHelper helper) {
        List<ResourceLocation> embers = List.of(ACIDIC.id(), ACIDIC.id(), ACIDIC.id(), ACIDIC.id());
        ResourceLocation fire = Element.FIRE.focus();
        ResourceLocation water = Element.WATER.focus();
        ResourceLocation earth = Element.EARTH.focus();
        ResourceLocation air = Element.AIR.focus();
        RandomSource random = RandomSource.create(42L);
        helper.assertTrue(ForgedSwordData.forge(embers, List.of(fire, fire, fire, water), random).primary() == Element.FIRE, "3 火 1 水 → 火");
        helper.assertTrue(ForgedSwordData.forge(embers, List.of(earth, earth, earth, earth), random).primary() == Element.EARTH, "4 地 → 地");
        helper.assertTrue(ForgedSwordData.forge(embers, List.of(fire, fire, water, water), random).primary() == Element.COMBINED, "2 火 2 水 → 复合");
        helper.assertTrue(ForgedSwordData.forge(embers, List.of(fire, water, earth, air), random).primary() == Element.COMBINED, "四种各一 → 复合");
        helper.assertTrue(ForgedSwordData.forge(embers, List.of(air, air, fire, water), random).primary() == Element.AIR, "2 风 1 火 1 水 → 风");
        helper.assertTrue(ForgedSwordData.forge(embers, List.of(air, air, air, air), random).secondary() == Element.AIR, "只有风 → 副元素只能是风");
        Set<Element> seen = EnumSet.noneOf(Element.class);
        for (int i = 0; i < 64; i++) {
            ForgedSwordData data = ForgedSwordData.forge(embers, List.of(fire, fire, fire, water), RandomSource.create(i * 0x9E3779B97F4A7C15L));
            helper.assertTrue(data.secondary() == Element.FIRE || data.secondary() == Element.WATER, "副元素只能是火或水，实际 " + data.secondary());
            seen.add(data.secondary());
        }
        helper.assertTrue(seen.size() == 2, "64 个种子里火、水都应挑到过：" + seen);
        ForgedSwordData data = ForgedSwordData.forge(embers, List.of(fire, fire, fire, water), RandomSource.create(7L));
        ItemStack stack = sword(data, 0);
        helper.assertTrue(ForgedSwordData.read(stack).orElseThrow().equals(data), "NBT 存读应还原");
        helper.succeed();
    }

    /** 28 对余烬组合逐一对照原作（三处笔误修好后；神圣 + 魔力 / 燃烧两对第十批补成驱散 / 腐蚀）；同种不配对；同值两对每对都算；同一效果两对都出只算一次 */
    @GameTest(template = EMPTY)
    public static void forgedSwordComboTable(GameTestHelper helper) {
        Object[][] expected = {
                {ACIDIC, CHAOS, 4}, {ACIDIC, COMMON, 2}, {ACIDIC, CORRUPTED, 0, SLOWNESS}, {ACIDIC, CRYSTAL, 6},
                {ACIDIC, DIVINE, 0, POISON}, {ACIDIC, MAGICAL, 0, GREATER_POISON}, {ACIDIC, FLAME, 8},
                {CHAOS, COMMON, 7, DAMAGE_SELF}, {CHAOS, CORRUPTED, 0, GREATER_SLOWNESS}, {CHAOS, CRYSTAL, 5}, {CHAOS, DIVINE, 0, LIGHTNING},
                {CHAOS, MAGICAL, 6}, {CHAOS, FLAME, 4},
                {COMMON, CORRUPTED, 0, WEAKNESS}, {COMMON, CRYSTAL, 3}, {COMMON, DIVINE, 0, STRENGTH}, {COMMON, MAGICAL, 0, SPEED}, {COMMON, FLAME, 11},
                {CORRUPTED, CRYSTAL, 0, LIFESTEAL}, {CORRUPTED, DIVINE, 0, DAMAGE_SELF, GREATER_HUNGER}, {CORRUPTED, MAGICAL, 0, GREATER_LIFESTEAL},
                {CORRUPTED, FLAME, 5},
                {CRYSTAL, DIVINE, 9}, {CRYSTAL, MAGICAL, 7}, {CRYSTAL, FLAME, 0, GREATER_SPEED},
                {DIVINE, MAGICAL, 0, DISPEL}, {DIVINE, FLAME, 0, CORROSION},
                {MAGICAL, FLAME, 6}};
        helper.assertTrue(EmberEffect.COMBOS.size() == 28 && expected.length == 28, "应有 28 对，实际 " + EmberEffect.COMBOS.size());
        int corrupted = 0;
        for (Object[] row : expected) {
            EmberKind a = (EmberKind) row[0];
            EmberKind b = (EmberKind) row[1];
            int damage = (Integer) row[2];
            Set<EmberEffect> effects = EnumSet.noneOf(EmberEffect.class);
            for (int i = 3; i < row.length; i++) {
                effects.add((EmberEffect) row[i]);
            }
            EmberEffect.Combo combo = EmberEffect.combo(b, a);
            helper.assertTrue(combo != null && combo == EmberEffect.combo(a, b), a + " + " + b + " 应查得到、顺序无关");
            helper.assertTrue(combo.damage() == damage && combo.effects().equals(effects), a + " + " + b + " 应为 " + damage + " " + effects + "，实际 " + combo);
            ForgedSwordData data = data(Element.COMBINED, Element.COMBINED, a, b);
            helper.assertTrue(data.emberDamage() == damage && data.effects().equals(effects), a + " + " + b + " 装在剑上的结果不对");
            if (a == CORRUPTED || b == CORRUPTED) {
                corrupted += combo.isEmpty() ? 0 : 1;
            }
        }
        helper.assertTrue(corrupted == 7, "腐化余烬的 7 对都应生效（原作写成 corruption 全失效），实际 " + corrupted);
        helper.assertTrue(EmberEffect.combo(CHAOS, CHAOS) == null && data(Element.FIRE, Element.FIRE, CHAOS).combos().isEmpty(), "同种不配对");
        helper.assertTrue(data(Element.FIRE, Element.FIRE, CHAOS, COMMON, CRYSTAL, MAGICAL).emberDamage() == 7 + 5 + 6 + 3 + 7,
                "混沌 + 阴燃 7 与水晶 + 魔力 7 都要算：28");
        Set<EmberEffect> both = data(Element.FIRE, Element.FIRE, CHAOS, COMMON, CORRUPTED, DIVINE).effects();
        helper.assertTrue(both.contains(DAMAGE_SELF) && both.containsAll(List.of(GREATER_SLOWNESS, LIGHTNING, WEAKNESS, STRENGTH, GREATER_HUNGER)),
                "两对都给反噬时集合里只有一份：" + both);
        helper.succeed();
    }

    /** 模型键：主 / 副元素 5 × 5 = 25 种（复合叫 normal），没锻造的是 normal/normal */
    @GameTest(template = EMPTY)
    public static void forgedSwordModelKeys(GameTestHelper helper) {
        Set<String> keys = new HashSet<>();
        for (Element bottom : Element.values()) {
            for (Element top : Element.values()) {
                String key = ForgedSwordData.modelKey(sword(data(bottom, top, COMMON), 0));
                helper.assertTrue(key.equals(bottom.layer() + "/" + top.layer()), bottom + "/" + top + " 的模型键应是 " + bottom.layer() + "/" + top.layer() + "，实际 " + key);
                keys.add(key);
            }
        }
        helper.assertTrue(keys.size() == 25, "应有 25 种模型键，实际 " + keys.size());
        helper.assertTrue(ForgedSwordData.modelKey(new ItemStack(ECEmberForge.FORGED_ELEMENTAL_SWORD.get())).equals("normal/normal"), "没锻造的是 normal/normal");
        helper.assertTrue(keys.contains("fire/water") && Element.COMBINED.layer().equals("normal"), "复合的层名是 normal");
        helper.succeed();
    }

    // ================================================================ 剑：命中

    /** 真玩家砍尸壳：水主元素 + 酸蚀 / 腐化（缓慢）→ 目标虚弱、缓慢，扣 50 + 50；只有伤害加成的余烬不扣那 50；MRU 不够没效果、伤害照打 */
    @GameTest(template = EMPTY)
    public static void forgedSwordHitEffects(GameTestHelper helper) {
        Husk target = husk(helper, new BlockPos(2, 1, 3));
        ServerPlayer player = fighter(helper, new Vec3(2.5D, 1D, 1.5D));
        try {
            ItemStack water = sword(data(Element.WATER, Element.WATER, ACIDIC, CORRUPTED), FULL);
            player.setItemInHand(InteractionHand.MAIN_HAND, water);
            player.attack(target);
            helper.assertTrue(amplifier(target, MobEffects.WEAKNESS) == 0, "水主元素：目标应虚弱 I");
            helper.assertTrue(amplifier(target, MobEffects.MOVEMENT_SLOWDOWN) == 0, "酸蚀 + 腐化：目标应缓慢 I");
            helper.assertTrue(mru(water) == FULL - 2 * ForgedElementalSwordItem.COST, "主元素 50 + 余烬 50，剩 " + mru(water));

            target.removeAllEffects();
            target.invulnerableTime = 0;
            ItemStack damageOnly = sword(data(Element.WATER, Element.WATER, ACIDIC, CHAOS), FULL);
            player.setItemInHand(InteractionHand.MAIN_HAND, damageOnly);
            player.attack(target);
            helper.assertTrue(mru(damageOnly) == FULL - ForgedElementalSwordItem.COST, "只有伤害加成的余烬不扣那 50，剩 " + mru(damageOnly));

            target.removeAllEffects();
            target.invulnerableTime = 0;
            ItemStack empty = sword(data(Element.EARTH, Element.EARTH, ACIDIC, DIVINE), 0);
            player.setItemInHand(InteractionHand.MAIN_HAND, empty);
            float before = target.getHealth();
            player.attack(target);
            helper.assertTrue(target.getActiveEffects().isEmpty(), "MRU 不够时没有任何效果");
            helper.assertTrue(target.getHealth() < before, "伤害照打");

            ItemStack air = sword(data(Element.AIR, Element.AIR, COMMON, MAGICAL), FULL);
            target.invulnerableTime = 0;
            player.setItemInHand(InteractionHand.MAIN_HAND, air);
            player.attack(target);
            helper.assertTrue(amplifier(player, MobEffects.DAMAGE_BOOST) == 0 && amplifier(player, MobEffects.MOVEMENT_SPEED) == 0,
                    "风主元素 + 阴燃 / 魔力：自己力量 I、迅捷 I");
        } finally {
            target.discard();
        }
        helper.succeed();
    }

    /**
     * 引雷（混沌 + 神圣）：世界里没有闪电实体；目标最后一次伤害是闪电、攻击者是玩家、着火；持剑人抗火 II、不掉血；
     * 旁边的村民不受伤、不着火；周围没有火方块；目标只被近战击退一次（闪电那一下不再推）；40 刻内再砍不再劈。复合主元素不扣主元素那份，只扣余烬 50。
     * 另一名持剑人砍另一只、闪电伤害被拦下：受伤冷却放回近战设的值、不着火
     */
    @GameTest(template = EMPTY)
    public static void forgedSwordLightning(GameTestHelper helper) {
        Husk target = husk(helper, new BlockPos(2, 1, 3));
        Villager bystander = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 3));
        bystander.setNoAi(true);
        ServerPlayer player = fighter(helper, new Vec3(2.5D, 1D, 1.5D));
        AtomicInteger strikes = new AtomicInteger();
        Runnable unlisten = listen(LivingHurtEvent.class, event -> {
            if (event.getEntity() == target && event.getSource().is(DamageTypes.LIGHTNING_BOLT)) {
                strikes.incrementAndGet();
            }
        });
        try {
            ItemStack stack = sword(data(Element.COMBINED, Element.COMBINED, CHAOS, DIVINE), FULL);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            float playerHealth = player.getHealth();
            player.attack(target);
            ServerLevel level = helper.getLevel();
            AABB area = around(helper, new BlockPos(2, 1, 3), 6D);
            helper.assertTrue(level.getEntitiesOfClass(LightningBolt.class, area.inflate(10D)).isEmpty(), "不该生成闪电实体");
            // 近战那一下从静止推出 0.4；闪电再推一次的话是 0.4 / 2 + 0.4 = 0.6
            double pushed = target.getDeltaMovement().horizontalDistance();
            helper.assertTrue(pushed > 0.3D && pushed < 0.5D, "目标只该被近战击退一次（约 0.4），实际水平速度 " + pushed);
            DamageSource last = target.getLastDamageSource();
            helper.assertTrue(last != null && last.is(DamageTypes.LIGHTNING_BOLT) && last.getEntity() == player, "目标最后一次伤害应是带攻击者的闪电：" + last);
            helper.assertTrue(target.getRemainingFireTicks() > 0, "目标应着火");
            helper.assertTrue(amplifier(player, MobEffects.FIRE_RESISTANCE) == 1 && player.getHealth() == playerHealth, "持剑人抗火 II、不掉血");
            helper.assertTrue(bystander.getHealth() == bystander.getMaxHealth() && bystander.getRemainingFireTicks() <= 0, "旁边的村民不受影响");
            BlockPos center = helper.absolutePos(new BlockPos(2, 1, 3));
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(3, 3, 3))) {
                helper.assertTrue(!level.getBlockState(pos).is(Blocks.FIRE), "周围不该有火方块：" + pos);
            }
            helper.assertTrue(mru(stack) == FULL - ForgedElementalSwordItem.COST, "复合主元素不扣，余烬扣 50，剩 " + mru(stack));
            helper.assertTrue(strikes.get() == 1, "应劈中一次，实际 " + strikes.get());
            target.invulnerableTime = 0;
            player.attack(target);
            helper.assertTrue(strikes.get() == 1, "40 刻内再砍不再劈，实际 " + strikes.get());
        } finally {
            unlisten.run();
            target.discard();
            bystander.discard();
        }

        // 被拦下：换一名持剑人（间隔按人记）、换一只目标，闪电伤害在 LivingAttackEvent 里取消
        Husk blocked = husk(helper, new BlockPos(0, 1, 3));
        ServerPlayer other = fighter(helper, new Vec3(0.5D, 1D, 1.5D));
        Runnable deny = listen(LivingAttackEvent.class, event -> {
            if (event.getEntity() == blocked && event.getSource().is(DamageTypes.LIGHTNING_BOLT)) {
                event.setCanceled(true);
            }
        });
        try {
            other.setItemInHand(InteractionHand.MAIN_HAND, sword(data(Element.COMBINED, Element.COMBINED, CHAOS, DIVINE), FULL));
            other.attack(blocked);
            helper.assertTrue(blocked.invulnerableTime > 10, "闪电被拦下，近战设的受伤冷却应放回去，实际 " + blocked.invulnerableTime);
            helper.assertTrue(blocked.getRemainingFireTicks() <= 0, "闪电被拦下不该点火");
            DamageSource last = blocked.getLastDamageSource();
            helper.assertTrue(last == null || !last.is(DamageTypes.LIGHTNING_BOLT), "闪电被拦下，最后一次伤害不该是闪电：" + last);
        } finally {
            deny.run();
            blocked.discard();
        }
        helper.succeed();
    }

    /** 反噬（混沌 + 阴燃）：持剑人挨 ember_backlash，伤害源不带攻击者、不带直接实体 */
    @GameTest(template = EMPTY)
    public static void forgedSwordBacklash(GameTestHelper helper) {
        Husk target = husk(helper, new BlockPos(2, 1, 3));
        ServerPlayer player = fighter(helper, new Vec3(2.5D, 1D, 1.5D));
        List<DamageSource> hits = new ArrayList<>();
        Runnable unlisten = listen(LivingHurtEvent.class, event -> {
            if (event.getEntity() == player) {
                hits.add(event.getSource());
            }
        });
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, sword(data(Element.FIRE, Element.FIRE, CHAOS, COMMON), FULL));
            float before = player.getHealth();
            player.attack(target);
            helper.assertTrue(hits.size() == 1, "持剑人应挨一下反噬，实际 " + hits.size());
            DamageSource source = hits.get(0);
            helper.assertTrue(source.is(ECDamageTypes.EMBER_BACKLASH) && source.getEntity() == null && source.getDirectEntity() == null,
                    "反噬伤害源应是 ember_backlash、不带攻击者：" + source);
            helper.assertTrue(!source.is(DamageTypeTags.IS_FIRE) && player.getHealth() < before, "掉了血");
            helper.assertTrue(target.getRemainingFireTicks() > 0, "火主元素：目标着火");
        } finally {
            unlisten.run();
            target.discard();
        }
        helper.succeed();
    }

    // ================================================================ 剑：右键

    /** 火焰右键只点燃敌对生物：尸壳着火，村民、驯服的狼不着火，别的玩家不算；自己抗火 */
    @GameTest(template = EMPTY)
    public static void forgedSwordFireUseIgnitesEnemies(GameTestHelper helper) {
        ServerPlayer player = fighter(helper, new Vec3(2.5D, 1D, 2.5D));
        Husk enemy = husk(helper, new BlockPos(3, 1, 2));
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 2));
        villager.setNoAi(true);
        Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(2, 1, 3));
        wolf.setNoAi(true);
        wolf.tame(player);
        try {
            ItemStack stack = sword(data(Element.WATER, Element.FIRE, COMMON), FULL);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(enemy.getRemainingFireTicks() > 0, "尸壳应着火");
            helper.assertTrue(villager.getRemainingFireTicks() <= 0 && wolf.getRemainingFireTicks() <= 0, "村民、驯服的狼不着火");
            helper.assertTrue(!ForgedElementalSwordItem.ignites(ProtectionFrameworkTests.realPlayer(helper), player), "别的玩家不点");
            helper.assertTrue(amplifier(player, MobEffects.FIRE_RESISTANCE) == 0 && mru(stack) == FULL - ForgedElementalSwordItem.COST, "自己抗火 I、扣 50");
            helper.assertTrue(player.isUsingItem(), "右键同时举剑格挡");
        } finally {
            enemy.discard();
            villager.discard();
            wolf.discard();
        }
        helper.succeed();
    }

    /** 水副元素右键：生命恢复 I 50 刻、扣 50；身上已有生命恢复时不刷新、不扣；地、风照扣；没锻造的什么都不做 */
    @GameTest(template = EMPTY)
    public static void forgedSwordSecondaryUse(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        ItemStack stack = sword(data(Element.FIRE, Element.WATER, COMMON), FULL);
        helper.assertTrue(ForgedElementalSwordItem.applySecondary(stack, Element.WATER, player), "第一次应生效");
        MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
        helper.assertTrue(regen != null && regen.getAmplifier() == 0 && regen.getDuration() == ForgedElementalSwordItem.USE_REGENERATION_TICKS
                && mru(stack) == FULL - ForgedElementalSwordItem.COST, "生命恢复 I 50 刻、扣 50");
        player.removeEffect(MobEffects.REGENERATION);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 10, 0));
        helper.assertTrue(!ForgedElementalSwordItem.applySecondary(stack, Element.WATER, player), "身上已有生命恢复不该生效");
        helper.assertTrue(player.getEffect(MobEffects.REGENERATION).getDuration() == 10 && mru(stack) == FULL - ForgedElementalSwordItem.COST,
                "不刷新、不扣");
        helper.assertTrue(ForgedElementalSwordItem.applySecondary(stack, Element.EARTH, player)
                && amplifier(player, MobEffects.DAMAGE_RESISTANCE) == 0, "地：抗性提升 I");
        helper.assertTrue(ForgedElementalSwordItem.applySecondary(stack, Element.AIR, player)
                && amplifier(player, MobEffects.MOVEMENT_SPEED) == 0 && amplifier(player, MobEffects.DAMAGE_BOOST) == 0, "风：迅捷 I、力量 I");
        helper.assertTrue(mru(stack) == FULL - 3 * ForgedElementalSwordItem.COST, "一共扣 150，剩 " + mru(stack));
        ItemStack plain = MRUStaffItem.full(ECEmberForge.FORGED_ELEMENTAL_SWORD.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, plain);
        plain.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(mru(plain) == FULL, "没锻造的右键只格挡、不扣");
        helper.succeed();
    }

    /** 引用到的别的模块的物品都在（余烬 8 种、焦点 4 种），锻炉认得出 */
    @GameTest(template = EMPTY)
    public static void emberForgeAcceptsRightItems(GameTestHelper helper) {
        for (EmberKind kind : EmberKind.values()) {
            helper.assertTrue(ForgeRegistries.ITEMS.containsKey(kind.id()), "余烬 " + kind.id() + " 应已注册");
            helper.assertTrue(EmberForgeBlockEntity.accepts(0, ember(kind, 1)) && !EmberForgeBlockEntity.accepts(4, ember(kind, 1)), kind + " 只放余烬格");
            helper.assertTrue(EmberKind.byId(kind.id()) == kind && EmberKind.of(ember(kind, 3)) == kind, kind + " 按注册名、按物品都认得");
        }
        for (Element element : Element.SINGLE) {
            ItemStack focus = focus(element);
            focus.setDamageValue(2_999);
            helper.assertTrue(EmberForgeBlockEntity.accepts(4, focus) && !EmberForgeBlockEntity.accepts(0, focus), element + " 焦点只放焦点格、不看耐久");
        }
        helper.assertTrue(!EmberForgeBlockEntity.accepts(0, new ItemStack(net.minecraft.world.item.Items.BLAZE_POWDER)), "别的东西不认");
        helper.assertTrue(Arrays.asList(EmberKind.values()).size() == 8, "8 种余烬");
        helper.succeed();
    }
}
