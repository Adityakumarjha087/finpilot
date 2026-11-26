package com.finpilot.security;

import com.finpilot.user.Role;

/** Principal stored in the security context for an authenticated API call. */
public record AuthenticatedUser(long id, String email, Role role) {}
