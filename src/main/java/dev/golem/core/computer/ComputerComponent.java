package dev.golem.core.computer;

/**
 * One block's face to computers, served to CC: Tweaked as a peripheral and to OpenComputers as a component by
 * {@link Computers}. Its methods are its {@link ComputerMethod}s unless {@link #methods} says otherwise.
 */
public interface ComputerComponent {

    /** The peripheral type or component name ({@code peripheral.find("ntm_geiger")}, {@code component.ntm_geiger}). */
    String type();

    /** The object it stands for, usually a block entity; two components with the same target and type are equal. */
    Object target();

    default ComponentMethods methods() {
        return ComponentMethods.of(getClass());
    }
}
