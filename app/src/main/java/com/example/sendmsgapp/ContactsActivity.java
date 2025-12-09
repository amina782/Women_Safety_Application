package com.example.sendmsgapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.HashSet;
import java.util.Set;

public class ContactsActivity extends AppCompatActivity {

    EditText contactName, contactNumber;
    Button saveContactBtn, viewContactsBtn, clearContactsBtn;
    TextView savedContactsText;
    SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);

        contactName = findViewById(R.id.contactName);
        contactNumber = findViewById(R.id.contactNumber);
        saveContactBtn = findViewById(R.id.saveContactBtn);
        viewContactsBtn = findViewById(R.id.viewContactsBtn);
        clearContactsBtn = findViewById(R.id.clearContactsBtn);
        savedContactsText = findViewById(R.id.savedContactsText);

        sharedPreferences = getSharedPreferences("EmergencyContacts", MODE_PRIVATE);

        // ✅ Save a contact with both name and number
        saveContactBtn.setOnClickListener(v -> {
            String name = contactName.getText().toString().trim();
            String number = contactNumber.getText().toString().trim();

            if (!name.isEmpty() && !number.isEmpty()) {
                SharedPreferences.Editor editor = sharedPreferences.edit();

                // Save as key-value (name → number)
                editor.putString(name, number);

                // Keep list of names for dropdown
                Set<String> names = new HashSet<>(sharedPreferences.getStringSet("names", new HashSet<>()));
                names.add(name);
                editor.putStringSet("names", names);
                editor.apply();

                Toast.makeText(this, "Contact Saved!", Toast.LENGTH_SHORT).show();
                contactName.setText("");
                contactNumber.setText("");
                setResult(RESULT_OK);  // ✅ Tell MainActivity that data was updated

            } else {
                Toast.makeText(this, "Enter both name and number!", Toast.LENGTH_SHORT).show();
            }
        });

        // ✅ View all saved contacts
        viewContactsBtn.setOnClickListener(v -> {
            Set<String> names = sharedPreferences.getStringSet("names", new HashSet<>());
            StringBuilder builder = new StringBuilder("Saved Contacts:\n");

            for (String name : names) {
                String number = sharedPreferences.getString(name, "N/A");
                builder.append(name).append(" - ").append(number).append("\n");
            }

            savedContactsText.setText(builder.toString());
        });

        // ✅ Clear all contacts
        clearContactsBtn.setOnClickListener(v -> {
            sharedPreferences.edit().clear().apply();
            savedContactsText.setText("");
            Toast.makeText(this, "All contacts cleared!", Toast.LENGTH_SHORT).show();
        });

        Button backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> {
            finish(); // ✅ Goes back to MainActivity
        });

    }
}
