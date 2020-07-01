package slimeknights.tconstruct.library.client.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.minecraft.client.Minecraft;
import net.minecraft.fluid.Fluid;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ForgeI18n;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.resource.IResourceType;
import net.minecraftforge.resource.ISelectiveResourceReloadListener;
import net.minecraftforge.resource.VanillaResourceType;
import slimeknights.tconstruct.library.Util;
import slimeknights.tconstruct.library.materials.MaterialValues;
import slimeknights.tconstruct.library.recipe.RecipeTypes;
import slimeknights.tconstruct.library.recipe.RecipeUtil;
import slimeknights.tconstruct.library.recipe.melting.MeltingRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FluidTooltipHandler implements ISelectiveResourceReloadListener {
  private static final Map<Fluid,List<FluidGuiEntry>> CACHE = new HashMap<>();
  private static final String HOLD_SHIFT = Util.makeTranslationKey("gui", "fluid.hold_shift");

  /*
   * Base units
   */
  private static final FluidGuiEntry KILOBUCKET = new FluidGuiEntry("kilobucket", 1000000);
  private static final FluidGuiEntry BUCKET = new FluidGuiEntry("bucket", 1000);
  private static final FluidGuiEntry MILLIBUCKET = new FluidGuiEntry("millibucket", 1);
  private static final FluidGuiEntry INGOT = new FluidGuiEntry("ingot", MaterialValues.VALUE_Ingot);

  /** List of options to check for tooltip entries */
  private static final Map<Integer,FluidGuiEntry> TOOLTIP_OPTIONS = new HashMap<>();
  // TODO: allow other mods to register amounts?
  static {
    FluidGuiEntry[] entries = {
      new FluidGuiEntry("block", MaterialValues.VALUE_Block),
      INGOT,
      new FluidGuiEntry("nugget", MaterialValues.VALUE_Nugget),
      new FluidGuiEntry("gem", MaterialValues.VALUE_Gem),
    };
    for (FluidGuiEntry entry : entries) {
      TOOLTIP_OPTIONS.put(entry.needed, entry);
    }
  }

  /** Final instance to register with the handler */
  public static final FluidTooltipHandler INSTANCE = new FluidTooltipHandler();

  @Override
  public void onResourceManagerReload(IResourceManager manager, Predicate<IResourceType> predicate) {
    if (predicate.test(VanillaResourceType.LANGUAGES)) {
      CACHE.clear();
    }
  }

  /**
   * Gets the tooltip for a fluid stack
   * @param fluid  Fluid stack instance
   * @return  Fluid tooltip
   */
  public static List<String> getFluidTooltip(FluidStack fluid) {
    List<String> tooltip = new ArrayList<>();
    // fluid name
    tooltip.add(fluid.getDisplayName().applyTextStyle(TextFormatting.WHITE).getString());
    // material
    appendMaterial(fluid, tooltip);
    // add mod display name
    ModList.get().getModContainerById(fluid.getFluid().getRegistryName().getNamespace())
           .map(container -> container.getModInfo().getDisplayName())
           .ifPresent(name -> tooltip.add(TextFormatting.BLUE + (TextFormatting.ITALIC + name)));
    return tooltip;
  }

  /**
   * Adds information for the tooltip based on material units
   * @param fluid    Input fluid stack
   * @param tooltip  Tooltip to append information
   */
  public static List<String> appendMaterial(FluidStack fluid, List<String> tooltip) {
    int original = fluid.getAmount();
    int amount = original;

    // if holding shift, skip specific units
    if(!Util.isShiftKeyDown()) {
      List<FluidGuiEntry> entries = CACHE.computeIfAbsent(fluid.getFluid(), FluidTooltipHandler::calcFluidEntries);
      for(FluidGuiEntry entry : entries) {
        amount = entry.getText(tooltip, amount);
      }
    }

    // standard display stuff: bucket amounts
    appendBuckets(amount, tooltip);

    // add hold shift message
    if (amount != original) {
      appendShift(tooltip);
    }

    return tooltip;
  }

  /**
   * Appends the hold shift message to the tooltip
   * @param tooltip  Tooltip to append information
   */
  public static List<String> appendShift(List<String> tooltip) {
    if(!Util.isShiftKeyDown()) {
      tooltip.add("");
      tooltip.add(TextFormatting.GRAY + ForgeI18n.getPattern(HOLD_SHIFT));
    }
    return tooltip;
  }

  /**
   * Adds information to the tooltip based on ingot units
   * @param amount   Fluid amount
   * @param tooltip  Tooltip to append information
   * @return
   */
  public static List<String> appendIngots(int amount, List<String> tooltip) {
    amount = INGOT.getText(tooltip, amount);
    return appendBuckets(amount, tooltip);
  }

  /**
   * Adds information to the tooltip based on the fluid using bucket units
   * @param amount   Fluid amount
   * @param tooltip  Tooltip to append information
   */
  public static List<String> appendBuckets(int amount, List<String> tooltip) {
    amount = KILOBUCKET.getText(tooltip, amount);
    amount = BUCKET.getText(tooltip, amount);
    MILLIBUCKET.getText(tooltip, amount);
    return tooltip;
  }

  /**
   * Gets all relevant entries for a fluid
   * @param fluid  Relevant fluid
   * @return  List of entries for the fluid
   */
  private static List<FluidGuiEntry> calcFluidEntries(Fluid fluid) {
    // use a set to prevent duplicates
    Set<FluidGuiEntry> set = new HashSet<>();

    // TODO: this should likely use casting recipes
    for (MeltingRecipe recipe : RecipeUtil.getRecipes(Minecraft.getInstance().world.getRecipeManager(), RecipeTypes.MELTING, MeltingRecipe.class)) {
      FluidStack output = recipe.getOutput();
      if (output.getFluid() == fluid) {
        FluidGuiEntry entry = TOOLTIP_OPTIONS.get(output.getAmount());
        if (entry != null) {
          set.add(entry);
        }
      }
    }

    // sort the set and return the sorted list
    // important that the largest value is first, as that is how the entries are processed
    return set.stream()
              .sorted((e1, e2) -> e2.needed - e1.needed)
              .collect(Collectors.toList());
  }

  private static class FluidGuiEntry {
    private final String translationKey;
    private final int needed;

    private FluidGuiEntry(String name, int needed) {
      this.translationKey = Util.makeTranslationKey("gui", "fluid." + name);
      this.needed = needed;
    }

    /**
     * Gets the display text for this fluid entry
     * @return  Display text
     */
    private int getText(List<String> tooltip, int amount) {
      int full = amount / needed;
      if (full > 0) {
        tooltip.add(TextFormatting.GRAY + ForgeI18n.parseMessage(translationKey, full));
      }
      return amount % needed;
    }
  }
}
