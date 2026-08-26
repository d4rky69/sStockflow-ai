package com.stockflow.imports.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SalesImportControllerTest(
    @Autowired private val mockMvc: MockMvc
) {
    @Test
    fun `sales package validates imports and feeds analytics`() {
        val packageBytes = packageBytes()
        mockMvc.perform(
            multipart("/api/v1/imports/synthetic-sales")
                .file(MockMultipartFile("file", "sales.zip", "application/zip", packageBytes))
                .header("X-Tenant-ID", "TEN-ACME-PHARMA")
                .param("mode", "VALIDATE_ONLY")
                .param("strict", "true")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("VALIDATED"))
            .andExpect(jsonPath("$.acceptedRows").value(3))
            .andExpect(jsonPath("$.rejectedRows").value(0))

        mockMvc.perform(
            multipart("/api/v1/imports/synthetic-sales")
                .file(MockMultipartFile("file", "sales.zip", "application/zip", packageBytes))
                .header("X-Tenant-ID", "TEN-ACME-PHARMA")
                .param("mode", "UPSERT")
                .param("strict", "true")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.acceptedRows").value(3))

        mockMvc.perform(
            get("/api/v1/analytics/sales/summary")
                .header("X-Tenant-ID", "TEN-ACME-PHARMA")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.transactionRows").value(2))
            .andExpect(jsonPath("$.orderedQuantity").value(35))
            .andExpect(jsonPath("$.salesQuantity").value(30))
            .andExpect(jsonPath("$.lostSalesQuantity").value(5))
            .andExpect(jsonPath("$.stockoutRows").value(1))
    }

    private fun packageBytes(): ByteArray {
        val files = linkedMapOf(
            "data/synthetic/reference/retailers.csv" to """
                retailer_id,tenant_id,retailer_name,retailer_type,warehouse_id,city,region,credit_days,active
                RET-TEST-001,TEN-ACME-PHARMA,Test Pharmacy,PHARMACY,WH-CHENNAI,Chennai,SOUTH,30,true
            """.trimIndent(),
            "data/synthetic/transactions/sales_history.csv" to """
                sales_date,tenant_id,warehouse_id,retailer_id,sku_id,ordered_quantity,fulfilled_quantity,sales_quantity,return_quantity,lost_sales_quantity,unit_selling_price,promotion_id,stockout_flag
                2026-07-01,TEN-ACME-PHARMA,WH-CHENNAI,RET-TEST-001,SKU-PARA-650,20,20,20,0,0,100.00,,false
                2026-07-02,TEN-ACME-PHARMA,WH-CHENNAI,RET-TEST-001,SKU-PARA-650,15,10,10,0,5,100.00,,true
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
