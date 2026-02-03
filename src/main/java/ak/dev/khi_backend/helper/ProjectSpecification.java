package ak.dev.khi_backend.helper;

import ak.dev.khi_backend.model.Project;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public class ProjectSpecification {

    public static Specification<Project> searchEverywhere(String text) {
        return (root, query, cb) -> {
            query.distinct(true);

            var titleLike = cb.like(cb.lower(root.get("title")), "%" + text.toLowerCase() + "%");

            var tagJoin = root.join("tags", JoinType.LEFT);
            var tagLike = cb.like(cb.lower(tagJoin.get("name")), "%" + text.toLowerCase() + "%");

            var keywordJoin = root.join("keywords", JoinType.LEFT);
            var keywordLike = cb.like(cb.lower(keywordJoin.get("name")), "%" + text.toLowerCase() + "%");

            return cb.or(titleLike, tagLike, keywordLike);
        };
    }
}
