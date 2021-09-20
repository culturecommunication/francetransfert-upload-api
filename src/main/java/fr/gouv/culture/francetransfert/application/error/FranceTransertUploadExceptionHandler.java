package fr.gouv.culture.francetransfert.application.error;


import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTDecodeException;
import fr.gouv.culture.francetransfert.domain.exceptions.*;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import redis.clients.jedis.exceptions.JedisDataException;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The type StarterKit exception handler.
 * @author Open Group
 * @since 1.0.0
 */
@ControllerAdvice
public class FranceTransertUploadExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FranceTransertUploadExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {

        return new ResponseEntity<>(new ApiError(status.value(), "NOT FOUND", "NOT_FOUND"), status);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        ApiErrorFranceTransfert apiError = new ApiErrorFranceTransfert(HttpStatus.BAD_REQUEST, "MethodArgumentNotValidException", errors);
        return new ResponseEntity<Object>(
                apiError, new HttpHeaders(), apiError.getStatus());
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    protected ResponseEntity<Object> handleUnauthorizedAccessException(UnauthorizedAccessException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put("token", "Invalid Token");
        ApiErrorFranceTransfert apiError = new ApiErrorFranceTransfert(HttpStatus.UNAUTHORIZED, "UnauthorizedAccessException", errors);
        return new ResponseEntity<Object>(
                apiError, new HttpHeaders(), apiError.getStatus());
    }


    @ExceptionHandler(DomainNotFoundException.class)
    public ResponseEntity<Object>  handleDomainNotFoundException(Exception ex)  {
        String errorId = RedisUtils.generateGUID();
        LOGGER.error("Type: {} -- id: {} -- message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(), errorId, ex.getMessage());
        return new ResponseEntity<>(new ApiError(HttpStatus.UNAUTHORIZED.value(), ErrorEnum.TECHNICAL_ERROR.getValue(), errorId), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(BusinessDomainException.class)
    public ResponseEntity<Object>  handleBusinessDomainException(Exception ex)  {
        return generateError(ex, ErrorEnum.TECHNICAL_ERROR.getValue());
    }

    @ExceptionHandler(ExtensionNotFoundException.class)
    public ResponseEntity<Object>  handleExtensionNotFoundException(Exception ex)  {
        return generateError(ex, ErrorEnum.TECHNICAL_ERROR.getValue());
    }

    @ExceptionHandler(FlowChunkNotExistException.class)
    public ResponseEntity<Object>  handleFlowChunkNotExistException(Exception ex)  {
        LOGGER.error(ex.getMessage());
        String errorId = RedisUtils.generateGUID();
        LOGGER.error("Type: {} -- id: {} -- message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(), errorId, ex.getMessage());
        return new ResponseEntity<>(new ApiError(HttpStatus.EXPECTATION_FAILED.value(), ErrorEnum.TECHNICAL_ERROR.getValue(), errorId), HttpStatus.EXPECTATION_FAILED);
    }

    @ExceptionHandler(UnauthorizedMailAddressException.class)
    public ResponseEntity<Object>  handleUnauthorizedMailAddressException(Exception ex)  {
        return generateError(ex, ErrorEnum.TECHNICAL_ERROR.getValue());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object>  handleValidationEmailException(ConstraintViolationException ex, WebRequest request)  {
        Map<String,String> errors = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String field = null;
            for (Path.Node node : violation.getPropertyPath()) {
                field = node.getName();
            }
            errors.put(field,violation.getMessage());
        }
        ApiErrorFranceTransfert apiError = new ApiErrorFranceTransfert(HttpStatus.BAD_REQUEST, ex.getLocalizedMessage(), errors);
        return new ResponseEntity<Object>(
                apiError, new HttpHeaders(), apiError.getStatus());
    }

    @ExceptionHandler(DateUpdateException.class)
    public ResponseEntity<Object>  handleValidationEmailException(DateUpdateException ex, WebRequest request)  {
        LOGGER.error("Exception handler DateUpdateException" + ex.getMessage(), ex);
        ApiErrorFranceTransfert apiError = new ApiErrorFranceTransfert(HttpStatus.BAD_REQUEST, ex.getLocalizedMessage(), null);
        return new ResponseEntity<Object>(
                apiError, new HttpHeaders(), apiError.getStatus());
    }

    @ExceptionHandler({AccessDeniedException.class,JWTDecodeException.class,JWTCreationException.class, })
    public ResponseEntity<Object>  handleUnauthorizedException(Exception ex)  {
        LOGGER.error(ex.getMessage());
        String errorId = RedisUtils.generateGUID();
        LOGGER.error("Type: {} -- id: {} -- message: {}", ErrorEnum.TECHNICAL_ERROR.getValue(), errorId, ex.getMessage());
        return new ResponseEntity<>(new ApiError(HttpStatus.UNAUTHORIZED.value(), ErrorEnum.TECHNICAL_ERROR.getValue(), errorId), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(JedisDataException.class)
    public ResponseEntity<Object>  handleRedisException(Exception ex)  {
        return generateError(ex, ErrorEnum.TECHNICAL_ERROR.getValue());
    }


    @ExceptionHandler(AmazonS3Exception.class)
    public ResponseEntity<Object>  handleAmazonS3Exception(Exception ex)  {
        return generateError(ex, ErrorEnum.TECHNICAL_ERROR.getValue());
    }

    @ExceptionHandler(SdkClientException.class)
    public ResponseEntity<Object>  handleSdkClientException(Exception ex)  {
        return generateError(ex, ErrorEnum.TECHNICAL_ERROR.getValue());
    }


    @ExceptionHandler(AmazonServiceException.class)
    public ResponseEntity<Object>  handleAmazonServiceException(Exception ex)  {
        return generateError(ex, ErrorEnum.TECHNICAL_ERROR.getValue());
    }

    @ExceptionHandler(UploadExcption.class)
    public ResponseEntity<Object>  handleUploadExcption(UploadExcption ex)  {
        LOGGER.error("Type: {} -- id: {} -- message: {}", ex.getType(), ex.getId(), ex.getMessage());
        return new ResponseEntity<>(new ApiError(HttpStatus.BAD_REQUEST.value(), ex.getType(), ex.getId()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConfirmationCodeException.class)
    public ResponseEntity<Object>  handleConfirmationCodeExcption(ConfirmationCodeException ex)  {
        LOGGER.error("Type: {} -- id: {} -- message: {}", ex.getType(), ex.getId(), ex.getMessage());
        return new ResponseEntity<>(new WrongCodeError(HttpStatus.UNAUTHORIZED.value(), ex.getCount(),ex.getMessage()), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(MaxTryException.class)
    public ResponseEntity<Object>  handleMaxTryException(MaxTryException ex)  {
        LOGGER.error("message: {}",ex.getMessage());
        return new ResponseEntity<>(new ApiError(HttpStatus.UNAUTHORIZED.value(), "", ex.getMessage()), HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<Object> generateError(Exception ex, String errorType) {
        String errorId = UUID.randomUUID().toString();
        LOGGER.error("Type: {} -- id: {} -- message: {}", errorType, errorId, ex.getMessage());
        return new ResponseEntity<>(new ApiError(HttpStatus.BAD_REQUEST.value(), errorType, errorId), HttpStatus.BAD_REQUEST);
    }

}
