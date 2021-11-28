package fr.gouv.culture.francetransfert.application.resources.upload;

import java.util.ArrayList;
import java.util.List;

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
import fr.gouv.culture.francetransfert.application.resources.model.ConfigRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.DateUpdateRequest;
import fr.gouv.culture.francetransfert.application.resources.model.DeleteRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.EnclosureRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FileInfoRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.application.services.ConfigService;
import fr.gouv.culture.francetransfert.application.services.ConfirmationServices;
import fr.gouv.culture.francetransfert.application.services.RateServices;
import fr.gouv.culture.francetransfert.application.services.UploadServices;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadException;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.exception.MetaloadException;
import fr.gouv.culture.francetransfert.francetransfert_metaload_api.utils.RedisUtils;
import fr.gouv.culture.francetransfert.francetransfert_storage_api.Exception.StorageException;
import fr.gouv.culture.francetransfert.model.RateRepresentation;
import fr.gouv.culture.francetransfert.validator.EmailsFranceTransfert;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@CrossOrigin
@RestController
@RequestMapping("/api-private/upload-module")
@Api(value = "Upload resources")
@Validated
public class UploadResources {

	private static final Logger LOGGER = LoggerFactory.getLogger(UploadResources.class);

	@Autowired
	private UploadServices uploadServices;

	@Autowired
	private ConfigService configService;

	@Autowired
	private RateServices rateServices;

	@Autowired
	private ConfirmationServices confirmationServices;

	@GetMapping("/upload")
	@ApiOperation(httpMethod = "GET", value = "Upload  ")
	public void chunkExists(HttpServletResponse response, @RequestParam("flowChunkNumber") int flowChunkNumber,
			@RequestParam("flowChunkSize") int flowChunkSize,
			@RequestParam("flowCurrentChunkSize") int flowCurrentChunkSize,
			@RequestParam("flowTotalSize") int flowTotalSize, @RequestParam("flowIdentifier") String flowIdentifier,
			@RequestParam("flowFilename") String flowFilename,
			@RequestParam("flowRelativePath") String flowRelativePath,
			@RequestParam("flowTotalChunks") int flowTotalChunks, @RequestParam("enclosureId") String enclosureId) {
		String hashFid = RedisUtils.generateHashsha1(enclosureId + ":" + flowIdentifier);
		uploadServices.chunkExists(flowChunkNumber, hashFid);
		response.setStatus(HttpStatus.EXPECTATION_FAILED.value());
	}

	@PostMapping("/upload")
	@ApiOperation(httpMethod = "POST", value = "Upload  ")
	public void processUpload(HttpServletResponse response, @RequestParam("flowChunkNumber") int flowChunkNumber,
			@RequestParam("flowTotalChunks") int flowTotalChunks, @RequestParam("flowChunkSize") long flowChunkSize,
			@RequestParam("flowTotalSize") long flowTotalSize, @RequestParam("flowIdentifier") String flowIdentifier,
			@RequestParam("flowFilename") String flowFilename, @RequestParam("file") MultipartFile file,
			@RequestParam("enclosureId") String enclosureId, @RequestParam("senderId") String senderId,
			@RequestParam("senderToken") String senderToken) throws MetaloadException, StorageException {
		LOGGER.info("upload chunk number: {}/{} ", flowChunkNumber, flowTotalChunks);
		uploadServices.processUpload(flowChunkNumber, flowTotalChunks, flowIdentifier, file, enclosureId, senderId,
				senderToken);
		response.setStatus(HttpStatus.OK.value());
	}

	@PostMapping("/sender-info")
	@ApiOperation(httpMethod = "POST", value = "sender Info  ")
	public EnclosureRepresentation senderInfo(HttpServletRequest request, HttpServletResponse response,
			@RequestBody FranceTransfertDataRepresentation metadata) {
		LOGGER.info("start upload enclosure ");
		String token = metadata.getSenderToken();
		metadata.setConfirmedSenderId(metadata.getSenderId());
		EnclosureRepresentation enclosureRepresentation = uploadServices.senderInfoWithTockenValidation(metadata,
				token);
		response.setStatus(HttpStatus.OK.value());
		return enclosureRepresentation;
	}

