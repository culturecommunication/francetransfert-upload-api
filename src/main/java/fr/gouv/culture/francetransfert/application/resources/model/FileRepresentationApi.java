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

import javax.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class FileRepresentationApi extends DataRepresentationApi {

	public FileRepresentationApi(FileRepresentation file) {
		String flowIdentifier = file.getName().replaceAll("\\W", "");
		flowIdentifier = file.getSize() + "-" + flowIdentifier;
		fid = flowIdentifier;
		size = file.getSize();
		name = file.getName();
	}

	@NotBlank
	@JsonProperty("idFichier")
	private String fid;
	@JsonProperty("tailleFichier")
	private long size;
}
