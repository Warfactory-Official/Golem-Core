package dev.golem.core.flywheel.plan;

//? if >=26.1 {

interface DirectPlan<C> extends Plan<C> {

    boolean supportsDirectExecution();

    void executeDirect(TaskExecutor executor, C context);
}
//?}
