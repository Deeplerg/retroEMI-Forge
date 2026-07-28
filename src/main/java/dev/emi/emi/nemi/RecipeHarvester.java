package dev.emi.emi.nemi;

import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.TemplateRecipeHandler;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecipeHarvester {
    // These are handlers that EMI natively covers better (e.g., standard crafting/smelting)
    private static final List<String> BLACKLISTED_CLASSES = shim.java.List.of(
        "codechicken.nei.recipe.ShapedRecipeHandler",
        "codechicken.nei.recipe.ShapelessRecipeHandler",
        "codechicken.nei.recipe.FurnaceRecipeHandler",
        "codechicken.nei.recipe.BrewingRecipeHandler"
    );

    private static final EmiStack DEFAULT_ICON = EmiStack.of(Blocks.crafting_table);

    private final EmiRegistry registry;
    private final TemplateRecipeHandler baseHandler;
    private final Map<String, NemiRecipeCategory> categories = new HashMap<>();

    public RecipeHarvester(EmiRegistry registry, TemplateRecipeHandler baseHandler) {
        this.registry = registry;
        this.baseHandler = baseHandler;
    }

    public void harvest() {
        if (BLACKLISTED_CLASSES.contains(baseHandler.getClass().getName())) {
            return;
        }

        Set<String> recipeIds = extractRecipeIds();
        if (recipeIds.isEmpty()) {
            return;
        }

        for (String recipeId : recipeIds) {
            harvestRecipesForId(recipeId);
        }
    }

    public Set<String> extractRecipeIds() {
        Set<String> ids = new HashSet<>();

        String overlayId = baseHandler.getOverlayIdentifier();
        if (overlayId != null) {
            ids.add(overlayId);
        }

        try {
            TemplateRecipeHandler tempHandler = baseHandler.newInstance();
            tempHandler.loadTransferRects();

            if (tempHandler.transferRects != null) {
                // TODO is there a cleaner way to do this than reflection?
                Field outputIdField = TemplateRecipeHandler.RecipeTransferRect.class.getDeclaredField("outputId");
                outputIdField.setAccessible(true);

                for (TemplateRecipeHandler.RecipeTransferRect rect : tempHandler.transferRects) {
                    if (rect != null) {
                        String id = (String) outputIdField.get(rect);
                        if (id != null) {
                            ids.add(id);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return ids;
    }

    private void harvestRecipesForId(String recipeId) {
        TemplateRecipeHandler handler = baseHandler.newInstance();
        try {
            handler.loadCraftingRecipes(recipeId);
        } catch (Exception e) {
            return; // Skip if handler fails to load for this ID
        }

        int numRecipes = handler.numRecipes();
        if (numRecipes <= 0) {
            return;
        }

        NemiRecipeCategory category = createCategory(handler, recipeId);
        registerAllRecipes(category, handler, numRecipes, recipeId);
    }

    public NemiRecipeCategory createCategory(TemplateRecipeHandler handler, String recipeId) {
        if (categories.containsKey(recipeId)) {
            return categories.get(recipeId);
        }
        ResourceLocation categoryId = EmiPort.id(NemiPlugin.DOMAIN, recipeId);
        EmiStack icon = determineCategoryIcon(handler);

        NemiRecipeCategory category = new NemiRecipeCategory(categoryId, icon, handler.getRecipeName());
        registry.addCategory(category);
        categories.put(recipeId, category);
        return category;
    }

    public Map<String, NemiRecipeCategory> getCategories() {
        return categories;
    }

    private void registerAllRecipes(NemiRecipeCategory category, TemplateRecipeHandler handler, int numRecipes, String recipeId) {
        for (int i = 0; i < numRecipes; i++) {
            ResourceLocation emiRecipeId = EmiPort.id(NemiPlugin.DOMAIN, recipeId + "/" + i);
            NemiRecipe recipe = new NemiRecipe(category, handler, i, emiRecipeId);
            registry.addRecipe(recipe);
        }
    }

    private EmiStack determineCategoryIcon(TemplateRecipeHandler handler) {
		HandlerInfo info = GuiRecipeTab.getHandlerInfo(handler);
		if (info != null && info.getItemStack() != null) {
			// TODO Couldn't make Image to Stack
			EmiStack icon = EmiStack.of(info.getItemStack());
			if (!icon.isEmpty()) {
				return icon;
			}
		}
        return DEFAULT_ICON;
    }
}
