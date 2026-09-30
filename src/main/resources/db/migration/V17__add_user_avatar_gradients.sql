ALTER TABLE users
    ADD COLUMN avatar_gradient_start VARCHAR(7) NOT NULL DEFAULT '#5c51ba',
    ADD COLUMN avatar_gradient_end VARCHAR(7) NOT NULL DEFAULT '#264a48',
    ADD CONSTRAINT users_avatar_gradient_start_check CHECK (avatar_gradient_start ~ '^#[a-f0-9]{6}$'),
    ADD CONSTRAINT users_avatar_gradient_end_check CHECK (avatar_gradient_end ~ '^#[a-f0-9]{6}$');
