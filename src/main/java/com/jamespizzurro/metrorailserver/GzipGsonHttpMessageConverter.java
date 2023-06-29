package com.jamespizzurro.metrorailserver;

import com.google.gson.Gson;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

// Based on GsonHttpMessageConverter
public class GzipGsonHttpMessageConverter extends AbstractJsonHttpMessageConverter {
    private Gson gson;

    public GzipGsonHttpMessageConverter() {
        this.gson = new Gson();

        List<MediaType> types = Arrays.asList(
                MediaType.TEXT_PLAIN,
                MediaType.APPLICATION_JSON,
                new MediaType("application", "*+json", DEFAULT_CHARSET)
        );
        super.setSupportedMediaTypes(types);
    }

    public void setGson(Gson gson) {
        Assert.notNull(gson, "A Gson instance is required");
        this.gson = gson;
    }

    public Gson getGson() {
        return this.gson;
    }

    protected Object readInternal(Type resolvedType, Reader reader) throws Exception {
        return this.getGson().fromJson(reader, resolvedType);
    }

    protected void writeInternal(Object o, @Nullable Type type, Writer writer) throws Exception {
        if (type instanceof ParameterizedType) {
            this.getGson().toJson(o, type, writer);
        } else {
            this.getGson().toJson(o, writer);
        }

    }

    @Override
    protected Reader getReader(HttpInputMessage inputMessage) throws IOException {
        GZIPInputStream json = new GZIPInputStream(inputMessage.getBody());
        return new InputStreamReader(json, getCharset(inputMessage.getHeaders()));
    }
}
