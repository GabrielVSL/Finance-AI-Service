package br.com.devlopes.smartbudget.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
        String provider
) {
    public boolean shouldGenerateAudio() {
        return Boolean.TRUE.equals(generateAudio);
    }
}
