package br.com.devlopes.smartbudget.model;

import br.com.devlopes.smartbudget.exception.InvalidAiProviderException;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum AiProvider {
    GROQ("groq", "Groq (GPT-OSS 120B)"),
    GEMINI("gemini", "Google Gemini (3.8 Flash)");

    private final String id;
    private final String label;

    AiProvider(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public AiProvider alternate() {
        return this == GROQ ? GEMINI : GROQ;
    }

    public static AiProvider fromStringOrNull(String rawProvider) {
        if (rawProvider == null || rawProvider.isBlank()) {
            return null;
        }
        String normalized = rawProvider.trim().toUpperCase(Locale.ROOT);
        try {
            return AiProvider.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            String allowed = Arrays.stream(values())
                    .map(AiProvider::getId)
                    .collect(Collectors.joining(", "));
            String safeInput = rawProvider.trim().replaceAll("[\\r\\n\\t]", " ");
            if (safeInput.length() > 32) {
                safeInput = safeInput.substring(0, 32) + "...";
            }
            throw new InvalidAiProviderException(
                    "Provedor de IA inválido: '" + safeInput + "'. Opções aceitas: " + allowed + ".");
        }
    }
}
