/*
 * Copyright (C) 2021 Radix IoT LLC. All rights reserved.
 */
package com.serotonin.m2m2.db;

import static org.junit.Assume.assumeTrue;

import java.io.IOException;

import org.junit.Test;
import org.springframework.context.annotation.Bean;

import com.infiniteautomation.mango.spring.DatabaseProxyConfiguration;
import com.serotonin.m2m2.MangoTestBase;
import com.serotonin.m2m2.MockMangoLifecycle;

/**
 * Perform a full upgrade from version 1 of the schema on an POSTGRES database.
 *
 * @author Terry Packer
 */
public class PostgresDatabaseUpgradeTest extends MangoTestBase {

    public PostgresDatabaseUpgradeTest() {
        String currentDb = properties.getProperty("db.type");
        assumeTrue(currentDb.equals("postgres"));
    }

    @Test
    public void doUpgrade() {
        // do nothing, before hook will throw exception if upgrade fails
    }

    public static class UpgradeConfig {
        @Bean
        public DatabaseProxy databaseProxy(DatabaseProxyConfiguration configuration) {
            return new PostgresProxy(null, configuration, "") {
                @Override
                protected boolean restoreTables() throws IOException {
                    try (var outputStream = createTablesOutputStream()) {
                        try (var inputStream = getClass().getResourceAsStream("version1/createTables-POSTGRES.sql")) {
                            runScript(inputStream, outputStream);
                        }
                        try (var inputStream = getClass().getResourceAsStream("version1/defaultData-POSTGRES.sql")) {
                                runScript(inputStream, outputStream);
                        }
                    }
                    return true;
                }
            };
        }
    }

    @Override
    protected MockMangoLifecycle getLifecycle() {
        MockMangoLifecycle lifecycle = super.getLifecycle();
        lifecycle.addRuntimeContextConfiguration(UpgradeConfig.class);
        return lifecycle;
    }
}
