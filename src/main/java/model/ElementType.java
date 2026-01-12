package model;

import lombok.Getter;

/**
 * Web element types for determining corresponding custom classes
 */
@Getter
public enum ElementType {

	BUTTON("Button", "Button"),
	FIELD("Field", "Input field"),
	SELECT("Select", "Dropdown list"),
	CHECKBOX("CheckBox", "Checkbox"),
	RADIO_BUTTON("RadioButton", "Radio button"),
	LINK("Link", "Link"),
	TEXT("Text", "Text element"),
	UNKNOWN("Unknown", "Unknown element");

	private final String className;
	private final String description;

	ElementType(String className, String description) {
		this.className = className;
		this.description = description;
	}

	public static ElementType fromClassName(String className) {
		for (ElementType type : values()) {
			if (type.className.equalsIgnoreCase(className)) {
				return type;
			}
		}
		return UNKNOWN;
	}

	@Override
	public String toString() {
		return className;
	}
}

