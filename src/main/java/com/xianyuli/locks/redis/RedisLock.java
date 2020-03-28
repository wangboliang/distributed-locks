package com.xianyuli.locks.redis;

import com.xianyuli.locks.redis.base.Lock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Component
public class RedisLock implements Lock {

    @Autowired
    JedisPool jedisPool;

    private static final int EXPIRE = 1000 * 60;

    @Override
    public boolean getLock(String key, String value) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            if (jedis == null) {
                return false;
            }
            return jedis.set(key, value, "NX", "PX", EXPIRE).equalsIgnoreCase("ok");
        } catch (Exception e) {
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
        return false;
    }

    @Override
    public boolean unLock(String key, String value) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            if (jedis == null) {
                return false;
            }
            //lua脚本原子操作判断是否是自己的锁：
            // if redis.call('get',#{key})==#{value} then return redis.cal('del',#{key}) else return 0 end
            String script = String.format("if redis.call('get','%s')=='%s' then return redis.call('del','%s') else return 0 end", key, value, key);
            return Integer.valueOf(jedis.eval(script).toString()) == 1;
        } catch (Exception e) {
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
        return false;
    }
}
