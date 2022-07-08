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

import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ValidateData {
    private String confirmedSenderId;
    
    @NotBlank (message = "TypePli obligatoire")
    private String typePli;

    @NotBlank (message = "SenderEmail obligatoire")
    private String senderEmail;
    private List<String> recipientEmails;
    
    // @Min(1)
	// @Max(90)
	@JsonFormat(pattern = "dd-MM-yyyy")
	private LocalDate expireDelay;
	
    private String password;
    private Boolean zipPassword;
    private Locale language;
    private Boolean protectionArchive;
    @Valid
    private List<FileRepresentation> rootFiles;
    @Valid
    private List<DirectoryRepresentation> rootDirs;

   /* private Boolean passwordGenerated;
    private String message;
    private String subject;
    private Boolean publicLink;
    private int passwordTryCount;
    private String senderId;
    private String senderToken;
*/
    
    

}
