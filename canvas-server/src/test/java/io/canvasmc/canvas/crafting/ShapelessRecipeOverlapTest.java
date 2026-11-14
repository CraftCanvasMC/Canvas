package io.canvasmc.canvas.crafting;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.bukkit.support.environment.VanillaFeature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@VanillaFeature
class ShapelessRecipeOverlapTest {

    private static ShapelessRecipe shapeless(final Ingredient... ingredients) {
        return new ShapelessRecipe(
            new Recipe.CommonInfo(true),
            new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
            new ItemStackTemplate(Items.DIAMOND),
            List.of(ingredients)
        );
    }

    private static boolean matches(final ShapelessRecipe recipe, final ItemStack... grid) {
        return recipe.matches(CraftingInput.of(grid.length, 1, List.of(grid)), null);
    }

    @Test
    void overlappingIngredientsMatchInAnyOrder() {
        final ShapelessRecipe recipe = shapeless(Ingredient.of(Items.STONE, Items.COBBLESTONE), Ingredient.of(Items.STONE));
        assertTrue(matches(recipe, new ItemStack(Items.COBBLESTONE), new ItemStack(Items.STONE)));
        assertTrue(matches(recipe, new ItemStack(Items.STONE), new ItemStack(Items.COBBLESTONE)));
    }

    @Test
    void overlappingIngredientsStillRejectMissingItems() {
        final ShapelessRecipe recipe = shapeless(Ingredient.of(Items.STONE, Items.COBBLESTONE), Ingredient.of(Items.STONE));
        assertFalse(matches(recipe, new ItemStack(Items.COBBLESTONE), new ItemStack(Items.COBBLESTONE)));
        assertFalse(matches(recipe, new ItemStack(Items.STONE), new ItemStack(Items.DIRT)));
        assertFalse(matches(recipe, new ItemStack(Items.STONE)));
    }
}
