package dev.nuclr.commander.ui;

import java.io.File;

import javax.swing.JPanel;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@Component
@Lazy
public class QuickViewPanel {

	private JPanel panel;
	
	@PostConstruct
	public void init() {
		log.info("QuickViewPanel initialized");
		this.panel = new JPanel();
	}
	
	public void show(File file) {
		
		
		
	}
	
	
	
}
