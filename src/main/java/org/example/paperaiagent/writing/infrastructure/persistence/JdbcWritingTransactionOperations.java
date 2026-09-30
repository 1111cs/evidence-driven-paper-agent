package org.example.paperaiagent.writing.infrastructure.persistence;

import org.example.paperaiagent.writing.application.WritingTransactionOperations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
@ConditionalOnProperty(name = "paper.writing.repository-type", havingValue = "jdbc")
public class JdbcWritingTransactionOperations implements WritingTransactionOperations {

    private final TransactionTemplate transactionTemplate;

    public JdbcWritingTransactionOperations(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T required(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }
}
