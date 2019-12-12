package fr.gouv.culture.francetransfert.application.error;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;


/**
 * The type Api error.
 * @author Open Group
 * @since 1.0.0
 */
@Data
public class ApiError {
    /**
     * Http Status Code
     */
    private int statusCode;
    /**
     * Api Message Error
     */
    private String message;
    /**
     * Api list field Error
     */
    private List<String> errors;

    public ApiError(int statusCode, String message, List<String> errors) {
        super();
        this.statusCode = statusCode;
        this.message = message;
        this.errors = errors;
    }

    public ApiError(int statusCode, String message) {
        super();
        this.statusCode = statusCode;
        this.message = message;
    }

}
