package org.sopt.ssingserver.domain.auth.dto.response;

public record InstructorAuthLoginResult(
        AuthLoginResult loginResult,
        InstructorStatusResponse instructorStatus
) {
}
