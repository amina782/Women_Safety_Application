# AI-Based Women Safety Application

## Description
An AI-based women safety Android application that detects emergency situations using voice commands and phone shaking. The app automatically sends SOS messages with the user’s live location to emergency contacts and can also call the police or start video recording for safety evidence.

---

## Features
- Voice-based emergency detection (offline speech recognition)
- Shake detection to trigger SOS
- Automatic SOS SMS with live Google Maps location
- Emergency contact management
- Call police using voice command
- Start video recording using voice command
- Normal SMS messaging functionality

---

## Technologies Used
- Android (Java)
- Vosk (Offline Speech Recognition)
- Android Sensors (Accelerometer)
- GPS / Location Services
- SMS Manager
- SharedPreferences

---

## How It Works
1. The user adds emergency contacts.
2. The app continuously listens for danger keywords or shake gestures.
3. On detection, SOS alerts are sent automatically with location.
4. Optional actions like calling police or recording video are triggered based on voice commands.

---

## Permissions Required
- RECORD_AUDIO  
- SEND_SMS  
- READ_CONTACTS  
- ACCESS_FINE_LOCATION  
- ACCESS_COARSE_LOCATION  
- CALL_PHONE  
- CAMERA  
