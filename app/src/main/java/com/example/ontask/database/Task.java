package com.example.ontask.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "tasks")
public class Task {
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private String title;
    private String date;
    private String time;
    private boolean isCompleted;
    private String firestoreId; // Link to cloud document
    private String userId; // Owner of the task

    public Task(String title, String date, String time, boolean isCompleted, String userId) {
        this.title = title;
        this.date = date;
        this.time = time;
        this.isCompleted = isCompleted;
        this.userId = userId;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }

    public String getFirestoreId() { return firestoreId; }
    public void setFirestoreId(String firestoreId) { this.firestoreId = firestoreId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
