package br.com.devlopes.smartbudget.service;

import br.com.devlopes.smartbudget.exception.AudioProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.audio.speech.Speech;
import org.springframework.ai.openai.audio.speech.SpeechPrompt;
import org.springframework.ai.openai.audio.speech.SpeechResponse;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioServiceTest {

    @Mock
    private OpenAiAudioTranscriptionModel transcriptionModel;

    @Mock
    private OpenAiAudioSpeechModel speechModel;

    private AudioService audioService;

    @BeforeEach
    void setUp() {
        audioService = new AudioService(transcriptionModel, speechModel, "whisper-1", "pt", "tts-1");
    }

    @Test
    @DisplayName("Deve transcrever arquivo de áudio válido usando OpenAiAudioTranscriptionModel mockado")
    void shouldTranscribeValidAudioFile() {
        MockMultipartFile audioFile = new MockMultipartFile(
                "audio",
                "comando.mp3",
                "audio/mpeg",
                "fake-audio-bytes".getBytes()
        );
        AudioTranscriptionResponse mockedResponse =
                new AudioTranscriptionResponse(new AudioTranscription("Gastei 50 reais no mercado"));

        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class))).thenReturn(mockedResponse);

        String transcript = audioService.transcribeAudio(audioFile);

        assertThat(transcript).isEqualTo("Gastei 50 reais no mercado");
    }

    @Test
    @DisplayName("Deve atribuir nome de arquivo fallback quando upload vier sem originalFilename")
    void shouldProvideFallbackFilenameWhenMissing() {
        MockMultipartFile audioWithoutFilename = new MockMultipartFile(
                "audio",
                "",
                "audio/mpeg",
                "fake-audio-bytes".getBytes()
        );
        when(transcriptionModel.call(any(AudioTranscriptionPrompt.class)))
                .thenReturn(new AudioTranscriptionResponse(new AudioTranscription("Saldo atual")));

        String transcript = audioService.transcribeAudio(audioWithoutFilename);

        ArgumentCaptor<AudioTranscriptionPrompt> promptCaptor =
                ArgumentCaptor.forClass(AudioTranscriptionPrompt.class);
        verify(transcriptionModel).call(promptCaptor.capture());

        assertThat(promptCaptor.getValue().getInstructions().getFilename()).isEqualTo("audio.mp3");
        assertThat(transcript).isEqualTo("Saldo atual");
    }

    @Test
    @DisplayName("Deve lançar AudioProcessingException quando arquivo de áudio estiver vazio")
    void shouldThrowExceptionWhenAudioFileIsEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile("audio", "vazio.mp3", "audio/mpeg", new byte[0]);

        assertThatThrownBy(() -> audioService.transcribeAudio(emptyFile))
                .isInstanceOf(AudioProcessingException.class)
                .hasMessageContaining("obrigatório");
    }

    @Test
    @DisplayName("Deve rejeitar arquivos com extensão inválida antes de chamar a OpenAI")
    void shouldRejectUnsupportedAudioFormat() {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "audio",
                "documento.pdf",
                "application/pdf",
                "conteudo-invalido".getBytes()
        );

        assertThatThrownBy(() -> audioService.transcribeAudio(pdfFile))
                .isInstanceOf(AudioProcessingException.class)
                .hasMessageContaining("Formato de arquivo não suportado");
    }

    @Test
    @DisplayName("Deve sintetizar resposta em áudio usando OpenAiAudioSpeechModel mockado")
    void shouldSynthesizeSpeech() {
        byte[] expectedBytes = new byte[]{1, 2, 3, 4};
        when(speechModel.call(any(SpeechPrompt.class)))
                .thenReturn(new SpeechResponse(new Speech(expectedBytes)));

        byte[] result = audioService.synthesizeSpeech("Transação registrada com sucesso.");

        assertThat(result).isEqualTo(expectedBytes);
    }
}
