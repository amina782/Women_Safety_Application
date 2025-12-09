package com.example.sendmsgapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SMS_DEBUG";
    private static final int PERMISSION_REQUEST = 101;

    // UI elements
    AutoCompleteTextView contactDropdown;
    EditText messageText;
    ScrollView scrollView;
    FloatingActionButton micToggleButton;
    MaterialButton sendButton;

    // Vosk voice recognition
    Model voskModel;
    Recognizer recognizer;
    SpeechService speechService;
    boolean isListening = false;

    // Location
    LocationManager locationManager;
    double latitude = 0.0, longitude = 0.0;
    boolean locationFetched = false;

    // Shake detection
    SensorManager sensorManager;
    Sensor accelerometer;
    private long lastShakeTime = 0;
    private static final int SHAKE_THRESHOLD = 1200; // adjust for sensitivity
    private int shakeCount = 0;

    // Emergency voice trigger cooldown
    private long lastTriggered = 0;
    private static final long TRIGGER_COOLDOWN_MS = 30_000; // 30 seconds cooldown

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI references
        contactDropdown = findViewById(R.id.contactDropdown);
        messageText = findViewById(R.id.messageText);
        scrollView = findViewById(R.id.scrollView);
        micToggleButton = findViewById(R.id.micToggleButton);
        sendButton = findViewById(R.id.sendButton);

        // Request runtime permissions
        requestPermissions();

        // If permissions are already granted (from previous runs), initialize things
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            loadVoskModel();
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            getLocation();
        }

        // Sensor for shake detection
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Listeners
        sendButton.setOnClickListener(v -> sendSMS());
        micToggleButton.setOnClickListener(v -> toggleMic());

        contactDropdown.setThreshold(0);
        contactDropdown.setOnClickListener(v -> contactDropdown.showDropDown());
        contactDropdown.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) contactDropdown.showDropDown();
        });

        // Bottom navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_contacts) {
                startActivity(new Intent(MainActivity.this, ContactsActivity.class));
                return true;
            } else if (id == R.id.nav_chat) {
                startActivity(new Intent(MainActivity.this, AiChatActivity.class));
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadContacts();
        // register shake listener
        if (accelerometer != null)
            sensorManager.registerListener(shakeListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // unregister shake listener
        sensorManager.unregisterListener(shakeListener);
    }

    // ------------------- PERMISSIONS -------------------
    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.CAMERA
                },
                PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            boolean audio = grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED;
            boolean contacts = grantResults.length > 2 &&
                    grantResults[2] == PackageManager.PERMISSION_GRANTED;

            boolean fine = grantResults.length > 3 &&
                    grantResults[3] == PackageManager.PERMISSION_GRANTED;
            boolean coarse = grantResults.length > 4 &&
                    grantResults[4] == PackageManager.PERMISSION_GRANTED;
            boolean location = fine || coarse;

            if (location) getLocation();
            if (contacts) loadContacts();
            if (audio) loadVoskModel();
        }
    }

    // ------------------- LOCATION -------------------
    private void getLocation() {
        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            if (gps != null) {
                latitude = gps.getLatitude();
                longitude = gps.getLongitude();
                locationFetched = true;
            } else if (network != null) {
                latitude = network.getLatitude();
                longitude = network.getLongitude();
                locationFetched = true;
            } else locationFetched = false;

            // Keep updating location in background (best-effort)
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 1, location -> {
                latitude = location.getLatitude();
                longitude = location.getLongitude();
                locationFetched = true;
            });
        } catch (Exception e) {
            Toast.makeText(this, "Location Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ------------------- CONTACTS -------------------
    private void loadContacts() {
        ArrayList<String> allContacts = new ArrayList<>();
        ArrayList<String> emergencyContacts = new ArrayList<>();

        SharedPreferences prefs = getSharedPreferences("EmergencyContacts", MODE_PRIVATE);
        Set<String> emergencyNames = prefs.getStringSet("names", new HashSet<>());
        emergencyContacts.addAll(emergencyNames);

        // Load phone contacts
        Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Phone.HAS_PHONE_NUMBER + " = 1",
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
                if (!emergencyContacts.contains(name)) allContacts.add(name);
            }
            cursor.close();
        }

        ArrayList<String> finalList = new ArrayList<>();
        finalList.addAll(emergencyContacts);
        finalList.addAll(allContacts);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                R.layout.dropdown_item,
                R.id.dropdownText,
                finalList
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text = view.findViewById(R.id.dropdownText);
                if (position < emergencyContacts.size()) {
                    text.setTextColor(Color.RED);
                    text.setTypeface(null, Typeface.BOLD);
                } else {
                    text.setTextColor(Color.BLACK);
                }
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView text = view.findViewById(R.id.dropdownText);
                if (position < emergencyContacts.size()) {
                    text.setTextColor(Color.RED);
                    text.setTypeface(null, Typeface.BOLD);
                } else {
                    text.setTextColor(Color.BLACK);
                    text.setTypeface(null, Typeface.NORMAL);
                }
                return view;
            }
        };

        contactDropdown.setAdapter(adapter);
    }

    private String getPhoneNumber(String name) {
        if (name == null || name.trim().isEmpty()) return "";

        // First try phone contacts
        Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + "=?",
                new String[]{name}, null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                String number = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                cursor.close();
                return (number != null) ? number : "";
            }
            cursor.close();
        }

        // Fallback: check stored EmergencyContacts in SharedPreferences (name->number)
        SharedPreferences prefs = getSharedPreferences("EmergencyContacts", MODE_PRIVATE);
        return prefs.getString(name, "");
    }

    // ------------------- SEND SMS (normal send button) -------------------
    private void sendSMS() {
        getLocation();
        String name = contactDropdown.getText().toString().trim();
        String message = messageText.getText().toString();

        if (locationFetched)
            message += "\nMy Location: https://maps.google.com/?q=" + latitude + "," + longitude;
        else
            message += "\nLocation not available.";

        if (name.isEmpty()) {
            Toast.makeText(this, "Select a contact!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (message.isEmpty()) {
            Toast.makeText(this, "Type or speak a message!", Toast.LENGTH_SHORT).show();
            return;
        }

        String number = getPhoneNumber(name);
        if (number == null || number.trim().isEmpty()) {
            Toast.makeText(this, "Contact not found!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Clean number (remove spaces, parentheses, dashes but keep plus sign)
        number = number.replaceAll("[^0-9+]", "");

        // Log for debugging
        Log.e(TAG, "Manual send -> to: " + number + " message: " + message);

        try {
            SmsManager sms = SmsManager.getDefault();
            ArrayList<String> parts = sms.divideMessage(message);
            sms.sendMultipartTextMessage(number, null, parts, null, null);
            Toast.makeText(this, "SMS Sent!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Manual send failed: " + e.getMessage());
            Toast.makeText(this, "SMS Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ------------------- VOSK MODEL -------------------
    private void loadVoskModel() {
        new Thread(() -> {
            try {
                String path = loadModelFromAssets("vosk-model-small-en-us-0.15");
                voskModel = new Model(path);
                recognizer = new Recognizer(voskModel, 16000);
                runOnUiThread(() ->
                        Toast.makeText(this, "Vosk model loaded!", Toast.LENGTH_SHORT).show()
                );
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Model load error: " + e, Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private void copyAssetFolder(String assetPath, File dest) throws IOException {
        String[] files = getAssets().list(assetPath);
        if (files == null || files.length == 0) {
            InputStream in = getAssets().open(assetPath);
            FileOutputStream out = new FileOutputStream(dest);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            in.close();
            out.close();
            return;
        }
        if (!dest.exists()) dest.mkdirs();
        for (String file : files) copyAssetFolder(assetPath + "/" + file, new File(dest, file));
    }

    private String loadModelFromAssets(String modelName) throws IOException {
        File modelDir = new File(getExternalFilesDir(null), modelName);
        if (!modelDir.exists()) {
            modelDir.mkdirs();
            copyAssetFolder(modelName, modelDir);
        }
        return modelDir.getAbsolutePath();
    }

    // ------------------- MIC -------------------
    private void toggleMic() {
        if (!isListening) {
            startListening();
            micToggleButton.setBackgroundColor(0xFF018786);
            isListening = true;
            Toast.makeText(this, "Started listening", Toast.LENGTH_SHORT).show();
        } else {
            stopListening();
            micToggleButton.setBackgroundColor(0xFFCCCCCC);
            isListening = false;
            Toast.makeText(this, "Listening stopped", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isDangerCommand(String text) {
        if (text == null) return false;
        text = text.toLowerCase().trim();
        String[] keywords = {"help", "sos", "save", "emergency", "i am scared", "please help", "danger", "police"};
        for (String k : keywords) if (text.contains(k)) return true;
        return false;
    }

    private boolean isVideoCommand(String text) {
        if (text == null) return false;
        text = text.toLowerCase().trim();
        return text.contains("record") || text.contains("video") || text.contains("camera");
    }

    private boolean isPoliceCommand(String text) {
        if (text == null) return false;
        text = text.toLowerCase().trim();
        return text.contains("police");
    }

    // ------------------- EMERGENCY VOICE COMMAND HANDLER -------------------
    private void triggerEmergencyAction() {
        triggerEmergencyAction("");
    }

    private void triggerEmergencyAction(String command) {
        long now = System.currentTimeMillis();
        if (now - lastTriggered < TRIGGER_COOLDOWN_MS) {
            Log.e(TAG, "Trigger suppressed by cooldown.");
            return; // avoid repeated triggers
        }
        lastTriggered = now;

        String shown = (command == null || command.trim().isEmpty()) ? "voice/shake" : command;
        String toastMsg = "Emergency command detected: " + shown;
        Log.e(TAG, "triggerEmergencyAction -> " + shown);

        // Ensure UI feedback and SMS sending happen on main thread
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, toastMsg, Toast.LENGTH_LONG).show();

            // If command asks for SOS (or it's a shake trigger), send SOS
            if (command == null || command.isEmpty() || isDangerCommand(command)) {
                try {
                    sendSOSAutomatically();
                } catch (Exception e) {
                    Log.e(TAG, "sendSOSAutomatically failed: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Error sending SOS", Toast.LENGTH_SHORT).show();
                }
            }

            // Additional actions based on command text
            if (command != null) {
                String cmd = command.toLowerCase();
                if (cmd.contains("police")) callPolice();
                if (cmd.contains("record") || cmd.contains("video") || cmd.contains("camera"))
                    startVideoRecording();
            }
        });
    }

    // ------------------- CALL POLICE (VOICE TRIGGER) -------------------
    private void callPolice() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, PERMISSION_REQUEST);
            return;
        }

        try {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:")); // India police number (100)
            startActivity(callIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Call failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ------------------- START VIDEO RECORDING -------------------
    private void startVideoRecording() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST);
            return;
        }

        try {
            Toast.makeText(this, "Opening camera for video recording...", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE);
            intent.putExtra(android.provider.MediaStore.EXTRA_DURATION_LIMIT, 15); // optional
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot start video: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Video start error: " + e.getMessage());
        }
    }

    // ------------------- START / STOP LISTENING -------------------
    private void startListening() {
        try {
            stopListening(); // ensure previous service stopped
            if (recognizer == null) {
                Toast.makeText(this, "Recognizer not ready yet", Toast.LENGTH_SHORT).show();
                return;
            }
            speechService = new SpeechService(recognizer, 16000);
            speechService.startListening(new RecognitionListener() {
                @Override
                public void onPartialResult(String s) {
                    try {
                        JSONObject j = new JSONObject(s);
                        String partial = j.optString("partial", "");
                        appendRecognizedText(s);
                        if (!partial.isEmpty()) {
                            if (isDangerCommand(partial)) {
                                runOnUiThread(() -> triggerEmergencyAction(partial));
                            } else if (isVideoCommand(partial)) {
                                runOnUiThread(() -> startVideoRecording());
                            } else if (isPoliceCommand(partial)) {
                                runOnUiThread(() -> callPolice());
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onResult(String s) {
                    try {
                        JSONObject j = new JSONObject(s);
                        String text = j.optString("text", "");
                        appendRecognizedText(s);
                        if (!text.isEmpty()) {
                            if (isDangerCommand(text)) {
                                runOnUiThread(() -> triggerEmergencyAction(text));
                            } else if (isVideoCommand(text)) {
                                runOnUiThread(() -> startVideoRecording());
                            } else if (isPoliceCommand(text)) {
                                runOnUiThread(() -> callPolice());
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFinalResult(String s) {
                    try {
                        JSONObject j = new JSONObject(s);
                        String text = j.optString("text", "");
                        appendRecognizedText(s);
                        if (!text.isEmpty()) {
                            if (isDangerCommand(text)) {
                                runOnUiThread(() -> triggerEmergencyAction(text));
                            } else if (isVideoCommand(text)) {
                                runOnUiThread(() -> startVideoRecording());
                            } else if (isPoliceCommand(text)) {
                                runOnUiThread(() -> callPolice());
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onError(Exception e) {
                    Toast.makeText(MainActivity.this, "Mic error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }

                @Override
                public void onTimeout() {}
            });
        } catch (Exception e) {
            Toast.makeText(this, "Mic error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopListening() {
        try {
            if (speechService != null) {
                speechService.stop();
                speechService = null;
            }
        } catch (Exception ignored) { }
    }

    // Append recognized text into messageText UI
    private void appendRecognizedText(String s) {
        try {
            JSONObject json = new JSONObject(s);
            // support both "text" and "partial"
            String text = json.optString("text", json.optString("partial", ""));
            if (!text.isEmpty()) {
                String current = messageText.getText().toString();
                if (!current.isEmpty()) current += " ";
                messageText.setText(current + text);
                scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // ------------------- SHAKE DETECTION -------------------
    private final SensorEventListener shakeListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            // Basic shake detection algorithm
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            long currentTime = System.currentTimeMillis();
            long diff = currentTime - lastShakeTime;

            if (diff > 100) {
                lastShakeTime = currentTime;
                float speed = Math.abs(x + y + z) / diff * 10000;

                if (speed > SHAKE_THRESHOLD) {
                    shakeCount++;
                    if (shakeCount >= 3) {
                        shakeCount = 0;
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "SOS Triggered by Shake!", Toast.LENGTH_LONG).show();
                            triggerEmergencyAction(""); // empty string -> treated as shake trigger
                        });
                    }
                }
            }
        }

        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    // ------------------- SOS FUNCTION -------------------
    private void sendSOSAutomatically() {
        // Ensure we have a fresh location (best-effort)
        getLocation();

        String message = "⚠ SOS ALERT!\nI need help!";
        if (locationFetched) {
            message += "\nLocation: https://maps.google.com/?q=" + latitude + "," + longitude;
        } else {
            message += "\nLocation not available.";
        }

        SharedPreferences prefs = getSharedPreferences("EmergencyContacts", MODE_PRIVATE);
        Set<String> names = prefs.getStringSet("names", new HashSet<>());

        if (names == null || names.isEmpty()) {
            Toast.makeText(this, "No emergency contacts saved!", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "No emergency contacts stored in preferences.");
            return;
        }

        boolean atLeastOneSent = false;

        for (String name : names) {
            String number = getPhoneNumber(name);

            if (number == null || number.trim().isEmpty()) {
                Log.e(TAG, "No number found for: " + name);
                number = prefs.getString(name, "");
            }

            if (number == null || number.trim().isEmpty()) {
                final String n = name;
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Number not found for: " + n, Toast.LENGTH_SHORT).show()
                );
                continue;
            }

            // Clean the number string: remove everything except digits and plus sign
            number = number.replaceAll("[^0-9+]", "");
            Log.e(TAG, "Sending SOS -> to: " + number + " msg: " + message);

            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, "SEND_SMS permission not granted!");
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this, "SMS permission not granted", Toast.LENGTH_LONG).show()
                    );
                    return;
                }

                SmsManager sms = SmsManager.getDefault();
                ArrayList<String> parts = sms.divideMessage(message);
                sms.sendMultipartTextMessage(number, null, parts, null, null);
                atLeastOneSent = true;

                Log.e(TAG, "SOS sent to: " + number);
            } catch (Exception e) {
                Log.e(TAG, "Failed to send SOS to " + number + " : " + e.getMessage());
                final String failedName = name;
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Failed to send SMS to " + failedName, Toast.LENGTH_SHORT).show()
                );
            }
        }

        if (atLeastOneSent) {
            runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "SOS Sent Automatically!", Toast.LENGTH_LONG).show()
            );
        } else {
            runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, "No valid numbers found to send SOS.", Toast.LENGTH_LONG).show()
            );
            Log.e(TAG, "No SMS sent: no valid numbers or all sends failed.");
        }
    }
}
