package org.freeplane.features.ai.code;

public enum CodeState {
    NO_CODE,
    EDITED,
    RUNNABLE,
    INVALID_SCRIPT,
    INVALID_ARGUMENTS_JSON,
    WAITING_FOR_USER_RUN,
    USER_RUN_CANCELLED,
    RUN_SUCCEEDED,
    RUN_FAILED
}
