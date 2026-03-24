package com.zynth.model;

public enum PostgresDataType {
    // Numeric
    SERIAL,
    BIGSERIAL,
    SMALLSERIAL,
    INTEGER,
    BIGINT,
    SMALLINT,
    DECIMAL,
    NUMERIC,
    REAL,
    DOUBLE_PRECISION,
    MONEY,

    // Character
    VARCHAR,
    CHAR,
    TEXT,

    // Boolean
    BOOLEAN,

    // Date/time
    DATE,
    TIME,
    TIMETZ,
    TIMESTAMP,
    TIMESTAMPTZ,

    // UUID / binary / network
    UUID,
    BYTEA,
    INET,
    CIDR,
    MACADDR,
    MACADDR8,

    // JSON / XML
    JSON,
    JSONB,
    XML,

    // Full text
    TSVECTOR,
    TSQUERY,

    // Geometric
    POINT,
    LINE,
    LSEG,
    BOX,
    PATH,
    POLYGON,
    CIRCLE,

    // Range
    INT4RANGE,
    INT8RANGE,
    NUMRANGE,
    TSRANGE,
    TSTZRANGE,
    DATERANGE,

    // Multirange
    INT4MULTIRANGE,
    INT8MULTIRANGE,
    NUMMULTIRANGE,
    TSMULTIRANGE,
    TSTZMULTIRANGE,
    DATEMULTIRANGE,

    // Misc
    ENUM,
    CITEXT,
    HSTORE,
    OID,
    REGCLASS,

    // Supabase/PostGIS common
    GEOMETRY,
    GEOGRAPHY,

    // Array marker for simple UI mode
    ARRAY
}
