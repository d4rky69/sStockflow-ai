package com.stockflow.actions

import com.stockflow.security.TenantAccessContext
import com.stockflow.fleetbase.FleetbaseClient
import com.stockflow.fleetbase.FleetbaseCreatedOrder
import com.stockflow.fleetbase.FleetbaseDispatchedOrder
import com.stockflow.fleetbase.FleetbaseOrderCreateCommand
import com.stockflow.fleetbase.FleetbaseTenantBinding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@SpringBootTest(properties = [
    "stockflow.fleetbase.tenant-id=TEN-ACME-PHARMA",
    "stockflow.fleetbase.organization-id=company_test"
])
@ActiveProfiles("test")
@Transactional
class FleetbaseOrderLinkServiceTest {
    @Autowired lateinit var service: FleetbaseOrderLinkService
    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    fun `prepare is durable idempotent and performs no Fleetbase write`() {
        val userId = "fleetbase-link-test-user"
        val proposalId = UUID.randomUUID()
        val executionId = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO app_user(user_id,email,display_name) VALUES (?, ?, ?)",
            userId, "fleetbase-link-test@stockflow.local", "Fleetbase Link Test"
        )
        jdbc.update(
            """INSERT INTO action_proposal(
                proposal_id,tenant_id,proposal_type,status,sku_id,quantity,source_warehouse_id,
                destination_warehouse_id,currency,reason,idempotency_key,created_by
            ) VALUES (?, 'TEN-ACME-PHARMA', 'TRANSFER', 'APPROVED', 'SKU-PARA-650', 10,
                'WH-CHENNAI', 'WH-BENGALURU', 'INR', 'Fleetbase linkage test', ?, ?)""",
            proposalId, "test-proposal-$proposalId", userId
        )
        jdbc.update(
            """INSERT INTO transfer_execution(
                execution_id,tenant_id,proposal_id,status,sku_id,source_warehouse_id,
                destination_warehouse_id,quantity,vehicle_reference,idempotency_key,created_by
            ) VALUES (?, 'TEN-ACME-PHARMA', ?, 'PLANNED', 'SKU-PARA-650', 'WH-CHENNAI',
                'WH-BENGALURU', 10, 'vehicle_test', ?, ?)""",
            executionId, proposalId, "test-execution-$executionId", userId
        )
        val actor = TenantAccessContext(
            userId = userId,
            email = "fleetbase-link-test@stockflow.local",
            tenantId = "TEN-ACME-PHARMA",
            roleCode = "LOGISTICS_MANAGER",
            permissions = setOf("TRANSFER_EXECUTE"),
            warehouseIds = emptySet()
        )

        val created = service.prepare(actor, executionId, "test-link-$executionId")
        val retried = service.prepare(actor, executionId, "test-link-$executionId")
        val retriedWithNewKey = service.prepare(actor, executionId, "replacement-key-$executionId")

