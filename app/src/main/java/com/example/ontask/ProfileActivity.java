package com.example.ontask;

import androidx.appcompat.app.AppCompatActivity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.ontask.database.AppDatabase;
import com.example.ontask.database.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseFirestore dbCloud;
    private AppDatabase dbLocal;
    private ImageView imgProfile;
    private TextView txtProfileName, txtProfileEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        dbCloud = FirebaseFirestore.getInstance();
        dbLocal = AppDatabase.getInstance(this);

        ImageView btnMenu = findViewById(R.id.btnMenu);
        androidx.appcompat.widget.AppCompatButton btnEditProfile = findViewById(R.id.btnEditProfile);
        androidx.appcompat.widget.AppCompatButton btnSignOut = findViewById(R.id.btnSignOut);
        androidx.appcompat.widget.AppCompatButton btnDeleteAccount = findViewById(R.id.btnDeleteAccount);

        imgProfile = findViewById(R.id.imgProfile);
        txtProfileName = findViewById(R.id.txtProfileName);
        txtProfileEmail = findViewById(R.id.txtProfileEmail);

        btnMenu.setOnClickListener(v -> startActivity(new Intent(ProfileActivity.this, MenuActivity.class)));
        btnEditProfile.setOnClickListener(v -> startActivity(new Intent(ProfileActivity.this, EditProfileActivity.class)));

        btnSignOut.setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        btnDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserProfile();
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        txtProfileEmail.setText(currentUser.getEmail());

        // 1. Load from LOCAL DATABASE (Instant)
        new Thread(() -> {
            User localUser = dbLocal.userDao().getUserByEmail(currentUser.getEmail());
            if (localUser != null) {
                runOnUiThread(() -> {
                    txtProfileName.setText(localUser.getName());
                    if (localUser.getProfileImageUri() != null) {
                        Glide.with(this)
                                .load(localUser.getProfileImageUri())
                                .placeholder(R.drawable.ic_person)
                                .into(imgProfile);
                    }
                });
            }
        }).start();

        // 2. Refresh from CLOUD (Background)
        dbCloud.collection("users").document(currentUser.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("name");
                        String photoUrl = doc.getString("photoUrl");
                        txtProfileName.setText(name);
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(photoUrl)
                                    .placeholder(R.drawable.ic_person)
                                    .into(imgProfile);
                        }
                    }
                });
    }

    private void showDeleteAccountDialog() {
        Dialog dialog = new Dialog(ProfileActivity.this);
        dialog.setContentView(R.layout.dialog_delete_account);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView txtCancelDelete = dialog.findViewById(R.id.txtCancelDelete);
        androidx.appcompat.widget.AppCompatButton btnConfirmDelete = dialog.findViewById(R.id.btnConfirmDelete);

        txtCancelDelete.setOnClickListener(v -> dialog.dismiss());

        btnConfirmDelete.setOnClickListener(v -> {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                user.delete().addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        dbCloud.collection("users").document(user.getUid()).delete();
                        dialog.dismiss();
                        startActivity(new Intent(ProfileActivity.this, LoginActivity.class)
                                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    } else {
                        Toast.makeText(ProfileActivity.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        dialog.show();
    }
}
