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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import fr.gouv.culture.francetransfert.application.resources.model.FileRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.InitialisationInfo;
import fr.gouv.culture.francetransfert.application.resources.model.PackageInfoRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.StatusInfo;
import fr.gouv.culture.francetransfert.application.resources.model.ValidateData;
import fr.gouv.culture.francetransfert.application.resources.model.ValidateUpload;
import fr.gouv.culture.francetransfert.application.services.ValidationMailService;
import fr.gouv.culture.francetransfert.core.exception.MetaloadException;
import fr.gouv.culture.francetransfert.core.exception.StatException;
import fr.gouv.culture.francetransfert.core.exception.StorageException;
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

	// ---
	@PostMapping("/chargementPli")
	@Operation(method = "POST", description = "chargementPli")
	public InitialisationInfo uploadData(HttpServletResponse response, HttpServletRequest request,
			@RequestParam("numMorceauFichier") int flowChunkNumber,
			@RequestParam("totalMorceauxFichier") int flowTotalChunks,
			@RequestParam("tailleMorceauFichier") long flowChunkSize, @RequestParam("tailleFichier") long flowTotalSize,
			@RequestParam("idFichier") String flowIdentifier, @RequestParam("nomFichier") String flowFilename,
			@RequestParam("fichier") MultipartFile file, @RequestParam("idPli") String enclosureId,
			@RequestParam("courrielExpediteur") String senderId)
			throws ApiValidationException, MetaloadException, StorageException {

		ValidateUpload metadata = new ValidateUpload();
		FileRepresentation rootFile = new FileRepresentation();

		rootFile.setFid(flowIdentifier);
		rootFile.setSize(flowTotalSize);
		rootFile.setName(flowFilename);

		metadata.setEnclosureId(enclosureId);
		metadata.setFlowChunkNumber(flowChunkNumber);
		metadata.setSenderEmail(senderId);
		metadata.setFlowChunkSize(flowChunkSize);
		metadata.setFlowTotalChunks(flowTotalChunks);
		metadata.setFichier(file);
		metadata.setRootFiles(rootFile);

		String headerAddr = request.getHeader(KEY);
		String remoteAddr = request.getHeader(FOR);
		if (remoteAddr == null || "".equals(remoteAddr)) {
			remoteAddr = request.getRemoteAddr();
		}
		return validationMailService.validateUpload(metadata, headerAddr, remoteAddr, senderId, flowIdentifier);
	}

	@GetMapping("/statutPli")
	@Operation(method = "GET", description = "statutPli")
	public InitialisationInfo packageStatus(HttpServletResponse response, HttpServletRequest request,
			@RequestParam(value = "idPli", required = false) String enclosureId,
			@RequestParam(value = "courrielExpediteur", required = false) String senderMail)
			throws ApiValidationException, MetaloadException {

		String headerAddr = request.getHeader(KEY);
		String remoteAddr = request.getHeader(FOR);
		if (remoteAddr == null || "".equals(remoteAddr)) {
			remoteAddr = request.getRemoteAddr();
		}
		StatusInfo metadata = new StatusInfo(enclosureId, senderMail);
		return validationMailService.getStatusPli(metadata, headerAddr, remoteAddr);

	}

	@GetMapping("/donneesPli")
	@Operation(method = "GET", description = "donneesPli")
	public PackageInfoRepresentation packageInfo(HttpServletResponse response, HttpServletRequest request,
			@RequestParam(value = "idPli", required = false) String enclosureId,
			@RequestParam(value = "courrielExpediteur", required = false) String senderMail)
			throws ApiValidationException, MetaloadException, StatException {

		String headerAddr = request.getHeader(KEY);
		String remoteAddr = request.getHeader(FOR);
		if (remoteAddr == null || "".equals(remoteAddr)) {
			remoteAddr = request.getRemoteAddr();
		}

		StatusInfo metadata = new StatusInfo();
		metadata.setEnclosureId(enclosureId);
		metadata.setSenderMail(senderMail);
		return validationMailService.getInfoPli(metadata, headerAddr, remoteAddr);

	}
}
