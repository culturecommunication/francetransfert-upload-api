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
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import fr.gouv.culture.francetransfert.application.error.ApiValidationError;
import fr.gouv.culture.francetransfert.application.error.ErrorEnum;
import fr.gouv.culture.francetransfert.application.error.UnauthorizedAccessException;
import fr.gouv.culture.francetransfert.application.resources.model.DirectoryRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.EnclosureRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FileInfoRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FileRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FileRepresentationApi;
import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.InitialisationInfo;
import fr.gouv.culture.francetransfert.application.resources.model.PackageInfoRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.PreferencesRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.RecipientInfoApi;
import fr.gouv.culture.francetransfert.application.resources.model.StatusInfo;
import fr.gouv.culture.francetransfert.application.resources.model.StatusRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.ValidateData;
import fr.gouv.culture.francetransfert.application.resources.model.ValidateUpload;
import fr.gouv.culture.francetransfert.core.enums.EnclosureKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.RedisKeysEnum;
import fr.gouv.culture.francetransfert.core.enums.SourceEnum;
import fr.gouv.culture.francetransfert.core.enums.StatutEnum;
import fr.gouv.culture.francetransfert.core.enums.TypePliEnum;
import fr.gouv.culture.francetransfert.core.enums.ValidationErrorEnum;
import fr.gouv.culture.francetransfert.core.exception.MetaloadException;
import fr.gouv.culture.francetransfert.core.exception.StatException;
import fr.gouv.culture.francetransfert.core.exception.StorageException;
import fr.gouv.culture.francetransfert.core.services.RedisManager;
import fr.gouv.culture.francetransfert.core.utils.Base64CryptoService;
import fr.gouv.culture.francetransfert.core.utils.RedisUtils;
import fr.gouv.culture.francetransfert.core.utils.StringUploadUtils;
import fr.gouv.culture.francetransfert.domain.exceptions.ApiValidationException;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadException;
import fr.gouv.culture.francetransfert.domain.utils.FileUtils;

@Service
public class ValidationMailService {

	private static final Logger LOGGER = LoggerFactory.getLogger(ValidationMailService.class);

	@Value("${url.download.public}")
	private String urlDownloadPublic;

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

	@Autowired
	private UploadServices uploadServices;

	@Autowired
	private RedisManager redisManager;

	public InitialisationInfo validateMailData(ValidateData metadata, String headerAddr, String remoteAddr)
			throws ApiValidationException {

		if (StringUtils.isBlank(headerAddr)) {
			throw new UnauthorizedAccessException("Erreur d’authentification : aucun objet de réponse renvoyé");
		}

		List<ApiValidationError> errorList = new ArrayList<ApiValidationError>();
		PreferencesRepresentation preferences = metadata.getPreferences();

		validDomainHeader(headerAddr, metadata.getSenderEmail());
		validIpAddress(headerAddr, remoteAddr);

		ApiValidationError passwordChecked = validPassword(preferences.getPassword());
		ApiValidationError typeChecked = validType(metadata.getTypePli());
		if (typeChecked == null) {
			ApiValidationError recipientSenderChecked = validRecipientSender(metadata.getSenderEmail(),
					metadata.getRecipientEmails(), metadata.getTypePli());
			errorList.add(recipientSenderChecked);
		}

		ApiValidationError langueCourrielChecked = validLangueCourriel(preferences.getLanguage());
		ApiValidationError dateFormatChecked = validDateFormat(preferences.getExpireDelay());
		ApiValidationError datePeriodChecked = validPeriodFormat(preferences.getExpireDelay());
		ApiValidationError protectionArchiveChecked = validProtectionArchive(preferences.getProtectionArchive());
		ApiValidationError idNameFilesChecked = validIdNameFiles(metadata.getRootFiles());
		ApiValidationError SizePackageChecked = validSizePackage(metadata.getRootFiles());

		errorList.add(passwordChecked);
		errorList.add(typeChecked);
		errorList.add(langueCourrielChecked);
		errorList.add(dateFormatChecked);
		errorList.add(datePeriodChecked);
		errorList.add(protectionArchiveChecked);
		errorList.add(idNameFilesChecked);
		errorList.add(SizePackageChecked);
		errorList.removeIf(Objects::isNull);

		if (CollectionUtils.isEmpty(errorList)) {
			InitialisationInfo validPackage = new InitialisationInfo();
			StatusRepresentation statutPli = new StatusRepresentation();
			statutPli.setCodeStatutPli(StatutEnum.INI.getCode());
			statutPli.setLibelleStatutPli(StatutEnum.INI.getWord());
			validPackage.setStatutPli(statutPli);

			LocalDate now = LocalDate.now();
			int daysBetween = (int) ChronoUnit.DAYS.between(now, preferences.getExpireDelay());

			List<DirectoryRepresentation> rootDirs = new ArrayList<DirectoryRepresentation>();

			FranceTransfertDataRepresentation data = FranceTransfertDataRepresentation.builder()
					.password(preferences.getPassword()).senderEmail(metadata.getSenderEmail())
					.subject(metadata.getObjet()).message(metadata.getMessage())
					.recipientEmails(metadata.getRecipientEmails()).expireDelay(daysBetween)
					.language(preferences.getLanguage()).rootFiles(metadata.getRootFiles()).rootDirs(rootDirs)
					.zipPassword(preferences.getProtectionArchive()).source(SourceEnum.PUBLIC.getValue())
					.publicLink(TypePliEnum.LINK.getKey().equalsIgnoreCase(metadata.getTypePli())).build();

			EnclosureRepresentation dataRedis = uploadServices.createMetaDataEnclosureInRedis(data);
			validPackage.setIdPli(dataRedis.getEnclosureId());

			return validPackage;

		} else {
			throw new ApiValidationException(errorList);
		}
	}

