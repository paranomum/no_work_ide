package dto;

public class AtomicStep {
	public String pageClassName;   // FQCN PageObject'а
	public String pageVarName;     // переменная в тесте (authorizationPage)
	public String fieldName;       // loginField
	public String actionCode;      // setValue, click, ...
	public String value;           // значение, если есть
	public String comment;         // комментарий
	public String javaWebElement;
}
