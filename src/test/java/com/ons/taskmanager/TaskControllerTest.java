package com.ons.taskmanager;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskService taskService;

    @Test
    void listReturnsTasks() throws Exception {
        when(taskService.findAll()).thenReturn(java.util.List.of(new Task(1L, "A", "", false)));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("A"));
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        when(taskService.create(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new Task(5L, "Nouvelle", "", false));

        mockMvc.perform(post("/api/tasks")
                        .contentType("application/json")
                        .content("{\"title\":\"Nouvelle\",\"description\":\"\",\"done\":false}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/tasks/5")))
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void createRejectsBlankTitle() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType("application/json")
                        .content("{\"title\":\"   \",\"description\":\"x\",\"done\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Le titre est obligatoire"));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(taskService.findById(99L)).thenThrow(new TaskNotFoundException(99L));

        mockMvc.perform(get("/api/tasks/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void updateReturnsUpdatedTask() throws Exception {
        when(taskService.update(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new Task(1L, "Modifiee", "", true));

        mockMvc.perform(put("/api/tasks/1")
                        .contentType("application/json")
                        .content("{\"title\":\"Modifiee\",\"description\":\"\",\"done\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/tasks/1"))
                .andExpect(status().isNoContent());
    }
}
