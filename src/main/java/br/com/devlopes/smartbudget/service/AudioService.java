package br.com.devlopes.smartbudget.service;

import br.com.devlopes.smartbudget.exception.AudioProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.audio.speech.SpeechPrompt;
import org.springframework.ai.openai.audio.speech.SpeechResponse;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

@Service
public class AudioService {

    private static final Logger log = LoggerFactory.getLogger(AudioService.class);

    private static final String FALLBACK_EXTENSION = "mp3";
    private static final long MAX_AUDIO_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "mp3", "mp4", "mpeg", "mpga", "m4a", "wav", "webm", "ogg", "oga", "flac"
    );
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "audio/mpeg", "audio/mp3", "audio/mp4", "audio/m4a", "audio/x-m4a",
            "audio/wav", "audio/x-wav", "audio/webm", "audio/ogg", "audio/flac",
            "audio/x-flac", "video/webm", "application/octet-stream"
    );

    private final OpenAiAudioTranscriptionModel transcriptionModel;
    private final OpenAiAudioSpeechModel speechModel;
    private final String whisperModel;
    private final String language;
    private final String ttsModel;

    public AudioService(
            OpenAiAudioTranscriptionModel transcriptionModel,
            OpenAiAudioSpeechModel speechModel,
            @Value("${spring.ai.openai.audio.transcription.options.model:whisper-large-v3-turbo}") String whisperModel,
            @Value("${spring.ai.openai.audio.transcription.options.language:pt}") String language,
            @Value("${spring.ai.openai.audio.speech.options.model:tts-1}") String ttsModel
    ) {
        this.transcriptionModel = transcriptionModel;
        this.speechModel = speechModel;
        this.whisperModel = whisperModel;
        this.language = language;
        this.ttsModel = ttsModel;
    }

    public String transcribeAudio(MultipartFile audioFile) {
        String safeExtension = validateAudioFile(audioFile);

        try {
            Resource audioResource = resolveAudioResource(audioFile, safeExtension);

            OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
                    .model(whisperModel)
                    .language(language)
                    .temperature(0.0f)
                    .build();

            AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(audioResource, options);
            AudioTranscriptionResponse response = transcriptionModel.call(prompt);

            String output = response != null && response.getResult() != null
                    ? response.getResult().getOutput()
                    : null;

            if (output == null || output.isBlank()) {
                throw new AudioProcessingException("Não foi possível transcrever conteúdo do áudio enviado.");
            }
            log.debug("Áudio transcrito com sucesso ({} caracteres)", output.length());
            return output.trim();
        } catch (AudioProcessingException | NonTransientAiException | TransientAiException | RestClientException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Falha interna ao processar arquivo de áudio: {}", ex.getMessage(), ex);
            throw new AudioProcessingException("Falha ao processar o arquivo de áudio enviado.", ex);
        }
    }

    public byte[] synthesizeSpeech(String text) {
        if (text == null || text.isBlank()) {
            throw new AudioProcessingException("O texto para síntese de voz não pode estar vazio.");
        }

        try {
            OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
                    .model(ttsModel)
                    .voice(OpenAiAudioApi.SpeechRequest.Voice.ALLOY)
                    .responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
                    .speed(1.0f)
                    .build();

            SpeechPrompt prompt = new SpeechPrompt(text, options);
            SpeechResponse response = speechModel.call(prompt);

            byte[] audioBytes = response != null && response.getResult() != null
                    ? response.getResult().getOutput()
                    : null;

            if (audioBytes == null || audioBytes.length == 0) {
                throw new AudioProcessingException("O serviço de síntese de voz retornou áudio vazio.");
            }
            return audioBytes;
        } catch (AudioProcessingException | NonTransientAiException | TransientAiException | RestClientException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Falha interna ao sintetizar resposta em áudio: {}", ex.getMessage(), ex);
            throw new AudioProcessingException("Falha ao gerar áudio da resposta.", ex);
        }
    }

    private String validateAudioFile(MultipartFile audioFile) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new AudioProcessingException("O arquivo de áudio é obrigatório e não pode estar vazio.");
        }

        if (audioFile.getSize() > MAX_AUDIO_BYTES) {
            throw new AudioProcessingException("O arquivo de áudio excede o limite máximo de 5 MB.");
        }

        String contentType = audioFile.getContentType();
        if (contentType != null && !contentType.isBlank()) {
            String baseMime = contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
            if (!SUPPORTED_CONTENT_TYPES.contains(baseMime)) {
                throw new AudioProcessingException(
                        "Formato de arquivo não suportado (MIME inválido). Envie MP3, WAV, M4A, WEBM, OGG ou FLAC.");
            }
        }

        String filename = audioFile.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            return FALLBACK_EXTENSION;
        }

        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            throw new AudioProcessingException(
                    "Formato de arquivo não suportado: extensão ausente. Formatos aceitos: "
                            + String.join(", ", SUPPORTED_EXTENSIONS) + ".");
        }

        String extension = filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            String safeExt = extension.replaceAll("[^a-z0-9]", "");
            if (safeExt.length() > 12) {
                safeExt = safeExt.substring(0, 12);
            }
            throw new AudioProcessingException(
                    "Formato de arquivo não suportado: '." + safeExt
                            + "'. Formatos aceitos: " + String.join(", ", SUPPORTED_EXTENSIONS) + ".");
        }
        return extension;
    }

    private Resource resolveAudioResource(MultipartFile audioFile, String safeExtension) throws IOException {
        // Nunca repassa originalFilename do usuário ao provedor externo (previne path traversal / header injection).
        String safeFilename = "audio." + safeExtension;
        byte[] bytes = audioFile.getBytes();
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return safeFilename;
            }
        };
    }
}
