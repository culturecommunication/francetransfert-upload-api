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

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonProperty;

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
public class RecipientInfoApi {

	public RecipientInfoApi(RecipientInfo recInfo) {
		recipientMail = recInfo.getRecipientMail();
		numberOfDownloadPerRecipient = recInfo.getNumberOfDownloadPerRecipient();
		downloadDates = recInfo.getDownloadDates();
	}

	@JsonProperty("courrielDestinataire")
	private String recipientMail;
	@JsonProperty("nbTelechargements")
	private int numberOfDownloadPerRecipient;
	@JsonProperty("datesTelechargement")
	private ArrayList<String> downloadDates;

}
