\set ON_ERROR_STOP on

\echo '=== SOSpot P1 validation ==='

\echo '[1] MAJOR store totals by period'
SELECT TRIM(period_id) AS period_id, SUM(store_count) AS total_store_count
FROM fact_store_count
WHERE cat_level = 'MAJOR'
GROUP BY period_id
ORDER BY period_id;

\echo '[2] Dimension counts'
SELECT COUNT(*) AS dong_count FROM dim_dong;
SELECT cat_level, COUNT(*) AS category_count
FROM dim_category
GROUP BY cat_level
ORDER BY cat_level;

\echo '[3] BSI structure and Daejeon values'
SELECT COUNT(DISTINCT metric_name) AS metric_count FROM fact_bsi;
SELECT TRIM(period_month) AS period_month, value
FROM fact_bsi
WHERE metric_name = '대전체감'
  AND period_month IN ('2026-04', '2026-05', '2026-06')
ORDER BY period_month;
SELECT ROUND(AVG(value), 1) AS daejeon_q2_available_month_average
FROM fact_bsi
WHERE metric_name = '대전체감'
  AND period_month BETWEEN '2026-04' AND '2026-06';

\echo '[4] Integrity checks'
SELECT COUNT(*) AS category_level_mismatch
FROM fact_store_count fact
JOIN dim_category category ON category.cat_code = fact.cat_code
WHERE fact.cat_level <> category.cat_level;

SELECT COUNT(*) AS missing_center_count
FROM dim_dong
WHERE center_lat IS NULL OR center_lng IS NULL;

DO $$
DECLARE
    actual INTEGER;
BEGIN
    SELECT COUNT(*) INTO actual FROM dim_dong;
    IF actual <> 82 THEN
        RAISE EXCEPTION 'dim_dong count mismatch: expected 82, got %', actual;
    END IF;

    SELECT COUNT(*) INTO actual FROM dim_category WHERE cat_level = 'MAJOR';
    IF actual <> 10 THEN
        RAISE EXCEPTION 'MAJOR category count mismatch: expected 10, got %', actual;
    END IF;

    SELECT COUNT(*) INTO actual FROM dim_category WHERE cat_level = 'MIDDLE';
    IF actual <> 74 THEN
        RAISE EXCEPTION 'MIDDLE category count mismatch: expected 74, got %', actual;
    END IF;

    SELECT COUNT(DISTINCT metric_name) INTO actual FROM fact_bsi;
    IF actual <> 70 THEN
        RAISE EXCEPTION 'BSI metric count mismatch: expected 70, got %', actual;
    END IF;
END $$;

DO $$
DECLARE
    expected_period CHAR(6);
    expected_total INTEGER;
    actual_total BIGINT;
BEGIN
    FOR expected_period, expected_total IN
        SELECT * FROM (
            VALUES
                ('202512'::CHAR(6), 78246),
                ('202603'::CHAR(6), 78607),
                ('202606'::CHAR(6), 80704)
        ) AS expected(period_id, total)
    LOOP
        SELECT SUM(store_count) INTO actual_total
        FROM fact_store_count
        WHERE period_id = expected_period AND cat_level = 'MAJOR';

        IF actual_total IS DISTINCT FROM expected_total THEN
            RAISE EXCEPTION
                'Store total mismatch for %: expected %, got %',
                expected_period,
                expected_total,
                actual_total;
        END IF;
    END LOOP;
END $$;

DO $$
DECLARE
    q2_average NUMERIC;
BEGIN
    IF (SELECT value FROM fact_bsi
        WHERE period_month = '2026-04' AND metric_name = '대전체감') IS NULL THEN
        RAISE EXCEPTION 'Missing 2026-04 Daejeon sentiment BSI';
    END IF;

    IF (SELECT value FROM fact_bsi
        WHERE period_month = '2026-05' AND metric_name = '대전체감') IS NULL THEN
        RAISE EXCEPTION 'Missing 2026-05 Daejeon sentiment BSI';
    END IF;

    SELECT ROUND(AVG(value), 1) INTO q2_average
    FROM fact_bsi
    WHERE metric_name = '대전체감'
      AND period_month BETWEEN '2026-04' AND '2026-06';

    IF q2_average IS DISTINCT FROM 60.0 THEN
        RAISE EXCEPTION 'Q2 Daejeon BSI mismatch: expected 60.0, got %', q2_average;
    END IF;
END $$;

\echo 'P1 validation passed.'