	public ApiValidationError validPassword(String password) {
		ApiValidationError passwordInfo = null;
		if (StringUtils.isNotBlank(password) && !base64CryptoService.validatePassword(password.trim())) {
			passwordInfo = new ApiValidationError();
			passwordInfo.setCodeChamp(ValidationErrorEnum.FT012.getCodeChamp());
			passwordInfo.setNumErreur(ValidationErrorEnum.FT012.getNumErreur());
			passwordInfo.setLibelleErreur(ValidationErrorEnum.FT012.getLibelleErreur());
		}
		return passwordInfo;
	}

	public ApiValidationError validType(String typePli) {

		ApiValidationError typeInfo = null;
		String[] typeArray = new String[] { TypePliEnum.COURRIEL.getKey(), TypePliEnum.LINK.getKey() };
		List<String> typeList = new ArrayList<>(Arrays.asList(typeArray));
		if (StringUtils.isNotBlank(typePli)) {
			if (!typeList.contains(typePli)) {
				typeInfo = new ApiValidationError();
				typeInfo.setCodeChamp(ValidationErrorEnum.FT02.getCodeChamp());
				typeInfo.setNumErreur(ValidationErrorEnum.FT02.getNumErreur());
				typeInfo.setLibelleErreur(ValidationErrorEnum.FT02.getLibelleErreur());
			}
		} else {
			typeInfo = new ApiValidationError();
			typeInfo.setCodeChamp(ValidationErrorEnum.FT01.getCodeChamp());
			typeInfo.setNumErreur(ValidationErrorEnum.FT01.getNumErreur());
			typeInfo.setLibelleErreur(ValidationErrorEnum.FT01.getLibelleErreur());
		}

		return typeInfo;
	}

	public ApiValidationError validRecipientSender(String senderEmail, List<String> recipientEmails, String typePli) {

		ApiValidationError recipientSenderInfo = null;

		if (StringUtils.isNotEmpty(senderEmail)) {
			if (stringUploadUtils.isValidEmailIgni(senderEmail)) {
				if (typePli.equals(TypePliEnum.COURRIEL.getKey())) {
					if (CollectionUtils.isNotEmpty(recipientEmails)) {
						boolean validFormatRecipients = false;

						validFormatRecipients = recipientEmails.stream().noneMatch(x -> {
							return !stringUploadUtils.isValidEmail(x);
						});
						if (!validFormatRecipients) {
							recipientSenderInfo = new ApiValidationError();
							recipientSenderInfo.setCodeChamp(ValidationErrorEnum.FT09.getCodeChamp());
							recipientSenderInfo.setNumErreur(ValidationErrorEnum.FT09.getNumErreur());
							recipientSenderInfo.setLibelleErreur(ValidationErrorEnum.FT09.getLibelleErreur());
						}
					} else {
						recipientSenderInfo = new ApiValidationError();
						recipientSenderInfo.setCodeChamp(ValidationErrorEnum.FT07.getCodeChamp());
						recipientSenderInfo.setNumErreur(ValidationErrorEnum.FT07.getNumErreur());
						recipientSenderInfo.setLibelleErreur(ValidationErrorEnum.FT07.getLibelleErreur());
					}
				}
			} else {
				recipientSenderInfo = new ApiValidationError();
				recipientSenderInfo.setCodeChamp(ValidationErrorEnum.FT06.getCodeChamp());
				recipientSenderInfo.setNumErreur(ValidationErrorEnum.FT06.getNumErreur());
				recipientSenderInfo.setLibelleErreur(ValidationErrorEnum.FT06.getLibelleErreur());
			}
		} else {
			recipientSenderInfo = new ApiValidationError();
			recipientSenderInfo.setCodeChamp(ValidationErrorEnum.FT05.getCodeChamp());
			recipientSenderInfo.setNumErreur(ValidationErrorEnum.FT05.getNumErreur());
			recipientSenderInfo.setLibelleErreur(ValidationErrorEnum.FT05.getLibelleErreur());
		}

		return recipientSenderInfo;

	}

