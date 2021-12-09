/*
 * Copyright (C) 2021 Radix IoT LLC. All rights reserved.
 */
package com.serotonin.m2m2.db.upgrade;

import java.io.PrintWriter;

import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

/**
 * Remove data points with dataTypeId = 5 (IMAGE) from the database.
 * If {@link Upgrade19} runs after the IMAGE data type was removed then you will have points with dataTypeId = 0,
 * these will be removed too.
 */
public class Upgrade44 extends DBUpgrade {

    @Override
    protected void upgrade() throws Exception {
        Field<Integer> dataTypeId = DSL.field("dataTypeId", SQLDataType.INTEGER.nullable(false));

        try (var log = new PrintWriter(createUpdateLogOutputStream())) {
            log.printf("Deleting all data points with dataTypeId 5 (IMAGE) and 0 (UNKNOWN)%n");

            int count = create.deleteFrom(DSL.table("dataPoints"))
                    .where(dataTypeId.in(0, 5))
                    .execute();

            log.printf("Deleted %d data points%n", count);
        }
    }

    @Override
    protected String getNewSchemaVersion() {
        return "45";
    }
}
