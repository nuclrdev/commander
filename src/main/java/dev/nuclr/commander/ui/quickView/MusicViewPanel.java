package dev.nuclr.commander.ui.quickView;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import javax.swing.JPanel;

import org.apache.commons.io.FilenameUtils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import sdl2.SDLMixerAudio;

@Data
@Slf4j
public class MusicViewPanel extends JPanel {

	private Set<String> allowedExtensions = Set
			.of(
					"wav",
					"flac",
					"aac",
					"voc",
					"aiff",
					"ogg",
					"mp3",
					"xm",
					"mod",
					"s3m",
					"it",
					"669");

	public static SDLMixerAudio TrackerMusic;

	public boolean isMusicFile(File file) {
		var ext = FilenameUtils.getExtension(file.getAbsolutePath().toLowerCase());
		return allowedExtensions.contains(ext);
	}

	public void setFile(File file) {
		try {

			if (TrackerMusic != null) {
				TrackerMusic.stopMusic();
			} else {
				TrackerMusic = new SDLMixerAudio();

			}

			TrackerMusic.loadMusic(file);
			TrackerMusic.playMusic(-1);

		} catch (Exception e) {
			log.error("Failed to read music file: {}", file.getAbsolutePath(), e);
		}
	}

	public void stopMusic() {
		if (TrackerMusic != null) {
			TrackerMusic.stopMusic();
		}
	}

}
