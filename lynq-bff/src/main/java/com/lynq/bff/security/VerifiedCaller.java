package com.lynq.bff.security;

import java.util.List;

public record VerifiedCaller(String userId, String username, String email, List<String> roles) {
}
