from __future__ import annotations

import argparse
import csv
import io
import json
import zipfile
from pathlib import Path

REPAIRS = {
    "SKU-PARA-650": "PRD-MED-004",
    "SKU-AMOX-500": "PRD-ANT-001",
    "SKU-MILK-1L": "PRD-DAIRY-004",
    "SKU-BACKPACK-01": "PRD-SCH-004",
    "SKU-ORS-PWD": "PRD-MED-008",
    "SKU-INSU-GL": "PRD-INS-003",
}

REQUIRED = {
    "tenants": "reference/tenants.csv",
    "warehouses": "reference/warehouses.csv",
    "products": "reference/products.csv",
    "skus": "reference/skus.csv",
    "batch_inventory": "transactions/batch_inventory.csv",
}


def normalized_entries(source: Path) -> dict[str, bytes]:
    with zipfile.ZipFile(source) as archive:
        return {
            info.filename.replace("\\", "/").lstrip("/"): archive.read(info)
            for info in archive.infolist()
            if not info.is_dir()
        }


def required_entry(entries: dict[str, bytes], suffix: str) -> bytes:
    matches = [value for name, value in entries.items() if name.endswith(suffix)]
    if len(matches) != 1:
        raise ValueError(f"Expected one file ending with {suffix!r}; found {len(matches)}")
    return matches[0]


def read_csv(data: bytes) -> tuple[list[str], list[dict[str, str]]]:
    text = data.decode("utf-8-sig")
    reader = csv.DictReader(io.StringIO(text))
    if not reader.fieldnames:
        raise ValueError("CSV header is missing")
    return list(reader.fieldnames), list(reader)


def write_csv(fieldnames: list[str], rows: list[dict[str, str]]) -> bytes:
    output = io.StringIO(newline="")
    writer = csv.DictWriter(output, fieldnames=fieldnames, lineterminator="\n")
    writer.writeheader()
    writer.writerows(rows)
    return output.getvalue().encode("utf-8")


