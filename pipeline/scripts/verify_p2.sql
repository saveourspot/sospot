-- P2 anomaly metrics and dong composite score verification.
-- Run after `python -m pipeline.src.metrics` has loaded the latest snapshots.

SELECT period_id,
       cat_level,
       sample_size_flag,
       COUNT(*) AS combination_count
FROM fact_anomaly
GROUP BY period_id, cat_level, sample_size_flag
ORDER BY period_id, cat_level, sample_size_flag;

SELECT period_id,
       grade,
       COUNT(*) AS combination_count
FROM fact_anomaly
WHERE cat_level = 'MAJOR'
  AND sample_size_flag = 'OK'
GROUP BY period_id, grade
ORDER BY period_id, grade;

SELECT period_id,
       grade,
       COUNT(*) AS dong_count
FROM fact_dong_score
GROUP BY period_id, grade
ORDER BY period_id, grade;

SELECT a.period_id,
       d.dong_name,
       c.cat_name,
       a.store_count,
       a.growth_rate,
       a.city_growth_rate,
       a.relative_gap,
       a.cum_change_rate,
       a.consecutive_decline,
       a.score,
       a.grade
FROM fact_anomaly a
JOIN dim_dong d ON d.dong_code = a.dong_code
JOIN dim_category c ON c.cat_code = a.cat_code
WHERE a.cat_level = 'MAJOR'
  AND a.sample_size_flag = 'OK'
ORDER BY a.period_id DESC, a.score DESC
LIMIT 10;

SELECT s.period_id,
       d.dong_name,
       s.raw_score,
       s.pct_score,
       s.grade,
       s.anomaly_cat_count,
       s.valid_cat_count
FROM fact_dong_score s
JOIN dim_dong d ON d.dong_code = s.dong_code
ORDER BY s.period_id DESC, s.pct_score DESC;
