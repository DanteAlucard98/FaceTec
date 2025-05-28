package com.example.flutter_facetec_sample_app;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import java.util.concurrent.TimeUnit;

public class NetworkingHelpers {
    private static OkHttpClient apiClient;

    public static OkHttpClient getApiClient() {
        if (apiClient == null) {
            apiClient = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build();
        }
        return apiClient;
    }

    public static void cancelPendingRequests() {
        if (apiClient != null) {
            apiClient.dispatcher().executorService().shutdown();
            apiClient.dispatcher().cancelAll();
        }
    }
} 