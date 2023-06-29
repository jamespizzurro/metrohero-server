package com.jamespizzurro.metrorailserver;

import org.springframework.jdbc.support.JdbcUtils;

import java.beans.PropertyDescriptor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;

public class BeanPropertyRowMapper<T> extends org.springframework.jdbc.core.BeanPropertyRowMapper {

    public BeanPropertyRowMapper(Class<T> mappedClass) {
        super(mappedClass);
    }

    @Override
    protected Object getColumnValue(ResultSet rs, int index, PropertyDescriptor pd) throws SQLException {
        if (Calendar.class == pd.getPropertyType() && rs.getTimestamp(index) == null) {
            // there seems to be a nasty bug in PgResultSet that makes it throw a NPE on null Calendar object instances,
            // so we handle that manually here
            return null;
        }

        return JdbcUtils.getResultSetValue(rs, index, pd.getPropertyType());
    }
}
