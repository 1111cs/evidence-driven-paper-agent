package org.example.paperaiagent.knowledge;

@FunctionalInterface
public interface KnowledgeSearchGateway {

    KnowledgeSearchResult search(String query);
}
