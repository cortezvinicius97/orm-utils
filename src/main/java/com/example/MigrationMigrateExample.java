package com.example;

import com.example.entity.Posts;
import com.example.entity.Tag;
import com.example.entity.User;
import com.vcinsidedigital.orm_utils.ORM;
import com.vcinsidedigital.orm_utils.config.DatabaseConfig;
import com.vcinsidedigital.orm_utils.core.EntityManager;
import com.vcinsidedigital.orm_utils.migration.MigrationManager;
import migrations.Version20260120171913;
import migrations.Version20260120172730;
import migrations.Version20260120175158;
import migrations.Version20260120215034;

public class MigrationMigrateExample
{
    public static void main(String[] args){
        try {

            // Configure database (SQLite example)
            /*DatabaseConfig config = DatabaseConfig.builder()
                    .sqlite("myapp.db")
                    .build();*/

            // Or MySQL:
            /*DatabaseConfig config = DatabaseConfig.builder()
                     .mysql("localhost", 3306, "teste")
                     .credentials("root", "123456")
                     .build();*/

            // Or Postgres
            /*DatabaseConfig config = DatabaseConfig.builder()
                    .postgresql("localhost", 5432, "teste")
                    .credentials("postgres", "123456")
                    .build();*/

            // Configure database sqlserver
            DatabaseConfig config = DatabaseConfig.builder()
                    .sqlserver("localhost", 1433, "teste").
                    credentials("sa", "123456").build();



            ORM orm = new ORM(config);

            EntityManager em = orm.getEntityManager();

            MigrationManager migrationManager = orm.getMigrationManager();
            migrationManager.addMigration(new Version20260120215034());
            migrationManager.migrate();

            User user1 = new User("john_doe", "john@example.com", 25, "John Doe");
            em.persist(user1);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
