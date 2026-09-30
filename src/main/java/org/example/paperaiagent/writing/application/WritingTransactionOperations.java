package org.example.paperaiagent.writing.application;

import java.util.function.Supplier;

public interface WritingTransactionOperations {

    <T> T required(Supplier<T> action);
}
