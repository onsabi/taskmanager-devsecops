package com.ons.taskmanager;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(Long id) {
        super("Tache introuvable : " + id);
    }
}
