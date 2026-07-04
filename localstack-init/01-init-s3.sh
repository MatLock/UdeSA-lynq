#!/bin/bash
# LocalStack init hook (runs from /etc/localstack/init/ready.d once S3 is ready).
# Creates the profile-image bucket and enables CORS so the browser can PUT/GET
# directly against the pre-signed URLs the backend hands out.
set -euo pipefail

BUCKET="${AWS_BUCKET_NAME:-lynq-bucket}"

awslocal s3 mb "s3://${BUCKET}" || true

awslocal s3api put-bucket-cors --bucket "${BUCKET}" --cors-configuration '{
  "CORSRules": [
    {
      "AllowedOrigins": ["*"],
      "AllowedMethods": ["GET", "PUT", "HEAD"],
      "AllowedHeaders": ["*"],
      "ExposeHeaders": ["ETag"],
      "MaxAgeSeconds": 3000
    }
  ]
}'

echo "LocalStack S3 ready: bucket '${BUCKET}' created with CORS enabled."
