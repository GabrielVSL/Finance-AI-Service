package br.com.devlopes.smartbudget.exception;

import br.com.devlopes.smartbudget.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidTransactionException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTransaction(
            InvalidTransactionException ex,
            HttpServletRequest request
    ) {
        log.warn("Violação de regra de domínio em {}: {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "Regra de Negócio Violada",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    @ExceptionHandler(ToolExecutionException.class)
    public ResponseEntity<ApiErrorResponse> handleToolExecution(
            ToolExecutionException ex,
            HttpServletRequest request
    ) {
        if (ex.getCause() instanceof InvalidTransactionException domainEx) {
            return handleInvalidTransaction(domainEx, request);
        }
        log.error("Falha na execução de Tool do Spring AI em {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "Erro ao Executar Ferramenta Financeira",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    @ExceptionHandler(AudioProcessingException.class)
    public ResponseEntity<ApiErrorResponse> handleAudioProcessing(
            AudioProcessingException ex,
            HttpServletRequest request
    ) {
        log.warn("Erro de processamento de áudio em {}: {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Erro no Processamento de Áudio",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler({NonTransientAiException.class, TransientAiException.class, RestClientException.class})
    public ResponseEntity<ApiErrorResponse> handleAiProviderError(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Falha de comunicação com provedor OpenAI em {}: {}", request.getRequestURI(), ex.getMessage());
        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.BAD_GATEWAY.value(),
                "Erro no Provedor de IA",
                "Não foi possível se comunicar com o provedor OpenAI. Verifique sua OPENAI_API_KEY ou tente novamente.",
                request.getRequestURI(),
                safeDetails(ex.getMessage())
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<String> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();

        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Erro de Validação",
                "Os dados enviados na requisição são inválidos.",
                request.getRequestURI(),
                details
        );
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        List<String> details = ex.getConstraintViolations()
                .stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .toList();

        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Erro de Validação",
                "Restrições de validação não foram atendidas.",
                request.getRequestURI(),
                details
        );
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestPartException.class,
            MaxUploadSizeExceededException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequestPayload(
            Exception ex,
            HttpServletRequest request
    ) {
        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Requisição Inválida",
                "Formato de entrada inválido, arquivo acima do limite ou parâmetro obrigatório ausente.",
                request.getRequestURI(),
                safeDetails(ex.getMessage())
        );
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Recurso Não Encontrado",
                "Rota não encontrada. Acesse / para o painel web ou /swagger-ui.html para a documentação da API.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                "Método HTTP Não Suportado",
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Erro inesperado em {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        ApiErrorResponse error = new ApiErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Erro Interno do Servidor",
                "Ocorreu um erro inesperado ao processar a solicitação.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private List<String> safeDetails(String message) {
        return message != null && !message.isBlank() ? List.of(message) : List.of();
    }
}
