package com.tsmc.autochannel.repository;

import com.tsmc.autochannel.entity.LogStatus;
import com.tsmc.autochannel.entity.OperationType;
import com.tsmc.autochannel.entity.TransferLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransferLogRepository extends JpaRepository<TransferLog, Long> {

    List<TransferLog> findByChannelId(String channelId);

    List<TransferLog> findByChannelIdAndOperation(String channelId, OperationType operation);

    List<TransferLog> findByStatus(LogStatus status);
}
