import os

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, "data")
MODELS_DIR = os.path.join(BASE_DIR, "models")

os.makedirs(DATA_DIR, exist_ok=True)
os.makedirs(MODELS_DIR, exist_ok=True)

GATEWAY_API_URL = "http://localhost:5005/api/servers"
GATEWAY_UPDATE_URL = "http://localhost:5005/api/ml/update"
POLL_INTERVAL = 1.0
