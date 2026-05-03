package com.bdmage.mage_backend.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.bdmage.mage_backend.dto.ApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		Map<String, FieldError> fieldErrors = new LinkedHashMap<>();

		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			String field = fieldError.getField();
			FieldError existingFieldError = fieldErrors.get(field);
			if (existingFieldError == null || isHigherPriorityValidationError(fieldError, existingFieldError)) {
				fieldErrors.put(field, fieldError);
			}
		}

		Map<String, String> details = new LinkedHashMap<>();
		fieldErrors.forEach((field, fieldError) -> details.put(field, fieldError.getDefaultMessage()));

		return buildResponse(
				HttpStatus.BAD_REQUEST,
				"VALIDATION_ERROR",
				"Request validation failed.",
				details,
				request.getRequestURI());
	}

	private static boolean isHigherPriorityValidationError(FieldError fieldError, FieldError existingFieldError) {
		return validationPriority(fieldError.getCode()) < validationPriority(existingFieldError.getCode());
	}

	private static int validationPriority(String constraintCode) {
		if ("NotBlank".equals(constraintCode)) {
			return 0;
		}
		if ("NotNull".equals(constraintCode)) {
			return 1;
		}
		if ("Email".equals(constraintCode)) {
			return 2;
		}
		if ("Size".equals(constraintCode)) {
			return 3;
		}
		return 4;
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(
			HttpMessageNotReadableException ex,
			HttpServletRequest request) {
		return buildResponse(
				HttpStatus.BAD_REQUEST,
				"MALFORMED_REQUEST",
				"Request body could not be parsed.",
				Map.of(),
				request.getRequestURI());
	}

	@ExceptionHandler(InvalidGoogleTokenException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidGoogleToken(
			InvalidGoogleTokenException ex,
			HttpServletRequest request) {
		return buildResponse(
				HttpStatus.UNAUTHORIZED,
				"INVALID_GOOGLE_TOKEN",
				ex.getMessage(),
				Map.of(),
				request.getRequestURI());
	}

	@ExceptionHandler(InvalidLocalCredentialsException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidLocalCredentials(
			InvalidLocalCredentialsException ex,
			HttpServletRequest request) {
		return buildResponse(
				HttpStatus.UNAUTHORIZED,
				"INVALID_LOCAL_CREDENTIALS",
				ex.getMessage(),
				Map.of(),
				request.getRequestURI());
	}

	@ExceptionHandler(AccountLinkRequiredException.class)
	ResponseEntity<ApiErrorResponse> handleAccountLinkRequired(
			AccountLinkRequiredException ex,
			HttpServletRequest request) {
		return buildResponse(
				HttpStatus.CONFLICT,
				"ACCOUNT_LINK_REQUIRED",
				ex.getMessage(),
				Map.of(),
				request.getRequestURI());
	}

	@ExceptionHandler(AccountConflictException.class)
	ResponseEntity<ApiErrorResponse> handleAccountConflict(
			AccountConflictException ex,
			HttpServletRequest request) {
		return buildResponse(
				HttpStatus.CONFLICT,
				"ACCOUNT_CONFLICT",
				ex.getMessage(),
				Map.of(),
				request.getRequestURI());
	}

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	ResponseEntity<ApiErrorResponse> handleEmailAlreadyRegistered(
			EmailAlreadyRegisteredException ex,
			HttpServletRequest request) {
		return buildResponse(
				HttpStatus.CONFLICT,
				"EMAIL_ALREADY_REGISTERED",
				ex.getMessage(),
				Map.of(),
				request.getRequestURI());
	}

	@ExceptionHandler(TagAlreadyExistsException.class)
	ResponseEntity<ApiErrorResponse> handleTagAlreadyExists(
			TagAlreadyExistsException ex,
			HttpServletRequest request) {
		return buildResponse(
				HttpStatus.CONFLICT,
				"TAG_ALREADY_EXISTS",
				ex.getMessage(),
				Map.of(),
				request.getRequestURI());
	}

	@ExceptionHandler(SceneTagAlreadyExistsException.class)
	ResponseEntity<ApiErrorResponse> handleSceneTagAlreadyExists(
			SceneTagAlreadyExistsException ex,
			HttpServletRequest request) {
		return buildResponse(
				HttpStatus.CONFLICT,
				"SCENE_TAG_ALREADY_EXISTS",
				ex.getMessage(),
				Map.of(),
				request.getRequestURI());
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
			InvalidCredentialsException ex,
			HttpServletRequest request) {
		return buildResponse(
				HttpStatus.UNAUTHORIZED,
				"INVALID_CREDENTIALS",
				ex.getMessage(),
				Map.of(),
				request.getRequestURI());
	}

	@ExceptionHandler(InvalidCurrentPasswordException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidCurrentPassword(
			InvalidCurrentPasswordException ex,
			HttpServletRequest request) {
		return buildResponse(
				HttpStatus.BAD_REQUEST,
				"INVALID_CURRENT_PASSWORD",
				ex.getMessage(),
				Map.of(),
				request.getRequestURI());
	}

	@ExceptionHandler(LocalPasswordChangeUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleLocalPasswordChangeUnavailable(
			LocalPasswordChangeUnavailableException ex,
			HttpServletRequest request) {
		return buildResponse(
				HttpStatus.CONFLICT,
				"LOCAL_PASSWORD_CHANGE_UNAVAILABLE",
				ex.getMessage(),
				Map.of(),
				request.getRequestURI());
	}

	@ExceptionHandler(AuthenticationRequiredException.class)
	ResponseEntity<ApiErrorResponse> handleAuthenticationRequired(
			AuthenticationRequiredException ex,
			HttpServletRequest request) {
		return buildResponse(
				HttpStatus.UNAUTHORIZED,
				"AUTHENTICATION_REQUIRED",
				ex.getMessage(),
				Map.of(),
				request.getRequestURI());
	}

	@ExceptionHandler(InvalidAuthenticationTokenException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidAuthenticationToken(
			InvalidAuthenticationTokenException ex,
			HttpServletRequest request) {
		return buildResponse(
				HttpStatus.UNAUTHORIZED,
				"INVALID_AUTH_TOKEN",
				ex.getMessage(),
				Map.of(),
				request.getRequestURI());
	}

	@ExceptionHandler(GoogleAuthenticationUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleGoogleAuthenticationUnavailable(
			GoogleAuthenticationUnavailableException ex,
			HttpServletRequest request) {
		return buildResponse(
				HttpStatus.SERVICE_UNAVAILABLE,
				"GOOGLE_AUTH_UNAVAILABLE",
				ex.getMessage(),
				Map.of(),
				request.getRequestURI());
	}
	
	@ExceptionHandler(SceneNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleSceneNotFound(
			SceneNotFoundException ex,
			HttpServletRequest request) {
		return buildResponse(
			HttpStatus.NOT_FOUND,
			"SCENE_NOT_FOUND",
			ex.getMessage(),
			Map.of(),
			request.getRequestURI());
	}

	@ExceptionHandler(PlaylistNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handlePlaylistNotFound(
			PlaylistNotFoundException ex,
			HttpServletRequest request) {
		return buildResponse(
			HttpStatus.NOT_FOUND,
			"PLAYLIST_NOT_FOUND",
			ex.getMessage(),
			Map.of(),
			request.getRequestURI());
	}

	@ExceptionHandler(SceneCommentNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleSceneCommentNotFound(
			SceneCommentNotFoundException ex,
			HttpServletRequest request) {
		return buildResponse(
			HttpStatus.NOT_FOUND,
			"COMMENT_NOT_FOUND",
			ex.getMessage(),
			Map.of(),
			request.getRequestURI());
	}

	@ExceptionHandler(InvalidSceneCommentParentException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidSceneCommentParent(
			InvalidSceneCommentParentException ex,
			HttpServletRequest request) {
		return buildResponse(
			HttpStatus.BAD_REQUEST,
			"INVALID_COMMENT_PARENT",
			ex.getMessage(),
			Map.of(),
			request.getRequestURI());
	}

	@ExceptionHandler(SceneForbiddenException.class)
	ResponseEntity<ApiErrorResponse> handleForbiddenException(
			SceneForbiddenException ex,
			HttpServletRequest request) {
		return buildResponse(
			HttpStatus.FORBIDDEN,
			"SCENE_FORBIDDEN",
			ex.getMessage(),
			Map.of(),
			request.getRequestURI());
	}

	@ExceptionHandler(TagNotFoundException.class)
	ResponseEntity<ApiErrorResponse> handleTagNotFound(
			TagNotFoundException ex,
			HttpServletRequest request) {
		return buildResponse(
			HttpStatus.NOT_FOUND,
			"TAG_NOT_FOUND",
			ex.getMessage(),
			Map.of(),
			request.getRequestURI());
	}

	@ExceptionHandler(SceneOwnershipRequiredException.class)
	ResponseEntity<ApiErrorResponse> handleSceneOwnershipRequired(
			SceneOwnershipRequiredException ex,
			HttpServletRequest request) {
		return buildResponse(
			HttpStatus.FORBIDDEN,
			"SCENE_OWNERSHIP_REQUIRED",
			ex.getMessage(),
			Map.of(),
			request.getRequestURI());
	}

	@ExceptionHandler(InvalidThumbnailException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidThumbnail(
			InvalidThumbnailException ex,
			HttpServletRequest request) {
		return buildResponse(
			HttpStatus.BAD_REQUEST,
			"INVALID_THUMBNAIL",
			ex.getMessage(),
			Map.of(),
			request.getRequestURI());
	}

	@ExceptionHandler(ThumbnailStorageUnavailableException.class)
	ResponseEntity<ApiErrorResponse> handleThumbnailStorageUnavailable(
			ThumbnailStorageUnavailableException ex,
			HttpServletRequest request) {
		return buildResponse(
			HttpStatus.SERVICE_UNAVAILABLE,
			"THUMBNAIL_STORAGE_UNAVAILABLE",
			ex.getMessage(),
			Map.of(),
			request.getRequestURI());
	}

	@ExceptionHandler(InvalidCommentSortException.class)
	ResponseEntity<ApiErrorResponse> handleInvalidCommentSort(
        	InvalidCommentSortException ex,
        	HttpServletRequest request) {
    	return buildResponse(
            HttpStatus.BAD_REQUEST,
            "INVALID_COMMENT_SORT",
            ex.getMessage(),
            Map.of(),
            request.getRequestURI());
	}

	private static ResponseEntity<ApiErrorResponse> buildResponse(
			HttpStatus status,
			String code,
			String message,
			Map<String, String> details,
			String path) {
		Map<String, String> responseDetails = details.isEmpty() ? null : details;

		return ResponseEntity.status(status)
				.body(new ApiErrorResponse(
						code,
						message,
						responseDetails,
						path,
						Instant.now()));
	}
}
