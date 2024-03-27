package com.food.ordering.system.restaurant.service;

import com.food.ordering.system.domain.valueobject.OrderId;
import com.food.ordering.system.outbox.OutboxStatus;
import com.food.ordering.system.restaurant.service.domain.RestaurantDomainService;
import com.food.ordering.system.restaurant.service.dto.RestaurantApprovalRequest;
import com.food.ordering.system.restaurant.service.domain.entity.Restaurant;
import com.food.ordering.system.restaurant.service.domain.event.OrderApprovalEvent;
import com.food.ordering.system.restaurant.service.domain.exception.RestaurantNotFoundException;
import com.food.ordering.system.restaurant.service.mapper.RestaurantDataMapper;
import com.food.ordering.system.restaurant.service.outbox.model.OrderOutboxMessage;
import com.food.ordering.system.restaurant.service.outbox.scheduler.OrderOutboxHelper;
//import com.food.ordering.system.restaurant.service.ports.output.message.publisher.OrderApprovedMessagePublisher;
//import com.food.ordering.system.restaurant.service.ports.output.message.publisher.OrderRejectedMessagePublisher;
import com.food.ordering.system.restaurant.service.ports.output.message.publisher.RestaurantApprovalResponseMessagePublisher;
import com.food.ordering.system.restaurant.service.ports.output.repository.OrderApprovalRepository;
import com.food.ordering.system.restaurant.service.ports.output.repository.RestaurantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class RestaurantApprovalRequestHelper {
    private final RestaurantDomainService restaurantDomainService;
    private final RestaurantDataMapper restaurantDataMapper;
    private final RestaurantRepository restaurantRepository;
    private final OrderApprovalRepository orderApprovalRepository;
//    private final OrderApprovedMessagePublisher orderApprovedMessagePublisher;
//    private final OrderRejectedMessagePublisher orderRejectedMessagePublisher;
    private final OrderOutboxHelper orderOutboxHelper;
    private final RestaurantApprovalResponseMessagePublisher restaurantApprovalResponseMessagePublisher;

    public RestaurantApprovalRequestHelper(RestaurantDomainService restaurantDomainService,
                                           RestaurantDataMapper restaurantDataMapper,
                                           RestaurantRepository restaurantRepository,
                                           OrderApprovalRepository orderApprovalRepository,
                                           OrderOutboxHelper orderOutboxHelper,
                                           RestaurantApprovalResponseMessagePublisher restaurantApprovalResponseMessagePublisher
//                                           OrderApprovedMessagePublisher orderApprovedMessagePublisher,
//                                           OrderRejectedMessagePublisher orderRejectedMessagePublisher
    ) {
        this.restaurantDomainService = restaurantDomainService;
        this.restaurantDataMapper = restaurantDataMapper;
        this.restaurantRepository = restaurantRepository;
        this.orderApprovalRepository = orderApprovalRepository;
//        this.orderApprovedMessagePublisher = orderApprovedMessagePublisher;
//        this.orderRejectedMessagePublisher = orderRejectedMessagePublisher;
        this.orderOutboxHelper = orderOutboxHelper;
        this.restaurantApprovalResponseMessagePublisher = restaurantApprovalResponseMessagePublisher;
    }

    @Transactional
//    public OrderApprovalEvent persistOrderApproval(RestaurantApprovalRequest restaurantApprovalRequest) {
    public void persistOrderApproval(RestaurantApprovalRequest restaurantApprovalRequest) {
        if (publishIfOutboxMessageProcessed(restaurantApprovalRequest)) {
            log.info("An outbox message with saga id: {} already saved to database!",
                    restaurantApprovalRequest.getSagaId());

            return;
        }

//        log.info("Processing restaurant approval for order id: {}", restaurantApprovalRequest.getOrderId());
        List<String> failureMessages = new ArrayList<>();
        Restaurant restaurant = findRestaurant(restaurantApprovalRequest);

//        OrderApprovalEvent orderApprovalEvent = restaurantDomainService.validateOrder(
//                restaurant, failureMessages, orderApprovedMessagePublisher, orderRejectedMessagePublisher);
        OrderApprovalEvent orderApprovalEvent = restaurantDomainService.validateOrder(restaurant, failureMessages);

        orderApprovalRepository.save(restaurant.getOrderApproval());

        orderOutboxHelper.saveOrderOutboxMessage(
                restaurantDataMapper.orderApprovalEventToOrderEventPayload(orderApprovalEvent),
                orderApprovalEvent.getOrderApproval().getApprovalStatus(),
                OutboxStatus.STARTED,
                UUID.fromString(restaurantApprovalRequest.getSagaId())
        );
    }

    private Restaurant findRestaurant(RestaurantApprovalRequest restaurantApprovalRequest) {
        Restaurant restaurant = restaurantDataMapper.restaurantApprovalRequestToRestaurant(restaurantApprovalRequest);

        Optional<Restaurant> restaurantResult = restaurantRepository.findRestaurantInformation(restaurant);

        if (restaurantResult.isEmpty()) {
            log.error("Restaurant with id " + restaurant.getId().getValue() + " not found!");

            throw new RestaurantNotFoundException("Restaurant with id " + restaurant.getId().getValue() + " not found!");
        }

        Restaurant restaurantEntity = restaurantResult.get();
        restaurant.setActive(restaurantEntity.isActive());
        restaurant.getOrderDetail().getProducts().forEach(product -> restaurantEntity.getOrderDetail().getProducts().forEach(p -> {
            if (p.getId().equals(product.getId())) {
                product.updateWithConfirmedNamePriceAndAvailability(p.getName(), p.getPrice(), p.isAvailable());
            }
        }));

        restaurant.getOrderDetail().setId(new OrderId(UUID.fromString(restaurantApprovalRequest.getOrderId())));

        return restaurant;
    }

    private boolean publishIfOutboxMessageProcessed(RestaurantApprovalRequest restaurantApprovalRequest) {
        Optional<OrderOutboxMessage> orderOutboxMessage =
            orderOutboxHelper.getCompletedOrderOutboxMessageBySagaIdAndOutboxStatus(
                    UUID.fromString(restaurantApprovalRequest.getSagaId()), OutboxStatus.COMPLETED);

        if (orderOutboxMessage.isPresent()) {
            restaurantApprovalResponseMessagePublisher.publish(orderOutboxMessage.get(),
                    orderOutboxHelper::updateOutboxStatus);

            return true;
        }

        return false;
    }
}
