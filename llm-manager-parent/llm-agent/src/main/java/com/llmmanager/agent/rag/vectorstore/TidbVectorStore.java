package com.llmmanager.agent.rag.vectorstore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * TiDB Vector Search 实现的 VectorStore，因为spring-ai官方没有原生支持，因此我们自己实现。
 *
 * 基于 TiDB 原生向量类型与距离函数：
 * - VECTOR(D) 列存储 Embedding 向量
 * - VEC_COSINE_DISTANCE(embedding, '[...]') 进行相似度检索
 *
 * 表结构参考：db/schema_vector.sql
 */
@Slf4j
public class TidbVectorStore implements VectorStore {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[0-9a-zA-Z_]+$");
    private static final Pattern SAFE_METADATA_KEY = Pattern.compile("^[0-9a-zA-Z_]+$");

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final String name;
    private final String tableName;
    private final String kbCodeFilter; // null 表示不按 kb_code 过滤（用于 _global）
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;

    public TidbVectorStore(
            String tableName,
            String kbCodeFilter,
            JdbcTemplate jdbcTemplate,
            EmbeddingModel embeddingModel,
            ObjectMapper objectMapper
    ) {
        this.tableName = requireSafeIdentifier(tableName, "tableName");
        this.kbCodeFilter = kbCodeFilter;
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.name = "tidb:" + this.tableName + (kbCodeFilter != null ? (":" + kbCodeFilter) : "");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void add(List<Document> documents) {
        if (CollectionUtils.isEmpty(documents)) {
            return;
        }

        List<String> contents = documents.stream()
                .map(Document::getText)
                .toList();

        List<float[]> embeddings = embeddingModel.embed(contents);
        if (embeddings == null || embeddings.size() != documents.size()) {
            throw new IllegalStateException("[TidbVectorStore] Embedding 结果数量与文档数量不一致");
        }

        // 批量插入
        String sql = "INSERT INTO " + tableName
                + " (vector_id, kb_code, doc_code, chunk_index, content, embedding, metadata, is_delete)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, 0)";

        List<Object[]> batchArgs = new ArrayList<>(documents.size());
        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);
            float[] embedding = embeddings.get(i);

            String vectorId = document.getId();
            String kbCode = resolveKbCode(document);
            String docCode = resolveMetadataString(document, "docCode");
            Integer chunkIndex = i;
            String content = document.getText();
            String embeddingText = toVectorText(embedding);
            String metadataJson = toJson(document.getMetadata());

            batchArgs.add(new Object[] {
                    vectorId,
                    kbCode,
                    docCode,
                    chunkIndex,
                    content,
                    embeddingText,
                    metadataJson
            });
        }

