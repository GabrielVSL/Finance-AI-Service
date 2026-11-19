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

    private static final String FALLBACK_FILENAME = "audio.mp3";
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "mp3", "mp4", "mpeg", "mpga", "m4a", "wav", "webm", "ogg", "oga", "flac"
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
        validateAudioFile(audioFile);

        try {
            Resource audioResource = resolveAudioResource(audioFile);

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
            throw new AudioProcessingException("Falha ao processar o arquivo de áudio: " + ex.getMessage(), ex);
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
            throw new AudioProcessingException("Falha ao gerar áudio da resposta: " + ex.getMessage(), ex);
        }
    }

    private void validateAudioFile(MultipartFile audioFile) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new AudioProcessingException("O arquivo de áudio é obrigatório e não pode estar vazio.");
        }

        String filename = audioFile.getOriginalFilename();
        if (filename != null && !filename.isBlank() && filename.contains(".")) {
            String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (!SUPPORTED_EXTENSIONS.contains(extension)) {
                throw new AudioProcessingException(
                        "Formato de arquivo não suportado: '." + extension
                                + "'. Formatos aceitos: " + String.join(", ", SUPPORTED_EXTENSIONS) + ".");
            }
        }
    }

    private Resource resolveAudioResource(MultipartFile audioFile) throws IOException {
        String filename = audioFile.getOriginalFilename();
        if (filename != null && !filename.isBlank()) {
            return audioFile.getResource();
        }
        // O endpoint Whisper da OpenAI exige um nome de arquivo com extensão suportada no multipart.
        byte[] bytes = audioFile.getBytes();
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return FALLBACK_FILENAME;
            }
        };
    }
}
