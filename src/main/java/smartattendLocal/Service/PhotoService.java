package smartattendLocal.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class PhotoService {

    @Value("${photo.upload.dir:uploads/photos}")
    private String uploadDir;

    public PhotoService() {
        this.uploadDir = "uploads/photos";
    }

    public void ensureUploadDirExists() {
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public String savePhoto(MultipartFile file, int attendanceId) throws IOException {
        ensureUploadDirExists();

        String originalFileName = file.getOriginalFilename();
        String extension = originalFileName != null ? originalFileName.substring(originalFileName.lastIndexOf(".")) : ".jpg";
        String fileName = "attendance_" + attendanceId + "_" + System.currentTimeMillis() + extension;
        Path filePath = Paths.get(uploadDir, fileName);

        Files.write(filePath, file.getBytes());

        return fileName;
    }

    public byte[] getPhoto(String fileName) throws IOException {
        Path filePath = Paths.get(uploadDir, fileName);
        if (!Files.exists(filePath)) {
            throw new IOException("Photo not found: " + fileName);
        }
        return Files.readAllBytes(filePath);
    }

    public void deletePhoto(String fileName) {
        try {
            Path filePath = Paths.get(uploadDir, fileName);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
        } catch (IOException e) {
            System.err.println("Failed to delete photo: " + fileName + " - " + e.getMessage());
        }
    }

    public boolean isValidImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && (contentType.equals("image/jpeg") || contentType.equals("image/png") || contentType.equals("image/jpg"));
    }

    public boolean isFileSizeValid(MultipartFile file) {
        return file.getSize() > 0 && file.getSize() <= 5 * 1024 * 1024;
    }
}
