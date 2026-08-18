package com.lynq.backend.service;

/**
 * A file registered in lynq-file-storage: {@code fileId} is what this service persists against the
 * owning user, company or resume, and {@code url} is the short-lived pre-signed URL the browser
 * PUTs the bytes to.
 */
public record RegisteredUpload(String fileId, String url) {
}
