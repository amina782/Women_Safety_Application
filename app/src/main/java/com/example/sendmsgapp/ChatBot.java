package com.example.sendmsgapp;

public class ChatBot {

    public static String getResponse(String inputText) {
        inputText = inputText.toLowerCase();

        // Safety related keywords
        if (inputText.contains("help") || inputText.contains("danger")) {
            return "⚠️ SOS sent to your emergency contacts!";
        } else if (inputText.contains("hello") || inputText.contains("hi")) {
            return "Hello! I am your assistant. How can I help you today?";
        } else if (inputText.contains("location")) {
            return "You can send your current location using the main screen.";
        } else if (inputText.contains("weather")) {
            return "I cannot fetch live weather offline, but always stay safe!";
        }
        return "I am here to assist you. You can ask about safety, help, or send an SOS!";
    }
}
