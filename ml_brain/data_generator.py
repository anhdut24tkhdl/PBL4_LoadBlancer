import os
import sys
import csv
import math
import random
import datetime

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

from config import DATA_DIR

def generate_traffic_timeseries(days=7, interval_seconds=60):
    filepath = os.path.join(DATA_DIR, 'train_traffic_timeseries.csv')
    start_time = datetime.datetime(2026, 1, 1, 0, 0, 0)
    total_steps = int((days * 24 * 3600) / interval_seconds)
    
    rows = []
    headers = ['timestamp', 'hour', 'day_of_week', 'request_count', 'avg_latency_ms', 'is_spike']
    
    for step in range(total_steps):
        cur_time = start_time + datetime.timedelta(seconds=step * interval_seconds)
        hour = cur_time.hour
        dow = cur_time.weekday()
        
        base_rate = 50 + 40 * math.sin((hour - 6) / 24 * 2 * math.pi)
        if dow in [5, 6]:
            base_rate *= 1.25
        
        noise = random.gauss(0, 5)
        req_count = max(5, int(base_rate + noise))
        
        is_spike = 0
        if random.random() < 0.015:
            spike_multiplier = random.uniform(2.5, 5.0)
            req_count = int(req_count * spike_multiplier)
            is_spike = 1
        
        latency = 20.0 + (req_count / 10.0) * 8.0 + random.uniform(-3, 3)
        if is_spike:
            latency += random.uniform(50, 150)
            
        rows.append([
            cur_time.strftime('%Y-%m-%d %H:%M:%S'),
            hour,
            dow,
            req_count,
            round(latency, 2),
            is_spike
        ])
    
    with open(filepath, mode='w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        writer.writerow(headers)
        writer.writerows(rows)
        
    print(f'[Generator] Created LSTM Traffic Dataset: {filepath} ({len(rows)} records)')

def generate_anomaly_dataset(num_samples=10000):
    filepath = os.path.join(DATA_DIR, 'train_anomaly_dataset.csv')
    headers = [
        'timestamp', 'server_id', 'cpu_usage', 'ram_usage', 
        'latency_ms', 'active_connections', 'error_rate', 'is_anomaly', 'anomaly_type'
    ]
    
    rows = []
    base_time = datetime.datetime.now()
    
    for i in range(num_samples):
        t = base_time + datetime.timedelta(seconds=i * 2)
        server_id = f'Server_{(i % 3) + 1}'
        
        if random.random() < 0.85:
            cpu = random.uniform(15.0, 65.0)
            ram = random.uniform(30.0, 70.0)
            latency = random.uniform(10.0, 80.0)
            conn = random.randint(2, 25)
            err_rate = random.uniform(0.0, 0.02)
            is_anomaly = 0
            anomaly_type = 'NORMAL'
        else:
            is_anomaly = 1
            anomaly_choice = random.choice(['MEMORY_LEAK', 'IO_DEGRADATION', 'CONNECTION_LEAK', 'CPU_THROTTLING'])
            anomaly_type = anomaly_choice
            
            if anomaly_choice == 'MEMORY_LEAK':
                cpu = random.uniform(20.0, 45.0)
                ram = random.uniform(88.0, 99.5)
                latency = random.uniform(80.0, 200.0)
                conn = random.randint(10, 30)
                err_rate = random.uniform(0.05, 0.20)
            elif anomaly_choice == 'IO_DEGRADATION':
                cpu = random.uniform(10.0, 30.0)
                ram = random.uniform(30.0, 50.0)
                latency = random.uniform(500.0, 2500.0)
                conn = random.randint(20, 45)
                err_rate = random.uniform(0.10, 0.40)
            elif anomaly_choice == 'CONNECTION_LEAK':
                cpu = random.uniform(30.0, 60.0)
                ram = random.uniform(50.0, 75.0)
                latency = random.uniform(150.0, 600.0)
                conn = random.randint(70, 150)
                err_rate = random.uniform(0.15, 0.50)
            else:
                cpu = random.uniform(92.0, 100.0)
                ram = random.uniform(60.0, 80.0)
                latency = random.uniform(300.0, 1200.0)
                conn = random.randint(30, 60)
                err_rate = random.uniform(0.08, 0.35)
                
        rows.append([
            t.strftime('%Y-%m-%d %H:%M:%S'),
            server_id,
            round(cpu, 2),
            round(ram, 2),
            round(latency, 2),
            conn,
            round(err_rate, 4),
            is_anomaly,
            anomaly_type
        ])
        
    with open(filepath, mode='w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        writer.writerow(headers)
        writer.writerows(rows)
        
    print(f'[Generator] Created Anomaly Dataset: {filepath} ({len(rows)} records)')

if __name__ == '__main__':
    print('[*] Generating synthetic datasets for Phase 1...')
    generate_traffic_timeseries(days=7, interval_seconds=60)
    generate_anomaly_dataset(num_samples=10000)
    print('[*] Synthetic dataset generation completed successfully!')
