package ui;

public class ChipItem {
	private final Long id;
	private final String label;

	public ChipItem(Long id, String label) {
		this.id = id;
		this.label = label;
	}

	public Long getId() {
		return id;
	}

	public String getLabel() {
		return label;
	}

	@Override
	public String toString() {
		return label;
	}
}