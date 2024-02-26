package com.food.ordering.system.payment.service.domain;

import com.food.ordering.system.domain.event.publisher.DomainEventPublisher;
import com.food.ordering.system.domain.valueobject.Money;
import com.food.ordering.system.domain.valueobject.PaymentStatus;
import com.food.ordering.system.payment.service.domain.entity.CreditEntry;
import com.food.ordering.system.payment.service.domain.entity.CreditHistory;
import com.food.ordering.system.payment.service.domain.entity.Payment;
import com.food.ordering.system.payment.service.domain.event.PaymentCancelledEvent;
import com.food.ordering.system.payment.service.domain.event.PaymentCompletedEvent;
import com.food.ordering.system.payment.service.domain.event.PaymentEvent;
import com.food.ordering.system.payment.service.domain.event.PaymentFailedEvent;
import com.food.ordering.system.payment.service.domain.valueobject.CreditHistoryId;
import com.food.ordering.system.payment.service.domain.valueobject.TransactionType;
import lombok.extern.slf4j.Slf4j;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.food.ordering.system.domain.DomainConstants.UTC;

@Slf4j
public class PaymentDomainServiceImpl implements PaymentDomainService {
    @Override
    public PaymentEvent validateAndInitiatePayment(Payment payment,
                                                   CreditEntry creditEntry,
                                                   List<CreditHistory> creditHistories,
                                                   List<String> failureMessages,
                                                   DomainEventPublisher<PaymentCompletedEvent> paymentCompletedEventDomainEventPublisher,
                                                   DomainEventPublisher<PaymentFailedEvent> paymentFailedEventDomainEventPublisher
                                                   ) {
        payment.validatePaymentMethod(failureMessages);
        payment.initializePayment();
        validateCreditEntry(payment, creditEntry, failureMessages);
        updateCreditHistory(payment, creditHistories, TransactionType.DEBIT);
        subtractCreditEntry(payment, creditEntry);
        validateCreditHistory(creditEntry, creditHistories, failureMessages);

        if (failureMessages.isEmpty()) {
            log.info("Payment is initiated for order id: {}", payment.getOrderId().getValue());
            payment.updateStatus(PaymentStatus.COMPLETED);

            return new PaymentCompletedEvent(payment, ZonedDateTime.now(ZoneId.of(UTC)), paymentCompletedEventDomainEventPublisher);
        }

        log.info("Payment initiation is failed for order id: {}", payment.getOrderId().getValue());
        payment.updateStatus(PaymentStatus.FAILED);

        return new PaymentFailedEvent(payment, ZonedDateTime.now(ZoneId.of(UTC)), failureMessages, paymentFailedEventDomainEventPublisher);
    }

    @Override
    public PaymentEvent validateAndCancelPayment(Payment payment,
                                                 CreditEntry creditEntry,
                                                 List<CreditHistory> creditHistories,
                                                 List<String> failureMessages,
                                                 DomainEventPublisher<PaymentCancelledEvent> paymentCancelledEventDomainEventPublisher,
                                                 DomainEventPublisher<PaymentFailedEvent> paymentFailedEventDomainEventPublisher) {
        payment.validatePaymentMethod(failureMessages);
        addCreditEntry(payment, creditEntry);

        /* Use TransactionType.CREDIT because this will topup a credit record into the history. Because here the payment is getting cancelled. */
        updateCreditHistory(payment, creditHistories, TransactionType.CREDIT);

        if (failureMessages.isEmpty()) {
            log.info("Payment is cancelled for order id: {}", payment.getOrderId().getValue());
            payment.updateStatus(PaymentStatus.CANCELLED);

            return new PaymentCancelledEvent(payment, ZonedDateTime.now(ZoneId.of(UTC)), paymentCancelledEventDomainEventPublisher);
        }

        log.info("Payment cancellation is failed for order id: {}", payment.getOrderId().getValue());
        payment.updateStatus(PaymentStatus.FAILED);

        return new PaymentFailedEvent(payment, ZonedDateTime.now(ZoneId.of(UTC)), failureMessages,
                paymentFailedEventDomainEventPublisher);
    }

    private void validateCreditEntry(Payment payment, CreditEntry creditEntry, List<String> failureMessages) {
        if (payment.getPrice().isGreaterThan(creditEntry.getTotalCreditAmount())) {
            log.error("Customer with id: {} doesn't have enough credit for payment!", payment.getCustomerId().getValue());

            /* Instead of throwing an error, we're accumulating the failureMessages into a list and in the end we return all of them to the
            client. */
            failureMessages.add("Customer with id=" + payment.getCustomerId().getValue() + " doesn't have enough credit for payment!");
        }
    }

    private void subtractCreditEntry(Payment payment, CreditEntry creditEntry) {
        creditEntry.subtractCreditAmount(payment.getPrice());
    }

    private void updateCreditHistory(Payment payment, List<CreditHistory> creditHistories, TransactionType transactionType) {
        creditHistories.add(CreditHistory.builder()
                .creditHistoryId(new CreditHistoryId(UUID.randomUUID()))
                .customerId(payment.getCustomerId())
                .amount(payment.getPrice())
                .transactionType(transactionType)
                .build());
    }

    private void validateCreditHistory(CreditEntry creditEntry, List<CreditHistory> creditHistories, List<String> failureMessages) {
        Money totalCreditHistory = getTotalHistoryAmount(creditHistories, TransactionType.CREDIT);
        Money totalDebitHistory = getTotalHistoryAmount(creditHistories, TransactionType.DEBIT);

        // checks if historical records of credits must be greater than the debit, if not we have an error
        if (totalDebitHistory.isGreaterThan(totalCreditHistory)) {
            log.error("Customer with id: {} doesn't have enough credit according to credit history", creditEntry.getCustomerId().getValue());
            failureMessages.add("Customer with id= {}" + creditEntry.getCustomerId().getValue() +
                    " doesn't have enough credit according to credit history");
        }

        if (creditEntry.getTotalCreditAmount().equals(totalCreditHistory.subtract(totalDebitHistory))) {
            log.error("Credit history total is not equal to current credit for customer id: {}!", creditEntry.getCustomerId().getValue());
            failureMessages.add("Credit history total is not equal to current credit for customer id: " + creditEntry.getCustomerId().getValue() + "!");
        }
    }

    private Money getTotalHistoryAmount(List<CreditHistory> creditHistories, TransactionType transactionType) {
        return creditHistories.stream()
                .filter(creditHistory -> transactionType == creditHistory.getTransactionType())
                .map(CreditHistory::getAmount)
                .reduce(Money.ZERO, Money::add);
    }

    private void addCreditEntry(Payment payment, CreditEntry creditEntry) {
        creditEntry.addCreditAmount(payment.getPrice());
    }
}