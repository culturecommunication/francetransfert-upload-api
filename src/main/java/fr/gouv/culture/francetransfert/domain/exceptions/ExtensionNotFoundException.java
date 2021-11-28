package fr.gouv.culture.francetransfert.domain.exceptions;

public class ExtensionNotFoundException extends RuntimeException {

	public ExtensionNotFoundException(String extension) {
		super(extension);
	}

}