	@GetMapping("/delete-file")
	@ApiOperation(httpMethod = "GET", value = "Generate delete URL ")
	public DeleteRepresentation deleteFile(HttpServletResponse response, @RequestParam("enclosure") String enclosureId,
			@RequestParam("token") String token) {
		LOGGER.info("start delete file ");
		DeleteRepresentation deleteRepresentation = uploadServices.deleteFile(enclosureId, token);
		response.setStatus(deleteRepresentation.getStatus());
		return deleteRepresentation;
	}

	@PostMapping("/update-expired-date")
	@ApiOperation(httpMethod = "POST", value = "Update expired date")
	public ResponseEntity<Object> updateTimeStamp(HttpServletResponse response,
			@RequestBody @Valid DateUpdateRequest dateUpdateRequest) throws UnauthorizedAccessException {
		uploadServices.validateAdminToken(dateUpdateRequest.getEnclosureId(), dateUpdateRequest.getToken());
		EnclosureRepresentation enclosureRepresentation = uploadServices
				.updateExpiredTimeStamp(dateUpdateRequest.getEnclosureId(), dateUpdateRequest.getNewDate());
		return new ResponseEntity<Object>(enclosureRepresentation, new HttpHeaders(), HttpStatus.OK);
	}

	@GetMapping("/validate-token")
	public ResponseEntity<Object> validateToken(
			@RequestParam @NotBlank(message = "Token must not be null") String token,
			@RequestParam @NotBlank(message = "EnclosureId must not be null") String enclosureId) {
		uploadServices.validateAdminToken(enclosureId, token);
		return new ResponseEntity<Object>(true, new HttpHeaders(), HttpStatus.OK);
	}

	@PostMapping("/validate-code")
	@ApiOperation(httpMethod = "POST", value = "Validate code  ")
	public EnclosureRepresentation validateCode(HttpServletRequest request, HttpServletResponse response,
			@RequestParam("senderMail") String senderMail, @RequestParam("code") String code,
			@Valid @EmailsFranceTransfert @RequestBody FranceTransfertDataRepresentation metadata) {
		EnclosureRepresentation enclosureRepresentation = null;
		LOGGER.info("start validate confirmation code : " + code);
		code = code.trim();
		String cookieTocken = confirmationServices.validateCodeConfirmationAndGenerateToken(metadata.getSenderEmail(),
				code);
		metadata.setConfirmedSenderId(metadata.getSenderId());
		enclosureRepresentation = uploadServices.senderInfoWithTockenValidation(metadata, cookieTocken);
		enclosureRepresentation.setSenderToken(cookieTocken);
		response.setStatus(HttpStatus.OK.value());
		return enclosureRepresentation;
	}

	@GetMapping("/file-info")
	@ApiOperation(httpMethod = "GET", value = "Download Info without URL ")
	public FileInfoRepresentation fileInfo(HttpServletResponse response, @RequestParam("enclosure") String enclosureId,
			@RequestParam("token") String token) throws UnauthorizedAccessException, MetaloadException {
		uploadServices.validateAdminToken(enclosureId, token);
		FileInfoRepresentation fileInfoRepresentation = uploadServices.getInfoPlis(enclosureId);
		response.setStatus(HttpStatus.OK.value());
		return fileInfoRepresentation;
	}

	@RequestMapping(value = "/satisfaction", method = RequestMethod.POST)
	@ApiOperation(httpMethod = "POST", value = "Rates the app on a scvale of 1 to 4")
	public void createSatisfactionFT(HttpServletResponse response,
			@Valid @RequestBody RateRepresentation rateRepresentation) throws UploadException {
		rateServices.createSatisfactionFT(rateRepresentation);
	}

	@RequestMapping(value = "/validate-mail", method = RequestMethod.GET)
	@ApiOperation(httpMethod = "GET", value = "Validate mail")
	public Boolean validateMailDomain(@RequestParam("mail") String mail) throws UploadException {
		ArrayList<String> list = new ArrayList<String>();
		list.add(mail);
		return uploadServices.validateMailDomain(list);
	}

	@RequestMapping(value = "/validate-mail", method = RequestMethod.POST)
	@ApiOperation(httpMethod = "POST", value = "Validate mail")
	public Boolean validateMailDomain(@RequestBody List<String> mails) throws UploadException {
		return uploadServices.validateMailDomain(mails);
	}

	@RequestMapping(value = "/config", method = RequestMethod.GET)
	@ApiOperation(httpMethod = "GET", value = "Get Config")
	public ConfigRepresentation getConfig() {
		return configService.getConfig();
	}

}
