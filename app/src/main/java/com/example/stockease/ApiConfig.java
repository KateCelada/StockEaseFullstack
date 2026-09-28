package com.example.stockease;

/**
 * Centralized API Configuration for StockEase.
 *
 * Switch BASE_URL in one place:
 * - Android Emulator: "http://10.0.2.2/stockease_api/"
 * - Local Wi-Fi (Physical Devices): "http://<YOUR_LOCAL_IP>/stockease_api/" (e.g. "http://192.168.1.5/stockease_api/")
 * - Production Server: "https://yourdomain.com/stockease_api/"
 */
public final class ApiConfig {

    private ApiConfig() {
        // Prevent instantiation
    }

    public static final String BASE_URL = "http://10.0.2.2/stockease_api/";

    /**
     * Builds a full URL for the specified endpoint or relative path.
     *
     * @param endpoint The API endpoint (e.g., "login.php" or "/login.php")
     * @return Complete URL
     */
    public static String getUrl(String endpoint) {
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return BASE_URL;
        }
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return endpoint;
        }
        if (endpoint.startsWith("/")) {
            return BASE_URL + endpoint.substring(1);
        }
        return BASE_URL + endpoint;
    }
}
