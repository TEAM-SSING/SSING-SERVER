package org.sopt.ssingserver.domain.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.sopt.ssingserver.domain.instructor.enums.LessonLevel;
import org.sopt.ssingserver.domain.instructor.enums.Sport;
import org.sopt.ssingserver.domain.matching.dto.result.InstructorMatchingOffersResult;
import org.sopt.ssingserver.domain.matching.enums.MatchingOfferStatus;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "강사 현재 노출 매칭 제안 목록 응답")
public record InstructorMatchingOffersResponse(

        @Schema(description = "현재 로그인한 강사에게 노출된 매칭 제안 목록. MVP에서는 보통 0개 또는 1개")
        List<ItemResponse> items,

        @Schema(description = "현재 페이지 번호. 0부터 시작", example = "0")
        int currentPage,

        @Schema(description = "페이지 크기", example = "20")
        int size,

        @Schema(description = "다음 페이지 존재 여부", example = "false")
        boolean hasNext
) {

    public static InstructorMatchingOffersResponse from(InstructorMatchingOffersResult result) {
        return new InstructorMatchingOffersResponse(
                result.items().stream()
                        .map(ItemResponse::from)
                        .toList(),
                result.currentPage(),
                result.size(),
                result.hasNext()
        );
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "강사 매칭 제안 항목")
    public record ItemResponse(

            @Schema(description = "매칭 제안 ID", example = "21")
            Long offerId,

            @Schema(description = "매칭 요청 그룹 ID", example = "3")
            Long groupId,

            @Schema(description = "제안 상태", example = "OFFERED")
            MatchingOfferStatus offerStatus,

            @Schema(description = "현 정책은 무기한 대기라 응답에서 생략되는 제안 만료 시각 예비 필드")
            Instant expiresAt,

            @Schema(description = "대표 요청자와 그룹 요청 수 요약")
            RequestSummaryResponse requestSummary,

            @Schema(description = "강습 조건 요약")
            LessonSummaryResponse lessonSummary,

            @Schema(description = "제안 생성 시점에 고정된 예상 가격")
            MatchingPriceSummaryResponse priceSummary
    ) {

        private static ItemResponse from(InstructorMatchingOffersResult.ItemResult result) {
            return new ItemResponse(
                    result.offerId(),
                    result.groupId(),
                    result.offerStatus(),
                    result.expiresAt(),
                    RequestSummaryResponse.from(result.requestSummary()),
                    LessonSummaryResponse.from(result.lessonSummary()),
                    MatchingPriceSummaryResponse.from(result.priceSummary())
            );
        }
    }

    @Schema(name = "InstructorMatchingOfferRequestSummary", description = "강사 매칭 제안의 대표 요청자 요약")
    public record RequestSummaryResponse(

            @Schema(description = "그룹 첫 요청자의 닉네임", example = "홍길동")
            String requesterName,

            @Schema(description = "그룹 첫 요청의 인원", example = "2")
            int headcount,

            @Schema(description = "같은 매칭 그룹의 요청 수", example = "1")
            int matchingRequestCount
    ) {

        public static RequestSummaryResponse from(InstructorMatchingOffersResult.RequestSummaryResult result) {
            if (result == null) {
                return null;
            }

            return new RequestSummaryResponse(
                    result.requesterName(),
                    result.headcount(),
                    result.matchingRequestCount()
            );
        }
    }

    @Schema(description = "강습 조건 요약")
    public record LessonSummaryResponse(

            @Schema(description = "리조트 요약")
            ResortResponse resort,

            @Schema(description = "종목", example = "SNOWBOARD")
            Sport sport,

            @Schema(description = "강습 레벨", example = "FIRST_TIME")
            LessonLevel level,

            @Schema(description = "서버가 확정한 강습 시간(분)", example = "120")
            int durationMinutes,

            @Schema(description = "그룹 전체 인원", example = "3")
            int totalHeadcount,

            @Schema(description = "강습 시작 유형", example = "IMMEDIATE")
            String startType
    ) {

        public static LessonSummaryResponse from(InstructorMatchingOffersResult.LessonSummaryResult result) {
            if (result == null) {
                return null;
            }

            return new LessonSummaryResponse(
                    ResortResponse.from(result.resort()),
                    result.sport(),
                    result.level(),
                    result.durationMinutes(),
                    result.totalHeadcount(),
                    result.startType()
            );
        }
    }

    @Schema(description = "리조트 요약")
    public record ResortResponse(

            @Schema(description = "서버 식별 코드", example = "HIGH1")
            String code,

            @Schema(description = "Android 표시 이름", example = "하이원")
            String displayName
    ) {

        public static ResortResponse from(InstructorMatchingOffersResult.ResortResult result) {
            if (result == null) {
                return null;
            }

            return new ResortResponse(result.code(), result.displayName());
        }
    }
}
