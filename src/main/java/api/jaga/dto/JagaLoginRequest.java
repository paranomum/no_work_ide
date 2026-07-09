package api.jaga.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * JagaLoginRequest
 */
@JsonPropertyOrder({
		JagaLoginRequest.JSON_PROPERTY_MAIL,
		JagaLoginRequest.JSON_PROPERTY_PASSWORD
})
public class JagaLoginRequest {
	public static final String JSON_PROPERTY_MAIL = "mail";
	private String mail;

	public static final String JSON_PROPERTY_PASSWORD = "password";
	private String password;

	public JagaLoginRequest() {
	}

	public JagaLoginRequest mail(String mail) {
		this.mail = mail;
		return this;
	}

	/**
	 * Get mail
	 *
	 * @return mail
	 **/
	@javax.annotation.Nonnull
	@JsonProperty(JSON_PROPERTY_MAIL)
	@JsonInclude(value = JsonInclude.Include.ALWAYS)
	public String getMail() {
		return mail;
	}

	@JsonProperty(JSON_PROPERTY_MAIL)
	@JsonInclude(value = JsonInclude.Include.ALWAYS)
	public void setMail(String mail) {
		this.mail = mail;
	}

	public JagaLoginRequest password(String password) {
		this.password = password;
		return this;
	}

	/**
	 * Get password
	 *
	 * @return password
	 **/
	@javax.annotation.Nonnull
	@JsonProperty(JSON_PROPERTY_PASSWORD)
	@JsonInclude(value = JsonInclude.Include.ALWAYS)
	public String getPassword() {
		return password;
	}

	@JsonProperty(JSON_PROPERTY_PASSWORD)
	@JsonInclude(value = JsonInclude.Include.ALWAYS)
	public void setPassword(String password) {
		this.password = password;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof JagaLoginRequest)) {
			return false;
		}
		JagaLoginRequest that = (JagaLoginRequest) o;
		return Objects.equals(this.mail, that.mail) &&
				Objects.equals(this.password, that.password);
	}

	@Override
	public int hashCode() {
		return Objects.hash(mail, password);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("class JagaLoginRequest {\n");
		sb.append("    mail: ").append(toIndentedString(mail)).append("\n");
		sb.append("    password: ").append(toIndentedString(password)).append("\n");
		sb.append("}");
		return sb.toString();
	}

	private String toIndentedString(Object o) {
		if (o == null) {
			return "null";
		}
		return o.toString().replace("\n", "\n    ");
	}
}