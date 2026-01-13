# ORM Utils

[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/license-APACHE2.0-blue.svg)](LICENSE)

## 🚀 Usage

Gradle Groovy

```Groovy
implementation 'com.vcinsidedigital:orm-utils:1.0.0'
```

Gradle Kotlin
```kotlin
implementation('com.vcinsidedigital:orm-utils:1.0.0')
```

Maven
```xml
<dependencies>
    <dependency>
        <groupId>com.vcinsidedigital</groupId>
        <artifactId>orm-utils</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```





A lightweight and simple ORM (Object-Relational Mapping) library for Java, supporting MySQL and SQLite.

## 📋 Table of Contents

- [Features](#-features)
- [Requirements](#-requirements)
- [Installation](#-installation)
- [Quick Start](#-quick-start)
- [Configuration](#-configuration)
- [Annotations](#-annotations)
- [CRUD Operations](#-crud-operations)
- [Relationships](#-relationships)
- [Migrations](#-migrations)
- [Examples](#-examples)
- [Limitations](#-limitations)
- [Contributing](#-contributing)
- [License](#-license)

## ✨ Features

- 🚀 **Simple and Lightweight** - No heavy dependencies, easy to integrate
- 🗄️ **Multi-Database** - Support for MySQL and SQLite
- 🔄 **Auto Schema Generation** - Automatically generates tables from entities
- 📦 **Connection Pooling** - Built-in and configurable connection pool
- 🔗 **Relationships** - Support for `@ManyToOne`, `@OneToMany`, and `@ManyToMany`
- ⏱️ **Automatic Timestamps** - `@CreatedDate` and `@UpdatedDate`
- 🔧 **Migration System** - Schema versioning with migrations
- 📝 **Type Mapping** - Automatic mapping from Java types to SQL

## 📦 Requirements

- Java 17 or higher
- MySQL 8.0+ or SQLite 3.x
- Maven or Gradle (for dependency management)

## 🚀 Installation

### Maven Dependencies

```xml
<!-- MySQL Driver (if using MySQL) -->
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.33</version>
</dependency>

<!-- SQLite Driver (if using SQLite) -->
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.42.0.0</version>
</dependency>

<!-- SLF4J for logging -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.7</version>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>2.0.7</version>
</dependency>
```

### Gradle

```gradle
dependencies {
    implementation 'mysql:mysql-connector-java:8.0.33'
    // or
    implementation 'org.xerial:sqlite-jdbc:3.42.0.0'

    implementation 'org.slf4j:slf4j-api:2.0.7'
    implementation 'org.slf4j:slf4j-simple:2.0.7'
}
```

## 🎯 Quick Start

### 1. Define Your Entities

```java
import annotations.com.cortez.ormUtils.Column;
import annotations.com.cortez.ormUtils.CreatedDate;
import annotations.com.cortez.ormUtils.Entity;
import annotations.com.cortez.ormUtils.Id;

import java.time.LocalDateTime;

@Entity(table = "users")
public class User {
    @Id(autoIncrement = true)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @CreatedDate
    private LocalDateTime createdAt;

    // Getters and Setters
}
```

### 2. Configure the ORM

```java
import com.cortez.ormUtils.ORM;
import config.com.cortez.ormUtils.DatabaseConfig;

public class Main {
    public static void main(String[] args) throws Exception {
        // Configure database
        DatabaseConfig config = DatabaseConfig.builder()
                .mysql("localhost", 3306, "my_database")
                .credentials("username", "password")
                .build();

        // Initialize ORM
        ORM orm = new ORM(config)
                .registerEntity(User.class)
                .initialize();

        // Use EntityManager
        EntityManager em = orm.getEntityManager();
    }
}
```

### 3. Perform Operations

```java
// CREATE
User user = new User();
user.setName("John Smith");
user.setEmail("john@email.com");
em.persist(user);

// READ
User found = em.find(User.class, user.getId());
List<User> all = em.findAll(User.class);

// UPDATE
found.setName("John Doe");
em.update(found);

// DELETE
em.delete(found);

// Shutdown
orm.shutdown();
```

## ⚙️ Configuration

### MySQL

```java
DatabaseConfig config = DatabaseConfig.builder()
    .mysql("localhost", 3306, "database_name")
    .credentials("username", "password")
    .build();
```

### SQLite

```java
DatabaseConfig config = DatabaseConfig.builder()
    .sqlite("path/to/database.db")
    .build();
```

## 📌 Annotations

### @Entity

Defines a class as an entity mapped to the database.

```java
@Entity(table = "users")  // Custom name
public class User { }

@Entity  // Uses class name in snake_case
public class PostComment { }  // Table: post_comment
```

### @Id

Marks the field as primary key.

```java
@Id(autoIncrement = true)   // Auto-increment (default)
private Long id;

@Id(autoIncrement = false)  // Manual ID
private String uuid;
```

> **⚠️ Important:** For SQLite with auto-increment, use `Long` instead of `Integer`.

### @Column

Configures the database column.

```java
@Column(
    name = "full_name",                  // Column name
    type = ColumnType.VARCHAR,           // SQL type
    length = 100,                        // Size (VARCHAR)
    nullable = false,                    // NOT NULL
    unique = true,                       // UNIQUE
    columnDefinition = "VARCHAR(100)"    // Custom SQL
)
private String name;
```

#### Supported Types

| Java Type | MySQL | SQLite |
|-----------|-------|--------|
| `String` | VARCHAR(n) | TEXT |
| `Long`/`Integer` | BIGINT/INT | INTEGER |
| `Double`/`Float` | DOUBLE/FLOAT | REAL |
| `Boolean` | TINYINT(1) | INTEGER |
| `LocalDateTime` | DATETIME | TEXT |
| `LocalDate` | DATE | TEXT |
| `LocalTime` | TIME | TEXT |

### @ManyToOne

Many-to-one relationship.

```java
@ManyToOne(fetch = FetchType.EAGER)
@JoinColumn(name = "author_id", nullable = false)
private User author;
```

### @OneToMany

One-to-many relationship.

```java
@OneToMany(targetEntity = Post.class, mappedBy = "author")
private List<Post> posts;
```

### @ManyToMany

Many-to-many relationship.

```java
@ManyToMany(
    targetEntity = Tag.class,
    joinTable = "post_tags",
    joinColumn = "post_id",
    inverseJoinColumn = "tag_id"
)
private List<Tag> tags;
```

### @CreatedDate / @UpdatedDate

Automatic timestamps.

```java
@CreatedDate
private LocalDateTime createdAt;  // Filled on persist()

@UpdatedDate
private LocalDateTime updatedAt;  // Updated on update()
```

## 🔧 CRUD Operations

### Create

```java
User user = new User();
user.setName("Mary Santos");
user.setEmail("mary@email.com");
em.persist(user);

System.out.println("ID: " + user.getId());  // Auto-generated ID
```

### Read

```java
// Find by ID
User user = em.find(User.class, 1L);

// Find all
List<User> users = em.findAll(User.class);

// EAGER relationships are loaded automatically
Post post = em.find(Post.class, 1L);
System.out.println(post.getAuthor().getName());
```

### Update

```java
User user = em.find(User.class, 1L);
user.setName("New Name");
em.update(user);  // @UpdatedDate is automatically updated
```

### Delete

```java
User user = em.find(User.class, 1L);
em.delete(user);
```

## 🔗 Relationships

### Complete Example

```java
@Entity(table = "posts")
public class Post {
    @Id(autoIncrement = true)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(type = ColumnType.TEXT)
    private String content;

    // ManyToOne relationship
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    // ManyToMany relationship
    @ManyToMany(
        targetEntity = Tag.class,
        joinTable = "post_tags"
    )
    private List<Tag> tags;

    @CreatedDate
    private LocalDateTime createdAt;

    @UpdatedDate
    private LocalDateTime updatedAt;

    // Getters and Setters
}
```

### Usage

```java
// Create relationship
Post post = new Post();
post.setTitle("Title");
post.setContent("Content");
post.setAuthor(existingAuthor);
em.persist(post);

// Load with relationship
Post loadedPost = em.find(Post.class, 1L);
System.out.println(loadedPost.getAuthor().getName());  // Automatically loaded
```

## 🔄 Migrations

### Create Migration

```java
import migration.com.cortez.ormUtils.Migration;
import migration.com.cortez.ormUtils.MigrationContext;

public class AddEmailIndexMigration extends Migration {
    public AddEmailIndexMigration() {
        super("2024-01-10-001");  // Unique version
    }

    @Override
    public void up(MigrationContext context) throws Exception {
        context.execute(
                "CREATE INDEX idx_users_email ON users(email)"
        );
    }

    @Override
    public void down(MigrationContext context) throws Exception {
        context.execute("DROP INDEX idx_users_email");
    }
}
```

### Execute Migrations

```java
MigrationManager mm = orm.getMigrationManager();

// Register migrations
mm.addMigration(new AddEmailIndexMigration());
mm.addMigration(new CreatePostsTableMigration());

// Execute pending migrations
mm.migrate();

// Rollback last migration
mm.rollback(1);
```

### Version Control

Migrations are registered in the `schema_migrations` table and executed only once, in alphabetical order.

## 📚 Examples

### Blog System

```java
// Entities
@Entity(table = "users")
public class User {
    @Id(autoIncrement = true)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String email;

    @CreatedDate
    private LocalDateTime createdAt;
}

@Entity(table = "posts")
public class Post {
    @Id(autoIncrement = true)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(type = ColumnType.TEXT)
    private String content;

    @ManyToOne
    @JoinColumn(name = "author_id")
    private User author;

    @CreatedDate
    private LocalDateTime createdAt;

    @UpdatedDate
    private LocalDateTime updatedAt;
}

// Usage
public class BlogApp {
    public static void main(String[] args) throws Exception {
        DatabaseConfig config = DatabaseConfig.builder()
            .mysql("localhost", 3306, "blog")
            .credentials("root", "password")
            .build();

        ORM orm = new ORM(config)
            .registerEntity(User.class)
            .registerEntity(Post.class)
            .initialize();

        EntityManager em = orm.getEntityManager();

        // Create author
        User author = new User();
        author.setName("John Smith");
        author.setEmail("john@blog.com");
        em.persist(author);

        // Create post
        Post post = new Post();
        post.setTitle("My First Post");
        post.setContent("Interesting content...");
        post.setAuthor(author);
        em.persist(post);

        // List posts
        List<Post> posts = em.findAll(Post.class);
        for (Post p : posts) {
            System.out.println(p.getTitle() + " by " + p.getAuthor().getName());
        }

        orm.shutdown();
    }
}
```

### Simple E-commerce

```java
@Entity
public class Product {
    @Id(autoIncrement = true)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(type = ColumnType.DECIMAL)
    private Double price;

    @Column(type = ColumnType.INT)
    private Integer stock;
}

@Entity
public class Order {
    @Id(autoIncrement = true)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(type = ColumnType.DECIMAL)
    private Double totalAmount;

    @Column(length = 20)
    private String status;

    @CreatedDate
    private LocalDateTime createdAt;
}
```

### Desktop Application with SQLite

```java
public class DesktopApp {
    public static void main(String[] args) throws Exception {
        DatabaseConfig config = DatabaseConfig.builder()
            .sqlite("app-data.db")
            .build();

        ORM orm = new ORM(config)
            .registerEntity(User.class)
            .registerEntity(Configuration.class)
            .initialize();

        EntityManager em = orm.getEntityManager();

        // Use normally
        User user = new User();
        user.setName("Local User");
        em.persist(user);

        orm.shutdown();
    }
}
```

## ⚠️ Limitations

- **FetchType.LAZY**: Not fully implemented (always loads EAGER)
- **Cascade Operations**: Not automatically executed
- **Query Builder**: Not available (use direct SQL via Connection)
- **Transactions**: Must be managed manually
- **OneToMany/ManyToMany Collections**: Not automatically loaded

## 🤝 Contributing

Contributions are welcome! Please:

1. Fork the project
2. Create a branch for your feature (`git checkout -b feature/MyFeature`)
3. Commit your changes (`git commit -m 'Add MyFeature'`)
4. Push to the branch (`git push origin feature/MyFeature`)
5. Open a Pull Request

## 📄 License

This project is under the MIT license. See the [LICENSE](LICENSE) file for more details.

---

## 📞 Support

For questions and support:

- 🐛 **Issues**: [GitHub Issues](https://github.com/cortezvinicius97/orm-utils/issues)
- 📧 **Email**: cortezvinicius881@gmail.com
- 📖 **Documentation**: [Wiki](https://github.com/cortezvinicius97/orm-utils/wiki)

---

**Made with ❤️ by [Vinicius Cortez]**