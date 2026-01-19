# ORM Utils

[![Java](https://img.shields.io/badge/Java-21%2B-orange)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/license-APACHE2.0-blue.svg)](LICENSE)

## 🚀 Usage

Gradle Groovy

```Groovy
implementation 'com.vcinsidedigital:orm-utils:1.0.1'
```

Gradle Kotlin
```kotlin
implementation('com.vcinsidedigital:orm-utils:1.0.1')
```

Maven
```xml
<dependencies>
    <dependency>
        <groupId>com.vcinsidedigital</groupId>
        <artifactId>orm-utils</artifactId>
        <version>1.0.1</version>
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
- [Advanced Queries](#-advanced-queries)
- [Schema Management](#-schema-management)
    - [Auto Schema Generation](#-auto-schema-generation)
    - [Auto Schema Update](#-auto-schema-update)
    - [Migration System](#-migration-system)
- [Relationships](#-relationships)
- [Examples](#-examples)
- [Limitations](#-limitations)
- [Contributing](#-contributing)
- [License](#-license)

## ✨ Features

- 🚀 **Simple and Lightweight** - No heavy dependencies, easy to integrate
- 🗄️ **Multi-Database** - Support for MySQL and SQLite
- 🔄 **Auto Schema Generation** - Automatically generates tables from entities
- 🔧 **Auto Schema Update** - Detects and applies schema changes automatically (with column removal support)
- 📦 **Connection Pooling** - Built-in and configurable connection pool
- 🔗 **Relationships** - Support for `@ManyToOne`, `@OneToMany`, and `@ManyToMany`
- ⏱️ **Automatic Timestamps** - `@CreatedDate` and `@UpdatedDate`
- 🔧 **Migration System** - Schema versioning with auto-generated migrations and dependency ordering
- 🔍 **Advanced Queries** - `findBy`, `findOneBy`, `findByLike` methods
- 📝 **Type Mapping** - Automatic mapping from Java types to SQL
- ⚡ **Dependency Resolution** - Automatic table creation ordering based on foreign key dependencies

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
    <version>1.7.36</version>
</dependency>
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>1.7.36</version>
</dependency>
```

### Gradle

```gradle
dependencies {
    implementation 'mysql:mysql-connector-java:8.0.33'
    // or
    implementation 'org.xerial:sqlite-jdbc:3.42.0.0'

    implementation 'org.slf4j:slf4j-api:1.7.36'
    implementation 'org.slf4j:slf4j-simple:1.7.36'
}
```

## 🎯 Quick Start

### 1. Define Your Entities

```java
import com.vcinsidedigital.orm_utils.annotations.*;
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
import com.vcinsidedigital.orm_utils.ORM;
import com.vcinsidedigital.orm_utils.config.DatabaseConfig;

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

## 🔍 Advanced Queries

### findBy - Single Field Filter

Search entities by a specific field value.

```java
// Find users by username
List<User> users = em.findBy(User.class, "username", "john_doe");

// Find users by age
List<User> users30 = em.findBy(User.class, "age", 30);

// Find posts by title
List<Post> posts = em.findBy(Post.class, "title", "Introduction");
```

### findBy - Multiple Fields Filter (AND)

Search with multiple conditions combined with AND.

```java
// Find user by username AND email
Map<String, Object> filters = new HashMap<>();
filters.put("username", "john_doe");
filters.put("email", "john@email.com");

List<User> users = em.findBy(User.class, filters);

// Find user with age 28 AND specific username
Map<String, Object> filters = Map.of(
    "age", 28,
    "username", "joao_silva"
);
User user = em.findBy(User.class, filters);
```

### findOneBy - Get First Result

Returns the first entity that matches the criteria, or null if none found.

```java
// Find one user by email
User user = em.findOneBy(User.class, "email", "john@email.com");

// Find one user with multiple filters
Map<String, Object> filters = Map.of(
    "username", "john_doe",
    "age", 30
);
User user = em.findOneBy(User.class, filters);

// Check if email exists (validation)
User existing = em.findOneBy(User.class, "email", emailToCheck);
if (existing != null) {
    System.out.println("Email already in use!");
}
```

### findByLike - Pattern Matching

Search using SQL LIKE for text pattern matching.

```java
// Find users with "silva" in username
List<User> users = em.findByLike(User.class, "username", "%silva%");

// Find Gmail users
List<User> gmailUsers = em.findByLike(User.class, "email", "%@gmail.com");

// Find users starting with "john"
List<User> users = em.findByLike(User.class, "username", "john%");

// Find users ending with "silva"
List<User> users = em.findByLike(User.class, "username", "%silva");
```

### Complete Query Examples

```java
// Example 1: User search system
public List<User> searchUsers(String searchTerm) {
    // Search by username containing the term
    List<User> byUsername = em.findByLike(User.class, "username", "%" + searchTerm + "%");
    
    // Or search by email
    List<User> byEmail = em.findByLike(User.class, "email", "%" + searchTerm + "%");
    
    return byUsername;
}

// Example 2: Email validation
public boolean isEmailAvailable(String email) {
    User existing = em.findOneBy(User.class, "email", email);
    return existing == null;
}

// Example 3: Filter users by multiple criteria
public List<User> findActiveAdultUsers() {
    Map<String, Object> filters = new HashMap<>();
    filters.put("status", "active");
    filters.put("age", 18);
    
    return em.findBy(User.class, filters);
}

// Example 4: Search posts by author
public List<Post> getPostsByAuthor(String authorName) {
    // First find the author
    User author = em.findOneBy(User.class, "username", authorName);
    
    if (author != null) {
        // Then find posts by author_id
        return em.findBy(Post.class, "author_id", author.getId());
    }
    
    return Collections.emptyList();
}
```

### Query Pattern Reference

| Method | Use Case | Example |
|--------|----------|---------|
| `find(Class, id)` | Get by primary key | `em.find(User.class, 1L)` |
| `findAll(Class)` | Get all records | `em.findAll(User.class)` |
| `findBy(Class, field, value)` | Filter by one field | `em.findBy(User.class, "age", 30)` |
| `findBy(Class, Map)` | Filter by multiple fields (AND) | `em.findBy(User.class, filters)` |
| `findOneBy(Class, field, value)` | Get first match (single field) | `em.findOneBy(User.class, "email", "test@email.com")` |
| `findOneBy(Class, Map)` | Get first match (multiple fields) | `em.findOneBy(User.class, filters)` |
| `findByLike(Class, field, pattern)` | Pattern matching with LIKE | `em.findByLike(User.class, "name", "%john%")` |

## 🗄️ Schema Management

### 🔄 Auto Schema Generation

The ORM automatically creates tables from your entity classes when they don't exist.

```java
@Entity(table = "users")
public class User {
    @Id(autoIncrement = true)
    private Long id;
    
    @Column(length = 50, nullable = false, unique = true)
    private String username;
    
    @Column(length = 100, nullable = false)
    private String email;
}

// First run - creates the table
ORM orm = new ORM(config)
        .registerEntity(User.class)
        .initialize();
```

**Output:**
```
INFO - Table 'users' does not exist, creating...
INFO - ✓ Table 'users' created successfully
```

**Smart Dependency Resolution:**
The ORM automatically detects foreign key dependencies and creates tables in the correct order.

```java
// Even if you register in any order:
ORM orm = new ORM(config)
        .registerEntity(Post.class)    // Has FK to User
        .registerEntity(User.class)    // No dependencies
        .registerEntity(Tag.class)     // No dependencies
        .initialize();

// Tables are created in dependency order:
// 1. users (no dependencies)
// 2. tags (no dependencies)
// 3. posts (depends on users)
// 4. post_tags (join table)
```

**Output:**
```
INFO - Entities ordered by dependencies: [User, Tag, Post]
INFO - ✓ Table 'users' created successfully
INFO - ✓ Table 'tags' created successfully
INFO - ✓ Table 'posts' created successfully
INFO - Creating join table: post_tags
```

### 🔧 Auto Schema Update

The ORM detects and applies schema changes automatically when you modify your entities.

#### Adding New Columns

```java
// Add a new field to your entity
@Entity(table = "users")
public class User {
    @Id(autoIncrement = true)
    private Long id;
    
    @Column(length = 50)
    private String username;
    
    @Column(length = 100)
    private String email;
    
    @Column(length = 20)  // ← NEW FIELD
    private String phone; // ← NEW FIELD
}

// Run again - column is added automatically!
orm.initialize();
```

**Output:**
```
INFO - Table 'users' exists, checking for updates...
INFO - Detected 1 schema changes for table 'users'
INFO - Executing: ALTER TABLE users ADD COLUMN phone VARCHAR(20)
INFO - ✓ Schema updated successfully
```

#### Modifying Columns (MySQL)

```java
// Before
@Column(length = 50)
private String username;

// After - change length and constraints
@Column(length = 100, nullable = false, unique = true)
private String username;

orm.initialize();
```

**Output (MySQL):**
```
INFO - Detected 1 schema changes for table 'users'
INFO - Executing: ALTER TABLE users MODIFY COLUMN username VARCHAR(100) NOT NULL UNIQUE
INFO - ✓ Schema updated successfully
```

**Output (SQLite):**
```
⚠ Column 'username' needs modification but SQLite doesn't support ALTER COLUMN
   Current: VARCHAR(50)
   Expected: VARCHAR(100) NOT NULL UNIQUE
   You'll need to create a migration to rebuild the table
```

#### Removing Columns (New Feature!)

When you remove a field from your entity, the ORM **automatically removes** the column from the database (MySQL).

```java
@Entity(table = "users")
public class User {
    @Id(autoIncrement = true)
    private Long id;
    
    @Column(length = 100, nullable = false, unique = true)
    private String username;
    
    @Column(length = 150)
    private String email;
    
    // phone field removed - column will be dropped!
}

orm.initialize();
```

**Output (MySQL):**
```
INFO - Table 'users' exists, checking for updates...
⚠ Column 'phone' removed from entity, will DROP from database
INFO - Executing: ALTER TABLE users DROP COLUMN phone
INFO - ✓ Schema updated successfully
```

**Output (SQLite):**
```
⚠ Column 'phone' removed from entity but SQLite doesn't support DROP COLUMN easily
   You'll need to manually rebuild the table without this column
```

#### Controlling Auto-Drop Behavior

You can control whether columns should be automatically removed:

```java
// Default: Auto-drop enabled
ORM orm = new ORM(config)
        .registerEntity(User.class)
        .initialize();  // Will drop removed columns

// Disable auto-drop (safe mode)
SchemaGenerator schema = new SchemaGenerator();
schema.setAutoDropColumns(false)  // Only warn, don't drop
      .addEntity(User.class);
schema.generateSchema();
```

**Output (when disabled):**
```
⚠ Column 'phone' exists in database but not in entity class
   This column will NOT be removed automatically (autoDropColumns=false)
   To remove it, either set autoDropColumns(true) or run manually:
   ALTER TABLE users DROP COLUMN phone
```

### Auto-Update Features Summary

| Feature | MySQL | SQLite | Notes |
|---------|-------|--------|-------|
| Create tables | ✅ Yes | ✅ Yes | Automatic with dependency ordering |
| Add new columns | ✅ Yes | ✅ Yes | Automatic |
| Modify column type | ✅ Yes | ❌ No | Requires manual migration for SQLite |
| Modify nullable | ✅ Yes | ❌ No | Requires manual migration for SQLite |
| Modify unique | ✅ Yes | ❌ No | Requires manual migration for SQLite |
| Modify length | ✅ Yes | ❌ No | Requires manual migration for SQLite |
| Remove columns | ✅ Yes | ⚠️ Warns | Automatic for MySQL, manual for SQLite |
| Rename columns | ❌ No | ❌ No | Use migrations |
| Circular dependencies | ⚠️ Warns | ⚠️ Warns | Detected and logged |

### 🔄 Migration System

For production environments and complex schema changes, use the migration system.

#### Auto-Generate Migrations

The ORM can automatically generate migration files based on your entities:

```java
ORM orm = new ORM(config)
        .registerEntity(User.class)
        .registerEntity(Post.class)
        .registerEntity(Tag.class)
        .createMigrations();  // Generates migration files
```

This creates a file like `migrations/Version20260119123456.java`:

```java
package migrations;

import com.vcinsidedigital.orm_utils.migration.Migration;
import com.vcinsidedigital.orm_utils.migration.MigrationContext;

/**
 * Auto-generated migration
 * Generated at: 2026-01-19T12:34:56
 */
public class Version20260119123456 extends Migration {
    
    public Version20260119123456() {
        super("20260119123456");
    }
    
    @Override
    public void up(MigrationContext context) throws Exception {
        // Creates tables in dependency order
        context.execute(
            "CREATE TABLE IF NOT EXISTS users (\n" +
            "    id BIGINT PRIMARY KEY AUTO_INCREMENT,\n" +
            "    username VARCHAR(50) NOT NULL UNIQUE,\n" +
            "    email VARCHAR(100) NOT NULL\n" +
            ")"
        );
        
        context.execute(
            "CREATE TABLE IF NOT EXISTS posts (\n" +
            "    id BIGINT PRIMARY KEY AUTO_INCREMENT,\n" +
            "    title VARCHAR(200) NOT NULL,\n" +
            "    content TEXT,\n" +
            "    user_id BIGINT,\n" +
            "    CONSTRAINT fk_posts_user_id FOREIGN KEY (user_id) REFERENCES users(id)\n" +
            ")"
        );
        
        // Drop removed columns
        context.execute("ALTER TABLE users DROP COLUMN phone");
    }
    
    @Override
    public void down(MigrationContext context) throws Exception {
        // Reverse operations
        context.execute("ALTER TABLE users ADD COLUMN phone VARCHAR(20)");
        context.execute("DROP TABLE IF EXISTS posts");
        context.execute("DROP TABLE IF EXISTS users");
    }
}
```

**Features of Auto-Generated Migrations:**
- ✅ Tables created in dependency order (no FK errors)
- ✅ Handles new tables
- ✅ Handles new columns
- ✅ Handles modified columns (MySQL)
- ✅ Handles removed columns (MySQL)
- ✅ Generates both UP and DOWN operations
- ✅ Detects circular dependencies

#### Manual Migrations

For custom operations:

```java
import com.vcinsidedigital.orm_utils.migration.Migration;
import com.vcinsidedigital.orm_utils.migration.MigrationContext;

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

#### Execute Migrations

```java
MigrationManager mm = orm.getMigrationManager();

// Register migrations (optional - if not using auto-generated)
mm.addMigration(new AddEmailIndexMigration());

// Execute pending migrations
mm.migrate();

// Rollback last N migrations
mm.rollback(1);
```

**Migration tracking:**
Migrations are registered in the `schema_migrations` table and executed only once, in version order.

### When to Use Auto-Update vs Migrations

**Use Auto-Update for:**
- 🔧 Development and prototyping
- ➕ Adding new optional columns
- 📝 Quick schema iterations
- 🧪 Testing different schema designs

**Use Migrations for:**
- 🏭 Production environments
- 🔄 Complex schema changes
- 📊 Data transformations
- 🔐 Sensitive operations
- 👥 Team collaboration (version control)
- 🔁 Rollback capability

### Best Practices

1. **Development**: Use auto-update for rapid iteration
   ```java
   ORM orm = new ORM(config)
           .registerEntity(User.class)
           .initialize(); // Auto-updates schema
   ```

2. **Production**: Generate and review migrations before applying
   ```java
   // 1. Generate migrations in development
   orm.createMigrations();
   
   // 2. Review generated migration files
   // 3. Commit migrations to version control
   // 4. In production, run migrations
   orm.getMigrationManager().migrate();
   ```

3. **Column Removal**:
    - Development: Enable auto-drop for convenience
    - Production: Always use migrations with data backup

4. **Always backup** before schema changes in production
5. **Test migrations** in a staging environment first
6. **Review logs** to see what changes are being applied

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

## 📚 Examples

### Blog System with Auto-Schema Management

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

        // Tables are created in correct order automatically
        ORM orm = new ORM(config)
            .registerEntity(Post.class)   // Registered first but created second
            .registerEntity(User.class)   // Registered second but created first (no deps)
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

        // Advanced queries
        User user = em.findOneBy(User.class, "email", "john@blog.com");
        List<Post> posts = em.findByLike(Post.class, "title", "%First%");

        for (Post p : posts) {
            System.out.println(p.getTitle() + " by " + p.getAuthor().getName());
        }

        orm.shutdown();
    }
}
```

### Evolving Schema Example

```java
// Version 1: Initial schema
@Entity(table = "products")
public class Product {
    @Id(autoIncrement = true)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(type = ColumnType.DECIMAL)
    private Double price;
}

