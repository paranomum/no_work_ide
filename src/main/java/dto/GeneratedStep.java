package dto;

import java.util.List;

public class GeneratedStep {
	public Kind kind;
	// для ATOMIC
	public AtomicStep atomic;
	// для METHOD_CALL
	public String pageClassName;
	public String pageVarName;
	public String methodName;
	public List<String> methodArgs;
	public enum Kind {ATOMIC, METHOD_CALL}
}
