package fr.gouv.culture.francetransfert.application.resources.model;

import java.util.ArrayList;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class RecipientInfo {

	private String recipientMail;
	private int numberOfDownloadPerRecipient;
	private boolean deleted;
	private ArrayList<String> downloadDates;

}
