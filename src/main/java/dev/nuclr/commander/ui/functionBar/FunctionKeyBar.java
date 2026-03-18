package dev.nuclr.commander.ui.functionBar;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.UIManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import dev.nuclr.commander.event.FunctionKeyCommandEvent;
import dev.nuclr.plugin.MenuResource;
import jakarta.annotation.PostConstruct;
import lombok.Getter;

@Component
@Getter
public class FunctionKeyBar {

	private static final Item[] ITEMS = new Item[] {
			new Item(1, "Help"),
			new Item(2, "UserMn"),
			new Item(3, "View"),
			new Item(4, "Edit"),
			new Item(5, "Copy"),
			new Item(6, "RenMov"),
			new Item(7, "MkFold"),
			new Item(8, "Delete"),
			new Item(9, "ConfMn"),
			new Item(10, "Quit"),
			new Item(11, "Plugin"),
			new Item(12, "Screen")
	};

	@Autowired
	private ApplicationEventPublisher applicationEventPublisher;

	private JPanel panel;
	private final List<JButton> buttons = new ArrayList<>();
	private final Map<Integer, String> defaultLabels = new HashMap<>();
	private final Map<Integer, String> currentLabels = new HashMap<>();
	private final Map<Integer, MenuResource> currentMenuResources = new HashMap<>();

	@PostConstruct
	public void init() {
		panel = new JPanel(new GridLayout(1, ITEMS.length, 8, 0));
		panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

		for (Item item : ITEMS) {
			defaultLabels.put(item.number(), item.label());
			currentLabels.put(item.number(), item.label());
			JButton button = createButton(item.number(), item.label());
			buttons.add(button);
			panel.add(button);
		}
		applyTheme();
	}

	public void publish(int functionKeyNumber) {
		String label = currentLabels.getOrDefault(functionKeyNumber, "");
		MenuResource menuResource = currentMenuResources.get(functionKeyNumber);
		applicationEventPublisher.publishEvent(
				new FunctionKeyCommandEvent(this, functionKeyNumber, label, menuResource));
	}

	public void setLabels(Map<Integer, String> labels) {
		for (int key = 1; key <= ITEMS.length; key++) {
			String label = labels.getOrDefault(key, "");
			currentLabels.put(key, label);
			currentMenuResources.remove(key);
			FunctionKeyButton button = (FunctionKeyButton) buttons.get(key - 1);
			button.setMenuLabel(label);
			button.setEnabled(!label.isBlank());
		}
	}

	public void setMenuResources(List<MenuResource> resources, boolean shiftDown, boolean ctrlDown, boolean altDown) {
		List<MenuResource> safeResources = resources != null ? resources : List.of();
		currentMenuResources.clear();

		for (int key = 1; key <= ITEMS.length; key++) {
			final int functionKeyNumber = key;
			MenuResource resource = safeResources.stream()
					.filter(item -> matches(item, functionKeyNumber, shiftDown, ctrlDown, altDown))
					.findFirst()
					.orElse(null);

			currentMenuResources.put(key, resource);
			String label = resource != null ? resource.getName() : "";
			currentLabels.put(key, label);

			FunctionKeyButton button = (FunctionKeyButton) buttons.get(key - 1);
			button.setMenuLabel(label);
			button.setEnabled(resource != null);
		}
	}

	public void resetDefaultLabels() {
		setLabels(defaultLabels);
	}

	private JButton createButton(int number, String label) {
		FunctionKeyButton button = new FunctionKeyButton(number, label);
		button.setFocusPainted(false);
		button.setOpaque(true);
//		button.setBorderPainted(false);
		button.setRolloverEnabled(false);
		button.setFocusable(false);
		button.setContentAreaFilled(false);
		button.setPreferredSize(new Dimension(108, 28));
		button.addActionListener(e -> publish(number));
		return button;
	}

