package dev.golem.core.flywheel.backend;

//? if >=26.1 {

import dev.golem.core.flywheel.DrawManager;

public interface Backend {

    String name();

    int priority();

    boolean supported();

    DrawManager createDrawManager();
}
//?}
