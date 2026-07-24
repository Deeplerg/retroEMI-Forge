package dev.emi.emi.nemi;

import java.lang.reflect.Method;

import codechicken.nei.LayoutManager;
import codechicken.nei.LayoutStyleMinecraft;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.RecipeCatalysts;
import codechicken.nei.recipe.TemplateRecipeHandler;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.screen.RecipeScreen;
import net.minecraft.client.Minecraft;

import static dev.emi.emi.nemi.NemiScreenHandler.*;

public class NemiPlugin implements EmiPlugin {
	public static final String DOMAIN = "nemi";

	public static boolean isNEILoaded = false;

	// NEI buttons are 18x18 pixels, with 1 pixel of spacing (19 pixels total per button step).
	// The block of buttons has a 2-pixel outer margin.
	private static final int NEI_BUTTON_SPACING = 19;
	private static final int NEI_OUTER_MARGIN = 2;

	private static final Minecraft client = Minecraft.getMinecraft();

	public static void onLoad() {
		try {
			Class<?> apiClass = Class.forName("codechicken.nei.api.API");
			Method registerMethod = apiClass.getMethod("registerNEIGuiHandler",
				Class.forName("codechicken.nei.api.INEIGuiHandler"));
			Object handler = new NemiScreenHandler();
			registerMethod.invoke(null, handler);

		} catch (Exception e) {
			EmiLog.error("Failed to register NEI GUI handler via reflection", e);
		}
		isNEILoaded = true;
	}

	@Override
	public void register(EmiRegistry registry) {
		registerExclusionArea(registry);

		if (isNEILoaded) {
			registerNeiRecipes(registry);
		}
	}

	private void registerExclusionArea(EmiRegistry registry) {
		registry.addGenericExclusionArea((screen, consumer) -> {
			if (!(LayoutManager.getLayoutStyle() instanceof LayoutStyleMinecraft layout)) {
				return;
			}

			if (!(client.currentScreen instanceof RecipeScreen)) {
				int rows = (int) Math.ceil((double) layout.buttonCount / layout.numButtons);
				int width = layout.numButtons * NEI_BUTTON_SPACING;
				int height = rows * NEI_BUTTON_SPACING + NEI_OUTER_MARGIN;
				consumer.accept(new Bounds(0, 0, width, height));
				consumer.accept(new Bounds(emiButton.x, emiButton.y - 22, emiButton.getWidth(), emiButton.getHeight()));
				consumer.accept(new Bounds(treeButton.x, treeButton.y - 22, treeButton.getWidth(), treeButton.getHeight()));
			}
		});
	}

	private void registerNeiRecipes(EmiRegistry registry) {
		for (ICraftingHandler baseHandler : GuiCraftingRecipe.craftinghandlers) {
			if (baseHandler instanceof TemplateRecipeHandler templateHandler) {
				RecipeHarvester harvester = new RecipeHarvester(registry, templateHandler);
				harvester.harvest();

				for (NemiRecipeCategory category : harvester.getCategories().values()) {
					for (PositionedStack stack : RecipeCatalysts.getRecipeCatalysts(templateHandler)) {
						registry.addWorkstation(category, EmiStack.of(stack.item));
					}
				}
			}
		}
	}
}
