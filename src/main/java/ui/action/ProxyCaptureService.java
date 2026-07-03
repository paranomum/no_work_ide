package ui.action;

import com.google.gson.Gson;
import dto.AppConfig;
import dto.BackendRequestDef;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.core.har.HarEntry;
import net.lightbody.bmp.core.har.HarRequest;
import net.lightbody.bmp.core.har.HarResponse;
import net.lightbody.bmp.proxy.CaptureType;
import org.openqa.selenium.Proxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ui.ActionWindow;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ProxyCaptureService {

	private static final Logger log = LoggerFactory.getLogger(ProxyCaptureService.class);
	private final Gson gson = new Gson();
	private final AppConfig config;
	private final ConfigService configService;
	private BrowserMobProxy proxy;
	private boolean captureActive = false;

	public ProxyCaptureService(AppConfig config, ConfigService configService) {
		this.config = config;
		this.configService = configService;
	}

	public void startProxy() {
		if (proxy != null && proxy.isStarted()) {
			return;
		}

		applyTrustStoreSettings();

		log.info("TLS settings: trustStore={}, trustStoreType={}",
				System.getProperty("javax.net.ssl.trustStore"),
				System.getProperty("javax.net.ssl.trustStoreType"));

		proxy = new BrowserMobProxyServer();
		proxy.enableHarCaptureTypes(
				CaptureType.REQUEST_HEADERS,
				CaptureType.REQUEST_CONTENT,
				CaptureType.RESPONSE_HEADERS,
				CaptureType.RESPONSE_CONTENT
		);
		proxy.start(0);

		log.info("BrowserMob started: started={}, port={}",
				proxy.isStarted(),
				proxy.getPort());
	}

	public Proxy createSeleniumProxy() {
		if (proxy == null || !proxy.isStarted()) {
			throw new IllegalStateException("Proxy is not started");
		}

		int port = proxy.getPort();
		String hostPort = "127.0.0.1:" + port;
		String noProxyHosts = "autofaq-hr.rt.ru,localhost,127.0.0.1";

		Proxy seleniumProxy = new Proxy();
		seleniumProxy.setProxyType(Proxy.ProxyType.MANUAL);
		seleniumProxy.setHttpProxy(hostPort);
		seleniumProxy.setSslProxy(hostPort);
		seleniumProxy.setNoProxy(noProxyHosts);

		log.info("Selenium proxy configured: proxy={}", hostPort);
		return seleniumProxy;
	}

	public void startCapture(String harLabel) {
		if (proxy == null || !proxy.isStarted()) {
			throw new IllegalStateException("Proxy is not started");
		}

		proxy.newHar(harLabel != null ? harLabel : "capture");
		captureActive = true;
		log.info("Capture started: {}", harLabel);
	}

	public boolean isCaptureActive() {
		return captureActive;
	}

	public List<BackendRequestDef> stopCaptureAndReadRequests() {
		captureActive = false;

		if (proxy == null || !proxy.isStarted()) {
			return List.of();
		}

		Har har = proxy.getHar();
		if (har == null || har.getLog() == null || har.getLog().getEntries() == null) {
			return List.of();
		}

		List<BackendRequestDef> result = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();

		for (HarEntry entry : har.getLog().getEntries()) {
			if (entry == null || entry.getRequest() == null) {
				continue;
			}

			HarRequest req = entry.getRequest();
			String fullUrl = req.getUrl();
			if (fullUrl == null || fullUrl.isBlank()) {
				continue;
			}

			if (!matchesSelectedDomain(fullUrl)) {
				continue;
			}

			String url = extractPathWithQuery(fullUrl);

			String method = req.getMethod() != null
					? req.getMethod().toUpperCase(Locale.ROOT)
					: "UNKNOWN";

			if (!isFetchOrXhrLike(entry, req, method, fullUrl)) {
				continue;
			}

			String bodyType = detectBodyType(req);
			List<dto.FormDataParam> formData = "FORM_URLENCODED".equals(bodyType)
					? extractFormData(req)
					: new ArrayList<>();
			String body = extractBody(req, bodyType, formData);
			String headersJson = extractHeaders(req);
			String responseBody = extractResponseBody(entry);
			String token = extractTokenFromCookies(req);

			String dedupeKey = method + "|" + url + "|" + body;
			if (!seen.add(dedupeKey)) {
				continue;
			}

			BackendRequestDef def = new BackendRequestDef(
					deriveName(url, method),
					url,
					method,
					body,
					headersJson,
					LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
			);
			def.setCapturedResponseBody(responseBody);
			def.setBodyType(bodyType);
			def.setFormData(formData);
			def.setToken(token);
			result.add(def);

			log.info(
					"Captured request: method={}, url={}, bodyType={}, requestBodyLength={}, responseBodyLength={}, formDataSize={}, tokenPresent={}",
					method,
					url,
					bodyType,
					body != null ? body.length() : 0,
					responseBody != null ? responseBody.length() : 0,
					formData != null ? formData.size() : 0,
					token != null && !token.isBlank()
			);
		}

		log.info("Capture finished. Total backend-like requests captured: {}", result.size());
		return result;
	}

	private String extractBody(HarRequest req, String bodyType, List<dto.FormDataParam> formData) {
		try {
			if (req.getPostData() == null) {
				return "";
			}

			if ("FORM_URLENCODED".equals(bodyType)) {
				String text = req.getPostData().getText();
				if (text != null && !text.isBlank()) {
					return text;
				}
				return buildFormUrlencodedBody(formData);
			}

			if (req.getPostData().getText() != null && !req.getPostData().getText().isBlank()) {
				return req.getPostData().getText();
			}

			if (req.getPostData().getParams() != null && !req.getPostData().getParams().isEmpty()) {
				Map<String, String> paramsMap = new LinkedHashMap<>();
				req.getPostData().getParams().forEach(p -> {
					String name = p.getName() != null ? p.getName() : "";
					String value = p.getValue() != null ? p.getValue() : "";
					paramsMap.put(name, value);
				});
				return gson.toJson(paramsMap);
			}

			if (req.getPostData().getMimeType() != null && !req.getPostData().getMimeType().isBlank()) {
				return "[body captured without text, mimeType=" + req.getPostData().getMimeType() + "]";
			}
		} catch (Exception ignored) {
		}
		return "";
	}

	private String buildFormUrlencodedBody(List<dto.FormDataParam> params) {
		if (params == null || params.isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		for (dto.FormDataParam param : params) {
			if (param == null) {
				continue;
			}

			String key = param.getKey() != null ? param.getKey().trim() : "";
			if (key.isEmpty()) {
				continue;
			}

			String value = param.getValue() != null ? param.getValue() : "";

			if (!sb.isEmpty()) {
				sb.append("&");
			}
			sb.append(java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8));
			sb.append("=");
			sb.append(java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8));
		}

		return sb.toString();
	}

	private String extractTokenFromCookies(HarRequest req) {
		String cookieHeader = getHeaderIgnoreCase(req, "Cookie");
		if (cookieHeader == null || cookieHeader.isBlank()) {
			return "";
		}

		String[] parts = cookieHeader.split(";");
		for (String part : parts) {
			if (part == null || part.isBlank()) {
				continue;
			}

			String[] kv = part.trim().split("=", 2);
			String key = kv.length > 0 ? kv[0].trim() : "";
			String value = kv.length > 1 ? kv[1].trim() : "";

			if ("token".equalsIgnoreCase(key)) {
				return value;
			}
		}

		return "";
	}

	private boolean isFetchOrXhrLike(HarEntry entry, HarRequest req, String method, String url) {
		String lowerUrl = url.toLowerCase(Locale.ROOT);

		if (isStaticResource(lowerUrl)) {
			return false;
		}

		if ("OPTIONS".equals(method)) {
			return false;
		}

		String accept = getHeaderIgnoreCase(req, "Accept");
		String xrw = getHeaderIgnoreCase(req, "X-Requested-With");
		String contentType = getPostMimeType(req);

		if (xrw != null && xrw.toLowerCase().contains("xmlhttprequest")) {
			return true;
		}

		if (contentType != null) {
			String ct = contentType.toLowerCase();
			if (ct.contains("application/json")
					|| ct.contains("application/xml")
					|| ct.contains("text/xml")
					|| ct.contains("application/x-www-form-urlencoded")
					|| ct.contains("multipart/form-data")) {
				return true;
			}
		}

		if (accept != null) {
			String a = accept.toLowerCase();
			if (a.contains("application/json")
					|| a.contains("text/plain")
					|| a.contains("application/xml")
					|| a.contains("*/*")) {
				if (!lowerUrl.endsWith(".js")
						&& !lowerUrl.endsWith(".css")
						&& !lowerUrl.endsWith(".png")
						&& !lowerUrl.endsWith(".jpg")
						&& !lowerUrl.endsWith(".jpeg")
						&& !lowerUrl.endsWith(".svg")
						&& !lowerUrl.endsWith(".gif")
						&& !lowerUrl.endsWith(".woff")
						&& !lowerUrl.endsWith(".woff2")
						&& !lowerUrl.endsWith(".ico")
						&& !lowerUrl.endsWith(".map")) {
					return true;
				}
			}
		}

		if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method)) {
			return true;
		}

		return lowerUrl.contains("/api/")
				|| lowerUrl.contains("/rest/")
				|| lowerUrl.contains("/graphql")
				|| lowerUrl.contains("/backend/")
				|| lowerUrl.contains("/ajax/")
				|| lowerUrl.contains("/rpc/");
	}

	private boolean isStaticResource(String lowerUrl) {
		return lowerUrl.endsWith(".js")
				|| lowerUrl.endsWith(".css")
				|| lowerUrl.endsWith(".png")
				|| lowerUrl.endsWith(".jpg")
				|| lowerUrl.endsWith(".jpeg")
				|| lowerUrl.endsWith(".gif")
				|| lowerUrl.endsWith(".svg")
				|| lowerUrl.endsWith(".ico")
				|| lowerUrl.endsWith(".woff")
				|| lowerUrl.endsWith(".woff2")
				|| lowerUrl.endsWith(".ttf")
				|| lowerUrl.endsWith(".map")
				|| lowerUrl.endsWith(".mp4")
				|| lowerUrl.endsWith(".webm")
				|| lowerUrl.endsWith(".mp3")
				|| lowerUrl.endsWith(".wav")
				|| lowerUrl.endsWith(".pdf")
				|| lowerUrl.endsWith(".zip");
	}

	private String getHeaderIgnoreCase(HarRequest req, String headerName) {
		try {
			if (req.getHeaders() == null) {
				return null;
			}
			for (var h : req.getHeaders()) {
				if (h.getName() != null && h.getName().equalsIgnoreCase(headerName)) {
					return h.getValue();
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private String getPostMimeType(HarRequest req) {
		try {
			if (req.getPostData() != null && req.getPostData().getMimeType() != null) {
				return req.getPostData().getMimeType();
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	public List<BackendRequestDef> findCapturedRequestsByUrlPart(String urlPart) {
		List<BackendRequestDef> all = stopCaptureAndReadRequests();
		if (urlPart == null || urlPart.isBlank()) {
			return all;
		}

		List<BackendRequestDef> filtered = new ArrayList<>();
		for (BackendRequestDef def : all) {
			if (def.getUrl() != null && def.getUrl().contains(urlPart)) {
				filtered.add(def);
			}
		}
		return filtered;
	}

	public void stopProxy() {
		captureActive = false;
		if (proxy != null) {
			try {
				proxy.stop();
			} catch (Exception ignored) {
			}
			proxy = null;
		}
	}

	public BrowserMobProxy getProxy() {
		return proxy;
	}

	private String detectBodyType(HarRequest req) {
		String mimeType = getPostMimeType(req);
		if (mimeType == null || mimeType.isBlank()) {
			return "JSON";
		}

		String lower = mimeType.toLowerCase(Locale.ROOT);
		if (lower.contains("application/x-www-form-urlencoded")) {
			return "FORM_URLENCODED";
		}

		return "JSON";
	}

	private List<dto.FormDataParam> extractFormData(HarRequest req) {
		List<dto.FormDataParam> result = new ArrayList<>();
		try {
			if (req.getPostData() == null) {
				return result;
			}

			if (req.getPostData().getParams() != null && !req.getPostData().getParams().isEmpty()) {
				req.getPostData().getParams().forEach(p -> {
					String key = p.getName() != null ? p.getName() : "";
					String value = p.getValue() != null ? p.getValue() : "";
					result.add(new dto.FormDataParam(key, value));
				});
				return result;
			}

			String text = req.getPostData().getText();
			if (text != null && !text.isBlank()) {
				for (String pair : text.split("&")) {
					if (pair.isBlank()) continue;
					String[] parts = pair.split("=", 2);
					String key = java.net.URLDecoder.decode(parts[0], java.nio.charset.StandardCharsets.UTF_8);
					String value = parts.length > 1
							? java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8)
							: "";
					result.add(new dto.FormDataParam(key, value));
				}
			}
		} catch (Exception ignored) {
		}
		return result;
	}

	private String extractBody(HarRequest req) {
		try {
			if (req.getPostData() != null) {
				if (req.getPostData().getText() != null && !req.getPostData().getText().isBlank()) {
					return req.getPostData().getText();
				}

				if (req.getPostData().getParams() != null && !req.getPostData().getParams().isEmpty()) {
					Map<String, String> paramsMap = new LinkedHashMap<>();
					req.getPostData().getParams().forEach(p -> {
						String name = p.getName() != null ? p.getName() : "";
						String value = p.getValue() != null ? p.getValue() : "";
						paramsMap.put(name, value);
					});
					return gson.toJson(paramsMap);
				}

				if (req.getPostData().getMimeType() != null && !req.getPostData().getMimeType().isBlank()) {
					return "[body captured without text, mimeType=" + req.getPostData().getMimeType() + "]";
				}
			}
		} catch (Exception ignored) {
		}
		return "";
	}

	private String extractHeaders(HarRequest req) {
		Map<String, String> headers = new LinkedHashMap<>();
		try {
			if (req.getHeaders() != null) {
				req.getHeaders().forEach(h -> headers.put(h.getName(), h.getValue()));
			}
		} catch (Exception ignored) {
		}
		return gson.toJson(headers);
	}

	private String deriveName(String url, String method) {
		try {
			java.net.URI uri = java.net.URI.create(url);
			String path = uri.getPath();
			if (path == null || path.isBlank() || "/".equals(path)) {
				return method + " root";
			}

			String[] parts = path.split("/");
			for (int i = parts.length - 1; i >= 0; i--) {
				if (parts[i] != null && !parts[i].isBlank()) {
					return method + " " + parts[i];
				}
			}
		} catch (Exception ignored) {
		}
		return method + " request";
	}

	private void applyTrustStoreSettings() {
		if (config == null) {
			return;
		}

		if (config.trustStorePath != null && !config.trustStorePath.isBlank()) {
			System.setProperty("javax.net.ssl.trustStore", config.trustStorePath.trim());
		}

		if (config.trustStorePassword != null && !config.trustStorePassword.isBlank()) {
			System.setProperty("javax.net.ssl.trustStorePassword", config.trustStorePassword);
		}

		if (config.trustStoreType != null && !config.trustStoreType.isBlank()) {
			System.setProperty("javax.net.ssl.trustStoreType", config.trustStoreType.trim());
		}
	}

	public void selectTrustStore(ActionWindow actionWindow,
								 JTextField trustStorePathField,
								 JTextField trustStorePasswordField,
								 JTextField trustStoreTypeField) {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fileChooser.setDialogTitle("Select TrustStore");

		fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
			@Override
			public boolean accept(File file) {
				if (file.isDirectory()) return true;
				String name = file.getName().toLowerCase();
				return name.endsWith(".jks")
						|| name.endsWith(".p12")
						|| name.endsWith(".pfx")
						|| name.endsWith(".cacerts");
			}

			@Override
			public String getDescription() {
				return "TrustStore file (*.jks, *.p12, *.pfx, *.cacerts)";
			}
		});

		int result = fileChooser.showOpenDialog(actionWindow);

		if (result == JFileChooser.APPROVE_OPTION) {
			File selectedFile = fileChooser.getSelectedFile();
			String path = selectedFile.getAbsolutePath();
			trustStorePathField.setText(path);
			System.out.println("Selected TrustStore: " + path);

			String lower = selectedFile.getName().toLowerCase();
			if (lower.endsWith(".p12") || lower.endsWith(".pfx")) {
				trustStoreTypeField.setText("PKCS12");
				config.trustStoreType = "PKCS12";
			} else {
				trustStoreTypeField.setText("JKS");
				config.trustStoreType = "JKS";
			}

			if (trustStorePasswordField.getText() == null || trustStorePasswordField.getText().isBlank()) {
				trustStorePasswordField.setText("changeit");
			}

			config.trustStorePath = path;
			config.trustStorePassword = trustStorePasswordField.getText().trim();

			try {
				configService.save(config);
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}

	private String extractResponseBody(HarEntry entry) {
		try {
			if (entry == null) {
				return "";
			}

			HarResponse response = entry.getResponse();
			if (response == null) {
				log.warn("HAR response is null");
				return "";
			}

			if (response.getContent() == null) {
				log.warn("HAR response content is null for url={}",
						entry.getRequest() != null ? entry.getRequest().getUrl() : "unknown");
				return "";
			}

			String mimeType = response.getContent().getMimeType();
			String encoding = response.getContent().getEncoding();
			String text = response.getContent().getText();

			log.info("Response capture info: url={}, status={}, mimeType={}, encoding={}, textLength={}",
					entry.getRequest() != null ? entry.getRequest().getUrl() : "unknown",
					response.getStatus(),
					mimeType,
					encoding,
					text != null ? text.length() : 0);

			if (text == null || text.isBlank()) {
				return "";
			}

			if (encoding != null && encoding.equalsIgnoreCase("base64")) {
				try {
					byte[] decoded = Base64.getDecoder().decode(text);
					return new String(decoded, StandardCharsets.UTF_8);
				} catch (Exception ex) {
					log.warn("Failed to decode base64 response body: {}", ex.getMessage());
					return text;
				}
			}

			return text;
		} catch (Exception ex) {
			log.warn("Failed to extract response body: {}", ex.getMessage(), ex);
			return "";
		}
	}

	private String extractPathWithQuery(String fullUrl) {
		try {
			java.net.URI uri = java.net.URI.create(fullUrl);

			String path = uri.getRawPath();
			String query = uri.getRawQuery();

			if (path == null || path.isBlank()) {
				path = "/";
			}

			return (query != null && !query.isBlank())
					? path + "?" + query
					: path;
		} catch (Exception ex) {
			log.warn("Failed to extract path from url={}", fullUrl, ex);
			return fullUrl;
		}
	}

	private boolean matchesSelectedDomain(String fullUrl) {
		String selectedDomain = config != null && config.selectedDomain != null
				? config.selectedDomain.trim()
				: "";

		if (selectedDomain.isEmpty()) {
			return true;
		}

		try {
			java.net.URI uri = java.net.URI.create(fullUrl);
			String host = uri.getHost();

			if (host == null || host.isBlank()) {
				return false;
			}

			host = host.trim().toLowerCase(Locale.ROOT);
			selectedDomain = selectedDomain.toLowerCase(Locale.ROOT);

			return host.equals(selectedDomain);
		} catch (Exception ex) {
			log.warn("Failed to match selected domain for url={}", fullUrl, ex);
			return false;
		}
	}
}