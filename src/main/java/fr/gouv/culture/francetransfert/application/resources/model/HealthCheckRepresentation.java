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
	private String smtpUuid;
	private int smtpDelay;
	private int smtpPending;
	private boolean smtpDelayOk;

	public boolean isFtError() {
		if (redis == false || s3 == false || smtp == false || config == false || smtpDelayOk == false) {
			return true;
		}
		return false;
	}

}
