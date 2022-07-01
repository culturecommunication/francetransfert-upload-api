package fr.gouv.culture.francetransfert.application.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

import lombok.Data;

@Data
public class ApiErrorFranceTransfert {
	private HttpStatus status;
	private String message;
	private Map<String, String> errors;

	public ApiErrorFranceTransfert(HttpStatus status, String message, Map<String, String> errors) {
		super();
		this.status = status;
		this.message = message;
		this.errors = errors;
	}
}
