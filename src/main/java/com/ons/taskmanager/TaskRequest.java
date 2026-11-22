package com.ons.taskmanager;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Donnees entrantes pour creer ou modifier une tache.
 * La validation evite d'accepter des entrees vides ou trop longues (defense en profondeur).
 */
public record TaskRequest(
        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 100, message = "Le titre ne doit pas depasser 100 caracteres")
        String title,

        @Size(max = 500, message = "La description ne doit pas depasser 500 caracteres")
        String description,

        boolean done
) {
}
