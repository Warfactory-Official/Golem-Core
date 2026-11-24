package dev.golem.probe;

//? if jei {
import dev.golem.core.Ids;
import dev.golem.core.jei.JeiViewer;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.Identifier;
import org.slf4j.LoggerFactory;

/** The probe's JEI plugin: found by its annotation on NeoForge and by the {@code jei_mod_plugin} entrypoint on Fabric. */
@JeiPlugin
public final class ProbeJeiPlugin implements IModPlugin {

    @Override
    public Identifier getPluginUid() {
        return Ids.of(ProbeEntry.MOD_ID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        JeiViewer.categories(registration, ProbeViewer.INSTANCE);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        JeiViewer.recipes(registration, ProbeViewer.INSTANCE);
        // JEI logs only its slow plugins; this line is the run's evidence that JEI reached the probe.
        LoggerFactory.getLogger("Golem Probe").info("JEI registered the probe's recipes");
    }
}
//?}
