plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1-neoforge" /* [SC] DO NOT EDIT */

// Builds every node in one invocation. Stonecutter 0.9 replaced the old `registerChiseled` API with
// task aggregation: `stonecutter.tasks.named` returns a lazy collection of that task across all nodes.
tasks.register("chiseledBuild") {
    group = "build"
    description = "Builds every registered version/loader node."
    dependsOn(stonecutter.tasks.named("build"))
}

// Uploads every node's jar to Modrinth and CurseForge (Golem-Core/gradle/mod/publish.gradle.kts). Add
// -Ppublish.dryRun=true to build and log the uploads without making them.
tasks.register("publishAllMods") {
    group = "publishing"
    description = "Publishes every version/loader node to Modrinth and CurseForge."
    dependsOn(stonecutter.tasks.named("publishMods"))
}

// The rules that rewrite the access widener, by the version they start at. Loom reads a node's widener while
// configuring, before Stonecutter has rewritten the tree, so build.fabric.gradle.kts applies these to its copy too.
val widenerRules: List<Pair<String, List<Pair<String, String>>>> = listOf(
    "26.2" to listOf(
        "# 26.2 only: the ArrayVoxelShape constructor OrderedShape calls is package-private there." to
            "accessible method net/minecraft/world/phys/shapes/ArrayVoxelShape <init> (Lnet/minecraft/world/phys/shapes/DiscreteVoxelShape;[D[D[D)V",
        "accessible class net/minecraft/client/renderer/SectionOcclusionGraph\$GraphEvents" to
            "# Before 26.2: SectionOcclusionGraph\$GraphEvents, read by RenderOps.terrainBusy.",
    ),
    "26.3" to listOf(
        // 26.3 rebuilt InterpolationHandler; its widened 26.2 members become comments.
        "accessible class net/minecraft/world/entity/InterpolationHandler\$InterpolationData" to
            "# Before 26.3: class net/minecraft/world/entity/InterpolationHandler\$InterpolationData",
        "accessible field net/minecraft/world/entity/InterpolationHandler\$InterpolationData " to
            "# Before 26.3: field net/minecraft/world/entity/InterpolationHandler\$InterpolationData ",
        "accessible field net/minecraft/world/entity/InterpolationHandler " to
            "# Before 26.3: field net/minecraft/world/entity/InterpolationHandler ",
        "# 26.3 only: recipe views, the RecipeMap constructor and interpolation targets (a 26.3 rule expands this line)." to
            listOf(
                "mutable field net/minecraft/world/item/crafting/RecipeManager recipes Lnet/minecraft/world/item/crafting/RecipeMap;",
                "accessible field net/minecraft/world/item/crafting/RecipeManager learnableRecipes Ljava/util/Collection;",
                "mutable field net/minecraft/world/item/crafting/RecipeManager learnableRecipes Ljava/util/Collection;",
                "accessible method net/minecraft/world/item/crafting/RecipeMap <init> (Lcom/google/common/collect/Multimap;Ljava/util/Map;)V",
                "accessible method net/minecraft/world/entity/AbstractInterpolationHandler interpolationData ()Lnet/minecraft/core/PositionAndRotation\$Mutable;",
                "accessible method net/minecraft/world/entity/LinearInterpolationHandler interpolationData ()Lnet/minecraft/core/PositionAndRotation\$Mutable;",
                "accessible method net/minecraft/world/entity/SteppedInterpolationHandler interpolationData ()Lnet/minecraft/core/PositionAndRotation\$Mutable;",
            ).joinToString("\n"),
        "net/minecraft/client/renderer/ItemInHandRenderer" to "net/minecraft/client/player/FirstPersonHandsAndItems",
        "com/mojang/blaze3d/opengl/GlBuffer" to "com/mojang/renderpearl/backend/opengl/GlBuffer",
        "com/mojang/blaze3d/opengl/GlRenderPass" to "com/mojang/renderpearl/backend/opengl/GlRenderPass",
        "com/mojang/blaze3d/opengl/GlCommandEncoder" to "com/mojang/renderpearl/backend/opengl/GlCommandEncoder",
        "com/mojang/blaze3d/buffers/GpuBuffer" to "com/mojang/renderpearl/api/buffers/GpuBuffer",
        "com/mojang/blaze3d/systems/RenderPass" to "com/mojang/renderpearl/api/commands/RenderPass",
        "com/mojang/blaze3d/pipeline/RenderPipeline" to "com/mojang/renderpearl/api/pipeline/RenderPipeline",
    ),
)
gradle.extra["golem.widenerRules"] = widenerRules

