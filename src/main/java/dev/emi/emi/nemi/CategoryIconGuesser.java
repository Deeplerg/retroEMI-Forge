package dev.emi.emi.nemi;

import com.rewindmc.retroemi.RetroEMI;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Matches localized NEI category name against cached localized item names
 */
public class CategoryIconGuesser {

	private static final List<String> STRIP_SUFFIXES = shim.java.List.of(
		" recipes", " crafting", " smelting", " brewing"
	);

	private Map<String, EmiStack> nameCache = null;

	public EmiStack guessIcon(String recipeName) {
		if (recipeName == null || recipeName.isEmpty()) {
			return EmiStack.EMPTY;
		}

		buildCacheIfNeeded();

		String cleanName = normalizeAndStrip(recipeName);

		if (nameCache.containsKey(cleanName)) {
			return nameCache.get(cleanName);
		}

		for (Map.Entry<String, EmiStack> entry : nameCache.entrySet()) {
			String cachedName = entry.getKey();
			if (cachedName.contains(cleanName) || cleanName.contains(cachedName)) {
				return entry.getValue();
			}
		}

		return EmiStack.EMPTY;
	}

	private String normalizeAndStrip(String name) {
		String normalized = EnumChatFormatting.getTextWithoutFormattingCodes(name).toLowerCase().trim();
		for (String suffix : STRIP_SUFFIXES) {
			if (normalized.endsWith(suffix)) {
				normalized = normalized.substring(0, normalized.length() - suffix.length()).trim();
			}
		}
		return normalized;
	}

	private void buildCacheIfNeeded() {
		if (nameCache != null) {
			return;
		}
		nameCache = new HashMap<>();

		for (Item item : RetroEMI.getAllItems()) {
			if (item == null) {
				continue;
			}

			List<ItemStack> subItems = new ArrayList<>();
			try {
				item.getSubItems(item, CreativeTabs.tabAllSearch, subItems);
			} catch (Exception ignored) {
			}

			if (subItems.isEmpty()) {
				subItems.add(new ItemStack(item));
			}

			for (ItemStack stack : subItems) {
				if (stack == null || stack.getItem() == null) {
					continue;
				}
				try {
					String displayName = EnumChatFormatting.getTextWithoutFormattingCodes(stack.getDisplayName()).toLowerCase().trim();
					if (!nameCache.containsKey(displayName)) {
						nameCache.put(displayName, EmiStack.of(stack));
					}
				} catch (Exception ignored) {
				}
			}
		}
	}
}
