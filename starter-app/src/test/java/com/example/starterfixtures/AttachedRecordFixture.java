package com.example.starterfixtures;

import com.example.starter.db.tables.records.GreetingRecord;

class AttachedRecordFixture {
    void save(GreetingRecord record) {
        record.store();
    }
}
