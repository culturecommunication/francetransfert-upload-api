package com.opengroup.jsbapi.application.resources.rate;


import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api-private/rate")
@Api(value = "Rate")
public class RateApplication {

    @RequestMapping(value = "/rate", method = RequestMethod.POST)
    @ApiOperation(httpMethod = "POST", value = "Rates the app on a scvale of 1 to 5")
    public Long get()  {
        return 0L;
    }
}
