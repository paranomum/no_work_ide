package api.jaga.dto;

import lombok.Data;

import java.nio.file.Path;

@Data
public class JagaCreateAttachmentRequest {
	private Long projectId;
	private Path file;
}