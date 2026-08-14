CREATE TABLE fact_store_count (
                                  dong_code   CHAR(8)    NOT NULL REFERENCES dim_dong(dong_code),
                                  cat_code    VARCHAR(6) NOT NULL REFERENCES dim_category(cat_code),
                                  period_id   CHAR(6)    NOT NULL REFERENCES dim_period(period_id),
                                  cat_level   VARCHAR(6) NOT NULL,
                                  store_count INTEGER    NOT NULL,
                                  PRIMARY KEY (dong_code, cat_code, period_id)
);

CREATE INDEX idx_store_count_lookup ON fact_store_count(period_id, cat_level);

CREATE TABLE fact_anomaly (
                              dong_code           CHAR(8)      NOT NULL REFERENCES dim_dong(dong_code),
                              cat_code            VARCHAR(6)   NOT NULL REFERENCES dim_category(cat_code),
                              period_id           CHAR(6)      NOT NULL REFERENCES dim_period(period_id),
                              cat_level           VARCHAR(6)   NOT NULL,
                              store_count         INTEGER      NOT NULL,
                              growth_rate         NUMERIC(8,5),
                              city_growth_rate    NUMERIC(8,5),
                              relative_gap        NUMERIC(8,5),
                              cum_change_rate     NUMERIC(8,5),
                              consecutive_decline BOOLEAN      NOT NULL DEFAULT FALSE,
                              sample_size_flag    VARCHAR(4)   NOT NULL DEFAULT 'OK',
                              score               NUMERIC(6,3),
                              grade               VARCHAR(10),
                              PRIMARY KEY (dong_code, cat_code, period_id)
);

CREATE INDEX idx_anomaly_rank ON fact_anomaly(period_id, cat_level, score DESC);

CREATE TABLE fact_dong_score (
                                 dong_code         CHAR(8)    NOT NULL REFERENCES dim_dong(dong_code),
                                 period_id         CHAR(6)    NOT NULL REFERENCES dim_period(period_id),
                                 raw_score         NUMERIC(6,3),
                                 pct_score         NUMERIC(6,3),
                                 grade             VARCHAR(10),
                                 anomaly_cat_count SMALLINT   NOT NULL DEFAULT 0,
                                 valid_cat_count   SMALLINT   NOT NULL DEFAULT 0,
                                 PRIMARY KEY (dong_code, period_id)
);