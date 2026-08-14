CREATE TABLE fact_bsi (
                          period_month CHAR(7)     NOT NULL,
                          metric_name  VARCHAR(40) NOT NULL,
                          value        NUMERIC(6,2),
                          PRIMARY KEY (period_month, metric_name)
);