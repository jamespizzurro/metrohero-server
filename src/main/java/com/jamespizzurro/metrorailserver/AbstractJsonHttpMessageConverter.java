package com.jamespizzurro.metrorailserver;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

import org.springframework.core.GenericTypeResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractGenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.Nullable;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public abstract class AbstractJsonHttpMessageConverter extends AbstractGenericHttpMessageConverter<Object> {
    public static final Charset DEFAULT_CHARSET;
    @Nullable
    private String jsonPrefix;

    public AbstractJsonHttpMessageConverter() {
        super(new MediaType[]{MediaType.APPLICATION_JSON, new MediaType("application", "*+json")});
        this.setDefaultCharset(DEFAULT_CHARSET);
    }

    public void setJsonPrefix(String jsonPrefix) {
        this.jsonPrefix = jsonPrefix;
    }

    public void setPrefixJson(boolean prefixJson) {
        this.jsonPrefix = prefixJson ? ")]}', " : null;
    }

    public final Object read(Type type, @Nullable Class<?> contextClass, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return this.readResolved(GenericTypeResolver.resolveType(type, contextClass), inputMessage);
    }

    protected final Object readInternal(Class<?> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        return this.readResolved(clazz, inputMessage);
    }

    private Object readResolved(Type resolvedType, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        Reader reader = getReader(inputMessage);

        try {
            return this.readInternal(resolvedType, reader);
        } catch (Exception var5) {
            throw new HttpMessageNotReadableException("Could not read JSON: " + var5.getMessage(), var5, inputMessage);
        }
    }

    protected final void writeInternal(Object o, @Nullable Type type, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        Writer writer = getWriter(outputMessage);
        if (this.jsonPrefix != null) {
            writer.append(this.jsonPrefix);
        }

        try {
            this.writeInternal(o, type, writer);
        } catch (Exception var6) {
            throw new HttpMessageNotWritableException("Could not write JSON: " + var6.getMessage(), var6);
        }

        writer.close();
    }

    protected abstract Object readInternal(Type var1, Reader var2) throws Exception;

    protected abstract void writeInternal(Object var1, @Nullable Type var2, Writer var3) throws Exception;

    protected Reader getReader(HttpInputMessage inputMessage) throws IOException {
        return new InputStreamReader(inputMessage.getBody(), getCharset(inputMessage.getHeaders()));
    }

    private static Writer getWriter(HttpOutputMessage outputMessage) throws IOException {
        return new OutputStreamWriter(outputMessage.getBody(), getCharset(outputMessage.getHeaders()));
    }

    protected static Charset getCharset(HttpHeaders headers) {
        Charset charset = headers.getContentType() != null ? headers.getContentType().getCharset() : null;
        return charset != null ? charset : DEFAULT_CHARSET;
    }

    static {
        DEFAULT_CHARSET = StandardCharsets.UTF_8;
    }
}
