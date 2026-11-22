package com.ons.taskmanager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

/**
 * Logique metier de gestion des taches.
 * Stockage en memoire (suffisant pour un mini-projet ; remplacer par une base en production).
 */
@Service
public class TaskService {

    private final Map<Long, Task> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);

    public List<Task> findAll() {
        return store.values().stream()
                .sorted((a, b) -> Long.compare(a.id(), b.id()))
                .toList();
    }

    public Task findById(Long id) {
        Task task = store.get(id);
        if (task == null) {
            throw new TaskNotFoundException(id);
        }
        return task;
    }

    public Task create(TaskRequest request) {
        Long id = sequence.incrementAndGet();
        Task task = new Task(id, request.title().trim(), normalize(request.description()), request.done());
        store.put(id, task);
        return task;
    }

    public Task update(Long id, TaskRequest request) {
        findById(id);
        Task updated = new Task(id, request.title().trim(), normalize(request.description()), request.done());
        store.put(id, updated);
        return updated;
    }

    public void delete(Long id) {
        if (store.remove(id) == null) {
            throw new TaskNotFoundException(id);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
