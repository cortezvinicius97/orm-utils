package com.example;

import com.example.entity.Posts;
import com.example.entity.Tag;
import com.example.entity.User;
import com.vcinsidedigital.orm_utils.ORM;
import com.vcinsidedigital.orm_utils.config.DatabaseConfig;
import com.vcinsidedigital.orm_utils.migration.MigrationManager;
import migrations.Version20260118233630;


public class MigrationExample
{
    public static void main(String[] args){
        try {

            // Configure database (SQLite example)
            /*DatabaseConfig config = DatabaseConfig.builder()
                    .sqlite("myapp.db")
                    .build();*/

            // Or MySQL:
            DatabaseConfig config = DatabaseConfig.builder()
                     .mysql("localhost", 3306, "teste")
                     .credentials("root", "123456")
                     .build();


            ORM orm = new ORM(config)
                    .registerEntity(User.class)
                    .registerEntity(Posts.class)
                    .registerEntity(Tag.class)
                    .createMigrations();

            MigrationManager migrationManager = orm.getMigrationManager();
            migrationManager.addMigration(new Version20260118233630());
            migrationManager.migrate();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
