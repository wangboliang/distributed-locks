package com.xianyuli.locks.redis.base;

public interface Lock {

    /**
     * 创建锁
     * @return
     */
    boolean getLock(String key, String value);

    /**
     * 解锁
     * @return
     */
    boolean unLock(String key, String value);
}
