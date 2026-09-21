FROM python:3.12-slim
WORKDIR /srv
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1
# libgomp1 provides libgomp.so.1, which the compiled LightGBM booster dlopens at import time;
# without it lightgbm fails to import and the service silently falls back to the stub.
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
COPY models ./models
RUN useradd --create-home --uid 10001 appuser && chown -R appuser /srv
USER appuser
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
