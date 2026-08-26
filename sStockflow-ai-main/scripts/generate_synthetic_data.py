from __future__ import annotations
import argparse, csv, json, math, random
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
import yaml

CITIES = [
    ("CHENNAI", "Chennai", "Tamil Nadu", 13.0827, 80.2707),
    ("BENGALURU", "Bengaluru", "Karnataka", 12.9716, 77.5946),
    ("HYDERABAD", "Hyderabad", "Telangana", 17.3850, 78.4867),
    ("DELHI", "Delhi", "Delhi", 28.6139, 77.2090),
    ("MUMBAI", "Mumbai", "Maharashtra", 19.0760, 72.8777),
    ("PUNE", "Pune", "Maharashtra", 18.5204, 73.8567),
    ("KOLKATA", "Kolkata", "West Bengal", 22.5726, 88.3639),
    ("KOCHI", "Kochi", "Kerala", 9.9312, 76.2673),
    ("AHMEDABAD", "Ahmedabad", "Gujarat", 23.0225, 72.5714),
    ("JAIPUR", "Jaipur", "Rajasthan", 26.9124, 75.7873),
]
TENANTS = [
    ("TEN-ACME-PHARMA", "Acme Pharma Distribution", "PHARMA"),
    ("TEN-FRESH-MART", "FreshMart Wholesale", "SUPERMARKET"),
    ("TEN-URBAN-TRADE", "Urban Trade Distribution", "MERCHANDISE"),
]

