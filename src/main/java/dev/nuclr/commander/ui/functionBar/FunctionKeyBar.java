package dev.nuclr.commander.ui.functionBar;

import java.awt.GridLayout;
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
		panel = new JPanel(new GridLayout(1, ITEMS.length, 2, 0));
		panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

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
			JButton button = buttons.get(key - 1);
			button.setText(buttonText(key, label));
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

			JButton button = buttons.get(key - 1);
			button.setText(buttonText(key, label));
			button.setEnabled(resource != null);
		}
	}

	public void resetDefaultLabels() {
		setLabels(defaultLabels);
	}

	private JButton createButton(int number, String label) {
		JButton button = new JButton(buttonText(number, label));
		button.setFocusPainted(false);
		button.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		button.setOpaque(true);
		button.setContentAreaFilled(true);
		button.addActionListener(e -> publish(number));
		return button;
	}

	private static String buttonText(int number, String label) {
		if (label == null || label.isBlank()) {
			return "<html><b>" + number + "</b></html>";
		}
		return "<html><b>" + number + "</b> " + label + "</html>";
	}

	private void applyTheme() {
		var barBg = uiColor("Panel.background", panel.getBackground());
		var btnBg = uiColor("Button.background", barBg);
		var btnFg = uiColor("Button.foreground", panel.getForeground());

		panel.setBackground(barBg);
		for (JButton button : buttons) {
			button.setBackground(btnBg);
			button.setForeground(btnFg);
		}
	}

	private static java.awt.Color uiColor(String key, java.awt.Color fallback) {
		java.awt.Color c = UIManager.getColor(key);
		return c != null ? c : fallback;
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
}
