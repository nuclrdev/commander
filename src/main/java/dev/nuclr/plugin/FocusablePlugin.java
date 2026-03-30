package dev.nuclr.plugin;

public interface FocusablePlugin {

	default void onFocusGained() {
	}

	default void onFocusLost() {
	}
}
