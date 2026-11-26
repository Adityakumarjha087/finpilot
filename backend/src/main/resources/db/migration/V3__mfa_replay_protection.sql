-- Last accepted TOTP time-step per user: a code can be used once, and never an older one (RFC 6238 s5.2).
ALTER TABLE users ADD COLUMN mfa_last_counter BIGINT;
