package com.flameforge.essentialcraft5.gametest;

import com.flameforge.essentialcraft5.EssentialCraft5;
import com.flameforge.essentialcraft5.item.InquisitoriumArmorEvents;
import com.flameforge.essentialcraft5.item.WindGearEvents;
import com.flameforge.essentialcraft5.network.GearFx;
import com.flameforge.essentialcraft5.network.S2CGearFxPacket;
import com.flameforge.essentialcraft5.registry.ECInquisitorium;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 装备动作的特效事件（特效第 5 批）：服务端只发事件包、不再撒原版粒子；同一刻连着触发只发一次。
 * 喷射、闪避的断言在 ComputerArmorTests / VoidArmorTests 原来那条里，封禁场穹顶的在 CurioSealFieldTests。
 */
@GameTestHolder(EssentialCraft5.MODID)
@PrefixGameTestTemplate(false)
public final class GearMotionFxTests {

    private static final String EMPTY = "empty";
    private static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};

    private GearMotionFxTests() {
    }

    /** 空中跳：发一个 AIR_JUMP（实体是跳的人），同一刻再调不再发 */
    @GameTest(template = EMPTY)
    public static void gearFxAirJumpEvent(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        List<S2CGearFxPacket> sent = new ArrayList<>();
        S2CGearFxPacket.setTestSink(sent::add);
        try {
            WindGearEvents.airJumpEffects(player);
            WindGearEvents.airJumpEffects(player);
        } finally {
            S2CGearFxPacket.setTestSink(null);
        }
        long jumps = sent.stream().filter(p -> p.kind() == GearFx.AIR_JUMP && p.entity() == player.getId()).count();
        helper.assertTrue(jumps == 1, "同一刻两次空中跳只发一个事件，实际 " + jumps);
        helper.succeed();
    }

    /** 定罪：新记上一个目标发一个 CONVICT（实体是目标、param 是定罪刻数），已经在表里的刷新时不发 */
    @GameTest(template = EMPTY)
    public static void gearFxConvictEvent(GameTestHelper helper) {
        ServerPlayer player = ProtectionFrameworkTests.realPlayer(helper);
        for (ArmorItem.Type type : TYPES) {
            player.setItemSlot(type.getSlot(), new ItemStack(ECInquisitorium.all().get(type.ordinal()).get()));
        }
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        List<S2CGearFxPacket> sent = new ArrayList<>();
        S2CGearFxPacket.setTestSink(sent::add);
        boolean first;
        boolean again;
        try {
            first = InquisitoriumArmorEvents.convict(player, zombie);
            again = InquisitoriumArmorEvents.convict(player, zombie);
        } finally {
            S2CGearFxPacket.setTestSink(null);
            zombie.discard();
        }
        helper.assertTrue(first && !again, "测试前提：第一次新记上、第二次只是刷新");
        List<S2CGearFxPacket> convicts = sent.stream().filter(p -> p.kind() == GearFx.CONVICT && p.entity() == zombie.getId()).toList();
        helper.assertTrue(convicts.size() == 1, "只在新记上时发一个定罪事件，实际 " + convicts.size());
        helper.assertTrue(convicts.get(0).param() == ECInquisitorium.Config.law().convictionTicks(), "param 是定罪刻数");
        helper.succeed();
    }
}
