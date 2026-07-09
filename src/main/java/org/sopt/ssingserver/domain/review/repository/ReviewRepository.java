package org.sopt.ssingserver.domain.review.repository;

import org.sopt.ssingserver.domain.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // 강사 홈 리뷰 요약에 필요한 평균 평점 조회함
    @Query("""
            select avg(review.rating) as averageRating
            from Review review
            where review.instructorProfile.id = :instructorProfileId
            """)
    Double findAverageRatingByInstructorProfileId(
            @Param("instructorProfileId") Long instructorProfileId
    );
}
