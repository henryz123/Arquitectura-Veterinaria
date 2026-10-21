package pe.arquitecturaveterinaria.shared.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorEnvelope> handleApiException(ApiException exception) {
		return ResponseEntity.status(exception.status())
				.body(ErrorEnvelope.of(exception.code(), exception.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException exception) {
		List<FieldIssue> fields = exception.getBindingResult().getFieldErrors().stream()
				.map(this::toFieldIssue)
				.toList();
		return ResponseEntity.unprocessableEntity()
				.body(ErrorEnvelope.of("VALIDATION_FAILED", "La solicitud no es v\u00e1lida.", fields));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ErrorEnvelope> handleConstraintViolation(ConstraintViolationException exception) {
		return ResponseEntity.unprocessableEntity()
				.body(ErrorEnvelope.of("VALIDATION_FAILED", "La solicitud no es válida."));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorEnvelope> handleUnexpected(Exception exception) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ErrorEnvelope.of("INTERNAL_ERROR", "No fue posible completar la solicitud."));
	}

	private FieldIssue toFieldIssue(FieldError error) {
		return new FieldIssue(error.getField(), "Valor no v\u00e1lido.");
	}

	record ErrorEnvelope(ErrorBody error) {
		static ErrorEnvelope of(String code, String message) {
			return of(code, message, List.of());
		}

		static ErrorEnvelope of(String code, String message, List<FieldIssue> fields) {
			return new ErrorEnvelope(new ErrorBody(code, message, UUID.randomUUID().toString(), fields));
		}
	}

	record ErrorBody(String code, String message, String traceId, List<FieldIssue> fieldErrors) {
	}

	record FieldIssue(String field, String message) {
	}
}
