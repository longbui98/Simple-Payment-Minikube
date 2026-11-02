package com.example.wallet.repository;

import com.example.wallet.data.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, String> {

    @Modifying
    @Query(value = """
        UPDATE wallet
        SET balance = balance - :amount
        WHERE user_id = :userId
          AND balance >= :amount
        """, nativeQuery = true)
    int debit(@Param("userId") String userId, @Param("amount") BigDecimal amount);

    @Modifying
    @Query(value = """
        UPDATE wallet
        SET balance = balance + :amount
        WHERE user_id = :userId
        """, nativeQuery = true)
    int credit(@Param("userId") String userId, @Param("amount") BigDecimal amount);

    @Query(value = """
            SELECT *
            FROM wallet
            WHERE user_id IN (:userIds)
            ORDER BY user_id FOR UPDATE
            """, nativeQuery = true)
    List<Wallet> findAllByIdForUpdate(@Param("userIds") List<String> userIds);
}
