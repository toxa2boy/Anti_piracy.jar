package net.mcreator.antipiracyjar;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = "anti_piracyjar")
public class StarterLootEvent {
    private static final String GIVEN_KEY = "AntiPiracyStarterLootGiven";

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(GIVEN_KEY)) {
            return;
        }

        persisted.putBoolean(GIVEN_KEY, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);

        RandomSource random = player.serverLevel().getRandom();

        giveInSlot(player, 0, worn(Items.STONE_SWORD, random, 45, 85));
        giveInSlot(player, 1, worn(random.nextInt(4) == 0 ? Items.IRON_PICKAXE : Items.STONE_PICKAXE, random, 30, 75));
        giveInSlot(player, 2, worn(Items.STONE_AXE, random, 35, 80));
        if (random.nextBoolean()) {
            giveInSlot(player, 3, worn(Items.STONE_SHOVEL, random, 25, 65));
        }
        giveInSlot(player, 4, new ItemStack(Items.TORCH, between(random, 6, 12)));
        giveInSlot(player, 8, new ItemStack(random.nextBoolean() ? Items.BREAD : Items.COOKED_PORKCHOP, between(random, 3, 5)));

        giveInSlot(player, 9, new ItemStack(Items.COAL, between(random, 3, 7)));
        giveInSlot(player, 10, new ItemStack(Items.STICK, between(random, 4, 9)));
        giveInSlot(player, 11, new ItemStack(Items.COBBLESTONE, between(random, 12, 22)));
        giveInSlot(player, 12, new ItemStack(random.nextBoolean() ? Items.OAK_LOG : Items.BIRCH_LOG, between(random, 3, 6)));
        if (random.nextInt(3) == 0) {
            giveInSlot(player, 13, new ItemStack(Items.RAW_IRON, between(random, 1, 3)));
        }

        switch (random.nextInt(4)) {
            case 0 -> {
                giveArmor(player, EquipmentSlot.HEAD, worn(Items.IRON_HELMET, random, 40, 75));
                giveArmor(player, EquipmentSlot.FEET, worn(Items.IRON_BOOTS, random, 30, 70));
            }
            case 1 -> {
                giveArmor(player, EquipmentSlot.CHEST, worn(Items.IRON_CHESTPLATE, random, 35, 65));
                giveArmor(player, EquipmentSlot.FEET, worn(Items.LEATHER_BOOTS, random, 30, 75));
            }
            case 2 -> {
                giveArmor(player, EquipmentSlot.LEGS, worn(Items.IRON_LEGGINGS, random, 40, 70));
                giveArmor(player, EquipmentSlot.HEAD, worn(Items.LEATHER_HELMET, random, 25, 65));
            }
            default -> {
                giveArmor(player, EquipmentSlot.FEET, worn(Items.IRON_BOOTS, random, 45, 80));
                giveArmor(player, EquipmentSlot.CHEST, worn(Items.LEATHER_CHESTPLATE, random, 35, 70));
            }
        }

        player.giveExperiencePoints(between(random, 16, 32));
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag previous = event.getOriginal().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (previous.getBoolean(GIVEN_KEY)) {
            CompoundTag persisted = event.getEntity().getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            persisted.putBoolean(GIVEN_KEY, true);
            event.getEntity().getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        }
    }

    private static ItemStack worn(Item item, RandomSource random, int minRemainingPercent, int maxRemainingPercent) {
        ItemStack stack = new ItemStack(item);
        int remainingPercent = between(random, minRemainingPercent, maxRemainingPercent);
        int remaining = Math.max(1, stack.getMaxDamage() * remainingPercent / 100);
        stack.set(DataComponents.DAMAGE, stack.getMaxDamage() - remaining);
        return stack;
    }

    private static int between(RandomSource random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private static void giveInSlot(ServerPlayer player, int slot, ItemStack stack) {
        if (player.getInventory().getItem(slot).isEmpty()) {
            player.getInventory().setItem(slot, stack);
        } else {
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
    }

    private static void giveArmor(ServerPlayer player, EquipmentSlot slot, ItemStack stack) {
        if (player.getItemBySlot(slot).isEmpty()) {
            player.setItemSlot(slot, stack);
        } else {
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
    }
}
