package com.vcinsidedigital.entity;


import com.vcinsidedigital.orm_utils.annotations.*;

import java.util.List;

@Entity(table = "posts")
public class Posts {
    @Id
    private Long id;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User author;

    @ManyToMany(targetEntity = Tag.class,
            joinTable = "post_tags",
            joinColumn = "post_id",
            inverseJoinColumn = "tag_id")
    private List<Tag> tags;

    // Constructors
    public Posts() {}

    public Posts(String title, String content, User author) {
        this.title = title;
        this.content = content;
        this.author = author;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public User getAuthor() { return author; }
    public void setAuthor(User author) { this.author = author; }

    public List<Tag> getTags() { return tags; }
    public void setTags(List<Tag> tags) { this.tags = tags; }

    @Override
    public String toString() {
        return String.format("Post{id=%d, title='%s', author=%s}",
                id, title, author != null ? author.getUsername() : "null");
    }
}
