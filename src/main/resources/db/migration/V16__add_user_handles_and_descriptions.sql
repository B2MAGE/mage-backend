ALTER TABLE users
    ADD COLUMN handle VARCHAR(30),
    ADD COLUMN description VARCHAR(300);

-- Give every existing account a readable, stable handle. Handles are stored
-- without the leading @; the API adds it when presenting a public identity.
DO $$
DECLARE
    account RECORD;
    base_handle TEXT;
    candidate_handle TEXT;
    suffix_number INTEGER;
    suffix_text TEXT;
BEGIN
    FOR account IN SELECT id, display_name, email FROM users ORDER BY id LOOP
        base_handle := regexp_replace(lower(account.display_name), '[^a-z0-9_]+', '', 'g');

        IF base_handle !~ '^[a-z]' OR length(base_handle) < 3 THEN
            base_handle := regexp_replace(lower(split_part(account.email, '@', 1)), '[^a-z0-9_]+', '', 'g');
        END IF;

        IF base_handle !~ '^[a-z]' OR length(base_handle) < 3 THEN
            base_handle := 'user' || account.id;
        END IF;

        base_handle := left(base_handle, 30);
        candidate_handle := base_handle;
        suffix_number := 2;

        WHILE EXISTS (SELECT 1 FROM users WHERE handle = candidate_handle) LOOP
            suffix_text := '_' || suffix_number;
            candidate_handle := left(base_handle, 30 - length(suffix_text)) || suffix_text;
            suffix_number := suffix_number + 1;
        END LOOP;

        UPDATE users SET handle = candidate_handle WHERE id = account.id;
    END LOOP;
END $$;

ALTER TABLE users
    ALTER COLUMN handle SET NOT NULL,
    ADD CONSTRAINT users_handle_key UNIQUE (handle),
    ADD CONSTRAINT users_handle_format_check CHECK (handle ~ '^[a-z][a-z0-9_]{2,29}$');
