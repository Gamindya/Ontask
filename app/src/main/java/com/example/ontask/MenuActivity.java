package com.example.ontask;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

public class MenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        // Find all the buttons on the screen
        ImageView btnClose = findViewById(R.id.btnClose);
        androidx.appcompat.widget.AppCompatButton btnNavWorkPlan = findViewById(R.id.btnNavWorkPlan);
        androidx.appcompat.widget.AppCompatButton btnNavProfile = findViewById(R.id.btnNavProfile);
        androidx.appcompat.widget.AppCompatButton btnNavDevInfo = findViewById(R.id.btnNavDevInfo);

        // 1. The 'X' Button
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // Closes the menu
            }
        });

        // 2. The "work plan" Button (FIXED)
        btnNavWorkPlan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Now it explicitly travels back to the Work Plan!
                Intent intent = new Intent(MenuActivity.this, WorkPlanActivity.class);
                startActivity(intent);
                finish();
            }
        });

        // 3. The "My profile" Button
        btnNavProfile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MenuActivity.this, ProfileActivity.class);
                startActivity(intent);
                finish();
            }
        });

        // 4. The "Dev info" Button
        btnNavDevInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MenuActivity.this, DevInfoActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}