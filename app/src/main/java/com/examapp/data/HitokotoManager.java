package com.examapp.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class HitokotoManager {
    private static final String HITOKOTO_API = "https://v1.hitokoto.cn/";

    public static String getHitokoto() {
        try {
            URL url = new URL(HITOKOTO_API);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                JsonObject jsonObject = new Gson().fromJson(response.toString(), JsonObject.class);
                String hitokoto = jsonObject.get("hitokoto").getAsString();
                String from = jsonObject.get("from").getAsString();
                return hitokoto + "\n—— " + from;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "继续加油！";
    }
}
