package fr.gouv.culture.francetransfert;

import java.util.ArrayList;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.ApiKey;
import springfox.documentation.service.Contact;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * The type Demo application.
 */
@EnableSwagger2
@EnableGlobalMethodSecurity(prePostEnabled = true)
@SpringBootApplication
@ComponentScan(basePackages = { "fr.gouv.culture" })
public class FranceTransfertUploadStarter extends WebSecurityConfigurerAdapter {

	@Autowired
	private Environment env;

	/**
	 * The entry point of application.
	 *
	 * @param args the input arguments
	 */
	public static void main(String[] args) {
		SpringApplication.run(FranceTransfertUploadStarter.class, args);
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception {
		http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and().exceptionHandling()
				.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)).and().csrf().disable()
				.headers().frameOptions().disable();
	}

	/**
	 * Api docket.
	 *
	 * @return the docket
	 */
	@Bean
	public Docket api() {
		return new Docket(DocumentationType.SWAGGER_2).select()
				.apis(RequestHandlerSelectors.basePackage("fr.gouv.culture.francetransfert.application.resources"))
				.paths(PathSelectors.any()).build().apiInfo(apiInfo())
				.securitySchemes(Collections.singletonList(new ApiKey("apiKey", "x-access-token", "header")));
	}

	/**
	 * Api info
	 *
	 * @return the info about Api
	 */
	private ApiInfo apiInfo() {
		return new ApiInfo(env.getProperty("tool.swagger.api.title"), env.getProperty("tool.swagger.api.description"),
				env.getProperty("tool.swagger.api.version"), env.getProperty("tool.swagger.api.terms-of-services-use"),
				new Contact(env.getProperty("tool.swagger.api.contact.name"),
						env.getProperty("tool.swagger.api.contact.url"),
						env.getProperty("tool.swagger.api.contact.email")),
				env.getProperty("tool.swagger.api.contact.licence"),
				env.getProperty("tool.swagger.api.contact.licence.url"), new ArrayList());
	}

}
