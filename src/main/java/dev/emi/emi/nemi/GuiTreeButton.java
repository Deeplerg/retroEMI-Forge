package dev.emi.emi.nemi;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.RecipeHandlerRef;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.rewindmc.retroemi.RetroEMI;
import dev.emi.emi.EmiPort;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.BoM;
import dev.emi.emi.runtime.EmiDrawContext;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import shim.com.mojang.blaze3d.systems.RenderSystem;
import shim.net.minecraft.client.gui.DrawContext;

import java.util.List;
import java.util.Map;

public class GuiTreeButton extends GuiRecipeButton {
    private static final int BUTTON_ID_START = 14;

    protected static final DrawableResource ICON_OFF = new DrawableBuilder(
        EmiRenderHelper.BUTTONS.toString(),
        36,
        0,
        12,
        12).build();
    protected static final DrawableResource ICON_OFF_OVER = new DrawableBuilder(
        EmiRenderHelper.BUTTONS.toString(),
        36,
        12,
        12,
        12).build();
    protected static final DrawableResource ICON_ON = new DrawableBuilder(
        EmiRenderHelper.BUTTONS.toString(),
        36,
        36,
        12,
        12).build();
    protected static final DrawableResource ICON_ON_OVER = new DrawableBuilder(
        EmiRenderHelper.BUTTONS.toString(),
        36,
        48,
        12,
        12).build();

    protected final Recipe recipe;
    protected NemiRecipe nemiRecipe;
    protected Recipe.RecipeIngredient treeResult = null;
    protected Recipe.RecipeIngredient selectedResult = null;

    public GuiTreeButton(RecipeHandlerRef handlerRef, int x, int y) {
        super(handlerRef, x, y, BUTTON_ID_START + handlerRef.recipeIndex, "品");// 难绷4个方形≈品
        this.recipe = Recipe.of(this.handlerRef);
        this.nemiRecipe = new NemiRecipe(
            new NemiRecipeCategory(EmiPort.id(NemiPlugin.DOMAIN, this.handlerRef.handler.getOverlayIdentifier()),
                EmiStack.of(GuiRecipeTab.getHandlerInfo(this.handlerRef.handler).getItemStack()), this.handlerRef.handler.getRecipeName()),
            (TemplateRecipeHandler) this.handlerRef.handler, this.handlerRef.recipeIndex,
            EmiPort.id(NemiPlugin.DOMAIN, this.handlerRef.handler.getOverlayIdentifier() + "/" + this.handlerRef.recipeIndex));

        this.visible = this.treeResult != null;
    }

    public boolean isGoal() {
        return BoM.tree != null && BoM.tree.goal.recipe == nemiRecipe;
    }

    @Override
    public List<String> handleTooltip(List<String> currenttip) {
        currenttip.add(RetroEMI.translate("tooltip.emi.view_tree"));
        return currenttip;
    }

    @Override
    protected void drawContent(Minecraft minecraft, int y, int x, boolean mouseOver) {
        EmiDrawContext context = EmiDrawContext.instance();
        final DrawableResource icon = isGoal() ? (mouseOver ? ICON_ON_OVER : ICON_ON) : (mouseOver ? ICON_OFF_OVER : ICON_OFF);
        final int iconX = this.xPosition + (this.width - icon.width - 1) / 2;
        final int iconY = this.yPosition + (this.height - icon.height) / 2;

        context.setColor(1, 1, 1, this.enabled ? 1 : 0.5f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        icon.draw(iconX, iconY);
        RenderSystem.disableBlend();
        context.resetColor();

        if (!mouseOver) {
            this.selectedResult = null;
        } else if (this.selectedResult == null) {
            this.selectedResult = this.treeResult;
        }
    }

    @Override
    public Map<String, String> handleHotkeys(int mousex, int mousey, Map<String, String> hotkeys) {
        return shim.java.Map.of();
    }

    @Override
    public void lastKeyTyped(char keyChar, int keyID) {
    }

    @Override
    public void drawItemOverlay() {
        if (this.selectedResult == null) return;

        PositionedStack result = this.handlerRef.handler.getResultStack(handlerRef.recipeIndex);
        if (result == null) return;

        NEIClientUtils.gl2DRenderContext(
                () -> GuiDraw.drawRect(result.relx, result.rely, 16, 16, 0x66333333));
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        BoM.setGoal(nemiRecipe);
        EmiApi.viewRecipeTree();
    }
}
