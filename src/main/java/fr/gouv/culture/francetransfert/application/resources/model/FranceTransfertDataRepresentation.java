/*
  * Copyright (c) Ministère de la Culture (2022) 
  * 
  * SPDX-License-Identifier: Apache-2.0 
  * License-Filename: LICENSE.txt 
  */

package fr.gouv.culture.francetransfert.application.resources.model;

import java.util.List;
import java.util.Locale;

import javax.validation.Valid;

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
public class FranceTransfertDataRepresentation {
	private String confirmedSenderId;
	private String senderEmail;
	private List<String> recipientEmails;
	private String password;
	private Boolean passwordGenerated;
	private String message;
	private String subject;
	private Boolean publicLink;
	private int passwordTryCount;
	private int expireDelay;
	private String senderId;
	private String senderToken;
	@Valid
	private List<FileRepresentation> rootFiles;
	@Valid
	private List<DirectoryRepresentation> rootDirs;

	private Locale language;
	private Boolean zipPassword;

	private String source;

}
