package com.example;

import com.vcinsidedigital.orm_utils.ORM;
import com.vcinsidedigital.orm_utils.config.DatabaseConfig;

public class CreateDatabaseORM
{
    public static void main(String[] args) {
        try {
            // Example 1: MySQL
            DatabaseConfig configMySQL = DatabaseConfig.builder()
                    .mysql("localhost", 3306, "teste")
                    .credentials("root", "123456")
                    .build();

            ORM.createDatabase(configMySQL);
            System.out.println("MySQL database created successfully");

            // Example 2: PostgreSQL
            DatabaseConfig configPostgreSQL = DatabaseConfig.builder()
                    .postgresql("localhost", 5432, "teste")
                    .credentials("postgres", "123456")
                    .build();



            //ORM.createDatabase(configPostgreSQL);
            System.out.println("PostgreSQL database created successfully");

            // Example 3: SQL Server
            DatabaseConfig configSQLServer = DatabaseConfig.builder()
                    .sqlserver("localhost", 1433, "teste")
                    .credentials("sa", "123456")
                    .build();



            //ORM.createDatabase(configSQLServer);
            System.out.println("SQL Server database created successfully");

            // You can now create the ORM normally
            // ORM orm = new ORM(configMySQL);
            // ... rest of the configuration

            // Delete example
            ORM.dropDatabase(configMySQL);
            System.out.println("MySQL database deleted successfully");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