def save_csv(path: Path, rows: list[dict], fields: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader(); writer.writerows(rows)

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--config', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    config = yaml.safe_load(Path(args.config).read_text(encoding='utf-8'))
    out = Path(args.output); out.mkdir(parents=True, exist_ok=True)
    rng = random.Random(config['dataset']['random_seed'])
    warehouse_count = int(config['scale']['warehouses'])
    retailer_count = int(config['scale']['retailers'])
    sku_count = int(config['scale']['skus'])
    start = date.fromisoformat(str(config['dataset']['history_start_date']))
    end = date.fromisoformat(str(config['dataset']['history_end_date']))
    as_of = date.fromisoformat(str(config['dataset']['forecast_as_of_date']))

    tenants = [{"tenant_id":i,"tenant_name":n,"vertical":v,"currency":"INR","timezone":"Asia/Kolkata","active":"true"} for i,n,v in TENANTS]
    warehouses=[]
    for idx in range(warehouse_count):
        code, city, state, lat, lon = CITIES[idx % len(CITIES)]
        pharma_limit = math.ceil(warehouse_count * 0.4)
        supermarket_limit = pharma_limit + math.ceil(warehouse_count * 0.3)
        tenant_index = 0 if idx < pharma_limit else (1 if idx < supermarket_limit else 2)
        tenant = TENANTS[tenant_index]
        warehouses.append({"warehouse_id":f"WH-{code}","tenant_id":tenant[0],"warehouse_name":f"{city} Regional Warehouse","city":city,"state":state,"latitude":lat,"longitude":lon,"capacity_units":rng.randrange(250000,550001,10000),"cold_chain_available":str(tenant[2]=='PHARMA').lower(),"active":"true"})
    retailers=[]
    for idx in range(retailer_count):
        wh=warehouses[idx % len(warehouses)]
        rtype={"PHARMA":"PHARMACY","SUPERMARKET":"SUPERMARKET","MERCHANDISE":"GENERAL_STORE"}[next(v for t,n,v in TENANTS if t==wh['tenant_id'])]
        retailers.append({"retailer_id":f"RET-{idx+1:03d}","tenant_id":wh['tenant_id'],"retailer_name":f"Demo Retailer {idx+1:03d}","retailer_type":rtype,"warehouse_id":wh['warehouse_id'],"city":wh['city'],"region":"INDIA","credit_days":rng.choice([15,30,45]),"active":"true"})
    skus=[]
    profiles=['STABLE','SEASONAL','PROMOTION_SENSITIVE','WEATHER_SENSITIVE','INTERMITTENT','ERRATIC']
    vertical_counts=config['vertical_distribution']
    verticals=[]
    for vertical,count in vertical_counts.items(): verticals += [vertical]*int(count)
    verticals=(verticals+['PHARMA']*sku_count)[:sku_count]
    for idx,vertical in enumerate(verticals):
        shelf=vertical!='MERCHANDISE'
        skus.append({"sku_id":f"SKU-{vertical[:3]}-{idx+1:03d}","tenant_id":TENANTS[['PHARMA','SUPERMARKET','MERCHANDISE'].index(vertical)][0],"sku_name":f"{vertical.title()} Demo SKU {idx+1:03d}","vertical":vertical,"category":"MEDICINE" if vertical=='PHARMA' else ('GROCERY' if vertical=='SUPERMARKET' else 'GENERAL_MERCHANDISE'),"base_uom":"UNIT","unit_cost":round(rng.uniform(10,700),2),"selling_price":round(rng.uniform(30,1200),2),"currency":"INR","minimum_safety_stock":rng.randrange(20,501,10),"reorder_multiple":rng.choice([5,10,20,50,100]),"default_shelf_life_days":rng.choice([180,365,540,730]) if shelf else '',"fefo_required":str(shelf).lower(),"demand_profile":rng.choice(profiles),"active":"true"})
    # Replace first few with judge-facing stable IDs
    if skus:
        skus[0].update({"sku_id":"SKU-PARA-650","sku_name":"Paracetamol 650mg Tablet","unit_cost":18.50,"selling_price":25.00,"minimum_safety_stock":500,"reorder_multiple":100,"default_shelf_life_days":730,"demand_profile":"STABLE"})
    sales=[]
    current=start
    while current<=end:
        day_idx=(current-start).days
        for sku in skus:
            tenant_wh=[w for w in warehouses if w['tenant_id']==sku['tenant_id']]
            for wh in tenant_wh:
                base=8+(int(sku['sku_id'][-3:]) if sku['sku_id'][-3:].isdigit() else 10)%30
                weekly=1+0.12*math.sin(2*math.pi*day_idx/7)
                seasonal=1+0.18*math.sin(2*math.pi*day_idx/365)
                noise=max(rng.gauss(1,config['sales']['random_noise_stddev']),0.2)
                true=max(round(base*weekly*seasonal*noise),0)
                stockout=(sku['sku_id']=='SKU-PARA-650' and wh['warehouse_id']=='WH-BENGALURU' and current> end-timedelta(days=12)) or rng.random()<0.01
                fulfilled=round(true*rng.uniform(.45,.85)) if stockout else true
                lost=max(true-fulfilled,0)
                sales.append({"sales_date":current.isoformat(),"tenant_id":sku['tenant_id'],"warehouse_id":wh['warehouse_id'],"retailer_id":"","sku_id":sku['sku_id'],"ordered_quantity":true,"fulfilled_quantity":fulfilled,"sales_quantity":fulfilled,"return_quantity":0,"lost_sales_quantity":lost,"unit_selling_price":sku['selling_price'],"promotion_id":"","stockout_flag":str(stockout).lower()})
        current+=timedelta(days=1)
    batches=[]
    for sku in skus:
        for wh in [w for w in warehouses if w['tenant_id']==sku['tenant_id']]:
            for batch_idx in range(rng.choice([1,2,3])):
                expiry=''
                if sku['fefo_required']=='true': expiry=(as_of+timedelta(days=rng.randint(25,600))).isoformat()
                qty=rng.randint(100,3000)
                if sku['sku_id']=='SKU-PARA-650' and wh['warehouse_id']=='WH-CHENNAI' and batch_idx==0:
                    qty=2450; expiry=(as_of+timedelta(days=45)).isoformat(); batch='B2456'
                else: batch=f"B{rng.randint(1000,9999)}"
                batches.append({"snapshot_date":as_of.isoformat(),"tenant_id":sku['tenant_id'],"warehouse_id":wh['warehouse_id'],"sku_id":sku['sku_id'],"batch_number":batch,"manufacture_date":(as_of-timedelta(days=180)).isoformat() if expiry else '',"expiry_date":expiry,"available_quantity":qty,"reserved_quantity":rng.randint(0,min(qty,100)),"blocked_quantity":0,"unit_cost":sku['unit_cost'],"currency":"INR","storage_condition_code":"AMBIENT","last_movement_at":datetime.combine(as_of-timedelta(days=rng.randint(0,20)),datetime.min.time(),tzinfo=timezone.utc).isoformat().replace('+00:00','Z')})
    pos=[{"purchase_order_id":"PO-2026-007823","tenant_id":"TEN-ACME-PHARMA","supplier_id":"SUP-001","warehouse_id":"WH-BENGALURU","sku_id":"SKU-PARA-650","order_date":(as_of-timedelta(days=2)).isoformat(),"expected_delivery_date":(as_of+timedelta(days=10)).isoformat(),"ordered_quantity":1500,"received_quantity":0,"open_quantity":1500,"unit_purchase_price":18.25,"currency":"INR","status":"OPEN","buyer_id":"USR-BUYER-01"}]
    routes=[]
    for a in warehouses:
        for b in warehouses:
            if a['tenant_id']==b['tenant_id'] and a['warehouse_id']!=b['warehouse_id']:
                distance=round(math.sqrt((float(a['latitude'])-float(b['latitude']))**2+(float(a['longitude'])-float(b['longitude']))**2)*111,1)
                routes.append({"source_warehouse_id":a['warehouse_id'],"destination_warehouse_id":b['warehouse_id'],"distance_km":distance,"travel_time_hours":round(distance/50+1,1),"fixed_transfer_cost":round(7000+distance*20,2),"cost_per_unit":0.6,"cold_chain_supported":"true","active":"true"})
    save_csv(out/'reference/tenants.csv',tenants,list(tenants[0]))
    save_csv(out/'reference/warehouses.csv',warehouses,list(warehouses[0]))
    save_csv(out/'reference/retailers.csv',retailers,list(retailers[0]))
    save_csv(out/'reference/skus.csv',skus,list(skus[0]))
    save_csv(out/'transactions/sales_history.csv',sales,list(sales[0]))
    save_csv(out/'transactions/batch_inventory.csv',batches,list(batches[0]))
    save_csv(out/'transactions/open_purchase_orders.csv',pos,list(pos[0]))
    save_csv(out/'reference/warehouse_routes.csv',routes,list(routes[0]))
    manifest={"dataset_name":config['dataset']['name'],"dataset_version":config['dataset']['version'],"generated_at":datetime.now(timezone.utc).isoformat(),"random_seed":config['dataset']['random_seed'],"forecast_as_of_date":as_of.isoformat(),"counts":{"tenants":len(tenants),"warehouses":len(warehouses),"retailers":len(retailers),"skus":len(skus),"sales_history":len(sales),"batch_inventory":len(batches)}}
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    print(json.dumps(manifest,indent=2))
if __name__=='__main__': main()
