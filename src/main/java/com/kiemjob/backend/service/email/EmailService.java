package com.kiemjob.backend.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final JavaMailSender mailSender;
    private final String senderEmail;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String senderEmail
    ) {
        this.mailSender = mailSender;
        this.senderEmail = senderEmail;
    }

    public void sendVerificationOtp(String recipient, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(recipient);
        message.setSubject("KiemJob - Ma xac minh email");
        message.setText(
                "Xin chao,\n\n"
                + "Ma OTP xac minh email KiemJob cua ban la: "
                + otp + "\n\n"
                + "Ma co hieu luc trong 10 phut.\n"
                + "Khong chia se ma nay voi bat ky ai.\n\n"
                + "Neu ban khong yeu cau ma nay, hay bo qua email."
        );

        mailSender.send(message);
    }

    public void sendResetPasswordOtp(String recipient, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(recipient);
        message.setSubject("KiemJob - Ma xac minh dat lai mat khau");
        message.setText(
                "Xin chao,\n\n"
                + "Ban da yeu cau dat lai mat khau cho tai khoan KiemJob.\n"
                + "Ma OTP xac minh cua ban la: "
                + otp + "\n\n"
                + "Ma co hieu luc trong 10 phut.\n"
                + "Khong chia se ma nay voi bat ky ai de bao ve tai khoan.\n\n"
                + "Neu ban khong thuc hien yeu cau nay, vui long bo qua email."
        );

        mailSender.send(message);
    }

    public void sendApplicationStatusUpdate(
            String recipient,
            String candidateName,
            String jobTitle,
            String companyName,
            String newStatus
    ) {
        SimpleMailMessage message = new SimpleMailMessage();

        String statusText = switch (newStatus.toLowerCase()) {
            case "viewed" -> "Da xem ho so (Viewed)";
            case "shortlisted" -> "Da vao danh sach phong van (Shortlisted)";
            case "interview" -> "Moi phong van (Interview)";
            case "accepted" -> "Trung tuyen / Chap nhan (Accepted)";
            case "rejected" -> "Tu choi (Rejected)";
            case "pending" -> "Dang cho xu ly (Pending)";
            default -> newStatus;
        };

        message.setFrom(senderEmail);
        message.setTo(recipient);
        message.setSubject("KiemJob - Cap nhat trang thai ung tuyen cho vi tri " + (jobTitle != null ? jobTitle : "cong viec"));
        message.setText(
                "Xin chao " + (candidateName != null && !candidateName.isBlank() ? candidateName : "Ung vien") + ",\n\n"
                + "Ho so ung tuyen cua ban tai " + (companyName != null ? companyName : "nha tuyen dung")
                + " cho vi tri \"" + (jobTitle != null ? jobTitle : "") + "\" vua co cap nhat moi.\n\n"
                + "Trang thai hien tai: " + statusText + "\n\n"
                + "Vui long dang nhap vao he thong KiemJob de theo doi chi tiet va trao doi truc tiep voi nha tuyen dung.\n\n"
                + "Chuc ban mot ngay lam viec hieu qua!\n"
                + "Doi ngu KiemJob\n"
        );

        mailSender.send(message);
    }

    public void sendInterviewInvitation(
            String recipient,
            String candidateName,
            String jobTitle,
            String companyName,
            LocalDateTime scheduledAt,
            String note
    ) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(recipient);
        message.setSubject("KiemJob - Thư mời phỏng vấn cho vị trí " + (jobTitle != null ? jobTitle : "công việc"));

        String formattedTime = scheduledAt != null
                ? scheduledAt.format(DATETIME_FORMATTER)
                : "Chưa xác định";

        message.setText(
                "Xin chào " + (candidateName != null && !candidateName.isBlank() ? candidateName : "Ứng viên") + ",\n\n"
                + "Chúc mừng bạn! Nhà tuyển dụng " + (companyName != null ? companyName : "doanh nghiệp tuyển dụng")
                + " đã xem xét hồ sơ và trân trọng mời bạn tham gia buổi phỏng vấn cho vị trí \"" + (jobTitle != null ? jobTitle : "") + "\".\n\n"
                + "THÔNG TIN LỊCH HẸN PHỎNG VẤN:\n"
                + "- Giờ hẹn: " + formattedTime + "\n"
                + "- Ghi chú / Nơi gặp: " + (note != null && !note.isBlank() ? note : "Theo trao đổi với nhà tuyển dụng") + "\n\n"
                + "Vui lòng đăng nhập vào hệ thống KiemJob để theo dõi chi tiết và chuẩn bị tốt cho buổi phỏng vấn.\n\n"
                + "Chúc bạn một buổi phỏng vấn thành công!\n"
                + "Đội ngũ KiemJob\n"
        );

        mailSender.send(message);
    }

    public void sendJobModerationResult(
            String recipient,
            String recruiterName,
            String jobTitle,
            String companyName,
            String decisionStatus,
            String notes
    ) {
        SimpleMailMessage message = new SimpleMailMessage();
        boolean isApproved = "approved".equalsIgnoreCase(decisionStatus) || "open".equalsIgnoreCase(decisionStatus);

        message.setFrom(senderEmail);
        message.setTo(recipient);
        message.setSubject("KiemJob - " + (isApproved ? "Tin tuyển dụng của bạn đã được phê duyệt" : "Thông báo về tin tuyển dụng"));

        String decisionText = isApproved
                ? "đã được Ban quản trị KiemJob phê duyệt và công khai trên hệ thống."
                : "chưa được phê duyệt hoặc đã bị từ chối công khai.";

        StringBuilder content = new StringBuilder();
        content.append("Xin chào ").append(recruiterName != null && !recruiterName.isBlank() ? recruiterName : "Nhà tuyển dụng").append(",\n\n");
        content.append("Tin tuyển dụng \"").append(jobTitle != null ? jobTitle : "").append("\" thuộc doanh nghiệp \"")
                .append(companyName != null ? companyName : "").append("\" ").append(decisionText).append("\n\n");

        if (notes != null && !notes.isBlank()) {
            content.append("Ghi chú từ Quản trị viên: ").append(notes).append("\n\n");
        }

        if (isApproved) {
            content.append("Ứng viên hiện đã có thể tìm kiếm và nộp hồ sơ ứng tuyển vào vị trí này.\n");
        } else {
            content.append("Vui lòng kiểm tra lại nội dung tin tuyển dụng hoặc liên hệ Ban quản trị để biết thêm chi tiết.\n");
        }

        content.append("\nTrân trọng,\nĐội ngũ Kiểm duyệt KiemJob\n");

        message.setText(content.toString());
        try {
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Không thể gửi email thông báo duyệt tin tới {}: {}", recipient, ex.getMessage());
        }
    }

    public void sendJobExpirationReminder(
            String recipient,
            String recruiterName,
            String jobTitle,
            String companyName,
            LocalDate deadline
    ) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(recipient);
        message.setSubject("KiemJob - Nhắc nhở: Tin tuyển dụng sắp hết hạn nhận hồ sơ");

        String formattedDate = deadline != null ? deadline.format(DATE_FORMATTER) : "sắp tới";
        String content = "Xin chào " + (recruiterName != null && !recruiterName.isBlank() ? recruiterName : "Nhà tuyển dụng") + ",\n\n"
                + "Tin tuyển dụng \"" + (jobTitle != null ? jobTitle : "") + "\""
                + (companyName != null && !companyName.isBlank() ? " thuộc doanh nghiệp \"" + companyName + "\"" : "")
                + " trên hệ thống KiemJob sẽ hết hạn nhận hồ sơ vào ngày " + formattedDate + " (trong vòng 3 ngày tới).\n\n"
                + "Nếu vị trí này vẫn còn nhu cầu tuyển dụng, bạn có thể gia hạn thời gian nộp hồ sơ bằng cách cập nhật ngày hết hạn trong trang Quản lý tin tuyển dụng.\n\n"
                + "Sau ngày này, nếu không được gia hạn, trạng thái tin sẽ tự động chuyển sang 'Hết hạn' và không còn hiển thị công khai tới ứng viên.\n\n"
                + "Trân trọng,\nĐội ngũ KiemJob\n";

        message.setText(content);
        try {
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Không thể gửi email nhắc hết hạn tin tuyển dụng tới {}: {}", recipient, ex.getMessage());
        }
    }

    public void sendFeaturedExpirationReminder(
            String recipient,
            String recruiterName,
            String jobTitle,
            String companyName,
            LocalDateTime featuredUntil
    ) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderEmail);
        message.setTo(recipient);
        message.setSubject("KiemJob - Nhắc nhở: Gói tin tuyển dụng nổi bật sắp hết hạn");

        String formattedTime = featuredUntil != null ? featuredUntil.format(DATETIME_FORMATTER) : "sắp tới";
        String content = "Xin chào " + (recruiterName != null && !recruiterName.isBlank() ? recruiterName : "Nhà tuyển dụng") + ",\n\n"
                + "Gói hiển thị ưu tiên nổi bật cho tin tuyển dụng \"" + (jobTitle != null ? jobTitle : "") + "\""
                + (companyName != null && !companyName.isBlank() ? " thuộc doanh nghiệp \"" + companyName + "\"" : "")
                + " sẽ hết hạn vào " + formattedTime + " (trong vòng 3 ngày tới).\n\n"
                + "Sau thời gian này, tin tuyển dụng sẽ trở về vị trí hiển thị thông thường.\n"
                + "Để tiếp tục duy trì thứ hạng ưu tiên trên trang tìm việc và thu hút thêm nhiều ứng viên tiềm năng, bạn có thể đăng ký gói dịch vụ nổi bật mới bất cứ lúc nào.\n\n"
                + "Trân trọng,\nĐội ngũ KiemJob\n";

        message.setText(content);
        try {
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Không thể gửi email nhắc hết hạn gói nổi bật tới {}: {}", recipient, ex.getMessage());
        }
    }
}