stonecutter parameters {
    val loader = node.metadata.project.substringAfterLast('-')

    // Loader constants, used as `//? if fabric {` / `//? if forgelike {`.
    constants["fabric"] = loader == "fabric"
    constants["neoforge"] = loader == "neoforge"
    constants["forge"] = loader == "forge"
    constants["forgelike"] = loader == "neoforge" || loader == "forge"

    // Version comparisons use the node's logical version (26.1, 1.21.1, ...), so `//? if >=1.21.11`
    // works regardless of the loader suffix in the node name.
    // True only where the node declares JEI coordinates, so the integration compiles out elsewhere.
    constants["jei"] = !(node.project.findProperty("deps.jei") as String?).isNullOrBlank()
    constants["rei"] = !(node.project.findProperty("deps.rei") as String?).isNullOrBlank()
    constants["emi"] = !(node.project.findProperty("deps.emi") as String?).isNullOrBlank()
    // True only where the node declares CC: Tweaked coordinates (an optional peer, like JEI).
    constants["computercraft"] = !(node.project.findProperty("deps.cc") as String?).isNullOrBlank()
    // True only where the node declares KubeJS coordinates (an optional peer; dev.golem.core.kubejs in Golem Core).
    constants["kubejs"] = !(node.project.findProperty("deps.kubejs") as String?).isNullOrBlank()
    // True only where the node declares Jade coordinates (an optional peer; dev.golem.core.jade in Golem Core).
    constants["jade"] = !(node.project.findProperty("deps.jade") as String?).isNullOrBlank()
    // True only where the node declares Applied Energistics 2 coordinates (an optional peer; dev.golem.core.ae2).
    constants["ae2"] = !(node.project.findProperty("deps.ae2") as String?).isNullOrBlank()
    // True only where the node declares OpenComputers: Rebooted coordinates (an optional peer; dev.golem.core.opencomputers).
    constants["opencomputers"] = !(node.project.findProperty("deps.oc") as String?).isNullOrBlank()

    swaps["mc_version"] = "\"${node.metadata.version}\";"

    // Mechanical renames, declared 26.1 -> older because 26.1 is this layer's source of truth. Keep
    // this list minimal: a replacement must be injective in BOTH directions, so only names that
    // cannot appear in the other era belong here. Anything needing real translation gets a facade.
    replacements {
        // Fabric API's own rename, not vanilla's. Qualified with the owning type so the rule can only
        // ever hit these calls, and reversible: neither spelling exists in the other era. Without
        // this, GolemNetwork would need two near-identical fabric branches differing by two tokens.
        string(current.parsed < "26.1") {
            replace("PayloadTypeRegistry.clientboundPlay()", "PayloadTypeRegistry.playS2C()")
            replace("PayloadTypeRegistry.serverboundPlay()", "PayloadTypeRegistry.playC2S()")
            replace("PayloadTypeRegistry.clientboundConfiguration()", "PayloadTypeRegistry.configurationS2C()")
            replace("PayloadTypeRegistry.serverboundConfiguration()", "PayloadTypeRegistry.configurationC2S()")
            replace("configurationContext.packetListener()", "configurationContext.networkHandler()")
            replace("FabricServerConfigurationPacketListenerImpl", "FabricServerConfigurationNetworkHandler")
        }
        string(current.parsed < "1.21.11") {
            replace("BuiltInRegistries.ITEM.getValue(Ids.parse(", "BuiltInRegistries.ITEM.get(Ids.parse(")
            replace("BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Ids.parse(",
                    "BuiltInRegistries.DATA_COMPONENT_TYPE.get(Ids.parse(")
        }
        // 26.1 renamed or moved these; 1.21.11 and older still use the old spelling. The tree writes the 26.1 one in
        // every branch, so the old spelling never occurs in it and the rule reverses cleanly.
        string(current.parsed < "26.1") {
            // Delimited: the bare name also occurs inside other identifiers (NeoForge's getGuiGraphics).
            replace("GuiGraphicsExtractor ", "GuiGraphics ")
            replace("GuiGraphicsExtractor;", "GuiGraphics;")
            replace("GuiGraphicsExtractor>", "GuiGraphics>")
            replace("GuiGraphicsExtractor}", "GuiGraphics}")
            replace("net.minecraft.world.inventory.ContainerInput", "net.minecraft.world.inventory.ClickType")
            replace("handleContainerInput", "handleInventoryMouseClick")
            replace("TestEnvironmentDefinition<?>>", "TestEnvironmentDefinition>")
            replace("net.minecraft.world.item.ItemStackTemplate", "dev.golem.core.world.StackTemplate")
            replace("DefaultVertexFormat.ENTITY", "DefaultVertexFormat.NEW_ENTITY")
            replace("gameRenderer.lightmap()", "gameRenderer.lightTexture().getTextureView()")
            replace("#moj_import <minecraft:sample_lightmap.glsl>", "#moj_import <golemcore:sample_lightmap.glsl>")
            // Access widener lines (Stonecutter rewrites resources too).
            replace("net/minecraft/client/renderer/state/gui/GuiRenderState", "net/minecraft/client/gui/render/state/GuiRenderState")
            replace("freeBuffers Ljava/util/concurrent/ArrayBlockingQueue;", "freeBuffers Ljava/util/Queue;")
            // The Fabric GameTest bridge reads RegistryLoadTask.registry on 26.1 and RegistryDataLoader.Loader before it.
            replace("accessible field net/minecraft/resources/RegistryLoadTask registry Lnet/minecraft/core/WritableRegistry;",
                    "accessible class net/minecraft/resources/RegistryDataLoader\$Loader")
            replace("net.minecraft.client.renderer.block.BlockAndTintGetter", "net.minecraft.world.level.BlockAndTintGetter")
            replace("net.minecraft.client.renderer.block.dispatch.ModelState", "net.minecraft.client.resources.model.ModelState")
            replace("net.minecraft.client.renderer.block.dispatch.BlockStateModel",
                    "net.minecraft.client.renderer.block.model.BlockStateModel")
            replace("net.minecraft.client.resources.model.geometry.BakedQuad", "net.minecraft.client.renderer.block.model.BakedQuad")
            replace("net.minecraft.client.resources.model.geometry.QuadCollection", "net.minecraft.client.resources.model.QuadCollection")
            replace("net.minecraft.client.resources.model.geometry.UnbakedGeometry",
                    "net.minecraft.client.resources.model.UnbakedGeometry")
            replace("net.minecraft.client.resources.model.sprite.Material", "net.minecraft.client.resources.model.Material")
            replace("net.minecraft.client.resources.model.sprite.TextureSlots", "net.minecraft.client.renderer.block.model.TextureSlots")
            replace("net.minecraft.client.multiplayer.chat.GuiMessage", "net.minecraft.client.GuiMessage")
            replace("net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState",
                    "net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState")
            replace("net.minecraft.client.renderer.state.level.CameraRenderState", "net.minecraft.client.renderer.state.CameraRenderState")
            replace("net.minecraft.client.renderer.state.level.LevelRenderState", "net.minecraft.client.renderer.state.LevelRenderState")
            replace("net.minecraft.client.renderer.state.level.QuadParticleRenderState",
                    "net.minecraft.client.renderer.state.QuadParticleRenderState")
            replace("net.minecraft.client.renderer.state.level.WeatherRenderState", "net.minecraft.client.renderer.state.WeatherRenderState")
            replace("net/minecraft/client/renderer/state/level/WeatherRenderState", "net/minecraft/client/renderer/state/WeatherRenderState")
        }
        // 26.2 split advancements.criterion into predicates and triggers. Forward rules: these nodes are newer than
        // the tree, and every name is a whole class, so neither spelling occurs in the other era.
        string(current.parsed >= "26.2") {
            replace("net.minecraft.advancements.CriteriaTriggers", "net.minecraft.advancements.triggers.CriteriaTriggers")
            replace("net.minecraft.advancements.CriterionTrigger<", "net.minecraft.advancements.triggers.CriterionTrigger<")
            replace("net.minecraft.advancements.criterion.SimpleCriterionTrigger",
                    "net.minecraft.advancements.triggers.SimpleCriterionTrigger")
            replace("net.minecraft.advancements.criterion.ContextAwarePredicate",
                    "net.minecraft.advancements.predicates.ContextAwarePredicate")
            replace("net.minecraft.advancements.criterion.EntityPredicate",
                    "net.minecraft.advancements.predicates.entity.EntityPredicate")
            replace("net.minecraft.advancements.criterion.ItemPredicate", "net.minecraft.advancements.predicates.ItemPredicate")
            replace("net.minecraft.advancements.criterion.MinMaxBounds", "net.minecraft.advancements.predicates.MinMaxBounds")
        }
        // 26.2 gave GameRenderer record-style accessors and moved screens to Gui, the HUD to Gui.hud.
        string(current.parsed >= "26.2") {
            replace("gameRenderer.getMainCamera()", "gameRenderer.mainCamera()")
            replace("gameRenderer.getGameRenderState()", "gameRenderer.gameRenderState()")
            replace("gameRenderer.getLighting()", "gameRenderer.lighting()")
            replace("gameRenderer.getFeatureRenderDispatcher()", "gameRenderer.featureRenderDispatcher()")
            replace(".getMainRenderTarget()", ".gameRenderer.mainRenderTarget()")
            replace("Minecraft.getInstance().renderBuffers()", "Minecraft.getInstance().gameRenderer.renderBuffers()")
            replace("client.renderBuffers()", "client.gameRenderer.renderBuffers()")
            replace("Minecraft.useShaderTransparency()",
                    "Minecraft.getInstance().gameRenderer.gameRenderState().useShaderTransparency()")
            replace("mc.screen", "mc.gui.screen()")
            replace("m.screen", "m.gui.screen()")
            replace("minecraft.screen", "minecraft.gui.screen()")
            replace("Minecraft.getInstance().screen", "Minecraft.getInstance().gui.screen()")
            replace("mc.setScreen(", "mc.gui.setScreen(")
            replace("minecraft.setScreen(", "minecraft.gui.setScreen(")
            replace("mc.getOverlay()", "mc.gui.overlay()")
            replace("m.getOverlay()", "m.gui.overlay()")
            replace("minecraft.getOverlay()", "minecraft.gui.overlay()")
            replace("gui.setOverlayMessage(", "gui.hud.setOverlayMessage(")
            replace("gui.getChat()", "gui.hud.getChat()")
        }
        // 26.2's pipeline API: topology apart from the vertex format, samplers and uniforms in bind group layouts,
        // record-style mesh and pipeline accessors. Forward rules; every target spelling is 26.2-only.
        string(current.parsed >= "26.2") {
            replace("VertexFormat.Mode", "com.mojang.blaze3d.PrimitiveTopology")
            replace("VertexFormat.IndexType", "com.mojang.blaze3d.IndexType")
            replace("drawState().mode()", "drawState().primitiveTopology()")
            replace("pipeline.getVertexFormat()", "pipeline.getVertexFormatBinding(0)")
            replace("RenderSystem.getModelViewMatrix()", "RenderSystem.getModelViewMatrixCopy()")
            replace("device.getUniformOffsetAlignment()", "device.getDeviceInfo().limits().minUniformOffsetAlignment()")
            replace("RenderPipelines.MATRICES_PROJECTION_SNIPPET", "dev.golem.core.render.PipelineStates.MATRICES_PROJECTION_SNIPPET")
            replace("RenderPipelines.FOG_SNIPPET", "dev.golem.core.render.PipelineStates.FOG_SNIPPET")
            replace(".withSampler(\"Sampler0\")", ".withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER0)")
            replace(".withSampler(\"Sampler1\")", ".withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER1)")
            replace(".withSampler(\"Sampler2\")", ".withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER2)")
            replace(".withSampler(\"Sampler3\")", ".withBindGroupLayout(dev.golem.core.render.PipelineStates.sampler(\"Sampler3\"))")
            replace(".withSampler(\"Sampler4\")", ".withBindGroupLayout(dev.golem.core.render.PipelineStates.sampler(\"Sampler4\"))")
            replace(".withSampler(\"Sampler\" + i)", ".withBindGroupLayout(dev.golem.core.render.PipelineStates.sampler(\"Sampler\" + i))")
            replace(".withUniform(\"GolemSkyShader\", UniformType.UNIFORM_BUFFER)",
                    ".withBindGroupLayout(dev.golem.core.render.PipelineStates.uniform(\"GolemSkyShader\"))")
            replace(".withUniform(\"StaticMesh\", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER)",
                    ".withBindGroupLayout(dev.golem.core.render.PipelineStates.uniform(\"StaticMesh\"))")
            replace("levelRenderer.getTranslucentTarget()", "levelRenderer.translucentTarget()")
            replace("levelRenderer.getItemEntityTarget()", "levelRenderer.itemEntityTarget()")
            replace("renderer.getSectionRenderDispatcher()", "renderer.sectionRenderDispatcher()")
            replace("renderer.getSectionOcclusionGraph()", "renderer.sectionOcclusionGraph()")
            replace("minecraft.levelRenderer.iterateVisibleBlockEntities(", "minecraft.levelExtractor.iterateVisibleBlockEntities(")
            replace("minecraft.levelRenderer.setSectionDirty(", "minecraft.levelExtractor.setSectionDirty(")
            replace("net.minecraft.client.renderer.LevelRenderer.getLightCoords(", "net.minecraft.util.LightCoordsUtil.getLightCoords(")
            replace("net.minecraft.world.entity.EntityType.PHANTOM", "net.minecraft.world.entity.EntityTypes.PHANTOM")
            replace("net.minecraft.world.entity.EntityType.WANDERING_TRADER", "net.minecraft.world.entity.EntityTypes.WANDERING_TRADER")
            replace("net.minecraft.world.entity.EntityType.TRADER_LLAMA", "net.minecraft.world.entity.EntityTypes.TRADER_LLAMA")
            replace("clearColorTexture(target.getColorTexture(), 0)", "clearColorTexture(target.getColorTexture(), new org.joml.Vector4f())")
            replace("TextureTransform.DEFAULT_TEXTURING.getMatrix()", "TextureTransform.DEFAULT_TEXTURING.createMatrix()")
            replace("vanilla.getVertexFormatMode()", "vanilla.getPrimitiveTopology()")
            replace("target.submitMovingBlock(poseStack, block.delegate)", "target.submitMovingBlock(poseStack, block.delegate, 0)")
            widenerRules.first { it.first == "26.2" }.second.forEach { (from, to) -> replace(from, to) }
            // Reversed depth: the nearest surface has the largest value.
            replace("gl_FragDepth = min(mainDepth, min(terrainDepth, entityDepth));",
                    "gl_FragDepth = max(mainDepth, max(terrainDepth, entityDepth));")
        }
        // 26.3 moved blaze3d's device, pipeline, buffer and texture API into com.mojang.renderpearl. Class by class,
        // longest names first so a prefix rule never splits a longer name.
        string(current.parsed >= "26.3") {
            replace("com.mojang.blaze3d.GpuFormat", "com.mojang.renderpearl.api.GpuFormat")
            replace("com.mojang.blaze3d.IndexType", "com.mojang.renderpearl.api.pipeline.IndexType")
            replace("com.mojang.blaze3d.PrimitiveTopology", "com.mojang.renderpearl.api.pipeline.PrimitiveTopology")
            replace("com.mojang.blaze3d.buffers.GpuBufferSlice", "com.mojang.renderpearl.api.buffers.GpuBufferSlice")
            replace("com.mojang.blaze3d.buffers.GpuBuffer", "com.mojang.renderpearl.api.buffers.GpuBuffer")
            replace("com.mojang.blaze3d.opengl.GlBuffer", "com.mojang.renderpearl.backend.opengl.GlBuffer")
            replace("com.mojang.blaze3d.opengl.GlCommandEncoder", "com.mojang.renderpearl.backend.opengl.GlCommandEncoder")
            replace("com.mojang.blaze3d.opengl.GlRenderPass", "com.mojang.renderpearl.backend.opengl.GlRenderPass")
            replace("com.mojang.blaze3d.pipeline.BindGroupLayout", "com.mojang.renderpearl.api.pipeline.BindGroupLayout")
            replace("com.mojang.blaze3d.pipeline.BlendFunction", "com.mojang.renderpearl.api.pipeline.BlendFunction")
            replace("com.mojang.blaze3d.pipeline.ColorTargetState", "com.mojang.renderpearl.api.pipeline.ColorTargetState")
            replace("com.mojang.blaze3d.pipeline.DepthStencilState", "com.mojang.renderpearl.api.pipeline.DepthStencilState")
            replace("com.mojang.blaze3d.pipeline.RenderPipeline", "com.mojang.renderpearl.api.pipeline.RenderPipeline")
            replace("com.mojang.blaze3d.platform.BlendFactor", "com.mojang.renderpearl.api.pipeline.BlendFactor")
            replace("com.mojang.blaze3d.platform.CompareOp", "com.mojang.renderpearl.api.pipeline.CompareOp")
            replace("com.mojang.blaze3d.shaders.UniformType", "com.mojang.renderpearl.api.pipeline.UniformType")
            replace("com.mojang.blaze3d.systems.GpuDevice", "com.mojang.renderpearl.api.device.GpuDevice")
            replace("com.mojang.blaze3d.systems.RenderPass", "com.mojang.renderpearl.api.commands.RenderPass")
            replace("com.mojang.blaze3d.textures.AddressMode", "com.mojang.renderpearl.api.textures.AddressMode")
            replace("com.mojang.blaze3d.textures.FilterMode", "com.mojang.renderpearl.api.textures.FilterMode")
            replace("com.mojang.blaze3d.textures.GpuSampler", "com.mojang.renderpearl.api.textures.GpuSampler")
            replace("com.mojang.blaze3d.textures.GpuTextureView", "com.mojang.renderpearl.api.textures.GpuTextureView")
            replace("com.mojang.blaze3d.vertex.VertexFormat", "com.mojang.renderpearl.api.vertex.VertexFormat")
            replace("com.mojang.blaze3d.vertex.@Nullable VertexFormat", "com.mojang.renderpearl.api.vertex.@Nullable VertexFormat")
            replace("com.mojang.blaze3d.pipeline.@Nullable RenderPipeline", "com.mojang.renderpearl.api.pipeline.@Nullable RenderPipeline")
            replace("com.mojang.blaze3d.buffers.@Nullable GpuBuffer", "com.mojang.renderpearl.api.buffers.@Nullable GpuBuffer")
            replace("InputConstants.Type.KEYSYM", "InputConstants.Type.KEYBOARD")
            // The hand-equip state moved out of the renderer into its own class; qualified spellings first.
            replace("net.minecraft.client.renderer.ItemInHandRenderer", "net.minecraft.client.player.FirstPersonHandsAndItems")
            widenerRules.first { it.first == "26.3" }.second.forEach { (from, to) -> replace(from, to) }
            replace("ItemInHandRenderer", "FirstPersonHandsAndItems")
        }
        // 1.21.11 moved these vanilla entity classes into per-animal/per-vehicle packages. The old
        // spellings cannot occur in 1.21.11+ code and the new ones cannot occur below it, so the rename
        // is reversible; the entity facades in dev.golem.core.data extend them by 26.1 name.
        string(current.parsed < "1.21.11") {
            replace("net.minecraft.world.entity.animal.chicken.Chicken", "net.minecraft.world.entity.animal.Chicken")
            replace("net.minecraft.world.entity.animal.squid.Squid", "net.minecraft.world.entity.animal.Squid")
            replace("net.minecraft.world.entity.animal.cow.Cow", "net.minecraft.world.entity.animal.Cow")
            replace("net.minecraft.world.entity.animal.fish.WaterAnimal", "net.minecraft.world.entity.animal.WaterAnimal")
            replace("net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer",
                    "net.minecraft.world.entity.vehicle.AbstractMinecartContainer")
            replace("net.minecraft.world.entity.vehicle.minecart.AbstractMinecart",
                    "net.minecraft.world.entity.vehicle.AbstractMinecart")
            replace("net.minecraft.world.entity.vehicle.minecart.MinecartTNT",
                    "net.minecraft.world.entity.vehicle.MinecartTNT")
            replace("net.minecraft.world.entity.vehicle.boat.Boat", "net.minecraft.world.entity.vehicle.Boat")
        }
    }
}
