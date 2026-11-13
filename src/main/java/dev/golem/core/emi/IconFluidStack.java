package dev.golem.core.emi;

//? if emi {
/*import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.render.EmiRender;
import dev.emi.emi.api.render.EmiTooltipComponents;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.FluidEmiStack;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.config.FluidUnit;
import dev.golem.core.fluid.FluidUnits;
import dev.golem.core.viewer.FluidIcons;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/^*
 * A fluid drawn and described as a mod's {@link FluidIcons} icon. It equals EMI's own stack of the same fluid, so
 * other mods' recipes with that fluid and this one find each other.
 ^/
final class IconFluidStack extends FluidEmiStack {

    private final Fluid fluid;
    private final ItemStack icon;

    IconFluidStack(Fluid fluid, long amount, ItemStack icon) {
        super(fluid);
        this.fluid = fluid;
        this.icon = icon;
        setAmount(amount);
    }

    /^* The icon stack as an EMI fluid stack, or null when it stands for no fluid. ^/
    static IconFluidStack fromIcon(ItemStack stack) {
        FluidIcons.Amount amount = FluidIcons.fluidOf(stack);
        if (amount == null) return null;
        return new IconFluidStack(amount.fluid(), amount.milliBuckets() * FluidUnit.BUCKET / FluidUnits.MB_PER_BUCKET, stack);
    }

    ItemStack icon() {
        return icon;
    }

    @Override
    public EmiStack copy() {
        IconFluidStack copy = new IconFluidStack(fluid, getAmount(), icon);
        copy.setChance(getChance());
        copy.setRemainder(getRemainder().copy());
        copy.comparison = comparison;
        return copy;
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float delta, int flags) {
        if ((flags & RENDER_ICON) != 0) graphics.renderItem(icon, x, y);
        if ((flags & RENDER_REMAINDER) != 0) EmiRender.renderRemainderIcon(this, graphics, x, y);
    }

    @Override
    public List<Component> getTooltipText() {
        return Screen.getTooltipFromItem(Minecraft.getInstance(), icon);
    }

    /^*
     * The icon's own lines, which carry the amount, then the fluid's mod name once. EMI already ends every item tooltip
     * with the item's mod name (its {@code ItemStackMixin}), which this replaces.
     ^/
    @Override
    public List<ClientTooltipComponent> getTooltip() {
        List<Component> lines = new ArrayList<>(getTooltipText());
        String itemMod = EmiUtil.getModName(BuiltInRegistries.ITEM.getKey(icon.getItem()).getNamespace());
        if (!lines.isEmpty() && lines.get(lines.size() - 1).getString().equals(itemMod)) lines.remove(lines.size() - 1);
        List<ClientTooltipComponent> tooltip = new ArrayList<>();
        for (Component line : lines) tooltip.add(ClientTooltipComponent.create(line.getVisualOrderText()));
        EmiTooltipComponents.appendModName(tooltip, BuiltInRegistries.FLUID.getKey(fluid).getNamespace());
        return tooltip;
    }

    /^* Favourites and the recipe tree save the fluid and the icon, so the icon comes back with them. ^/
    static final class Serializer implements EmiIngredientSerializer<IconFluidStack> {
        @Override
        public String getType() {
            return "golemcore:fluid_icon";
        }

        @Override
        public JsonElement serialize(IconFluidStack stack) {
            JsonObject json = new JsonObject();
            json.addProperty("type", getType());
            json.add("fluid", EmiIngredientSerializer.getSerialized(EmiStack.of(stack.fluid, stack.getAmount())));
            json.add("icon", EmiIngredientSerializer.getSerialized(EmiStack.of(stack.icon)));
            return json;
        }

        @Override
        public EmiIngredient deserialize(JsonElement element) {
            JsonObject json = element.getAsJsonObject();
            EmiIngredient fluid = EmiIngredientSerializer.getDeserialized(json.get("fluid"));
            EmiIngredient icon = EmiIngredientSerializer.getDeserialized(json.get("icon"));
            if (fluid.isEmpty() || icon.isEmpty()) return fluid;
            EmiStack stack = fluid.getEmiStacks().get(0);
            return new IconFluidStack((Fluid) stack.getKey(), stack.getAmount(), icon.getEmiStacks().get(0).getItemStack());
        }
    }
}
*///?}
