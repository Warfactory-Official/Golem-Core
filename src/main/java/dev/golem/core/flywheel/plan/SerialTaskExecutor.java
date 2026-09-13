package dev.golem.core.flywheel.plan;

//? if >=26.1 {

public enum SerialTaskExecutor implements TaskExecutor {
    INSTANCE;

    @Override
    public int threadCount() {
        return 1;
    }

    @Override
    public void execute(Runnable command) {
        command.run();
    }
}
//?}
