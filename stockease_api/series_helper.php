<?php
/**
 * Date / hour series helper for MySQL.
 *
 * PostgreSQL's generate_series() has no MySQL equivalent, so the report
 * queries build a small literal UNION ALL spine instead (the reports cover
 * days, weeks or 24 hours - never big enough for this to matter).
 *
 * Usage:
 *   $series = series_dates_sql('2026-10-01', '2026-10-07', 'd');
 *   ... FROM $series s LEFT JOIN transactions t ON DATE(t.created_at) = s.d ...
 *
 * Every generated row is emitted as an aliased single-column SELECT so the
 * result can be dropped into any FROM clause.
 */

/** Builds "(SELECT '2026-10-01' AS d UNION ALL SELECT ...)" for each day in [start, end]. */
function series_dates_sql(string $start, string $end, string $alias = 'd'): string
{
    $startDate = new DateTimeImmutable($start);
    $endDate = new DateTimeImmutable($end);
    if ($endDate < $startDate) {
        $tmp = $startDate;
        $startDate = $endDate;
        $endDate = $tmp;
    }

    $parts = [];
    for ($date = $startDate; $date <= $endDate; $date = $date->modify('+1 day')) {
        $parts[] = "SELECT '" . $date->format('Y-m-d') . "' AS `$alias`";
    }
    if (!$parts) {
        $parts[] = "SELECT '" . $startDate->format('Y-m-d') . "' AS `$alias`";
    }

    return '(' . implode(' UNION ALL ', $parts) . ')';
}

/** Builds a spine of 24 hourly timestamps ("YYYY-MM-DD HH:00:00") for the given day. */
function series_hours_sql(string $day, string $alias = 'h'): string
{
    $dayStart = new DateTimeImmutable($day);
    $parts = [];
    for ($hour = 0; $hour < 24; $hour++) {
        $parts[] = "SELECT '" . $dayStart->format('Y-m-d') . ' ' . sprintf('%02d', $hour) . ":00:00' AS `$alias`";
    }
    return '(' . implode(' UNION ALL ', $parts) . ')';
}

/** Builds a spine of hourly timestamps for every hour in [startDay, endDay] (capped). */
function series_hours_range_sql(string $startDay, string $endDay, int $maxHours, string $alias = 'h'): string
{
    $start = new DateTimeImmutable($startDay);
    $end = new DateTimeImmutable($endDay . ' 23:59:59');
    $parts = [];
    for ($time = $start; $time <= $end && count($parts) < $maxHours; $time = $time->modify('+1 hour')) {
        $parts[] = "SELECT '" . $time->format('Y-m-d H:00:00') . "' AS `$alias`";
    }
    if (!$parts) {
        $parts[] = "SELECT '" . $start->format('Y-m-d H:00:00') . "' AS `$alias`";
    }
    return '(' . implode(' UNION ALL ', $parts) . ')';
}
