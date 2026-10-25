package smartattendLocal.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import smartattendLocal.Entity.Attendance;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Integer> {
    boolean existsByStudentIdAndCardIdAndScanTimeBetween(
            int studentId,
            int cardId,
            LocalDateTime start,
            LocalDateTime end
    );

    List<Attendance> findByCardIdAndScanTimeBetween(
            int cardId,
            LocalDateTime start,
            LocalDateTime end
    );

    List<Attendance> findByScanTimeBetween(
            LocalDateTime start,
            LocalDateTime end
    );

    List<Attendance> findByStudentIdAndPhotoPathIsNotNullOrderByScanTimeDesc(int studentId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Attendance a WHERE a.card.id = :cardId")
    void deleteByCardId(@Param("cardId") int cardId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Attendance a WHERE a.studentId = :studentId")
    void deleteByStudentId(@Param("studentId") int studentId);
}
