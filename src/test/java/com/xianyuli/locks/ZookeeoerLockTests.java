package com.xianyuli.locks;

import com.xianyuli.locks.zookeeper.ZookeeperLock;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@SpringBootTest
@RunWith(SpringRunner.class)
public class ZookeeoerLockTests {
    Logger logger = LoggerFactory.getLogger(ZookeeoerLockTests.class);


    @Autowired
    ZookeeperLock zkLock;

    @Test
    public void testZkLock() {
        //模拟多用户秒杀
        //商品
        String shopKey = "iphone";
        //预加载库存
        AtomicInteger stock = new AtomicInteger(10);
        //秒杀开始时间
        long startTime = System.currentTimeMillis();

        //构建多用户
        ArrayList<String> users = new ArrayList<>();
        IntStream.range(0, 10000).forEach(i -> {
            users.add("黄牛党-" + i);
        });
        //秒杀
        users.parallelStream().forEach(user -> {
            long accessTime = System.currentTimeMillis();
            //判断秒杀是否开始秒杀时间,并且用户等待为超时
            if (accessTime > startTime ) {
                //进入秒杀 判断库存是否剩余否则秒杀失败
                zkLock.lock();
                logger.info("用户[{}]拿到锁", user);
                try {
                    if (stock.get() <= 0) {
                        logger.error("用户[{}]抢购失败", user);
                        return;
                    }
                    //生成订单的代码，秒杀业务数据落地到DB通过消息队列完成
                    try {
                        TimeUnit.SECONDS.sleep(1);
                        logger.info("用户[{}]生成订单", user);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    //减库存
                    stock.getAndDecrement();
                    logger.info("用户[{}]抢单成功---库存剩余:{}", user, stock);
                } finally {
                    zkLock.unlock();
                }

            }
        });
        System.out.println(stock.get());
    }


}
