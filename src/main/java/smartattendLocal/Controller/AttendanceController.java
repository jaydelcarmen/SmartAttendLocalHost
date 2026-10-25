package smartattendLocal.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import smartattendLocal.Entity.Attendance;
import smartattendLocal.Entity.Card;
import smartattendLocal.Entity.Student;
import smartattendLocal.Entity.Teacher;
import smartattendLocal.Repository.AttendanceRepository;
import smartattendLocal.Repository.CardsRepository;
import smartattendLocal.Repository.StudentRepository;
import smartattendLocal.Service.PhotoService;
import smartattendLocal.Service.EmailService;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.*;

@RestController
@RequestMapping("/attendance")
public class AttendanceController {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private CardsRepository cardsRepository;

    @Autowired
    private PhotoService photoService;

    @Autowired
    private EmailService emailService;

    @PostMapping("/scan")
    public ResponseEntity<?> scanAttendance(@RequestBody Map<String, String> request) {
        String qrcode = request.get("qrcode");
        int cardId = Integer.parseInt(request.get("cardId"));

        String[] parts = qrcode.split("\\|");
        int studentId = Integer.parseInt(parts[1].split(":")[1]);
        Student student = studentRepository.findById(studentId).orElse(null);
        System.out.println("Student ID: " + studentId);
        System.out.println("Student Qrcode: " + qrcode);

        if (student == null) {
            return ResponseEntity.badRequest().body("Student not found");
        }

        if (!qrcode.equals(student.getQrCode())) {
            return ResponseEntity.badRequest().body("Invalid QR code");
        }

        if (LocalDateTime.now().isAfter(student.getQrExpiry())) {
            return ResponseEntity.badRequest().body("QR Expired");
        }

        if(student.getCard().getId() != cardId) {
            return ResponseEntity.badRequest().body("This QR does not belong to this class");
        }

        Card card = student.getCard();

        LocalDate today = LocalDate.now();
        boolean alreadyScanned = attendanceRepository.existsByStudentIdAndCardIdAndScanTimeBetween(
                studentId,
                card.getId(),
                today.atStartOfDay(),
                today.atTime(23, 59, 59)
        );

        if (alreadyScanned) {
            return ResponseEntity.badRequest().body("Student has already scanned today");
        }

        LocalTime now = LocalTime.now();
        String status;

        if (card.getClassDays() != null && !card.getClassDays().isEmpty()) {
            String todayAbbr = LocalDate.now().getDayOfWeek()
                    .getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            List<String> days = Arrays.asList(card.getClassDays().split(","));
            if (!days.contains(todayAbbr)) {
                return ResponseEntity.badRequest().body("No class today");
            }
        }

        if (card.getEndTime() != null && now.isAfter(card.getEndTime())) {
            return ResponseEntity.badRequest().body("Class has already ended");
        } else if (card.getStartTime() != null && now.isAfter(card.getStartTime().plusMinutes(30))) {
            status = "Late";
        } else {
            status = "Present";
        }

        Attendance attendance = new Attendance();
        attendance.setStudentId(studentId);
        attendance.setClassName(card.getSubjectName());
        attendance.setCard(card);
        attendance.setScanTime(LocalDateTime.now());
        attendance.setStatus(status);
        attendance.setPhotoStatus("PENDING");

        attendanceRepository.save(attendance);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Scan successful - Photo required");
        response.put("attendanceId", attendance.getId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{attendanceId}/capture-photo")
    public ResponseEntity<?> capturePhoto(@PathVariable int attendanceId, @RequestParam("photo") MultipartFile file) {
        try {
            Attendance attendance = attendanceRepository.findById(attendanceId).orElse(null);
            if (attendance == null) {
                return ResponseEntity.badRequest().body("Attendance record not found");
            }

            if (!photoService.isValidImageFile(file)) {
                return ResponseEntity.badRequest().body("Invalid file type. Only JPEG and PNG are allowed");
            }

            if (!photoService.isFileSizeValid(file)) {
                return ResponseEntity.badRequest().body("File size exceeds 5MB limit");
            }

            String fileName = photoService.savePhoto(file, attendanceId);
            attendance.setPhotoPath(fileName);
            attendance.setPhotoStatus("CAPTURED");
            attendanceRepository.save(attendance);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Photo captured and saved successfully");
            response.put("photoPath", fileName);
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            return ResponseEntity.status(500).body("Failed to save photo: " + e.getMessage());
        }
    }

    @GetMapping("/{attendanceId}/photo")
    public ResponseEntity<?> getPhoto(@PathVariable int attendanceId) {
        try {
            Attendance attendance = attendanceRepository.findById(attendanceId).orElse(null);
            if (attendance == null || attendance.getPhotoPath() == null) {
                return ResponseEntity.notFound().build();
            }

            byte[] photoData = photoService.getPhoto(attendance.getPhotoPath());
            String contentType = attendance.getPhotoPath().endsWith(".png") ? MediaType.IMAGE_PNG_VALUE : MediaType.IMAGE_JPEG_VALUE;

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + attendance.getPhotoPath() + "\"")
                    .body(photoData);

        } catch (IOException e) {
            return ResponseEntity.status(404).body("Photo not found");
        }
    }

    @GetMapping("/student/{studentId}/photos")
    public ResponseEntity<?> getStudentPhotos(@PathVariable int studentId) {
        try {
            List<Attendance> attendanceList = attendanceRepository.findByStudentIdAndPhotoPathIsNotNullOrderByScanTimeDesc(studentId);
            List<Map<String, Object>> response = new ArrayList<>();

            for (Attendance attendance : attendanceList) {
                Map<String, Object> photoData = new HashMap<>();
                photoData.put("attendanceId", attendance.getId());
                photoData.put("photoUrl", "/attendance/" + attendance.getId() + "/photo");
                photoData.put("photoPath", attendance.getPhotoPath());
                photoData.put("timestamp", attendance.getScanTime());
                photoData.put("className", attendance.getClassName());
                photoData.put("status", attendance.getStatus());
                response.add(photoData);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to retrieve photos: " + e.getMessage());
        }
    }

    @GetMapping("/scanned/{cardId}")
    public ResponseEntity<?> getScannedStudents(@PathVariable int cardId) {
        LocalDate today = LocalDate.now();

        List<Attendance> attendanceList =
                attendanceRepository.findByCardIdAndScanTimeBetween(
                       cardId,
                       today.atStartOfDay(),
                       today.atTime(23, 59, 59)
                );

        List<Map<String, Object>> response = new ArrayList<>();
        for (Attendance attendance : attendanceList) {
            Student student = studentRepository.findById(attendance.getStudentId()).orElse(null);

            if (student != null) {
                Map<String, Object> data = new HashMap<>();
                data.put("id", student.getId());
                data.put("attendanceId", attendance.getId());
                data.put("name", student.getName());
                data.put("studentNumber", student.getStudentNumber());
                data.put("email", student.getEmail());
                data.put("time", attendance.getScanTime());
                data.put("status", attendance.getStatus());
                data.put("photoStatus", attendance.getPhotoStatus());
                data.put("hasPhoto", attendance.getPhotoPath() != null);

                response.add(data);
            }
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/report/{cardId}")
    public ResponseEntity<?> getAttendanceReport(@PathVariable int cardId) {
        LocalDate today = LocalDate.now();

        List<Student> students = studentRepository.findByCardId(cardId);
        List<Attendance> attendanceList = attendanceRepository.findByCardIdAndScanTimeBetween(
                cardId,
                today.atStartOfDay(),
                today.atTime(23, 59, 59)
        );

        Map<Integer, Attendance> attendanceMap = new HashMap<>();
        for (Attendance a : attendanceList) {
            attendanceMap.put(a.getStudentId(), a);
        }

        List<Map<String, Object>> response = new ArrayList<>();
        for (Student student : students) {
            Map<String, Object> data = new HashMap<>();
            data.put("name", student.getName());
            data.put("studentNumber", student.getStudentNumber());
            data.put("email", student.getEmail());

            Attendance attendance = attendanceMap.get(student.getId());
            if (attendance != null) {
                data.put("time", attendance.getScanTime());
                data.put("status", attendance.getStatus());
            } else {
                data.put("time", null);
                data.put("status", "Absent");
            }
            response.add(data);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/chart")
    public ResponseEntity<?> getChartData(Authentication authentication) {
        String teacherEmail = authentication.getName();
        List<Card> cards = cardsRepository.findByTeacher_Email(teacherEmail);

        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            String dayAbbr = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);

            Set<Integer> scheduledCardIds = new HashSet<>();
            Set<Integer> endedCardIds = new HashSet<>();
            long scheduledStudents = 0;
            LocalTime nowTime = LocalTime.now();
            boolean isToday = day.equals(today);
            for (Card c : cards) {
                if (c.getClassDays() == null || c.getClassDays().isEmpty()) continue;
                List<String> days = Arrays.asList(c.getClassDays().split(","));
                if (!days.contains(dayAbbr)) continue;
                scheduledCardIds.add(c.getId());
                scheduledStudents += studentRepository.findByCardId(c.getId()).size();
                if (!isToday || c.getEndTime() == null || nowTime.isAfter(c.getEndTime())) {
                    endedCardIds.add(c.getId());
                }
            }

            long present = 0, late = 0, endedScheduledStudents = 0;
            if (!scheduledCardIds.isEmpty()) {
                List<Attendance> records = attendanceRepository.findByScanTimeBetween(
                        day.atStartOfDay(), day.atTime(23, 59, 59));
                for (Attendance a : records) {
                    if (!scheduledCardIds.contains(a.getCard().getId())) continue;
                    if ("Present".equals(a.getStatus())) present++;
                    else if ("Late".equals(a.getStatus())) late++;
                }
            }
            for (Card c : cards) {
                if (endedCardIds.contains(c.getId())) {
                    endedScheduledStudents += studentRepository.findByCardId(c.getId()).size();
                }
            }
            long absent = Math.max(endedScheduledStudents - present - late, 0);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("date", dayAbbr + " " + day.getMonthValue() + "/" + day.getDayOfMonth());
            entry.put("present", present);
            entry.put("late", late);
            entry.put("absent", absent);
            result.add(entry);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/today-stats")
    public ResponseEntity<?> getTodayStats(Authentication authentication) {
        String teacherEmail = authentication.getName();
        List<Card> cards = cardsRepository.findByTeacher_Email(teacherEmail);

        LocalDate today = LocalDate.now();
        String todayAbbr = today.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        LocalTime nowTime = LocalTime.now();

        long totalStudents = 0;
        Set<Integer> allCardIds = new HashSet<>();
        for (Card c : cards) {
            allCardIds.add(c.getId());
            totalStudents += studentRepository.findByCardId(c.getId()).size();
        }

        List<Card> todayCards = new ArrayList<>();
        for (Card c : cards) {
            if (c.getClassDays() == null || c.getClassDays().isEmpty()) continue;
            if (Arrays.asList(c.getClassDays().split(",")).contains(todayAbbr)) todayCards.add(c);
        }
        Set<Integer> todayCardIds = new HashSet<>();
        for (Card c : todayCards) todayCardIds.add(c.getId());

        List<Attendance> records = attendanceRepository.findByScanTimeBetween(
                today.atStartOfDay(), today.atTime(23, 59, 59));

        Map<Integer, long[]> scansByCard = new HashMap<>();
        List<Map<String, Object>> recentList = new ArrayList<>();
        for (Attendance a : records) {
            if (!allCardIds.contains(a.getCard().getId())) continue;
            if (todayCardIds.contains(a.getCard().getId())) {
                long[] counts = scansByCard.computeIfAbsent(a.getCard().getId(), k -> new long[2]);
                if ("Present".equals(a.getStatus())) counts[0]++;
                else if ("Late".equals(a.getStatus())) counts[1]++;
            }
            Student s = studentRepository.findById(a.getStudentId()).orElse(null);
            if (s != null) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", s.getName());
                row.put("studentNumber", s.getStudentNumber());
                row.put("time", a.getScanTime());
                row.put("status", a.getStatus());
                recentList.add(row);
            }
        }

        long present = 0, late = 0, absent = 0;
        for (Card c : todayCards) {
            long[] counts = scansByCard.getOrDefault(c.getId(), new long[2]);
            present += counts[0];
            late    += counts[1];
            boolean classEnded = c.getEndTime() == null || nowTime.isAfter(c.getEndTime());
            if (classEnded) {
                long total = studentRepository.findByCardId(c.getId()).size();
                absent += Math.max(total - counts[0] - counts[1], 0);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalStudents", totalStudents);
        result.put("present", present);
        result.put("late", late);
        result.put("absent", absent);
        result.put("recent", recentList);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/report-all")
    public ResponseEntity<?> getFullReport(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String cardId,
            Authentication authentication) {

        String teacherEmail = authentication.getName();
        List<Card> cards = cardsRepository.findByTeacher_Email(teacherEmail);

        LocalDate targetDate = (date != null && !date.isEmpty()) ? LocalDate.parse(date) : LocalDate.now();
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end   = targetDate.atTime(23, 59, 59);

        List<Card> targetCards = cards;
        if (cardId != null && !cardId.isEmpty()) {
            int cid = Integer.parseInt(cardId);
            targetCards = cards.stream().filter(c -> c.getId() == cid).toList();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Card card : targetCards) {
            List<Student> students = studentRepository.findByCardId(card.getId());
            List<Attendance> attendanceList = attendanceRepository.findByCardIdAndScanTimeBetween(card.getId(), start, end);

            Map<Integer, Attendance> attendanceMap = new HashMap<>();
            for (Attendance a : attendanceList) attendanceMap.put(a.getStudentId(), a);

            for (Student student : students) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("className", card.getSubjectName() != null ? card.getSubjectName() : card.getName());
                row.put("cardId", card.getId());
                row.put("name", student.getName());
                row.put("studentNumber", student.getStudentNumber());
                row.put("email", student.getEmail());
                Attendance a = attendanceMap.get(student.getId());
                row.put("time", a != null ? a.getScanTime() : null);
                row.put("status", a != null ? a.getStatus() : "Absent");
                result.add(row);
            }
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/cards")
    public ResponseEntity<?> getTeacherCards(Authentication authentication) {
        String teacherEmail = authentication.getName();
        List<Card> cards = cardsRepository.findByTeacher_Email(teacherEmail);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Card c : cards) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getSubjectName() != null ? c.getSubjectName() : c.getName());
            result.add(m);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/all")
    public List<Attendance> getAllAttendance(){
        return attendanceRepository.findAll();
    }

    @DeleteMapping("/delete/{id}")
    public String deleteAttendance(@PathVariable int id) {
        attendanceRepository.deleteById(id);
        return "Attendance with ID " + id + " deleted successfully.";
    }

    @PutMapping("/{attendanceId}/revoke")
    public ResponseEntity<?> revokeAttendance(@PathVariable int attendanceId, Authentication authentication) {
        try {
            String teacherEmail = authentication.getName();

            Attendance attendance = attendanceRepository.findById(attendanceId).orElse(null);
            if (attendance == null) {
                return ResponseEntity.status(404).body("Attendance record not found");
            }

            Card card = attendance.getCard();
            if (card == null || !card.getTeacher().getEmail().equals(teacherEmail)) {
                return ResponseEntity.status(403).body("Unauthorized: You do not have permission to revoke this attendance");
            }

            Student student = studentRepository.findById(attendance.getStudentId()).orElse(null);
            if (student == null) {
                return ResponseEntity.status(404).body("Student record not found");
            }

            String originalStatus = attendance.getStatus();
            attendance.setStatus("Absent");
            attendance.setRevokedAt(java.time.LocalDateTime.now());
            attendance.setRevokedBy(teacherEmail);

            boolean emailSent = emailService.sendRevokeNotification(student, attendance, attendance.getPhotoPath(), card.getTeacher());
            attendance.setEmailSent(emailSent);

            attendanceRepository.save(attendance);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Attendance revoked successfully");
            response.put("originalStatus", originalStatus);
            response.put("newStatus", "Absent");
            response.put("emailSent", emailSent);
            response.put("studentEmail", student.getEmail());
            response.put("revokedAt", attendance.getRevokedAt());
            response.put("revokedBy", attendance.getRevokedBy());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to revoke attendance: " + e.getMessage());
        }
    }
}
