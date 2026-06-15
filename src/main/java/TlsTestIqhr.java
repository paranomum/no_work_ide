//import javax.net.ssl.HttpsURLConnection;
//import java.net.URL;
//
//public class TlsTestIqhr {
//	public static void main(String[] args) throws Exception {
//		System.setProperty("javax.net.ssl.trustStore", "/Users/admin/Downloads/my-truststore.jks");
//		System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
//		System.setProperty("javax.net.ssl.trustStoreType", "JKS"); // раз keytool показывает JKS
//
//		URL url = new URL("https://test-iqhr.rt.ru/");
//		HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
//		conn.setConnectTimeout(15000);
//		conn.setReadTimeout(15000);
//		System.out.println("Response code: " + conn.getResponseCode());
//		conn.disconnect();
//	}
//}