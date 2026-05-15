package com.example.ontask;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.example.ontask.database.AppDatabase;
import com.example.ontask.database.User;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class EditProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditProfileDebug";
    private FirebaseAuth mAuth;
    private FirebaseFirestore dbCloud;
    private FirebaseStorage storage;
    private AppDatabase dbLocal;

    private ImageView imgProfile;
    private Uri selectedImageUri;
    private String currentPhotoUrl = null;

    private final ActivityResultLauncher<String> mGetContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    imgProfile.setImageURI(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        mAuth = FirebaseAuth.getInstance();
        dbCloud = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        dbLocal = AppDatabase.getInstance(this);

        imgProfile = findViewById(R.id.imgEditProfile);
        TextView btnChangePhoto = findViewById(R.id.btnChangePhoto);
        EditText editName = findViewById(R.id.editUpdateName);
        EditText editEmail = findViewById(R.id.editUpdateEmail);
        androidx.appcompat.widget.AppCompatButton btnChangePassword = findViewById(R.id.btnOpenChangePassword);
        androidx.appcompat.widget.AppCompatButton btnSave = findViewById(R.id.btnSaveChanges);
        TextView txtCancel = findViewById(R.id.txtCancelEdit);

        // 1. Immediate pre-fill from Local Database & Auth
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            editEmail.setText(currentUser.getEmail());
            new Thread(() -> {
                User localUser = dbLocal.userDao().getUserByEmail(currentUser.getEmail());
                if (localUser != null) {
                    runOnUiThread(() -> {
                        if (editName.getText().toString().isEmpty()) {
                            editName.setText(localUser.getName());
                        }
                    });
                }
            }).start();
        }

        // 2. Load latest from Firestore
        String uid = mAuth.getUid();
        if (uid != null) {
            dbCloud.collection("users").document(uid).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    String name = doc.getString("name");
                    String email = doc.getString("email");
                    currentPhotoUrl = doc.getString("photoUrl");
                    
                    if (name != null) editName.setText(name);
                    if (email != null) editEmail.setText(email);
                    if (currentPhotoUrl != null && !currentPhotoUrl.isEmpty() && selectedImageUri == null) {
                        Glide.with(this).load(currentPhotoUrl).placeholder(R.drawable.ic_person).into(imgProfile);
                    }
                }
            }).addOnFailureListener(e -> Log.e(TAG, "Failed to load from Firestore", e));
        }

        btnChangePhoto.setOnClickListener(v -> mGetContent.launch("image/*"));
        imgProfile.setOnClickListener(v -> mGetContent.launch("image/*"));
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        btnSave.setOnClickListener(v -> {
            Log.d(TAG, "Save Button Clicked");
            String name = editName.getText().toString().trim();
            String email = editEmail.getText().toString().trim();

            if (name.isEmpty()) {
                editName.setError("Name required");
                return;
            }

            if (selectedImageUri != null) {
                Log.d(TAG, "Image selected, starting uploadThenSave");
                uploadThenSave(name, email);
            } else {
                Log.d(TAG, "No new image, starting saveAllData directly");
                saveAllData(name, email, currentPhotoUrl);
            }
        });

        txtCancel.setOnClickListener(v -> finish());
    }

    private void uploadThenSave(String name, String email) {
        String uid = mAuth.getUid();
        if (uid == null) {
            Log.e(TAG, "UID is null in uploadThenSave");
            return;
        }
        
        Toast.makeText(this, "Saving changes...", Toast.LENGTH_SHORT).show();
        StorageReference ref = storage.getReference().child("profile_pics/" + uid + ".jpg");
        
        Log.d(TAG, "Uploading file to storage: " + selectedImageUri.toString());
        ref.putFile(selectedImageUri)
            .addOnSuccessListener(taskSnapshot -> {
                Log.d(TAG, "Upload successful, getting URL");
                ref.getDownloadUrl().addOnSuccessListener(uri -> {
                    Log.d(TAG, "Got URL: " + uri.toString());
                    saveAllData(name, email, uri.toString());
                });
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Upload failed", e);
                Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                saveAllData(name, email, currentPhotoUrl);
            });
    }

    private void saveAllData(String name, String email, String photoUrl) {
        String uid = mAuth.getUid();
        if (uid == null) return;

        Log.d(TAG, "Preparing to save to Firestore. Name: " + name + ", Email: " + email);
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("name", name);
        userMap.put("email", email);
        if (photoUrl != null) userMap.put("photoUrl", photoUrl);

        // 1. Update Firestore (Using Merge to be safer)
        dbCloud.collection("users").document(uid).set(userMap, SetOptions.merge())
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Firestore save successful");
                // 2. Update Local Room
                new Thread(() -> {
                    FirebaseUser currentFBUser = mAuth.getCurrentUser();
                    if (currentFBUser != null && currentFBUser.getEmail() != null) {
                        User localUser = dbLocal.userDao().getUserByEmail(currentFBUser.getEmail());
                        if (localUser != null) {
                            localUser.setName(name);
                            localUser.setEmail(email);
                            if (photoUrl != null) localUser.setProfileImageUri(photoUrl);
                            dbLocal.userDao().updateUser(localUser);
                            Log.d(TAG, "Local Room update successful");
                        }
                    }
                }).start();

                Toast.makeText(this, "Profile Updated!", Toast.LENGTH_SHORT).show();
                
                // Explicitly return to My Profile to ensure it "appears"
                Intent intent = new Intent(EditProfileActivity.this, ProfileActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            })
            .addOnFailureListener(e -> {
                Log.e(TAG, "Firestore save failed", e);
                if (e.getMessage() != null && e.getMessage().contains("NOT_FOUND")) {
                    Toast.makeText(this, "ERROR: Database does not exist! Please create Firestore on website.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Cloud save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
                // Fallback to local save so the app doesn't freeze
                saveToLocalRoomOnly(name, email, photoUrl);
            });
    }

    private void saveToLocalRoomOnly(String name, String email, String photoUrl) {
        new Thread(() -> {
            FirebaseUser currentFBUser = mAuth.getCurrentUser();
            if (currentFBUser != null && currentFBUser.getEmail() != null) {
                User localUser = dbLocal.userDao().getUserByEmail(currentFBUser.getEmail());
                if (localUser != null) {
                    localUser.setName(name);
                    localUser.setEmail(email);
                    if (photoUrl != null) localUser.setProfileImageUri(photoUrl);
                    dbLocal.userDao().updateUser(localUser);
                }
            }
            runOnUiThread(() -> {
                Toast.makeText(this, "Saved to Local Only (Cloud Failed)", Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }

    private void showChangePasswordDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_change_password);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        EditText editCurrentPass = dialog.findViewById(R.id.editCurrentPassword);
        EditText editNewPass = dialog.findViewById(R.id.editNewPassword);
        androidx.appcompat.widget.AppCompatButton btnUpdate = dialog.findViewById(R.id.btnUpdatePassword);
        TextView txtCancel = dialog.findViewById(R.id.txtCancelPassword);

        txtCancel.setOnClickListener(v -> dialog.dismiss());

        btnUpdate.setOnClickListener(v -> {
            String currentPass = editCurrentPass.getText().toString().trim();
            String newPass = editNewPass.getText().toString().trim();

            if (currentPass.isEmpty() || newPass.isEmpty()) {
                Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null && user.getEmail() != null) {
                AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPass);
                user.reauthenticate(credential).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        user.updatePassword(newPass).addOnCompleteListener(updateTask -> {
                            if (updateTask.isSuccessful()) {
                                Toast.makeText(this, "Password Updated!", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            } else {
                                String msg = updateTask.getException() != null ? updateTask.getException().getMessage() : "Failed";
                                Toast.makeText(this, "Error: " + msg, Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        Toast.makeText(this, "Current password incorrect", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        dialog.show();
    }
}
