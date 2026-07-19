CREATE TABLE tenants (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT tenants_code_canonical CHECK (code = upper(btrim(code)) AND btrim(code) <> ''),
    CONSTRAINT tenants_code_grammar CHECK (code ~ '^[A-Z0-9][A-Z0-9._-]{0,63}$'),
    CONSTRAINT tenants_display_name_nonblank CHECK (
        btrim(
            display_name,
            U&'\0009\000A\000B\000C\000D\001C\001D\001E\001F\0020\0085\00A0\1680\2000\2001\2002\2003\2004\2005\2006\2007\2008\2009\200A\2028\2029\202F\205F\3000'
        ) <> ''
    ),
    CONSTRAINT tenants_version_nonnegative CHECK (version >= 0)
);

CREATE UNIQUE INDEX ux_tenants_code ON tenants (code);
