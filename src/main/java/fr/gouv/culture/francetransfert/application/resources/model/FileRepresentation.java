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
public class FileRepresentation extends DataRepresentation {

	@NotBlank
	private String fid;

	private long size;
}
