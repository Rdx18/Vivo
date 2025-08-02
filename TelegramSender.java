package com.example.telegramforwarder;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class TelegramSender {

    private static final String TELEGRAM_BOT_TOKEN = "8400096846:AAErUkhr4eOPHM7qiFRnbyrDVnlUBZtYl8I";
    private static final String TELEGRAM_CHAT_ID = "6650114658";

    public static void sendMessage(Context context, String message) {
        new SendMessageTask().execute(message);
    }

    private static class SendMessageTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... params) {
            try {
                String encodedMessage = URLEncoder.encode(params[0], "UTF-8");
                String urlString = "https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN
                        + "/sendMessage?chat_id=" + TELEGRAM_CHAT_ID
                        + "&text=" + encodedMessage;

                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String inputLine;

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                Log.i("TelegramSender", "Message sent: " + response.toString());
            } catch (Exception e) {
                Log.e("TelegramSender", "Error sending message", e);
            }
            return null;
        }
    }
}
