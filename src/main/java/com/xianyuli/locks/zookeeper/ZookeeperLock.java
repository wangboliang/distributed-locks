package com.xianyuli.locks.zookeeper;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.NodeCache;
import org.apache.curator.framework.recipes.cache.NodeCacheListener;
import org.apache.zookeeper.CreateMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

@Component
public class ZookeeperLock implements Lock {

    @Autowired
    CuratorFramework zkClient;

    @Value("${zk.localPath}")
    private String lockPath;

    private final String separator = "/";

    /**
     * 线程当前所在节点
     */
    private ThreadLocal<String> currentPath = new ThreadLocal<>();

    /**
     * 线程前一个节点，监控它
     */
    private ThreadLocal<String> beforePath = new ThreadLocal<>();

    /**
     * 重入计数器
     */
    private ThreadLocal<Integer> reenterCount = ThreadLocal.withInitial(() -> 0);


    @Override
    public boolean tryLock() {
        try {
            //根节点是否存在，没有就创建一个
            if (zkClient.checkExists().forPath(lockPath) == null) {
                zkClient.create().creatingParentsIfNeeded().forPath(lockPath);
            }
            //当前节点
            if (currentPath.get() == null) {
                //当前节点是父节点，设置属性为临时顺序节点
                String pNode = zkClient.create().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath(lockPath + separator);
                currentPath.set(pNode);
                reenterCount.set(0);
            }
            //当前节点是否是序号最小的节点，是的就获取锁
            List<String> childNodes = this.zkClient.getChildren().forPath(lockPath);
            Collections.sort(childNodes);
            if (currentPath.get().equals(lockPath + separator + childNodes.get(0))) {
                //获取锁的次数加一
                reenterCount.set(reenterCount.get() + 1);
                return true;
            } else {
                //取前一个节点
                int curIndex = childNodes.indexOf(currentPath.get().substring(lockPath.length() + 1));
                //如果是-1表示children里面没有该节点
                String beforeNode = lockPath + separator + childNodes.get(curIndex - 1);
                beforePath.set(beforeNode);
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public void waitForlock() {
        final CountDownLatch cdl = new CountDownLatch(1);
        //创建监听器watch
        NodeCache nodeCache = new NodeCache(zkClient, beforePath.get());
        try {
            nodeCache.start(true);
            nodeCache.getListenable().addListener(() -> {
                cdl.countDown();
            });
        } catch (Exception e) {
        }
        //如果前一个节点还存在，则阻塞自己
        try {
            if (zkClient.checkExists().forPath(beforePath.get()) != null) {
                cdl.await();
            }
        } catch (Exception e) {
        } finally {
            //阻塞结束，说明自己是最小的节点，则取消watch，开始获取锁
            try {
                nodeCache.close();
            } catch (IOException e) {
            }
        }
    }

    @Override
    public void lock() {
        if (!tryLock()) {
            //阻塞等待锁
            waitForlock();
            //重新尝试获取锁
            lock();
        }

    }

    @Override
    public void unlock() {
        if(reenterCount.get() > 1) {
            // 重入次数减1，释放锁
            reenterCount.set(reenterCount.get() - 1);
            return;
        }
        // 删除节点
        if(currentPath.get() != null) {
            try {
                zkClient.delete().guaranteed().deletingChildrenIfNeeded().forPath(currentPath.get());
            } catch (Exception e) {
                e.printStackTrace();
            }finally {
                currentPath.set(null);
                beforePath.set(null);
                reenterCount.set(0);
            }

        }
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return false;
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {

    }

    @Override
    public Condition newCondition() {
        return null;
    }
}
