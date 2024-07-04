package com.shop.orderservice.controller;

import com.shop.orderservice.dto.OrderRequest;
import com.shop.orderservice.service.OrderService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.*;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10); // Example thread pool

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public String welcome() {
        return "Welcome to Order Service";
    }

//    @PostMapping
//    @ResponseStatus(HttpStatus.CREATED)
//    @CircuitBreaker(name = "inventory", fallbackMethod = "fallBackMethod")
//    @TimeLimiter(name = "inventory")
//    @Retry(name = "inventory")
//    public CompletableFuture<String> placeOrder(@RequestBody OrderRequest orderRequest) {
//        try {
//            String status = orderService.placeOrder(orderRequest);
//            return CompletableFuture.supplyAsync(() -> status);
//        } catch (IllegalArgumentException illegalArgumentException){
//            return CompletableFuture.supplyAsync(illegalArgumentException::getMessage);
//        }
//    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CircuitBreaker(name = "inventory", fallbackMethod = "fallBackMethod")
    @TimeLimiter(name = "inventory")
    @Retry(name = "inventory")
    public CompletableFuture<String> placeOrder(@RequestBody OrderRequest orderRequest) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Future<?> task = executorService.submit(() -> {
            try {
                String status = orderService.placeOrder(orderRequest);
                future.complete(status);
            }
            catch (IllegalArgumentException illegalArgumentException){
                future.complete(illegalArgumentException.getMessage());
            }
            catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        // Example timeout implementation
        executorService.submit(() -> {
            try {
                task.get(5, TimeUnit.SECONDS); // Wait for task to complete within 5 seconds
            } catch (Exception e) {
                future.completeExceptionally(new TimeoutException("Operation timed out"));
                task.cancel(true); // Cancel the task if it times out
            }
        });

        return future;
    }

    public CompletableFuture<String> fallBackMethod(OrderRequest orderRequest, RuntimeException runtimeException) {
        return CompletableFuture.supplyAsync(() -> "Oops!!! something went wrong, please try again after some time.");
    }
}
