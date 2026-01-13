package com.vcinsidedigital.orm_utils.migration;

public abstract class Migration {
    private final String version;

    protected Migration(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public abstract void up(MigrationContext context) throws Exception;
    public abstract void down(MigrationContext context) throws Exception;
}
