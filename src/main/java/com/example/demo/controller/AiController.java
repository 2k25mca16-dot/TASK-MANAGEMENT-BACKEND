package com.example.demo.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.model.SignupUser;
import com.example.demo.model.Task;
import com.example.demo.repository.SignupUserRepository;
import com.example.demo.repository.TaskRepository;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private SignupUserRepository userRepository;

    @Value("${gemini.api.key:${GEMINI_API_KEY:}}")
    private String geminiApiKey;

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@RequestParam(value = "role", required = false) String role) {
        boolean authorized = "admin".equalsIgnoreCase(role) || "manager".equalsIgnoreCase(role);
        return ResponseEntity.ok(Map.of(
            "active", true,
            "authorized", authorized,
            "hasExternalApiKey", (geminiApiKey != null && !geminiApiKey.trim().isEmpty()),
            "message", authorized
                ? "AI Assistant is active and ready for Executive Operations."
                : "Restricted: AI Assistant is exclusively available to Admin and Manager roles."
        ));
    }

    @PostMapping("/chat")
    public ResponseEntity<?> handleChat(@RequestBody Map<String, Object> body) {
        String role = body.get("role") != null ? body.get("role").toString().trim().toLowerCase() : "";
        String userEmail = body.get("userEmail") != null ? body.get("userEmail").toString().trim() : "Unknown";
        String message = body.get("message") != null ? body.get("message").toString().trim() : "";

        // STRICT ACCESS CONTROL: Only manager and admin allowed
        if (!"admin".equals(role) && !"manager".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "Access Denied: The AI Assistant is an executive tool exclusively available for Managers and Administrators.",
                "role", role
            ));
        }

        if (message.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message cannot be empty."));
        }

        List<Task> allTasks = taskRepository.findAllByOrderByIdDesc();
        List<SignupUser> allUsers = userRepository.findAll();

        // Build system context
        long totalTasks = allTasks.size();
        long completedTasks = allTasks.stream().filter(t -> Boolean.TRUE.equals(t.getCompleted()) || "Completed".equalsIgnoreCase(t.getStatus())).count();
        long pendingTasks = totalTasks - completedTasks;
        long highPriorityCount = allTasks.stream().filter(t -> "High".equalsIgnoreCase(t.getPriority()) && !Boolean.TRUE.equals(t.getCompleted())).count();

        Map<String, Long> userWorkloads = allTasks.stream()
            .filter(t -> t.getAssignedTo() != null && !t.getAssignedTo().isBlank() && !Boolean.TRUE.equals(t.getCompleted()))
            .collect(Collectors.groupingBy(Task::getAssignedTo, Collectors.counting()));

        // If Gemini API Key is configured, attempt call to Gemini API
        if (geminiApiKey != null && !geminiApiKey.trim().isEmpty()) {
            try {
                String geminiReply = callGeminiApi(message, role, userEmail, totalTasks, completedTasks, pendingTasks, highPriorityCount, userWorkloads, allTasks);
                if (geminiReply != null && !geminiReply.trim().isEmpty()) {
                    return ResponseEntity.ok(Map.of(
                        "reply", geminiReply,
                        "source", "gemini",
                        "timestamp", Instant.now().toString(),
                        "role", role
                    ));
                }
            } catch (Exception e) {
                System.err.println("Gemini API call failed, falling back to built-in executive engine: " + e.getMessage());
            }
        }

        // Built-in intelligent executive engine
        String reply = generateIntelligentResponse(message, role, userEmail, totalTasks, completedTasks, pendingTasks, highPriorityCount, userWorkloads, allTasks, allUsers);

        return ResponseEntity.ok(Map.of(
            "reply", reply,
            "source", "internal-engine",
            "timestamp", Instant.now().toString(),
            "role", role
        ));
    }

    private String generateIntelligentResponse(
            String message,
            String role,
            String userEmail,
            long totalTasks,
            long completedTasks,
            long pendingTasks,
            long highPriorityCount,
            Map<String, Long> userWorkloads,
            List<Task> allTasks,
            List<SignupUser> allUsers) {

        String lower = message.toLowerCase();

        // 1. Status / Overview / Summary request
        if (lower.contains("summary") || lower.contains("overview") || lower.contains("status") || lower.contains("progress") || lower.contains("report")) {
            double rate = totalTasks > 0 ? ((double) completedTasks / totalTasks) * 100.0 : 0.0;
            StringBuilder sb = new StringBuilder();
            sb.append("### 📊 Project & Operations Status Overview\n\n");
            sb.append(String.format("- **Total Registered Tasks:** %d\n", totalTasks));
            sb.append(String.format("- **Completed Deliverables:** %d (%.1f%% completion rate)\n", completedTasks, rate));
            sb.append(String.format("- **Pending / Active Tasks:** %d\n", pendingTasks));
            sb.append(String.format("- **High Priority Tasks in Progress:** %d\n\n", highPriorityCount));

            if (!userWorkloads.isEmpty()) {
                sb.append("#### 👥 Active Workload Distribution:\n");
                userWorkloads.forEach((user, count) -> {
                    sb.append(String.format("- **%s**: %d active task(s)\n", user, count));
                });
                sb.append("\n");
            }

            if (highPriorityCount > 0) {
                sb.append("⚠️ **Executive Alert:** You have ").append(highPriorityCount)
                  .append(" high-priority task(s) requiring attention. Consider reviewing team allocation for these items.");
            } else {
                sb.append("✅ **Operational Health:** Work is progressing smoothly without unaddressed critical priority bottlenecks.");
            }
            return sb.toString();
        }

        // 2. Workload / Team balance / Bottleneck request
        if (lower.contains("workload") || lower.contains("team") || lower.contains("bottleneck") || lower.contains("member") || lower.contains("balance")) {
            StringBuilder sb = new StringBuilder();
            sb.append("### 👥 Team Workload & Capacity Audit\n\n");

            if (userWorkloads.isEmpty()) {
                sb.append("No active pending tasks are assigned to individual team members right now.\n\n");
            } else {
                sb.append("| Assignee / Team Member | Active Pending Tasks | Status |\n");
                sb.append("| :--- | :--- | :--- |\n");
                userWorkloads.forEach((user, count) -> {
                    String status = count > 3 ? "🔴 Heavy Load" : (count > 1 ? "🟡 Moderate" : "🟢 Optimal Capacity");
                    sb.append(String.format("| `%s` | %d | %s |\n", user, count, status));
                });
                sb.append("\n");
            }

            sb.append("#### 💡 Recommendations for ").append(role.toUpperCase()).append(":\n");
            sb.append("1. **Rebalance Workloads:** Reassign tasks from heavily loaded members to those with available capacity.\n");
            sb.append("2. **Blocker Check-ins:** Conduct a quick 10-minute standup with team members working on multiple deliverables.\n");
            sb.append("3. **Due Date Audit:** Ensure realistic delivery timelines are set on upcoming deadlines.");
            return sb.toString();
        }

        // 3. Task breakdown / Suggest subtasks / Create feature plan
        if (lower.contains("breakdown") || lower.contains("subtask") || lower.contains("feature") || lower.contains("plan") || lower.contains("create task")) {
            return "### ⚡ Executive Task Decomposition & Plan\n\n" +
                   "Here is a recommended operational breakdown for your initiative:\n\n" +
                   "1. **Phase 1: Architecture & Requirements Specification**\n" +
                   "   - *Priority:* High\n" +
                   "   - *Action:* Define technical specifications, API contracts, and database schema updates.\n\n" +
                   "2. **Phase 2: Core Implementation & Business Logic**\n" +
                   "   - *Priority:* High\n" +
                   "   - *Action:* Implement core backend endpoints and link database entities.\n\n" +
                   "3. **Phase 3: Frontend Integration & UI States**\n" +
                   "   - *Priority:* Medium\n" +
                   "   - *Action:* Build user interfaces with responsive layout, validations, and loading/error states.\n\n" +
                   "4. **Phase 4: Quality Assurance & Review**\n" +
                   "   - *Priority:* High\n" +
                   "   - *Action:* Validate end-to-end functionality, conduct code review, and verify role security.\n\n" +
                   "💡 *Tip:* You can assign each of these phases directly in the **Assign Tasks** tab to track individual progress.";
        }

        // 4. Draft announcement / Message / Email
        if (lower.contains("announcement") || lower.contains("draft") || lower.contains("message") || lower.contains("email") || lower.contains("reminder")) {
            return "### 📢 Draft Executive Communication\n\n" +
                   "**Subject:** Update on Sprint Deliverables & Task Milestones\n\n" +
                   "---\n\n" +
                   "Hi Team,\n\n" +
                   "Hope you're having a productive week. As we advance through our current sprint milestones, please ensure all your assigned tasks on the **Task Management Portal** are up-to-date:\n\n" +
                   "- Update any completed tasks with documentation notes or pull request references.\n" +
                   "- Flag any technical blockers or deadline risks early so management can assist.\n" +
                   "- For pending reviews, ensure relevant test instructions are attached.\n\n" +
                   "Thank you for your hard work and dedication!\n\n" +
                   "Best regards,\n" +
                   "**" + ("admin".equals(role) ? "System Administration" : "Engineering Management") + "**\n\n" +
                   "---\n" +
                   "*(Feel free to copy and customize this message for your team chat or email.)*";
        }

        // 5. Priorities / Risk assessment
        if (lower.contains("risk") || lower.contains("priority") || lower.contains("urgent") || lower.contains("deadline")) {
            long highTasks = allTasks.stream().filter(t -> "High".equalsIgnoreCase(t.getPriority()) && !Boolean.TRUE.equals(t.getCompleted())).count();
            return "### ⚠️ Operational Risk & Priority Assessment\n\n" +
                   "- **Critical Items Requiring Oversight:** " + highTasks + " high-priority task(s) currently open.\n" +
                   "- **Recommended Next Steps:**\n" +
                   "  1. Review pending reviews submitted by team members.\n" +
                   "  2. Validate whether any upcoming deadlines overlap with public holidays or leave requests.\n" +
                   "  3. Use the **Chat with Team Member** feature to verify milestones on high-risk deliverables.\n\n" +
                   "Would you like me to analyze a specific team member's progress or help draft a deadline adjustment plan?";
        }

        // Default response
        return "### 🤖 AI Executive Operations Assistant\n\n" +
               "Hello! As your management AI assistant, I can help you with:\n\n" +
               "- 📊 **Project Analytics:** Ask me to *summarize project status* or *analyze task progress*.\n" +
               "- 👥 **Team Coordination:** Ask me about *team workload*, *capacity*, or *bottlenecks*.\n" +
               "- ⚡ **Task Breakdown:** Ask me to *break down a new feature* into structured phases and milestones.\n" +
               "- 📢 **Communications:** Ask me to *draft a team announcement* or *sprint reminder*.\n" +
               "- 🎯 **Prioritization:** Ask me to *assess risks and priorities* across active tasks.\n\n" +
               "*How can I assist your operations today?*";
    }

    private String callGeminiApi(
            String userPrompt,
            String role,
            String userEmail,
            long total,
            long completed,
            long pending,
            long highPriority,
            Map<String, Long> workloads,
            List<Task> tasks) throws Exception {

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + geminiApiKey.trim();

        String systemContext = String.format(
            "You are an Executive AI Operations Assistant embedded in a full-stack Task Management System. " +
            "The logged-in user is a %s (%s). Current system metrics: Total Tasks: %d, Completed: %d, Pending: %d, High Priority Pending: %d. " +
            "Provide helpful, concise, well-structured markdown answers focusing on task efficiency, delegation, project tracking, and management leadership.",
            role.toUpperCase(), userEmail, total, completed, pending, highPriority
        );

        String escapedPrompt = userPrompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        String escapedSystem = systemContext.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");

        String jsonPayload = String.format("""
            {
              "contents": [
                {
                  "role": "user",
                  "parts": [
                    { "text": "%s\\n\\nUser query: %s" }
                  ]
                }
              ]
            }
            """, escapedSystem, escapedPrompt);

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .timeout(Duration.ofSeconds(20))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            String body = response.body();
            // Simple JSON text extractor to avoid heavy JSON parser dependency
            int textIdx = body.indexOf("\"text\": \"");
            if (textIdx != -1) {
                int start = textIdx + 9;
                int end = body.indexOf("\"", start);
                while (end != -1 && body.charAt(end - 1) == '\\') {
                    end = body.indexOf("\"", end + 1);
                }
                if (end > start) {
                    String extracted = body.substring(start, end);
                    return extracted.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
                }
            }
        }
        return null;
    }
}

