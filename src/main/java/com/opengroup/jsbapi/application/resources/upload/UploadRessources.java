package com.opengroup.jsbapi.application.resources.upload;


import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api-private/upload")
@Api(value = "Upload", description = "Upload ressources")
public class UploadRessources {

    @RequestMapping(value = "/get", method = RequestMethod.GET)
    @ApiOperation(httpMethod = "GET", value = "Gets  ")
    public Long get()  {
        return 0L;
    }
}
