<?php
/**
 * Daily shift helpers.
 *
 * Shifts start automatically the first time an account logs in for the day and
 * are closed on logout (or as soon as a new day's shift begins), so staff never
 * press a "Start Shift" button.
 */

/** Closes any shift that is still open from a previous day. */
function shift_close_stale_shifts(PDO $conn, int $userId): void
{
    if ($userId <= 0) {
        return;
    }

    try {
        $stmt = $conn->prepare("
            UPDATE staff_shifts
            SET status = 'ended', ended_at = CURRENT_TIMESTAMP
            WHERE user_id = :uid AND status = 'active' AND DATE(started_at) < CURRENT_DATE
        ");
        $stmt->execute(['uid' => $userId]);
    } catch (PDOException $e) {
        // staff_shifts may not exist yet if the migration has not been run
    }
}

/** Returns the currently active shift for a user, or null. */
function shift_get_active_shift(PDO $conn, int $userId): ?array
{
    if ($userId <= 0) {
        return null;
    }

    try {
        $stmt = $conn->prepare("
            SELECT ss.id, ss.user_id, ss.store_id, ss.status,
                   ss.started_at AS started_at,
                   ss.ended_at AS ended_at
            FROM staff_shifts ss
            WHERE ss.user_id = :uid AND ss.status = 'active'
            ORDER BY ss.id DESC
            LIMIT 1
        ");
        $stmt->execute(['uid' => $userId]);
        $shift = $stmt->fetch(PDO::FETCH_ASSOC);
        return $shift ?: null;
    } catch (PDOException $e) {
        return null;
    }
}

/**
 * Starts today's shift if the account has not started one yet today.
 *
 * @return array{started: bool, shift: ?array}
 */
function shift_start_daily_shift(PDO $conn, int $userId, int $storeId): array
{
    shift_close_stale_shifts($conn, $userId);

    $existing = shift_get_active_shift($conn, $userId);
    if ($existing !== null) {
        return ['started' => false, 'shift' => $existing];
    }

    if ($storeId <= 0) {
        return ['started' => false, 'shift' => null];
    }

    try {
        $stmt = $conn->prepare("
            INSERT INTO staff_shifts (user_id, store_id, status, started_at)
            VALUES (:uid, :sid, 'active', CURRENT_TIMESTAMP)
        ");
        $stmt->execute(['uid' => $userId, 'sid' => $storeId]);
        $newShiftId = (int)$conn->lastInsertId();
        $fetchStmt = $conn->prepare("
            SELECT id, user_id, store_id, status, started_at
            FROM staff_shifts WHERE id = :id
        ");
        $fetchStmt->execute(['id' => $newShiftId]);
        $shift = $fetchStmt->fetch(PDO::FETCH_ASSOC);
        return ['started' => true, 'shift' => $shift ?: null];
    } catch (PDOException $e) {
        return ['started' => false, 'shift' => null];
    }
}

/** Ends the currently active shift (used on logout). Returns the closed shift or null. */
function shift_end_active_shift(PDO $conn, int $userId): ?array
{
    $shift = shift_get_active_shift($conn, $userId);
    if ($shift === null) {
        return null;
    }

    try {
        $stmt = $conn->prepare("
            UPDATE staff_shifts
            SET status = 'ended', ended_at = CURRENT_TIMESTAMP
            WHERE id = :id AND user_id = :uid AND status = 'active'
        ");
        $stmt->execute(['id' => (int)$shift['id'], 'uid' => $userId]);
        $closedStmt = $conn->prepare("
            SELECT id, started_at, ended_at
            FROM staff_shifts WHERE id = :id
        ");
        $closedStmt->execute(['id' => (int)$shift['id']]);
        return $closedStmt->fetch(PDO::FETCH_ASSOC) ?: null;
    } catch (PDOException $e) {
        return null;
    }
}
