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

package fr.gouv.culture.francetransfert.application.services;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;


import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;

import fr.gouv.culture.francetransfert.application.error.UnauthorizedAccessException;
import fr.gouv.culture.francetransfert.application.resources.model.DirectoryRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FileRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.InitialisationInfo;
import fr.gouv.culture.francetransfert.core.enums.TypePliEnum;
import fr.gouv.culture.francetransfert.core.enums.ValidationErrorEnum;
import fr.gouv.culture.francetransfert.core.utils.Base64CryptoService;
import fr.gouv.culture.francetransfert.core.utils.StringUploadUtils;
import fr.gouv.culture.francetransfert.domain.utils.FileUtils;


@Service
public class ValidationMailService {

	private static final Logger LOGGER = LoggerFactory.getLogger(ValidationMailService.class);


	@Value("${upload.limit}")
	private long uploadLimitSize;

	@Value("${upload.file.limit}")
	private long uploadFileLimitSize;


	@Value("#{${api.key}}")
	Map<String, Map<String, String[]>> apiKey;

	@Autowired
	private Base64CryptoService base64CryptoService;

	@Autowired
	private StringUploadUtils stringUploadUtils;





	public InitialisationInfo validPassword(String password) {
		InitialisationInfo passwordInfo = null;
		if (!base64CryptoService.validatePassword(password.trim())) {
			passwordInfo = new InitialisationInfo();
			passwordInfo.setCodeChamp(ValidationErrorEnum.FT012.getCodeChamp());
			passwordInfo.setNumErreur(ValidationErrorEnum.FT012.getNumErreur());
			passwordInfo.setLibelleErreur(ValidationErrorEnum.FT012.getLibelleErreur());
		}
		return passwordInfo;
	}

	public InitialisationInfo validType(String typePli) {

		InitialisationInfo typeInfo = null;
		String[] typeArray = new String[] { TypePliEnum.COURRIEL.getKey(), TypePliEnum.LINK.getKey() };
		List<String> typeList = new ArrayList<>(Arrays.asList(typeArray));
		if (typePli != null && !typePli.isEmpty()) {
			if (!typeList.contains(typePli)) {
				typeInfo = new InitialisationInfo();
				typeInfo.setCodeChamp(ValidationErrorEnum.FT02.getCodeChamp());
				typeInfo.setNumErreur(ValidationErrorEnum.FT02.getNumErreur());
				typeInfo.setLibelleErreur(ValidationErrorEnum.FT02.getLibelleErreur());
			}
		} else {
			typeInfo = new InitialisationInfo();
			typeInfo.setCodeChamp(ValidationErrorEnum.FT01.getCodeChamp());
			typeInfo.setNumErreur(ValidationErrorEnum.FT01.getNumErreur());
			typeInfo.setLibelleErreur(ValidationErrorEnum.FT01.getLibelleErreur());
		}

		return typeInfo;
	}

	
	public InitialisationInfo validRecipientSender(String senderEmail, List<String> recipientEmails, String typePli) {

		InitialisationInfo recipientSenderInfo = null;

		if (StringUtils.isNotEmpty(senderEmail)) {
			if (stringUploadUtils.isValidEmail(senderEmail)) {
					if (typePli.equals(TypePliEnum.COURRIEL.getKey())) {
						if (CollectionUtils.isNotEmpty(recipientEmails)) {
							boolean validFormatRecipients = false;

							validFormatRecipients = recipientEmails.stream().noneMatch(x -> {
								return !stringUploadUtils.isValidEmail(x);
							});
							if (!validFormatRecipients) {
								recipientSenderInfo = new InitialisationInfo();
								recipientSenderInfo.setCodeChamp(ValidationErrorEnum.FT09.getCodeChamp());
								recipientSenderInfo.setNumErreur(ValidationErrorEnum.FT09.getNumErreur());
								recipientSenderInfo.setLibelleErreur(ValidationErrorEnum.FT09.getLibelleErreur());
							}
						} else {
							recipientSenderInfo = new InitialisationInfo();
							recipientSenderInfo.setCodeChamp(ValidationErrorEnum.FT07.getCodeChamp());
							recipientSenderInfo.setNumErreur(ValidationErrorEnum.FT07.getNumErreur());
							recipientSenderInfo.setLibelleErreur(ValidationErrorEnum.FT07.getLibelleErreur());
						}
					}		
			} else {
				recipientSenderInfo = new InitialisationInfo();
				recipientSenderInfo.setCodeChamp(ValidationErrorEnum.FT06.getCodeChamp());
				recipientSenderInfo.setNumErreur(ValidationErrorEnum.FT06.getNumErreur());
				recipientSenderInfo.setLibelleErreur(ValidationErrorEnum.FT06.getLibelleErreur());
			}
		} else {
			recipientSenderInfo = new InitialisationInfo();
			recipientSenderInfo.setCodeChamp(ValidationErrorEnum.FT05.getCodeChamp());
			recipientSenderInfo.setNumErreur(ValidationErrorEnum.FT05.getNumErreur());
			recipientSenderInfo.setLibelleErreur(ValidationErrorEnum.FT05.getLibelleErreur());
		}

		return recipientSenderInfo;

	}

