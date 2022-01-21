package fr.gouv.culture.francetransfert.application.resources.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HealthCheckRepresentation {

	private boolean redis;
	private boolean s3;
	private boolean smtp;
	private boolean config;

}
