package dev.emi.emi.nemi;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.TemplateRecipeHandler;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiLog;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class NemiRecipe implements EmiRecipe {
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
        return Math.max(HandlerInfo.DEFAULT_WIDTH, GuiRecipeTab.getHandlerInfo(neiHandler).getWidth());
    }

    @Override
    public int getDisplayHeight() {
        HandlerInfo info = GuiRecipeTab.getHandlerInfo(neiHandler);
        int recipeHeight = neiHandler.getRecipeHeight(recipeIndex);
        int h = recipeHeight > 0 ? recipeHeight : info.getHeight();
        return h + info.getYShift() + 4;
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
        EmiDrawContext context = EmiDrawContext.instance();
        widgets.addDrawable(0, 0, this.getDisplayWidth(), this.getDisplayHeight(), (raw, mouseX, mouseY, delta) -> {
            try {
                context.push();
                context.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                context.enableBlend();

                neiHandler.cycleticks++; // Progresses NEI's internal animation state

                neiHandler.drawBackground(recipeIndex);
                neiHandler.drawForeground(recipeIndex);
            } catch (Exception e) {
                EmiLog.error("Error drawing NEI background for recipe " + id, e);
            } finally {
                context.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                context.pop();
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
            widgets.addSlot(parseIngredient(result), result.relx - 1, result.rely - 1).drawBack(false).recipeContext(this);
        }
    }

    private void addSecondaryOutputWidgets(WidgetHolder widgets) {
        List<PositionedStack> secondaryResults = neiHandler.getOtherStacks(recipeIndex);
        if (secondaryResults != null) {
            for (PositionedStack stack : secondaryResults) {
                if (stack != null && stack.item != null) {
                    widgets.addSlot(parseIngredient(stack), stack.relx - 1, stack.rely - 1).drawBack(false).recipeContext(this);
                }
            }
        }
    }
}
