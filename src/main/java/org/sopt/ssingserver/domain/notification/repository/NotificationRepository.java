package org.sopt.ssingserver.domain.notification.repository;

import java.time.Instant;
import java.util.List;
import org.sopt.ssingserver.domain.notification.entity.Notification;
import org.sopt.ssingserver.domain.notification.enums.ClientApp;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            select notification
            from Notification notification
            where notification.member.id = :memberId
              and notification.clientApp = :clientApp
              and notification.createdAt >= :since
            order by notification.createdAt desc, notification.id desc
            """)
    List<Notification> findFirstPage(
            @Param("memberId") Long memberId,
            @Param("clientApp") ClientApp clientApp,
            @Param("since") Instant since,
            Pageable pageable
    );

    @Query("""
            select notification
            from Notification notification
            where notification.member.id = :memberId
              and notification.clientApp = :clientApp
              and notification.createdAt >= :since
              and (
                  notification.createdAt < :cursorCreatedAt
                  or (
                      notification.createdAt = :cursorCreatedAt
                      and notification.id < :cursorNotificationId
                  )
              )
            order by notification.createdAt desc, notification.id desc
            """)
    List<Notification> findNextPage(
            @Param("memberId") Long memberId,
            @Param("clientApp") ClientApp clientApp,
            @Param("since") Instant since,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorNotificationId") Long cursorNotificationId,
            Pageable pageable
    );
}
