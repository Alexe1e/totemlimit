package com.onetotem;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side mod: every player may carry at most ONE Totem of Undying in the
 * inventory (hotbar, main inventory, armor and offhand slots). Extra totems are
 * dropped in front of the player.
 */
public class TotemLimitMod implements ModInitializer {
	public static final String MOD_ID = "totemlimit";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Max totems per player. */
	private static final int MAX_TOTEMS = 1;
	/** Check every N ticks (10 ticks = 0.5 seconds). */
	private static final int CHECK_INTERVAL = 10;

	@Override
	public void onInitialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_INTERVAL != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				enforceLimit(player);
			}
		});
		LOGGER.info("Totem Limit loaded: max {} totem(s) per player", MAX_TOTEMS);
	}

	private static void enforceLimit(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		int found = 0;
		boolean droppedAny = false;

		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || !stack.is(Items.TOTEM_OF_UNDYING)) {
				continue;
			}

			int count = stack.getCount();
			int allowed = Math.max(0, MAX_TOTEMS - found);
			int keep = Math.min(count, allowed);
			int extra = count - keep;
			found += keep;

			if (extra > 0) {
				ItemStack toDrop = stack.copyWithCount(extra);
				if (keep == 0) {
					inventory.setItem(slot, ItemStack.EMPTY);
				} else {
					stack.setCount(keep);
				}
				ItemEntity entity = player.drop(toDrop, false);
				if (entity != null) {
					entity.setPickUpDelay(100);
				}
				droppedAny = true;
			}
		}

		if (droppedAny) {
			player.sendSystemMessage(Component.literal("Du darfst nur " + MAX_TOTEMS + " Totem tragen."));
		}
	}
}
