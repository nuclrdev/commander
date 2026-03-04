package dev.nuclr.commander.ui.copy;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.nuclr.commander.panel.FilePanelProviderRegistry;
import dev.nuclr.commander.ui.FilePanel;
import dev.nuclr.plugin.panel.CollisionAction;
import dev.nuclr.plugin.panel.CopyOptions;
import dev.nuclr.plugin.panel.CopyProgress;
import dev.nuclr.plugin.panel.CopyStatus;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CopyCommandHandler {

	@Autowired
	private FilePanelProviderRegistry providerRegistry;

	public void copyBetween(FilePanel sourcePanel, FilePanel targetPanel, Frame owner) {
		if (sourcePanel == null) {
			showError(owner, "Copy requires an active file panel.");
			return;
		}

		var selected = sourcePanel.getSelectedPaths();
		if (selected.isEmpty()) {
			sourcePanel.showTransientMessage("Nothing selected");
			return;
		}

		if (targetPanel == null) {
			showError(owner, "The other panel is unavailable.");
			return;
		}
		if (targetPanel.getCurrentPath() == null) {
			showError(owner, "The target panel has no current directory.");
			return;
		}

		var targetProvider = providerRegistry.findProviderFor(targetPanel.getCurrentPath()).orElse(null);
		if (targetProvider == null) {
			showError(owner, "No provider is available for the target panel.");
			return;
		}

		String validationError = targetProvider.validateCopy(selected, targetPanel.getCurrentPath());
		if (validationError != null) {
			showError(owner, validationError);
			return;
		}

		int plannedItems = countPlannedItems(selected);
		var progress = createProgressDialog(owner, plannedItems);
		progress.show();

		Thread.ofVirtual().start(() -> {
			var state = new ConflictState();
			var failures = new ArrayList<String>();
			var copied = new AtomicInteger();
			var progressed = new AtomicInteger();

			try {
				for (Path source : selected) {
					if (progress.cancelled().get()) {
						break;
					}
					Path target = targetPanel.getCurrentPath().resolve(source.getFileName().toString());
					progress.setCurrentItem(source.getFileName() != null ? source.getFileName().toString() : source.toString());
					try {
						executeCopy(
								source,
								targetPanel.getCurrentPath(),
								target,
								owner,
								targetProvider,
								state,
								progress,
								copied,
								progressed,
								failures);
					} catch (CancelledCopyException ex) {
						break;
					} catch (Exception ex) {
						log.warn("Copy failed for {} -> {}: {}", source, target, ex.getMessage(), ex);
						failures.add(source + " -> " + ex.getMessage());
					}
				}
			} finally {
				progress.close();
			}

			SwingUtilities.invokeLater(() -> {
				targetPanel.refresh();
				if (sourcePanel == targetPanel) {
					sourcePanel.refresh();
				}

				if (progress.cancelled().get()) {
					showInfo(owner,
							buildSummary("Copy cancelled", copied.get(), failures),
							"Copy Cancelled");
				} else if (!failures.isEmpty()) {
					showError(owner,
							buildSummary("Copy completed with errors", copied.get(), failures),
							"Copy Completed With Errors");
				}
			});
		});
	}

	private void executeCopy(
			Path source,
			Path targetDirectory,
			Path intendedTarget,
			Frame owner,
			dev.nuclr.plugin.panel.FilePanelProvider targetProvider,
			ConflictState state,
			ProgressDialog progress,
			AtomicInteger copied,
			AtomicInteger progressed,
			List<String> failures) throws CancelledCopyException {

		checkCancelled(progress);

		Path target = resolveConflict(source, intendedTarget, owner, state, progress);
		if (target == null) {
			return;
		}

		Path effectiveTargetDirectory = target.equals(intendedTarget)
				? targetDirectory
				: target.getParent();
		if (effectiveTargetDirectory == null) {
			failures.add(source + " -> Invalid target directory.");
			return;
		}

		CollisionAction action = target.equals(intendedTarget)
				? CollisionAction.OVERWRITE
				: CollisionAction.KEEP_BOTH;
		String targetNameOverride = target.equals(intendedTarget)
				? null
				: target.getFileName().toString();

		var result = targetProvider.copy(
				List.of(source),
				effectiveTargetDirectory,
				new CopyOptions(action, true, targetNameOverride),
				new CopyProgress() {
					@Override
					public boolean isCancelled() {
						return progress.cancelled().get();
					}

					@Override
					public void onItemStarted(Path itemSource, Path itemTarget) {
						progress.setCurrentItem(itemSource.getFileName() != null
								? itemSource.getFileName().toString()
								: itemSource.toString());
					}

					@Override
					public void onItemCompleted(Path itemSource, Path itemTarget) {
						progress.setProgress(progressed.incrementAndGet());
					}
				});

		copied.addAndGet(result.copiedItems());

		if (result.userMessage() != null
				&& (result.status() == CopyStatus.NOT_SUPPORTED || result.status() == CopyStatus.FAILED)) {
			failures.add(source + " -> " + result.userMessage());
		}
		failures.addAll(result.errors());

		if (result.status() == CopyStatus.CANCELLED) {
			progress.cancelled().set(true);
			throw new CancelledCopyException();
		}
	}

	private Path resolveConflict(
			Path source,
			Path intendedTarget,
			Frame owner,
			ConflictState state,
			ProgressDialog progress) throws CancelledCopyException {

		if (!Files.exists(intendedTarget)) {
			return intendedTarget;
		}

		if (isSamePath(source, intendedTarget)) {
			return uniqueSibling(intendedTarget);
		}

		ConflictChoice remembered = state.rememberedChoice;
		if (remembered != null) {
			return applyConflictChoice(remembered, source, intendedTarget, owner, state);
		}

		ConflictDecision decision = promptForConflict(source, intendedTarget, owner);
		if (decision.choice() == ConflictChoice.CANCEL) {
			progress.cancelled().set(true);
			throw new CancelledCopyException();
		}

		if (decision.remember()) {
			state.rememberedChoice = decision.choice() == ConflictChoice.RENAME
					? ConflictChoice.APPEND_KEEP_BOTH
					: decision.choice();
		}

		if (decision.choice() == ConflictChoice.RENAME) {
			return decision.customTarget();
		}

		return applyConflictChoice(decision.choice(), source, intendedTarget, owner, state);
	}

	private Path applyConflictChoice(
			ConflictChoice choice,
			Path source,
			Path intendedTarget,
			Frame owner,
			ConflictState state) {
		switch (choice) {
			case OVERWRITE:
				return intendedTarget;
			case SKIP:
				return null;
			case APPEND_KEEP_BOTH:
				return uniqueSibling(intendedTarget);
			case RENAME:
				return promptForRename(intendedTarget, owner);
			case CANCEL:
				return null;
			default:
				return null;
		}
	}

	private ConflictDecision promptForConflict(Path source, Path target, Frame owner) {
		return invokeOnEdt(() -> {
			JCheckBox remember = new JCheckBox("Remember choice for remaining conflicts");
			JPanel panel = new JPanel(new BorderLayout(0, 8));
			panel.add(new JLabel("<html>Target already exists:<br><b>" + escapeHtml(target.toString())
					+ "</b><br>Copying: <b>" + escapeHtml(source.getFileName().toString()) + "</b></html>"),
					BorderLayout.CENTER);
			panel.add(remember, BorderLayout.SOUTH);

			Object[] options = {"Overwrite", "Skip", "Rename", "Append (keep both)", "Cancel"};
			int result = JOptionPane.showOptionDialog(
					owner,
					panel,
					"Name Conflict",
					JOptionPane.DEFAULT_OPTION,
					JOptionPane.WARNING_MESSAGE,
					null,
					options,
					options[1]);

			ConflictChoice choice = switch (result) {
				case 0 -> ConflictChoice.OVERWRITE;
				case 1 -> ConflictChoice.SKIP;
				case 2 -> ConflictChoice.RENAME;
				case 3 -> ConflictChoice.APPEND_KEEP_BOTH;
				default -> ConflictChoice.CANCEL;
			};

			if (choice == ConflictChoice.RENAME) {
				Path renamed = promptForRename(target, owner);
				if (renamed == null) {
					return new ConflictDecision(ConflictChoice.CANCEL, null, remember.isSelected());
				}
				return new ConflictDecision(choice, renamed, remember.isSelected());
			}

			return new ConflictDecision(choice, null, remember.isSelected());
		});
	}

	private Path promptForRename(Path target, Frame owner) {
		return invokeOnEdt(() -> {
			while (true) {
				String value = JOptionPane.showInputDialog(
						owner,
						"Enter a new name:",
						target.getFileName().toString());
				if (value == null) {
					return null;
				}
				value = value.strip();
				if (value.isEmpty() || value.contains("/") || value.contains("\\")) {
					showError(owner, "Please enter a valid file or folder name.", "Invalid Name");
					continue;
				}
				return target.resolveSibling(value);
			}
		});
	}

	private Path uniqueSibling(Path target) {
		String name = target.getFileName().toString();
		String base = name;
		String ext = "";
		int dot = name.lastIndexOf('.');
		if (dot > 0) {
			base = name.substring(0, dot);
			ext = name.substring(dot);
		}

		int index = 2;
		Path candidate = target.resolveSibling(base + " - Copy" + ext);
		while (Files.exists(candidate)) {
			candidate = target.resolveSibling(base + " - Copy (" + index + ")" + ext);
			index++;
		}
		return candidate;
	}

	private boolean isSamePath(Path left, Path right) {
		try {
			return Files.exists(left) && Files.exists(right) && Files.isSameFile(left, right);
		} catch (IOException e) {
			return left.normalize().equals(right.normalize());
		}
	}

	private void checkCancelled(ProgressDialog progress) throws CancelledCopyException {
		if (progress.cancelled().get()) {
			throw new CancelledCopyException();
		}
	}

	private String buildSummary(String prefix, int copiedCount, List<String> failures) {
		StringBuilder sb = new StringBuilder(prefix)
				.append(". Copied items: ")
				.append(copiedCount);
		if (!failures.isEmpty()) {
			sb.append("\n\nFailures:");
			int limit = Math.min(failures.size(), 8);
			for (int i = 0; i < limit; i++) {
				sb.append("\n- ").append(failures.get(i));
			}
			if (failures.size() > limit) {
				sb.append("\n- ... and ").append(failures.size() - limit).append(" more");
			}
		}
		return sb.toString();
	}

	private void showError(Frame owner, String message) {
		showError(owner, message, "Copy Error");
	}

	private void showError(Frame owner, String message, String title) {
		SwingUtilities.invokeLater(() ->
				JOptionPane.showMessageDialog(owner, message, title, JOptionPane.ERROR_MESSAGE));
	}

	private void showInfo(Frame owner, String message, String title) {
		SwingUtilities.invokeLater(() ->
				JOptionPane.showMessageDialog(owner, message, title, JOptionPane.INFORMATION_MESSAGE));
	}

	private String escapeHtml(String text) {
		return text
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;");
	}

	private <T> T invokeOnEdt(EdtSupplier<T> supplier) {
		if (SwingUtilities.isEventDispatchThread()) {
			return supplier.get();
		}

		final Object[] box = new Object[1];
		final RuntimeException[] failure = new RuntimeException[1];
		try {
			SwingUtilities.invokeAndWait(() -> {
				try {
					box[0] = supplier.get();
				} catch (RuntimeException ex) {
					failure[0] = ex;
				}
			});
		} catch (Exception e) {
			throw new IllegalStateException("Failed to run on EDT", e);
		}
		if (failure[0] != null) {
			throw failure[0];
		}
		@SuppressWarnings("unchecked")
		T value = (T) box[0];
		return value;
	}

	private int countPlannedItems(List<Path> items) {
		int total = 0;
		for (Path item : items) {
			total += countPathItems(item);
		}
		return Math.max(total, 1);
	}

	private int countPathItems(Path path) {
		try {
			if (!Files.isDirectory(path)) {
				return 1;
			}
			try (var walk = Files.walk(path)) {
				return Math.toIntExact(walk.count());
			}
		} catch (Exception e) {
			return 1;
		}
	}

	private interface EdtSupplier<T> {
		T get();
	}

	private enum ConflictChoice {
		OVERWRITE,
		SKIP,
		RENAME,
		APPEND_KEEP_BOTH,
		CANCEL
	}

	private record ConflictDecision(ConflictChoice choice, Path customTarget, boolean remember) {
	}

	private static class ConflictState {
		private ConflictChoice rememberedChoice;
	}

	private static class CancelledCopyException extends Exception {
		private static final long serialVersionUID = 1L;
	}

	private static class ProgressDialog {
		private final AtomicBoolean cancelled = new AtomicBoolean(false);
		private final JDialog dialog;
		private final JLabel label;
		private final JProgressBar progressBar;

		private ProgressDialog(Frame owner, int maximum) {
			dialog = new JDialog(owner, "Copying", false);
			label = new JLabel("Preparing copy...");
			progressBar = new JProgressBar(0, Math.max(maximum, 1));
			progressBar.setStringPainted(true);

			JButton cancelButton = new JButton("Cancel");
			cancelButton.addActionListener(e -> cancelled.set(true));

			JPanel content = new JPanel(new BorderLayout(0, 8));
			content.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 12, 12, 12));
			content.add(label, BorderLayout.NORTH);
			content.add(progressBar, BorderLayout.CENTER);
			content.add(Box.createVerticalStrut(4), BorderLayout.WEST);
			content.add(cancelButton, BorderLayout.SOUTH);

			dialog.setContentPane(content);
			dialog.pack();
			dialog.setLocationRelativeTo(owner);
		}

		private void show() {
			SwingUtilities.invokeLater(() -> dialog.setVisible(true));
		}

		private void setCurrentItem(String item) {
			SwingUtilities.invokeLater(() -> label.setText("Copying: " + item));
		}

		private void setProgress(int value) {
			SwingUtilities.invokeLater(() -> progressBar.setValue(value));
		}

		private void close() {
			SwingUtilities.invokeLater(() -> dialog.dispose());
		}

		private AtomicBoolean cancelled() {
			return cancelled;
		}
	}

	private ProgressDialog createProgressDialog(Frame owner, int maximum) {
		return invokeOnEdt(() -> new ProgressDialog(owner, maximum));
	}
}
