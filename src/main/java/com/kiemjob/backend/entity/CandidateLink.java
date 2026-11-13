
package com.kiemjob.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "candidates_link")
public class CandidateLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidat_id", nullable = false)
    private Candidate candidate;

    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "link", nullable = false, length = 500)
    private String link;

    public CandidateLink() {
    }

    public Long getId() {
        return id;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public void setCandidate(Candidate candidate) {
        this.candidate = candidate;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }
}
