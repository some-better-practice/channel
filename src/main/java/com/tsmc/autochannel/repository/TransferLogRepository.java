package com.tsmc.autochannel.repository;

import com.tsmc.autochannel.entity.LogStatus;
import com.tsmc.autochannel.entity.OperationType;
import com.tsmc.autochannel.entity.TransferLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransferLogRepository extends JpaRepository<TransferLog, Long> {

    List<TransferLog> findByChannelId(String channelId);

    List<TransferLog> findByChannelIdAndOperation(String channelId, OperationType operation);

    List<TransferLog> findByStatus(LogStatus status);

    long countByChannelIdAndStatus(String channelId, LogStatus status);

    @Query("SELECT t FROM TransferLog t WHERE t.channelId = :channelId ORDER BY t.createdAt DESC")
    List<TransferLog> findLatestByChannelId(@Param("channelId") String channelId);

    @Query("SELECT COALESCE(SUM(t.fileSizeBytes), 0) FROM TransferLog t WHERE t.channelId = :channelId AND t.status = 'SUCCESS'")
    long sumFileSizeByChannelId(@Param("channelId") String channelId);
}
