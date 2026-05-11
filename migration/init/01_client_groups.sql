CREATE TABLE IF NOT EXISTS client_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
ALTER TABLE clients ADD COLUMN IF NOT EXISTS group_id UUID REFERENCES client_groups(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_clients_group_id ON clients(group_id);
