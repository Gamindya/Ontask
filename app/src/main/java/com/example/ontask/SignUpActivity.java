package com.example.ontask;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.ontask.database.AppDatabase;
import com.example.ontask.database.User;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore dbCloud;
    private AppDatabase dbLocal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        mAuth = FirebaseAuth.getInstance();
        dbCloud = FirebaseFirestore.getInstance();
        dbLocal = AppDatabase.getInstance(this);

        EditText editName = findViewById(R.id.editName);
        EditText editEmail = findViewById(R.id.editEmail);
        EditText editPassword = findViewById(R.id.editSignUpPassword);
        EditText editRePassword = findViewById(R.id.editRePassword);
        androidx.appcompat.widget.AppCompatButton btnSignUp = findViewById(R.id.btnCreate);
        TextView txtLoginLink = findViewById(R.id.txtGoToLogin);

        if (txtLoginLink != null) {
            txtLoginLink.setOnClickListener(v -> finish());
        }

        btnSignUp.setOnClickListener(v -> {
            String name = editName.getText().toString().trim();
            String email = editEmail.getText().toString().trim();
            String password = editPassword.getText().toString().trim();
            String rePassword = editRePassword.getText().toString().trim();

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!password.equals(rePassword)) {
                editRePassword.setError("Passwords do not match!");
                return;
            }

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            String userId = mAuth.getCurrentUser().getUid();
                            
                            // 1. Save to Firestore (Cloud)
                            Map<String, Object> userMap = new HashMap<>();
                            userMap.put("name", name);
                            userMap.put("email", email);
                            
                            dbCloud.collection("users").document(userId).set(userMap)
                                    .addOnSuccessListener(aVoid -> {
                                        // 2. Save to Room (Local)
                                        User newUser = new User(name, email, password);
                                        dbLocal.userDao().registerUser(newUser);

                                        Toast.makeText(SignUpActivity.this, "Account Created!", Toast.LENGTH_SHORT).show();
                                        startActivity(new Intent(SignUpActivity.this, WorkPlanActivity.class));
                                        finish();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(SignUpActivity.this, "Cloud Sync Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    });
                        } else {
                            Toast.makeText(SignUpActivity.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }
}
