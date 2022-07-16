package com.hmdp.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 简易的线程池。手动创建线程操作后销毁，性能不太好。
 */

public class SimpleThreadPool {
    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

}