	public ApiValidationError validLangueCourriel(Locale langue) {

		ApiValidationError langueCourrielInfo = null;
		String[] langueArray = new String[] { Locale.FRANCE.toString(), Locale.UK.toString() };
		List<String> langueList = new ArrayList<>(Arrays.asList(langueArray));
		if (!langueList.contains(langue.toString())) {
			langueCourrielInfo = new ApiValidationError();
			langueCourrielInfo.setCodeChamp(ValidationErrorEnum.FT014.getCodeChamp());
			langueCourrielInfo.setNumErreur(ValidationErrorEnum.FT014.getNumErreur());
			langueCourrielInfo.setLibelleErreur(ValidationErrorEnum.FT014.getLibelleErreur());
		}
		return langueCourrielInfo;
	}

	public ApiValidationError validDateFormat(LocalDate expireDelay) {

		ApiValidationError dateFormatInfo = null;
		if (!expireDelay.getClass().getSimpleName().equals("LocalDate")) {
			dateFormatInfo = new ApiValidationError();
			dateFormatInfo.setCodeChamp(ValidationErrorEnum.FT011.getCodeChamp());
			dateFormatInfo.setNumErreur(ValidationErrorEnum.FT011.getNumErreur());
			dateFormatInfo.setLibelleErreur(ValidationErrorEnum.FT011.getLibelleErreur());
		}

		return dateFormatInfo;
	}

	public ApiValidationError validPeriodFormat(LocalDate expireDelay) {

		ApiValidationError periodFormatInfo = null;
		LocalDate now = LocalDate.now();

		long daysBetween = ChronoUnit.DAYS.between(now, expireDelay);
		if (daysBetween > 90 || daysBetween <= 0 || LocalDate.now().isAfter(expireDelay)) {
			periodFormatInfo = new ApiValidationError();
			periodFormatInfo.setCodeChamp(ValidationErrorEnum.FT010.getCodeChamp());
			periodFormatInfo.setNumErreur(ValidationErrorEnum.FT010.getNumErreur());
			periodFormatInfo.setLibelleErreur(ValidationErrorEnum.FT010.getLibelleErreur());
		}

		return periodFormatInfo;
	}

	public ApiValidationError validProtectionArchive(Boolean protectionArchive) {

		ApiValidationError protectionArchiveInfo = null;
		if (!protectionArchive.getClass().getSimpleName().equals("Boolean")) {
			protectionArchiveInfo = new ApiValidationError();
			protectionArchiveInfo.setCodeChamp(ValidationErrorEnum.FT016.getCodeChamp());
			protectionArchiveInfo.setNumErreur(ValidationErrorEnum.FT016.getNumErreur());
			protectionArchiveInfo.setLibelleErreur(ValidationErrorEnum.FT016.getLibelleErreur());
		}

		return protectionArchiveInfo;
	}

	public ApiValidationError validSizePackage(List<FileRepresentation> rootFiles) {

		ApiValidationError SizePackageInfo = null;

		if (CollectionUtils.isNotEmpty(rootFiles)) {
			if (FileUtils.getEnclosureTotalFileSize(rootFiles) == 0) {
				SizePackageInfo = new ApiValidationError();
				SizePackageInfo.setCodeChamp(ValidationErrorEnum.FT020.getCodeChamp());
				SizePackageInfo.setNumErreur(ValidationErrorEnum.FT020.getNumErreur());
				SizePackageInfo.setLibelleErreur(ValidationErrorEnum.FT020.getLibelleErreur());
			} else {
				if (FileUtils.getEnclosureTotalFileSize(rootFiles) > uploadLimitSize) {
					SizePackageInfo = new ApiValidationError();
					SizePackageInfo.setCodeChamp(ValidationErrorEnum.FT022.getCodeChamp());
					SizePackageInfo.setNumErreur(ValidationErrorEnum.FT022.getNumErreur());
					SizePackageInfo.setLibelleErreur(ValidationErrorEnum.FT022.getLibelleErreur());
				} else {
					if (FileUtils.getSizeFileOver(rootFiles, uploadFileLimitSize)) {
						SizePackageInfo = new ApiValidationError();
						SizePackageInfo.setCodeChamp(ValidationErrorEnum.FT021.getCodeChamp());
						SizePackageInfo.setNumErreur(ValidationErrorEnum.FT021.getNumErreur());
						SizePackageInfo.setLibelleErreur(ValidationErrorEnum.FT021.getLibelleErreur());
					}

				}
			}
		} else {
			SizePackageInfo = new ApiValidationError();
			SizePackageInfo.setCodeChamp(ValidationErrorEnum.FT018.getCodeChamp());
			SizePackageInfo.setNumErreur(ValidationErrorEnum.FT018.getNumErreur());
			SizePackageInfo.setLibelleErreur(ValidationErrorEnum.FT018.getLibelleErreur());
		}
		return SizePackageInfo;
	}

