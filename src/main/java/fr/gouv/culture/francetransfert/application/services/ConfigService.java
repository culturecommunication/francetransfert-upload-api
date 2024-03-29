/*
  * Copyright (c) Ministère de la Culture (2022) 
  * 
  * SPDX-License-Identifier: Apache-2.0 
  * License-Filename: LICENSE.txt 
  */

package fr.gouv.culture.francetransfert.application.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import fr.gouv.culture.francetransfert.application.resources.model.ConfigRepresentation;
import fr.gouv.culture.francetransfert.core.services.MimeService;

@Service
public class ConfigService {

	@Value("${extension.name}")
	private List<String> extensionList;

	@Value("${mimetype.front}")
	private List<String> mimeList;

	@Autowired
	MimeService mimeService;

	public ConfigRepresentation getConfig() {
		return ConfigRepresentation.builder().extension(extensionList).mimeType(mimeList).build();
	}

}
