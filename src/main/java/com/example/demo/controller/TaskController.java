package com.example.demo.controller;

import com.example.demo.model.Task;
import com.example.demo.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired
    private TaskRepository taskRepository;

    // GET /api/tasks
    @GetMapping
    public ResponseEntity<List<Task>> getTasks() {
        List<Task> tasks = taskRepository.findAllByOrderByIdDesc();
        return ResponseEntity.ok(tasks);
    }

    // POST /api/tasks (Add or upsert task)
    @PostMapping
    public ResponseEntity<?> createTask(@RequestBody Map<String, Object> body) {
        Long id = null;
        if (body.get("id") != null) {
            try {
                id = Long.valueOf(body.get("id").toString());
            } catch (Exception ignored) {
            }
        }
        if (id == null) {
            id = System.currentTimeMillis();
        }

        Task task = taskRepository.findById(id).orElse(new Task());
        task.setId(id);
        task.setName((String) body.getOrDefault("name", "Untitled Task"));
        task.setPriority((String) body.getOrDefault("priority", "Medium"));
        task.setDueDate((String) body.getOrDefault("dueDate", ""));
        task.setStatus((String) body.getOrDefault("status", "Pending"));

        Object compObj = body.get("completed");
        boolean completed = false;
        if (compObj instanceof Boolean) completed = (Boolean) compObj;
        else if (compObj != null) completed = Boolean.parseBoolean(compObj.toString());
        task.setCompleted(completed);

        task.setAssignedTo((String) body.getOrDefault("assignedTo", ""));
        task.setAssignedBy((String) body.getOrDefault("assignedBy", ""));
        task.setType((String) body.getOrDefault("type", "task"));
        task.setCompletionNote((String) body.getOrDefault("completionNote", ""));

        Object fileObj = body.get("hasFile");
        boolean hasFile = false;
        if (fileObj instanceof Boolean) hasFile = (Boolean) fileObj;
        else if (fileObj != null) hasFile = Boolean.parseBoolean(fileObj.toString());
        task.setHasFile(hasFile);

        if (body.get("fileName") != null) task.setFileName(body.get("fileName").toString());
        if (body.get("fileUrl") != null) task.setFileUrl(body.get("fileUrl").toString());
        if (body.get("fileSize") != null) task.setFileSize(body.get("fileSize").toString());
        if (body.get("fileType") != null) task.setFileType(body.get("fileType").toString());
        if (body.get("reviewStatus") != null) task.setReviewStatus(body.get("reviewStatus").toString());
        if (body.get("reviewComment") != null) task.setReviewComment(body.get("reviewComment").toString());
        if (body.get("reviewedBy") != null) task.setReviewedBy(body.get("reviewedBy").toString());
        if (body.get("reviewedAt") != null) task.setReviewedAt(body.get("reviewedAt").toString());

        if (body.get("secondaryAssignee") != null) task.setSecondaryAssignee(body.get("secondaryAssignee").toString().trim());
        else if (body.containsKey("secondaryAssignee")) task.setSecondaryAssignee("");

        Task saved = taskRepository.save(task);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Task added",
                "id", saved.getId()
        ));
    }

    // PUT /api/tasks (Update task)
    @PutMapping
    public ResponseEntity<?> updateTask(@RequestBody Map<String, Object> body) {
        if (body == null || body.get("id") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Task ID required"));
        }

        Long id;
        try {
            id = Long.valueOf(body.get("id").toString());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid Task ID"));
        }

        Optional<Task> opt = taskRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Task not found"));
        }

        Task task = opt.get();

        if (body.containsKey("name")) task.setName((String) body.get("name"));
        if (body.containsKey("priority")) task.setPriority((String) body.get("priority"));
        if (body.containsKey("dueDate")) task.setDueDate((String) body.get("dueDate"));
        if (body.containsKey("status")) task.setStatus((String) body.get("status"));

        if (body.containsKey("completed")) {
            Object comp = body.get("completed");
            task.setCompleted(comp instanceof Boolean ? (Boolean) comp : Boolean.parseBoolean(comp.toString()));
        }

        if (body.containsKey("assignedTo")) task.setAssignedTo((String) body.get("assignedTo"));
        if (body.containsKey("secondaryAssignee")) task.setSecondaryAssignee((String) body.get("secondaryAssignee"));
        if (body.containsKey("assignedBy")) task.setAssignedBy((String) body.get("assignedBy"));
        if (body.containsKey("type")) task.setType((String) body.get("type"));
        if (body.containsKey("completionNote")) task.setCompletionNote((String) body.get("completionNote"));

        if (body.containsKey("hasFile")) {
            Object hf = body.get("hasFile");
            task.setHasFile(hf instanceof Boolean ? (Boolean) hf : Boolean.parseBoolean(hf.toString()));
        }

        if (body.containsKey("fileName")) task.setFileName((String) body.get("fileName"));
        if (body.containsKey("fileUrl")) task.setFileUrl((String) body.get("fileUrl"));
        if (body.containsKey("fileSize")) task.setFileSize((String) body.get("fileSize"));
        if (body.containsKey("fileType")) task.setFileType((String) body.get("fileType"));
        if (body.containsKey("reviewStatus")) task.setReviewStatus((String) body.get("reviewStatus"));
        if (body.containsKey("reviewComment")) task.setReviewComment((String) body.get("reviewComment"));
        if (body.containsKey("reviewedBy")) task.setReviewedBy((String) body.get("reviewedBy"));
        if (body.containsKey("reviewedAt")) task.setReviewedAt((String) body.get("reviewedAt"));

        taskRepository.save(task);

        return ResponseEntity.ok(Map.of("message", "Task updated"));
    }

    // POST /api/tasks/{id}/remove-primary (Automatically transfers task to secondary assignee)
    @PostMapping("/{id}/remove-primary")
    public ResponseEntity<?> removePrimaryAssignee(@PathVariable Long id) {
        Optional<Task> opt = taskRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Task not found"));
        }

        Task task = opt.get();
        String oldPrimary = task.getAssignedTo();
        String fallback = task.getSecondaryAssignee();

        if (fallback != null && !fallback.trim().isEmpty() && !fallback.trim().equalsIgnoreCase(oldPrimary)) {
            task.setAssignedTo(fallback.trim());
            task.setSecondaryAssignee("");
            taskRepository.save(task);
            return ResponseEntity.ok(Map.of(
                "message", "Primary assignee removed. Task automatically transferred to " + fallback.trim(),
                "oldPrimary", oldPrimary != null ? oldPrimary : "",
                "newPrimary", fallback.trim(),
                "transferred", true,
                "task", task
            ));
        } else {
            task.setAssignedTo("Unassigned");
            task.setSecondaryAssignee("");
            taskRepository.save(task);
            return ResponseEntity.ok(Map.of(
                "message", "Primary assignee removed. No secondary assignee found, task is now unassigned.",
                "oldPrimary", oldPrimary != null ? oldPrimary : "",
                "newPrimary", "Unassigned",
                "transferred", false,
                "task", task
            ));
        }
    }

    // POST /api/tasks/{id}/review (Manager reviews task / file)
    @PostMapping("/{id}/review")
    public ResponseEntity<?> reviewTask(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Optional<Task> opt = taskRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Task not found"));
        }

        Task task = opt.get();
        String reviewStatus = (String) body.getOrDefault("reviewStatus", "Approved");
        String reviewComment = (String) body.getOrDefault("reviewComment", "");
        String reviewedBy = (String) body.getOrDefault("reviewedBy", "Manager");
        String reviewedAt = (String) body.getOrDefault("reviewedAt", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        task.setReviewStatus(reviewStatus);
        task.setReviewComment(reviewComment);
        task.setReviewedBy(reviewedBy);
        task.setReviewedAt(reviewedAt);

        // Update task status according to review decision
        if ("Approved".equalsIgnoreCase(reviewStatus)) {
            task.setStatus("Completed");
            task.setCompleted(true);
        } else if ("Changes Requested".equalsIgnoreCase(reviewStatus) || "Needs Revision".equalsIgnoreCase(reviewStatus)) {
            task.setStatus("Changes Requested");
            task.setCompleted(false);
        } else if ("Rejected".equalsIgnoreCase(reviewStatus)) {
            task.setStatus("Rejected");
            task.setCompleted(false);
        }

        taskRepository.save(task);

        return ResponseEntity.ok(Map.of(
                "message", "Task reviewed successfully",
                "task", task
        ));
    }

    // DELETE /api/tasks
    @DeleteMapping
    public ResponseEntity<?> deleteTask(@RequestBody(required = false) Map<String, Object> body,
                                         @RequestParam(required = false) Long id) {
        Long targetId = id;
        if (targetId == null && body != null && body.get("id") != null) {
            try {
                targetId = Long.valueOf(body.get("id").toString());
            } catch (Exception ignored) {
            }
        }

        if (targetId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Task ID required to delete"));
        }

        if (!taskRepository.existsById(targetId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Task not found"));
        }

        taskRepository.deleteById(targetId);

        return ResponseEntity.ok(Map.of("message", "Task deleted"));
    }
}