	public ApiValidationError validIdNameFiles(List<FileRepresentation> rootFiles) {

		ApiValidationError validFiles = null;
		Map<String, String> filesMap = FileUtils.RootFilesValidation(rootFiles);
		boolean idCheck = false;
		boolean nameCheck = false;

		for (Map.Entry<String, String> currentFile : filesMap.entrySet()) {
			idCheck = StringUtils.isNotEmpty(currentFile.getValue());
			nameCheck = StringUtils.isNotEmpty(currentFile.getKey());
			if (!idCheck) {
				validFiles = new ApiValidationError();
				validFiles.setCodeChamp(ValidationErrorEnum.FT023.getCodeChamp());
				validFiles.setNumErreur(ValidationErrorEnum.FT023.getNumErreur());
				validFiles.setLibelleErreur(ValidationErrorEnum.FT023.getLibelleErreur());
				return validFiles;
			} else {
				if (!nameCheck) {
					validFiles = new ApiValidationError();
					validFiles.setCodeChamp(ValidationErrorEnum.FT019.getCodeChamp());
					validFiles.setNumErreur(ValidationErrorEnum.FT019.getNumErreur());
					validFiles.setLibelleErreur(ValidationErrorEnum.FT019.getLibelleErreur());
					return validFiles;
				}
			}
		}

		return validFiles;
	}

	public void validDomainHeader(String headerAddr, String sender) {

		// get domain from properties
		String[] domaine = apiKey.get(headerAddr).get("domaine");
		String senderDomaine = stringUploadUtils.extractDomainNameFromEmailAddress(sender);

		if (!StringUtils.containsIgnoreCase(domaine[0], senderDomaine)) {
			throw new UnauthorizedAccessException("Erreur d’authentification : aucun objet de réponse renvoyé");
		}

	}

	public void validIpAddress(String headerAddr, String remoteAddr) {

		String[] ips = apiKey.get(headerAddr).get("ips");
		boolean ipMatch = Arrays.stream(ips).anyMatch(ip -> {
			IpAddressMatcher ipAddressMatcher = new IpAddressMatcher(ip);
			return ipAddressMatcher.matches(remoteAddr);
		});

		if (!ipMatch) {
			throw new UnauthorizedAccessException("Erreur d’authentification : aucun objet de réponse renvoyé");
		}

	}

	// ------------Upload API-------------

