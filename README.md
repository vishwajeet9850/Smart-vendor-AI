# 🎓 EduSathi AI

> **An AI-powered multilingual tutor that understands the student, not just the question.**

![Status](https://img.shields.io/badge/Status-In%20Development-orange)
![Hackathon](https://img.shields.io/badge/SKH-2026-blue)
![License](https://img.shields.io/badge/License-MIT-green)
![Built With](https://img.shields.io/badge/Built%20With-React%20%7C%20FastAPI%20%7C%20Gemini%20%7C%20Ollama-success)

---

# 📖 Overview

EduSathi AI is an AI-powered Progressive Web Application (PWA) designed for rural students.

Unlike traditional AI chatbots, EduSathi AI focuses on **personalized learning**, **voice interaction**, **multilingual education**, and **intelligent mistake analysis**.

The system uses a **Hybrid AI Architecture** that combines a **Local LLM** with **Google Gemini** to provide faster responses, reduce cloud dependency, and improve user experience.

---

# 🚀 Vision

Build an AI tutor that **understands the student's learning process**, not just their questions.

Instead of simply answering questions, EduSathi AI:

- Explains concepts
- Detects misconceptions
- Generates personalized quizzes
- Tracks learning progress
- Supports multiple regional languages
- Works with Hybrid AI

---

# 🎯 Problem Statement

Rural students often face:

- Language barriers
- Limited access to quality teachers
- Lack of personalized learning
- Difficulty typing queries
- Limited internet connectivity

Our goal is to make AI tutoring accessible, interactive, and personalized.

---

# ⭐ Key Features

## 🤖 AI Tutor

- AI-powered doubt solving
- Follow-up conversations
- Personalized explanations

---

## 🎤 Voice Tutor

- Speech-to-Text
- AI Conversation
- Text-to-Speech

Students can simply talk to the tutor.

---

## 🌍 Multilingual Support

Supports

- English
- Marathi
- Hindi

(More languages can be added later.)

---

## 📝 AI Quiz Generator

Automatically generates quizzes after every lesson.

Features:

- Adaptive questions
- Instant evaluation
- Personalized practice

---

## 📊 Progress Dashboard

Tracks

- Quiz performance
- Weak topics
- Learning history
- Study statistics

---

## 🧠 Smart Error Analysis

Instead of saying

> "Wrong Answer"

The AI identifies

- Why the answer is wrong
- The misconception behind it
- Suggests revision
- Generates another practice question

Example

Student:

```
3/5 + 2/5 = 5/10
```

AI:

> You are adding denominators.

> This indicates a misunderstanding of fractions.

> Let's revise fractions before continuing.

---

# ⚡ Hybrid AI Architecture

Instead of sending every request to a cloud model,

EduSathi AI intelligently decides which model should answer.

```
                 Student

                     │

            Voice / Text Input

                     │

             AI Request Router

        ┌────────────┴────────────┐

        │                         │

 Local LLM                 Gemini API

(Simple Tasks)          (Complex Tasks)

        │                         │

        └────────────┬────────────┘

                     │

               Final Response
```

### Local LLM

Used for

- Definitions
- Translation
- Basic tutoring
- Greetings
- Quick responses

---

### Gemini

Used for

- Deep reasoning
- Smart Error Analysis
- Personalized tutoring
- Long explanations

---

# 🏗️ System Architecture

```
Student

↓

React PWA

↓

FastAPI Backend

↓

AI Router

↓

──────────────

Ollama

Gemini

──────────────

↓

MongoDB

↓

Response
```

---

# 🛠️ Technology Stack

## Frontend

- React
- Vite
- Tailwind CSS

---

## Backend

- FastAPI
- Python

---

## Database

- MongoDB Atlas

---

## Artificial Intelligence

### Cloud

- Google Gemini API

### Local

- Ollama
- Qwen 2.5 3B (or Gemma)

---

## Voice

- Whisper (Speech-to-Text)

- Piper / Edge TTS (Text-to-Speech)

---

## Deployment

Frontend

- Vercel

Backend

- Render

Database

- MongoDB Atlas

---

# 📂 Repository Structure

```
EduSathi-AI/

│

├── docs/

│   ├── Vision.md

│   ├── Features.md

│   ├── Architecture.md

│   ├── AIArchitecture.md

│   ├── DecisionLog.md

│   ├── API.md

│   ├── Database.md

│   ├── Testing.md

│   └── Roadmap.md

│

├── frontend/

├── backend/

├── ai/

├── prompts/

├── assets/

├── README.md

└── LICENSE
```

---

# 📅 Development Workflow

```
Research

↓

Planning

↓

Architecture

↓

Prototype

↓

Development

↓

Testing

↓

Benchmarking

↓

Optimization

↓

Presentation
```

---

# 🎯 Project Philosophy

> **Quality over Quantity**

Instead of adding numerous average features,

we focus on building a few polished and impactful ones.

Every feature must answer:

- Does it solve a real problem?
- Does it improve learning?
- Will judges remember it?
- Can we explain it clearly?

---

# 🚫 What We're NOT Building

- Teacher Portal
- Admin Panel
- Leaderboards
- Social Features
- Unnecessary AI Integrations
- Features without clear value

---

# 📈 Current Status

- [x] Problem Statement Selected
- [x] Product Vision
- [x] Feature Planning
- [ ] System Architecture
- [ ] UI Design
- [ ] Backend Development
- [ ] AI Integration
- [ ] Hybrid AI
- [ ] Voice Tutor
- [ ] Smart Error Analysis
- [ ] Testing
- [ ] Final Deployment

---

# 👨‍💻 Team

| Role | Responsibility |
|------|----------------|
| Frontend | React PWA |
| Backend | FastAPI + MongoDB |
| AI | Gemini + Ollama |
| Voice | STT + TTS |
| Integration | Testing & Deployment |

---

# 📜 License

This project is developed for **SKH 2026 Hackathon**.

---

## ⭐ Motto

> **"Build the smallest product that creates the biggest impression."**