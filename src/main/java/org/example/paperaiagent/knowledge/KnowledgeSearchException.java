package org.example.paperaiagent.knowledge;

public final class KnowledgeSearchException extends RuntimeException {

    private final KnowledgeSearchErrorCode code;
    private final Integer httpStatus;

    public KnowledgeSearchException(KnowledgeSearchErrorCode code, String message) {
        this(code, message, null, null);
    }

    public KnowledgeSearchException(KnowledgeSearchErrorCode code, String message, Throwable cause) {
        this(code, message, null, cause);
    }

    public KnowledgeSearchException(
            KnowledgeSearchErrorCode code,
            String message,
            Integer httpStatus,
            Throwable cause
    ) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public KnowledgeSearchErrorCode code() {
        return code;
    }

    public Integer httpStatus() {
        return httpStatus;
    }
}
