# 🧠 Ollama Setup Guide (Local AI for AI Reliability Copilot)

## 🎯 Overview
This guide explains how to install and run Ollama locally and use it as the AI backend in the project.

---

## ✅ Why use Ollama?
- Runs locally (no API cost)
- No API key required
- Works offline
- Ideal for development and testing

---

## ⚙️ Requirements
- Windows / Mac / Linux
- Minimum ~8GB RAM recommended
- At least ~5GB free disk space for models

---

## 🚀 Step 1 — Install Ollama
Download from:
https://ollama.com/download

Install and open a terminal.

Verify installation:

```bash
ollama --version
```

---

## 🚀 Step 2 — Download a model

Recommended:

```bash
ollama pull llama3.2
```

Alternative (lighter):

```bash
ollama pull phi3
```

---

## 🚀 Step 3 — Test locally

```bash
ollama run llama3.2 "Explain timeout error in payment service"
```

---

## 🌐 Step 4 — Test REST API

```bash
curl http://localhost:11434/api/chat -H "Content-Type: application/json" -d "{"model":"llama3.2","messages":[{"role":"user","content":"Say hello"}],"stream":false}"
```

---

## ✅ Expected response

```json
{
  "model": "llama3.2",
  "message": {
    "role": "assistant",
    "content": "..."
  }
}
```

---

## 🔌 Integration in Project

Endpoint used:

```
POST http://localhost:11434/api/chat
```

---

## ✅ Request structure

```json
{
  "model": "llama3.2",
  "messages": [
    {
      "role": "user",
      "content": "Analyze log"
    }
  ],
  "stream": false
}
```

---

## 🧩 Structured JSON Mode

Add:

```json
"format": "json"
```

And prompt example:

```text
Return ONLY valid JSON:
{
  "rootCause": "...",
  "explanation": "...",
  "suggestion": "...",
  "severity": "LOW|MEDIUM|HIGH"
}
```

---

## ⚠️ Troubleshooting

### ❌ Ollama not responding

```bash
curl http://localhost:11434/api/tags
```

If fails:
- Make sure Ollama is running

---

### ❌ Model missing

```bash
ollama pull llama3.2
```

---

### ❌ Slow response
- First request loads model
- Try smaller model

---

## 🏁 Summary

Ollama provides local AI processing with:
- No cost
- No API limits
- Full control

---
