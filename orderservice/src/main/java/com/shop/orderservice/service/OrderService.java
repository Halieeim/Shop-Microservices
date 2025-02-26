package com.shop.orderservice.service;

import com.shop.orderservice.dto.InventoryResponse;
import com.shop.orderservice.dto.OrderLineItemsDto;
import com.shop.orderservice.dto.OrderRequest;
import com.shop.orderservice.model.Order;
import com.shop.orderservice.model.OrderLineItems;
import com.shop.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {
    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;
    public String placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        List<OrderLineItems> list = orderRequest.getOrderLineItemsDtoList().stream().map(this::mapToDto).toList();
        order.setOrderLineItemsList(list);

        List<String> skuCodesList = order.getOrderLineItemsList().stream().map(OrderLineItems::getSkuCode).toList();
        // Call inventory service and check if the product is in the stock before placing the order
        // Synchronous Communication
        InventoryResponse[] inventoryResponses = webClientBuilder.build().get()
                .uri("http://inventoryservice/api/inventory",
                        uriBuilder -> uriBuilder.queryParam("skuCode", skuCodesList).build())
                .retrieve()
                .bodyToMono(InventoryResponse[].class)
                .block();
        assert inventoryResponses != null;
        boolean allProductsInStock = Arrays.stream(inventoryResponses).allMatch(InventoryResponse::isInStock);

        if (allProductsInStock){
            orderRepository.save(order);
            return "Order Placed Successfully";
        } else {
            throw new IllegalArgumentException("Product is not in stock, please try again later.");
        }
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setId(orderLineItemsDto.getId());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}
