package migrations;

import com.vcinsidedigital.orm_utils.migration.Migration;
import com.vcinsidedigital.orm_utils.migration.MigrationContext;

/**
 * Auto-generated migration
 * Generated at: 2026-01-20T17:19:13.716673600
 * example SQLite
 */
public class Version20260120171913 extends Migration {

    public Version20260120171913() {
        super("20260120171913");
    }

    @Override
    public void up(MigrationContext context) throws Exception {
        context.execute(
            "CREATE TABLE IF NOT EXISTS users (\n" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                "    username TEXT NOT NULL UNIQUE,\n" +
                "    email TEXT NOT NULL,\n" +
                "    age INTEGER,\n" +
                "    full_name TEXT NOT NULL\n" +
                ")"
        );
        context.execute(
            "CREATE TABLE IF NOT EXISTS tags (\n" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                "    name TEXT NOT NULL UNIQUE\n" +
                ")"
        );
        context.execute(
            "CREATE TABLE IF NOT EXISTS posts (\n" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                "    title TEXT NOT NULL,\n" +
                "    content TEXT,\n" +
                "    user_id INTEGER,\n" +
                "    CONSTRAINT fk_posts_user_id FOREIGN KEY (user_id) REFERENCES users(id)\n" +
                ")"
        );
        context.execute(
            "CREATE TABLE IF NOT EXISTS post_tags (\n" +
                "    post_id INTEGER,\n" +
                "    tag_id INTEGER,\n" +
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
