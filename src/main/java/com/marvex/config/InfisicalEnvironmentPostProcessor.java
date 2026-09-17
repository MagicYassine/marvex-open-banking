package com.marvex.config;

import com.infisical.sdk.InfisicalSdk;
import com.infisical.sdk.config.SdkConfig;
import com.infisical.sdk.models.Secret;
import com.infisical.sdk.util.InfisicalException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Fetches secrets from Infisical before context refresh and exposes them as a property source,
 * so existing {@code ${SECRET_NAME}} placeholders in application*.yml resolve from Infisical
 * instead of (or in addition to) real OS env vars. Runs after config file loading but before any
 * bean creation, since {@link EnvironmentPostProcessor}s default to a lower priority than
 * Spring Boot's own ConfigDataEnvironmentPostProcessor.
 *
 * <p>Bootstrap coordinates (infisical.client-id/client-secret/project-id/environment-slug) are
 * read from the environment itself; if any are blank, Infisical is skipped entirely (e.g. the
 * test profile), leaving existing property sources untouched.
 */
@Slf4j
public class InfisicalEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "infisicalSecrets";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String clientId = environment.getProperty("infisical.client-id");
        String clientSecret = environment.getProperty("infisical.client-secret");
        String projectId = environment.getProperty("infisical.project-id");
        String environmentSlug = environment.getProperty("infisical.environment-slug");

        if (isBlank(clientId) || isBlank(clientSecret) || isBlank(projectId) || isBlank(environmentSlug)) {
            log.info("Infisical bootstrap config not set, skipping remote secret fetch");
            return;
        }

        try {
            InfisicalSdk sdk = new InfisicalSdk(new SdkConfig.Builder().build());
            sdk.Auth().UniversalAuthLogin(clientId, clientSecret);

            List<Secret> secrets = sdk.Secrets().ListSecrets(projectId, environmentSlug, "/", true, true, false);

            Map<String, Object> properties = new HashMap<>();
            for (Secret secret : secrets) {
                properties.put(secret.getSecretKey(), secret.getSecretValue());
            }

            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
            log.info("Loaded {} secret(s) from Infisical environment '{}'", properties.size(), environmentSlug);
        } catch (InfisicalException e) {
            throw new IllegalStateException("Infisical is configured but secrets could not be loaded", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
