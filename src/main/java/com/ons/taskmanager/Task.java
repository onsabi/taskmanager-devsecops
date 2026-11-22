package com.ons.taskmanager;

/**
 * Representation d'une tache. Immuable : toute modification produit une nouvelle instance.
 */
public record Task(Long id, String title, String description, boolean done) {
}
