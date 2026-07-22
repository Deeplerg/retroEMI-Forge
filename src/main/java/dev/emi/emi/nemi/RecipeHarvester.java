package dev.emi.emi.nemi;

import codechicken.nei.recipe.TemplateRecipeHandler;
import dev.emi.emi.EmiPort;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecipeHarvester {
    // These are handlers that EMI natively covers better (e.g., standard crafting/smelting)
    private static final List<String> BLACKLISTED_CLASSES = shim.java.List.of(
        "codechicken.nei.recipe.ShapedRecipeHandler",
        "codechicken.nei.recipe.ShapelessRecipeHandler",
        "codechicken.nei.recipe.FurnaceRecipeHandler",
        "codechicken.nei.recipe.BrewingRecipeHandler"
    );

    private static final CategoryIconGuesser ICON_GUESSER = new CategoryIconGuesser();
    private static final EmiStack DEFAULT_ICON = EmiStack.of(Blocks.crafting_table);

    private final EmiRegistry registry;
    private final TemplateRecipeHandler baseHandler;

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

    private Set<String> extractRecipeIds() {
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

    private NemiRecipeCategory createCategory(TemplateRecipeHandler handler, String recipeId) {
        ResourceLocation categoryId = EmiPort.id(NemiPlugin.DOMAIN, recipeId);
        EmiStack icon = determineCategoryIcon(handler);

        NemiRecipeCategory category = new NemiRecipeCategory(categoryId, icon, handler.getRecipeName());
        registry.addCategory(category);
        return category;
    }

    private void registerAllRecipes(NemiRecipeCategory category, TemplateRecipeHandler handler, int numRecipes, String recipeId) {
        for (int i = 0; i < numRecipes; i++) {
            ResourceLocation emiRecipeId = EmiPort.id(NemiPlugin.DOMAIN, recipeId + "/" + i);
            NemiRecipe recipe = new NemiRecipe(category, handler, i, emiRecipeId);
            registry.addRecipe(recipe);
        }
    }

    private EmiStack determineCategoryIcon(TemplateRecipeHandler handler) {
        // TODO I couldn't find a better way to handle this.
        // This is dirty AF. But it works, somewhat.
        // This is probably better than just a default icon everywhere.
        // Modpack devs can override this in category_properties.json.
        EmiStack guessedIcon = ICON_GUESSER.guessIcon(handler.getRecipeName());

        if (!guessedIcon.isEmpty()) {
            return guessedIcon;
        }

        return DEFAULT_ICON;
    }
}
