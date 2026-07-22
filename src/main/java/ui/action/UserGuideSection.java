package ui.action;

public class UserGuideSection {

	private final String title;
	private final String resourcePath;

	public UserGuideSection(String title, String resourcePath) {
		this.title = title;
		this.resourcePath = resourcePath;
	}

	public String getTitle() {
		return title;
	}

	public String getResourcePath() {
		return resourcePath;
	}

	@Override
	public String toString() {
		return title;
	}
}