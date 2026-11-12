package br.com.devlopes.smartbudget.controller;

import br.com.devlopes.smartbudget.dto.AssistantResponse;
import br.com.devlopes.smartbudget.dto.ChatRequest;
import br.com.devlopes.smartbudget.service.AssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "Assistente Financeiro IA", description = "Endpoints de voz e texto integrados ao Spring AI com Tool Calling")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping(value = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Enviar comando por voz",
            description = "Recebe um arquivo de áudio (MP3, WAV, M4A, WEBM, OGG, FLAC), transcreve via Whisper, executa o ChatClient com Tool Calling e opcionalmente retorna a resposta em áudio Base64."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Áudio transcrito e processado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Arquivo de áudio ausente, vazio ou em formato não suportado"),
            @ApiResponse(responseCode = "422", description = "Regra de negócio financeira violada durante execução da Tool"),
            @ApiResponse(responseCode = "502", description = "Falha de comunicação ou autenticação com o provedor OpenAI")
    })
    public ResponseEntity<AssistantResponse> processVoice(
            @Parameter(description = "Arquivo de áudio com o comando financeiro")
            @RequestPart("audio") MultipartFile audio,
            @Parameter(description = "Se true, sintetiza a resposta da IA em áudio MP3 Base64")
            @RequestParam(value = "generateAudio", defaultValue = "false") boolean generateAudio
    ) {
        return ResponseEntity.ok(assistantService.processVoiceCommand(audio, generateAudio));
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Enviar comando por texto",
            description = "Recebe um comando em linguagem natural via JSON para testar rapidamente o Consultor Financeiro e o Tool Calling sem precisar enviar arquivos de áudio."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comando processado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Payload JSON inválido ou mensagem vazia"),
            @ApiResponse(responseCode = "422", description = "Regra de negócio financeira violada durante execução da Tool"),
            @ApiResponse(responseCode = "502", description = "Falha de comunicação ou autenticação com o provedor OpenAI")
    })
    public ResponseEntity<AssistantResponse> processChat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(assistantService.processTextCommand(request.message(), request.shouldGenerateAudio()));
    }
}
