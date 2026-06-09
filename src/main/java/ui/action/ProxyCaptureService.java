package ui.action;

import com.google.gson.Gson;
import dto.AppConfig;
import dto.BackendRequestDef;
import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.core.har.HarEntry;
import net.lightbody.bmp.core.har.HarRequest;
import net.lightbody.bmp.proxy.CaptureType;
import org.openqa.selenium.Proxy;
import ui.ActionWindow;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ProxyCaptureService {

	private final Gson gson = new Gson();

	private BrowserMobProxy proxy;
	private boolean captureActive = false;
	private final AppConfig config;
	private final ConfigService configService;

	public ProxyCaptureService(AppConfig config, ConfigService configService) {
		this.config = config;
		this.configService = configService;
	}

	public void startProxy() {
		if (proxy != null && proxy.isStarted()) {
			return;
		}

		applyTrustStoreSettings();

		System.out.println("== TLS DEBUG ==");
		System.out.println("trustStore     = " + System.getProperty("javax.net.ssl.trustStore"));
		System.out.println("trustStorePass = " + System.getProperty("javax.net.ssl.trustStorePassword"));
		System.out.println("trustStoreType = " + System.getProperty("javax.net.ssl.trustStoreType"));

		proxy = new BrowserMobProxyServer();
		proxy.enableHarCaptureTypes(
				CaptureType.REQUEST_HEADERS,
				CaptureType.REQUEST_CONTENT,
				CaptureType.RESPONSE_HEADERS,
				CaptureType.RESPONSE_CONTENT
		);
		proxy.start(0);
	}

	public Proxy createSeleniumProxy() {
		if (proxy == null || !proxy.isStarted()) {
			throw new IllegalStateException("Proxy is not started");
		}
		return ClientUtil.createSeleniumProxy(proxy);
	}

	public void startCapture(String harLabel) {
		if (proxy == null || !proxy.isStarted()) {
			throw new IllegalStateException("Proxy is not started");
		}
		proxy.newHar(harLabel != null ? harLabel : "capture");
		captureActive = true;
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
			String url = req.getUrl();
			if (url == null || url.isBlank()) {
				continue;
			}

			String method = req.getMethod() != null
					? req.getMethod().toString().toUpperCase()
					: "UNKNOWN";

			if (!isFetchOrXhrLike(entry, req, method, url)) {
				continue;
			}

			String body = extractBody(req);
			String headersJson = extractHeaders(req);

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
			result.add(def);
		}

		return result;
	}

	private boolean isFetchOrXhrLike(HarEntry entry, HarRequest req, String method, String url) {
		String lowerUrl = url.toLowerCase();

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

	private String extractBody(HarRequest req) {
		try {
			if (req.getPostData() != null) {
				if (req.getPostData().getText() != null && !req.getPostData().getText().isBlank()) {
					return req.getPostData().getText();
				}

				if (req.getPostData().getParams() != null && !req.getPostData().getParams().isEmpty()) {
					Map<String, Object> paramsMap = new LinkedHashMap<>();
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
}