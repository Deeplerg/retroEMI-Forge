package dev.emi.emi.nemi;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.TemplateRecipeHandler;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.emi.emi.runtime.EmiLog;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class NemiRecipe implements EmiRecipe {
	private static final int DEFAULT_NEI_WIDTH = 166;
	private static final int DEFAULT_NEI_HEIGHT = 65;

	private final EmiRecipeCategory category;
	private final ResourceLocation id;
	private final TemplateRecipeHandler neiHandler;
	private final int recipeIndex;

	private final List<EmiIngredient> inputs;
	private final List<EmiStack> outputs;

	public NemiRecipe(EmiRecipeCategory category, TemplateRecipeHandler neiHandler, int recipeIndex, ResourceLocation id) {
		this.category = category;
		this.neiHandler = neiHandler;
		this.recipeIndex = recipeIndex;
		this.id = id;

		this.inputs = parseInputs();
		this.outputs = parseOutputs();
	}

	private List<EmiIngredient> parseInputs() {
		List<EmiIngredient> parsedInputs = new ArrayList<>();
		List<PositionedStack> ingredients = neiHandler.getIngredientStacks(recipeIndex);

		if (ingredients != null) {
			for (PositionedStack stack : ingredients) {
				parsedInputs.add(parseIngredient(stack));
			}
		}

		return parsedInputs;
	}

	private List<EmiStack> parseOutputs() {
		List<EmiStack> parsedOutputs = new ArrayList<>();

		addMainOutput(parsedOutputs);
		addSecondaryOutputs(parsedOutputs);

		return parsedOutputs;
	}

	private void addMainOutput(List<EmiStack> parsedOutputs) {
		PositionedStack mainResult = neiHandler.getResultStack(recipeIndex);
		if (mainResult != null && mainResult.item != null) {
			parsedOutputs.add(EmiStack.of(mainResult.item));
		}
	}

	private void addSecondaryOutputs(List<EmiStack> parsedOutputs) {
		List<PositionedStack> secondaryResults = neiHandler.getOtherStacks(recipeIndex);
		if (secondaryResults != null) {
			for (PositionedStack stack : secondaryResults) {
				if (stack != null && stack.item != null) {
					parsedOutputs.add(EmiStack.of(stack.item));
				}
			}
		}
	}

	private EmiIngredient parseIngredient(PositionedStack positionedStack) {
		if (positionedStack == null) {
			return EmiStack.EMPTY;
		}

		List<EmiIngredient> ingredients = new ArrayList<>();

		if (positionedStack.items != null && positionedStack.items.length > 0) {
			for (ItemStack stack : positionedStack.items) {
				if (stack != null) {
					// ofPotentialTag expands wildcard metadata used heavily by NEI
					ingredients.add(EmiStack.ofPotentialTag(stack));
				}
			}
		} else if (positionedStack.item != null) {
			ingredients.add(EmiStack.ofPotentialTag(positionedStack.item));
		}

		if (ingredients.isEmpty()) {
			return EmiStack.EMPTY;
		}

		return EmiIngredient.of(ingredients);
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return category;
	}

	@Override
	public @Nullable ResourceLocation getId() {
		return id;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return inputs;
	}

	@Override
	public List<EmiStack> getOutputs() {
		return outputs;
	}

	@Override
	public int getDisplayWidth() {
		return DEFAULT_NEI_WIDTH;
	}

	@Override
	public int getDisplayHeight() {
		return DEFAULT_NEI_HEIGHT;
	}

	@Override
	public boolean supportsRecipeTree() {
		return true;
	}

	@Override
	public void addWidgets(WidgetHolder widgets) {
		addBackgroundAndExtrasWidget(widgets);
		addInputWidgets(widgets);
		addMainOutputWidget(widgets);
		addSecondaryOutputWidgets(widgets);
	}

	private void addBackgroundAndExtrasWidget(WidgetHolder widgets) {
		widgets.addDrawable(0, 0, DEFAULT_NEI_WIDTH, DEFAULT_NEI_HEIGHT, (raw, mouseX, mouseY, delta) -> {
			try {
				org.lwjgl.opengl.GL11.glPushMatrix();
				org.lwjgl.opengl.GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
				shim.com.mojang.blaze3d.systems.RenderSystem.enableBlend();

				neiHandler.cycleticks++; // Progresses NEI's internal animation state

				neiHandler.drawBackground(recipeIndex);
				neiHandler.drawExtras(recipeIndex);
			} catch (Exception e) {
				EmiLog.error("Error drawing NEI background for recipe " + id, e);
			} finally {
				org.lwjgl.opengl.GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
				org.lwjgl.opengl.GL11.glPopMatrix();
			}
		});
	}

	private void addInputWidgets(WidgetHolder widgets) {
		List<PositionedStack> ingredients = neiHandler.getIngredientStacks(recipeIndex);
		if (ingredients != null) {
			for (PositionedStack stack : ingredients) {
				widgets.addSlot(parseIngredient(stack), stack.relx - 1, stack.rely - 1).drawBack(false);
			}
		}
	}

	private void addMainOutputWidget(WidgetHolder widgets) {
		PositionedStack result = neiHandler.getResultStack(recipeIndex);
		if (result != null && result.item != null) {
			widgets.addSlot(EmiStack.of(result.item), result.relx - 1, result.rely - 1).drawBack(false).recipeContext(this);
		}
	}

	private void addSecondaryOutputWidgets(WidgetHolder widgets) {
		List<PositionedStack> secondaryResults = neiHandler.getOtherStacks(recipeIndex);
		if (secondaryResults != null) {
			for (PositionedStack stack : secondaryResults) {
				if (stack != null && stack.item != null) {
					widgets.addSlot(EmiStack.of(stack.item), stack.relx - 1, stack.rely - 1).drawBack(false).recipeContext(this);
				}
			}
		}
	}
}
