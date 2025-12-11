package com.example.sendmsgapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        mAuth = FirebaseAuth.getInstance();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            FirebaseUser currentUser = mAuth.getCurrentUser();

            // have they ever registered on this device?
            boolean hasRegistered = getSharedPreferences("AuthPrefs", MODE_PRIVATE)
                    .getBoolean("hasRegistered", false);

            Intent next;

            if (currentUser != null) {
                // ✅ already logged in → go to main
                next = new Intent(SplashActivity.this, MainActivity.class);
            } else if (hasRegistered) {
                // ✅ registered before but not logged in → login
                next = new Intent(SplashActivity.this, LoginActivity.class);
            } else {
                // ❌ never registered → show register first
                next = new Intent(SplashActivity.this, RegisterActivity.class);
            }

            startActivity(next);
            finish();
        }, 2000);
    }
}