	public InitialisationInfo validateUpload(ValidateUpload metadata, String headerAddr, String remoteAddr,
			String senderId, String flowIdentifier) throws ApiValidationException, MetaloadException, StorageException {

		if (StringUtils.isBlank(headerAddr)) {
			throw new UnauthorizedAccessException("Erreur d’authentification : aucun objet de réponse renvoyé");
		}

		List<ApiValidationError> errorList = new ArrayList<ApiValidationError>();

		validDomainHeader(headerAddr, metadata.getSenderEmail());
		validIpAddress(headerAddr, remoteAddr);

		ApiValidationError idNameFilesChecked = validIdNameFile(metadata.getRootFiles());
		ApiValidationError totalChunksChecked = checkTotalChunks(metadata.getFlowChunkNumber());
		ApiValidationError fileSizeChecked = checkFileSize(metadata.getRootFiles().getSize());
		ApiValidationError fileContentChecked = checkFileContent(metadata.getFichier());
		ApiValidationError packageStatusChecked = checkPackageStatus(metadata.getEnclosureId());
		ApiValidationError flowChunkSizeChecked = checkFlowChunkSize(metadata.getFlowChunkSize());
		ApiValidationError totalChunkNumberChecked = checkTotalChunkNumber(metadata.getFlowChunkNumber(),
				metadata.getFlowTotalChunks());

		ApiValidationError senderIdPliChecked = validateSenderIdPli(metadata.getEnclosureId(),
				metadata.getSenderEmail());

		errorList.add(idNameFilesChecked);
		errorList.add(totalChunksChecked);
		errorList.add(fileSizeChecked);
		errorList.add(fileContentChecked);
		errorList.add(packageStatusChecked);
		errorList.add(flowChunkSizeChecked);
		errorList.add(totalChunkNumberChecked);
		errorList.add(senderIdPliChecked);

		errorList.removeIf(Objects::isNull);

		if (CollectionUtils.isEmpty(errorList)) {
			processUploadApi(metadata.getFlowChunkNumber(), metadata.getFlowTotalChunks(), flowIdentifier,
					metadata.getFichier(), metadata.getEnclosureId(), senderId);
			InitialisationInfo validPackage = new InitialisationInfo();
			StatusRepresentation statutPli = new StatusRepresentation();
			Map<String, String> enclosureRedis = RedisUtils.getEnclosure(redisManager, metadata.getEnclosureId());
			statutPli.setCodeStatutPli(enclosureRedis.get(EnclosureKeysEnum.STATUS_CODE.getKey()));
			statutPli.setLibelleStatutPli(enclosureRedis.get(EnclosureKeysEnum.STATUS_WORD.getKey()));
			validPackage.setStatutPli(statutPli);
			validPackage.setIdPli(metadata.getEnclosureId());
			return validPackage;
		} else {
			throw new ApiValidationException(errorList);
		}
	}

	public ApiValidationError validIdNameFile(FileRepresentation rootFile) {

		ApiValidationError validFiles = null;
		boolean idCheck = false;
		boolean nameCheck = false;

		idCheck = StringUtils.isNotEmpty(rootFile.getFid());
		nameCheck = StringUtils.isNotEmpty(rootFile.getName());
		if (!idCheck) {
			validFiles = new ApiValidationError();
			validFiles.setCodeChamp(ValidationErrorEnum.FT023.getCodeChamp());
			validFiles.setNumErreur(ValidationErrorEnum.FT023.getNumErreur());
			validFiles.setLibelleErreur(ValidationErrorEnum.FT023.getLibelleErreur());
			return validFiles;
		} else {
			if (!nameCheck) {
				validFiles = new ApiValidationError();
				validFiles.setCodeChamp(ValidationErrorEnum.FT019.getCodeChamp());
				validFiles.setNumErreur(ValidationErrorEnum.FT019.getNumErreur());
				validFiles.setLibelleErreur(ValidationErrorEnum.FT019.getLibelleErreur());
				return validFiles;
			}
		}

		return validFiles;
	}

	public ApiValidationError checkChunkNumber(Integer flowChunkNumber) {

		ApiValidationError validChunkNumber = null;
		if (flowChunkNumber.equals(null)) {
			if (flowChunkNumber > 0 && flowChunkNumber == (int) flowChunkNumber) {
			} else {
				validChunkNumber = new ApiValidationError();
				validChunkNumber.setCodeChamp(ValidationErrorEnum.FT2010.getCodeChamp());
				validChunkNumber.setNumErreur(ValidationErrorEnum.FT2010.getNumErreur());
				validChunkNumber.setLibelleErreur(ValidationErrorEnum.FT2010.getLibelleErreur());
			}
		} else {
			validChunkNumber = new ApiValidationError();
			validChunkNumber.setCodeChamp(ValidationErrorEnum.FT208.getCodeChamp());
			validChunkNumber.setNumErreur(ValidationErrorEnum.FT208.getNumErreur());
			validChunkNumber.setLibelleErreur(ValidationErrorEnum.FT208.getLibelleErreur());
		}

		return validChunkNumber;
	}

	public ApiValidationError checkTotalChunks(Integer flowTotalChunks) {

		ApiValidationError validTotalChunks = null;
		if (!flowTotalChunks.equals(null)) {
			if (flowTotalChunks <= 0 || flowTotalChunks != (int) flowTotalChunks) {
				validTotalChunks = new ApiValidationError();
				validTotalChunks.setCodeChamp(ValidationErrorEnum.FT2012.getCodeChamp());
				validTotalChunks.setNumErreur(ValidationErrorEnum.FT2012.getNumErreur());
				validTotalChunks.setLibelleErreur(ValidationErrorEnum.FT2012.getLibelleErreur());
			}
		} else {
			validTotalChunks = new ApiValidationError();
			validTotalChunks.setCodeChamp(ValidationErrorEnum.FT2011.getCodeChamp());
			validTotalChunks.setNumErreur(ValidationErrorEnum.FT2011.getNumErreur());
			validTotalChunks.setLibelleErreur(ValidationErrorEnum.FT2011.getLibelleErreur());
		}

		return validTotalChunks;
	}

