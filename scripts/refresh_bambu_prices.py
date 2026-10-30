import json, urllib.request, datetime, pathlib
BASE = "https://us.store.bambulab.com"
PRODUCTS = {"pla": ["pla-basic-filament"], "petg": ["petg-hf", "petg-hf-filament", "petg-basic"], "silk": ["pla-silk-upgrade"], "tpu": ["tpu-for-ams", "tpu-95a-hf"]}
def lookup(handle):
    data = None
    errors = []
    for host in ("https://us.store.bambulab.com", "https://bambulab-us.myshopify.com"):
        for suffix in (".js", ".json"):
            try:
                req = urllib.request.Request(host + "/products/" + handle + suffix, headers={"User-Agent":"Mozilla/5.0", "Accept":"application/json"})
                with urllib.request.urlopen(req, timeout=20) as response:
                    payload = json.load(response)
                candidate = payload.get("product", payload)
                if candidate.get("variants"):
                    data = candidate
                    break
            except Exception as exc:
                errors.append(str(exc))
        if data is not None:
            break
    if data is None:
        raise ValueError("Store product data unavailable: " + "; ".join(errors[-2:]))
    values = []
    for v in data.get("variants", []):
        name = " ".join(str(v.get(k,"")) for k in ("title","option1","option2","option3")).lower()
        if "refill" in name or not ("spool" in name or "with spool" in name):
            continue
        if any(x in name for x in ("500g", "0.5kg", "2kg")):
            continue
        cents = v.get("price")
        if isinstance(cents, str):
            try: cents = float(cents)
            except ValueError: cents = None
        if isinstance(cents, (int,float)) and 1000 <= cents <= 15000:
            values.append(round(cents / 100, 2))
    if not values:
        raise ValueError("No matching spool variant; available options: " + repr([v.get("title") for v in data.get("variants", [])][:20]))
    return {"usd":min(values), "product":data.get("title",handle), "source":BASE + "/products/" + handle}
path = pathlib.Path("bambu-prices.json")
old = json.loads(path.read_text())
prices = dict(old.get("prices",{}))
for material, handles in PRODUCTS.items():
    for handle in handles:
        try:
            prices[material] = lookup(handle)
            print(material, prices[material]["usd"])
            break
        except Exception as e:
            print(material, handle, str(e))
    else:
        print("Retaining last verified price for",material)
if not any(isinstance(v,dict) and isinstance(v.get("usd"),(int,float)) for v in prices.values()):
    raise SystemExit("No verified prices obtained; not publishing estimates")
new = {"currency":"USD","checkedAt":datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d"),"prices":prices,"note":"Official Bambu US single-spool prices, not bulk pricing. Some products may retain last verified value if unavailable."}
path.write_text(json.dumps(new,indent=2)+"\n")
