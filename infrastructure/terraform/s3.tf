# ---------------------------------------------------------------------------
# S3 bucket for the backend (profile images via pre-signed uploads).
# (Local uses LocalStack instead, handled entirely by Helm.)
# ---------------------------------------------------------------------------
resource "aws_s3_bucket" "lynq" {
  bucket = var.s3_bucket_name
}

# Keep the bucket private — access is granted through the pre-signed URLs the
# backend issues, not via public ACLs/policies.
resource "aws_s3_bucket_public_access_block" "lynq" {
  bucket                  = aws_s3_bucket.lynq.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# CORS so the browser can PUT/GET directly against the pre-signed URLs the
# backend hands out.
resource "aws_s3_bucket_cors_configuration" "lynq" {
  bucket = aws_s3_bucket.lynq.id

  cors_rule {
    allowed_origins = var.s3_cors_allowed_origins
    allowed_methods = ["GET", "PUT", "HEAD"]
    allowed_headers = ["*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}
