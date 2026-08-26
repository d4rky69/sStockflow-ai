from __future__ import annotations

import argparse
import csv
import io
import json
import zipfile
from pathlib import Path

REQUIRED = [
    "reference/tenants.csv",
    "reference/warehouses.csv",
    "reference/products.csv",
    "reference/skus.csv",
    "transactions/batch_inventory.csv",
]


def read_entries(path: Path) -> dict[str, bytes]:
    with zipfile.ZipFile(path) as archive:
        return {
            info.filename.replace("\\", "/"): archive.read(info)
            for info in archive.infolist()
            if not info.is_dir()
        }


def one(entries: dict[str, bytes], suffix: str) -> bytes:
    matches = [data for name, data in entries.items() if name.endswith(suffix)]
    if len(matches) != 1:
        raise ValueError(f"{suffix}: expected one match, found {len(matches)}")
    return matches[0]


def rows(data: bytes) -> list[dict[str, str]]:
    return list(csv.DictReader(io.StringIO(data.decode("utf-8-sig"))))


def validate(path: Path) -> dict[str, object]:
    entries = read_entries(path)
    data = {suffix: rows(one(entries, suffix)) for suffix in REQUIRED}
    tenants = {row["tenant_id"] for row in data["reference/tenants.csv"]}
    warehouses = {row["warehouse_id"]: row for row in data["reference/warehouses.csv"]}
    products = {row["product_id"]: row for row in data["reference/products.csv"]}
    skus = {row["sku_id"]: row for row in data["reference/skus.csv"]}
    batches = data["transactions/batch_inventory.csv"]

    errors: list[str] = []
    errors += [
        f"warehouse {row['warehouse_id']} has unknown tenant {row['tenant_id']}"
        for row in warehouses.values()
        if row["tenant_id"] not in tenants
    ]
    errors += [
        f"product {row['product_id']} has unknown tenant {row['tenant_id']}"
        for row in products.values()
        if row["tenant_id"] not in tenants
    ]
    errors += [
        f"sku {row['sku_id']} has unknown product {row['product_id']}"
        for row in skus.values()
        if row["product_id"] not in products
    ]
    errors += [
        f"sku {row['sku_id']} tenant does not match product"
        for row in skus.values()
        if row["product_id"] in products
        and row.get("tenant_id") != products[row["product_id"]]["tenant_id"]
    ]
    errors += [
        f"batch {row['batch_number']} has unknown warehouse {row['warehouse_id']}"
        for row in batches
        if row["warehouse_id"] not in warehouses
    ]
    errors += [
        f"batch {row['batch_number']} has unknown sku {row['sku_id']}"
        for row in batches
        if row["sku_id"] not in skus
    ]
    errors += [
        f"batch {row['batch_number']} tenant mismatch"
        for row in batches
        if row["warehouse_id"] in warehouses
        and row["sku_id"] in skus
        and not (
            row["tenant_id"] == warehouses[row["warehouse_id"]]["tenant_id"]
            == skus[row["sku_id"]]["tenant_id"]
        )
    ]

    result = {
        "valid": not errors,
        "errors": errors,
        "row_counts": {
            "tenants": len(tenants),
            "warehouses": len(warehouses),
            "products": len(products),
            "skus": len(skus),
            "batch_inventory": len(batches),
        },
    }
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("package_zip", type=Path)
    args = parser.parse_args()
    result = validate(args.package_zip)
    print(json.dumps(result, indent=2))
    if not result["valid"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
