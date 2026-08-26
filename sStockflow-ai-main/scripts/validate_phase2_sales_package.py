#!/usr/bin/env python3
"""Validate the prepared Phase 2 sales package against the imported foundation package."""
from __future__ import annotations

import csv
import io
import json
import sys
import zipfile
from collections import Counter
from pathlib import Path


def find_entry(zf: zipfile.ZipFile, suffix: str) -> str:
    names = [name for name in zf.namelist() if name.replace("\\", "/").endswith(suffix)]
    if len(names) != 1:
        raise ValueError(f"Expected exactly one '*{suffix}' entry, found {len(names)}")
    return names[0]


def read_csv(zf: zipfile.ZipFile, suffix: str) -> list[dict[str, str]]:
    name = find_entry(zf, suffix)
    text = zf.read(name).decode("utf-8-sig")
    return list(csv.DictReader(io.StringIO(text)))


def main() -> int:
    if len(sys.argv) not in (2, 3):
        print("Usage: python scripts/validate_phase2_sales_package.py SALES_ZIP [FOUNDATION_ZIP]")
        return 2

    sales_path = Path(sys.argv[1])
    foundation_path = Path(sys.argv[2]) if len(sys.argv) == 3 else Path(
        "data/import/StockFlow_AI_Synthetic_Foundation_Phase2_Ready.zip"
    )
    errors: list[str] = []

    with zipfile.ZipFile(foundation_path) as foundation_zip:
        warehouses = read_csv(foundation_zip, "reference/warehouses.csv")
        skus = read_csv(foundation_zip, "reference/skus.csv")
    warehouse_tenant = {row["warehouse_id"]: row["tenant_id"] for row in warehouses}
    sku_tenant = {row["sku_id"]: row["tenant_id"] for row in skus}

    with zipfile.ZipFile(sales_path) as sales_zip:
        retailers = read_csv(sales_zip, "reference/retailers.csv")
        sales = read_csv(sales_zip, "transactions/sales_history.csv")

    retailer_tenant = {row["retailer_id"]: row["tenant_id"] for row in retailers}
    for line, row in enumerate(retailers, start=2):
        if warehouse_tenant.get(row["warehouse_id"]) != row["tenant_id"]:
            errors.append(f"retailers.csv:{line}: warehouse/tenant mismatch")

    seen: set[tuple[str, ...]] = set()
    for line, row in enumerate(sales, start=2):
        tenant = row["tenant_id"]
        if warehouse_tenant.get(row["warehouse_id"]) != tenant:
            errors.append(f"sales_history.csv:{line}: warehouse/tenant mismatch")
        if retailer_tenant.get(row["retailer_id"]) != tenant:
            errors.append(f"sales_history.csv:{line}: retailer/tenant mismatch")
        if sku_tenant.get(row["sku_id"]) != tenant:
            errors.append(f"sales_history.csv:{line}: sku/tenant mismatch")
        key = (tenant, row["sales_date"], row["warehouse_id"], row["retailer_id"], row["sku_id"])
        if key in seen:
            errors.append(f"sales_history.csv:{line}: duplicate natural key")
        seen.add(key)
        for field in (
            "ordered_quantity", "fulfilled_quantity", "sales_quantity",
            "return_quantity", "lost_sales_quantity"
        ):
            try:
                if int(row[field]) < 0:
                    raise ValueError
            except ValueError:
                errors.append(f"sales_history.csv:{line}: invalid {field}")

    result = {
        "valid": not errors,
        "errors": errors[:100],
        "error_count": len(errors),
        "row_counts": {
            "retailers": len(retailers),
            "sales_history": len(sales),
        },
        "tenant_counts": {
            "retailers": dict(Counter(row["tenant_id"] for row in retailers)),
            "sales_history": dict(Counter(row["tenant_id"] for row in sales)),
        },
    }
    print(json.dumps(result, indent=2))
    return 0 if result["valid"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
