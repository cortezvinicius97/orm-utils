package com.vcinsidedigital.orm_utils.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface OneToMany {
    Class<?> targetEntity();
    String mappedBy();
    CascadeType[] cascade() default {};
    FetchType fetch() default FetchType.LAZY;
}