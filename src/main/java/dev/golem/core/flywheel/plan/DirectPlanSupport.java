package dev.golem.core.flywheel.plan;

//? if neoforge && >=26.1 {

final class DirectPlanSupport {

    private DirectPlanSupport() {
    }

    static <C> boolean supports(Plan<C> plan) {
        return plan instanceof DirectPlan<?> direct && direct.supportsDirectExecution();
    }

    static <C> boolean all(Plan<C>[] plans) {
        for (Plan<C> plan : plans) {
            if (!supports(plan)) {
                return false;
            }
        }
        return true;
    }

    static <C> void execute(Plan<C> plan, TaskExecutor executor, C context) {
        DirectPlan<C> direct = (DirectPlan<C>) plan;
        direct.executeDirect(executor, context);
    }
}
//?}
