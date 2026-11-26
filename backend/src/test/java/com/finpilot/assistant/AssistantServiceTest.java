package com.finpilot.assistant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finpilot.ai.LlmException;
import com.finpilot.ai.LlmProvider;
import com.finpilot.audit.AuditService;
import com.finpilot.security.AuthenticatedUser;
import com.finpilot.user.Role;
import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

/** A (possibly prompt-injected) LLM returning hostile SQL must never reach the database. */
class AssistantServiceTest {
    private final LlmProvider llm = mock(LlmProvider.class);
    private final ReadOnlyQueryExecutor executor = mock(ReadOnlyQueryExecutor.class);
    private final AssistantService service = new AssistantService(llm, new SqlValidator(), executor,
            mock(AssistantQueryRepository.class), mock(AuditService.class), Clock.systemUTC(), ZoneId.of("Asia/Kolkata"));
    private final AuthenticatedUser user = new AuthenticatedUser(1, "a@b.c", Role.USER);

    @ParameterizedTest
    @ValueSource(strings = {
            "DROP TABLE users",
            "SELECT * FROM nl_transactions; DELETE FROM transactions",
            "```sql\nSELECT * FROM users\n```",
            "SELECT * FROM nl_transactions WHERE 1=1 -- ignore user filter",
            "SELECT set_config('finpilot.user_id','2',false), * FROM nl_transactions",
            "SELECT * FROM pg_catalog.pg_user",
            "Sure! Here is the query: SELECT * FROM nl_transactions"})
    void hostileModelOutputIsRejectedBeforeExecution(String modelOutput) {
        when(llm.complete(any())).thenReturn(modelOutput);
        when(llm.name()).thenReturn("mock");
        assertThatThrownBy(() -> service.ask(user, "anything")).isInstanceOf(com.finpilot.common.ApiException.class);
        verify(executor, never()).run(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void providerOutageSurfacesAsBadGateway() {
        when(llm.complete(any())).thenThrow(new LlmException("Claude request failed: timeout"));
        assertThatThrownBy(() -> service.ask(user, "how much did I spend")).isInstanceOf(LlmException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.BAD_GATEWAY);
    }
}
