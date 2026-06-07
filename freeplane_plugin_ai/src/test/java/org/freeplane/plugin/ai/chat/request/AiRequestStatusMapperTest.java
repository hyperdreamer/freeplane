package org.freeplane.plugin.ai.chat.request;

import org.freeplane.api.ai.AiRequestStatus;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AiRequestStatusMapperTest {

    @Test
    public void classifiesAuthenticationFailures() {
        assertThat(AiRequestStatusMapper.fromFailure(new RuntimeException("HTTP 401 unauthorized")))
            .isEqualTo(AiRequestStatus.AUTHENTICATION_ERROR);
    }

    @Test
    public void classifiesModelUnavailableFailures() {
        assertThat(AiRequestStatusMapper.fromFailure(new RuntimeException("model not found")))
            .isEqualTo(AiRequestStatus.MODEL_UNAVAILABLE);
    }

    @Test
    public void classifiesProviderFailures() {
        assertThat(AiRequestStatusMapper.fromFailure(new RuntimeException("HTTP 503 Service Unavailable")))
            .isEqualTo(AiRequestStatus.PROVIDER_ERROR);
    }

    @Test
    public void classifiesUnknownFailuresAsFailed() {
        assertThat(AiRequestStatusMapper.fromFailure(new RuntimeException("boom")))
            .isEqualTo(AiRequestStatus.FAILED);
    }
}
