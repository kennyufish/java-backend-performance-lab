package com.tszkinyu.performancelab.events;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CustomerEventRepository extends JpaRepository<CustomerEvent, Long> {

	@Query(value = CustomerEventSql.FIND_RECENT, nativeQuery = true)
	List<CustomerEvent> findRecent(
			@Param("tenantId") int tenantId,
			@Param("eventType") String eventType,
			@Param("from") Instant from,
			@Param("limit") int limit);
}
