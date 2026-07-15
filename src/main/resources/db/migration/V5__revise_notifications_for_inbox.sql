-- 기존 앱과의 롤백 호환성을 유지하는 expand 단계다.
-- delivery_status, sent_at, 자유 형식 type은 이번 버전에서 보존하고 후속 contract migration에서 정리한다.
alter table notifications
    add column client_app enum ('CONSUMER','INSTRUCTOR') null after member_id,
    modify column delivery_status enum ('FAILED','PENDING','SENT') not null default 'PENDING',
    add index idx_notifications_member_app_created_id (member_id, client_app, created_at, id);

update notifications n
    join members m on n.member_id = m.id
set n.client_app = case
    when m.role = 'INSTRUCTOR' then 'INSTRUCTOR'
    when m.role = 'CONSUMER' then 'CONSUMER'
end
where n.client_app is null
  and m.role in ('CONSUMER', 'INSTRUCTOR')
  and n.type in (
      'MATCHING_OFFER_RECEIVED',
      'MATCHING_OFFER_CLOSED',
      'MATCHING_CONFIRMED'
  );

-- ADMIN 또는 지원하지 않는 legacy type은 삭제·오분류하지 않고 client_app=NULL로 보존한다.
-- 새 알림함 조회는 client_app을 필수 조건으로 사용하므로 이 행들은 노출되지 않는다.
