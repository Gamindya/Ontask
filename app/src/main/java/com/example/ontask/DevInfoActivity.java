package com.example.ontask;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

public class DevInfoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dev_info);

        // Find the hamburger menu button
        ImageView btnMenu = findViewById(R.id.btnMenu);

        // Tell it to open the MenuActivity when clicked
        btnMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DevInfoActivity.this, MenuActivity.class);
                startActivity(intent);
            }
        });
    }
}