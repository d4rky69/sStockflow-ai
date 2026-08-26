from __future__ import annotations
import argparse, csv, json
from pathlib import Path

def load(path: Path) -> list[dict]:
    with path.open(encoding='utf-8', newline='') as f: return list(csv.DictReader(f))

def main() -> None:
    p=argparse.ArgumentParser(); p.add_argument('--dataset',required=True); args=p.parse_args(); root=Path(args.dataset)
    required=['reference/tenants.csv','reference/warehouses.csv','reference/retailers.csv','reference/skus.csv','transactions/sales_history.csv','transactions/batch_inventory.csv','transactions/open_purchase_orders.csv','reference/warehouse_routes.csv','manifest.json']
    errors=[]
    for rel in required:
        if not (root/rel).exists(): errors.append(f'Missing {rel}')
    if errors:
        print(json.dumps({'valid':False,'errors':errors},indent=2)); raise SystemExit(1)
    tenants={r['tenant_id'] for r in load(root/'reference/tenants.csv')}
    warehouses=load(root/'reference/warehouses.csv'); wh_ids={r['warehouse_id'] for r in warehouses}; wh_tenant={r['warehouse_id']:r['tenant_id'] for r in warehouses}
    skus=load(root/'reference/skus.csv'); sku_ids={r['sku_id'] for r in skus}; sku_tenant={r['sku_id']:r['tenant_id'] for r in skus}
    checks=[('sales',load(root/'transactions/sales_history.csv')),('batches',load(root/'transactions/batch_inventory.csv')),('purchase_orders',load(root/'transactions/open_purchase_orders.csv'))]
    for name,rows in checks:
        for idx,row in enumerate(rows,2):
            if row.get('tenant_id') not in tenants: errors.append(f'{name} row {idx}: invalid tenant')
            if row.get('warehouse_id') not in wh_ids: errors.append(f'{name} row {idx}: invalid warehouse')
            if row.get('sku_id') not in sku_ids: errors.append(f'{name} row {idx}: invalid SKU')
            if row.get('warehouse_id') in wh_tenant and row.get('tenant_id')!=wh_tenant[row['warehouse_id']]: errors.append(f'{name} row {idx}: cross-tenant warehouse')
            if row.get('sku_id') in sku_tenant and row.get('tenant_id')!=sku_tenant[row['sku_id']]: errors.append(f'{name} row {idx}: cross-tenant SKU')
            if len(errors)>100: break
    report={'valid':not errors,'error_count':len(errors),'errors':errors[:100],'row_counts':{name:len(rows) for name,rows in checks},'tenant_count':len(tenants),'warehouse_count':len(wh_ids),'sku_count':len(sku_ids)}
    (root/'validation_report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report,indent=2))
    if errors: raise SystemExit(1)
if __name__=='__main__': main()
