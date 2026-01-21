package com.example;

import com.example.entity.Posts;
import com.example.entity.Tag;
import com.example.entity.User;
import com.vcinsidedigital.orm_utils.ORM;
import com.vcinsidedigital.orm_utils.config.DatabaseConfig;


public class MigrationGenExample
{
    public static void main(String[] args){
        try {

            // Configure database (SQLite example)
            /*DatabaseConfig config = DatabaseConfig.builder()
                    .sqlite("myapp.db")
                    .build();*/

            // Or MySQL:
           /* DatabaseConfig config = DatabaseConfig.builder()
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


            ORM orm = new ORM(config)
                    .registerEntity(User.class)
                    .registerEntity(Posts.class)
                    .registerEntity(Tag.class)
                    .createMigrations();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
