package io.quarkiverse.chicory.runtime.wasm;

/**
 * Defines how a Wasm module code can be executed.
 */
public enum ExecutionMode {
    RuntimeCompiler,
    Interpreter;
}
