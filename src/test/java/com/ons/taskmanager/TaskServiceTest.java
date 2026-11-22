package com.ons.taskmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskServiceTest {

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService();
    }

    @Test
    void createAssignsIncrementingIdsAndTrimsTitle() {
        Task first = service.create(new TaskRequest("  Acheter du pain  ", " baguette ", false));
        Task second = service.create(new TaskRequest("Ecrire le rapport", null, true));

        assertThat(first.id()).isEqualTo(1L);
        assertThat(second.id()).isEqualTo(2L);
        assertThat(first.title()).isEqualTo("Acheter du pain");
        assertThat(first.description()).isEqualTo("baguette");
        assertThat(second.description()).isEmpty();
        assertThat(second.done()).isTrue();
    }

    @Test
    void findAllReturnsTasksSortedById() {
        service.create(new TaskRequest("A", "", false));
        service.create(new TaskRequest("B", "", false));
        service.create(new TaskRequest("C", "", false));

        assertThat(service.findAll())
                .extracting(Task::title)
                .containsExactly("A", "B", "C");
    }

    @Test
    void findByIdThrowsWhenMissing() {
        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void updateReplacesTaskFields() {
        Task created = service.create(new TaskRequest("Ancien titre", "", false));

        Task updated = service.update(created.id(), new TaskRequest("Nouveau titre", "details", true));

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.title()).isEqualTo("Nouveau titre");
        assertThat(updated.done()).isTrue();
        assertThat(service.findById(created.id())).isEqualTo(updated);
    }

    @Test
    void updateThrowsWhenMissing() {
        assertThatThrownBy(() -> service.update(42L, new TaskRequest("X", "", false)))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void deleteRemovesTask() {
        Task created = service.create(new TaskRequest("A supprimer", "", false));

        service.delete(created.id());

        assertThat(service.findAll()).isEmpty();
    }

    @Test
    void deleteThrowsWhenMissing() {
        assertThatThrownBy(() -> service.delete(7L))
                .isInstanceOf(TaskNotFoundException.class);
    }
}
