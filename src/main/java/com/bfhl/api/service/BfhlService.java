package com.bfhl.api.service;

import com.bfhl.api.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BfhlService {

    public List<Integer> fibonacci(int n) {
        if (n < 0 || n > 1000) throw new BadRequestException("fibonacci must be between 0 and 1000.");
        if (n == 0) return List.of();
        if (n == 1) return List.of(0);

        List<Integer> res = new ArrayList<>();
        res.add(0);
        res.add(1);
        while (res.size() < n) {
            int a = res.get(res.size() - 1);
            int b = res.get(res.size() - 2);
            res.add(a + b);
        }
        return res;
    }

    public List<Integer> primes(List<Integer> arr) {
        if (arr == null || arr.isEmpty()) throw new BadRequestException("prime must be a non-empty integer array.");
        if (arr.size() > 2000) throw new BadRequestException("prime array too large (max 2000).");

        List<Integer> res = new ArrayList<>();
        for (int x : arr) {
            if (isPrime(x)) res.add(x);
        }
        return res;
    }

    public long hcf(List<Integer> arr) {
        if (arr == null || arr.isEmpty()) throw new BadRequestException("hcf must be a non-empty integer array.");
        if (arr.size() > 2000) throw new BadRequestException("hcf array too large (max 2000).");

        long g = 0;
        for (int x : arr) {
            g = gcd(g, Math.abs((long) x));
        }
        return g;
    }

    public long lcm(List<Integer> arr) {
        if (arr == null || arr.isEmpty()) throw new BadRequestException("lcm must be a non-empty integer array.");
        if (arr.size() > 1000) throw new BadRequestException("lcm array too large (max 1000).");

        for (int x : arr) {
            if (Math.abs((long) x) > 100000) {
                throw new BadRequestException("lcm values too large (abs must be <= 100000).");
            }
        }

        long res = 1;
        for (int x : arr) {
            res = lcm2(res, x);
        }
        return Math.abs(res);
    }

    private boolean isPrime(int n) {
        if (n < 2) return false;
        if (n == 2 || n == 3) return true;
        if (n % 2 == 0) return false;
        int r = (int) Math.sqrt(n);
        for (int i = 3; i <= r; i += 2) {
            if (n % i == 0) return false;
        }
        return true;
    }

    private long gcd(long a, long b) {
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    private long lcm2(long a, long b) {
        if (a == 0 || b == 0) return 0;
        return Math.abs(a / gcd(a, b) * b);
    }
}
