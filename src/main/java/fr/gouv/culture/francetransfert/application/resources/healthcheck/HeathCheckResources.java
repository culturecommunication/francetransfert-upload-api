package fr.gouv.culture.francetransfert.application.resources.healthcheck;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.gouv.culture.francetransfert.application.error.UnauthorizedAccessException;
import fr.gouv.culture.francetransfert.application.resources.model.HealthCheckRepresentation;
import fr.gouv.culture.francetransfert.application.services.HealthCheckService;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@CrossOrigin
@RestController
@RequestMapping("/api-private/heathcheck")
@Tag(name = "HeathCheck")
public class HeathCheckResources {

	private static final Logger LOGGER = LoggerFactory.getLogger(HeathCheckResources.class);

	@Autowired
	private HealthCheckService healthCheckService;

	@Value("${healthcheck.api.key:''}")
	String apiKeyConfig;

	@GetMapping("/")
	@Operation(method = "Get", description = "HeathCheck")
	public HealthCheckRepresentation healthCheck(@RequestHeader("X-Api-Key") String apiKey) throws UploadException {
		if (apiKeyConfig.equals(apiKey)) {
			return healthCheckService.healthCheck();
		}
		throw new UnauthorizedAccessException("Invalid Header");
	}

}
