package com.evolutionnext.structuredconcurrency;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class UserService {
    private final Map<Long, User> users = new ConcurrentHashMap<>();

    public UserService() {
        users.put(1L, new User("Simon", "Roberts"));
        users.put(2L, new User("Sharat", "Chander"));
        users.put(3L, new User("James", "Gosling"));
    }

    public User findUser(Long id) {
        System.out.println("findUser: " + Thread.currentThread());
        return Objects.requireNonNull(users.get(id));
    }

    public User findUserLongTime(long id) {
        System.out.println("findUserLongTime: " + Thread.currentThread());
        try {
            Thread.sleep(40000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return users.get(id);
    }
}
