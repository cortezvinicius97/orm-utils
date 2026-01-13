package com.vcinsidedigital.orm_utils.annotations;


import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface JoinColumn {
    String name();
    String referencedColumnName() default "id";
    boolean nullable() default true;
}
