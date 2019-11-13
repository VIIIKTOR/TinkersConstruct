package slimeknights.tconstruct.library.exception;

import net.minecraft.util.ResourceLocation;
import slimeknights.tconstruct.library.TinkerAPIException;

public class TinkerJSONException extends TinkerAPIException {

  public static TinkerJSONException materialJsonWithoutRequiredData(ResourceLocation materialId) {
    return new TinkerJSONException("Malformed JSON for the material '" + materialId + "'. Missing craftable information.");
  }

  private TinkerJSONException(String message) {
    super(message);
  }

  private TinkerJSONException(String message, Throwable cause) {
    super(message, cause);
  }
}
