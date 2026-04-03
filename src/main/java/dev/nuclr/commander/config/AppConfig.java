package dev.nuclr.commander.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class AppConfig {

	@Bean
	public TaskExecutor taskExecutor() {
		var te = new SimpleAsyncTaskExecutor();
		te.setVirtualThreads(true);
		te.setThreadNamePrefix("tasks-");
		return te;
	}
	
	@Bean
	public ObjectMapper objectMapper() {
		var mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		return mapper;
	}

}
