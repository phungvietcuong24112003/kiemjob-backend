package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.conversation.*;
import com.kiemjob.backend.entity.Account;
import com.kiemjob.backend.entity.Candidate;
import com.kiemjob.backend.entity.Conversation;
import com.kiemjob.backend.entity.Message;
import com.kiemjob.backend.entity.Recruiter;
import com.kiemjob.backend.repository.AccountRepository;
import com.kiemjob.backend.repository.CandidateRepository;
import com.kiemjob.backend.repository.CompanyRepository;
import com.kiemjob.backend.repository.RecruiterRepository;
import com.kiemjob.backend.repository.ConversationRepository;
import com.kiemjob.backend.repository.MessageRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final CandidateRepository candidateRepository;
    private final RecruiterRepository recruiterRepository;
    private final CompanyRepository companyRepository;
    private final AccountRepository accountRepository;

    private record Participant(Long id, String type) {
    }

    // Xác định hồ sơ từ email trong JWT
    private Participant getParticipant(String email) {

        Account account = accountRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Không tìm thấy tài khoản đăng nhập"));

        Long accountId = account.getId();

        Optional<Candidate> candidate = candidateRepository.findByAccountId(accountId);

        if (candidate.isPresent()) {
            return new Participant(
                    candidate.get().getId(),
                    "candidate");
        }

        Optional<Recruiter> recruiter = recruiterRepository.findByAccountId(accountId);

        if (recruiter.isPresent()) {
            return new Participant(
                    recruiter.get().getId(),
                    "recruiter");
        }

        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Tài khoản chưa có hồ sơ ứng viên hoặc nhà tuyển dụng");
    }

    private Conversation getConversation(Long id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy cuộc hội thoại"));
    }

    // Chỉ người tham gia mới được truy cập hội thoại
    private Participant requireParticipant(
            Conversation conversation,
            String email) {
        Participant participant = getParticipant(email);

        boolean allowed = ("candidate".equals(participant.type())
                && conversation.getCandidateId()
                        .equals(participant.id()))
                ||
                ("recruiter".equals(participant.type())
                        && conversation.getRecruiterId()
                                .equals(participant.id()));

        if (!allowed) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn không có quyền truy cập cuộc hội thoại này");
        }

        return participant;
    }

    // 1. Xem danh sách hội thoại
    @Transactional(readOnly = true)
    public List<ConversationResponse> getConversations(String email) {

        Participant participant = getParticipant(email);

        List<Conversation> conversations;

        if ("candidate".equals(participant.type())) {
            conversations = conversationRepository
                    .findByCandidateIdOrderByIdDesc(participant.id());
        } else {
            conversations = conversationRepository
                    .findByRecruiterIdOrderByIdDesc(participant.id());
        }

        return conversations.stream()
                .map(this::enrichConversationResponse)
                .toList();
    }

    // 2. Xem chi tiết hội thoại
    @Transactional(readOnly = true)
    public ConversationResponse getConversation(
            Long conversationId,
            String email) {
        Conversation conversation = getConversation(conversationId);

        requireParticipant(conversation, email);

        return enrichConversationResponse(conversation);
    }

    // Làm giàu dữ liệu hội thoại cho giao diện
    private ConversationResponse enrichConversationResponse(Conversation conversation) {
        ConversationResponse resp = ConversationResponse.from(conversation);

        if (conversation.getCandidateId() != null) {
            candidateRepository.findById(conversation.getCandidateId()).ifPresent(c -> {
                resp.setCandidateName(c.getFullname());
                resp.setCandidateAvatar(c.getAvatarUrl());
                resp.setCandidateHeadline(c.getHeadline());
            });
        }

        if (conversation.getRecruiterId() != null) {
            recruiterRepository.findById(conversation.getRecruiterId()).ifPresent(r -> {
                resp.setRecruiterName(r.getFullName());
                if (r.getCompanyId() != null) {
                    companyRepository.findById(r.getCompanyId()).ifPresent(comp -> {
                        resp.setCompanyName(comp.getName());
                        resp.setCompanyLogo(comp.getLogoUrl());
                    });
                }
            });
        }

        List<Message> msgs = messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversation.getId());
        if (!msgs.isEmpty()) {
            Message lastMsg = msgs.get(msgs.size() - 1);
            resp.setLastMessage(lastMsg.getContent());
            resp.setLastMessageTime(lastMsg.getCreatedAt());
        }

        return resp;
    }

    // 3. Tạo hội thoại
    public ConversationResponse createConversation(
            CreateConversationRequest request,
            String email) {
        Participant participant = getParticipant(email);

        if ("candidate".equals(participant.type())) {
            if (request.getCandidateId() == null) {
                request.setCandidateId(participant.id());
            } else if (!participant.id().equals(request.getCandidateId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Bạn chỉ được tạo hội thoại với tư cách tài khoản của mình");
            }

            if (request.getRecruiterId() == null && request.getCompanyId() != null) {
                List<Recruiter> recruiters = recruiterRepository.findByCompanyId(request.getCompanyId());

                if (recruiters.isEmpty()) {
                    throw new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Không tìm thấy nhà tuyển dụng thuộc công ty này");
                }

                if (recruiters.size() > 1) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Công ty có nhiều nhà tuyển dụng. Cần xác định recruiterId cụ thể.");
                }

                request.setRecruiterId(recruiters.get(0).getId());
            }

            if (request.getRecruiterId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Thiếu thông tin nhà tuyển dụng (recruiterId hoặc companyId)");
            }
        } else if ("recruiter".equals(participant.type())) {
            if (request.getRecruiterId() == null) {
                request.setRecruiterId(participant.id());
            } else if (!participant.id().equals(request.getRecruiterId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Bạn chỉ được tạo hội thoại với tư cách tài khoản của mình");
            }

            if (request.getCandidateId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Thiếu candidateId của ứng viên");
            }
        } else {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ ứng viên và nhà tuyển dụng mới có thể tạo cuộc hội thoại");
        }

        var existing = conversationRepository
                .findByCandidateIdAndRecruiterId(
                        request.getCandidateId(),
                        request.getRecruiterId());

        if (existing.isPresent()) {
            return enrichConversationResponse(existing.get());
        }

        Conversation conversation = new Conversation();
        conversation.setCandidateId(request.getCandidateId());
        conversation.setRecruiterId(request.getRecruiterId());

        try {
            return enrichConversationResponse(
                    conversationRepository.saveAndFlush(conversation));
        } catch (DataIntegrityViolationException ex) {
            var retry = conversationRepository
                    .findByCandidateIdAndRecruiterId(
                            request.getCandidateId(),
                            request.getRecruiterId());
            if (retry.isPresent()) {
                return enrichConversationResponse(retry.get());
            }
            throw ex;
        }
    }

    // 4. Xóa hội thoại
    public void deleteConversation(Long conversationId, String email) {

        Conversation conversation = getConversation(conversationId);

        requireParticipant(conversation, email);

        // PostgreSQL ON DELETE CASCADE xóa các tin nhắn liên quan
        conversationRepository.delete(conversation);
    }

    // 5. Xem tin nhắn của hội thoại
    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(
            Long conversationId,
            String email) {
        Conversation conversation = getConversation(conversationId);

        requireParticipant(conversation, email);

        return messageRepository
                .findByConversationIdOrderByCreatedAtAscIdAsc(conversationId)
                .stream()
                .map(MessageResponse::from)
                .toList();
    }

    // 6. Gửi tin nhắn
    public MessageResponse sendMessage(
            Long conversationId,
            SendMessageRequest request,
            String email) {
        Conversation conversation = getConversation(conversationId);

        Participant participant = requireParticipant(conversation, email);

        String content = request.getContent().trim();

        if (content.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nội dung tin nhắn không được để trống");
        }

        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSenderType(participant.type());
        message.setContent(content);

        return MessageResponse.from(
                messageRepository.saveAndFlush(message));
    }

    // 7. Xóa tin nhắn: chỉ người gửi mới được xóa
    public void deleteMessage(Long messageId, String email) {

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy tin nhắn"));

        Conversation conversation = getConversation(
                message.getConversationId());

        Participant participant = requireParticipant(conversation, email);

        if (!message.getSenderType().equals(participant.type())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn chỉ được xóa tin nhắn do chính mình gửi");
        }

        messageRepository.delete(message);
    }
}
