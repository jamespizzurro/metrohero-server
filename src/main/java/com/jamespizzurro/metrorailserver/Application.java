package com.jamespizzurro.metrorailserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jamespizzurro.metrorailserver.web.PublicApiFilter;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.realm.NullRealm;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.Filter;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@EnableScheduling
@SpringBootApplication // same as @Configuration @EnableAutoConfiguration @ComponentScan
@EnableAutoConfiguration(exclude = { JacksonAutoConfiguration.class })
@EnableAsync
public class Application implements WebMvcConfigurer, AsyncConfigurer {

    private static final GsonHttpMessageConverter gsonHttpMessageConverter = createGsonHttpMessageConverter();

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    private static GsonHttpMessageConverter createGsonHttpMessageConverter() {
        Gson gson = new GsonBuilder()
                .setExclusionStrategies(new AnnotationExclusionStrategy())
                .registerTypeHierarchyAdapter(Calendar.class, new CalendarGsonConverter())
                .create();
        GsonHttpMessageConverter gsonConverter = new GsonHttpMessageConverter();
        gsonConverter.setGson(gson);
        return gsonConverter;
    }

    private final ConfigUtil configUtil;
    private final PublicApiFilter publicApiFilter;

    @Autowired
    public Application(ConfigUtil configUtil, PublicApiFilter publicApiFilter) {
        this.configUtil = configUtil;
        this.publicApiFilter = publicApiFilter;
    }

    @Bean
    public FilterRegistrationBean filterRegistration() {
        FilterRegistrationBean registration = new FilterRegistrationBean();
        registration.setFilter(this.publicApiFilter);
        registration.addUrlPatterns("/api/v1/*");
        registration.setName("publicApiFilter");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public ServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory() {
            @Override
            protected void postProcessContext(Context context) {
                SecurityConstraint securityConstraint = new SecurityConstraint();
                securityConstraint.setUserConstraint("CONFIDENTIAL");
                SecurityCollection collection = new SecurityCollection();
                collection.addPattern("/*");
                securityConstraint.addCollection(collection);
                context.addConstraint(securityConstraint);
            }
        };
        tomcat.addAdditionalTomcatConnectors(createHttpConnector());
        tomcat.addContextCustomizers(context -> {
            NullRealm realm = new NullRealm();
            realm.setTransportGuaranteeRedirectStatus(301);
            context.setRealm(realm);
        });
        return tomcat;
    }

    private Connector createHttpConnector() {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setScheme("http");
        connector.setSecure(false);
        connector.setPort(this.configUtil.isDevelopmentMode() ? 8080 : 80);
        connector.setRedirectPort(this.configUtil.isDevelopmentMode() ? 9443 : 443);
        return connector;
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("index.html");
        registry.addViewController("/dashboard").setViewName("index.html");
        registry.addViewController("/my-commute").setViewName("index.html");
        registry.addViewController("/system-map").setViewName("index.html");
        registry.addViewController("/faq").setViewName("index.html");
        registry.addViewController("/settings").setViewName("index.html");
        registry.addViewController("/line-*").setViewName("index.html");
        registry.addViewController("/realtime-map").setViewName("index.html");
        registry.addViewController("/realtime-audit").setViewName("index.html");
        registry.addViewController("/circuit-map").setViewName("index.html");
        registry.addViewController("/bocc").setViewName("bocc.html");
        registry.addViewController("/performance").setViewName("index.html");
        registry.addViewController("/departures").setViewName("index.html");
        registry.addViewController("/history").setViewName("index.html");
        registry.addViewController("/mareydiagram").setViewName("index.html");
        registry.addViewController("/apis").setViewName("/apis/index.html");

        registry.addViewController("/safetrack").setViewName("safetrack.html");

        registry.addRedirectViewController("/api", "/apis");
        registry.addRedirectViewController("/terminal-departures", "/departures");

        // deprecated apps and pages
        registry.addRedirectViewController("/bocc", "/dashboard");
        registry.addRedirectViewController("/metrobus", "/dashboard");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/index.html")
                .addResourceLocations("/index.html")
                .setCachePeriod(1);
        registry.addResourceHandler("/index.appcache.html")
                .addResourceLocations("/index.appcache.html")
                .setCachePeriod(1);
        registry.addResourceHandler("/safetrack.html")
                .addResourceLocations("/safetrack.html")
                .setCachePeriod(1);
        registry.addResourceHandler("/index.appcache")
                .addResourceLocations("/index.appcache")
                .setCachePeriod(1);
        registry.addResourceHandler("/sw.js")
                .addResourceLocations("/sw.js")
                .setCachePeriod(1);

        registry.addResourceHandler("/apis/**")
                .addResourceLocations("/apis/")
                .setCachePeriod(1);
        registry.addResourceHandler("/js/**")
                .addResourceLocations("/js/")
                .setCachePeriod((int) TimeUnit.DAYS.toSeconds(365));
        registry.addResourceHandler("/styles/**")
                .addResourceLocations("/styles/")
                .setCachePeriod((int) TimeUnit.DAYS.toSeconds(365));

        registry.addResourceHandler("/**")
                .addResourceLocations("/")
                .setCachePeriod((int) TimeUnit.DAYS.toSeconds(7));
    }

    @Bean
    public Filter shallowEtagHeaderFilter() {
        ShallowEtagHeaderFilter filter = new ShallowEtagHeaderFilter();

        // source: https://stackoverflow.com/a/62830480
        filter.setWriteWeakETag(true);

        return filter;
    }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.clear();
        converters.add(new StringHttpMessageConverter());
        converters.add(gsonHttpMessageConverter);
    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(8);
        return taskScheduler;
    }

    @Override  // AsyncConfigurer
    public Executor getAsyncExecutor() {
        // we only have one asynchronous operation to worry about: updateTrackCircuitLocationData in TrackCircuitService
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("MetroHeroAsync-");
        executor.initialize();
        return executor;
    }

    @Override  // AsyncConfigurer
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return null;
    }

    public static Gson getGson() {
        return gsonHttpMessageConverter.getGson();
    }
}
