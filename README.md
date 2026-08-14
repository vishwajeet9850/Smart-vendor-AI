# 🛒 SmartVendor AI

> **Transforming any Android smartphone into an AI-powered Point of Sale (POS) system for small retailers.**

![Status](https://img.shields.io/badge/Status-In%20Development-orange)
![Hackathon](https://img.shields.io/badge/SKH%202026-Hackathon-blue)
![Platform](https://img.shields.io/badge/Platform-Android-success)
![AI](https://img.shields.io/badge/AI-YOLOv8%20%7C%20OpenCV-green)
![Backend](https://img.shields.io/badge/Backend-FastAPI-blue)

---

# 📖 Overview

SmartVendor AI is an AI-powered retail assistant that converts any Android smartphone into a complete Point of Sale (POS) system.

Instead of relying on expensive barcode scanners and dedicated billing hardware, SmartVendor AI uses Computer Vision and Artificial Intelligence to recognize products directly through the phone's camera, automatically generate bills, update inventory, analyze sales, and recommend future stock requirements.

Designed especially for:

- Kirana Stores
- Grocery Shops
- Bakeries
- Medical Stores
- Small Retail Businesses

---

# 🎯 Problem Statement

Millions of small retailers still rely on:

- Manual billing
- Paper notebooks
- Expensive POS systems
- Barcode scanners
- Manual inventory tracking

These methods are slow, error-prone, and difficult to manage.

SmartVendor AI provides an affordable AI-powered alternative using only a smartphone.

---

# 💡 Our Solution

SmartVendor AI replaces traditional billing systems with AI-powered product recognition.

Simply point the phone camera at a product.

The system automatically:

📷 Detects the product

↓

🧠 Identifies it using AI

↓

🧾 Creates the bill

↓

📦 Updates inventory

↓

📊 Stores sales history

↓

📈 Predicts future stock requirements

---

# ⭐ Features

## 📷 AI Product Recognition

- Camera-based product detection
- No barcode scanner required
- Real-time object detection using YOLO

---

## 🧾 Smart Billing

- Automatic bill generation
- Quantity adjustment
- GST support
- Instant total calculation

---

## 📦 Inventory Management

- Automatic stock updates
- Product database
- Low-stock alerts
- Inventory history

---

## 📊 Sales Analytics

- Daily sales
- Weekly sales
- Monthly sales
- Best-selling products
- Revenue tracking

---

## 📈 AI Demand Forecasting

Machine Learning predicts:

- Future demand
- Inventory requirements
- Restocking recommendations

---

## 📄 Digital Bill

- PDF Bill Generation
- WhatsApp Sharing
- Digital Receipt Storage

---

## 📱 Offline Support

Basic billing and inventory continue to work even without an internet connection.

Data syncs automatically when connectivity is restored.

---

# 🏗️ System Workflow

```
Customer Product

↓

Phone Camera

↓

YOLO Object Detection

↓

Product Identification

↓

Billing Engine

↓

Inventory Engine

↓

Database

↓

Sales Analytics

↓

Demand Forecasting

↓

Dashboard
```

---

# 🏛 System Architecture

```
               Flutter Mobile App

                        │

                 REST API (FastAPI)

                        │

      ┌─────────────────┼─────────────────┐

      │                 │                 │

YOLO Detection     Billing Engine    Inventory Engine

      │                 │                 │

      └──────────────┬───────────────────┘

                     │

              Analytics Engine

                     │

          Demand Forecast Model

                     │

              SQLite / PostgreSQL
```

---

# 🛠 Technology Stack

## Frontend

- Kotlin
- Android studio

---

## Backend

- FastAPI
- SQL
- Python

---

## AI & Computer Vision

- YOLOv8n
- OpenCV

---

## OCR

- Google ML Kit OCR

---

## Machine Learning

- Scikit-learn

---

## Database

Development

- SQLite

Production

- PostgreSQL

---

## Version Control

- Git
- GitHub

---

# 📂 Repository Structure

```
SmartVendor-AI/

│

├── frontend/

│

├── backend/

│

├── datasets/

│

├── trained_models/

│

├── docs/

│

├── presentations/

│

├── README.md

│

└── LICENSE
```

---

# 🚀 Development Roadmap

## Phase 1

- Project Setup
- GitHub Repository
- Folder Structure

---

## Phase 2

- Kotlin UI
- Product Database
- FastAPI Backend
- OCR

---

## Phase 3

- YOLO Dataset
- Image Annotation
- Model Training

---

## Phase 4

- Product Detection
- Billing Engine
- Inventory Engine

---

## Phase 5

- Analytics Dashboard
- Demand Forecasting
- Reports

---

## Phase 6

- Testing
- Optimization
- Deployment

---

# 🎯 Target Users

- Grocery Shops
- Kirana Stores
- Supermarkets
- Bakeries
- Medical Stores
- Stationery Shops

---

# 📈 Future Scope

- Multi-store Management
- Cloud Synchronization
- UPI Payment Integration
- Voice Billing
- Customer Loyalty Program
- Business Insights Dashboard

---

# 👥 Team

| Module | Responsibility |
|---------|----------------|
| Mobile App | Kotlin |
| Backend | FastAPI |
| AI Model | YOLOv8 |
| Database | SQLite / PostgreSQL |
| ML | Demand Forecasting |
| Integration | API & Testing |

---

# 📌 Project Status

- [x] Problem Statement Selected
- [x] Feature Planning
- [x] Technology Stack Finalized
- [ ] Git Repository Setup
- [ ] Flutter Development
- [ ] Backend APIs
- [ ] Dataset Collection
- [ ] Image Annotation
- [ ] YOLO Model Training
- [ ] AI Integration
- [ ] Billing System
- [ ] Inventory Management
- [ ] Analytics Dashboard
- [ ] Demand Forecasting
- [ ] Testing
- [ ] Final Deployment

---

# 🎯 Project Goal

To empower every small retailer with an affordable AI-powered billing and inventory management system using only a smartphone.

---

## ⭐ Motto

> **"From Camera to Checkout — Smart Billing Powered by AI."**
