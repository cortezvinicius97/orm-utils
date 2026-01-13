package com.vcinsidedigital.entity;

import com.vcinsidedigital.orm_utils.annotations.*;

import java.util.List;

@Entity(table = "users")
public class User {
    @Id
    private Long id;

    @Column(name = "username", length = 100, nullable = false, unique = true)
    private String username;

    @Column(name = "email", length = 255, nullable = false)
    private String email;

    @Column(nullable = true)
    private Integer age;

    @OneToMany(targetEntity = Posts.class, mappedBy = "author",
            cascade = {CascadeType.ALL})
    private List<Posts> posts;

    // Constructors
    public User() {}

    public User(String username, String email, Integer age) {
        this.username = username;
        this.email = email;
        this.age = age;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

    public List<Posts> getPosts() { return posts; }
    public void setPosts(List<Posts> posts) { this.posts = posts; }

    @Override
    public String toString() {
        return String.format("User{id=%d, username='%s', email='%s', age=%d}",
                id, username, email, age);
    }
}

