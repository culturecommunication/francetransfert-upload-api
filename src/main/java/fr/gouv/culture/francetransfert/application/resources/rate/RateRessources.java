package fr.gouv.culture.francetransfert.application.resources.rate;


import fr.gouv.culture.francetransfert.application.resources.model.FranceTransfertDataRepresentation;
import fr.gouv.culture.francetransfert.application.resources.model.rate.RateRepresentation;
import fr.gouv.culture.francetransfert.application.services.RateServices;
import fr.gouv.culture.francetransfert.domain.exceptions.UploadExcption;
import fr.gouv.culture.francetransfert.validator.EmailsFranceTransfert;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

@RestController
@RequestMapping(value = "/api-private/rate")
@Api(value = "Rate")
public class RateRessources {

    @Autowired
    private RateServices rateServices;

    @RequestMapping(value = "/satisfaction", method = RequestMethod.POST)
    @ApiOperation(httpMethod = "POST", value = "Rates the app on a scvale of 1 to 4")
    public void createSatisfactionFT(HttpServletResponse response,
                             @Valid @RequestBody RateRepresentation rateRepresentation) throws UploadExcption {
        rateServices.createSatisfactionFT(rateRepresentation);
    }
}
