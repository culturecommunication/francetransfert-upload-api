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

package fr.gouv.culture.francetransfert.application.resources.upload;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.gouv.culture.francetransfert.application.resources.model.InitialisationInfo;
import fr.gouv.culture.francetransfert.application.resources.model.ValidateData;
import fr.gouv.culture.francetransfert.application.services.ValidationMailService;
import fr.gouv.culture.francetransfert.domain.exceptions.ApiValidationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@CrossOrigin
@RestController
@RequestMapping("/api-public")
@Tag(name = "Public resources")
@Validated
public class PublicResources {

	@Autowired
	private ValidationMailService validationMailService;

	private static final String KEY = "cleAPI";
	private static final String FOR = "X-FORWARDED-FOR";

	@PostMapping("/initPli")
	@Operation(method = "POST", description = "initPli")
	public InitialisationInfo validateCode(HttpServletResponse response, HttpServletRequest request,
			@Valid @RequestBody ValidateData metadata) throws ApiValidationException {

		String headerAddr = request.getHeader(KEY);
		String remoteAddr = request.getHeader(FOR);
		if (remoteAddr == null || "".equals(remoteAddr)) {
			remoteAddr = request.getRemoteAddr();
		}
		return validationMailService.validateMailData(metadata, headerAddr, remoteAddr);
	}

}
