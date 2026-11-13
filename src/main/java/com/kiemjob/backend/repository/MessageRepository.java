
package com.kiemjob.backend.repository;

import com.kiemjob.backend.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository
        extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAscIdAsc(
            Long conversationId
    );
}
