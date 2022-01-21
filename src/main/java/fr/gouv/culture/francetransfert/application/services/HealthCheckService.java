package fr.gouv.culture.francetransfert.application.services;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import fr.gouv.culture.francetransfert.application.resources.model.HealthCheckRepresentation;
import fr.gouv.culture.francetransfert.core.services.RedisManager;
import fr.gouv.culture.francetransfert.core.services.StorageManager;

@Service
public class HealthCheckService {

	@Autowired
	RestTemplate restTemplate;

	@Autowired
	RedisManager redisManager;

	@Autowired
	StorageManager storageManager;

	@Value("${healthcheck.config.url:''}")
	String configUrl;

	@Value("${healthcheck.smtp.host:''}")
	String smtpHost;

	@Value("${healthcheck.smtp.port:''}")
	int smtpPort;

	private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckService.class);

	public HealthCheckRepresentation healthCheck() {

		boolean redis = false;
		boolean s3 = false;
		boolean config = false;
		boolean smtp = false;

		try {
			redis = redisManager.ping().equalsIgnoreCase("PONG");
		} catch (Exception e) {
			LOGGER.error("Error while checking redis", e);
		}

		try {
			s3 = storageManager.healthCheckQuery();
		} catch (Exception e) {
			LOGGER.error("Error while checking s3", e);
		}

		try {
			config = restTemplate.exchange(configUrl, HttpMethod.GET, null, String.class).getBody()
					.contains("mimeType");
		} catch (Exception e) {
			LOGGER.error("Error while checking config", e);
		}

		try {
			smtp = checkMail();
		} catch (Exception e) {
			LOGGER.error("Error while checking smtp", e);
		}

		return HealthCheckRepresentation.builder().smtp(smtp).s3(s3).redis(redis).config(config).build();

	}

	private boolean checkMail() throws Exception {
		Socket s = new Socket();
		s.setReuseAddress(true);
		SocketAddress sa = new InetSocketAddress(smtpHost, smtpPort);
		s.connect(sa, 1000);
		s.close();
		return true;
	}

}
