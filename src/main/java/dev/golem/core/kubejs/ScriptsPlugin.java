package dev.golem.core.kubejs;

//? if kubejs && >=1.20.5 {

import dev.golem.core.platform.ModEntries;
import dev.golem.core.script.Scripts;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentTypeRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import dev.latvian.mods.kubejs.script.BindingRegistry;

/**
 * Golem Core's KubeJS plugin (listed in {@code kubejs.plugins.txt}): serves every Golem-based mod's script surface
 * with no KubeJS code in the mod. Recipe tables become recipe schemas ({@link TableSchemas}), {@link Scripts}' groups
 * become event groups and its bindings script globals ({@link Events}).
 *
 * <p>KubeJS 7/8 order (both {@code KubeJS} constructors): plugins' {@code init}, startup scripts, client scripts, then
 * plugins' {@code initStartup} ("after startup scripts have been loaded, but before any registry stuff"), which is
 * where {@link Scripts#startupScriptsLoaded()} runs.
 */
public final class ScriptsPlugin implements KubeJSPlugin {

    @Override
    public void init() {
        ModEntries.ensureDeclared(); // no-op on NeoForge, where each mod's metadata orders it before KubeJS
        Events.install();
    }

    @Override
    public void initStartup() {
        Scripts.startupScriptsLoaded();
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        Events.INSTANCE.register(registry);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        Events.INSTANCE.bind(bindings);
    }

    @Override
    public void registerRecipeComponents(RecipeComponentTypeRegistry registry) {
        //? if >=1.21.2 {
        registry.register(FieldComponent.TYPE, FieldComponent.MAP_CODEC);
        //?} else {
        /*registry.register(FieldComponent.TYPE);
        *///?}
    }

    @Override
    public void registerRecipeSchemas(RecipeSchemaRegistry registry) {
        TableSchemas.register(registry);
    }
}
//?} elif kubejs {
/*import dev.golem.core.platform.ModEntries;
import dev.golem.core.script.Scripts;
import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.schema.RegisterRecipeSchemasEvent;
import dev.latvian.mods.kubejs.script.BindingsEvent;

// Golem Core's KubeJS 6 plugin (listed in kubejs.plugins.txt): the same surface as the 7/8 plugin above, through
// KubeJS 6's class-based plugin. KubeJS 6 order (KubeJS's common init, inside its Forge mod constructor or Fabric main
// entrypoint): plugins' init, registerEvents, startup scripts, then initStartup.
public final class ScriptsPlugin extends KubeJSPlugin {

    @Override
    public void init() {
        // Forge 1.20.1 constructs mods in parallel and Fabric runs main entrypoints in no set order: make every
        // Golem-based mod's declarations (its script groups and bindings) happen before KubeJS asks for them.
        ModEntries.ensureDeclared();
        Events.install();
    }

    @Override
    public void registerEvents() {
        Events.INSTANCE.register();
    }

    @Override
    public void initStartup() {
        Scripts.startupScriptsLoaded();
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        Events.INSTANCE.bind(event);
    }

    @Override
    public void registerRecipeSchemas(RegisterRecipeSchemasEvent event) {
        LegacyTableSchemas.register(event);
    }
}
*///?}
