package br.com.devlopes.smartbudget.controller;

import br.com.devlopes.smartbudget.exception.InvalidTransactionException;
import br.com.devlopes.smartbudget.model.Transaction;
import br.com.devlopes.smartbudget.model.TransactionCategory;
import br.com.devlopes.smartbudget.model.TransactionType;
import br.com.devlopes.smartbudget.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.audio.speech.Speech;
import org.springframework.ai.openai.audio.speech.SpeechPrompt;
import org.springframework.ai.openai.audio.speech.SpeechResponse;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SmartBudgetApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository transactionRepository;

    @MockitoBean
    private ChatModel chatModel;

    @MockitoBean
    private OpenAiAudioTranscriptionModel transcriptionModel;

    @MockitoBean
    private OpenAiAudioSpeechModel speechModel;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /v3/api-docs deve gerar definição OpenAPI para o Swagger UI sem erros")
    void shouldExposeOpenApiDocs() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/v1/assistant/chat']").exists());
    }

    @Test
    @DisplayName("GET / deve encaminhar para o painel web interativo e rota inexistente deve retornar 404")
    void shouldServeWebUiAndReturn404ForUnknownRoute() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl("index.html"));

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Smart Budget AI")));

        mockMvc.perform(get("/api/v1/rota-inexistente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("/swagger-ui.html")));
    }

    @Test
    @DisplayName("POST /api/v1/assistant/chat deve responder via ChatModel mockado")
    void shouldProcessTextChatCommand() throws Exception {
        ChatResponse mockedChatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage("Despesa de R$ 85,50 registrada na categoria FOOD.")))
        );
        when(chatModel.call(any(Prompt.class))).thenReturn(mockedChatResponse);

        String payload = """
                {
                  "message": "Gastei 85,50 no almoço",
                  "generateAudio": false
                }
                """;

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Despesa de R$ 85,50 registrada na categoria FOOD."))
                .andExpect(jsonPath("$.transcribedText").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("POST /api/v1/assistant/voice deve transcrever áudio, consultar ChatModel e retornar áudio Base64 quando solicitado")
    void shouldProcessVoiceCommandWithOptionalTts() throws Exception {
        MockMultipartFile audioFile = new MockMultipartFile(
                "audio",
                "comando.mp3",
                "audio/mpeg",
                "audio-binario".getBytes()
        );

        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class)))
                .thenReturn(new AudioTranscriptionResponse(new AudioTranscription("Qual meu saldo atual?")));

        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(
                        List.of(new Generation(new AssistantMessage("Seu saldo líquido atual é de R$ 1.500,00.")))
                ));

        when(speechModel.call(any(SpeechPrompt.class)))
                .thenReturn(new SpeechResponse(new Speech("mp3-output".getBytes())));

        mockMvc.perform(multipart("/api/v1/assistant/voice")
                        .file(audioFile)
                        .param("generateAudio", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transcribedText").value("Qual meu saldo atual?"))
                .andExpect(jsonPath("$.response").value("Seu saldo líquido atual é de R$ 1.500,00."))
                .andExpect(jsonPath("$.audioBase64").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/assistant/voice deve retornar 400 Bad Request para formato de arquivo inválido")
    void shouldReturnBadRequestForUnsupportedAudioFile() throws Exception {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "audio",
                "planilha.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dados".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/assistant/voice").file(invalidFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Erro no Processamento de Áudio"));
    }

    @Test
    @DisplayName("GET /api/v1/transactions e /summary devem auditar transações e alerta de déficit")
    void shouldListTransactionsAndSummary() throws Exception {
        transactionRepository.save(new Transaction("Salário", new BigDecimal("2500.00"), TransactionType.INCOME, TransactionCategory.SALARY));
        transactionRepository.save(new Transaction("Curso Java", new BigDecimal("3000.00"), TransactionType.EXPENSE, TransactionCategory.TECH));

        mockMvc.perform(get("/api/v1/transactions").param("category", "TECH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].description").value("Curso Java"));

        mockMvc.perform(get("/api/v1/transactions/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(2500.00))
                .andExpect(jsonPath("$.totalExpense").value(3000.00))
                .andExpect(jsonPath("$.netBalance").value(-500.00))
                .andExpect(jsonPath("$.deficitAlert").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/transactions deve retornar 400 via @RestControllerAdvice para dados inválidos")
    void shouldReturnBadRequestForInvalidTransaction() throws Exception {
        String invalidPayload = """
                {
                  "description": "",
                  "amount": 0,
                  "type": "EXPENSE",
                  "category": "FOOD"
                }
                """;

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Erro de Validação"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @DisplayName("POST /api/v1/assistant/chat deve retornar 422 Unprocessable Entity quando regra de domínio for violada")
    void shouldReturnUnprocessableEntityWhenDomainRuleFails() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new InvalidTransactionException("O valor da transação deve ser maior que zero."));

        String payload = """
                {
                  "message": "Registra gasto de -50 reais"
                }
                """;

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Regra de Negócio Violada"));
    }

    @Test
    @DisplayName("POST /api/v1/assistant/chat deve retornar 502 Bad Gateway quando o provedor OpenAI falhar")
    void shouldReturnBadGatewayWhenOpenAiFails() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new NonTransientAiException("401 - Incorrect API key provided: demo-key"));

        String payload = """
                {
                  "message": "Qual é o meu saldo?"
                }
                """;

        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.error").value("Erro no Provedor de IA"));
    }
}