	public ApiValidationError checkTotalChunkNumber(Integer flowChunkNumber, Integer flowTotalChunks) {

		ApiValidationError validTotalChunksNumber = null;
		if (flowChunkNumber > flowTotalChunks) {
			validTotalChunksNumber = new ApiValidationError();
			validTotalChunksNumber.setCodeChamp(ValidationErrorEnum.FT209.getCodeChamp());
			validTotalChunksNumber.setNumErreur(ValidationErrorEnum.FT209.getNumErreur());
			validTotalChunksNumber.setLibelleErreur(ValidationErrorEnum.FT209.getLibelleErreur());
		}
		return validTotalChunksNumber;
	}

	public ApiValidationError checkFlowChunkSize(long flowChunkSize) {

		ApiValidationError validFlowChunkSize = null;
		if (!Objects.isNull(flowChunkSize)) {
			if (flowChunkSize <= 0) {
				validFlowChunkSize = new ApiValidationError();
				validFlowChunkSize.setCodeChamp(ValidationErrorEnum.FT2014.getCodeChamp());
				validFlowChunkSize.setNumErreur(ValidationErrorEnum.FT2014.getNumErreur());
				validFlowChunkSize.setLibelleErreur(ValidationErrorEnum.FT2014.getLibelleErreur());
			}
		} else {
			validFlowChunkSize = new ApiValidationError();
			validFlowChunkSize.setCodeChamp(ValidationErrorEnum.FT2013.getCodeChamp());
			validFlowChunkSize.setNumErreur(ValidationErrorEnum.FT2013.getNumErreur());
			validFlowChunkSize.setLibelleErreur(ValidationErrorEnum.FT2013.getLibelleErreur());
		}

		return validFlowChunkSize;
	}

	public ApiValidationError checkFileSize(long fileSize) {

		ApiValidationError validFileSize = null;
		if (!Objects.isNull(fileSize)) {
			if (fileSize <= 0) {
				validFileSize = new ApiValidationError();
				validFileSize.setCodeChamp(ValidationErrorEnum.FT2018.getCodeChamp());
				validFileSize.setNumErreur(ValidationErrorEnum.FT2018.getNumErreur());
				validFileSize.setLibelleErreur(ValidationErrorEnum.FT2018.getLibelleErreur());
			}
		} else {
			validFileSize = new ApiValidationError();
			validFileSize.setCodeChamp(ValidationErrorEnum.FT2019.getCodeChamp());
			validFileSize.setNumErreur(ValidationErrorEnum.FT2019.getNumErreur());
			validFileSize.setLibelleErreur(ValidationErrorEnum.FT2019.getLibelleErreur());
		}
		return validFileSize;
	}

	public ApiValidationError checkFileContent(MultipartFile file) {

		ApiValidationError validFileContent = null;
		if (file.isEmpty()) {
			validFileContent = new ApiValidationError();
			validFileContent.setCodeChamp(ValidationErrorEnum.FT2016.getCodeChamp());
			validFileContent.setNumErreur(ValidationErrorEnum.FT2016.getNumErreur());
			validFileContent.setLibelleErreur(ValidationErrorEnum.FT2016.getLibelleErreur());
		}
		return validFileContent;
	}

	public ApiValidationError checkPackageStatus(String enclosureId) throws MetaloadException {

		ApiValidationError validPackageStatus = null;
		Map<String, String> enclosureRedis = RedisUtils.getEnclosure(redisManager, enclosureId);
		String statusCode = enclosureRedis.get(EnclosureKeysEnum.STATUS_CODE.getKey());
		if (!statusCode.equals(StatutEnum.INI.getCode()) && !statusCode.equals(StatutEnum.ECC.getCode())) {
			validPackageStatus = new ApiValidationError();
			validPackageStatus.setCodeChamp(ValidationErrorEnum.FT2017.getCodeChamp());
			validPackageStatus.setNumErreur(ValidationErrorEnum.FT2017.getNumErreur());
			validPackageStatus.setLibelleErreur(ValidationErrorEnum.FT2017.getLibelleErreur());
		}

		return validPackageStatus;
	}

