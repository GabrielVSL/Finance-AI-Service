package br.com.devlopes.smartbudget.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AiProviderInfo(
        @Schema(description = "Identificador do provedor para envio em requisições", example = "groq")
        String id,

        @Schema(description = "Nome amigável do provedor para exibição na UI", example = "Groq (Llama 3.3 70B)")
        String label,

        @Schema(description = "Modelo LLM configurado para este provedor", example = "llama-3.3-70b-versatile")
        String model,

        @Schema(description = "Indica se existe uma chave de API real configurada (diferente de demo-key)")
        boolean configured,

        @Schema(description = "Indica se este é o provedor padrão selecionado automaticamente")
        boolean defaultProvider,

        @Schema(description = "Indica se este provedor suporta transcrição de voz (Whisper STT)")
        boolean supportsVoiceTranscription
) {
}
