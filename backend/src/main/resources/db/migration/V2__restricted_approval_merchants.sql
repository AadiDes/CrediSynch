-- Module A (ADR 0007): a merchant catalog to resolve messy card descriptors against.
-- descriptor_pattern is the representative "clean" descriptor for trigram matching; the vector
-- embedding column added in V1 stays for a future Titan-backed matcher (ADR 0003's fallback path
-- is this trigram matcher, used until Bedrock model access is provisioned).

CREATE EXTENSION IF NOT EXISTS pg_trgm;

ALTER TABLE merchants ADD COLUMN descriptor_pattern VARCHAR(256) NOT NULL DEFAULT '';
CREATE INDEX idx_merchants_descriptor_trgm ON merchants USING gin (descriptor_pattern gin_trgm_ops);

INSERT INTO merchants (id, partner_id, display_name, category, descriptor_pattern) VALUES
    (gen_random_uuid(), 'partner-electronics', 'Circuit & Byte Electronics', 'ELECTRONICS', 'CIRCUIT BYTE ELECTRONICS'),
    (gen_random_uuid(), 'partner-groceries',   'GreenCart Grocers',          'GROCERY',     'GREENCART GROCERS'),
    (gen_random_uuid(), 'partner-fuel',        'QuickFuel Station',          'FUEL',        'QUICKFUEL STATION'),
    (gen_random_uuid(), 'partner-fashion',     'Thread & Weave Apparel',     'FASHION',     'THREAD WEAVE APPAREL'),
    (gen_random_uuid(), 'partner-travel',      'Horizon Travel Co',          'TRAVEL',      'HORIZON TRAVEL');
