package api.jaga.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * JagaLoginResponse
 */
@JsonPropertyOrder({
		JagaLoginResponse.JSON_PROPERTY_ACCESS_TOKEN,
		JagaLoginResponse.JSON_PROPERTY_REFRESH_TOKEN,
		JagaLoginResponse.JSON_PROPERTY_FULL_NAME,
		JagaLoginResponse.JSON_PROPERTY_ID,
		JagaLoginResponse.JSON_PROPERTY_MAIL,
		JagaLoginResponse.JSON_PROPERTY_EXPIRES_AT
})
public class JagaLoginResponse {
	public static final String JSON_PROPERTY_ACCESS_TOKEN = "accessToken";
	private String accessToken;

	public static final String JSON_PROPERTY_REFRESH_TOKEN = "refreshToken";
	private String refreshToken;

	public static final String JSON_PROPERTY_FULL_NAME = "fullName";
	private String fullName;

	public static final String JSON_PROPERTY_ID = "id";
	private Long id;

	public static final String JSON_PROPERTY_MAIL = "mail";
	private String mail;

	public static final String JSON_PROPERTY_EXPIRES_AT = "expiresAt";
	private String expiresAt;

	public JagaLoginResponse() {
	}

	public JagaLoginResponse accessToken(String accessToken) {
		this.accessToken = accessToken;
		return this;
	}

	@javax.annotation.Nonnull
	@JsonProperty(JSON_PROPERTY_ACCESS_TOKEN)
	@JsonInclude(value = JsonInclude.Include.ALWAYS)
	public String getAccessToken() {
		return accessToken;
	}

	@JsonProperty(JSON_PROPERTY_ACCESS_TOKEN)
	@JsonInclude(value = JsonInclude.Include.ALWAYS)
	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	public JagaLoginResponse refreshToken(String refreshToken) {
		this.refreshToken = refreshToken;
		return this;
	}

	@javax.annotation.Nonnull
	@JsonProperty(JSON_PROPERTY_REFRESH_TOKEN)
	@JsonInclude(value = JsonInclude.Include.ALWAYS)
	public String getRefreshToken() {
		return refreshToken;
	}

	@JsonProperty(JSON_PROPERTY_REFRESH_TOKEN)
	@JsonInclude(value = JsonInclude.Include.ALWAYS)
	public void setRefreshToken(String refreshToken) {
		this.refreshToken = refreshToken;
	}

	public JagaLoginResponse fullName(String fullName) {
		this.fullName = fullName;
		return this;
	}

	@javax.annotation.Nonnull
	@JsonProperty(JSON_PROPERTY_FULL_NAME)
	@JsonInclude(value = JsonInclude.Include.ALWAYS)
	public String getFullName() {
		return fullName;
	}

	@JsonProperty(JSON_PROPERTY_FULL_NAME)
	@JsonInclude(value = JsonInclude.Include.ALWAYS)
	public void setFullName(String fullName) {
		this.fullName = fullName;
	}

	public JagaLoginResponse id(Long id) {
		this.id = id;
		return this;
	}

	@javax.annotation.Nonnull
	@JsonProperty(JSON_PROPERTY_ID)
	@JsonInclude(value = JsonInclude.Include.ALWAYS)
	public Long getId() {
		return id;
	}

	@JsonProperty(JSON_PROPERTY_ID)
	@JsonInclude(value = JsonInclude.Include.ALWAYS)
	public void setId(Long id) {
		this.id = id;
	}

	public JagaLoginResponse mail(String mail) {
		this.mail = mail;
		return this;
	}

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

	public JagaLoginResponse expiresAt(String expiresAt) {
		this.expiresAt = expiresAt;
		return this;
	}

	@javax.annotation.Nonnull
	@JsonProperty(JSON_PROPERTY_EXPIRES_AT)
	@JsonInclude(value = JsonInclude.Include.ALWAYS)
	public String getExpiresAt() {
		return expiresAt;
	}

	@JsonProperty(JSON_PROPERTY_EXPIRES_AT)
	@JsonInclude(value = JsonInclude.Include.ALWAYS)
	public void setExpiresAt(String expiresAt) {
		this.expiresAt = expiresAt;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof JagaLoginResponse)) {
			return false;
		}
		JagaLoginResponse that = (JagaLoginResponse) o;
		return Objects.equals(this.accessToken, that.accessToken) &&
				Objects.equals(this.refreshToken, that.refreshToken) &&
				Objects.equals(this.fullName, that.fullName) &&
				Objects.equals(this.id, that.id) &&
				Objects.equals(this.mail, that.mail) &&
				Objects.equals(this.expiresAt, that.expiresAt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(accessToken, refreshToken, fullName, id, mail, expiresAt);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("class JagaLoginResponse {\n");
		sb.append("    accessToken: ").append(toIndentedString(accessToken)).append("\n");
		sb.append("    refreshToken: ").append(toIndentedString(refreshToken)).append("\n");
		sb.append("    fullName: ").append(toIndentedString(fullName)).append("\n");
		sb.append("    id: ").append(toIndentedString(id)).append("\n");
		sb.append("    mail: ").append(toIndentedString(mail)).append("\n");
		sb.append("    expiresAt: ").append(toIndentedString(expiresAt)).append("\n");
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