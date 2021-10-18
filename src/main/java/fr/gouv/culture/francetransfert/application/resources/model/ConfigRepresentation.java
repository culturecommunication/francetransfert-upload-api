package fr.gouv.culture.francetransfert.application.resources.model;

import java.io.Serializable;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConfigRepresentation implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	List<String> mimeType;
	List<String> extension;

}
