package org.sopt.ssingserver.domain.matching.dto.result;

public record MatchingProgressSummaryResult(
        Integer acceptedRequesterCount,
        int totalRequesterCount,
        Integer paidRequesterCount
) {

    public static MatchingProgressSummaryResult confirmation(int acceptedRequesterCount, int totalRequesterCount) {
        return new MatchingProgressSummaryResult(acceptedRequesterCount, totalRequesterCount, null);
    }

    public static MatchingProgressSummaryResult payment(int paidRequesterCount, int totalRequesterCount) {
        return new MatchingProgressSummaryResult(null, totalRequesterCount, paidRequesterCount);
    }
}
