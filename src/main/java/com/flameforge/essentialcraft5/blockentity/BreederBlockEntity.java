package com.flameforge.essentialcraft5.blockentity;

import com.flameforge.essentialcraft5.config.ECConfig;
import com.flameforge.essentialcraft5.registry.ECFarmDevices;
import com.flameforge.essentialcraft5.util.ECEntityRules;
import com.flameforge.essentialcraft5.util.ECItemEntities;
import com.flameforge.essentialcraft5.util.ECProtection;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.animal.horse.ZombieHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 魔力繁殖器（原作 {@code BlockRedstoneDeviceNotSided.breed} L102~117，触发 L196~206）：红石从无到有那一下，
 * 用地上的食物掉落物喂附近的成年动物，喂一只扣一个食物，被喂的进入求偶状态（{@code Animal.setInLove(null)}：不用假玩家，
 * 繁殖照常掉经验，只是不给谁记繁殖统计和进度）。
 *
 * <h2>和原作不同（batch4 §3.1、§7，policy §4.3）</h2>
 * <ul>
 *   <li>喂食距离对称 ±{@value #FEED_REACH} 格：原作 {@code AABB(食物 ±0.5).expand(3, 3, 3)}，1.12 的 {@code expand} 只往正方向扩，
 *       食物西、北、下方的动物永远喂不到。</li>
 *   <li>同一种动物（{@link EntityType}）在范围里已经有 {@code max_same_type}（默认 24）只（成年 + 幼崽都数）就不喂这一种；每次最多喂 16 只。</li>
 *   <li>三道关（policy §4.3 ③）：不喂敌对生物（疣猪兽是 Animal 也是 Enemy）；起了名字、拴着绳的不喂，驯服的只喂主人就是机器主人的
 *       （{@code ECEntityRules.belongsToOther}，D1 取 B）；动物所在格、食物所在格都要主人能动。</li>
 *   <li>要驯服才能繁殖的（狼、猫这类 {@link TamableAnimal}，马、驴、羊驼这类 {@link AbstractHorse}）只喂驯服了的：原版手喂也只让驯服的发情，
 *       没驯服的喂了也生不出来、白吃食物（原作会把没驯服的狼、豹猫喂到发情）。马类只认原版能让它们发情的食物（马、驴：金胡萝卜、金苹果、附魔金苹果；
 *       羊驼：干草块；骆驼：仙人掌），不会拿小麦、苹果让马发情——原作的繁殖器根本喂不了马和羊驼（1.12 {@code isBreedingItem} 恒为 false），这是新增的。</li>
 *   <li>骆驼不用驯服（4a 审查 FARM-3）：原版 {@code Camel.isTamed()} 恒为真，野生骆驼手喂仙人掌就发情（{@code Camel.handleEating}），机器照原版喂；
 *       骆驼从不记主人，{@code ECEntityRules.isTamed} 对它只看主人 UUID，所以野生的、繁殖出来的都不算别人的。起了名字的、拴着绳的、
 *       装了鞍的骆驼由 {@code ECEntityRules} 的「谁的机器都不碰」挡住。</li>
 *   <li>原版生不出小的马类不喂（4a 审查 FARM-4）：骡（没覆盖 {@code canMate}，{@code AbstractHorse.canMate} 恒为 false）、骷髅马、僵尸马。
 *       原版手喂骡也会发情，但人会停手；机器接着红石时钟会每 30 秒（求偶 600 刻一过）再喂一个金胡萝卜，永远生不出来。模组的马类不受影响。</li>
 *   <li>喂完确认真的进了求偶状态才扣食物、才计数（4a 审查 FARM-2）：别的模组拦下 {@code setInLove}、或者 Mohist 系混合端上的插件取消求偶事件时，
 *       动物没发情，食物不扣（原作 setInLove 后无条件扣一个；接着红石时钟会把地上的食物一直吃光）。</li>
 *   <li>桶装的食物（美西螈吃的热带鱼桶）和带容器的（喂完要还容器的）不喂：直接扣一个会把桶一起吞掉。指定了拾取人、又不是主人的食物不用。</li>
 *   <li>红石上升沿 + 最小间隔 20 刻。</li>
 * </ul>
 * P2：食物查 25³、动物查 32³（25³ 外加喂食距离），每次脉冲各一次（原作每个食物各查一次动物）；地上没有能用的掉落物时不查动物。
 * 候选表<b>先配对、后截断</b>（4a 审查 FARM-1）：掉落物只留「是某只能喂的动物的繁殖食物、又在它够得着的地方」的，按离机器远近截断以后，
 * 动物只留够得着表里食物的，再截断。否则一地羊毛、鸡蛋，或者离机器更近、却没有食物的一群动物，会把真正的食物和真正能喂的动物挤出表外，每次脉冲都喂不上。
 */
public class BreederBlockEntity extends FarmDeviceBlockEntity {

    /** 喂食距离：动物的碰撞箱碰到以食物为中心、半边长这么多的方框就算够得着（原作 ±0.5 再扩 3） */
    public static final double FEED_REACH = 3.5D;

    /** 服务端配置 machines.breeder（新加的限制，原作没有） */
    public static final class Config {
        public static ForgeConfigSpec.IntValue maxSameType;
        public static ForgeConfigSpec.IntValue maxPerPulse;
        public static ForgeConfigSpec.IntValue pulseInterval;

        public static void define(ForgeConfigSpec.Builder builder) {
            builder.push("breeder");
            maxSameType = builder.comment("A kind of animal is not fed when this many of it (adults and babies) are already in range.",
                            "范围内同一种动物（成年和幼崽都数）已经有这么多只时，这一种不再喂")
                    .defineInRange("max_same_type", 24, 1, 256);
            maxPerPulse = builder.comment("At most this many animals are fed per redstone pulse.",
                            "每次红石脉冲最多喂几只")
                    .defineInRange("max_per_pulse", 16, 1, 64);
            pulseInterval = builder.comment("Minimum ticks between two redstone pulses (a pulse inside the interval waits for it to end).",
                            "Not below 10: every pulse queries all animals in a 32x32x32 area.",
                            "两次红石脉冲之间至少隔几刻（间隔内来的脉冲等间隔结束再做）；每次脉冲都要查 32×32×32 的动物，所以不低于 10")
                    .defineInRange("pulse_interval", 20, 10, 1200);
            builder.pop();
        }
    }

    /** 要喂的动物（按离机器的距离排好） */
    private final List<Animal> candidates = new ArrayList<>();
    /** 这次脉冲能用的食物（按离机器的距离排好） */
    private final List<ItemEntity> foods = new ArrayList<>();
    /** 这次脉冲里所在格被拒绝的食物（别的动物也不再拿它问） */
    private final Set<ItemEntity> deniedFoods = Collections.newSetFromMap(new IdentityHashMap<>());

    public BreederBlockEntity(BlockPos pos, BlockState state) {
        super(ECFarmDevices.BREEDER_BE.get(), pos, state, () -> ECConfig.get(Config.pulseInterval));
    }

    @Override
    protected int maxPerPulse() {
        return ECConfig.get(Config.maxPerPulse);
    }

    // ------------------------------------------------------------------ 规则（纯内存判断，不探测）

    /** 这个掉落物能不能当食物用：活着、不空、不是永远捡不起来的、拾取人是主人或没指定、不是桶也不带容器 */
    private static boolean usableFood(ItemEntity item, @Nullable UUID owner) {
        if (!item.isAlive() || !ECItemEntities.pickableBy(item, owner) || ECItemEntities.neverPickUp(item)) {
            return false;
        }
        ItemStack stack = item.getItem();
        return !stack.isEmpty() && !(stack.getItem() instanceof BucketItem) && !stack.hasCraftingRemainingItem();
    }

    /**
     * 要驯服了才能繁殖的动物（原版手喂也只让驯服的发情）。骆驼除外：原版 {@code Camel.isTamed()} 恒为真，
     * {@code Camel.handleEating} 让野生骆驼吃仙人掌就发情（{@code ECEntityRules.isTamed} 对骆驼只看主人 UUID，野生的恒为 false，不能拿它卡）。
     */
    public static boolean needsTaming(Animal animal) {
        return animal instanceof TamableAnimal || (animal instanceof AbstractHorse && !(animal instanceof Camel));
    }

    /**
     * 原版生不出小的马类：骡（{@code AbstractHorse.canMate} 恒为 false，骡没覆盖）、骷髅马、僵尸马。喂了只会发情，
     * 求偶 600 刻一过又能喂，接着红石时钟每 30 秒白吃一个金胡萝卜。模组的马类不在此列。
     */
    public static boolean cannotBreed(Animal animal) {
        return animal instanceof Mule || animal instanceof SkeletonHorse || animal instanceof ZombieHorse;
    }

    /**
     * 这只动物现在能不能被这台机器喂（不看食物、不探测）：成年、没在求偶（原版手喂的条件 {@code getAge() == 0 && canFallInLove()}）、
     * 不是敌对生物、不是原版生不出小的（{@link #cannotBreed}）、不是别人的（名字、拴绳、村民、装了鞍的骆驼……驯服的只认主人就是机器主人的）、
     * 要驯服才能繁殖的已经驯服。
     */
    public static boolean canFeed(Animal animal, @Nullable UUID owner) {
        return animal.isAlive() && animal.getAge() == 0 && animal.canFallInLove() && !(animal instanceof Enemy) && !cannotBreed(animal)
                && !ECEntityRules.belongsToOther(animal, owner)
                && (!needsTaming(animal) || ECEntityRules.isTamed(animal));
    }

    /**
     * 这份食物能不能让这只动物发情：一般动物看 {@code isFood}；马类照原版只认让它们发情的那几样
     * （{@code AbstractHorse.handleEating} L467~483、{@code Llama.handleEating}、{@code Camel.handleEating}）。
     * 骡、骷髅马、僵尸马在这里也算「吃金胡萝卜」，由 {@link #canFeed} 挡掉。
     */
    public static boolean isBreedingFood(Animal animal, ItemStack stack) {
        if (animal instanceof Llama) {
            return stack.is(Items.HAY_BLOCK);
        }
        if (animal instanceof Camel) {
            return animal.isFood(stack);
        }
        if (animal instanceof AbstractHorse) {
            return stack.is(Items.GOLDEN_CARROT) || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
        }
        return animal.isFood(stack);
    }

    /** 动物够不够得着这份食物：动物的碰撞箱碰到以食物为中心 ±{@value #FEED_REACH} 的方框（对称，修原作只朝正方向） */
    public static boolean inReach(Animal animal, ItemEntity food) {
        AABB reach = new AABB(food.getX(), food.getY(), food.getZ(), food.getX(), food.getY(), food.getZ()).inflate(FEED_REACH);
        return animal.getBoundingBox().intersects(reach);
    }

    // ------------------------------------------------------------------ 脉冲

    /**
     * 先配对、后截断（见类注释 P2）：① 能用的掉落物，没有就不查动物；② 能喂的动物按种类分组（不截断）；
     * ③ 掉落物只留是某一组的繁殖食物、又有这组的动物够得着的，按离机器远近截断；④ 动物只留够得着截断后的表里食物的，再截断。
     */
    @Override
    protected void collect(ServerLevel level) {
        UUID owner = machineOwner();
        foods.addAll(level.getEntitiesOfClass(ItemEntity.class, scanArea(0D), item -> usableFood(item, owner)));
        if (foods.isEmpty()) {
            return;
        }
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, scanArea(FEED_REACH));
        // 同一种动物的只数（成年 + 幼崽都数，裁定 9：卡服的是实体数）
        Object2IntOpenHashMap<EntityType<?>> perType = new Object2IntOpenHashMap<>();
        for (Animal animal : animals) {
            if (animal.isAlive()) {
                perType.addTo(animal.getType(), 1);
            }
        }
        int maxSameType = ECConfig.get(Config.maxSameType);
        Map<EntityType<?>, List<Animal>> feedable = new LinkedHashMap<>();
        for (Animal animal : animals) {
            if (perType.getInt(animal.getType()) < maxSameType && canFeed(animal, owner)) {
                feedable.computeIfAbsent(animal.getType(), type -> new ArrayList<>()).add(animal);
            }
        }
        // 没有能喂的动物时 feedsAnyone 全为 false，食物表清空
        foods.removeIf(food -> !feedsAnyone(food, feedable));
        if (foods.isEmpty()) {
            return;
        }
        nearestFirst(foods);
        for (List<Animal> group : feedable.values()) {
            for (Animal animal : group) {
                if (hasReachableFood(animal)) {
                    candidates.add(animal);
                }
            }
        }
        nearestFirst(candidates);
    }

    /**
     * 这份掉落物有没有用：是某一种能喂的动物的繁殖食物，这种里又有一只够得着它。同一种动物吃的东西一样（原版的 {@code isFood} 只看物品），
     * 每种拿第一只当代表问 {@link #isBreedingFood}，只对是食物的那几种逐只看够不够得着（不是食物的掉落物每份只问几次，不跟每只动物配对）。
     * 这里只是预筛：{@link #process} 喂之前还会逐只核一遍。
     */
    private static boolean feedsAnyone(ItemEntity food, Map<EntityType<?>, List<Animal>> feedable) {
        ItemStack stack = food.getItem();
        for (List<Animal> group : feedable.values()) {
            if (!isBreedingFood(group.get(0), stack)) {
                continue;
            }
            for (Animal animal : group) {
                if (inReach(animal, food)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected boolean process(ServerLevel level) {
        UUID owner = machineOwner();
        // 食物的归属每轮只核一次（读拾取人要反射）：这几刻里可能被捡走、被别的机器用掉
        foods.removeIf(food -> deniedFoods.contains(food) || !usableFood(food, owner));
        boolean unresolved = false;
        for (Iterator<Animal> it = candidates.iterator(); it.hasNext() && !full(); ) {
            Animal animal = it.next();
            // 这几刻里可能被人喂过、起了名字、被拴上
            if (!canFeed(animal, owner) || !hasReachableFood(animal)) {
                it.remove();
                continue;
            }
            ECProtection.Verdict animalCell = check(animal.blockPosition());
            if (animalCell == ECProtection.Verdict.UNKNOWN) {
                unresolved = true;
                continue;
            }
            if (animalCell == ECProtection.Verdict.DENY) {
                it.remove();
                continue;
            }
            ItemEntity chosen = null;
            boolean waiting = false;
            for (ItemEntity food : foods) {
                if (!usable(food, animal)) {
                    continue;
                }
                ECProtection.Verdict foodCell = check(food.blockPosition());
                if (foodCell == ECProtection.Verdict.ALLOW) {
                    chosen = food;
                    break;
                }
                if (foodCell == ECProtection.Verdict.DENY) {
                    deniedFoods.add(food);
                } else {
                    waiting = true;
                    break;
                }
            }
            if (chosen == null) {
                if (waiting) {
                    unresolved = true;
                } else {
                    it.remove();
                }
                continue;
            }
            it.remove();
            animal.setInLove(null);
            // 别的模组拦下 setInLove、Mohist 系混合端的插件取消求偶事件时它没发情：不扣食物、不计数（已经从候选表拿掉，这次脉冲不再选它）
            if (!animal.isInLove()) {
                continue;
            }
            consumeOne(chosen);
            markDone();
        }
        return unresolved;
    }

    /** 表里有没有这只动物够得着、能吃的食物（不探测） */
    private boolean hasReachableFood(Animal animal) {
        for (ItemEntity food : foods) {
            if (usable(food, animal)) {
                return true;
            }
        }
        return false;
    }

    /** 这份食物（这一轮开头核过归属）这只动物现在能不能吃：还在、没被拒、是它发情用的食物、够得着 */
    private boolean usable(ItemEntity food, Animal animal) {
        return food.isAlive() && !food.getItem().isEmpty() && !deniedFoods.contains(food)
                && isBreedingFood(animal, food.getItem()) && inReach(animal, food);
    }

    /** 掉落物扣一个：换一个新的物品堆（原地改数量不会同步给客户端），扣完就移除 */
    private static void consumeOne(ItemEntity food) {
        ItemStack rest = food.getItem().copy();
        rest.shrink(1);
        if (rest.isEmpty()) {
            food.discard();
        } else {
            food.setItem(rest);
        }
    }

    @Override
    protected void clearCandidates() {
        candidates.clear();
        foods.clear();
        deniedFoods.clear();
    }
}
