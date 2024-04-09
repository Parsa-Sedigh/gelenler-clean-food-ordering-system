package com.food.ordering.system.payment.service.dataaccess.creditentry.repository;

import com.food.ordering.system.payment.service.dataaccess.creditentry.entity.CreditEntryEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreditEntryJpaRepository extends JpaRepository<CreditEntryEntity, UUID> {
    /* this will get a SELECT FOR UPDATE lock. So it will put a lock on this customer row and therefore won't allow another
    SELECT FOR UPDATE, until the tx is completed which will serialize the multiple transactions and only allow one at a time.

    Now because of this pessimistic lock, a thread might wait for some time and may also get a timeout exception.*/
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CreditEntryEntity> findByCustomerId(UUID customerId);


}