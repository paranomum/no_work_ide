import com.formdev.flatlaf.FlatLightLaf;
import ui.ActionWindow;

import javax.swing.*;

public class Main {
	public static void main(String[] args) {
		FlatLightLaf.install();

		SwingUtilities.invokeLater(() -> {
			ActionWindow actionWindow = new ActionWindow();
			actionWindow.show();
		});
	}
}
