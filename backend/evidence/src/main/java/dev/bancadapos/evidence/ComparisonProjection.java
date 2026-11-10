package dev.bancadapos.evidence;

import dev.bancadapos.common.Comparison;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
class ComparisonProjection {
    private final MongoTemplate mongo;

    ComparisonProjection(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    boolean store(Comparison comparison) {
        var byId = Query.query(Criteria.where("_id").is(comparison.id().toString()));
        var onInsert = new Update().setOnInsert("comparison", comparison);
        return mongo.upsert(byId, onInsert, ProjectedComparison.class).getUpsertedId() != null;
    }

    Optional<Comparison> find(UUID id) {
        return Optional.ofNullable(mongo.findById(id.toString(), ProjectedComparison.class))
                .map(ProjectedComparison::comparison);
    }

    @Document("comparisons")
    record ProjectedComparison(@Id String id, Comparison comparison) {}
}