        assertEquals(created.linkId, retried.linkId)
        assertEquals(created.linkId, retriedWithNewKey.linkId)
        assertEquals("PREPARED", created.status)
        assertEquals("company_test", created.fleetbaseOrganizationId)
        assertEquals("vehicle_test", created.vehicleId)
        assertEquals(0, created.attemptCount)
        assertNull(created.fleetbaseOrderId)
        assertFalse(created.remoteWritePerformed)
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM fleetbase_order_link WHERE transfer_execution_id=?",
            Int::class.java,
            executionId
        ))
    }

    @Test
    fun `StockFlow-only reserved transfer remains dispatchable without a Fleetbase link`() {
        val userId = "stockflow-only-dispatch-user"
        val proposalId = UUID.randomUUID()
        val executionId = UUID.randomUUID()
        jdbc.update("INSERT INTO app_user(user_id,email,display_name) VALUES (?, ?, ?)", userId, "stockflow-only@stockflow.local", "StockFlow Only")
        jdbc.update(
            """INSERT INTO action_proposal(
                proposal_id,tenant_id,proposal_type,status,sku_id,quantity,source_warehouse_id,
                destination_warehouse_id,currency,reason,idempotency_key,created_by
            ) VALUES (?, 'TEN-ACME-PHARMA', 'TRANSFER', 'APPROVED', 'SKU-PARA-650', 1,
                'WH-CHENNAI', 'WH-BENGALURU', 'INR', 'No Fleetbase link', ?, ?)""",
            proposalId, "stockflow-only-proposal-$proposalId", userId
        )
        jdbc.update(
            """INSERT INTO transfer_execution(
                execution_id,tenant_id,proposal_id,status,sku_id,source_warehouse_id,
                destination_warehouse_id,quantity,idempotency_key,created_by
            ) VALUES (?, 'TEN-ACME-PHARMA', ?, 'RESERVED', 'SKU-PARA-650', 'WH-CHENNAI',
                'WH-BENGALURU', 1, ?, ?)""",
            executionId, proposalId, "stockflow-only-execution-$executionId", userId
        )
        val actor = TenantAccessContext(userId, "stockflow-only@stockflow.local", "TEN-ACME-PHARMA", "LOGISTICS_MANAGER", setOf("TRANSFER_EXECUTE"), emptySet())

        assertNull(service.dispatchRemoteOrderIfLinked(actor, executionId))
    }

    @Test
    fun `remote creation persists Fleetbase identity once and never dispatches locally`() {
        val userId = "fleetbase-create-test-user"
        val proposalId = UUID.randomUUID()
        val executionId = UUID.randomUUID()
        jdbc.update("INSERT INTO app_user(user_id,email,display_name) VALUES (?, ?, ?)", userId, "fleetbase-create@stockflow.local", "Fleetbase Create Test")
        jdbc.update(
            """INSERT INTO action_proposal(
                proposal_id,tenant_id,proposal_type,status,sku_id,quantity,source_warehouse_id,
                destination_warehouse_id,currency,reason,idempotency_key,created_by
            ) VALUES (?, 'TEN-ACME-PHARMA', 'TRANSFER', 'APPROVED', 'SKU-PARA-650', 25,
                'WH-CHENNAI', 'WH-BENGALURU', 'INR', 'Fleetbase remote create test', ?, ?)""",
            proposalId, "test-create-proposal-$proposalId", userId
        )
        jdbc.update(
            """INSERT INTO transfer_execution(
                execution_id,tenant_id,proposal_id,status,sku_id,source_warehouse_id,
                destination_warehouse_id,quantity,vehicle_reference,idempotency_key,created_by
            ) VALUES (?, 'TEN-ACME-PHARMA', ?, 'PLANNED', 'SKU-PARA-650', 'WH-CHENNAI',
                'WH-BENGALURU', 25, 'vehicle_test', ?, ?)""",
            executionId, proposalId, "test-create-execution-$executionId", userId
        )
        val actor = TenantAccessContext(userId, "fleetbase-create@stockflow.local", "TEN-ACME-PHARMA", "LOGISTICS_MANAGER", setOf("TRANSFER_EXECUTE"), emptySet())
        var createCalls = 0
        var dispatchCalls = 0
        val client = object : FleetbaseClient(false, "https://api.fleetbase.io/v1", "", false, 1, 1) {
            override fun createOrder(command: FleetbaseOrderCreateCommand): FleetbaseCreatedOrder {
                createCalls++
                return FleetbaseCreatedOrder("order_test_01", command.internalId, "created", false, "FB-TEST-01", null)
            }

            override fun dispatchOrder(orderId: String): FleetbaseDispatchedOrder {
                dispatchCalls++
                return FleetbaseDispatchedOrder(orderId, "dispatched", true, "FB-TEST-01")
            }
        }
        val subject = FleetbaseOrderLinkService(jdbc, FleetbaseTenantBinding("TEN-ACME-PHARMA", "company_test"), client)

        val prepared = subject.prepare(actor, executionId, "prepare-$executionId")
        val created = subject.createRemoteOrder(actor, executionId, "create-$executionId")
        val retried = subject.createRemoteOrder(actor, executionId, "retry-$executionId")

        assertEquals(prepared.linkId, created.linkId)
        assertEquals(created.linkId, retried.linkId)
        assertEquals("CREATED", created.status)
        assertEquals("order_test_01", created.fleetbaseOrderId)
        assertEquals(1, created.attemptCount)
        assertTrue(created.remoteWritePerformed)
        assertEquals(1, createCalls)

        val batchId = jdbc.queryForObject(
            """SELECT batch_inventory_id FROM batch_inventory WHERE tenant_id='TEN-ACME-PHARMA'
               AND warehouse_id='WH-CHENNAI' AND sku_id='SKU-PARA-650' ORDER BY expiry_date LIMIT 1""",
            UUID::class.java
        )!!
        jdbc.update("UPDATE transfer_execution SET status='RESERVED' WHERE execution_id=?", executionId)
        jdbc.update("UPDATE batch_inventory SET reserved_quantity=reserved_quantity+25 WHERE batch_inventory_id=?", batchId)
        jdbc.update(
            """INSERT INTO transfer_execution_allocation(
                allocation_id,execution_id,tenant_id,source_batch_inventory_id,batch_number,quantity,
                expiry_date,manufacture_date,unit_cost,currency,storage_condition_code
            ) SELECT ?,?,?,batch_inventory_id,batch_number,25,expiry_date,manufacture_date,unit_cost,currency,storage_condition_code
                FROM batch_inventory WHERE batch_inventory_id=?""",
            UUID.randomUUID(), executionId, "TEN-ACME-PHARMA", batchId
        )

        val dispatched = subject.dispatchRemoteOrderIfLinked(actor, executionId)
        val dispatchRetry = subject.dispatchRemoteOrderIfLinked(actor, executionId)

        assertEquals("DISPATCHED", dispatched?.status)
        assertEquals(dispatched?.linkId, dispatchRetry?.linkId)
        assertEquals(1, dispatchCalls)
    }
}
