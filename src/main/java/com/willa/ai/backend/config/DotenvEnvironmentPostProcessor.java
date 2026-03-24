package com.willa.ai.backend.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Load .env file early in the Spring Boot startup process
 * This ensures environment variables are available for property resolution
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger logger = Logger.getLogger(DotenvEnvironmentPostProcessor.class.getName());
    private static final String DOT_ENV_PROPERTY_SOURCE = "dotenv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Try to load .env from multiple locations
        String[] envPaths = {
                System.getProperty("user.dir") + File.separator + ".env",  // Project root
                System.getProperty("user.home") + File.separator + ".env",   // Home directory
                "/opt/willa/.env",                                            // VPS deployment
                "/app/.env"                                                   // Docker deployment
        };

        for (String envPath : envPaths) {
            File envFile = new File(envPath);
            if (envFile.exists()) {
                try {
                    Dotenv dotenv = Dotenv.configure()
                            .directory(envFile.getParent())
                            .filename(envFile.getName())
                            .ignoreIfMissing()
                            .load();

                    // Convert .env entries to Map for Spring PropertySource
                    Map<String, Object> envMap = new HashMap<>();
                    dotenv.entries().forEach(entry -> {
                        envMap.put(entry.getKey(), entry.getValue());
                    });

                    // Add as highest priority property source
                    if (!envMap.isEmpty()) {
                        MapPropertySource propertySource = new MapPropertySource(DOT_ENV_PROPERTY_SOURCE, envMap);
                        environment.getPropertySources().addFirst(propertySource);
                        System.out.println("✓ Successfully loaded .env from: " + envPath);
                        return;
                    }
                } catch (Exception e) {
                    System.out.println("Failed to load .env from " + envPath + ": " + e.getMessage());
                }
            }
        }

        System.out.println("⚠ No .env file found in any expected locations. Using system environment variables and defaults.");
    }
}
