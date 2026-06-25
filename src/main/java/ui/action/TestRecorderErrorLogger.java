package ui.action;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TestRecorderErrorLogger {

	private static final String DIR_NAME = "test-recorder-error-log";
	private static final String FILE_NAME = "errors.log";

	private static final DateTimeFormatter TS =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static Path getLogFile() throws IOException {
		// ./test-recorder-error-log
		Path dir = Paths.get(".", DIR_NAME).toAbsolutePath().normalize();
		if (Files.notExists(dir)) {
			Files.createDirectories(dir); // не упадёт, если уже существует [web:32][web:39]
		}
		return dir.resolve(FILE_NAME);
	}

	public static void logError(String message, Throwable t) {
		try {
			Path logFile = getLogFile();
			StringBuilder sb = new StringBuilder();

			sb.append("[").append(LocalDateTime.now().format(TS)).append("] ");
			if (message != null && !message.isBlank()) {
				sb.append(message);
			} else if (t != null && t.getMessage() != null) {
				sb.append(t.getMessage());
			} else {
				sb.append("Unknown error");
			}
			sb.append(System.lineSeparator());

			if (t != null) {
				StringWriter sw = new StringWriter();
				t.printStackTrace(new PrintWriter(sw));
				sb.append(sw);
			}

			sb.append(System.lineSeparator());

			Files.writeString(
					logFile,
					sb.toString(),
					StandardCharsets.UTF_8,
					StandardOpenOption.CREATE,
					StandardOpenOption.APPEND
			); // append в один файл [web:36][web:44]
		} catch (IOException ioEx) {
			// тут уже ничего не делаем, чтобы не зациклиться
			ioEx.printStackTrace();
		}
	}

	public static void logError(Throwable t) {
		logError(null, t);
	}
}