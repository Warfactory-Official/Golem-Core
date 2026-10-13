package dev.golem.core.render.display;

/**
 * The display contexts a vanilla {@code display{}} block names, as a version-neutral enum.
 *
 * <p>This is not {@code ItemDisplayContext}: that enum gained {@code on_shelf} at 26.1, so its membership differs
 * across the range, and it is a Minecraft class where Minecraft must not be needed (the model generator runs
 * headless). These eight are the contexts every supported version reads, and the set the 1.7 item frames have a
 * matrix for.
 *
 * <p>{@code none} is absent on purpose: vanilla never looks a transform up for it, and a model that
 * named one would be stating something nothing applies.
 */
public enum DisplaySlot {

    THIRD_PERSON_RIGHT_HAND("thirdperson_righthand"),
    THIRD_PERSON_LEFT_HAND("thirdperson_lefthand"),
    FIRST_PERSON_RIGHT_HAND("firstperson_righthand"),
    FIRST_PERSON_LEFT_HAND("firstperson_lefthand"),
    HEAD("head"),
    GUI("gui"),
    GROUND("ground"),
    FIXED("fixed");

    private final String serializedName;

    DisplaySlot(String serializedName) {
        this.serializedName = serializedName;
    }

    /** The key this slot has inside a {@code display{}} block. */
    public String serializedName() {
        return serializedName;
    }

    /**
     * Whether {@code ItemTransform.apply} will negate this slot's x translation and its y and z
     * rotations. It does so for the two left hands whatever the file says, so a frame meant for one of
     * them ships pre-negated.
     */
    public boolean leftHand() {
        return this == THIRD_PERSON_LEFT_HAND || this == FIRST_PERSON_LEFT_HAND;
    }

    /** The hand this slot pairs with, or null if it is not a hand slot. */
    public DisplaySlot partner() {
        return switch (this) {
            case THIRD_PERSON_RIGHT_HAND -> THIRD_PERSON_LEFT_HAND;
            case THIRD_PERSON_LEFT_HAND -> THIRD_PERSON_RIGHT_HAND;
            case FIRST_PERSON_RIGHT_HAND -> FIRST_PERSON_LEFT_HAND;
            case FIRST_PERSON_LEFT_HAND -> FIRST_PERSON_RIGHT_HAND;
            default -> null;
        };
    }
}
