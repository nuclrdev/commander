package dev.nuclr.commander.ui.functionBar;

import java.awt.Color;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import dev.nuclr.commander.event.FunctionKeyCommandEvent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;

@Component
@Getter
public class FunctionKeyBar {

	private static final Color BAR_BG = new Color(0x00, 0x00, 0x80);
	private static final Color BTN_BG = new Color(0x00, 0x00, 0xA8);
	private static final Color BTN_FG = new Color(0xF2, 0xF2, 0xF2);
	private static final Color KEY_COLOR = new Color(0xFF, 0xE0, 0x66);

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

	@PostConstruct
	public void init() {
		panel = new JPanel(new GridLayout(1, ITEMS.length, 2, 0));
		panel.setBackground(BAR_BG);
		panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		for (Item item : ITEMS) {
			panel.add(createButton(item));
		}
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
				"<html><span style='color:" + toHtml(KEY_COLOR) + ";font-weight:bold;'>" + item.number()
						+ "</span> "
						+ "<span style='color:" + toHtml(BTN_FG) + ";'>" + item.label() + "</span></html>");
		button.setFocusPainted(false);
		button.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		button.setBackground(BTN_BG);
		button.setForeground(BTN_FG);
		button.setOpaque(true);
		button.setContentAreaFilled(true);
		button.addActionListener(e -> applicationEventPublisher.publishEvent(
				new FunctionKeyCommandEvent(this, item.number(), item.label())));
		return button;
	}

	private String toHtml(Color color) {
		return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}

	private record Item(int number, String label) {
	}
}
