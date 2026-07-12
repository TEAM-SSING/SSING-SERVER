delete from lesson_start_confirmations
where status = 'PENDING';

alter table lesson_start_confirmations
    drop column status;
