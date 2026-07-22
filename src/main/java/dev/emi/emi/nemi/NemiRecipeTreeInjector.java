package dev.emi.emi.nemi;

import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.TemplateRecipeHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.mixin.accessor.GuiContainerAccessor;
import dev.emi.emi.runtime.EmiDrawContext;
import dev.emi.emi.runtime.EmiLog;
import dev.emi.emi.widget.RecipeTreeButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.input.Mouse;
import shim.com.mojang.blaze3d.systems.RenderSystem;
import shim.net.minecraft.client.gui.DrawContext;
import shim.net.minecraft.client.util.math.MatrixStack;

import java.lang.reflect.Field;
import java.util.ArrayList;

public class NemiRecipeTreeInjector {

    private static final int RECIPE_X_OFFSET = 5;
    private static final int RECIPE_START_Y_OFFSET = 32;
    private static final int CONTAINER_Y_PADDING = 36;
    private static final int TREE_BUTTON_REL_X = 154;
    private static final int TREE_BUTTON_BOTTOM_ANCHOR_OFFSET = 44;
    private static final int DEFAULT_RECIPE_HEIGHT = 65;
    private static final int FALLBACK_GUI_Y_SIZE = 166;
    private static final int BUTTON_SIZE = 12;

    private Field currentHandlersField;
    private Field recipeTypeField;
    private Field pageField;
    private Field ySizeField;

    private boolean wasMouseDown = false;

    public NemiRecipeTreeInjector() {
        try {
            currentHandlersField = GuiRecipe.class.getField("currenthandlers");
            recipeTypeField = GuiRecipe.class.getField("recipetype");
            pageField = GuiRecipe.class.getField("page");
        } catch (Exception e) {
            EmiLog.error("Failed to map NEI GuiRecipe fields.", e);
        }

        try {
            ySizeField = GuiContainer.class.getDeclaredField("ySize");
            ySizeField.setAccessible(true);
        } catch (Exception e) {
            try {
                ySizeField = GuiContainer.class.getDeclaredField("field_147000_g");
                ySizeField.setAccessible(true);
            } catch (Exception e2) {
                EmiLog.error("Failed to map GuiContainer ySize field.", e2);
            }
        }
    }

