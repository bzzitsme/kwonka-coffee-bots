CREATE TABLE coffee_shops
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    address    VARCHAR(255),
    code       VARCHAR(50)  NOT NULL UNIQUE,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

CREATE TABLE orders
(
    id             BIGSERIAL PRIMARY KEY,
    order_number   VARCHAR(50)    NOT NULL UNIQUE,
    customer_id    BIGINT,
    coffee_shop_id BIGINT         NOT NULL REFERENCES coffee_shops (id),
    coffee_type    VARCHAR(100)   NOT NULL,
    size           VARCHAR(50)    NOT NULL,
    milk_type      VARCHAR(50),
    syrup_type     VARCHAR(50),
    total_price    DECIMAL(10, 2) NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    created_at     TIMESTAMP      NOT NULL,
    updated_at     TIMESTAMP      NOT NULL
);

INSERT INTO coffee_shops (name, address, code, active, created_at, updated_at)
VALUES ('ONESHOTT Bi Group', '', 'BIGROUP', TRUE, NOW(), NOW()),
       ('ONESHOTT Green Mall', '', 'GREENMALL', TRUE, NOW(), NOW());