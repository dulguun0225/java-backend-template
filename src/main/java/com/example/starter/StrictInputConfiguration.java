package com.example.starter;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link DeclaredInputCheck}, which holds every path variable, query parameter and header to what its
 * handler declares before any argument is read: an undeclared or repeated query parameter, a repeated single-valued
 * header, and the text of a UUID, {@code int32}, {@code int64} or enumeration input that is not its exact form are
 * each refused at the input's {@code in} and {@code name}. Every other type keeps Spring's conversion: a
 * {@code Boolean} parameter takes {@code yes} and {@code on}, and a date its formatter's pattern;
 * {@code docs/GATES.md} records that.
 */
@Configuration(proxyBeanMethods = false)
class StrictInputConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new DeclaredInputCheck());
    }
}
