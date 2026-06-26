package com.example.inventory_system_ht.network;

import android.content.Context;

import com.example.inventory_system_ht.BuildConfig;
import com.example.inventory_system_ht.util.PrefManager;
import com.franmontiel.persistentcookiejar.ClearableCookieJar;
import com.franmontiel.persistentcookiejar.PersistentCookieJar;
import com.franmontiel.persistentcookiejar.cache.SetCookieCache;
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor;

import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static Retrofit retrofit = null;
    private static Retrofit retrofitLong = null;

    public static Retrofit getClient(Context context) {
        PrefManager prefManager = new PrefManager(context);
        String baseUrl = prefManager.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalStateException("Base URL is not configured.");
        }

        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BODY
                : HttpLoggingInterceptor.Level.NONE);

        Interceptor authInterceptor = chain -> {
            Request originalRequest = chain.request();
            String token = prefManager.getToken();
            Request.Builder builder = originalRequest.newBuilder()
                    .header("Accept", "application/json");
            if (token != null && !token.isEmpty())
                builder.header("Authorization", "Bearer " + token);
            return chain.proceed(builder.build());
        };

        ClearableCookieJar cookieJar = new PersistentCookieJar(
                new SetCookieCache(),
                new SharedPrefsCookiePersistor(context)
        );

        String normalizedBase = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";

        OkHttpClient client = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .addInterceptor(loggingInterceptor)
                .addInterceptor(authInterceptor)
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();

        if (retrofit == null || !retrofit.baseUrl().toString().equals(normalizedBase)) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(normalizedBase)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            retrofitLong = null;
        }
        return retrofit;
    }

    public static Retrofit getClientLongTimeout(Context context) {
        PrefManager prefManager = new PrefManager(context);
        String baseUrl = prefManager.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            throw new IllegalStateException("Base URL is not configured.");
        }

        if (retrofitLong != null && retrofitLong.baseUrl().toString().equals(baseUrl)) {
            return retrofitLong;
        }

        Interceptor authInterceptor = chain -> {
            String token = prefManager.getToken();
            Request.Builder builder = chain.request().newBuilder()
                    .header("Accept", "application/json");
            if (token != null && !token.isEmpty())
                builder.header("Authorization", "Bearer " + token);
            return chain.proceed(builder.build());
        };

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        retrofitLong = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofitLong;
    }
}