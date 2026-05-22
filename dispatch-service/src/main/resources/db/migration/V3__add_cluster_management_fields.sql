ALTER TABLE ${dispatch-schema}.cluster ADD COLUMN IF NOT EXISTS api_url VARCHAR(500);

UPDATE ${dispatch-schema}.cluster
SET api_url = 'http://localhost:8081'
WHERE id = '00000000-0000-0000-0000-000000000010';
