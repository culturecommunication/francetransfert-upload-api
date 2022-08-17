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

import java.time.LocalDate;
import java.util.List;

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
public class PackageInfoRepresentation {
	
	private String idPli;
	private StatusRepresentation statutPli;
	private String typePli;
	private String courrielExpediteur;
	private List<RecipientInfoApi> destinataires;	
	private String objet;
	private String message;
	private PreferencesRepresentation preferences;
	private List<FileRepresentationApi> fichiers;
	private String lienTelechargementPublic;

	

	
}
