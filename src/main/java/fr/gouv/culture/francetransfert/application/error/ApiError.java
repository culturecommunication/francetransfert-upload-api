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
@AllArgsConstructor
public class ApiError {
    /**
     * Http Status Code
     */
    private int statusCode;
    /**
     * TYPE ERROR
     */
    private String Type;
    /**
     * ID ERROR
     */
    private String id;

}
