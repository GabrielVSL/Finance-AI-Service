package br.com.devlopes.smartbudget.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatRequest(
        @Schema(
                description = "Comando em linguagem natural para o consultor financeiro",
                example = "Gastei R$ 85,50 no almoço hoje na categoria FOOD"
        )
        @NotBlank(message = "A mensagem não pode estar vazia.")
        @Size(max = 1000, message = "A mensagem deve ter no máximo 1000 caracteres.")
        String message,

        @Schema(
                description = "Indica se a resposta também deve ser sintetizada em áudio (Base64 MP3)",
                example = "false",
                defaultValue = "false"
        )
        Boolean generateAudio,

        @Schema(
                description = "Provedor de IA opcional: 'groq' ou 'gemini'. Se omitido, usa o provedor padrão configurado.",
                example = "groq",
                allowableValues = {"groq", "gemini"}
        )
        @Size(max = 32, message = "Nome do provedor de IA excede o tamanho permitido.")
        String provider,

        @Schema(description = "Histórico recente opcional da conversa para manter contexto entre mensagens (até 10 itens)")
        @Size(max = 10, message = "O histórico de contexto suporta no máximo 10 mensagens.")
        @Valid
        List<ChatHistoryEntry> history
) {
    public ChatRequest(String message, Boolean generateAudio, String provider) {
        this(message, generateAudio, provider, List.of());
    }

    public boolean shouldGenerateAudio() {
        return Boolean.TRUE.equals(generateAudio);
    }

    public List<ChatHistoryEntry> safeHistory() {
        return history == null ? List.of() : history;
    }

    public record ChatHistoryEntry(
            @NotBlank(message = "O papel da mensagem no histórico é obrigatório.")
            @Size(max = 16, message = "Papel inválido.")
            String role,

            @NotBlank(message = "O conteúdo da mensagem no histórico não pode ser vazio.")
            @Size(max = 600, message = "Cada item do histórico deve ter no máximo 600 caracteres.")
            String content
    ) {
    }
}
