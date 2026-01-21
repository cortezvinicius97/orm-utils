package com.example;

import com.example.entity.Posts;
import com.example.entity.Tag;
import com.example.entity.User;
import com.vcinsidedigital.orm_utils.ORM;
import com.vcinsidedigital.orm_utils.config.DatabaseConfig;
import com.vcinsidedigital.orm_utils.core.EntityManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        try {
            // Configure database (SQLite example)
            /*DatabaseConfig config = DatabaseConfig.builder()
                    .sqlite("myapp.db")
                    .build();*/

            // Configure database MySQL
            /*DatabaseConfig config = DatabaseConfig.builder()
                 .mysql("localhost", 3306, "teste")
                 .credentials("root", "123456")
                 .build();*/

            // Configure database postgres
            /*DatabaseConfig config = DatabaseConfig.builder()
                    .postgresql("localhost", 5432, "teste")
                    .credentials("postgres", "123456")
                    .build();*/

            // Configure database sqlserver
            DatabaseConfig config = DatabaseConfig.builder()
                    .sqlserver("localhost", 1433, "teste").
                    credentials("sa", "123456").build();


            // Initialize ORM
            ORM orm = new ORM(config)
                    .registerEntity(User.class)
                    .registerEntity(Posts.class)
                    .registerEntity(Tag.class)
                    .initialize();


            EntityManager em = orm.getEntityManager();

            // ==================== CRUD BÁSICO ====================

            // CREATE - Insert new users
            User user1 = new User("john_doe", "john@example.com", 25, "John Doe");
            em.persist(user1);
            System.out.println("✓ Created: " + user1);

            User user2 = new User("maria_silva", "maria@gmail.com", 30, "Maria Silva");
            em.persist(user2);
            System.out.println("✓ Created: " + user2);

            User user3 = new User("joao_silva", "joao@gmail.com", 28, "João Silva");
            em.persist(user3);
            System.out.println("✓ Created: " + user3);

            // CREATE - Insert post with relationship
            Posts post = new Posts("My First Post", "This is the content", user1);
            em.persist(post);
            System.out.println("✓ Created: " + post);

            System.out.println("\n========================================");
            System.out.println("EXEMPLOS DE BUSCAS COM findBy");
            System.out.println("========================================\n");

            // ==================== EXEMPLOS findBy ====================

            // 1. findBy - Buscar por nome de usuário
            System.out.println("--- 1. Buscar usuário por username ---");
            List<User> johnUsers = em.findBy(User.class, "username", "john_doe");
            System.out.println("Usuários encontrados: " + johnUsers.size());
            johnUsers.forEach(u -> System.out.println("  → " + u.getUsername() + " - " + u.getEmail()));

            // 2. findBy - Buscar por idade
            System.out.println("\n--- 2. Buscar usuários com 30 anos ---");
            List<User> users30 = em.findBy(User.class, "age", 30);
            System.out.println("Usuários com 30 anos: " + users30.size());
            users30.forEach(u -> System.out.println("  → " + u.getUsername() + " - Idade: " + u.getAge()));

            // 3. findOneBy - Buscar um único usuário por email
            System.out.println("\n--- 3. Buscar usuário por email (único) ---");
            Map<String, Object> emailFilter = Map.of("email", "maria@gmail.com");
            User userByEmail = em.findOneBy(User.class, emailFilter);
            if (userByEmail != null) {
                System.out.println("✓ Encontrado: " + userByEmail.getUsername() + " - " + userByEmail.getEmail());
            } else {
                System.out.println("✗ Email não encontrado");
            }

            // 4. findByLike - Buscar usuários com "silva" no username
            System.out.println("\n--- 4. Buscar usuários com 'silva' no username ---");
            List<User> silvaUsers = em.findByLike(User.class, "username", "%silva%");
            System.out.println("Usuários com 'silva': " + silvaUsers.size());
            silvaUsers.forEach(u -> System.out.println("  → " + u.getUsername()));

            // 5. findByLike - Buscar emails do Gmail
            System.out.println("\n--- 5. Buscar emails do Gmail ---");
            List<User> gmailUsers = em.findByLike(User.class, "email", "%@gmail.com");
            System.out.println("Usuários Gmail: " + gmailUsers.size());
            gmailUsers.forEach(u -> System.out.println("  → " + u.getUsername() + " - " + u.getEmail()));

            // 6. findOneBy - Buscar com múltiplos filtros (NOVO!)
            System.out.println("\n--- 6. Buscar usuário por username E email (múltiplos filtros) ---");
            Map<String, Object> multipleFilters = new HashMap<>();
            multipleFilters.put("username", "joao_silva");
            multipleFilters.put("email", "joao@gmail.com");

            User userMultiple = em.findOneBy(User.class, multipleFilters);
            if (userMultiple != null) {
                System.out.println("✓ Encontrado: " + userMultiple.getUsername() +
                        " - " + userMultiple.getEmail() +
                        " - " + userMultiple.getAge() + " anos");
            } else {
                System.out.println("✗ Usuário não encontrado");
            }

            // 7. findOneBy - Buscar por idade e username
            System.out.println("\n--- 7. Buscar usuário com 28 anos e username específico ---");
            Map<String, Object> ageAndUsername = new HashMap<>();
            ageAndUsername.put("age", 28);
            ageAndUsername.put("username", "joao_silva");

            User userByAgeAndName = em.findOneBy(User.class, ageAndUsername);
            if (userByAgeAndName != null) {
                System.out.println("✓ Encontrado: " + userByAgeAndName.getUsername() +
                        " com " + userByAgeAndName.getAge() + " anos");
            }

            // 8. findOneBy - Verificar se email já existe (validação)
            System.out.println("\n--- 8. Verificar se email já existe ---");
            String emailToCheck = "john@example.com";
            Map<String, Object> checkEmailFilter = Map.of("email", emailToCheck);
            User existingUser = em.findOneBy(User.class, checkEmailFilter);
            if (existingUser != null) {
                System.out.println("✗ Email '" + emailToCheck + "' já está cadastrado!");
            } else {
                System.out.println("✓ Email disponível");
            }

            // ==================== CRUD TRADICIONAL ====================

            System.out.println("\n========================================");
            System.out.println("CRUD TRADICIONAL");
            System.out.println("========================================\n");

            // READ - Find by ID
            User foundUser = em.find(User.class, user1.getId());
            System.out.println("Found by ID: " + foundUser);

            // READ - Find all
            var allUsers = em.findAll(User.class);
            System.out.println("All users count: " + allUsers.size());

            // UPDATE
            foundUser.setAge(26);
            em.update(foundUser);
            System.out.println("Updated: " + foundUser);

            // Verify update
            User updatedUser = em.find(User.class, user1.getId());
            System.out.println("Verified update: " + updatedUser);

            // DELETE (comentado para manter os dados)
            // em.delete(post);
            // em.delete(user1);

            // Shutdown
            orm.shutdown();
            System.out.println("\n✓ ORM shutdown complete");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}