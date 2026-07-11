# Lynq

Lynq is a job-search platform built around the idea that finding a job shouldn't be a job of its own. It aims to streamline the experience for candidates by bringing the pieces of the search — identity, listings, applications, and tracking — under one roof.

This repository is the umbrella for all modules that make up the platform. Each subdirectory is an independent module with its own README and lifecycle.

> Lynq home page: https://lynqoficial.com/  
> Trello board: https://trello.com/b/2inGRZwL/lyqn


## Modules

### lynq-iam &nbsp; [![CI](https://github.com/MatLock/UdeSA-lyqn/actions/workflows/lynq-iam-test-workflow.yaml/badge.svg)](https://github.com/MatLock/UdeSA-lyqn/actions/workflows/lynq-iam-test-workflow.yaml) [![Coverage](https://raw.githubusercontent.com/MatLock/UdeSA-lyqn/main/.github/badges/jacoco.svg)](https://github.com/MatLock/UdeSA-lyqn/actions/workflows/lynq-iam-test-workflow.yaml)

The identity and access management module for Lynq. It handles user accounts and sign-in, keeps sessions secure, and acts as the gatekeeper that lets the rest of the platform know who is making each request.

### lynq-app-backend &nbsp; [![CI](https://github.com/MatLock/UdeSA-lyqn/actions/workflows/lynq-app-backend-test-workflow.yaml/badge.svg)](https://github.com/MatLock/UdeSA-lyqn/actions/workflows/lynq-app-backend-test-workflow.yaml) [![Coverage](https://raw.githubusercontent.com/MatLock/UdeSA-lyqn/main/.github/badges/jacoco-app-backend.svg)](https://github.com/MatLock/UdeSA-lyqn/actions/workflows/lynq-app-backend-test-workflow.yaml)

The core application backend for Lynq. It exposes the platform's REST API, enforcing authenticated and audited access on top of the identity provided by lynq-iam, and backs the candidate-facing experience — listings, applications, and tracking — with persistent storage and caching.

### lynq-app-frontend

The candidate-facing web app for Lynq, built with React 19 and Vite. It delivers the interactive experience — sign-in, the job feed, profiles, applications, and job creation — talking to lynq-iam for identity and to lynq-app-backend for platform data.

### lynq-ml

The machine-learning service for Lynq, a FastAPI app that augments the platform with LLM-backed features. It currently exposes a skill-enhancement endpoint and pluggable LLM clients (OpenAI or a local Ollama model), returning results in the platform's standard response envelope.

### lynq-home

The public landing page for Lynq — a static site served from Cloudflare Workers and deployed with Wrangler.

### feeders

Tooling to seed Lynq with realistic job-listing data for development. Bundles three feeders that normalize to a shared listing schema: a LinkedIn dataset loader (bulk seed data from Hugging Face) and polite live scrapers for Computrabajo Argentina and Bumeran Argentina.


## Running the stack

The full platform is orchestrated with Docker Compose. From the repository root:

```bash
docker compose up
```

This brings up the application modules (`lynq-iam`, `lynq-app-backend`, `lynq-app-frontend`, `lynq-ml`) together with their infrastructure dependencies: MySQL, Redis, LocalStack, and an Ollama model server (pulled on first start). Each module can also be built and run on its own — see the individual module READMEs for details.
