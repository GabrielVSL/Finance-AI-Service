package br.com.devlopes.smartbudget.service;

import br.com.devlopes.smartbudget.model.AiProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.TransientAiException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiProviderRouterTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient groqClient;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient geminiClient;

    @Test
    @DisplayName("Deve rotear chamada para o provedor explicitamente escolhido (Gemini)")
    void shouldRouteToExplicitlyRequestedProvider() {
        when(geminiClient.prompt().user("Qual meu saldo?").call().content())
                .thenReturn("Resposta gerada pelo Gemini.");

        AiProviderRouter router = buildRouter(true, true, AiProvider.GROQ, true);

        AiProviderRouter.ExecutionResult result = router.execute("Qual meu saldo?", AiProvider.GEMINI);

        assertThat(result.providerUsed()).isEqualTo(AiProvider.GEMINI);
        assertThat(result.reply()).isEqualTo("Resposta gerada pelo Gemini.");
        verifyNoInteractions(groqClient);
    }

    @Test
    @DisplayName("Deve acionar fallback automático para Gemini quando Groq falhar e Gemini estiver configurado")
    void shouldFallbackFromGroqToGeminiWhenConfigured() {
        when(groqClient.prompt().user("Gastei 50 no almoço").call().content())
                .thenThrow(new TransientAiException("429 Rate Limit no Groq"));
        when(geminiClient.prompt().user("Gastei 50 no almoço").call().content())
                .thenReturn("Registrado via fallback Gemini.");

        AiProviderRouter router = buildRouter(true, true, AiProvider.GROQ, true);

        AiProviderRouter.ExecutionResult result = router.execute("Gastei 50 no almoço", AiProvider.GROQ);

        assertThat(result.providerUsed()).isEqualTo(AiProvider.GEMINI);
        assertThat(result.reply()).isEqualTo("Registrado via fallback Gemini.");
        verify(groqClient.prompt().user("Gastei 50 no almoço").call()).content();
    }

    @Test
    @DisplayName("Não deve tentar fallback quando o provedor alternativo não possuir chave configurada")
    void shouldPropagateErrorWhenAlternateProviderIsNotConfigured() {
        when(groqClient.prompt().user("Saldo").call().content())
                .thenThrow(new TransientAiException("Falha no Groq"));

        AiProviderRouter router = buildRouter(true, false, AiProvider.GROQ, true);

        assertThatThrownBy(() -> router.execute("Saldo", AiProvider.GROQ))
                .isInstanceOf(TransientAiException.class)
                .hasMessageContaining("Falha no Groq");

        verifyNoInteractions(geminiClient);
    }

    private AiProviderRouter buildRouter(
            boolean groqConfigured,
            boolean geminiConfigured,
            AiProvider defaultProvider,
            boolean fallbackEnabled
    ) {
        return new AiProviderRouter(
                Map.of(AiProvider.GROQ, groqClient, AiProvider.GEMINI, geminiClient),
                Map.of(AiProvider.GROQ, groqConfigured, AiProvider.GEMINI, geminiConfigured),
                Map.of(AiProvider.GROQ, "llama-3.3-70b-versatile", AiProvider.GEMINI, "gemini-2.5-flash"),
                defaultProvider,
                fallbackEnabled
        );
    }
}
