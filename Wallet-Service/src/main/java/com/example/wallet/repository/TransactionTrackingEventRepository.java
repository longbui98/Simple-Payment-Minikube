package com.example.wallet.repository;

import com.example.wallet.data.TransactionEventHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionTrackingEventRepository extends JpaRepository<TransactionEventHistory, String> {}
