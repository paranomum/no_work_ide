package ui.action.iqhr_only;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FunnelMoveRequestDef {
	private String jrId;
	private String candidateId;
	private String vacancyId;
	private String username;
	private String password;
}