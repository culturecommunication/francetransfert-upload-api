package fr.gouv.culture.francetransfert.application.resources.model;

import javax.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
//@Builder
@NoArgsConstructor
public class DeleteRequest {

	@NotBlank
	private String token;

	@NotBlank
	private String enclosureId;

	private String senderMail;
}
