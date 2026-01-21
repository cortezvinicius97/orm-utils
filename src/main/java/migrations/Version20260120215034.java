package migrations;

import com.vcinsidedigital.orm_utils.migration.Migration;
import com.vcinsidedigital.orm_utils.migration.MigrationContext;

/**
 * Auto-generated SQL Server migration
 * Generated at: 2026-01-20T21:50:34.739248400
 * example SQL Server
 */
public class Version20260120215034 extends Migration {

    public Version20260120215034() {
        super("20260120215034");
    }

    @Override
    public void up(MigrationContext context) throws Exception {
        context.execute(
            "CREATE TABLE users (\n" +
                "    id BIGINT PRIMARY KEY IDENTITY(1,1),\n" +
                "    username NVARCHAR(20) NOT NULL UNIQUE,\n" +
                "    email NVARCHAR(255) NOT NULL,\n" +
                "    age INT,\n" +
                "    full_name NVARCHAR(60) NOT NULL\n" +
                ")"
        );
        context.execute(
            "CREATE TABLE tags (\n" +
                "    id BIGINT PRIMARY KEY IDENTITY(1,1),\n" +
                "    name NVARCHAR(50) NOT NULL UNIQUE\n" +
                ")"
        );
        context.execute(
            "CREATE TABLE posts (\n" +
                "    id BIGINT PRIMARY KEY IDENTITY(1,1),\n" +
                "    title NVARCHAR(200) NOT NULL,\n" +
                "    content TEXT,\n" +
                "    user_id BIGINT,\n" +
                "    CONSTRAINT FK_posts_user_id FOREIGN KEY (user_id) REFERENCES users(id)\n" +
                ")"
        );
        context.execute(
            "CREATE TABLE post_tags (\n" +
                "    post_id BIGINT,\n" +
                "    tag_id BIGINT,\n" +
                "    PRIMARY KEY (post_id, tag_id)\n" +
                ")"
        );
    }

    @Override
    public void down(MigrationContext context) throws Exception {
        context.execute(
            "DROP TABLE IF EXISTS post_tags"
        );
        context.execute(
            "DROP TABLE IF EXISTS posts"
        );
        context.execute(
            "DROP TABLE IF EXISTS tags"
        );
        context.execute(
            "DROP TABLE IF EXISTS users"
        );
    }
}
