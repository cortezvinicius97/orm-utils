package migrations;

import com.vcinsidedigital.orm_utils.migration.Migration;
import com.vcinsidedigital.orm_utils.migration.MigrationContext;

/**
 * Auto-generated migration
 * Generated at: 2026-01-20T17:51:58.112289200
 * example Postgres
 */
public class Version20260120175158 extends Migration {

    public Version20260120175158() {
        super("20260120175158");
    }

    @Override
    public void up(MigrationContext context) throws Exception {
        context.execute(
            "CREATE TABLE IF NOT EXISTS users (\n" +
                "    id BIGSERIAL PRIMARY KEY,\n" +
                "    username VARCHAR(20) NOT NULL UNIQUE,\n" +
                "    email VARCHAR(255) NOT NULL,\n" +
                "    age INT,\n" +
                "    full_name VARCHAR(60) NOT NULL\n" +
                ")"
        );
        context.execute(
            "CREATE TABLE IF NOT EXISTS tags (\n" +
                "    id BIGSERIAL PRIMARY KEY,\n" +
                "    name VARCHAR(50) NOT NULL UNIQUE\n" +
                ")"
        );
        context.execute(
            "CREATE TABLE IF NOT EXISTS posts (\n" +
                "    id BIGSERIAL PRIMARY KEY,\n" +
                "    title VARCHAR(200) NOT NULL,\n" +
                "    content TEXT,\n" +
                "    user_id BIGINT,\n" +
                "    CONSTRAINT fk_posts_user_id FOREIGN KEY (user_id) REFERENCES users(id)\n" +
                ")"
        );
        context.execute(
            "CREATE TABLE IF NOT EXISTS post_tags (\n" +
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
