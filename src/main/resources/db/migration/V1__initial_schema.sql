-- Hibernate metadata snapshot verified against MySQL 8.4.8.
-- Applied migrations are immutable; evolve this schema with a new versioned file.
SET time_zone = '+00:00';
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

create table dev_personas (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        member_id bigint not null,
        updated_at datetime(6) not null,
        persona_key varchar(80) not null,
        template enum ('GENERAL_CONSUMER','INSTRUCTOR_APPROVED','INSTRUCTOR_PENDING','SUSPENDED_CONSUMER') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table fcm_tokens (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        last_registered_at datetime(6) not null,
        member_id bigint not null,
        updated_at datetime(6) not null,
        token varchar(512) not null,
        client_app enum ('CONSUMER','INSTRUCTOR') not null,
        platform enum ('ANDROID','IOS') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table instructor_matching_settings (
        is_equipment_ready bit not null,
        is_exposed bit not null,
        max_headcount integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        instructor_profile_id bigint not null,
        updated_at datetime(6) not null,
        sport enum ('SKI','SNOWBOARD') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table instructor_matching_settings_available_durations (
        available_duration_minutes integer not null,
        instructor_matching_setting_id bigint not null
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table instructor_matching_settings_lesson_levels (
        instructor_matching_setting_id bigint not null,
        lesson_level enum ('BEGINNER','CERTIFIED','FIRST_TIME','INTERMEDIATE') not null
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table instructor_price_policies (
        additional_person_price_amount integer not null,
        base_price_amount integer not null,
        is_active bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        instructor_profile_id bigint not null,
        updated_at datetime(6) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table instructor_profile_certificates (
        instructor_profile_id bigint not null,
        certificate_type enum ('KSIA_SKI_LEVEL_1','KSIA_SKI_LEVEL_2','KSIA_SKI_LEVEL_3','KSIA_SNOWBOARD_LEVEL_1','KSIA_SNOWBOARD_LEVEL_2','KSIA_SNOWBOARD_LEVEL_3','SBAK_SKI_TEACHING_1','SBAK_SKI_TEACHING_2','SBAK_SKI_TEACHING_3','SBAK_SNOWBOARD_TEACHING_1','SBAK_SNOWBOARD_TEACHING_2','SBAK_SNOWBOARD_TEACHING_3') not null
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table instructor_profiles (
        birth_date date not null,
        career_start_date date not null,
        experience integer not null,
        level integer not null,
        approved_at datetime(6),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        member_id bigint not null,
        resort_id bigint,
        updated_at datetime(6) not null,
        phone varchar(30) not null,
        real_name varchar(50) not null,
        intro TEXT,
        approval_status enum ('APPROVED','PENDING','REJECTED','SUSPENDED') not null,
        certificate_type enum ('KSIA_SKI_LEVEL_1','KSIA_SKI_LEVEL_2','KSIA_SKI_LEVEL_3','KSIA_SNOWBOARD_LEVEL_1','KSIA_SNOWBOARD_LEVEL_2','KSIA_SNOWBOARD_LEVEL_3','SBAK_SKI_TEACHING_1','SBAK_SKI_TEACHING_2','SBAK_SKI_TEACHING_3','SBAK_SNOWBOARD_TEACHING_1','SBAK_SNOWBOARD_TEACHING_2','SBAK_SNOWBOARD_TEACHING_3'),
        gender enum ('FEMALE','MALE') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table lesson_cancellations (
        canceled_at datetime(6) not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        lesson_id bigint not null,
        matching_request_id bigint,
        member_id bigint not null,
        updated_at datetime(6) not null,
        cancel_reason varchar(500) not null,
        canceled_by enum ('ADMIN','CONSUMER','INSTRUCTOR') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table lesson_participants (
        age integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        lesson_id bigint not null,
        matching_request_id bigint not null,
        matching_request_participant_id bigint not null,
        member_id bigint,
        updated_at datetime(6) not null,
        gender enum ('FEMALE','MALE') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table lesson_start_confirmations (
        confirmed_at datetime(6),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        lesson_id bigint not null,
        matching_request_id bigint,
        member_id bigint not null,
        updated_at datetime(6) not null,
        actor_type enum ('CONSUMER','INSTRUCTOR') not null,
        status enum ('CONFIRMED','PENDING') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table lessons (
        duration_minutes integer not null,
        total_headcount integer not null,
        canceled_at datetime(6),
        completed_at datetime(6),
        confirmed_at datetime(6) not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        instructor_profile_id bigint not null,
        matching_offer_id bigint not null,
        resort_id bigint not null,
        scheduled_at datetime(6) not null,
        started_at datetime(6),
        updated_at datetime(6) not null,
        meeting_place varchar(200),
        lesson_level enum ('BEGINNER','CERTIFIED','FIRST_TIME','INTERMEDIATE') not null,
        sport enum ('SKI','SNOWBOARD') not null,
        status enum ('CANCELED','COMPLETED','CONFIRMED','IN_PROGRESS') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table matching_offer_price_snapshots (
        additional_person_price_amount integer not null,
        base_price_amount integer not null,
        consumer_total_amount integer not null,
        fee_rate_bps integer not null,
        instructor_settlement_amount integer not null,
        platform_fee_amount integer not null,
        resort_pass_fee_amount integer not null,
        total_headcount integer not null,
        total_payment_amount integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        instructor_price_policy_id bigint not null,
        matching_offer_id bigint not null,
        platform_fee_policy_id bigint not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table matching_offers (
        created_at datetime(6) not null,
        expires_at datetime(6),
        exposed_at datetime(6) not null,
        id bigint not null auto_increment,
        instructor_profile_id bigint not null,
        matching_request_group_id bigint not null,
        responded_at datetime(6),
        updated_at datetime(6) not null,
        status enum ('ACCEPTED','CANCELED','EXPIRED','OFFERED','REJECTED') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table matching_request_group_items (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        matching_request_group_id bigint not null,
        matching_request_id bigint not null,
        responded_at datetime(6),
        updated_at datetime(6) not null,
        status enum ('ACCEPTED','CANCELED','EXPIRED','NOT_REQUESTED','PENDING','REJECTED') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table matching_request_groups (
        duration_minutes integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        status enum ('CANCELED','CANDIDATE','CONFIRMED','CONSUMER_ACCEPTED','EXPIRED','EXPOSED','INSTRUCTOR_ACCEPTED','LOST','PAYMENT_EXPIRED','PAYMENT_PENDING') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table matching_request_participants (
        age integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        matching_request_id bigint not null,
        updated_at datetime(6) not null,
        gender enum ('FEMALE','MALE') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table matching_request_payments (
        amount integer not null,
        canceled_at datetime(6),
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        matching_offer_id bigint not null,
        matching_request_id bigint not null,
        matching_request_price_snapshot_id bigint not null,
        paid_at datetime(6),
        payment_expires_at datetime(6),
        payment_requested_at datetime(6) not null,
        updated_at datetime(6) not null,
        status enum ('CANCELED','COMPLETED','EXPIRED','PENDING','REFUNDED','REFUND_REQUIRED') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table matching_request_price_snapshots (
        consumer_payment_amount integer not null,
        headcount integer not null,
        lesson_price_amount integer not null,
        resort_pass_fee_amount integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        matching_offer_price_snapshot_id bigint not null,
        matching_request_id bigint not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table matching_requests (
        headcount integer not null,
        is_equipment_ready bit not null,
        canceled_at datetime(6),
        created_at datetime(6) not null,
        expires_at datetime(6),
        id bigint not null auto_increment,
        matching_offer_id bigint,
        member_id bigint not null,
        resort_id bigint not null,
        updated_at datetime(6) not null,
        lesson_level enum ('BEGINNER','CERTIFIED','FIRST_TIME','INTERMEDIATE') not null,
        sport enum ('SKI','SNOWBOARD') not null,
        status enum ('CANCELED','COMPLETED','CONFIRMED','EXPIRED','FAILED','GROUPED','MATCHED','REQUESTED') not null,
        status_reason enum ('CONFIRMATION_TIMEOUT','CONSUMER_CANCELED','CONSUMER_REJECTED_INSTRUCTOR','GROUP_CANCELED','INSTRUCTOR_REJECTED','INSTRUCTOR_TIMEOUT','NO_AVAILABLE_INSTRUCTOR','PAYMENT_TIMEOUT','SYSTEM_ERROR'),
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table matching_requests_requested_duration_minutes (
        duration_minutes integer not null,
        matching_request_id bigint not null
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table members (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        nickname varchar(50) not null,
        profile_image_url varchar(500),
        role enum ('ADMIN','CONSUMER','INSTRUCTOR') not null,
        status enum ('ACTIVE','SUSPENDED','WITHDRAWN') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table notifications (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        member_id bigint not null,
        read_at datetime(6),
        sent_at datetime(6),
        updated_at datetime(6) not null,
        type varchar(50) not null,
        title varchar(100) not null,
        body varchar(500) not null,
        data_json json,
        delivery_status enum ('FAILED','PENDING','SENT') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table oauth_accounts (
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        member_id bigint not null,
        updated_at datetime(6) not null,
        provider_user_id varchar(100) not null,
        provider enum ('KAKAO') not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table platform_fee_policies (
        fee_rate_bps integer not null,
        is_active bit not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table refresh_tokens (
        created_at datetime(6) not null,
        expires_at datetime(6) not null,
        id bigint not null auto_increment,
        member_id bigint not null,
        revoked_at datetime(6),
        updated_at datetime(6) not null,
        token_hash varchar(64) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table resorts (
        pass_fee_amount integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        updated_at datetime(6) not null,
        code varchar(50) not null,
        display_name varchar(100) not null,
        name varchar(100) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    create table reviews (
        rating integer not null,
        created_at datetime(6) not null,
        id bigint not null auto_increment,
        instructor_profile_id bigint not null,
        lesson_id bigint not null,
        member_id bigint not null,
        updated_at datetime(6) not null,
        content varchar(1000) not null,
        primary key (id)
    ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_0900_ai_ci;

    alter table dev_personas
       add constraint uk_dev_personas_persona_key unique (persona_key);

    alter table dev_personas
       add constraint uk_dev_personas_member unique (member_id);

    alter table fcm_tokens
       add constraint uk_fcm_tokens_token unique (token);

    alter table instructor_matching_settings
       add constraint uk_instructor_matching_settings_instructor_profile unique (instructor_profile_id);

    alter table instructor_matching_settings_available_durations
       add constraint uk_instructor_matching_settings_available_duration unique (instructor_matching_setting_id, available_duration_minutes);

    alter table instructor_matching_settings_lesson_levels
       add constraint uk_instructor_matching_settings_lesson_level unique (instructor_matching_setting_id, lesson_level);

    alter table instructor_profile_certificates
       add constraint uk_instructor_profile_certificate unique (instructor_profile_id, certificate_type);

    alter table instructor_profiles
       add constraint uk_instructor_profiles_member unique (member_id);

    alter table lesson_cancellations
       add constraint uk_lesson_cancellations_lesson_member unique (lesson_id, member_id);

    alter table lesson_cancellations
       add constraint uk_lesson_cancellations_matching_request unique (matching_request_id);

    create index idx_lesson_participants_lesson_request_id
       on lesson_participants (lesson_id, matching_request_id, id);

    alter table lesson_participants
       add constraint uk_lesson_participants_lesson_request_participant unique (lesson_id, matching_request_participant_id);

    alter table lesson_start_confirmations
       add constraint uk_lesson_start_confirmations_lesson_member unique (lesson_id, member_id);

    alter table lesson_start_confirmations
       add constraint uk_lesson_confirmations_matching_request unique (matching_request_id);

    alter table lessons
       add constraint uk_lessons_matching_offer unique (matching_offer_id);

    alter table matching_offer_price_snapshots
       add constraint uk_offer_price_snapshots_matching_offer unique (matching_offer_id);

    alter table matching_offers
       add constraint uk_matching_offers_group_instructor unique (matching_request_group_id, instructor_profile_id);

    alter table matching_request_group_items
       add constraint uk_matching_request_group_items_group_request unique (matching_request_group_id, matching_request_id);

    alter table matching_request_payments
       add constraint uk_matching_request_payments_request_offer unique (matching_request_id, matching_offer_id);

    alter table matching_request_price_snapshots
       add constraint uk_matching_request_price_snapshots_request_offer_snapshot unique (matching_request_id, matching_offer_price_snapshot_id);

    alter table matching_requests_requested_duration_minutes
       add constraint uk_matching_requests_duration_minutes unique (matching_request_id, duration_minutes);

    alter table oauth_accounts
       add constraint uk_oauth_accounts_provider_user unique (provider, provider_user_id);

    alter table oauth_accounts
       add constraint uk_oauth_accounts_member_provider unique (member_id, provider);

    alter table refresh_tokens
       add constraint uk_refresh_tokens_token_hash unique (token_hash);

    alter table resorts
       add constraint uk_resorts_code unique (code);

    alter table dev_personas
       add constraint fk_dev_personas_member
       foreign key (member_id)
       references members (id);

    alter table fcm_tokens
       add constraint fk_fcm_tokens_member
       foreign key (member_id)
       references members (id);

    alter table instructor_matching_settings
       add constraint fk_instructor_matching_settings_instructor_profile
       foreign key (instructor_profile_id)
       references instructor_profiles (id);

    alter table instructor_matching_settings_available_durations
       add constraint fk_ims_available_durations_instructor_matching_setting
       foreign key (instructor_matching_setting_id)
       references instructor_matching_settings (id);

    alter table instructor_matching_settings_lesson_levels
       add constraint fk_ims_lesson_levels_instructor_matching_setting
       foreign key (instructor_matching_setting_id)
       references instructor_matching_settings (id);

    alter table instructor_price_policies
       add constraint fk_instructor_price_policies_instructor_profile
       foreign key (instructor_profile_id)
       references instructor_profiles (id);

    alter table instructor_profile_certificates
       add constraint fk_instructor_certificates_instructor_profile
       foreign key (instructor_profile_id)
       references instructor_profiles (id);

    alter table instructor_profiles
       add constraint fk_instructor_profiles_member
       foreign key (member_id)
       references members (id);

    alter table instructor_profiles
       add constraint fk_instructor_profiles_resort
       foreign key (resort_id)
       references resorts (id);

    alter table lesson_cancellations
       add constraint fk_lesson_cancellations_lesson
       foreign key (lesson_id)
       references lessons (id);

    alter table lesson_cancellations
       add constraint fk_lesson_cancellations_matching_request
       foreign key (matching_request_id)
       references matching_requests (id);

    alter table lesson_cancellations
       add constraint fk_lesson_cancellations_member
       foreign key (member_id)
       references members (id);

    alter table lesson_participants
       add constraint fk_lesson_participants_lesson
       foreign key (lesson_id)
       references lessons (id);

    alter table lesson_participants
       add constraint fk_lesson_participants_matching_request
       foreign key (matching_request_id)
       references matching_requests (id);

    alter table lesson_participants
       add constraint fk_lesson_participants_matching_request_participant
       foreign key (matching_request_participant_id)
       references matching_request_participants (id);

    alter table lesson_participants
       add constraint fk_lesson_participants_member
       foreign key (member_id)
       references members (id);

    alter table lesson_start_confirmations
       add constraint fk_lesson_confirmations_lesson
       foreign key (lesson_id)
       references lessons (id);

    alter table lesson_start_confirmations
       add constraint fk_lesson_confirmations_matching_request
       foreign key (matching_request_id)
       references matching_requests (id);

    alter table lesson_start_confirmations
       add constraint fk_lesson_confirmations_member
       foreign key (member_id)
       references members (id);

    alter table lessons
       add constraint fk_lessons_instructor_profile
       foreign key (instructor_profile_id)
       references instructor_profiles (id);

    alter table lessons
       add constraint fk_lessons_matching_offer
       foreign key (matching_offer_id)
       references matching_offers (id);

    alter table lessons
       add constraint fk_lessons_resort
       foreign key (resort_id)
       references resorts (id);

    alter table matching_offer_price_snapshots
       add constraint fk_offer_price_snapshots_instructor_price_policy
       foreign key (instructor_price_policy_id)
       references instructor_price_policies (id);

    alter table matching_offer_price_snapshots
       add constraint fk_offer_price_snapshots_matching_offer
       foreign key (matching_offer_id)
       references matching_offers (id);

    alter table matching_offer_price_snapshots
       add constraint fk_offer_price_snapshots_platform_fee_policy
       foreign key (platform_fee_policy_id)
       references platform_fee_policies (id);

    alter table matching_offers
       add constraint fk_matching_offers_instructor_profile
       foreign key (instructor_profile_id)
       references instructor_profiles (id);

    alter table matching_offers
       add constraint fk_matching_offers_matching_request_group
       foreign key (matching_request_group_id)
       references matching_request_groups (id);

    alter table matching_request_group_items
       add constraint fk_matching_group_items_matching_request
       foreign key (matching_request_id)
       references matching_requests (id);

    alter table matching_request_group_items
       add constraint fk_matching_group_items_matching_request_group
       foreign key (matching_request_group_id)
       references matching_request_groups (id);

    alter table matching_request_participants
       add constraint fk_request_participants_matching_request
       foreign key (matching_request_id)
       references matching_requests (id);

    alter table matching_request_payments
       add constraint fk_matching_payments_matching_offer
       foreign key (matching_offer_id)
       references matching_offers (id);

    alter table matching_request_payments
       add constraint fk_matching_payments_matching_request
       foreign key (matching_request_id)
       references matching_requests (id);

    alter table matching_request_payments
       add constraint fk_matching_payments_matching_request_price_snapshot
       foreign key (matching_request_price_snapshot_id)
       references matching_request_price_snapshots (id);

    alter table matching_request_price_snapshots
       add constraint fk_request_price_snapshots_matching_offer_price_snapshot
       foreign key (matching_offer_price_snapshot_id)
       references matching_offer_price_snapshots (id);

    alter table matching_request_price_snapshots
       add constraint fk_request_price_snapshots_matching_request
       foreign key (matching_request_id)
       references matching_requests (id);

    alter table matching_requests
       add constraint fk_matching_requests_matching_offer
       foreign key (matching_offer_id)
       references matching_offers (id);

    alter table matching_requests
       add constraint fk_matching_requests_member
       foreign key (member_id)
       references members (id);

    alter table matching_requests
       add constraint fk_matching_requests_resort
       foreign key (resort_id)
       references resorts (id);

    alter table matching_requests_requested_duration_minutes
       add constraint fk_request_durations_matching_request
       foreign key (matching_request_id)
       references matching_requests (id);

    alter table notifications
       add constraint fk_notifications_member
       foreign key (member_id)
       references members (id);

    alter table oauth_accounts
       add constraint fk_oauth_accounts_member
       foreign key (member_id)
       references members (id);

    alter table refresh_tokens
       add constraint fk_refresh_tokens_member
       foreign key (member_id)
       references members (id);

    alter table reviews
       add constraint fk_reviews_instructor_profile
       foreign key (instructor_profile_id)
       references instructor_profiles (id);

    alter table reviews
       add constraint fk_reviews_lesson
       foreign key (lesson_id)
       references lessons (id);

    alter table reviews
       add constraint fk_reviews_member
       foreign key (member_id)
       references members (id);
