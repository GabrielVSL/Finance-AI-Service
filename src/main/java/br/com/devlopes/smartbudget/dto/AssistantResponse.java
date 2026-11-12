package br.com.devlopes.smartbudget.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssistantResponse(
        @Schema(description = "Texto transcrito do áudio enviado (presente apenas no fluxo de voz)")
        String transcribedText,

        @Schema(description = "Resposta gerada pelo Consultor Financeiro Pessoal via Spring AI")
        String response,

        @Schema(description = "Áudio da resposta em MP3 codificado em Base64 (opcional)")
        String audioBase64,

        @Schema(description = "Data e hora do processamento")
        LocalDateTime timestamp
) {
    public static AssistantResponse ofText(String response, String audioBase64) {
        return new AssistantResponse(null, response, audioBase64, LocalDateTime.now());
    }

    public static AssistantResponse ofVoice(String transcribedText, String response, String audioBase64) {
        return new AssistantResponse(transcribedText, response, audioBase64, LocalDateTime.now());
    }
}
