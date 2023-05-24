/* 
 * Copyright (c) Ministère de la Culture (2022) 
 * 
 * SPDX-License-Identifier: Apache-2.0 
 * License-Filename: LICENSE.txt 
 */

/*
  * Copyright (c) Ministère de la Culture (2022) 
  * 
  * SPDX-License-Identifier: Apache-2.0 
  * License-Filename: LICENSE.txt 
  */

package fr.gouv.culture.francetransfert.application.resources.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ValidateData {

	private String typePli;
	@JsonProperty("courrielExpediteur")
	private String senderEmail;
	@Size(max = 101)
	@JsonProperty("destinataires")
	private List<String> recipientEmails;
	@Size(max = 2500)
	private String message;
	@Size(max = 255)
	private String objet;
	private PreferencesRepresentation preferences;

	@JsonProperty("fichiers")
	private List<FileRepresentationApi> rootFiles;

}
