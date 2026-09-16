package com.example.starterfixtures;

import org.springframework.jdbc.core.JdbcTemplate;

class JdbcTemplateFixture {
    int count(JdbcTemplate template) {
        Integer n = template.queryForObject("select 1", Integer.class);
        return n == null ? 0 : n;
    }
}
