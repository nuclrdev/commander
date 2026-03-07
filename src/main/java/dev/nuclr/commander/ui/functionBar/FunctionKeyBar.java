package dev.nuclr.commander.ui.functionBar;

import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.UIManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import dev.nuclr.commander.event.FunctionKeyCommandEvent;
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

	@PostConstruct
	public void init() {
		panel = new JPanel(new GridLayout(1, ITEMS.length, 2, 0));
		panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		for (Item item : ITEMS) {
			JButton button = createButton(item);
			buttons.add(button);
			panel.add(button);
		}
		applyTheme();
	}

	public void publish(int functionKeyNumber) {
		for (Item item : ITEMS) {
			if (item.number() == functionKeyNumber) {
				applicationEventPublisher.publishEvent(
						new FunctionKeyCommandEvent(this, item.number(), item.label()));
				return;
			}
		}
	}

	private JButton createButton(Item item) {
		JButton button = new JButton(
				"<html><b>" + item.number() + "</b> " + item.label() + "</html>");
		button.setFocusPainted(false);
		button.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		button.setOpaque(true);
		button.setContentAreaFilled(true);
		button.addActionListener(e -> applicationEventPublisher.publishEvent(
				new FunctionKeyCommandEvent(this, item.number(), item.label())));
		return button;
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

	private record Item(int number, String label) {
	}
}
