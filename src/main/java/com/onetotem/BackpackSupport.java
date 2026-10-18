package com.onetotem;

import java.util.Set;
import java.util.UUID;

import com.rouesvm.servback.technical.manager.BackpackManager;
import com.rouesvm.servback.technical.manager.BackpackUUID;
import com.rouesvm.servback.technical.ui.inventory.BackpackInventory;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Support for the Server Backpacks mod. This class is only loaded when that mod is installed.
 */
final class BackpackSupport {
	private BackpackSupport() {
	}

	/** If the stack is a backpack, delete extra totems inside it (and in shulkers/bundles inside it). */
	static void clean(ItemStack stack, TotemLimitMod.Budget budget, Set<UUID> seen) {
		UUID id = BackpackUUID.getStackUUID(stack);
		if (id == null || !seen.add(id)) {
			return;
		}
		BackpackInventory inventory = BackpackManager.getInventory(id);
		if (inventory == null) {
			return;
		}

		int deletedBefore = budget.deleted;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack inner = inventory.getItem(i);
			if (inner.isEmpty()) {
				continue;
			}
			if (inner.is(Items.TOTEM_OF_UNDYING)) {
				int keep = budget.take(inner.getCount());
				if (keep == 0) {
					inventory.setItem(i, ItemStack.EMPTY);
				} else if (keep < inner.getCount()) {
					inner.setCount(keep);
					inventory.setItem(i, inner);
				}
			} else {
				int before = budget.deleted;
				TotemLimitMod.cleanNested(inner, budget);
				if (budget.deleted != before) {
					inventory.setItem(i, inner);
				}
			}
		}

		if (budget.deleted != deletedBefore) {
			BackpackManager.getInstance(id).ifPresent(instance -> BackpackManager.saveData(instance));
		}
	}
}