def prepare(source: Path, destination: Path) -> dict[str, object]:
    entries = normalized_entries(source)
    selected = {key: required_entry(entries, suffix) for key, suffix in REQUIRED.items()}

    product_fields, products = read_csv(selected["products"])
    product_tenant = {row["product_id"]: row["tenant_id"] for row in products}

    sku_fields, skus = read_csv(selected["skus"])
    repairs: list[dict[str, str]] = []
    for row in skus:
        sku_id = row["sku_id"]
        if sku_id in REPAIRS:
            repairs.append(
                {
                    "sku_id": sku_id,
                    "old_product_id": row["product_id"],
                    "new_product_id": REPAIRS[sku_id],
                }
            )
            row["product_id"] = REPAIRS[sku_id]
        row["tenant_id"] = product_tenant.get(row["product_id"], "")

    unresolved_products = sorted(
        {row["product_id"] for row in skus if row["product_id"] not in product_tenant}
    )
    blank_tenants = sorted({row["sku_id"] for row in skus if not row["tenant_id"]})
    if unresolved_products or blank_tenants:
        raise ValueError(
            f"Prepared data remains invalid: unresolved_products={unresolved_products}, "
            f"blank_tenant_skus={blank_tenants}"
        )

    tenant_fields, tenants = read_csv(selected["tenants"])
    warehouse_fields, warehouses = read_csv(selected["warehouses"])
    batch_fields, batches = read_csv(selected["batch_inventory"])

    warehouse_ids = {row["warehouse_id"] for row in warehouses}
    warehouse_tenant = {row["warehouse_id"]: row["tenant_id"] for row in warehouses}
    sku_ids = {row["sku_id"] for row in skus}
    sku_tenant = {row["sku_id"]: row["tenant_id"] for row in skus}
    bad_batch_warehouses = sorted(
        {row["warehouse_id"] for row in batches if row["warehouse_id"] not in warehouse_ids}
    )
    bad_batch_skus = sorted({row["sku_id"] for row in batches if row["sku_id"] not in sku_ids})
    if bad_batch_warehouses or bad_batch_skus:
        raise ValueError(
            f"Batch references remain invalid: warehouses={bad_batch_warehouses}, skus={bad_batch_skus}"
        )

    invalid_cross_tenant_batches = [
        row
        for row in batches
        if not (
            row["tenant_id"] == warehouse_tenant[row["warehouse_id"]]
            == sku_tenant[row["sku_id"]]
        )
    ]
    batches = [
        row
        for row in batches
        if row["tenant_id"] == warehouse_tenant[row["warehouse_id"]]
        == sku_tenant[row["sku_id"]]
    ]

    quality_report = {
        "dataset": "stockflow-synthetic-foundation-phase2-ready",
        "source": source.name,
        "purpose": "Phase 2 Increment 2 controlled foundation import",
        "included_files": list(REQUIRED.values()),
        "row_counts": {
            "tenants": len(tenants),
            "warehouses": len(warehouses),
            "products": len(products),
            "skus": len(skus),
            "batch_inventory": len(batches),
        },
        "tenant_counts": {
            tenant_id: {
                "warehouses": sum(row["tenant_id"] == tenant_id for row in warehouses),
                "products": sum(row["tenant_id"] == tenant_id for row in products),
                "skus": sum(row["tenant_id"] == tenant_id for row in skus),
                "batch_inventory": sum(row["tenant_id"] == tenant_id for row in batches),
            }
            for tenant_id in [row["tenant_id"] for row in tenants]
        },
        "repairs": {
            "sku_product_reference_repairs": repairs,
            "tenant_id_added_to_skus": True,
            "nullable_shelf_life_and_expiry_preserved": True,
            "cross_tenant_batch_rows_removed": len(invalid_cross_tenant_batches),
            "removed_batch_numbers": [row["batch_number"] for row in invalid_cross_tenant_batches],
        },
        "validation": {
            "unresolved_product_references": 0,
            "blank_sku_tenant_ids": 0,
            "invalid_batch_warehouse_references": 0,
            "invalid_batch_sku_references": 0,
            "cross_tenant_batch_rows": 0,
        },
        "known_limitations": [
            "Sales history, signals, purchase orders, movements, returns and dispatches remain in the original dataset for later increments.",
            "The source dataset has one inventory snapshot date: 2026-07-01.",
            "Promotion IDs are not linked from sales_history in the source dataset.",
            "Warehouse transfer movements are not present in the source dataset.",
        ],
    }
    manifest = {
        "package_name": "stockflow-synthetic-foundation-phase2-ready",
        "version": "1.0.0",
        "target_endpoint": "/api/v1/imports/synthetic-foundation",
        "files": list(REQUIRED.values()),
        "tenants": [row["tenant_id"] for row in tenants],
        "usage": {
            "validate": "mode=VALIDATE_ONLY&strict=true",
            "import": "mode=UPSERT&strict=true",
        },
    }

    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("data/synthetic/reference/tenants.csv", write_csv(tenant_fields, tenants))
        archive.writestr(
            "data/synthetic/reference/warehouses.csv", write_csv(warehouse_fields, warehouses)
        )
        archive.writestr("data/synthetic/reference/products.csv", write_csv(product_fields, products))
        archive.writestr(
            "data/synthetic/reference/skus.csv", write_csv(["tenant_id", *sku_fields], skus)
        )
        archive.writestr(
            "data/synthetic/transactions/batch_inventory.csv", write_csv(batch_fields, batches)
        )
        archive.writestr(
            "data/synthetic/quality_report.json", json.dumps(quality_report, indent=2) + "\n"
        )
        archive.writestr("data/synthetic/manifest.json", json.dumps(manifest, indent=2) + "\n")

    return quality_report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source_zip", type=Path)
    parser.add_argument("output_zip", type=Path)
    args = parser.parse_args()
    report = prepare(args.source_zip, args.output_zip)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
