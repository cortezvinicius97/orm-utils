package com.example;

import com.example.entity.User;
import com.vcinsidedigital.orm_utils.ORM;
import com.vcinsidedigital.orm_utils.config.DatabaseConfig;
import com.vcinsidedigital.orm_utils.core.EntityManager;

import java.util.List;

public class QueryBuilderExample
{
    public static void main(String[] args){
        try {
            // Configure database (SQLite example)
            /*DatabaseConfig config = DatabaseConfig.builder()
                    .sqlite("myapp.db")
                    .build();*/

            //MYSQL
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
            EntityManager entityManager = orm.getEntityManager();
            String result = entityManager.query("SELECT * FROM users").toString();

            System.out.println(result);
            List<User> users = entityManager.queryBuilder(User.class)
                    .like("username", "%john_doe%")
                    .get();

            System.out.println(users.getFirst().getUsername());

        }catch (Exception e){
            throw new RuntimeException(e);
        }

    }
}
