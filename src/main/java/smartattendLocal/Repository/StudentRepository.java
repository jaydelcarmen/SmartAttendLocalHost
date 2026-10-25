package smartattendLocal.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import smartattendLocal.Entity.Student;
import java.util.List;

@Repository
public interface StudentRepository extends JpaRepository<Student, Integer> {
    boolean existsByEmailAndCardId(String email, int cardId);
    boolean existsByStudentNumberAndTeacherEmail(String studentNumber, String teacherEmail);
    List<Student> findByTeacherEmail(String teacherEmail);
    List<Student> findByCardId(int cardId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Student s WHERE s.card.id = :cardId")
    void deleteByCardId(@Param("cardId") int cardId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Student s WHERE s.teacher.id = :teacherId")
    void deleteByTeacherId(@Param("teacherId") int teacherId);
}
