package ui.action.iqhr_only;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.val;
import ru.rt.iqhr.framework.config.TokenDto;
import ru.rt.iqhr.services.invoker.ApiClient;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FunnelMoveService {

	public static void processTillJrCandidateMass(
			Long jrId,
			Long candidateId,
			Long vacancyId,
			String username,
			String password,
			String domain
	) {
		ru.rt.iqhr.framework.util.FunnelMoveService.processTillJrCandidateMass(
				jrId,
				candidateId,
				vacancyId,
				getApiClient("/recruiting", username, password, domain),
				getApiClient("/funnel", username, password, domain)
		);
	}

	public static void processFullCandidateMass(
			Long jrId,
			Long candidateId,
			Long vacancyId,
			String username,
			String password,
			String domain
	) {
		ru.rt.iqhr.framework.util.FunnelMoveService.processCandidateMass(
				jrId,
				candidateId,
				vacancyId,
				getApiClient("/recruiting", username, password, domain),
				getApiClient("/funnel", username, password, domain)
		);
	}

	protected static ApiClient getApiClient(String baseUrl, String username, String password, String domain) {
		ApiClient apiClient = new ApiClient();
		String token = getToken(username, password, domain);
		apiClient.setBearerToken(token);
		apiClient.setBasePath(domain + "/api" + baseUrl);
		return apiClient;
	}

	@SneakyThrows
	public static TokenDto oauthToken(String formData, String url) {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url + "/api/auth/oauth/token"))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.header("Accept", "application/json, text/plain, */*")
				.POST(HttpRequest.BodyPublishers.ofString(formData))
				.build();

		HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

		return new ObjectMapper().readValue(response.body(), TokenDto.class);
	}

	public static String getToken(String username, String password, String url) {
		val formData = getFormData(username, password, null);
		val tokenDto = oauthToken(formData, url);
		return tokenDto.getAccessToken();
	}

	private static String getFormData(String username, String password, String code) {

		Map<String, String> formData = new HashMap<>();
		formData.put("grant_type", "password");
		formData.put("username", username);
		formData.put("password", password);
		if (code != null) {
			formData.put("code", code);
		}

		StringBuilder formBodyBuilder = new StringBuilder();

		for (Map.Entry<String, String> singleEntry : formData.entrySet()) {
			if (!formBodyBuilder.isEmpty()) {
				formBodyBuilder.append("&");
			}
			formBodyBuilder.append(URLEncoder.encode(singleEntry.getKey(), StandardCharsets.UTF_8));
			formBodyBuilder.append("=");
			formBodyBuilder.append(URLEncoder.encode(singleEntry.getValue(), StandardCharsets.UTF_8));
		}
		return formBodyBuilder.toString();
	}
}

