package dev.nuclr.commander.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import com.fasterxml.jackson.databind.ObjectMapper;

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
		return new ObjectMapper();
	}

}
