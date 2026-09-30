package org.example.paperaiagent.writing.infrastructure.memory;

import org.example.paperaiagent.writing.application.WritingTransactionOperations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@ConditionalOnProperty(name = "paper.writing.repository-type", havingValue = "memory", matchIfMissing = true)
public class InMemoryWritingTransactionOperations implements WritingTransactionOperations {

    @Override
    public <T> T required(Supplier<T> action) {
        return action.get();
    }
}
