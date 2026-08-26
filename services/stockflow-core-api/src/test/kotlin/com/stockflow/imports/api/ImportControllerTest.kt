package com.stockflow.imports.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ImportControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    @Test
    fun `foundation package validates and imports`() {
        val packageBytes = packageBytes(productId = "PRD-IMPORT-001")

        mockMvc.perform(
            multipart("/api/v1/imports/synthetic-foundation")
                .file(MockMultipartFile("file", "foundation.zip", "application/zip", packageBytes))
                .header("X-Tenant-ID", "TEN-IMPORT-DEMO")
                .param("mode", "VALIDATE_ONLY")
                .param("strict", "true")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("VALIDATED"))
            .andExpect(jsonPath("$.acceptedRows").value(5))
            .andExpect(jsonPath("$.rejectedRows").value(0))

        mockMvc.perform(
            multipart("/api/v1/imports/synthetic-foundation")
                .file(MockMultipartFile("file", "foundation.zip", "application/zip", packageBytes))
                .header("X-Tenant-ID", "TEN-IMPORT-DEMO")
                .param("mode", "UPSERT")
                .param("strict", "true")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.acceptedRows").value(5))

        mockMvc.perform(
            get("/api/v1/foundation/summary")
                .header("X-Tenant-ID", "TEN-IMPORT-DEMO")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.warehouseCount").value(1))
            .andExpect(jsonPath("$.productCount").value(1))
            .andExpect(jsonPath("$.skuCount").value(1))
            .andExpect(jsonPath("$.batchCount").value(1))
    }

    @Test
    fun `strict validation rejects unknown product reference`() {
        mockMvc.perform(
            multipart("/api/v1/imports/synthetic-foundation")
                .file(MockMultipartFile("file", "invalid.zip", "application/zip", packageBytes("PRD-MISSING")))
                .header("X-Tenant-ID", "TEN-IMPORT-DEMO")
                .param("mode", "VALIDATE_ONLY")
                .param("strict", "true")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.rejectedRows").value(2))
    }

    private fun packageBytes(productId: String): ByteArray {
        val files = linkedMapOf(
            "data/synthetic/reference/tenants.csv" to """
                tenant_id,tenant_name,vertical,currency,timezone,active
                TEN-IMPORT-DEMO,Import Demo,PHARMA,INR,Asia/Kolkata,true
            """.trimIndent(),
            "data/synthetic/reference/warehouses.csv" to """
                warehouse_id,tenant_id,warehouse_name,city,state,latitude,longitude,capacity_units,cold_chain_available,active
                WH-IMPORT,TEN-IMPORT-DEMO,Import Warehouse,Chennai,Tamil Nadu,13.0827,80.2707,1000,true,true
            """.trimIndent(),
            "data/synthetic/reference/products.csv" to """
                product_id,tenant_id,product_name,category,vertical,criticality,shelf_life_controlled,cold_chain_required,active
                PRD-IMPORT-001,TEN-IMPORT-DEMO,Import Product,MEDICINE,PHARMA,HIGH,true,false,true
            """.trimIndent(),
            "data/synthetic/reference/skus.csv" to """
                tenant_id,sku_id,product_id,sku_name,brand,pack_size,base_uom,unit_cost,selling_price,currency,minimum_safety_stock,reorder_multiple,default_shelf_life_days,fefo_required,active
                TEN-IMPORT-DEMO,SKU-IMPORT-001,$productId,Import SKU,Demo,10 TABLETS,STRIP,10.00,15.00,INR,50,10,365,true,true
            """.trimIndent(),
            "data/synthetic/transactions/batch_inventory.csv" to """
                snapshot_date,tenant_id,warehouse_id,sku_id,batch_number,manufacture_date,expiry_date,available_quantity,reserved_quantity,blocked_quantity,unit_cost,currency,storage_condition_code,last_movement_at
                2026-08-01,TEN-IMPORT-DEMO,WH-IMPORT,SKU-IMPORT-001,BATCH-1,2026-01-01,2027-01-01,100,10,0,10.00,INR,AMBIENT,2026-07-31T10:00:00Z
            """.trimIndent()
        )
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            files.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write((content + "\n").toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
