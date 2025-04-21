CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    customer_id BIGINT,
    coffee_type VARCHAR(100) NOT NULL,
    size VARCHAR(50) NOT NULL,
    milk_type VARCHAR(50),
    syrup_type VARCHAR(50),
    total_price DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);