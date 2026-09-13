package dev.golem.core.flywheel.visual;

//? if >=26.1 {

/** Visual work that must publish before asynchronous backend maintenance begins. */
public interface SynchronousDynamicVisual extends Visual {
    void beginFrame(DynamicVisual.Context context);
}
//?}
