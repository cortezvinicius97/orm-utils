package com.vcinsidedigital.orm_utils.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {
    String name() default "";
    ColumnType type() default ColumnType.AUTO;
    int length() default 255;
    boolean nullable() default true;
    boolean unique() default false;
    String columnDefinition() default "";
}
