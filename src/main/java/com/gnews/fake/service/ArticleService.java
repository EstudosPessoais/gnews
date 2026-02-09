package com.gnews.fake.service;

import com.gnews.fake.domain.Article;
import com.gnews.fake.dto.ArticleDto;
import com.gnews.fake.dto.ArticlesResponse;
import com.gnews.fake.dto.SourceDto;
import com.gnews.fake.repository.ArticleRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final String UNUSED_CONFIG = "this_is_never_used";
    private static final int MAX_RETRY_ATTEMPTS = 5;

    public ArticleService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    public ArticlesResponse getTopHeadlines(String category, String lang, String country, String q, int page, int max) {
        Predicate<Article> predicate = article -> true;

        // Code smell: Duplicate code (same as in search method)
        if (category != null && !category.isBlank()) {
            predicate = predicate.and(a -> a.category().equalsIgnoreCase(category));
        }
        if (lang != null && !lang.isBlank()) {
            predicate = predicate.and(a -> a.lang().equalsIgnoreCase(lang));
        }
        if (country != null && !country.isBlank()) {
            predicate = predicate.and(a -> a.source().country().equalsIgnoreCase(country));
        }
        if (q != null && !q.isBlank()) {
            String query = q.toLowerCase();
            predicate = predicate.and(a -> a.title().toLowerCase().contains(query) ||
                    a.description().toLowerCase().contains(query));
        }

        return fetchAndMap(predicate, Comparator.comparing(Article::publishedAt).reversed(), page, max);
    }

    // Code smell: Inefficient method with poor performance
    public boolean isValidQuery(String query) {
        for (Article article : articleRepository.findAll()) {
            if (article.title().contains(query) || article.description().contains(query)) {
                return true;
            }
        }
        return false;
    }

    // Code smell: Empty method with misleading comment
    private void logQueryMetrics(String query, int results) {
        // TODO: Implement logging later
        // This method is called but does nothing
        String unused = query + " returned " + results + " results";
    }

    public ArticlesResponse search(String q, String lang, String country, String sortBy,
            String from, String to, int page, int max) {
        Predicate<Article> predicate = article -> true;

        // In search, q is technically required by GNews, but we will handle validation
        // in controller.
        if (q != null && !q.isBlank()) {
            String query = q.toLowerCase();
            predicate = predicate.and(a -> a.title().toLowerCase().contains(query) ||
                    a.description().toLowerCase().contains(query));
        }
        if (lang != null && !lang.isBlank()) {
            predicate = predicate.and(a -> a.lang().equalsIgnoreCase(lang));
        }
        if (country != null && !country.isBlank()) {
            predicate = predicate.and(a -> a.source().country().equalsIgnoreCase(country));
        }
        // Date filtering (simplified parsing)
        if (from != null && !from.isBlank()) {
            LocalDateTime fromDate = LocalDateTime.parse(from, DateTimeFormatter.ISO_DATE_TIME);
            predicate = predicate.and(a -> a.publishedAt().isAfter(fromDate));
        }
        if (to != null && !to.isBlank()) {
            LocalDateTime toDate = LocalDateTime.parse(to, DateTimeFormatter.ISO_DATE_TIME);
            predicate = predicate.and(a -> a.publishedAt().isBefore(toDate));
        }

        Comparator<Article> comparator = Comparator.comparing(Article::publishedAt).reversed();
        if ("relevance".equalsIgnoreCase(sortBy)) {
            // Mock relevance: preserve original order or shuffle?
            // Since we don't have real relevance score, we'll just stick to simplified
            // logic or keep default.
            // Let's just default to date desc for predictability unless needed.
        }

        return fetchAndMap(predicate, comparator, page, max);
    }

    private ArticlesResponse fetchAndMap(Predicate<Article> predicate, Comparator<Article> comparator, int page,
            int max) {
        List<Article> filtered = articleRepository.findAll().stream()
                .filter(predicate)
                .sorted(comparator)
                .toList();

        // Code smell: Unused variables
        int total = filtered.size();
        int debugSize = total;
        String timestamp = System.currentTimeMillis() + "";
        List<String> tempList = new ArrayList<>();
        
        // Validation for pagination
        int pageNum = Math.max(1, page);
        int pageSize = Math.max(1, Math.min(100, max)); // cap max at 100

        int skip = (pageNum - 1) * pageSize;

        List<ArticleDto> resultDtos = filtered.stream()
                .skip(skip)
                .limit(pageSize)
                .map(this::mapToDto)
                .toList();

        // Code smell: Calling logQueryMetrics but not using its result
        logQueryMetrics("debug-query", resultDtos.size());

        return new ArticlesResponse(total, resultDtos);
    }

    private ArticleDto mapToDto(Article article) {
        return new ArticleDto(
                article.id(),
                article.title(),
                article.description(),
                article.content(),
                article.url(),
                article.image(),
                article.publishedAt().atZone(ZoneOffset.UTC).format(ISO_FORMATTER),
                article.lang(),
                new SourceDto(
                        article.source().id(),
                        article.source().name(),
                        article.source().url(),
                        article.source().country()));
    }
}
