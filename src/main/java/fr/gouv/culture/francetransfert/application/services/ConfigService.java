package fr.gouv.culture.francetransfert.application.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import fr.gouv.culture.francetransfert.application.resources.model.ConfigRepresentation;

@Service
public class ConfigService {

	@Value("${extension.name}")
	private List<String> extensionList;

	@Value("${mimetype.list}")
	private List<String> mimeList;

	public ConfigRepresentation getConfig() {
		return ConfigRepresentation.builder().extension(extensionList).mimeType(mimeList).build();
	}

}
