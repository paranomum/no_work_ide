package util;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public final class CertImporter {

	private CertImporter() {}

	public static final class ImportResult {
		private final boolean changed;
		private final List<String> addedAliases;
		private final List<String> updatedAliases;
		private final List<String> unchangedAliases;
		private final List<String> removedAliases;

		public ImportResult(
				boolean changed,
				List<String> addedAliases,
				List<String> updatedAliases,
				List<String> unchangedAliases,
				List<String> removedAliases
		) {
			this.changed = changed;
			this.addedAliases = addedAliases;
			this.updatedAliases = updatedAliases;
			this.unchangedAliases = unchangedAliases;
			this.removedAliases = removedAliases;
		}

		public boolean isChanged() {
			return changed;
		}

		public List<String> getAddedAliases() {
			return addedAliases;
		}

		public List<String> getUpdatedAliases() {
			return updatedAliases;
		}

		public List<String> getUnchangedAliases() {
			return unchangedAliases;
		}

		public List<String> getRemovedAliases() {
			return removedAliases;
		}

		@Override
		public String toString() {
			return "ImportResult{" +
					"changed=" + changed +
					", addedAliases=" + addedAliases +
					", updatedAliases=" + updatedAliases +
					", unchangedAliases=" + unchangedAliases +
					", removedAliases=" + removedAliases +
					'}';
		}
	}

	/**
	 * Подключается к домену, получает текущую цепочку сертификатов и синхронизирует её с keystore.
	 *
	 * Логика:
	 * - если alias для позиции сертификата отсутствует -> добавляем
	 * - если alias есть, но fingerprint сертификата изменился -> обновляем
	 * - если alias есть и fingerprint тот же -> оставляем как есть
	 * - если в keystore есть старые alias этого домена, которых больше нет в текущей цепочке -> удаляем
	 *
	 * @param domain            доменное имя, например "example.com"
	 * @param port              порт, обычно 443
	 * @param keystorePath      путь к JKS или PKCS12
	 * @param keystorePassword  пароль keystore
	 * @param keystoreType      "JKS" или "PKCS12"
	 * @return результат импорта и сравнения
	 * @throws Exception при ошибках TLS или работы с keystore
	 */
	public static List<String> importCertsFromDomain(
			String domain,
			int port,
			String keystorePath,
			String keystorePassword,
			String keystoreType
	) throws Exception {

		X509Certificate[] chain = fetchCertChain(domain, port);
		if (chain == null || chain.length == 0) {
			throw new IOException("Не удалось получить цепочку сертификатов от " + domain + ":" + port);
		}

		KeyStore ks = KeyStore.getInstance(keystoreType);
		char[] password = keystorePassword.toCharArray();

		File ksFile = new File(keystorePath);
		if (ksFile.exists()) {
			try (FileInputStream fis = new FileInputStream(ksFile)) {
				ks.load(fis, password);
			}
		} else {
			ks.load(null, password);
		}

		String domainPrefix = normalizeAliasPart(domain) + "_";

		List<String> existingDomainAliases = findAliasesForDomain(ks, domainPrefix);

		List<String> addedAliases = new ArrayList<>();
		List<String> updatedAliases = new ArrayList<>();
		List<String> unchangedAliases = new ArrayList<>();
		List<String> expectedAliases = new ArrayList<>();

		for (int i = 0; i < chain.length; i++) {
			X509Certificate newCert = chain[i];
			String alias = buildAlias(domain, i, newCert);
			expectedAliases.add(alias);

			if (!ks.containsAlias(alias)) {
				ks.setCertificateEntry(alias, newCert);
				addedAliases.add(alias);
				continue;
			}

			X509Certificate existingCert = getCertificateAsX509(ks, alias);
			if (existingCert == null) {
				ks.setCertificateEntry(alias, newCert);
				updatedAliases.add(alias);
				continue;
			}

			String oldFingerprint = sha256Fingerprint(existingCert);
			String newFingerprint = sha256Fingerprint(newCert);

			if (oldFingerprint.equals(newFingerprint)) {
				unchangedAliases.add(alias);
			} else {
				ks.deleteEntry(alias);
				ks.setCertificateEntry(alias, newCert);
				updatedAliases.add(alias);
			}
		}

		List<String> removedAliases = new ArrayList<>();
		for (String existingAlias : existingDomainAliases) {
			if (!expectedAliases.contains(existingAlias)) {
				ks.deleteEntry(existingAlias);
				removedAliases.add(existingAlias);
			}
		}

		boolean changed = !addedAliases.isEmpty()
				|| !updatedAliases.isEmpty()
				|| !removedAliases.isEmpty();

		try (FileOutputStream fos = new FileOutputStream(ksFile)) {
			ks.store(fos, password);
		}

		return addedAliases;
	}

	/**
	 * Открывает TLS-соединение и получает цепочку сертификатов, не проверяя их валидность.
	 */
	private static X509Certificate[] fetchCertChain(String domain, int port) throws Exception {
		final X509Certificate[][] chainHolder = new X509Certificate[1][];

		TrustManager[] trustAll = new TrustManager[] {
				new X509TrustManager() {
					@Override
					public void checkClientTrusted(X509Certificate[] chain, String authType) {
					}

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
		sslCtx.init(null, trustAll, new SecureRandom());
		SSLSocketFactory factory = sslCtx.getSocketFactory();

		try (Socket raw = new Socket()) {
			raw.connect(new InetSocketAddress(domain, port), 5000);

			try (SSLSocket ssl = (SSLSocket) factory.createSocket(raw, domain, port, true)) {
				ssl.setUseClientMode(true);
				ssl.startHandshake();
			}
		}

		return chainHolder[0];
	}

	private static List<String> findAliasesForDomain(KeyStore ks, String domainPrefix) throws Exception {
		List<String> aliases = new ArrayList<>();
		Enumeration<String> enumeration = ks.aliases();
		while (enumeration.hasMoreElements()) {
			String alias = enumeration.nextElement();
			if (alias != null && alias.startsWith(domainPrefix)) {
				aliases.add(alias);
			}
		}
		return aliases;
	}

	private static X509Certificate getCertificateAsX509(KeyStore ks, String alias) throws Exception {
		java.security.cert.Certificate cert = ks.getCertificate(alias);
		if (cert instanceof X509Certificate) {
			return (X509Certificate) cert;
		}
		return null;
	}

	private static String buildAlias(String domain, int index, X509Certificate cert) {
		String cn = extractCN(cert.getSubjectX500Principal().getName());
		return (normalizeAliasPart(domain) + "_" + index + "_" + normalizeAliasPart(cn));
	}

	private static String normalizeAliasPart(String value) {
		if (value == null || value.isBlank()) {
			return "unknown";
		}
		return value.toLowerCase().replaceAll("[^a-z0-9_\\-.]", "_");
	}

	/**
	 * Извлекает CN из DN строки вида "CN=example.com, O=..., C=...".
	 */
	private static String extractCN(String dn) {
		if (dn == null) {
			return "unknown";
		}

		for (String part : dn.split(",")) {
			String trimmed = part.trim();
			if (trimmed.startsWith("CN=")) {
				String cn = trimmed.substring(3).trim();
				return cn.isEmpty() ? "unknown" : cn;
			}
		}

		return "unknown";
	}

	/**
	 * SHA-256 fingerprint сертификата.
	 */
	private static String sha256Fingerprint(X509Certificate cert) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		byte[] digest = md.digest(cert.getEncoded());

		StringBuilder sb = new StringBuilder(digest.length * 2);
		for (byte b : digest) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}
}