	public ApiValidationError validateSenderIdPli(String enclosureId, String senderMail) {

		ApiValidationError validSenderIdPli = null;
		if (StringUtils.isNotBlank(senderMail)) {
			try {
				String senderEnclosureMail = RedisUtils.getEmailSenderEnclosure(redisManager, enclosureId);
				if (!StringUtils.equalsIgnoreCase(senderMail, senderEnclosureMail)) {
					validSenderIdPli = new ApiValidationError();
					validSenderIdPli.setCodeChamp(ValidationErrorEnum.FT205.getCodeChamp());
					validSenderIdPli.setNumErreur(ValidationErrorEnum.FT205.getNumErreur());
					validSenderIdPli.setLibelleErreur(ValidationErrorEnum.FT205.getLibelleErreur());
				}
			} catch (Exception e) {
				throw new UnauthorizedAccessException("Invalid enclosureId");
			}
		} else {
			validSenderIdPli = new ApiValidationError();
			validSenderIdPli.setCodeChamp(ValidationErrorEnum.FT05.getCodeChamp());
			validSenderIdPli.setNumErreur(ValidationErrorEnum.FT05.getNumErreur());
			validSenderIdPli.setLibelleErreur(ValidationErrorEnum.FT05.getLibelleErreur());
		}

		return validSenderIdPli;
	}

