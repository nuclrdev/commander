package dev.nuclr.commander.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class Banner {

	public static void printBanner() throws IOException {
		
		var bannerFile = "/dev/nuclr/commander/banner.txt";
		
		log.info(IOUtils.toString(Banner.class.getResourceAsStream(bannerFile), StandardCharsets.UTF_8));
		
	}
	
}