    @SubscribeEvent
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof GuiRecipe<?> neiGui)) {
            wasMouseDown = false;
            return;
        }

        NeiState state = extractNeiState(neiGui);
        if (state == null) {
            return;
        }

        int guiLeft = ((GuiContainerAccessor) neiGui).getGuiLeft();
        int guiTop = ((GuiContainerAccessor) neiGui).getGuiTop();
        int guiHeight = getGuiHeight(neiGui);

        int defaultHeight = getHandlerDefaultHeight(state.handler);
        int handlerYShift = getHandlerYShift(state.handler);
        int defaultTotalHeight = Math.max(defaultHeight + handlerYShift, DEFAULT_RECIPE_HEIGHT);

        int containerHeight = guiHeight - CONTAINER_Y_PADDING;
        int recipesPerPage = Math.max(containerHeight / defaultTotalHeight, 1);
        int startIndex = state.page * recipesPerPage;
        int currentSlotTop = guiTop + RECIPE_START_Y_OFFSET;

        EmiDrawContext context = EmiDrawContext.wrap(new DrawContext(Minecraft.getMinecraft(), MatrixStack.INSTANCE));
        RenderSystem.disableLighting();

        boolean isMouseDown = Mouse.isButtonDown(0);
        boolean clicked = isMouseDown && !wasMouseDown;
        wasMouseDown = isMouseDown;

        for (int i = 0; i < recipesPerPage; i++) {
            int recipeIndex = startIndex + i;
            if (recipeIndex >= state.numRecipes) {
                break;
            }

            int recipeHeight = getRecipeHeight(state.handler, recipeIndex, defaultHeight) + handlerYShift;
            Bounds bounds = calculateButtonBounds(guiLeft, currentSlotTop, recipeHeight);
            RecipeTreeButtonWidget widget = createTreeButtonWidget(state.handler, recipeIndex, bounds);

            widget.render(context.raw(), event.mouseX, event.mouseY, event.renderPartialTicks);

            if (bounds.contains(event.mouseX, event.mouseY)) {
                EmiRenderHelper.drawTooltip(neiGui, context,
                    widget.getTooltip(event.mouseX, event.mouseY),
                    event.mouseX, event.mouseY);

                if (clicked) {
                    Minecraft.getMinecraft().getSoundHandler()
                        .playSound(PositionedSoundRecord.func_147674_a(EmiPort.id("gui.button.press"), 1.0f));
                    widget.mouseClicked(event.mouseX, event.mouseY, 0);
                    return;
                }
            }

            currentSlotTop += recipeHeight;
        }
    }

    private Bounds calculateButtonBounds(int guiLeft, int currentSlotTop, int recipeHeight) {
        int x = guiLeft + RECIPE_X_OFFSET + TREE_BUTTON_REL_X;
        int y = currentSlotTop + recipeHeight - TREE_BUTTON_BOTTOM_ANCHOR_OFFSET;
        return new Bounds(x, y, BUTTON_SIZE, BUTTON_SIZE);
    }

    private int getGuiHeight(GuiRecipe<?> neiGui) {
        if (ySizeField != null) {
            try {
                return ySizeField.getInt(neiGui);
            } catch (Exception ignored) {
            }
        }
        return FALLBACK_GUI_Y_SIZE;
    }

    private int getHandlerDefaultHeight(TemplateRecipeHandler handler) {
        try {
            HandlerInfo info = GuiRecipeTab.getHandlerInfo(handler);
            if (info != null) {
                return info.getHeight();
            }
        } catch (Throwable ignored) {
        }
        return DEFAULT_RECIPE_HEIGHT;
    }

    private int getHandlerYShift(TemplateRecipeHandler handler) {
        try {
            HandlerInfo info = GuiRecipeTab.getHandlerInfo(handler);
            if (info != null) {
                return info.getYShift();
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private int getRecipeHeight(TemplateRecipeHandler handler, int recipeIndex, int defaultHeight) {
        int customHeight = handler.getRecipeHeight(recipeIndex);
        return customHeight > 0 ? customHeight : defaultHeight;
    }

    private RecipeTreeButtonWidget createTreeButtonWidget(
        TemplateRecipeHandler handler, int recipeIndex, Bounds bounds) {

        String overlayId = handler.getOverlayIdentifier();
        if (overlayId == null) {
            overlayId = "unknown";
        }

        ResourceLocation categoryId = EmiPort.id(NemiPlugin.DOMAIN, overlayId);
        EmiRecipeCategory category = new NemiRecipeCategory(categoryId, EmiStack.EMPTY, handler.getRecipeName());

        ResourceLocation emiRecipeId = EmiPort.id(NemiPlugin.DOMAIN, overlayId + "/" + recipeIndex);
        NemiRecipe nemiRecipe = new NemiRecipe(category, handler, recipeIndex, emiRecipeId);

        return new RecipeTreeButtonWidget(bounds.x(), bounds.y(), nemiRecipe);
    }

    private NeiState extractNeiState(GuiRecipe<?> neiGui) {
        if (currentHandlersField == null || recipeTypeField == null || pageField == null) {
            return null;
        }

        try {
            ArrayList<?> handlers = (ArrayList<?>) currentHandlersField.get(neiGui);
            int recipeType = recipeTypeField.getInt(neiGui);
            int page = pageField.getInt(neiGui);

            if (recipeType < 0 || recipeType >= handlers.size()) {
                return null;
            }

            Object rawHandler = handlers.get(recipeType);
            if (rawHandler instanceof TemplateRecipeHandler handler) {
                return new NeiState(handler, page, handler.numRecipes());
            }
        } catch (Exception ignored) {
        }
        return null;
    }


    private static class NeiState {
        final TemplateRecipeHandler handler;
        final int page;
        final int numRecipes;

        NeiState(TemplateRecipeHandler handler, int page, int numRecipes) {
            this.handler = handler;
            this.page = page;
            this.numRecipes = numRecipes;
        }
    }
}
