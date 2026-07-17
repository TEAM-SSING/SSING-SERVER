<?php

declare(strict_types=1);

final class SsingAdminerAutoLogin extends \Adminer\Plugin
{
    private const LOGIN_MARKER = 'ssing-managed-login';

    private string $server;
    private string $username;
    private string $database;
    private string $passwordFile;

    public function __construct()
    {
        $this->server = $this->requiredEnvironment('SSING_ADMINER_DB_SERVER');
        $this->username = $this->requiredEnvironment('SSING_ADMINER_DB_USERNAME');
        $this->database = $this->requiredEnvironment('SSING_ADMINER_DB_NAME');
        $this->passwordFile = $this->requiredEnvironment('SSING_ADMINER_DB_PASSWORD_FILE');

        // 비밀번호 파일이 빠진 배포는 로그인 화면으로 우회하지 않고 즉시 실패시킨다.
        $this->password();
    }

    /** @return array{string, string, string} */
    public function credentials(): array
    {
        return [$this->server, $this->username, $this->password()];
    }

    public function loginForm(): bool
    {
        echo $this->hiddenField('auth[driver]', 'server');
        echo $this->hiddenField('auth[server]', $this->server);
        echo $this->hiddenField('auth[username]', $this->username);
        echo $this->hiddenField('auth[password]', self::LOGIN_MARKER);
        echo $this->hiddenField('auth[db]', $this->database);
        echo '<p>SSING dev DB 계정으로 자동 로그인합니다.</p>';
        echo '<p><button type="submit">다시 로그인</button></p>';

        // 로그인 실패 뒤에는 무한 재시도를 막고 개발자가 오류를 확인할 수 있게 둔다.
        if (!isset($_GET['username'])) {
            echo \Adminer\script('document.currentScript.closest("form").submit();');
        }

        return true;
    }

    public function login(string $login, string $password): bool|string
    {
        if (
            hash_equals($this->username, $login)
            && hash_equals(self::LOGIN_MARKER, $password)
        ) {
            return true;
        }

        return 'SSING dev 자동 로그인 정보가 올바르지 않습니다.';
    }

    private function requiredEnvironment(string $name): string
    {
        $value = getenv($name);
        if ($value === false || $value === '') {
            throw new RuntimeException($name . ' is required.');
        }

        return $value;
    }

    private function password(): string
    {
        if (!is_file($this->passwordFile) || !is_readable($this->passwordFile)) {
            throw new RuntimeException('Adminer DB password file is not readable.');
        }

        $password = file_get_contents($this->passwordFile);
        if ($password === false || $password === '') {
            throw new RuntimeException('Adminer DB password file is empty.');
        }

        return $password;
    }

    private function hiddenField(string $name, string $value): string
    {
        return sprintf(
            '<input type="hidden" name="%s" value="%s">',
            htmlspecialchars($name, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'),
            htmlspecialchars($value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'),
        );
    }
}

return new SsingAdminerAutoLogin();
