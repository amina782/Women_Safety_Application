package com.example.sendmsgapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.animation.AlphaAnimation;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class AiChatActivity extends AppCompatActivity {

    LinearLayout chatContainer;
    EditText userInput;
    ImageButton sendButton;
    ScrollView scrollView;
    TextView typingView;
    ImageButton backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        chatContainer = findViewById(R.id.chatContainer);
        userInput = findViewById(R.id.userInput);
        sendButton = findViewById(R.id.sendButton);
        scrollView = findViewById(R.id.scrollView);
        backButton = findViewById(R.id.backButton);

        // Back button
        backButton.setOnClickListener(v -> finish());

        // Bottom Navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                finish(); // Go back to MainActivity
                return true;
            } else if (id == R.id.nav_contacts) {
                startActivity(new Intent(AiChatActivity.this, ContactsActivity.class));
                return true;
            }
            return false;
        });

        // Send Button
        sendButton.setOnClickListener(v -> {
            String text = userInput.getText().toString().trim();
            if (!text.isEmpty()) {
                addMessage(text, true);  // User message
                userInput.setText("");

                showTypingIndicator();

                // Simulate bot response
                new Thread(() -> {
                    try {
                        Thread.sleep(500); // delay to simulate typing
                    } catch (InterruptedException e) { e.printStackTrace(); }

                    String response = ChatBot.getResponse(text); // Your ChatBot logic

                    runOnUiThread(() -> {
                        removeTypingIndicator();
                        addMessage(response, false); // AI message
                    });
                }).start();
            }
        });
    }

    private void addMessage(String msg, boolean isUser) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(10, 10, 10, 10);

        ImageView icon = new ImageView(this);
        icon.setLayoutParams(new LinearLayout.LayoutParams(90, 90));

        TextView bubble = new TextView(this);
        bubble.setText(msg);
        bubble.setTextSize(16);
        bubble.setPadding(30, 20, 30, 20);

        LinearLayout.LayoutParams bubbleParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        bubbleParams.setMargins(10, 0, 10, 0);
        bubble.setLayoutParams(bubbleParams);

        if (isUser) {
            row.setGravity(Gravity.END);
            icon.setImageResource(R.drawable.user_icon);
            bubble.setBackgroundResource(R.drawable.user_bubble);
            bubble.setTextColor(Color.WHITE);

            row.addView(bubble);
            row.addView(icon);

        } else {
            row.setGravity(Gravity.START);
            icon.setImageResource(R.drawable.ai_icon);
            bubble.setBackgroundResource(R.drawable.ai_bubble);
            bubble.setTextColor(Color.BLACK);

            row.addView(icon);
            row.addView(bubble);
        }

        // Fade animation
        AlphaAnimation animation = new AlphaAnimation(0f, 1f);
        animation.setDuration(500);
        row.startAnimation(animation);

        chatContainer.addView(row);
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void showTypingIndicator() {
        typingView = new TextView(this);
        typingView.setText("AI is typing...");
        typingView.setTextColor(Color.GRAY);
        typingView.setPadding(20, 20, 20, 20);
        chatContainer.addView(typingView);
    }

    private void removeTypingIndicator() {
        if (typingView != null) {
            chatContainer.removeView(typingView);
        }
    }
}
