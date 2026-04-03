package dev.nuclr.commander.plugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

import javax.swing.JOptionPane;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.nuclr.commander.common.IOUtils;
import dev.nuclr.commander.ui.common.Alerts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PluginLoader {

	@Autowired
	private TaskExecutor taskExecutor;

	@Autowired
	private ObjectMapper objectMapper;

	@Value("${core.plugins.folder}")
	private String corePluginsDirectory;

	@Autowired
	private PluginRegistry pluginRegistry;

	private byte[] publicKey;
	
	private File pluginsFolder;

	@PostConstruct
	public void init() {

		this.pluginsFolder = new File(".", corePluginsDirectory);
		
		this.publicKey = IOUtils.toByteArray(this.getClass().getResourceAsStream("/dev/nuclr/key/nuclr-cert.pem"));

		// Load the latest version of the file panel plugin first
		File latest = findLatestVersion(pluginsFolder, "filepanel-fs-", ".zip");
		if (latest != null) {
		    this.loadFile(latest);
		} else {
		    log.error("No file panel plugin found in directory: [{}]", pluginsFolder);
		    Alerts.showMessageDialog(null, "No file panel plugin found in directory: " + pluginsFolder, "Error", JOptionPane.ERROR_MESSAGE);
		    System.exit(1);
		}
		
		taskExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					loadPlugins();
				} catch (IOException e) {
					log.error("Failed to load plugins: {}", e.getMessage(), e);
				}
			}
		});
	}

	protected void loadPlugins() throws IOException {

		var pluginsFolder = new File(".", corePluginsDirectory);

		log.info("Loading plugins from directory: [{}]", pluginsFolder);

		Arrays.stream(pluginsFolder.listFiles(File::isFile)).filter(file -> file.getName().endsWith("zip"))
			.forEach(file -> {
				loadFile(file);
			}
		);

	}

	private void loadFile(File file) {
		
		log.info("Loading core plugin: [{}]", file.getName());

		var sigFile = new File(file.getAbsolutePath() + ".sig");

		if (false == sigFile.exists()) {
			log.warn("Plugin signature file not found for plugin: [{}]", file.getName());
			Alerts.showMessageDialog(null, "Plugin signature file not found for plugin: " + file.getName(),
					"Plugin Load Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		try {
			var valid = ZipVerifier.verify(file, sigFile, publicKey);
			if (valid) {
				log.info("Plugin [{}] verified successfully", file.getName());
				pluginRegistry.loadPlugin(file);
			} else {
				log.warn("Invalid plugin signature for plugin: [{}]", file.getName());
				Alerts.showMessageDialog(null, "Invalid plugin signature for plugin: " + file.getName(),
						"Plugin Load Error", JOptionPane.ERROR_MESSAGE);
			}
		} catch (Exception e) {
			log.error("Failed to verify plugin [{}]: {}", file.getName(), e.getMessage(), e);
			Alerts.showMessageDialog(null, "Failed to verify plugin: " + file.getName(), "Plugin Load Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}
	
	private File findLatestVersion(File folder, String prefix, String extension) {
		
	    File[] candidates = folder.listFiles((dir, name) ->
	        name.startsWith(prefix) && name.endsWith(extension)
	    );

	    if (candidates == null || candidates.length == 0) {
	        return null;
	    }
	    
	    return Arrays.stream(candidates)
		    .max((a, b) -> {
		        int[] va = extractVersion(a.getName(), prefix, extension);
		        int[] vb = extractVersion(b.getName(), prefix, extension);
		        for (int i = 0; i < 3; i++) {
		            if (va[i] != vb[i]) return Integer.compare(va[i], vb[i]);
		        }
		        return 0;
		    })
		    .orElse(null);
	}

	private int[] extractVersion(String filename, String prefix, String extension) {
	    String versionStr = filename
	        .substring(prefix.length(), filename.length() - extension.length());
	    
	    String[] parts = versionStr.split("\\.");
	    int[] version = new int[3];
	    for (int i = 0; i < Math.min(parts.length, 3); i++) {
	        try {
	            version[i] = Integer.parseInt(parts[i]);
	        } catch (NumberFormatException e) {
	            version[i] = 0;
	        }
	    }
	    return version;
	}

}
