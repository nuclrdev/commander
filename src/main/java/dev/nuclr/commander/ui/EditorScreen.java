package dev.nuclr.commander.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.swing.JPanel;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class EditorScreen {

	private static final Map<String, String> EXTENSION_TO_SYNTAX = Map.ofEntries(
			Map.entry("java", SyntaxConstants.SYNTAX_STYLE_JAVA),
			Map.entry("js", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT),
			Map.entry("mjs", SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT),
			Map.entry("ts", SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT),
			Map.entry("tsx", SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT),
			Map.entry("json", SyntaxConstants.SYNTAX_STYLE_JSON),
			Map.entry("xml", SyntaxConstants.SYNTAX_STYLE_XML),
			Map.entry("html", SyntaxConstants.SYNTAX_STYLE_HTML),
			Map.entry("htm", SyntaxConstants.SYNTAX_STYLE_HTML),
			Map.entry("css", SyntaxConstants.SYNTAX_STYLE_CSS),
			Map.entry("py", SyntaxConstants.SYNTAX_STYLE_PYTHON),
			Map.entry("rb", SyntaxConstants.SYNTAX_STYLE_RUBY),
			Map.entry("sh", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL),
			Map.entry("bash", SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL),
			Map.entry("bat", SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH),
			Map.entry("cmd", SyntaxConstants.SYNTAX_STYLE_WINDOWS_BATCH),
			Map.entry("sql", SyntaxConstants.SYNTAX_STYLE_SQL),
			Map.entry("c", SyntaxConstants.SYNTAX_STYLE_C),
			Map.entry("h", SyntaxConstants.SYNTAX_STYLE_C),
			Map.entry("cpp", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS),
			Map.entry("hpp", SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS),
			Map.entry("cs", SyntaxConstants.SYNTAX_STYLE_CSHARP),
			Map.entry("go", SyntaxConstants.SYNTAX_STYLE_GO),
			Map.entry("rs", SyntaxConstants.SYNTAX_STYLE_RUST),
			Map.entry("php", SyntaxConstants.SYNTAX_STYLE_PHP),
			Map.entry("yaml", SyntaxConstants.SYNTAX_STYLE_YAML),
			Map.entry("yml", SyntaxConstants.SYNTAX_STYLE_YAML),
			Map.entry("md", SyntaxConstants.SYNTAX_STYLE_MARKDOWN),
			Map.entry("properties", SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE),
			Map.entry("groovy", SyntaxConstants.SYNTAX_STYLE_GROOVY),
			Map.entry("gradle", SyntaxConstants.SYNTAX_STYLE_GROOVY),
			Map.entry("kt", SyntaxConstants.SYNTAX_STYLE_KOTLIN),
			Map.entry("scala", SyntaxConstants.SYNTAX_STYLE_SCALA),
			Map.entry("lua", SyntaxConstants.SYNTAX_STYLE_LUA),
			Map.entry("perl", SyntaxConstants.SYNTAX_STYLE_PERL),
			Map.entry("pl", SyntaxConstants.SYNTAX_STYLE_PERL),
			Map.entry("dart", SyntaxConstants.SYNTAX_STYLE_DART),
			Map.entry("dockerfile", SyntaxConstants.SYNTAX_STYLE_DOCKERFILE),
			Map.entry("toml", SyntaxConstants.SYNTAX_STYLE_YAML),
			Map.entry("ini", SyntaxConstants.SYNTAX_STYLE_INI),
			Map.entry("prefs", SyntaxConstants.SYNTAX_STYLE_INI),
			Map.entry("cfg", SyntaxConstants.SYNTAX_STYLE_INI),
			Map.entry("csv", SyntaxConstants.SYNTAX_STYLE_CSV),
			
			
			Map.entry("svg", SyntaxConstants.SYNTAX_STYLE_XML),
			Map.entry(".classpath", SyntaxConstants.SYNTAX_STYLE_XML)
			
	);

	private JPanel panel;

	private RSyntaxTextArea textArea;

	private File file;

	public EditorScreen(File file) {
		this.file = file;
		this.panel = new JPanel(new BorderLayout());

		this.textArea = new RSyntaxTextArea();
		
		try (InputStream in = getClass().getResourceAsStream(
		        "/org/fife/ui/rsyntaxtextarea/themes/dark.xml")) {

		    Theme theme = Theme.load(in);
		    theme.apply(textArea);

		} catch (IOException e) {
		    e.printStackTrace();
		}		
		
		textArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 16));

		
		this.textArea.setCodeFoldingEnabled(true);
		this.textArea.setAntiAliasingEnabled(true);
		this.textArea.setTabSize(4);
		this.textArea.setTabsEmulated(false);

		String ext = FilenameUtils.getExtension(file.getName()).toLowerCase();
		this.textArea.setSyntaxEditingStyle(
				EXTENSION_TO_SYNTAX.getOrDefault(ext, SyntaxConstants.SYNTAX_STYLE_NONE));

		try {
			String content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
			this.textArea.setText(content);
			this.textArea.setCaretPosition(0);
		} catch (IOException e) {
			log.error("Failed to read file: {}", file.getAbsolutePath(), e);
			this.textArea.setText("Error reading file: " + e.getMessage());
			this.textArea.setEditable(false);
		}

		var scrollPane = new RTextScrollPane(this.textArea);
		
		
		scrollPane.setLineNumbersEnabled(true);
		this.panel.add(scrollPane, BorderLayout.CENTER);
	}

	public void dispose() {
		panel.removeAll();
	}

}
