# Lynq

[![lynq-latest-version](https://raw.githubusercontent.com/MatLock/UdeSA-lynq/main/.github/badges/version.svg)](https://github.com/MatLock/UdeSA-lynq/releases)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=MatLock_UdeSA-lynq&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=MatLock_UdeSA-lynq)

Lynq is a job-search platform built around the idea that finding a job shouldn't be a job of its own. It aims to streamline the experience for candidates by bringing the pieces of the search — identity, listings, applications, and tracking — under one roof.

This repository is the umbrella for all modules that make up the platform. Each subdirectory is an independent module with its own README and lifecycle.

> * Lynq Home Page: https://lynqoficial.com/  
> * Trello Board: https://trello.com/b/2inGRZwL/lyqn 
> * Sonar Board: https://sonarcloud.io/project/branches_list?id=MatLock_UdeSA-lynq 


## Modules

### lynq-iam &nbsp; [![CI](https://github.com/MatLock/UdeSA-lynq/actions/workflows/lynq-iam-test-workflow.yaml/badge.svg)](https://github.com/MatLock/UdeSA-lynq/actions/workflows/lynq-iam-test-workflow.yaml) [![Coverage](https://raw.githubusercontent.com/MatLock/UdeSA-lynq/main/.github/badges/jacoco.svg)](https://github.com/MatLock/UdeSA-lynq/actions/workflows/lynq-iam-test-workflow.yaml)

The identity and access management module for Lynq. It handles user accounts and sign-in, keeps sessions secure, and acts as the gatekeeper that lets the rest of the platform know who is making each request.

### lynq-bff &nbsp; [![CI](https://github.com/MatLock/UdeSA-lynq/actions/workflows/lynq-bff-test-workflow.yaml/badge.svg)](https://github.com/MatLock/UdeSA-lynq/actions/workflows/lynq-bff-test-workflow.yaml) [![Coverage](https://raw.githubusercontent.com/MatLock/UdeSA-lynq/main/.github/badges/jacoco-bff.svg)](https://github.com/MatLock/UdeSA-lynq/actions/workflows/lynq-bff-test-workflow.yaml)

The gateway module for Lynq — the single entry point from the frontend into the platform. It verifies the signature of every access token and then relays the request, unchanged, to whichever service owns it: lynq-app-backend, lynq-ml or lynq-file-storage. Those three expose their APIs behind a `/dmz` prefix and are reached only through here, which is why none of them has to check the token's signature for itself.

### lynq-app-backend &nbsp; [![CI](https://github.com/MatLock/UdeSA-lynq/actions/workflows/lynq-app-backend-test-workflow.yaml/badge.svg)](https://github.com/MatLock/UdeSA-lynq/actions/workflows/lynq-app-backend-test-workflow.yaml) [![Coverage](https://raw.githubusercontent.com/MatLock/UdeSA-lynq/main/.github/badges/jacoco-app-backend.svg)](https://github.com/MatLock/UdeSA-lynq/actions/workflows/lynq-app-backend-test-workflow.yaml)

The core application backend for Lynq. It exposes the platform's REST API under a `/dmz` prefix, reached only through lynq-bff, and backs the candidate-facing experience — listings, applications, and tracking — with persistent storage and caching. It resolves the caller's identity from lynq-iam on every request; verifying the token's signature is lynq-bff's job.

### lynq-file-storage &nbsp; [![CI](https://github.com/MatLock/UdeSA-lynq/actions/workflows/lynq-file-storage-test-workflow.yaml/badge.svg)](https://github.com/MatLock/UdeSA-lynq/actions/workflows/lynq-file-storage-test-workflow.yaml) [![Coverage](https://raw.githubusercontent.com/MatLock/UdeSA-lynq/main/.github/badges/jacoco-file-storage.svg)](https://github.com/MatLock/UdeSA-lynq/actions/workflows/lynq-file-storage-test-workflow.yaml)

The file service for Lynq. It owns every file the platform stores — profile images, company logos and résumé PDFs — holding the bucket credentials and the file metadata, and handing out short-lived pre-signed upload and download URLs. Every other module keeps only the file ids it returns and never touches the bucket itself.

### lynq-app-frontend

The candidate-facing web app for Lynq, built with React 19 and Vite. It delivers the interactive experience — sign-in, the job feed, profiles, applications, and job creation — talking to lynq-iam for identity and to lynq-bff for everything else.

### lynq-ml &nbsp; [![CI](https://github.com/MatLock/UdeSA-lynq/actions/workflows/lynq-ml-test-workflow.yaml/badge.svg)](https://github.com/MatLock/UdeSA-lynq/actions/workflows/lynq-ml-test-workflow.yaml) [![Coverage](https://raw.githubusercontent.com/MatLock/UdeSA-lynq/main/.github/badges/coverage-ml.svg)](https://github.com/MatLock/UdeSA-lynq/actions/workflows/lynq-ml-test-workflow.yaml)

The machine-learning service for Lynq, a FastAPI app that augments the platform with LLM-backed features: skill extraction from job postings, upskilling suggestions with real Udemy courses, candidate hiring assessments, and resume parsing, translation, and styled-PDF rendering. It runs on pluggable LLM clients (Amazon Bedrock or a local Ollama model) and returns results in the platform's standard response envelope.

### lynq-home

The public landing page for Lynq — a static site served from Cloudflare Workers and deployed with Wrangler.

### feeders

Tooling to seed Lynq with realistic job-listing data for development. Bundles three feeders that normalize to a shared listing schema: a LinkedIn dataset loader (bulk seed data from Hugging Face) and polite live scrapers for Computrabajo Argentina and Bumeran Argentina.

### infrastructure

The deployment setup for Lynq. A Helm chart runs the platform on a self-contained local cluster (minikube) and on production (AWS EKS), while Terraform provisions and applies production — the EC2 MySQL/Redis host, the S3 bucket, the ACM certificate and Cloudflare DNS, the Kubernetes secrets, and the Helm release. See [infrastructure/README.md](infrastructure/README.md) for the layout, the per-environment flags, and step-by-step instructions.


## Running the stack

The full platform is orchestrated with Docker Compose. From the repository root:

```bash
docker compose up
```

This brings up the application modules (`lynq-iam`, `lynq-bff`, `lynq-app-backend`, `lynq-file-storage`, `lynq-app-frontend`, `lynq-ml`) together with their infrastructure dependencies: MySQL, Redis, LocalStack, and an Ollama model server (pulled on first start). Each module can also be built and run on its own — see the individual module READMEs for details.
