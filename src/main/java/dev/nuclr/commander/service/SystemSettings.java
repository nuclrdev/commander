package dev.nuclr.commander.service;

import org.springframework.stereotype.Service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Data
public class SystemSettings {

	private boolean developerModeOn = false;
	
}
