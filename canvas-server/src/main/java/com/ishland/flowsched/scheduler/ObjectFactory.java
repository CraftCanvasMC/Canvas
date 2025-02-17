package com.ishland.flowsched.scheduler;

import java.util.Collections;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

public interface ObjectFactory {

    <K, V> ConcurrentMap<K, V> createConcurrentHashMap();

    <E> Set<E> createConcurrentSet();

    <E> Queue<E> newMPMCQueue();

    <E> Queue<E> newMPSCQueue();

    public static class DefaultObjectFactory implements ObjectFactory {

        @Override
        public <K, V> ConcurrentMap<K, V> createConcurrentHashMap() {
            return new ConcurrentHashMap<>();
        }

        @Override
        public <E> Set<E> createConcurrentSet() {
            return Collections.newSetFromMap(new ConcurrentHashMap<>());
        }

        @Override
        public <E> Queue<E> newMPMCQueue() {
            return new ConcurrentLinkedQueue<>();
        }

        @Override
        public <E> Queue<E> newMPSCQueue() {
            return new ConcurrentLinkedQueue<>();
        }

    }

}
