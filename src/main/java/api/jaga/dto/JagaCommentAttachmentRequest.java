package api.jaga.dto;

import lombok.Data;

@Data
public class JagaCommentAttachmentRequest {
	private Long id;
	private Long attachUser;
	private String attachPath;
	private String attachName;
	private String originalName;
	private String attachType;
	private String createTs;
	private Long attachSize;
	private Integer attachSrc;
	private Object serviceAttribute;
	private Boolean isDeleted;
}