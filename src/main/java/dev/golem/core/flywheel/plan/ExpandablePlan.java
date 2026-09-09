package dev.golem.core.flywheel.plan;

//? if neoforge && >=26.1 {

interface ExpandablePlan<C> extends Plan<C> {

    Plan<C>[] expand(TaskExecutor executor, C context);
}
//?}
