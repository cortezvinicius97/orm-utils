package com.example.entity;


import com.vcinsidedigital.orm_utils.annotations.*;

@Entity(table = "tags")
public class Tag {
    @Id
    private Long id;

    @Column(name = "name", length = 50, nullable = false, unique = true)
    private String name;

    public Tag() {}

    public Tag(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

