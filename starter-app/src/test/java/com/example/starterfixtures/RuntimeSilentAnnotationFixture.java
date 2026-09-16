package com.example.starterfixtures;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.transaction.annotation.Transactional;

/** Meta-annotated: the wrapper hides @Transactional one level down, which is exactly what the rule must see. */
@RuntimeSilentAnnotationFixture.WrappedTx
class RuntimeSilentAnnotationFixture {

    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Transactional
    @interface WrappedTx {}
}
