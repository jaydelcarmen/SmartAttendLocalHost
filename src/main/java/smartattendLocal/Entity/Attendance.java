package smartattendLocal.Entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "ATTENDANCE")
public class Attendance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "STUDENT_ID")
    private int studentId;

    @Column(name = "CLASS_NAME")
    private String className;

    @Column(name = "TIME")
    private LocalDateTime scanTime;

    @Column(name = "STATUS")
    private String status;

    @Column(name = "PHOTO_PATH")
    private String photoPath;

    @Column(name = "PHOTO_STATUS")
    private String photoStatus;

    @Column(name = "REVOKED_AT")
    private LocalDateTime revokedAt;

    @Column(name = "REVOKED_BY")
    private String revokedBy;

    @Column(name = "EMAIL_SENT")
    private Boolean emailSent;

    @ManyToOne
    @JoinColumn(name = "CARD_ID",referencedColumnName = "ID", nullable = false)
    private Card card;
}
