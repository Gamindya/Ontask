package com.example.ontask;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.ontask.database.AppDatabase;
import com.example.ontask.database.Task;
import com.example.ontask.worker.CleanupWorker;
import com.example.ontask.worker.NotificationWorker;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class WorkPlanActivity extends AppCompatActivity {

    private static final String TAG = "WorkPlanDebug";
    private AppDatabase dbLocal;
    private FirebaseFirestore dbCloud;
    private FirebaseAuth mAuth;
    private LinearLayout taskListLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_work_plan);

        dbLocal = AppDatabase.getInstance(this);
        dbCloud = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        taskListLayout = findViewById(R.id.taskListLayout);

        ImageView btnMenu = findViewById(R.id.btnMenu);
        androidx.appcompat.widget.AppCompatButton btnAddToDo = findViewById(R.id.btnAddToDo);

        btnMenu.setOnClickListener(v -> startActivity(new Intent(WorkPlanActivity.this, MenuActivity.class)));
        btnAddToDo.setOnClickListener(v -> showAddTaskDialog(null));

        requestNotificationPermission();
        setupDailyCleanup();
        refreshLocalTaskList();
        listenToCloudTasks();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void setupDailyCleanup() {
        PeriodicWorkRequest cleanupRequest = new PeriodicWorkRequest.Builder(CleanupWorker.class, 1, TimeUnit.DAYS).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("DailyCleanup", ExistingPeriodicWorkPolicy.KEEP, cleanupRequest);
    }

    private void refreshLocalTaskList() {
        String uid = mAuth.getUid();
        if (uid == null) return;
        
        new Thread(() -> {
            List<Task> tasks = dbLocal.taskDao().getTasksByUser(uid);
            runOnUiThread(() -> {
                taskListLayout.removeAllViews();
                for (Task task : tasks) {
                    addTaskView(task);
                }
            });
        }).start();
    }

    private void addTaskView(Task task) {
        View itemView = LayoutInflater.from(this).inflate(R.layout.item_task, taskListLayout, false);
        TextView txtTaskName = itemView.findViewById(R.id.txtTaskName);
        ImageView btnEdit = itemView.findViewById(R.id.btnEditTask);
        ImageView btnDelete = itemView.findViewById(R.id.btnDeleteTask);
        
        txtTaskName.setText(task.getTitle());
        btnEdit.setOnClickListener(v -> showAddTaskDialog(task));
        btnDelete.setOnClickListener(v -> {
            new Thread(() -> {
                dbLocal.taskDao().delete(task);
                if (task.getFirestoreId() != null) {
                    dbCloud.collection("tasks").document(task.getFirestoreId()).delete();
                }
                runOnUiThread(this::refreshLocalTaskList);
            }).start();
        });
        taskListLayout.addView(itemView);
    }

    private void listenToCloudTasks() {
        String uid = mAuth.getUid();
        if (uid == null) return;
        dbCloud.collection("tasks").whereEqualTo("userId", uid).addSnapshotListener((value, error) -> {
            if (error != null || value == null) return;
            runOnUiThread(this::refreshLocalTaskList);
        });
    }

    private void showAddTaskDialog(Task existingTask) {
        Dialog dialog = new Dialog(WorkPlanActivity.this);
        dialog.setContentView(R.layout.dialog_add_task);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView txtTitle = dialog.findViewById(R.id.txtTitle);
        EditText editTaskName = dialog.findViewById(R.id.editTaskName);
        EditText editTaskDate = dialog.findViewById(R.id.editTaskDate);
        EditText editTaskTime = dialog.findViewById(R.id.editTaskTime);
        androidx.appcompat.widget.AppCompatButton btnSaveTask = dialog.findViewById(R.id.btnSaveTask);
        TextView txtCancel = dialog.findViewById(R.id.txtCancel);

        if (existingTask != null) {
            txtTitle.setText("Edit Task");
            editTaskName.setText(existingTask.getTitle());
            editTaskDate.setText(existingTask.getDate());
            editTaskTime.setText(existingTask.getTime());
            btnSaveTask.setText("Update");
        }

        txtCancel.setOnClickListener(v -> dialog.dismiss());
        editTaskDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, y, m, d) -> editTaskDate.setText(d + "/" + (m + 1) + "/" + y), c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        editTaskTime.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new TimePickerDialog(this, (view, h, m) -> {
                String amPm = (h >= 12) ? "PM" : "AM";
                int displayHour = (h > 12) ? h - 12 : (h == 0 ? 12 : h);
                editTaskTime.setText(String.format(Locale.getDefault(), "%d:%02d %s", displayHour, m, amPm));
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show();
        });

        btnSaveTask.setOnClickListener(v -> {
            String title = editTaskName.getText().toString().trim();
            String dateStr = editTaskDate.getText().toString().trim();
            String timeStr = editTaskTime.getText().toString().trim();
            if (title.isEmpty()) return;

            new Thread(() -> {
                String uid = mAuth.getUid();
                if (uid == null) return;
                
                Task taskToSave = (existingTask == null) ? new Task(title, dateStr, timeStr, false, uid) : existingTask;
                if (existingTask != null) {
                    taskToSave.setTitle(title); taskToSave.setDate(dateStr); taskToSave.setTime(timeStr);
                    dbLocal.taskDao().update(taskToSave);
                } else {
                    dbLocal.taskDao().insert(taskToSave);
                }

                scheduleTaskReminders(title, dateStr, timeStr, taskToSave.getId());

                Map<String, Object> taskMap = new HashMap<>();
                taskMap.put("title", title); taskMap.put("date", dateStr); taskMap.put("time", timeStr); taskMap.put("userId", mAuth.getUid());

                if (taskToSave.getFirestoreId() == null) {
                    dbCloud.collection("tasks").add(taskMap).addOnSuccessListener(docRef -> {
                        taskToSave.setFirestoreId(docRef.getId());
                        new Thread(() -> dbLocal.taskDao().update(taskToSave)).start();
                    });
                } else {
                    dbCloud.collection("tasks").document(taskToSave.getFirestoreId()).update(taskMap);
                }
                runOnUiThread(this::refreshLocalTaskList);
            }).start();
            dialog.dismiss();
            Toast.makeText(this, "Task Saved", Toast.LENGTH_SHORT).show();
        });
        dialog.show();
    }

    private void scheduleTaskReminders(String title, String date, String time, int taskId) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("d/M/yyyy h:mm a", Locale.getDefault());
            Date dueDate = sdf.parse(date + " " + time);
            if (dueDate == null) return;

            long currentTime = System.currentTimeMillis();
            long[] offsets = {5 * 3600000L, 3 * 3600000L, 3600000L}; // 5h, 3h, 1h in ms
            String[] labels = {"5 hours", "3 hours", "1 hour"};

            for (int k = 0; k < offsets.length; k++) {
                long triggerTime = dueDate.getTime() - offsets[k];
                long delay = triggerTime - currentTime;

                if (delay > 0) {
                    Data data = new Data.Builder()
                            .putString("title", "Reminder: " + title)
                            .putString("message", "Task is due in " + labels[k])
                            .putInt("id", taskId + k)
                            .build();

                    OneTimeWorkRequest reminderRequest = new OneTimeWorkRequest.Builder(NotificationWorker.class)
                            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                            .setInputData(data)
                            .addTag("TaskReminder_" + taskId)
                            .build();

                    WorkManager.getInstance(this).enqueue(reminderRequest);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling reminders", e);
        }
    }
}
