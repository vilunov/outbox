create table texts
(
    id         uuid primary key,
    author     varchar,
    text       varchar,
    created_on timestamp default now()
);

create table outbox
(
    id                  bigserial PRIMARY KEY,
    create_time         timestamp default now(),
    kafka_topic         varchar(249)   not null,
    kafka_key           varchar(100)   not null,
    kafka_value         varchar(10000) not null,
    kafka_header_keys   text[] not null default array[]::text[],
    kafka_header_values text[] not null default array[]::text[],
    leader_id           uuid
);

create table processed_texts
(
    id           uuid primary key,
    processed_on timestamp default now()
);
