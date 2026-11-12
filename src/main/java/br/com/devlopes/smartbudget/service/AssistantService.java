package br.com.devlopes.smartbudget.service;

import br.com.devlopes.smartbudget.dto.AssistantResponse;
import br.com.devlopes.smartbudget.exception.InvalidTransactionException;
import br.com.devlopes.smartbudget.tools.FinancialTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;

@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private static final String SYSTEM_PROMPT = """
            Você é o Smart Budget AI, um Consultor Financeiro Pessoal inteligente, preciso e amigável.
            Responda sempre em português do Brasil de forma clara, estruturada e objetiva.

            Regras de comportamento:
            1. Sempre que o usuário relatar um ganho/receita ou gasto/despesa, use a ferramenta `registrarTransacao`
               com descrição clara, valor numérico positivo, tipo (`INCOME` ou `EXPENSE`) e a categoria mais adequada
               (`FOOD`, `TRANSPORT`, `SALARY`, `TECH`, `HOUSING`, `HEALTH`, `ENTERTAINMENT` ou `OTHER`).
            2. Sempre que o usuário pedir para consultar lançamentos, gastos ou receitas por categoria ou tipo,
               use a ferramenta `listarTransacoesPorCategoria`.
            3. Sempre que o usuário perguntar sobre saldo, total de receitas, total de despesas, resumo do mês
               ou situação financeira, use a ferramenta `calcularResumoFinanceiro`.
            4. Se `calcularResumoFinanceiro` indicar `deficitAlert = true`, destaque o alerta de déficit com empatia
               e sugira brevemente um próximo passo prático de controle financeiro.
            5. Nunca invente valores ou transações que não vieram das ferramentas.
            """;

    private final ChatClient chatClient;
    private final AudioService audioService;

    public AssistantService(
            ChatClient.Builder chatClientBuilder,
            FinancialTools financialTools,
            AudioService audioService
    ) {
        this.chatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(financialTools)
                .build();
        this.audioService = audioService;
    }

    public AssistantResponse processTextCommand(String message, boolean generateAudio) {
        if (message == null || message.isBlank()) {
            throw new InvalidTransactionException("O comando de texto não pode ser vazio.");
        }
        String aiReply = executePrompt(message.trim());
        String audioBase64 = generateAudio ? encodeAudioResponse(aiReply) : null;
        return AssistantResponse.ofText(aiReply, audioBase64);
    }

    public AssistantResponse processVoiceCommand(MultipartFile audioFile, boolean generateAudio) {
        String transcribedText = audioService.transcribeAudio(audioFile);
        String aiReply = executePrompt(transcribedText);
        String audioBase64 = generateAudio ? encodeAudioResponse(aiReply) : null;
        return AssistantResponse.ofVoice(transcribedText, aiReply, audioBase64);
    }

    private String executePrompt(String userMessage) {
        log.debug("Enviando prompt ao ChatClient: {}", userMessage);
        String reply = chatClient.prompt()
                .user(userMessage)
                .call()
                .content();
        return reply != null && !reply.isBlank()
                ? reply
                : "Operação processada pelo consultor financeiro.";
    }

    private String encodeAudioResponse(String aiReply) {
        byte[] audioBytes = audioService.synthesizeSpeech(aiReply);
        return Base64.getEncoder().encodeToString(audioBytes);
    }
}
