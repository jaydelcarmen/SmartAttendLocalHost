package smartattendLocal.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import smartattendLocal.Entity.Attendance;
import smartattendLocal.Entity.Student;
import smartattendLocal.Entity.Teacher;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private PhotoService photoService;

    public boolean sendRevokeNotification(Student student, Attendance attendance, String photoPath, Teacher teacher) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(student.getEmail());
            helper.setSubject("Attendance Verification Required - " + attendance.getClassName());
            helper.setFrom("smartattend@system.com");

            String htmlBody = buildRevokeEmailBody(student, attendance, photoPath, teacher);
            helper.setText(htmlBody, true);

            if (photoPath != null && !photoPath.isEmpty()) {
                attachPhotoToEmail(helper, photoPath);
            }

            mailSender.send(message);
            return true;

        } catch (MessagingException | IOException e) {
            System.err.println("Failed to send revoke notification email: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private String buildRevokeEmailBody(Student student, Attendance attendance, String photoPath, Teacher teacher) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm a");

        String scanDate = attendance.getScanTime().format(dateFormatter);
        String scanTime = attendance.getScanTime().format(timeFormatter);

        String photoBase64 = "";
        try {
            if (photoPath != null && !photoPath.isEmpty()) {
                byte[] photoData = photoService.getPhoto(photoPath);
                photoBase64 = Base64.getEncoder().encodeToString(photoData);
            }
        } catch (IOException e) {
            System.err.println("Failed to read photo for email: " + e.getMessage());
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<style>\n");
        html.append("  body { font-family: Arial, sans-serif; color: #333; line-height: 1.6; }\n");
        html.append("  .container { max-width: 600px; margin: 0 auto; padding: 20px; }\n");
        html.append("  .header { background-color: #0F6577; color: white; padding: 20px; border-radius: 8px 8px 0 0; }\n");
        html.append("  .content { background-color: #f9f9f9; padding: 20px; border: 1px solid #ddd; }\n");
        html.append("  .footer { background-color: #222a35; color: white; padding: 15px; text-align: center; border-radius: 0 0 8px 8px; font-size: 12px; }\n");
        html.append("  .alert { background-color: #fee2e2; border-left: 4px solid #dc2626; padding: 15px; margin: 15px 0; border-radius: 4px; }\n");
        html.append("  .photo-section { margin: 20px 0; text-align: center; }\n");
        html.append("  .photo-section img { max-width: 100%; height: auto; border-radius: 8px; border: 1px solid #ddd; margin-top: 10px; }\n");
        html.append("  .details { background-color: #f0f9ff; padding: 15px; border-radius: 6px; margin: 15px 0; }\n");
        html.append("  .details table { width: 100%; border-collapse: collapse; }\n");
        html.append("  .details td { padding: 8px; border-bottom: 1px solid #e5e7eb; }\n");
        html.append("  .details td:first-child { font-weight: bold; width: 40%; color: #0F6577; }\n");
        html.append("  .details td:last-child { text-align: right; }\n");
        html.append("  .contact-box { background-color: #fef3c7; border-left: 4px solid #f59e0b; padding: 15px; margin: 15px 0; border-radius: 4px; }\n");
        html.append("  .button { display: inline-block; background-color: #0F6577; color: white; padding: 10px 20px; text-decoration: none; border-radius: 4px; margin-top: 10px; }\n");
        html.append("</style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("<div class=\"container\">\n");
        html.append("  <div class=\"header\">\n");
        html.append("    <h2>Attendance Verification Notice</h2>\n");
        html.append("  </div>\n");
        html.append("  <div class=\"content\">\n");
        html.append("    <p>Hi <strong>").append(student.getName()).append("</strong>,</p>\n");
        html.append("    <div class=\"alert\">\n");
        html.append("      <strong>⚠️ Your attendance for ").append(attendance.getClassName()).append(" has been REVOKED.</strong>\n");
        html.append("    </div>\n");
        html.append("    <p>Your attendance record for this class has been changed to <strong style=\"color: #dc2626;\">Absent</strong> due to photo verification.</p>\n");
        html.append("    <h3 style=\"color: #0F6577; margin-top: 20px;\">Reason for Revocation</h3>\n");
        html.append("    <p>The captured photo verification indicated you may not be the correct student enrolled in this class. To maintain attendance integrity, your attendance record has been revoked.</p>\n");

        if (!photoBase64.isEmpty()) {
            html.append("    <div class=\"photo-section\">\n");
            html.append("      <strong>Photo Captured During Attendance:</strong>\n");
            html.append("      <img src=\"data:image/jpeg;base64,").append(photoBase64).append("\" alt=\"Captured attendance photo\" style=\"max-width: 100%; height: auto; border-radius: 8px; border: 1px solid #ddd; margin-top: 10px;\">\n");
            html.append("    </div>\n");
        }

        html.append("    <div class=\"details\">\n");
        html.append("      <strong>Class Details:</strong>\n");
        html.append("      <table>\n");
        html.append("        <tr><td>Class Name:</td><td>").append(attendance.getClassName()).append("</td></tr>\n");
        html.append("        <tr><td>Date:</td><td>").append(scanDate).append("</td></tr>\n");
        html.append("        <tr><td>Time Scanned:</td><td>").append(scanTime).append("</td></tr>\n");
        html.append("        <tr><td>Previous Status:</td><td><span style=\"color: #16a34a;\">").append(attendance.getStatus()).append("</span></td></tr>\n");
        html.append("        <tr><td>Current Status:</td><td><span style=\"color: #dc2626;\">Absent</span></td></tr>\n");
        html.append("      </table>\n");
        html.append("    </div>\n");

        html.append("    <div class=\"contact-box\">\n");
        html.append("      <strong>If you believe this is an error, please contact your instructor:</strong>\n");
        html.append("      <p style=\"margin: 8px 0;\">\n");
        html.append("        <strong>").append(teacher.getName()).append("</strong><br>\n");
        html.append("        <a href=\"mailto:").append(teacher.getEmail()).append("\">").append(teacher.getEmail()).append("</a>\n");
        html.append("      </p>\n");
        html.append("    </div>\n");

        html.append("    <p style=\"margin-top: 20px; font-size: 12px; color: #666;\">\n");
        html.append("      This is an automated notification from SmartAttend. Please do not reply to this email.\n");
        html.append("    </p>\n");
        html.append("  </div>\n");
        html.append("  <div class=\"footer\">\n");
        html.append("    <p style=\"margin: 0;\">SmartAttend Attendance Management System</p>\n");
        html.append("    <p style=\"margin: 5px 0 0 0;\">© 2026 All Rights Reserved</p>\n");
        html.append("  </div>\n");
        html.append("</div>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        return html.toString();
    }

    private void attachPhotoToEmail(MimeMessageHelper helper, String photoPath) throws MessagingException, IOException {
        try {
            byte[] photoData = photoService.getPhoto(photoPath);
            String fileName = photoPath.contains("/") ? photoPath.substring(photoPath.lastIndexOf("/") + 1) : photoPath;
            String mediaType = photoPath.endsWith(".png") ? "image/png" : "image/jpeg";
            helper.addAttachment(fileName, new org.springframework.core.io.ByteArrayResource(photoData), mediaType);
        } catch (IOException e) {
            System.err.println("Failed to attach photo to email: " + e.getMessage());
        }
    }
}
