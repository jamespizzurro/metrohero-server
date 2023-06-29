package com.jamespizzurro.metrorailserver;

import org.hibernate.dialect.PostgreSQL9Dialect;

import java.sql.Types;

public class CustomPostgresDialect extends PostgreSQL9Dialect {

    public CustomPostgresDialect() {
        super();

        // Register mappings

        registerHibernateType(Types.ARRAY, StringArrayType.class.getCanonicalName());
        registerColumnType(Types.ARRAY, "text[]");

        registerHibernateType(Types.JAVA_OBJECT, JsonType.class.getCanonicalName());
        registerColumnType(Types.JAVA_OBJECT, "jsonb");
    }
}