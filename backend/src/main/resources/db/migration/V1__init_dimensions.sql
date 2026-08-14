CREATE TABLE dim_period (
                            period_id   CHAR(6) PRIMARY KEY,
                            year        SMALLINT NOT NULL,
                            quarter     SMALLINT NOT NULL,
                            base_date   DATE     NOT NULL
);

CREATE TABLE dim_dong (
                          dong_code   CHAR(8) PRIMARY KEY,
                          sigungu     VARCHAR(20) NOT NULL,
                          dong_name   VARCHAR(40) NOT NULL,
                          center_lat  NUMERIC(10,7),
                          center_lng  NUMERIC(10,7)
);

CREATE TABLE dim_category (
                              cat_code    VARCHAR(6) PRIMARY KEY,
                              cat_name    VARCHAR(60) NOT NULL,
                              parent_code VARCHAR(6),
                              cat_level   VARCHAR(6) NOT NULL,
                              CONSTRAINT fk_category_parent FOREIGN KEY (parent_code) REFERENCES dim_category(cat_code)
);

CREATE INDEX idx_category_level ON dim_category(cat_level);