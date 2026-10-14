import os
import sys
import csv
import time
import json
import urllib.request

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")

from config import GATEWAY_API_URL, DATA_DIR, POLL_INTERVAL

CSV_FILE = os.path.join(DATA_DIR, "live_telemetry.csv")
CSV_HEADERS = [
    "timestamp", "server_name", "host", "alive",
    "cpu", "ram", "latency", "connections",
    "anomaly_score", "ml_weight", "total_requests", "failed_requests"
]

def init_csv():
    if not os.path.exists(CSV_FILE):
        with open(CSV_FILE, mode="w", newline="", encoding="utf-8") as f:
            writer = csv.writer(f)
            writer.writerow(CSV_HEADERS)

def fetch_servers():
    try:
        req = urllib.request.Request(GATEWAY_API_URL, headers={"User-Agent": "ML-Brain-Collector"})
        with urllib.request.urlopen(req, timeout=3) as resp:
            if resp.status == 200:
                data = resp.read().decode("utf-8")
                return json.loads(data)
    except Exception as e:
        print(f"[Collector] Connection error ({GATEWAY_API_URL}): {e}")
    return None

def log_telemetry():
    init_csv()
    servers = fetch_servers()
    if not servers:
        return 0

    now = time.strftime("%Y-%m-%d %H:%M:%S")
    rows = []
    for s in servers:
        rows.append([
            now,
            s.get("name", "Unknown"),
            s.get("host", ""),
            1 if s.get("alive", True) else 0,
            s.get("cpu", 0.0),
            s.get("ram", 0.0),
            s.get("latency", 0.0),
            s.get("connections", 0),
            s.get("anomalyScore", 0.0),
            s.get("mlWeight", 1.0),
            s.get("totalRequests", 0),
            s.get("failedRequests", 0)
        ])

    with open(CSV_FILE, mode="a", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerows(rows)
    return len(rows)

def run_collector():
    print(f"[*] Starting telemetry collector: {GATEWAY_API_URL} -> {CSV_FILE}")
    init_csv()
    while True:
        count = log_telemetry()
        if count > 0:
            print(f"[Collector] Logged {count} telemetry records ({time.strftime('%H:%M:%S')})")
        time.sleep(POLL_INTERVAL)

if __name__ == "__main__":
    run_collector()
