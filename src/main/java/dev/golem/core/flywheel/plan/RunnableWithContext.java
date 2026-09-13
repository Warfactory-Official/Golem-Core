package dev.golem.core.flywheel.plan;

//? if >=26.1 {

@FunctionalInterface
public interface RunnableWithContext<C> {

    void run(C context);
}
//?}
