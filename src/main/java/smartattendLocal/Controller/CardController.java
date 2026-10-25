package smartattendLocal.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import smartattendLocal.Entity.Card;
import smartattendLocal.Entity.Teacher;
import smartattendLocal.Repository.AttendanceRepository;
import smartattendLocal.Repository.CardsRepository;
import smartattendLocal.Repository.StudentRepository;
import smartattendLocal.Repository.TeacherRepository;

import java.util.List;

@RestController
@RequestMapping("/cards")
public class CardController {

    @Autowired
    private CardsRepository cardsRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @PostMapping("/add")
    public ResponseEntity<?> addCard(@RequestBody Card card, Authentication authentication) {
        String teacherEmail = authentication.getName();

        Teacher teacher = teacherRepository.findByEmail(teacherEmail);

        if (teacher == null) {
            return ResponseEntity
                    .badRequest()
                    .body("Teacher not found");
        }

        card.setTeacher(teacher);

        Card savedCard = cardsRepository.save(card);

        return ResponseEntity.ok(savedCard);
    }

    @GetMapping("/today-schedule")
    public ResponseEntity<?> getTodaySchedule(Authentication authentication) {
        String teacherEmail = authentication.getName();
        List<Card> cards = cardsRepository.findByTeacher_Email(teacherEmail);

        String todayAbbr = java.time.LocalDate.now()
                .getDayOfWeek()
                .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH);

        java.time.LocalTime now = java.time.LocalTime.now();

        List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (Card c : cards) {
            if (c.getClassDays() == null || c.getClassDays().isEmpty()) continue;
            List<String> days = java.util.Arrays.asList(c.getClassDays().split(","));
            if (!days.contains(todayAbbr)) continue;

            String status;
            if (c.getStartTime() == null || c.getEndTime() == null) {
                status = "scheduled";
            } else if (now.isBefore(c.getStartTime())) {
                status = "upcoming";
            } else if (!now.isAfter(c.getEndTime())) {
                status = "ongoing";
            } else {
                status = "done";
            }

            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("subject", c.getSubjectName() != null ? c.getSubjectName() : c.getName());
            m.put("name", c.getName());
            m.put("startTime", c.getStartTime() != null ? c.getStartTime().toString() : null);
            m.put("endTime",   c.getEndTime()   != null ? c.getEndTime().toString()   : null);
            m.put("classDays", c.getClassDays());
            m.put("status", status);
            result.add(m);
        }

        result.sort((a, b) -> {
            String sa = (String) a.get("startTime");
            String sb = (String) b.get("startTime");
            if (sa == null && sb == null) return 0;
            if (sa == null) return 1;
            if (sb == null) return -1;
            return sa.compareTo(sb);
        });

        return ResponseEntity.ok(result);
    }

    @GetMapping("/my-cards")
    public ResponseEntity<?> getMyCards(Authentication authentication) {
        String teacherEmail = authentication.getName();
        Teacher teacher = teacherRepository.findByEmail(teacherEmail);


        if (teacher == null) {
            return ResponseEntity
                    .badRequest()
                    .body("Teacher not found");
        }

        List<Card> cards = cardsRepository.findByTeacher_Email(teacherEmail);

        return ResponseEntity.ok(cards);
    }

    @Transactional
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteCard(@PathVariable int id, Authentication authentication) {
        try {
            String teacherEmail = authentication.getName();
            Teacher teacher = teacherRepository.findByEmail(teacherEmail);
            if (teacher == null) {
                return ResponseEntity.status(403).body("Teacher not found");
            }

            Card card = cardsRepository.findById(id).orElse(null);
            if (card == null) {
                return ResponseEntity.status(404).body("Card not found");
            }

            // Authorization check: verify card belongs to authenticated teacher
            if (card.getTeacher().getId() != teacher.getId()) {
                return ResponseEntity.status(403).body("Unauthorized: Card does not belong to this teacher");
            }

            // delete child records first to avoid FK constraint violation
            try {
                attendanceRepository.deleteByCardId(id);
            } catch (Exception e) {
                System.err.println("Error deleting attendance records for card " + id + ": " + e.getMessage());
                return ResponseEntity.status(500).body("Failed to delete attendance records");
            }

            try {
                studentRepository.deleteByCardId(id);
            } catch (Exception e) {
                System.err.println("Error deleting students for card " + id + ": " + e.getMessage());
                return ResponseEntity.status(500).body("Failed to delete students associated with card");
            }

            try {
                cardsRepository.deleteById(id);
            } catch (Exception e) {
                System.err.println("Error deleting card " + id + ": " + e.getMessage());
                return ResponseEntity.status(500).body("Failed to delete card");
            }

            return ResponseEntity.ok("Card deleted successfully");

        } catch (Exception e) {
            System.err.println("Unexpected error in deleteCard: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("An unexpected error occurred while deleting the card");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCard(@PathVariable int id, @RequestBody Card card, Authentication authentication) {
        String teacherEmail = authentication.getName();
        Teacher teacher = teacherRepository.findByEmail(teacherEmail);
        if (teacher == null) {
            return ResponseEntity
                    .badRequest()
                    .body("Teacher not found");
        }

        Card existing = cardsRepository.findById(id).orElse(null);
        if (existing == null) {
            return ResponseEntity
                    .badRequest()
                    .body("Card not found");
        }

        if (card.getName() != null) {
            existing.setName(card.getName());
        }

        if (card.getSubjectName() != null) {
            existing.setSubjectName(card.getSubjectName());
        }

        if (card.getStartTime() != null) {
            existing.setStartTime(card.getStartTime());
        }

        if (card.getEndTime() != null) {
            existing.setEndTime(card.getEndTime());
        }

        if (card.getClassDays() != null) {
            existing.setClassDays(card.getClassDays());
        }

        cardsRepository.save(existing);
        return ResponseEntity.ok(existing);
    }

}
