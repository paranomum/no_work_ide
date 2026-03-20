package model;

public record FrameworkElementDescriptor(
		Class<?> pageClass,
		String pageSimpleName,
		String fieldName,
		Class<?> fieldType,   // Field, Button, LinkButton, ...
		String label          // человекочитаемый label из твоего web_element
) {}

