package org.yhj.srim.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StockShareStatusQueryRepositoryImpl implements StockShareStatusQueryRepository{

    private final JPAQueryFactory queryFactory;


}
