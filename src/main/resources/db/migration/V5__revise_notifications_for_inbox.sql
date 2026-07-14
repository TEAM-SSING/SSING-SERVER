alter table notifications
    add column client_app enum ('CONSUMER','INSTRUCTOR') null after member_id;

update notifications n
    join members m on n.member_id = m.id
set n.client_app = case
    when m.role = 'INSTRUCTOR' then 'INSTRUCTOR'
    else 'CONSUMER'
end
where n.client_app is null;

alter table notifications
    modify column client_app enum ('CONSUMER','INSTRUCTOR') not null,
    modify column type enum ('MATCHING_OFFER_RECEIVED','MATCHING_OFFER_CLOSED','MATCHING_CONFIRMED') not null,
    drop column delivery_status,
    drop column sent_at;

create index idx_notifications_member_created_id
    on notifications (member_id, created_at, id);
