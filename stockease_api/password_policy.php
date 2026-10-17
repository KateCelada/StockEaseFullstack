<?php
/**
 * Shared password strength policy for StockEase.
 *
 * Used by the API (registration, staff/admin creation, password changes), the
 * web admin panel and the Android app (which mirrors these rules client-side
 * and shows a live strength meter). Keeping one definition here means the
 * "Weak / Fair / Strong" verdict can never drift between the three surfaces.
 *
 * Levels: 0 Too weak | 1 Weak | 2 Fair | 3 Strong | 4 Very strong
 */

/** Minimum length accepted anywhere in the system. */
const PASSWORD_MIN_LENGTH = 8;

/** Scores a password from 0 (too weak) to 4 (very strong). */
function password_strength_score(string $password): int
{
    $length = strlen($password);
    if ($length === 0) {
        return 0;
    }

    $score = 0;
    if ($length >= PASSWORD_MIN_LENGTH) {
        $score++;
    }
    if ($length >= 12) {
        $score++;
    }
    if (preg_match('/[a-z]/', $password) && preg_match('/[A-Z]/', $password)) {
        $score++;
    }
    if (preg_match('/\d/', $password)) {
        $score++;
    }
    if (preg_match('/[^A-Za-z0-9]/', $password)) {
        $score++;
    }

    // A very short password can never look strong, however many symbol classes it uses
    if ($length < PASSWORD_MIN_LENGTH) {
        $score = min($score, 1);
    }

    return max(0, min(4, $score));
}

/** Human readable label for a score (0-4). */
function password_strength_label(int $score): string
{
    $labels = ['Too weak', 'Weak', 'Fair', 'Strong', 'Very strong'];
    return $labels[max(0, min(4, $score))];
}

/** Lists the concrete rules a password breaks (empty array = acceptable). */
function password_policy_errors(string $password): array
{
    $errors = [];
    if (strlen($password) < PASSWORD_MIN_LENGTH) {
        $errors[] = 'at least ' . PASSWORD_MIN_LENGTH . ' characters';
    }
    if (!preg_match('/[A-Za-z]/', $password)) {
        $errors[] = 'at least one letter';
    }
    if (!preg_match('/\d/', $password)) {
        $errors[] = 'at least one number';
    }
    return $errors;
}

/** True when the password satisfies the minimum policy. */
function password_is_acceptable(string $password): bool
{
    return empty(password_policy_errors($password));
}

/**
 * Standard failure payload for endpoints that create or change a password.
 * Returns the array AND echoes it, so callers can `return password_policy_fail($pw);`
 */
function password_policy_fail(string $password): array
{
    $errors = password_policy_errors($password);
    $response = [
        'success' => false,
        'message' => 'Password is too weak. It must contain ' . implode(', ', $errors) . '.',
        'password_score' => password_strength_score($password),
        'password_label' => password_strength_label(password_strength_score($password))
    ];
    echo json_encode($response);
    return $response;
}
