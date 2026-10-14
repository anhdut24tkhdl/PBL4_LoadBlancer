import sys
import json
import urllib.request

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')

from config import GATEWAY_UPDATE_URL

def send_ml_update(updates):
    payload = json.dumps(updates).encode('utf-8')
    req = urllib.request.Request(
        GATEWAY_UPDATE_URL,
        data=payload,
        headers={'Content-Type': 'application/json', 'User-Agent': 'ML-Brain-Bridge'},
        method='POST'
    )
    try:
        with urllib.request.urlopen(req, timeout=3) as resp:
            body = resp.read().decode('utf-8')
            return json.loads(body)
    except Exception as e:
        print(f'[Bridge] Failed to send ML update to Gateway: {e}')
        return None

if __name__ == '__main__':
    test_data = [
        {'name': 'Server 1', 'mlWeight': 0.95, 'anomalyScore': 0.02},
        {'name': 'Server 2', 'mlWeight': 1.05, 'anomalyScore': 0.01},
        {'name': 'Server 3', 'mlWeight': 0.50, 'anomalyScore': 0.88}
    ]
    print(f'[*] Testing ML Bridge sending update to {GATEWAY_UPDATE_URL}...')
    res = send_ml_update(test_data)
    print('[Bridge] Response from Gateway:', res)
