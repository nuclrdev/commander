package dev.nuclr.commander.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:/dev/nuclr/commander/app.properties")
public class PropertiesConfig {
}