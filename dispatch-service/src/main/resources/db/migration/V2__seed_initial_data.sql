-- Seed default cluster
INSERT INTO ${dispatch-schema}.cluster (id, name, url, status)
VALUES ('00000000-0000-0000-0000-000000000010',
        'default',
        'http://localhost:4201',
        'ACTIVE')
ON CONFLICT (name) DO NOTHING;

-- Seed initial backoffice admin user. Password: Admin@1234 (BCrypt $2a$12$)
INSERT INTO ${dispatch-schema}.app_user (id, email, password_hash, first_name, last_name, user_type, status)
VALUES ('00000000-0000-0000-0000-000000000001',
        'admin@dispatch.local',
        '$2a$12$bhkiWBCSkiFzr4fBYKbhHOwwTZwqUHzvoipBQUjzElhbrbRqRQI3a',
        'Admin', 'User', 'BACKOFFICE', 'ACTIVE')
ON CONFLICT (email) DO NOTHING;
