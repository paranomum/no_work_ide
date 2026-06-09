import com.formdev.flatlaf.FlatLightLaf;
import ui.ActionWindow;

import javax.swing.*;

public class Main {
	public static void main(String[] args) {
		util.SslUtil.disableSslVerification();

		FlatLightLaf.setup();

		SwingUtilities.invokeLater(() -> {
			ActionWindow actionWindow = new ActionWindow();
			actionWindow.setVisible(true);
		});
	}
}
