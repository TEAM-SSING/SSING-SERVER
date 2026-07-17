<?php

declare(strict_types=1);

namespace Adminer {
    class Plugin
    {
    }

    function script(string $source): string
    {
        return '<script>' . $source . '</script>';
    }
}

namespace {
    function failTest(string $message): never
    {
        fwrite(STDERR, "dev Adminer auto-login contract test failed: {$message}\n");
        exit(1);
    }

    function assertContract(bool $condition, string $message): void
    {
        if (!$condition) {
            failTest($message);
        }
    }

    $secretFile = tempnam(sys_get_temp_dir(), 'ssing-adminer-secret-');
    if ($secretFile === false) {
        failTest('could not create a temporary secret file');
    }

    try {
        $realPassword = 'real-db-password-that-must-not-reach-html';
        file_put_contents($secretFile, $realPassword);

        putenv('SSING_ADMINER_DB_SERVER=dev-db.example.internal');
        putenv('SSING_ADMINER_DB_USERNAME=admin');
        putenv('SSING_ADMINER_DB_NAME=ssing');
        putenv('SSING_ADMINER_DB_PASSWORD_FILE=' . $secretFile);

        $_GET = [];
        $plugin = require dirname(__DIR__, 2) . '/deploy/adminer/001-ssing-autologin.php';

        assertContract(
            $plugin->credentials() === [
                'dev-db.example.internal',
                'admin',
                $realPassword,
            ],
            'credentials() must read the real password from the server-side file',
        );
        assertContract(
            $plugin->login('admin', 'ssing-managed-login') === true,
            'the managed username and marker must be accepted',
        );
        assertContract(
            $plugin->login('other-user', 'ssing-managed-login') !== true,
            'a different username must fail closed',
        );
        assertContract(
            $plugin->login('admin', $realPassword) !== true,
            'the real DB password must never be accepted from the browser form',
        );

        ob_start();
        $plugin->loginForm();
        $initialForm = (string) ob_get_clean();
        foreach ([
            'name="auth[driver]" value="server"',
            'name="auth[server]" value="dev-db.example.internal"',
            'name="auth[username]" value="admin"',
            'name="auth[password]" value="ssing-managed-login"',
            'name="auth[db]" value="ssing"',
            '.submit()',
        ] as $requiredFragment) {
            assertContract(
                str_contains($initialForm, $requiredFragment),
                "initial form is missing {$requiredFragment}",
            );
        }
        assertContract(
            !str_contains($initialForm, $realPassword),
            'the real DB password leaked into the rendered form',
        );

        $_GET = ['username' => 'admin'];
        ob_start();
        $plugin->loginForm();
        $retryForm = (string) ob_get_clean();
        assertContract(
            !str_contains($retryForm, '.submit()'),
            'a failed login must not enter an automatic retry loop',
        );

        putenv('SSING_ADMINER_DB_PASSWORD_FILE=/missing/adminer-secret');
        $failedClosed = false;
        try {
            new \SsingAdminerAutoLogin();
        } catch (\RuntimeException) {
            $failedClosed = true;
        }
        assertContract($failedClosed, 'a missing password file must fail closed');
    } finally {
        @unlink($secretFile);
    }

    echo "dev Adminer auto-login contract tests passed\n";
}
