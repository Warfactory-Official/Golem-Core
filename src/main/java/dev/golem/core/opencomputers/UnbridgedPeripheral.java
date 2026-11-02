package dev.golem.core.opencomputers;

//? if opencomputers && computercraft {
/*import dev.golem.core.computer.ComputerComponent;
import dev.golem.core.computer.ComputerCraftPeripheral;
import li.cil.oc.api.network.BlacklistedPeripheral;

/^*
 * A component's CC peripheral with OpenComputers installed too. OC's own CC bridge would wrap it into a second,
 * CC-shaped component on an Adapter; {@link OpenComputers} already serves the component itself.
 ^/
public final class UnbridgedPeripheral extends ComputerCraftPeripheral implements BlacklistedPeripheral {

    public UnbridgedPeripheral(ComputerComponent component) {
        super(component);
    }

    @Override
    public boolean isPeripheralBlacklisted() {
        return true;
    }
}
*///?}
