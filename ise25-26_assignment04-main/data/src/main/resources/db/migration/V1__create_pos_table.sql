SET TIME ZONE 'UTC';

CREATE SEQUENCE pos_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE pos (
    id bigint NOT NULL PRIMARY KEY,
    created_at timestamp NOT NULL,
    updated_at timestamp NOT NULL,
    name varchar(255) NOT NULL UNIQUE CHECK (name <> ''),
    description text CHECK (description <> ''),
    type varchar(255) NOT NULL,
    campus varchar(255) NOT NULL,
    street varchar(255) NOT NULL CHECK (street <> ''),
    house_number int NOT NULL,
    house_number_suffix varchar(1),
    postal_code int NOT NULL,
    city varchar(255) NOT NULL CHECK (city <> '')
);