	public boolean processUploadApi(int flowChunkNumber, int flowTotalChunks, String flowIdentifier,
			MultipartFile multipartFile, String enclosureId, String senderId)
			throws MetaloadException, StorageException {

		try {
			boolean isUploaded = uploadServices.uploadFile(flowChunkNumber, flowTotalChunks, flowIdentifier,
					multipartFile, enclosureId, senderId);

			return isUploaded;
		} catch (Exception e) {
			LOGGER.error("Error while uploading enclosure " + enclosureId + " for chunk " + flowChunkNumber
					+ " and flowidentifier " + flowIdentifier + " : " + e.getMessage(), e);
			redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId),
					EnclosureKeysEnum.STATUS_CODE.getKey(), StatutEnum.ECH.getCode(), -1);
			redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(enclosureId),
					EnclosureKeysEnum.STATUS_WORD.getKey(), StatutEnum.ECH.getWord(), -1);
			throw new UploadException(ErrorEnum.TECHNICAL_ERROR.getValue() + " during file upload : " + e.getMessage(),
					enclosureId, e);
		}
	}

	// ----------Récupération du statut d’un pli API--------------

	public InitialisationInfo getStatusPli(StatusInfo metadata, String headerAddr, String remoteAddr)
			throws ApiValidationException, MetaloadException {

		if (StringUtils.isBlank(headerAddr)) {
			throw new UnauthorizedAccessException("Erreur d’authentification : aucun objet de réponse renvoyé");
		}

		List<ApiValidationError> errorList = new ArrayList<ApiValidationError>();

		validDomainHeader(headerAddr, metadata.getSenderMail());
		validIpAddress(headerAddr, remoteAddr);

		ApiValidationError senderIdPliChecked = validateSenderIdPli(metadata.getEnclosureId(),
				metadata.getSenderMail());

		errorList.add(senderIdPliChecked);
		errorList.removeIf(Objects::isNull);

		if (CollectionUtils.isEmpty(errorList)) {
			InitialisationInfo validPackage = new InitialisationInfo();
			StatusRepresentation statutPli = new StatusRepresentation();

			Map<String, String> enclosureRedis = RedisUtils.getEnclosure(redisManager, metadata.getEnclosureId());
			statutPli.setCodeStatutPli(enclosureRedis.get(EnclosureKeysEnum.STATUS_CODE.getKey()));
			statutPli.setLibelleStatutPli(enclosureRedis.get(EnclosureKeysEnum.STATUS_WORD.getKey()));
			validPackage.setStatutPli(statutPli);

			validPackage.setIdPli(metadata.getEnclosureId());

			return validPackage;

		} else {
			throw new ApiValidationException(errorList);
		}
	}

	public PackageInfoRepresentation getInfoPli(StatusInfo metadata, String headerAddr, String remoteAddr)
			throws ApiValidationException, MetaloadException, StatException {

		if (StringUtils.isBlank(headerAddr)) {
			throw new UnauthorizedAccessException("Erreur d’authentification : aucun objet de réponse renvoyé");
		}

		List<ApiValidationError> errorList = new ArrayList<ApiValidationError>();

		validDomainHeader(headerAddr, metadata.getSenderMail());
		validIpAddress(headerAddr, remoteAddr);

		ApiValidationError senderIdPliChecked = validateSenderIdPli(metadata.getEnclosureId(),
				metadata.getSenderMail());
		ApiValidationError statutPliChecked = validateStatutPli(metadata.getEnclosureId());

		errorList.add(senderIdPliChecked);
		errorList.add(statutPliChecked);
		errorList.removeIf(Objects::isNull);

		if (CollectionUtils.isEmpty(errorList)) {
			PackageInfoRepresentation data = new PackageInfoRepresentation();
			StatusRepresentation statutPli = new StatusRepresentation();
			PreferencesRepresentation preferences = new PreferencesRepresentation();

			List<RecipientInfoApi> destinataires = new ArrayList<RecipientInfoApi>();
			List<FileRepresentationApi> files = new ArrayList<FileRepresentationApi>();

			String passwordUnHashed = "";
			String link = "";
			String typePli = TypePliEnum.COURRIEL.name();
			Locale language;
			language = LocaleUtils.toLocale(RedisUtils.getEnclosureValue(redisManager, metadata.getEnclosureId(),
					EnclosureKeysEnum.LANGUAGE.getKey()));

			FileInfoRepresentation fileInfoRepresentation = uploadServices.getInfoPlis(metadata.getEnclosureId());

			if (fileInfoRepresentation.isPublicLink()) {
				typePli = TypePliEnum.LINK.name();
				link = urlDownloadPublic + metadata.getEnclosureId();
			}

			Map<String, String> enclosureRedis = RedisUtils.getEnclosure(redisManager, metadata.getEnclosureId());
			statutPli.setCodeStatutPli(enclosureRedis.get(EnclosureKeysEnum.STATUS_CODE.getKey()));
			statutPli.setLibelleStatutPli(enclosureRedis.get(EnclosureKeysEnum.STATUS_WORD.getKey()));
			passwordUnHashed = getUnhashedPassword(metadata.getEnclosureId());

			destinataires.addAll(fileInfoRepresentation.getRecipientsMails().stream().map(dest -> {
				return new RecipientInfoApi(dest);
			}).collect(Collectors.toList()));

			files.addAll(fileInfoRepresentation.getRootFiles().stream().map(file -> {
				return new FileRepresentationApi(file);
			}).collect(Collectors.toList()));

			preferences = PreferencesRepresentation.builder().language(language).password(passwordUnHashed)
					.protectionArchive(fileInfoRepresentation.isWithPassword())
					.expireDelay(fileInfoRepresentation.getValidUntilDate()).build();

			data = PackageInfoRepresentation.builder().idPli(metadata.getEnclosureId()).statutPli(statutPli)
					.typePli(typePli).courrielExpediteur(fileInfoRepresentation.getSenderEmail())
					.destinataires(destinataires).objet(fileInfoRepresentation.getSubject())
					.message(fileInfoRepresentation.getMessage()).preferences(preferences).fichiers(files)
					.lienTelechargementPublic(link).build();

			redisManager.hsetString(RedisKeysEnum.FT_ENCLOSURE.getKey(metadata.getEnclosureId()),
					EnclosureKeysEnum.INFOPLI.getKey(), Boolean.TRUE.toString(), -1);

			return data;

		} else {
			throw new ApiValidationException(errorList);
		}
	}

	public ApiValidationError validateStatutPli(String enclosureId) throws MetaloadException {

		ApiValidationError validPackageStatus = null;
		Map<String, String> enclosureRedis = RedisUtils.getEnclosure(redisManager, enclosureId);
		String statusCode = enclosureRedis.get(EnclosureKeysEnum.STATUS_CODE.getKey());
		String sourceCode = enclosureRedis.get(EnclosureKeysEnum.SOURCE.getKey());
		if (!StatutEnum.PAT.getCode().equals(statusCode) || !SourceEnum.PUBLIC.getValue().equals(sourceCode)) {
			validPackageStatus = new ApiValidationError();
			validPackageStatus.setCodeChamp(ValidationErrorEnum.FT406.getCodeChamp());
			validPackageStatus.setNumErreur(ValidationErrorEnum.FT406.getNumErreur());
			validPackageStatus.setLibelleErreur(ValidationErrorEnum.FT406.getLibelleErreur());
		}

		return validPackageStatus;
	}

	private String getUnhashedPassword(String enclosureId) throws MetaloadException, StatException {
		String passwordRedis;
		passwordRedis = RedisUtils.getEnclosureValue(redisManager, enclosureId, EnclosureKeysEnum.PASSWORD.getKey());
		if (passwordRedis != null && !StringUtils.isEmpty(passwordRedis)) {
			return base64CryptoService.aesDecrypt(passwordRedis);
		} else {
			passwordRedis = "";
			throw new UploadException("No Password for enclosure {}", enclosureId);
		}
	}

}