	public InitialisationInfo validLangueCourriel(Locale langue) {

		InitialisationInfo langueCourrielInfo = null;
		String[] langueArray = new String[] { Locale.FRANCE.toString(), Locale.UK.toString() };
		List<String> langueList = new ArrayList<>(Arrays.asList(langueArray));
		if (!langueList.contains(langue.toString())) {
			langueCourrielInfo = new InitialisationInfo();
			langueCourrielInfo.setCodeChamp(ValidationErrorEnum.FT014.getCodeChamp());
			langueCourrielInfo.setNumErreur(ValidationErrorEnum.FT014.getNumErreur());
			langueCourrielInfo.setLibelleErreur(ValidationErrorEnum.FT014.getLibelleErreur());
		}
		return langueCourrielInfo;
	}

	public InitialisationInfo validDateFormat(LocalDate expireDelay) {

		InitialisationInfo dateFormatInfo = null;
		if (!expireDelay.getClass().getSimpleName().equals("LocalDate")) {
			dateFormatInfo = new InitialisationInfo();
			dateFormatInfo.setCodeChamp(ValidationErrorEnum.FT011.getCodeChamp());
			dateFormatInfo.setNumErreur(ValidationErrorEnum.FT011.getNumErreur());
			dateFormatInfo.setLibelleErreur(ValidationErrorEnum.FT011.getLibelleErreur());
		}

		return dateFormatInfo;
	}

	public InitialisationInfo validPeriodFormat(LocalDate expireDelay) {

		InitialisationInfo periodFormatInfo = null;
		LocalDate now = LocalDate.now();

		long daysBetween = ChronoUnit.DAYS.between(now, expireDelay);
		if (daysBetween > 90 || daysBetween <= 0 || LocalDate.now().isAfter(expireDelay)) {
			periodFormatInfo = new InitialisationInfo();
			periodFormatInfo.setCodeChamp(ValidationErrorEnum.FT010.getCodeChamp());
			periodFormatInfo.setNumErreur(ValidationErrorEnum.FT010.getNumErreur());
			periodFormatInfo
					.setLibelleErreur(ValidationErrorEnum.FT010.getLibelleErreur());
		}

		return periodFormatInfo;
	}

	public InitialisationInfo validProtectionArchive(Boolean protectionArchive) {

		InitialisationInfo protectionArchiveInfo = null;
		if (!protectionArchive.getClass().getSimpleName().equals("Boolean")) {
			protectionArchiveInfo = new InitialisationInfo();
			protectionArchiveInfo.setCodeChamp(ValidationErrorEnum.FT016.getCodeChamp());
			protectionArchiveInfo.setNumErreur(ValidationErrorEnum.FT016.getNumErreur());
			protectionArchiveInfo.setLibelleErreur(ValidationErrorEnum.FT016.getLibelleErreur());
		}

		return protectionArchiveInfo;
	}

