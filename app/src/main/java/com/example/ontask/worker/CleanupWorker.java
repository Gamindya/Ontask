package com.example.ontask.worker;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.example.ontask.database.AppDatabase;
import com.example.ontask.database.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Calendar;
import java.util.List;

public class CleanupWorker extends Worker {

    public CleanupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d("CleanupWorker", "Starting daily cleanup of old tasks...");
        AppDatabase dbLocal = AppDatabase.getInstance(getApplicationContext());
        FirebaseFirestore dbCloud = FirebaseFirestore.getInstance();

        // 1. Get all tasks in the local database to clean up old ones system-wide
        List<Task> tasks = dbLocal.taskDao().getAllTasks();

        
        Calendar oneWeekAgo = Calendar.getInstance();
        oneWeekAgo.add(Calendar.DAY_OF_YEAR, -7);

        for (Task task : tasks) {
            try {
                // Parse task date: "day/month/year"
                String[] dateParts = task.getDate().split("/");
                if (dateParts.length == 3) {
                    Calendar taskDate = Calendar.getInstance();
                    taskDate.set(Integer.parseInt(dateParts[2]), Integer.parseInt(dateParts[1]) - 1, Integer.parseInt(dateParts[0]));

                    if (taskDate.before(oneWeekAgo)) {
                        Log.d("CleanupWorker", "Deleting old task: " + task.getTitle());
                        
                        // Delete from Local
                        dbLocal.taskDao().delete(task);
                        
                        // Delete from Cloud
                        if (task.getFirestoreId() != null) {
                            dbCloud.collection("tasks").document(task.getFirestoreId()).delete();
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("CleanupWorker", "Error parsing date for cleanup", e);
            }
        }

        return Result.success();
    }
}
