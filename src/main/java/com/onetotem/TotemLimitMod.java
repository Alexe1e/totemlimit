package com.onetotem;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side mod: every player may carry at most ONE Totem of Undying in total
 * (inventory, armor, offhand, and inside shulker boxes / bundles in the inventory).
 * Extra totems are deleted.
 */
public class TotemLimitMod implements ModInitializer {
	public static final String MOD_ID = "totemlimit";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final int MAX_TOTEMS = 1;
	private static final int CHECK_INTERVAL = 10; // ticks (10 = 0.5 s)

	/** Counts how many totems may still be kept and how many were deleted. */
	private static final class Budget {
		int remaining = MAX_TOTEMS;
		int deleted = 0;

		/** Returns how many of {@code count} totems may stay; the rest is counted as deleted. */
		int take(int count) {
			int keep = Math.min(count, remaining);
			remaining -= keep;
			deleted += count - keep;
			return keep;
		}
	}

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
		LOGGER.info("Totem Limit loaded: max {} totem(s) per player, extras are deleted", MAX_TOTEMS);
	}

	private static void enforceLimit(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		Budget budget = new Budget();

		// Pass 1: loose totems in the inventory (hotbar, main, armor, offhand)
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty() || !stack.is(Items.TOTEM_OF_UNDYING)) {
				continue;
			}
			int keep = budget.take(stack.getCount());
			if (keep == 0) {
				inventory.setItem(slot, ItemStack.EMPTY);
			} else if (keep < stack.getCount()) {
				stack.setCount(keep);
			}
		}

		// Pass 2: totems inside shulker boxes / other containers and bundles
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			cleanContainerContents(stack, budget);
			cleanBundleContents(stack, budget);
		}

		if (budget.deleted > 0) {
			player.sendSystemMessage(Component.literal(
					"Du darfst nur " + MAX_TOTEMS + " Totem besitzen. Überzählige wurden gelöscht."));
		}
	}

	private static void cleanContainerContents(ItemStack stack, Budget budget) {
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents == null) {
			return;
		}
		NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
		contents.copyInto(items);

		boolean changed = false;
		for (int i = 0; i < items.size(); i++) {
			ItemStack inner = items.get(i);
			if (inner.isEmpty() || !inner.is(Items.TOTEM_OF_UNDYING)) {
				continue;
			}
			int keep = budget.take(inner.getCount());
			if (keep == 0) {
				items.set(i, ItemStack.EMPTY);
				changed = true;
			} else if (keep < inner.getCount()) {
				inner.setCount(keep);
				changed = true;
			}
		}
		if (changed) {
			stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
		}
	}

	private static void cleanBundleContents(ItemStack stack, Budget budget) {
		BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
		if (contents == null) {
			return;
		}
		List<ItemStackTemplate> items = new ArrayList<>();
		boolean changed = false;
		for (ItemStackTemplate template : contents.items()) {
			ItemStack inner = template.create();
			if (!inner.isEmpty() && inner.is(Items.TOTEM_OF_UNDYING)) {
				int keep = budget.take(inner.getCount());
				if (keep == 0) {
					changed = true;
					continue;
				}
				if (keep < inner.getCount()) {
					inner.setCount(keep);
					items.add(ItemStackTemplate.fromNonEmptyStack(inner));
					changed = true;
					continue;
				}
			}
			items.add(template);
		}
		if (changed) {
			stack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(items));
		}
	}
}
