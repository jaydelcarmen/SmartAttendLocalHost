package smartattendLocal.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import smartattendLocal.Entity.Admin;
import smartattendLocal.Entity.Card;
import smartattendLocal.Entity.Teacher;
import smartattendLocal.Repository.AdminRepository;
import smartattendLocal.Repository.AttendanceRepository;
import smartattendLocal.Repository.CardsRepository;
import smartattendLocal.Repository.StudentRepository;
import smartattendLocal.Repository.TeacherRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private CardsRepository cardsRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/add")
    public ResponseEntity<?> addAdmin(@RequestBody Admin admin) {
        if (adminRepository.existsByEmail(admin.getEmail())) {
            return ResponseEntity.badRequest().body("Email already exist");
        }
        admin.setPassword(passwordEncoder.encode(admin.getPassword()));
        return ResponseEntity.ok(adminRepository.save(admin));
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginAdmin(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String email = body.get("email");
        String password = body.get("password");

        Admin admin = adminRepository.findByEmail(email);
        if (admin == null) return ResponseEntity.badRequest().body("Email not found");
        if (!passwordEncoder.matches(password, admin.getPassword())) return ResponseEntity.badRequest().body("Wrong password");

        Authentication auth = new UsernamePasswordAuthenticationToken(
                admin.getEmail(), admin.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", admin.getName());
        result.put("email", admin.getEmail());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(Authentication authentication) {
        Admin admin = adminRepository.findByEmail(authentication.getName());
        if (admin == null) return ResponseEntity.badRequest().body("Not found");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", admin.getName());
        result.put("email", admin.getEmail());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/teachers")
    public ResponseEntity<?> getAllTeachers() {
        List<Map<String, Object>> result = teacherRepository.findAll().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("name", t.getName());
            m.put("email", t.getEmail());
            m.put("status", t.getStatus());
            m.put("createdAt", t.getCreatedAt());
            m.put("profilePicture", t.getProfilePicture());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PutMapping("/teachers/{id}/approve")
    public ResponseEntity<?> approveTeacher(@PathVariable int id) {
        Teacher teacher = teacherRepository.findById(id).orElse(null);
        if (teacher == null) return ResponseEntity.badRequest().body("Teacher not found");
        teacher.setStatus("approved");
        teacherRepository.save(teacher);
        return ResponseEntity.ok("Approved");
    }

    @PutMapping("/teachers/{id}/ban")
    public ResponseEntity<?> banTeacher(@PathVariable int id) {
        Teacher teacher = teacherRepository.findById(id).orElse(null);
        if (teacher == null) return ResponseEntity.badRequest().body("Teacher not found");
        teacher.setStatus("banned");
        teacherRepository.save(teacher);
        return ResponseEntity.ok("Banned");
    }

    @DeleteMapping("/teachers/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> deleteTeacher(@PathVariable int id) {
        Teacher teacher = teacherRepository.findById(id).orElse(null);
        if (teacher == null) return ResponseEntity.badRequest().body("Teacher not found");

        try {
            // delete in order: attendance_photo → attendance → cards → students → teacher
            List<Card> cards = cardsRepository.findByTeacher_Email(teacher.getEmail());
            for (Card card : cards) {
                attendanceRepository.deleteByCardId(card.getId());
            }
            cardsRepository.deleteAll(cards);
            studentRepository.deleteByTeacherId(id);
            teacherRepository.deleteById(id);
            return ResponseEntity.ok("Deleted");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error deleting teacher: " + e.getMessage());
        }
    }

    @GetMapping("/all")
    public List<Admin> GetAllAdmins() {
        return adminRepository.findAll();
    }

    @DeleteMapping("/delete/{id}")
    public String deleteAdmin(@PathVariable int id) {
        adminRepository.deleteById(id);
        return "Admin with ID " + id + " deleted successfully.";
    }
}
