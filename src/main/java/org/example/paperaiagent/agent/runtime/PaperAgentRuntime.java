package org.example.paperaiagent.agent.runtime;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PaperAgentRuntime {

    Flux<AgentRuntimeEvent> stream(AgentRunCommand command);

    default Mono<AgentRunResult> run(AgentRunCommand command) {
        return stream(command)
                .collectList()
                .map(events -> AgentRunResult.fromEvents(command.sessionId(), events));
    }
}
