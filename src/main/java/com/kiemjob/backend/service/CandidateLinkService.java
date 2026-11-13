
package com.kiemjob.backend.service;

import com.kiemjob.backend.dto.candidate.CandidateLinkRequest;
import com.kiemjob.backend.dto.candidate.CandidateLinkResponse;
import com.kiemjob.backend.entity.Account;
import com.kiemjob.backend.entity.Candidate;
import com.kiemjob.backend.entity.CandidateLink;
import com.kiemjob.backend.repository.AccountRepository;
import com.kiemjob.backend.repository.CandidateRepository;
import com.kiemjob.backend.repository.CandidateLinkRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class CandidateLinkService {

    private final CandidateLinkRepository linkRepository;
    private final CandidateRepository candidateRepository;
    private final AccountRepository accountRepository;

    public CandidateLinkService(
            CandidateLinkRepository linkRepository,
            CandidateRepository candidateRepository,
            AccountRepository accountRepository) {
        this.linkRepository = linkRepository;
        this.candidateRepository = candidateRepository;
        this.accountRepository = accountRepository;
    }

    private Candidate getCurrentCandidate(String email) {
        Account account = accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Tài khoản không tồn tại"
                ));


        return candidateRepository.findByAccountId(account.getId())
                .orElseGet(() -> {
                    Candidate c = new Candidate();
                    c.setAccountId(account.getId());
                    String emailPart = account.getEmail().split("@")[0].replaceAll("[._-]", " ").trim();
                    c.setFullname(emailPart.isEmpty() ? "Ứng viên" : emailPart);
                    return candidateRepository.save(c);
                });
    }

    private CandidateLink getOwnedLink(Long id, Long candidateId) {
        return linkRepository.findByIdAndCandidate_Id(id, candidateId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy liên kết"
                ));
    }

    @Transactional
    public CandidateLinkResponse create(
            String email,
            CandidateLinkRequest request) {

        Candidate candidate = getCurrentCandidate(email);

        CandidateLink entity = new CandidateLink();
        entity.setCandidate(candidate);
        applyRequest(entity, request);

        return CandidateLinkResponse.from(linkRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<CandidateLinkResponse> getAll(String email) {
        Candidate candidate = getCurrentCandidate(email);

        return linkRepository
                .findAllByCandidate_IdOrderByIdAsc(candidate.getId())
                .stream()
                .map(CandidateLinkResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CandidateLinkResponse> getAllByCandidateId(Long candidateId) {
        return linkRepository
                .findAllByCandidate_IdOrderByIdAsc(candidateId)
                .stream()
                .map(CandidateLinkResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CandidateLinkResponse getById(String email, Long id) {
        Candidate candidate = getCurrentCandidate(email);

        return CandidateLinkResponse.from(
                getOwnedLink(id, candidate.getId())
        );
    }

    @Transactional
    public CandidateLinkResponse update(
            String email,
            Long id,
            CandidateLinkRequest request) {

        Candidate candidate = getCurrentCandidate(email);
        CandidateLink entity = getOwnedLink(id, candidate.getId());

        applyRequest(entity, request);

        return CandidateLinkResponse.from(linkRepository.save(entity));
    }

    @Transactional
    public void delete(String email, Long id) {
        Candidate candidate = getCurrentCandidate(email);
        CandidateLink entity = getOwnedLink(id, candidate.getId());

        linkRepository.delete(entity);
    }

    private void applyRequest(
            CandidateLink entity,
            CandidateLinkRequest request) {

        entity.setType(request.type().trim().toLowerCase(Locale.ROOT));
        entity.setLink(request.link().trim());
    }
}
