package com.datapeice.slbackend.repository;

import com.datapeice.slbackend.entity.BotMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BotMessageRepository extends JpaRepository<BotMessage, Long> {

    List<BotMessage> findByRecipientUserIdOrderByCreatedAtAsc(Long recipientUserId);

    @Query("SELECT bm FROM BotMessage bm WHERE bm.id IN " +
           "(SELECT MAX(m.id) FROM BotMessage m GROUP BY m.recipientUser.id)")
    List<BotMessage> findLatestMessagePerRecipient();

    void deleteByRecipientUserId(Long recipientUserId);
}
