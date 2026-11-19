package br.com.devlopes.smartbudget.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @Schema(
                description = "Comando em linguagem natural para o consultor financeiro",
                example = "Gastei R$ 85,50 no almoço hoje na categoria FOOD"
        )
        @NotBlank(message = "A mensagem não pode estar vazia.")
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
        String provider
) {
    public boolean shouldGenerateAudio() {
        return Boolean.TRUE.equals(generateAudio);
    }
}
