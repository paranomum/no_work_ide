package util;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public final class CertImporter {

	private CertImporter() {}

	/**
	 * Подключается к домену, получает цепочку сертификатов и добавляет их в keystore.
	 *
	 * @param domain          доменное имя (без схемы и пути, например "example.com")
	 * @param port            порт, обычно 443
	 * @param keystorePath    путь к файлу JKS или PKCS12
	 * @param keystorePassword пароль к keystorе
	 * @param keystoreType    "JKS" или "PKCS12"
	 * @return список добавленных алиасов
	 * @throws Exception при ошибке соединения или работы с keystore
	 */
	public static List<String> importCertsFromDomain(
			String domain,
			int port,
			String keystorePath,
			String keystorePassword,
			String keystoreType
	) throws Exception {

		// 1. Получаем цепочку сертификатов через TLS-соединение
		X509Certificate[] chain = fetchCertChain(domain, port);

		if (chain == null || chain.length == 0) {
			throw new IOException("Не удалось получить цепочку сертификатов от " + domain + ":" + port);
		}

		// 2. Загружаем существующий keystore
		KeyStore ks = KeyStore.getInstance(keystoreType);
		char[] password = keystorePassword.toCharArray();

		java.io.File ksFile = new java.io.File(keystorePath);
		if (ksFile.exists()) {
			try (FileInputStream fis = new FileInputStream(ksFile)) {
				ks.load(fis, password);
			}
		} else {
			// Создаём новый пустой keystore
			ks.load(null, password);
		}

		// 3. Добавляем каждый сертификат цепочки
		List<String> addedAliases = new ArrayList<>();
		for (int i = 0; i < chain.length; i++) {
			X509Certificate cert = chain[i];
			// Алиас: domain_i_CN
			String cn = extractCN(cert.getSubjectX500Principal().getName());
			String alias = (domain + "_" + i + "_" + cn)
					.toLowerCase()
					.replaceAll("[^a-z0-9_\\-.]", "_");

			if (ks.containsAlias(alias)) {
				// Уже есть — пропускаем
				continue;
			}

			ks.setCertificateEntry(alias, cert);
			addedAliases.add(alias);
		}

		// 4. Сохраняем keystore обратно в файл
		try (FileOutputStream fos = new FileOutputStream(ksFile)) {
			ks.store(fos, password);
		}

		return addedAliases;
	}

	/**
	 * Открывает TLS-соединение и получает цепочку сертификатов, не проверяя их валидность.
	 */
	private static X509Certificate[] fetchCertChain(String domain, int port) throws Exception {
		// TrustManager, который принимает всё и сохраняет цепочку
		final X509Certificate[][] chainHolder = new X509Certificate[1][];

		TrustManager[] trustAll = new TrustManager[]{
				new X509TrustManager() {
					@Override
					public void checkClientTrusted(X509Certificate[] chain, String authType) {}

					@Override
					public void checkServerTrusted(X509Certificate[] chain, String authType) {
						chainHolder[0] = chain;
					}

					@Override
					public X509Certificate[] getAcceptedIssuers() {
						return new X509Certificate[0];
					}
				}
		};

		SSLContext sslCtx = SSLContext.getInstance("TLS");
		sslCtx.init(null, trustAll, new java.security.SecureRandom());
		SSLSocketFactory factory = sslCtx.getSocketFactory();

		try (Socket raw = new Socket();) {
			raw.connect(new InetSocketAddress(domain, port), 5000);
			try (SSLSocket ssl = (SSLSocket) factory.createSocket(raw, domain, port, true)) {
				ssl.startHandshake();
			}
		}

		return chainHolder[0];
	}

	/**
	 * Извлекает значение CN= из строки вида "CN=example.com, O=..., C=...".
	 */
	private static String extractCN(String dn) {
		if (dn == null) return "unknown";
		for (String part : dn.split(",")) {
			String trimmed = part.trim();
			if (trimmed.startsWith("CN=")) {
				return trimmed.substring(3).trim();
			}
		}
		return "unknown";
	}
}