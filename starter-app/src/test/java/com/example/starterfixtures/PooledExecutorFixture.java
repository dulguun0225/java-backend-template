package com.example.starterfixtures;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class PooledExecutorFixture {
    ExecutorService pool() {
        return Executors.newFixedThreadPool(4);
    }
}