	private void applyTheme() {
		Color barBg = Color.BLACK;
		Color keyBg = new Color(0, 128, 128);
		Color keyFg = Color.BLACK;
		Color gutterBg = Color.BLACK;
		Color gutterFg = new Color(212, 212, 212);
		Color disabledBg = new Color(34, 79, 79);
		Color disabledFg = new Color(130, 130, 130);
		Font buttonFont = UIManager.getFont("Button.font");
		if (buttonFont == null) {
			buttonFont = panel.getFont();
		}

		panel.setBackground(barBg);
		for (JButton button : buttons) {
			FunctionKeyButton functionKeyButton = (FunctionKeyButton) button;
			functionKeyButton.setTheme(keyBg, keyFg, gutterBg, gutterFg, disabledBg, disabledFg);
			button.setFont(buttonFont);
			button.setMargin(new java.awt.Insets(0, 0, 0, 0));
		}
	}

	private static boolean matches(MenuResource resource, int functionKeyNumber, boolean shiftDown, boolean ctrlDown, boolean altDown) {
		KeyBinding binding = parse(resource.getKeyStroke());
		return binding != null
				&& binding.functionKeyNumber() == functionKeyNumber
				&& binding.shiftDown() == shiftDown
				&& binding.ctrlDown() == ctrlDown
				&& binding.altDown() == altDown;
	}

	private static KeyBinding parse(String keyStroke) {
		if (keyStroke == null || keyStroke.isBlank()) {
			return null;
		}

		boolean shift = false;
		boolean ctrl = false;
		boolean alt = false;
		Integer functionKey = null;

		for (String rawPart : keyStroke.split("\\+")) {
			String part = rawPart.trim().toUpperCase();
			switch (part) {
				case "SHIFT" -> shift = true;
				case "CTRL", "CONTROL" -> ctrl = true;
				case "ALT" -> alt = true;
				default -> {
					if (part.matches("F(1[0-2]|[1-9])")) {
						functionKey = Integer.parseInt(part.substring(1));
					}
				}
			}
		}

		if (functionKey == null) {
			return null;
		}
		return new KeyBinding(functionKey, shift, ctrl, alt);
	}

	private record Item(int number, String label) {
	}

	private record KeyBinding(int functionKeyNumber, boolean shiftDown, boolean ctrlDown, boolean altDown) {
	}

	private static final class FunctionKeyButton extends JButton {
		private static final long serialVersionUID = 1L;

		private final int number;
		private String label;
		private Color keyBackground = new Color(0, 128, 128);
		private Color keyForeground = Color.BLACK;
		private Color gutterBackground = Color.BLACK;
		private Color gutterForeground = new Color(212, 212, 212);
		private Color disabledBackground = new Color(34, 79, 79);
		private Color disabledForeground = new Color(130, 130, 130);

		private FunctionKeyButton(int number, String label) {
			this.number = number;
			this.label = label != null ? label : "";
			setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		}

		private void setMenuLabel(String label) {
			this.label = label != null ? label : "";
			repaint();
		}

		private void setTheme(
				Color keyBackground,
				Color keyForeground,
				Color gutterBackground,
				Color gutterForeground,
				Color disabledBackground,
				Color disabledForeground) {
			this.keyBackground = keyBackground;
			this.keyForeground = keyForeground;
			this.gutterBackground = gutterBackground;
			this.gutterForeground = gutterForeground;
			this.disabledBackground = disabledBackground;
			this.disabledForeground = disabledForeground;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics.create();
			try {
				g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

				int width = getWidth();
				int height = getHeight();
				int numberWidth = number >= 10 ? 20 : 12;
				int blockX = numberWidth + 2;
				int blockWidth = Math.max(0, width - blockX);

				g.setColor(gutterBackground);
				g.fillRect(0, 0, width, height);

				Color fillColor = isEnabled() ? keyBackground : disabledBackground;
				Color textColor = isEnabled() ? keyForeground : disabledForeground;
				g.setColor(fillColor);
				g.fillRect(blockX, 1, blockWidth, Math.max(0, height - 2));

				g.setFont(getFont());
				FontMetrics metrics = g.getFontMetrics();
				int baseline = (height - metrics.getHeight()) / 2 + metrics.getAscent();

				g.setColor(isEnabled() ? gutterForeground : disabledForeground);
				g.drawString(Integer.toString(number), 0, baseline);

				if (!label.isBlank()) {
					g.setColor(textColor);
					g.drawString(label, blockX + 4, baseline);
				}
			} finally {
				g.dispose();
			}
		}
	}
}
