package com.datapeice.slbackend.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * Converts Heroku's DATABASE_URL (postgres://user:pass@host/db) to
 * a proper JDBC URL and injects it as spring.datasource.url when
 * SPRING_DATASOURCE_URL is not already set or is in postgres:// format.
 */
public class HerokuDatabaseUrlProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Check if there's a postgres:// style URL that needs converting
        String datasourceUrl = environment.getProperty("spring.datasource.url", "");

        if (datasourceUrl.startsWith("postgres://")) {
            try {
                String jdbcUrl = convertToJdbc(datasourceUrl);
                Map<String, Object> props = new HashMap<>();
                props.put("spring.datasource.url", jdbcUrl);
                // Extract credentials from URL for Hikari
                URI uri = new URI(datasourceUrl);
                String userInfo = uri.getUserInfo();
                if (userInfo != null && !userInfo.isBlank()) {
                    String[] parts = userInfo.split(":", 2);
                    props.put("spring.datasource.username", parts[0]);
                    if (parts.length > 1) {
                        props.put("spring.datasource.password", parts[1]);
                    }
                }
                environment.getPropertySources().addFirst(
                    new MapPropertySource("herokuDatasourceConverter", props)
                );
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Cannot parse DATABASE_URL: " + datasourceUrl, e);
            }
        }
    }

    private String convertToJdbc(String postgresUrl) throws URISyntaxException {
        URI uri = new URI(postgresUrl);
        String host = uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String path = uri.getPath();
        return "jdbc:postgresql://" + host + ":" + port + path + "?sslmode=require";
    }
}

