package util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class SimpleSecretService {
	private static final String AES = "AES";
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int IV_LENGTH = 12;
	private static final int TAG_LENGTH = 128;

	private static final String APP_SECRET = "TestRecorder_Jaga_Secret_2026";

	private static SecretKey getKey() {
		try {
			String seed = APP_SECRET
					+ "|"
					+ System.getProperty("user.name", "")
					+ "|"
					+ System.getProperty("os.name", "");

			byte[] hash = MessageDigest.getInstance("SHA-256")
					.digest(seed.getBytes(StandardCharsets.UTF_8));

			byte[] keyBytes = Arrays.copyOf(hash, 16); // AES-128
			return new SecretKeySpec(keyBytes, AES);
		} catch (Exception e) {
			throw new RuntimeException("Cannot initialize encryption key", e);
		}
	}

	public static String encrypt(String plainText) {
		if (plainText == null || plainText.isBlank()) {
			return "";
		}

		try {
			byte[] iv = new byte[IV_LENGTH];
			new SecureRandom().nextBytes(iv);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, getKey(), new GCMParameterSpec(TAG_LENGTH, iv));

			byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

			return Base64.getEncoder().encodeToString(iv) + ":"
					+ Base64.getEncoder().encodeToString(encrypted);
		} catch (Exception e) {
			throw new RuntimeException("Password encryption failed", e);
		}
	}

	public static String decrypt(String encryptedValue) {
		if (encryptedValue == null || encryptedValue.isBlank()) {
			return "";
		}

		try {
			String[] parts = encryptedValue.split(":");
			if (parts.length != 2) {
				throw new IllegalArgumentException("Invalid encrypted password format");
			}

			byte[] iv = Base64.getDecoder().decode(parts[0]);
			byte[] encrypted = Base64.getDecoder().decode(parts[1]);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(TAG_LENGTH, iv));

			byte[] decrypted = cipher.doFinal(encrypted);
			return new String(decrypted, StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new RuntimeException("Password decryption failed", e);
		}
	}
}