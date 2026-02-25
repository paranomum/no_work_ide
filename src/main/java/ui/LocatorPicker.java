package ui;

import java.util.function.Consumer;

public interface LocatorPicker {
	void pick(Consumer<String> callback);
}
