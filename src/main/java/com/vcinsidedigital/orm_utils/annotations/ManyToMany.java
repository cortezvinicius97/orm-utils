package com.vcinsidedigital.orm_utils.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ManyToMany {
    Class<?> targetEntity();
    String joinTable() default "";
    String joinColumn() default "";
    String inverseJoinColumn() default "";
    CascadeType[] cascade() default {};
    FetchType fetch() default FetchType.LAZY;
}
