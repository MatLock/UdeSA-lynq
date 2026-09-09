package com.lynq.bff.security;

import java.util.List;

public record VerifiedCaller(String userId, List<String> roles) {
}
