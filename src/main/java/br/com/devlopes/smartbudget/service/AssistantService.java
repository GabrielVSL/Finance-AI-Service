package br.com.devlopes.smartbudget.service;

import br.com.devlopes.smartbudget.dto.AiProviderInfo;
import br.com.devlopes.smartbudget.dto.AssistantResponse;
import br.com.devlopes.smartbudget.exception.InvalidTransactionException;
import br.com.devlopes.smartbudget.model.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.List;

@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final AiProviderRouter aiProviderRouter;
    private final AudioService audioService;

    public AssistantService(AiProviderRouter aiProviderRouter, AudioService audioService) {
        this.aiProviderRouter = aiProviderRouter;
        this.audioService = audioService;
    }

    public List<AiProviderInfo> listAvailableProviders() {
        return aiProviderRouter.listProviders();
    }

    private static final int MAX_MESSAGE_LENGTH = 1000;

    public AssistantResponse processTextCommand(String message, boolean generateAudio, String requestedProvider) {
        if (message == null || message.isBlank()) {
            throw new InvalidTransactionException("O comando de texto não pode ser vazio.");
        }
        String trimmed = message.trim();
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            throw new InvalidTransactionException("O comando de texto deve ter no máximo 1000 caracteres.");
        }
        AiProvider provider = AiProvider.fromStringOrNull(requestedProvider);
        AiProviderRouter.ExecutionResult result = aiProviderRouter.execute(trimmed, provider);
        String audioBase64 = generateAudio ? encodeAudioResponse(result.reply()) : null;
        return AssistantResponse.ofText(result.reply(), audioBase64, result.providerUsed().getId());
    }

    public AssistantResponse processVoiceCommand(
            MultipartFile audioFile,
            boolean generateAudio,
            String requestedProvider
    ) {
        AiProvider provider = AiProvider.fromStringOrNull(requestedProvider);
        String transcribedText = audioService.transcribeAudio(audioFile);
        AiProviderRouter.ExecutionResult result = aiProviderRouter.execute(transcribedText, provider);
        String audioBase64 = generateAudio ? encodeAudioResponse(result.reply()) : null;
        return AssistantResponse.ofVoice(transcribedText, result.reply(), audioBase64, result.providerUsed().getId());
    }

    private String encodeAudioResponse(String aiReply) {
        try {
            byte[] audioBytes = audioService.synthesizeSpeech(aiReply);
            return audioBytes != null && audioBytes.length > 0
                    ? Base64.getEncoder().encodeToString(audioBytes)
                    : null;
        } catch (Exception ex) {
            log.warn("Síntese de voz (TTS) não disponível no provedor atual: {}", ex.getMessage());
            return null;
        }
    }
}
