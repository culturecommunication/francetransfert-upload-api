package fr.gouv.culture.francetransfert.application.resources.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import fr.gouv.culture.francetransfert.validator.DateUpdateConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
//@Builder
@NoArgsConstructor
@DateUpdateConstraint(enclosureId = "enclosureId", newDate = "newDate")
public class DateUpdateRequest {

    private String token;

    private String enclosureId;

    @JsonFormat(pattern = "dd-MM-yyyy")
    private LocalDate newDate;
}
