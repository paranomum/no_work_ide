package api.jaga.dto;

import lombok.Data;
import java.util.List;

@Data
public class JagaCreateCommentRequest {
	private String contentComment;
	private Long creatorId;
	private Long replyTo;
	private String replyComment;
	private Long taskId;
	private List<JagaCommentAttachmentRequest> attachments;
	private Boolean attachIsPending;
}