orm.initialize(); // Creates table

// Version 2: Add stock tracking
@Entity(table = "products")
public class Product {
    @Id(autoIncrement = true)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(type = ColumnType.DECIMAL)
    private Double price;
    
    @Column(type = ColumnType.INT)  // NEW
    private Integer stock;          // NEW
}

orm.initialize(); // Adds 'stock' column automatically

// Version 3: Remove price, add cost and markup
@Entity(table = "products")
public class Product {
    @Id(autoIncrement = true)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    // price removed - will be dropped
    
    @Column(type = ColumnType.DECIMAL)  // NEW
    private Double cost;                // NEW
    
    @Column(type = ColumnType.DECIMAL)  // NEW
    private Double markup;              // NEW
    
    @Column(type = ColumnType.INT)
    private Integer stock;
}

orm.initialize(); 
// Adds 'cost' and 'markup' columns
// Drops 'price' column (MySQL)
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
        
        // Advanced queries work the same
        User foundUser = em.findOneBy(User.class, "name", "Local User");

        orm.shutdown();
    }
}
```

## ⚠️ Limitations

- **FetchType.LAZY**: Not fully implemented (always loads EAGER)
- **Cascade Operations**: Not automatically executed
- **Query Builder**: Limited to provided query methods
- **Transactions**: Must be managed manually
- **OneToMany/ManyToMany Collections**: Not automatically loaded
- **Schema Modifications (SQLite)**: Limited ALTER TABLE support (no MODIFY COLUMN, limited DROP COLUMN)
- **JOIN Queries**: Not supported (use separate queries)
- **Column Renaming**: Not detected automatically (use migrations)

## 🆕 What's New in Recent Updates

### Schema Management Improvements
- ✅ **Automatic Dependency Resolution**: Tables are created in the correct order based on foreign key dependencies
- ✅ **Automatic Column Removal**: Removed fields are automatically dropped from the database (MySQL)
- ✅ **Enhanced Migration Generation**: Auto-generated migrations now include all schema changes
- ✅ **Smart Ordering**: Migration system uses topological sorting to prevent foreign key errors
- ✅ **Configurable Auto-Drop**: Control whether columns should be automatically removed

### Migration System Enhancements
- ✅ **Auto-Generated Migrations**: `orm.createMigrations()` generates migration files automatically
- ✅ **Dependency-Aware**: Migrations create tables in the correct order
- ✅ **Complete UP/DOWN**: Both migration directions are generated automatically
- ✅ **Column Removal Support**: Tracks and reverses column drops
- ✅ **Circular Dependency Detection**: Warns about circular foreign key relationships

## 🤝 Contributing

Contributions are welcome! Please:

1. Fork the project
2. Create a branch for your feature (`git checkout -b feature/MyFeature`)
3. Commit your changes (`git commit -m 'Add MyFeature'`)
4. Push to the branch (`git push origin feature/MyFeature`)
5. Open a Pull Request

### Areas for Contribution
- Lazy loading implementation
- Transaction management
- Query builder enhancements
- Additional database support (PostgreSQL, Oracle)
- Performance optimizations
- Better circular dependency handling

## 📄 License

This project is under the Apache 2.0 license. See the [LICENSE](LICENSE) file for more details.

---

## 📞 Support

For questions and support:

- 🐛 **Issues**: [GitHub Issues](https://github.com/cortezvinicius97/orm-utils/issues)
- 📧 **Email**: cortezvinicius881@gmail.com
- 📖 **Documentation**: [Wiki](https://github.com/cortezvinicius97/orm-utils/wiki)

---

## 🙏 Acknowledgments

Special thanks to all contributors and users who have helped improve this library.

---

**Made with ❤️ by [Vinicius Cortez]**