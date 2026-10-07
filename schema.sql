-- =========================================================
-- Shop App - Supabase PostgreSQL Schema & Seed Data (Lab 2)
-- =========================================================

-- 1. Inventory Table
CREATE TABLE IF NOT EXISTS inventory (
    product_id VARCHAR(50) PRIMARY KEY,
    product_name VARCHAR(100) NOT NULL,
    stock INT NOT NULL CHECK (stock >= 0)
);

-- 2. Orders Table (Supports CONFIRMED, REJECTED, CANCELLED)
CREATE TABLE IF NOT EXISTS orders (
    order_id VARCHAR(50) PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Order Items Table (Multi-Item Order Relationships)
CREATE TABLE IF NOT EXISTS order_items (
    id SERIAL PRIMARY KEY,
    order_id VARCHAR(50) REFERENCES orders(order_id) ON DELETE CASCADE,
    product_id VARCHAR(50) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0)
);

-- 4. Notifications Table (Event Feed Log)
CREATE TABLE IF NOT EXISTS notifications (
    notification_id SERIAL PRIMARY KEY,
    message TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. Seed Inventory Catalog
INSERT INTO inventory (product_id, product_name, stock) VALUES
    ('P100', 'Mechanical Keyboard', 25),
    ('P200', 'Wireless Mouse', 10),
    ('P300', 'USB-C Monitor Hub', 5)
ON CONFLICT (product_id) DO UPDATE 
SET product_name = EXCLUDED.product_name, stock = EXCLUDED.stock;