        jdbcTemplate.batchUpdate(sql, batchArgs);
        log.debug("[TidbVectorStore] 插入向量: table={}, kbCodeFilter={}, rows={}", tableName, kbCodeFilter, documents.size());
    }

    @Override
    public void delete(List<String> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }

        // 分批防止 SQL 过长
        final int batchSize = 200;
        for (int start = 0; start < ids.size(); start += batchSize) {
            List<String> batch = ids.subList(start, Math.min(ids.size(), start + batchSize));
            String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
            String sql = "UPDATE " + tableName
                    + " SET is_delete=1, update_time=CURRENT_TIMESTAMP"
                    + " WHERE is_delete=0 AND vector_id IN (" + placeholders + ")"
                    + (kbCodeFilter != null ? " AND kb_code=?" : "");

            List<Object> params = new ArrayList<>(batch.size() + 1);
            params.addAll(batch);
            if (kbCodeFilter != null) {
                params.add(kbCodeFilter);
            }
            jdbcTemplate.update(sql, params.toArray());
        }
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        if (filterExpression == null) {
            return;
        }

        List<Object> params = new ArrayList<>();
        String where = buildFilterSql(filterExpression, params);
        if (where == null || where.isBlank()) {
            return;
        }

        String sql = "UPDATE " + tableName
                + " SET is_delete=1, update_time=CURRENT_TIMESTAMP"
                + " WHERE is_delete=0"
                + (kbCodeFilter != null ? " AND kb_code=?" : "")
                + " AND (" + where + ")";

        if (kbCodeFilter != null) {
            params.add(0, kbCodeFilter);
        }

        jdbcTemplate.update(sql, params.toArray());
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return List.of();
        }

        String query = request.getQuery();
        int topK = request.getTopK() > 0 ? request.getTopK() : SearchRequest.DEFAULT_TOP_K;
        double threshold = request.getSimilarityThreshold();

        float[] queryEmbedding = embeddingModel.embed(query);
        String queryVectorText = toVectorText(queryEmbedding);

        String distanceExpr = "VEC_COSINE_DISTANCE(embedding, ?)";

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT vector_id, kb_code, doc_code, chunk_index, content, metadata, ")
                .append(distanceExpr)
                .append(" AS distance ")
                .append("FROM ").append(tableName)
                .append(" WHERE is_delete=0");

        params.add(queryVectorText);

        if (kbCodeFilter != null) {
            sql.append(" AND kb_code=?");
            params.add(kbCodeFilter);
        }

        // 元数据过滤
        if (request.hasFilterExpression()) {
            List<Object> filterParams = new ArrayList<>();
            String where = buildFilterSql(request.getFilterExpression(), filterParams);
            if (where != null && !where.isBlank()) {
                sql.append(" AND (").append(where).append(")");
                params.addAll(filterParams);
            }
        }

        // 相似度阈值：score = 1 - cosine_distance；score >= threshold => cosine_distance <= 1 - threshold
        if (threshold != SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL) {
            double maxDistance = 1.0d - threshold;
            sql.append(" AND ").append(distanceExpr).append(" <= ?");
            params.add(queryVectorText);
            params.add(maxDistance);
        }

        sql.append(" ORDER BY ").append(distanceExpr).append(" ASC")
                .append(" LIMIT ?");
        params.add(queryVectorText);
        params.add(topK);

        return jdbcTemplate.query(sql.toString(), params.toArray(), (rs, rowNum) -> {
            String vectorId = rs.getString("vector_id");
            String content = rs.getString("content");
            String metadataJson = rs.getString("metadata");
            double distance = rs.getDouble("distance");
            double score = 1.0d - distance;

            Map<String, Object> metadata = parseJsonMap(metadataJson);
            if (metadata == null) {
                metadata = new HashMap<>();
            }

            String kbCode = rs.getString("kb_code");
            String docCode = rs.getString("doc_code");
            Number chunkIndexNum = (Number) rs.getObject("chunk_index");
            Integer chunkIndex = chunkIndexNum != null ? chunkIndexNum.intValue() : null;

            if (kbCode != null) {
                metadata.putIfAbsent("kbCode", kbCode);
            }
            if (docCode != null) {
                metadata.putIfAbsent("docCode", docCode);
            }
            if (chunkIndex != null) {
                metadata.putIfAbsent("chunkIndex", chunkIndex);
            }

            // 兼容现有 MultiKbDocumentRetriever：按 metadata.score(Float) 排序
            metadata.put("score", (float) score);
            metadata.put("distance", (float) distance);

            return Document.builder()
                    .id(vectorId)
                    .text(content)
                    .metadata(metadata)
                    .score(score)
                    .build();
        });
    }

    @Override
    public <T> Optional<T> getNativeClient() {
        @SuppressWarnings("unchecked")
        T nativeClient = (T) jdbcTemplate;
        return Optional.of(nativeClient);
    }

    private static String requireSafeIdentifier(String identifier, String paramName) {
        if (identifier == null || identifier.isBlank() || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("非法 SQL 标识符 " + paramName + ": " + identifier);
        }
        return identifier;
    }

    private String resolveKbCode(Document document) {
        if (kbCodeFilter != null) {
            return kbCodeFilter;
        }

        Object kbCode = document != null && document.getMetadata() != null ? document.getMetadata().get("kbCode") : null;
        if (kbCode == null) {
            throw new IllegalArgumentException("[TidbVectorStore] 未指定 kbCodeFilter，且 Document.metadata.kbCode 为空");
        }
        return String.valueOf(kbCode);
    }

    private static String resolveMetadataString(Document document, String key) {
        if (document == null || document.getMetadata() == null) {
            return null;
        }
        Object value = document.getMetadata().get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new IllegalArgumentException("[TidbVectorStore] metadata 序列化失败", e);
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            log.warn("[TidbVectorStore] metadata 解析失败，将忽略 metadata: {}", e.getMessage());
            return null;
        }
    }

    private static String toVectorText(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("向量为空");
        }

        StringBuilder sb = new StringBuilder(vector.length * 8);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * 将 Spring AI Filter.Expression 转换为 TiDB(MySQL) 可执行的 WHERE 子句（仅作用于 metadata JSON）。
     *
     * 目前实现：AND/OR/EQ/NOT，满足项目现有用法（RagAdvisorBuilder.buildAdvisorWithMetadata）；
     * 其他表达式类型可按需扩展。
     */
    private String buildFilterSql(Filter.Expression expression, List<Object> params) {
        return buildFilterOperandSql(expression, params);
    }

    private static String buildJsonEqSql(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("metadata key 为空");
        }
        if (!SAFE_METADATA_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("metadata key 含非法字符（仅允许字母/数字/下划线）: " + key);
        }
        String jsonPath = "$.\"" + key + "\"";
        return "JSON_UNQUOTE(JSON_EXTRACT(metadata, '" + jsonPath + "')) = ?";
    }

    private String buildFilterOperandSql(Filter.Operand operand, List<Object> params) {
        if (operand == null) {
            return null;
        }
        if (operand instanceof Filter.Group group) {
            return buildFilterOperandSql(group.content(), params);
        }
        if (operand instanceof Filter.Expression expression) {
            return switch (expression.type()) {
                case AND, OR -> {
                    String left = buildFilterOperandSql(expression.left(), params);
                    String right = buildFilterOperandSql(expression.right(), params);
                    if (left == null || left.isBlank()) {
                        yield right;
                    }
                    if (right == null || right.isBlank()) {
                        yield left;
                    }
                    yield "(" + left + " " + expression.type().name() + " " + right + ")";
                }
                case NOT -> {
                    String inner = buildFilterOperandSql(expression.left(), params);
                    if (inner == null || inner.isBlank()) {
                        yield null;
                    }
                    yield "NOT (" + inner + ")";
                }
                case EQ -> {
                    Filter.Key key;
                    Filter.Value value;
                    if (expression.left() instanceof Filter.Key k && expression.right() instanceof Filter.Value v) {
                        key = k;
                        value = v;
                    } else if (expression.left() instanceof Filter.Value v && expression.right() instanceof Filter.Key k) {
                        key = k;
                        value = v;
                    } else {
                        throw new UnsupportedOperationException("不支持的 EQ 操作数: " + expression);
                    }
                    params.add(value.value() != null ? String.valueOf(value.value()) : null);
                    yield buildJsonEqSql(key.key());
                }
                default -> throw new UnsupportedOperationException("暂不支持的过滤表达式类型: " + expression.type());
            };
        }

        throw new UnsupportedOperationException("不支持的过滤操作数类型: " + operand.getClass());
    }
}