	public InitialisationInfo validSizePackage(List<FileRepresentation> rootFiles) {

		InitialisationInfo SizePackageInfo = null;

		if (CollectionUtils.isNotEmpty(rootFiles)) {
			if (FileUtils.getEnclosureTotalFileSize(rootFiles) == 0) {
				SizePackageInfo = new InitialisationInfo();
				SizePackageInfo.setCodeChamp(ValidationErrorEnum.FT020.getCodeChamp());
				SizePackageInfo.setNumErreur(ValidationErrorEnum.FT020.getNumErreur());
				SizePackageInfo.setLibelleErreur(ValidationErrorEnum.FT020.getLibelleErreur());
			} else {
				if (FileUtils.getEnclosureTotalFileSize(rootFiles) > uploadLimitSize) {
					SizePackageInfo = new InitialisationInfo();
					SizePackageInfo.setCodeChamp(ValidationErrorEnum.FT022.getCodeChamp());
					SizePackageInfo.setNumErreur(ValidationErrorEnum.FT022.getNumErreur());
					SizePackageInfo
							.setLibelleErreur(ValidationErrorEnum.FT022.getLibelleErreur());
				} else {
					if (FileUtils.getSizeFileOver(rootFiles, uploadFileLimitSize)) {
						SizePackageInfo = new InitialisationInfo();
						SizePackageInfo.setCodeChamp(ValidationErrorEnum.FT021.getCodeChamp());
						SizePackageInfo.setNumErreur(ValidationErrorEnum.FT021.getNumErreur());
						SizePackageInfo.setLibelleErreur(ValidationErrorEnum.FT021.getLibelleErreur());
					}

				}
			}
		} else {
			SizePackageInfo = new InitialisationInfo();
			SizePackageInfo.setCodeChamp(ValidationErrorEnum.FT018.getCodeChamp());
			SizePackageInfo.setNumErreur(ValidationErrorEnum.FT018.getNumErreur());
			SizePackageInfo.setLibelleErreur(ValidationErrorEnum.FT018.getLibelleErreur());
		}
		return SizePackageInfo;
	}

	public InitialisationInfo validIdNameFiles(List<FileRepresentation> rootFiles) {

		InitialisationInfo validFiles = null;
		Map<String, String> filesMap = FileUtils.RootFilesValidation(rootFiles);
		boolean idCheck = false;
		boolean nameCheck = false;

		for (Map.Entry<String, String> currentFile : filesMap.entrySet()) {
			idCheck = StringUtils.isNotEmpty(currentFile.getValue());
			nameCheck = StringUtils.isNotEmpty(currentFile.getKey());
			if (!idCheck) {
				validFiles = new InitialisationInfo();
				validFiles.setCodeChamp(ValidationErrorEnum.FT023.getCodeChamp());
				validFiles.setNumErreur(ValidationErrorEnum.FT023.getNumErreur());
				validFiles.setLibelleErreur(ValidationErrorEnum.FT023.getLibelleErreur());
				return validFiles;
			} else {
				if (!nameCheck) {
					validFiles = new InitialisationInfo();
					validFiles.setCodeChamp(ValidationErrorEnum.FT019.getCodeChamp());
					validFiles.setNumErreur(ValidationErrorEnum.FT019.getNumErreur());
					validFiles.setLibelleErreur(ValidationErrorEnum.FT019.getLibelleErreur());
					return validFiles;
				}
			}
		}

		return validFiles;
	}


	public InitialisationInfo validDomainHeader(String headerAddr, String sender) {

		InitialisationInfo validDomainHeader = null;

		String[] domaine = apiKey.get(headerAddr).get("domaine");// get domain from properties
		String senderDomaine = sender.substring(sender.indexOf("@") + 1);

		if (!StringUtils.contains(domaine[0], senderDomaine)) {
			throw new UnauthorizedAccessException("Erreur d’authentification : aucun objet de réponse renvoyé");
		}

		return validDomainHeader;
	}

	public InitialisationInfo validIpAddress(String headerAddr, String remoteAddr) {

		InitialisationInfo validIpAddress = null;

		String[] ips = apiKey.get(headerAddr).get("ips");
		boolean ipMatch = Arrays.stream(ips).anyMatch(ip -> {
			IpAddressMatcher ipAddressMatcher = new IpAddressMatcher(ip);
			return ipAddressMatcher.matches(remoteAddr);
		});

		if (!ipMatch) {
			throw new UnauthorizedAccessException("Erreur d’authentification : aucun objet de réponse renvoyé");
		}

		return validIpAddress;
	}

}
