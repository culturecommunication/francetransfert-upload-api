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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ValidateData {
   
   private String typePli;
   @JsonProperty("courrielExpediteur")
   private String senderEmail;
   @JsonProperty("destinataires")
   private List<String> recipientEmails;
   private String message;
   private String objet;
   private PreferencesRepresentation preferences;	
   private boolean envoiMdpDestinataires;

   @Valid
   @JsonProperty("fichiers")
   private List<FileRepresentationApi> rootFiles;
      
}


