package io.github.shyyyuki.regionbridge.api;

public interface TaskHandle {
    void cancel();
    boolean isCancelled();

    TaskHandle COMPLETED = new TaskHandle() {
        @Override public void cancel() { }
        @Override public boolean isCancelled() { return false; }
    };
}
