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

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import fr.gouv.culture.francetransfert.application.error.UnauthorizedAccessException;
import fr.gouv.culture.francetransfert.application.resources.model.AddNewRecipientRequest;
import fr.gouv.culture.francetransfert.application.resources.model.ConfigRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.DateUpdateRequest;
import fr.gouv.culture.francetransfert.application.resources.model.DeleteRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.DeleteRequest;
import fr.gouv.culture.francetransfert.application.resources.model.EnclosureRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FileInfoRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.InitialisationInfo;
import fr.gouv.culture.francetransfert.application.resources.model.StatusRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.ValidateCodeResponse;
import fr.gouv.culture.francetransfert.application.resources.model.ValidateData;
import fr.gouv.culture.francetransfert.application.services.ConfigService;
import fr.gouv.culture.francetransfert.application.services.ConfirmationServices;
import fr.gouv.culture.francetransfert.application.services.RateServices;
import fr.gouv.culture.francetransfert.application.services.UploadServices;
import fr.gouv.culture.francetransfert.application.services.ValidationMailService;
import fr.gouv.culture.francetransfert.core.exception.MetaloadException;
import fr.gouv.culture.francetransfert.core.exception.StorageException;
import fr.gouv.culture.francetransfert.core.model.FormulaireContactData;
import fr.gouv.culture.francetransfert.core.model.RateRepresentation;
import fr.gouv.culture.francetransfert.core.utils.RedisUtils;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadException;
import fr.gouv.culture.francetransfert.validator.EmailsFranceTransfert;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@CrossOrigin
@RestController
@RequestMapping("/api-public/upload-module")
@Tag(name = "Valid resources")
@Validated
public class ValidResources {

	@Autowired
	private ValidationMailService validationMailService;

	@Autowired
	private UploadServices uploadServices;
	
	private static final String KEY = "X-API-KEY";
	private static final String FOR = "X-FORWARDED-FOR";
	private static final String CodeStatutPli = "000-INI";
	private static final String LibelleStatutPli = "Pli créé avec ses métadonnées uniquement";
	
	@PostMapping("/validate-data")
	@Operation(method = "POST", description = "validate data")
	public List<InitialisationInfo> validateCode(HttpServletResponse response, HttpServletRequest request,
			@Valid @RequestBody ValidateData metadata) {

		List<InitialisationInfo> listStatutPlis = new ArrayList<InitialisationInfo>();

		if (request != null) {
			String headerAddr = request.getHeader(KEY);
			InitialisationInfo headerAddressChecked = validationMailService.validDomainHeader(headerAddr,
					metadata.getSenderEmail());
			listStatutPlis.add(headerAddressChecked);

			String remoteAddr = request.getHeader(FOR);
			if (remoteAddr == null || "".equals(remoteAddr)) {
				remoteAddr = request.getRemoteAddr();
			}
			InitialisationInfo ipAddressChecked = validationMailService.validIpAddress(headerAddr, remoteAddr);
			listStatutPlis.add(ipAddressChecked);

			listStatutPlis.removeIf(Objects::isNull);

			if (listStatutPlis == null || listStatutPlis.isEmpty()) {
				InitialisationInfo passwordChecked = validationMailService.validPassword(metadata.getPassword());
				InitialisationInfo typeChecked = validationMailService.validType(metadata.getTypePli());
				if (typeChecked == null) {
					InitialisationInfo recipientSenderChecked = validationMailService.validRecipientSender(
							metadata.getSenderEmail(), metadata.getRecipientEmails(), metadata.getTypePli());
					listStatutPlis.add(recipientSenderChecked);
				}

				InitialisationInfo langueCourrielChecked = validationMailService.validLangueCourriel(metadata.getLanguage());
				InitialisationInfo dateFormatChecked = validationMailService.validDateFormat(metadata.getExpireDelay());
				InitialisationInfo datePeriodChecked = validationMailService.validPeriodFormat(metadata.getExpireDelay());
				InitialisationInfo protectionArchiveChecked = validationMailService
						.validProtectionArchive(metadata.getProtectionArchive());
				InitialisationInfo idNameDirsChecked = validationMailService.validIdNameDirs(metadata.getRootDirs());
				InitialisationInfo idNameFilesChecked = validationMailService.validIdNameFiles(metadata.getRootFiles());
				InitialisationInfo pathDirsChecked = validationMailService.validPathDirs(metadata.getRootDirs());
				InitialisationInfo pathFilesChecked = validationMailService.validPathFiles(metadata.getRootFiles());
				InitialisationInfo SizePackageChecked = validationMailService.validSizePackage(metadata.getRootFiles(),
						metadata.getRootDirs());

				listStatutPlis.add(passwordChecked);
				listStatutPlis.add(typeChecked);
				listStatutPlis.add(langueCourrielChecked);
				listStatutPlis.add(dateFormatChecked);
				listStatutPlis.add(datePeriodChecked);
				listStatutPlis.add(protectionArchiveChecked);
				listStatutPlis.add(idNameDirsChecked);
				listStatutPlis.add(idNameFilesChecked);
				listStatutPlis.add(pathDirsChecked);
				listStatutPlis.add(pathFilesChecked);
				listStatutPlis.add(SizePackageChecked);
				listStatutPlis.removeIf(Objects::isNull);

				if (listStatutPlis == null || listStatutPlis.isEmpty()) {
					InitialisationInfo validPackage = new InitialisationInfo();
					StatusRepresentation statutPli = new StatusRepresentation();
					statutPli.setCodeStatutPli(CodeStatutPli);
					statutPli.setLibelleStatutPli(LibelleStatutPli);
					validPackage.setStatutPli(statutPli);

					LocalDate now = LocalDate.now();
					int daysBetween = (int) ChronoUnit.DAYS.between(metadata.getExpireDelay(), now);

					FranceTransfertDataRepresentation data = FranceTransfertDataRepresentation.builder()
							.password(metadata.getPassword()).senderEmail(metadata.getSenderEmail())
							.recipientEmails(metadata.getRecipientEmails()).expireDelay(daysBetween)
							.zipPassword(metadata.getZipPassword()).language(metadata.getLanguage())
							.rootFiles(metadata.getRootFiles()).rootDirs(metadata.getRootDirs())
							.passwordGenerated(false).publicLink(false).build();

					EnclosureRepresentation dataRedis = uploadServices.createMetaDataEnclosureInRedis(data);
					validPackage.setIdPli(dataRedis.getEnclosureId());

					listStatutPlis.add(validPackage);
				}

			}
		}

		return listStatutPlis;
	}

}
