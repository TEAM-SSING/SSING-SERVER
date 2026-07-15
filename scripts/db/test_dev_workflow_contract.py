from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
RESET_WORKFLOW = ROOT / ".github/workflows/reset-dev-db.yml"
CI_WORKFLOW = ROOT / ".github/workflows/ci.yml"
DEPLOY_WORKFLOW = ROOT / ".github/workflows/deploy-dev.yml"
DB_CHECK_WORKFLOW = ROOT / ".github/workflows/db-seed-check.yml"
DEV_COMPOSE = ROOT / "deploy/docker-compose.dev.yml"
BUILD_GRADLE = ROOT / "build.gradle"
DEV_ENV_EXAMPLE = ROOT / "deploy/env.dev.example"
DEV_RUNNERS = (
    ROOT / "scripts/db/dev-common.sh",
    ROOT / "scripts/db/reset-dev.sh",
    ROOT / "scripts/db/migrate-dev.sh",
)


def indented_block(text, header, indent):
    lines = text.splitlines()
    header_line = " " * indent + header
    try:
        start = lines.index(header_line)
    except ValueError as error:
        raise AssertionError(f"missing YAML block: {header_line}") from error

    end = len(lines)
    for index in range(start + 1, len(lines)):
        line = lines[index]
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        current_indent = len(line) - len(line.lstrip())
        if current_indent <= indent:
            end = index
            break
    return "\n".join(lines[start:end])


class DevWorkflowContractTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.reset = RESET_WORKFLOW.read_text(encoding="utf-8")
        cls.ci = CI_WORKFLOW.read_text(encoding="utf-8")
        cls.deploy = DEPLOY_WORKFLOW.read_text(encoding="utf-8")
        cls.db_check = DB_CHECK_WORKFLOW.read_text(encoding="utf-8")
        cls.dev_compose = DEV_COMPOSE.read_text(encoding="utf-8")
        cls.build_gradle = BUILD_GRADLE.read_text(encoding="utf-8")
        cls.dev_env_example = DEV_ENV_EXAMPLE.read_text(encoding="utf-8")
        cls.reset_job = indented_block(cls.reset, "reset:", 2)
        cls.ci_verify_job = indented_block(cls.ci, "verify:", 2)
        cls.ci_deploy_job = indented_block(cls.ci, "deploy-dev:", 2)
        cls.deploy_job = indented_block(cls.deploy, "build-and-deploy:", 2)

    def test_reset_has_only_scenario_and_boolean_confirmation_inputs(self):
        workflow_dispatch = indented_block(self.reset, "workflow_dispatch:", 2)
        inputs_block = indented_block(workflow_dispatch, "inputs:", 4)
        input_names = re.findall(r"^      ([A-Za-z][A-Za-z0-9]*):$", inputs_block, re.MULTILINE)
        scenario_block = indented_block(inputs_block, "scenarioKey:", 6)
        scenario_options = indented_block(scenario_block, "options:", 8)
        options = re.findall(r"^          - ([a-z0-9-]+)$", scenario_options, re.MULTILINE)

        self.assertEqual(["scenarioKey", "confirmReset"], input_names)
        self.assertEqual(
            [
                "matching-price-vivaldi",
                "matching-no-candidate-alpensia",
                "matching-multi-request-oak",
                "pm-full-requested-catalog",
            ],
            options,
        )
        self.assertRegex(scenario_block, r"(?m)^        type: choice$")
        confirmation_block = indented_block(inputs_block, "confirmReset:", 6)
        self.assertRegex(confirmation_block, r"(?m)^        type: boolean$")
        self.assertRegex(confirmation_block, r"(?m)^        default: false$")
        self.assertNotRegex(inputs_block, r"(?i)reason|사유|scheduler")

    def test_reset_is_main_only_allowlisted_and_fail_closed(self):
        preflight_step = indented_block(
            self.reset_job,
            "- name: 파괴 작업 입력 재검증",
            6,
        )
        self.assertIn('CONFIRM_RESET: ${{ inputs.confirmReset }}', preflight_step)
        self.assertIn('GIT_REF: ${{ github.ref }}', preflight_step)
        self.assertIn('if [ "$CONFIRM_RESET" != "true" ]', preflight_step)
        self.assertIn('if [ "$GIT_REF" != "refs/heads/main" ]', preflight_step)
        for scenario in (
            "matching-price-vivaldi",
            "matching-no-candidate-alpensia",
            "matching-multi-request-oak",
            "pm-full-requested-catalog",
        ):
            self.assertIn(scenario, preflight_step)

        self.assertRegex(self.reset_job, r"(?m)^    environment: dev-reset$")
        self.assertNotRegex(self.reset, r"(?m)^  preflight:$")
        self.assertIn("./scripts/db/reset-dev.sh --confirm-dev-reset", self.reset_job)

    def test_reset_and_deploy_share_one_max_queue(self):
        for workflow in (self.reset, self.deploy):
            concurrency = indented_block(workflow, "concurrency:", 0)
            self.assertIn("group: ssing-dev-mutation", concurrency)
            self.assertIn("queue: max", concurrency)
            self.assertNotIn("cancel-in-progress: true", concurrency)

    def test_gradle_shards_are_complementary_and_fail_closed(self):
        def shard_block(shard):
            match = re.search(
                rf"case '{re.escape(shard)}':(?P<body>.*?)\n\s+break",
                self.build_gradle,
                re.DOTALL,
            )
            self.assertIsNotNone(match, f"missing Gradle shard: {shard}")
            return match.group("body")

        seed_shard = shard_block("seed-migration")
        application_shard = shard_block("application")
        seed_contract = (
            "includeTestsMatching "
            "'org.sopt.ssingserver.database.DatabaseSeedContractTest'"
        )
        migration_contract = "includeTestsMatching '*MigrationTest'"
        seed_exclusion = seed_contract.replace("includeTestsMatching", "excludeTestsMatching")
        migration_exclusion = migration_contract.replace(
            "includeTestsMatching",
            "excludeTestsMatching",
        )

        for contract in (seed_contract, migration_contract):
            self.assertIn(contract, seed_shard)
            self.assertNotIn(contract, application_shard)
        for exclusion in (seed_exclusion, migration_exclusion):
            self.assertIn(exclusion, application_shard)
            self.assertNotIn(exclusion, seed_shard)

        self.assertIn("setFailOnNoMatchingTests(true)", self.build_gradle)
        self.assertRegex(self.build_gradle, r"(?m)^\s+case null:$")
        self.assertIn(
            'throw new GradleException("Unsupported integrationTestShard: ${shard}")',
            self.build_gradle,
        )

    def test_ci_uses_two_complementary_runners_before_exact_sha_deploy(self):
        self.assertIn("max-parallel: 2", self.ci_verify_job)
        matrix_pairs = re.findall(
            r"(?m)^            shard: ([a-z-]+)\n"
            r"            gradle-tasks: ([A-Za-z][A-Za-z0-9-]*)$",
            self.ci_verify_job,
        )
        self.assertEqual(
            [
                ("application", "build"),
                ("seed-migration", "integrationTest"),
            ],
            matrix_pairs,
        )
        self.assertIn("-PintegrationTestShard=${{ matrix.shard }}", self.ci_verify_job)

        fast_contract_commands = (
            "bash -n scripts/db/*.sh",
            "bash scripts/db/test-mysql-client-auth.sh",
            "bash scripts/db/test-dev-runner-contract.sh",
            "python3 scripts/db/test_dev_workflow_contract.py",
        )
        self.assertIn("if: matrix.shard == 'application'", self.ci_verify_job)
        for command in fast_contract_commands:
            self.assertIn(command, self.ci_verify_job)
            self.assertNotIn(command, self.db_check)

        self.assertIn(
            "if: github.event_name == 'push' && github.ref == 'refs/heads/main'",
            self.ci_deploy_job,
        )
        for bypass in ("always()", "failure()", "cancelled()"):
            self.assertNotIn(bypass, self.ci_deploy_job)
        self.assertIn("needs: verify", self.ci_deploy_job)
        self.assertIn("uses: ./.github/workflows/deploy-dev.yml", self.ci_deploy_job)
        self.assertIn("deploy_sha: ${{ github.sha }}", self.ci_deploy_job)
        self.assertIn("workflow_call:", self.deploy)
        self.assertIn("workflow_dispatch:", self.deploy)
        self.assertNotRegex(self.deploy, r"(?m)^  push:$")

        self.assertIn("DEPLOY_SHA: ${{ inputs.deploy_sha || github.sha }}", self.deploy)
        self.assertNotIn("./gradlew", self.deploy_job)
        self.assertIn("ref: ${{ env.DEPLOY_SHA }}", self.deploy_job)
        self.assertIn(
            'current_main_sha="$(gh api "repos/${GITHUB_REPOSITORY}/commits/main" '
            "--jq '.sha')\"",
            self.deploy_job,
        )
        self.assertIn(
            'if [ "$DEPLOY_SHA" != "$current_main_sha" ]; then',
            self.deploy_job,
        )
        self.assertIn("dev-${DEPLOY_SHA}", self.deploy_job)
        self.assertIn("ssing-server@dev-${{ env.DEPLOY_SHA }}", self.deploy_job)
        self.assertNotIn("dev-latest", self.deploy_job)
        self.assertNotIn("dev-latest", self.dev_env_example)

        for workflow in (self.ci, self.db_check):
            self.assertIn(
                "github.event.pull_request.number || github.run_id",
                workflow,
            )
            self.assertNotIn("github.head_ref", workflow)

    def test_runtime_and_migration_credentials_and_ssh_trust_are_separated(self):
        self.assertIn("SSING_DEV_DB_MIGRATION_PASSWORD", self.reset)
        for source in (RESET_WORKFLOW, *DEV_RUNNERS):
            source_text = source.read_text(encoding="utf-8")
            self.assertNotIn("SSING_DEV_DB_RESET_PASSWORD", source_text)
            self.assertNotIn("SSING_DEV_DB_RESET_USERNAME", source_text)
        self.assertIn("SSING_DEV_RUNTIME_DB_USERNAME", self.reset)
        self.assertIn("SSING_DEV_RUNTIME_DB_USERNAME", self.deploy)
        self.assertNotIn("SSING_DEV_DATASOURCE_PASSWORD", self.reset)
        for workflow in (self.reset, self.deploy):
            self.assertIn("EC2_SSH_KNOWN_HOSTS", workflow)
            self.assertNotIn("ssh-keyscan", workflow)

    def test_db_targets_are_masked_before_remote_db_commands(self):
        for job, runner_command in (
            (self.reset_job, "./scripts/db/reset-dev.sh --confirm-dev-reset"),
            (self.deploy_job, "./scripts/db/migrate-dev.sh"),
        ):
            mask_step = indented_block(
                job,
                "- name: dev DB 대상 로그 마스킹",
                6,
            )
            self.assertIn("SSING_DEV_DB_HOST", mask_step)
            self.assertIn("SSING_DEV_RUNTIME_DATASOURCE_URL", mask_step)
            self.assertIn("jdbc:mysql://", mask_step)
            self.assertIn("::add-mask::%s", mask_step)
            self.assertLess(
                job.index("- name: dev DB 대상 로그 마스킹"),
                job.index(runner_command),
            )

    def test_remote_report_failure_is_marker_aware_and_fail_closed(self):
        report_step = indented_block(
            self.reset_job,
            "- name: 원격 실행기 결과 회수",
            6,
        )

        self.assertIn("if scp ", report_step)
        self.assertIn(".dev-db-reset-incomplete", report_step)
        for marker_state in ("PRESENT", "ABSENT", "UNKNOWN"):
            self.assertIn(marker_state, report_step)
        self.assertIn(".dev-reset-report.md", report_step)
        self.assertIn("exit 1", report_step)
        self.assertNotIn("continue-on-error: true", report_step)
        self.assertNotIn("원격 실행기 시작 전 실패", self.reset_job)
        self.assertIn("원격 상태를 확정할 수 없습니다", self.reset_job)
        self.assertIn("먼저 실패한 첫 Actions step의 한글 로그", report_step)
        self.assertIn("${{ github.run_id }}-${{ github.run_attempt }}", self.reset_job)

    def test_incomplete_reset_blocks_deploy_and_migration_precedes_restart(self):
        marker_position = self.deploy_job.index("미완료 dev DB reset 사전 차단")
        upload_position = self.deploy_job.index("EC2 배포 파일 업로드")
        runtime_env_position = self.deploy_job.index("EC2 런타임 환경변수 생성")
        migration_position = self.deploy_job.index("./scripts/db/migrate-dev.sh")
        activate_env_position = self.deploy_job.index("mv -f .env.next .env")
        restart_position = self.deploy_job.index("up -d --remove-orphans")
        health_position = self.deploy_job.index('/actuator/health | grep -q')
        restart_step = indented_block(
            self.deploy_job,
            "- name: EC2 Docker Compose 재시작",
            6,
        )
        health_step = indented_block(
            self.deploy_job,
            "- name: 애플리케이션 헬스체크",
            6,
        )

        self.assertLess(marker_position, upload_position)
        self.assertLess(marker_position, runtime_env_position)
        self.assertLess(upload_position, runtime_env_position)
        self.assertLess(runtime_env_position, migration_position)
        self.assertLess(migration_position, restart_position)
        self.assertLess(migration_position, activate_env_position)
        self.assertLess(restart_position, activate_env_position)
        self.assertLess(health_position, activate_env_position)
        self.assertLess(marker_position, restart_position)
        self.assertNotIn("mv -f .env.next .env", restart_step)
        self.assertRegex(
            restart_step,
            r"sudo env SSING_RUNTIME_ENV_FILE=\.env\.next \\\n"
            r"\s+docker compose --env-file \.env\.next -f \"\$COMPOSE_FILE\" "
            r"up -d --remove-orphans",
        )
        self.assertRegex(
            health_step,
            r"if curl [^\n]+; then\n"
            r"\s+echo \"헬스체크 성공:[^\n]+\"\n"
            r"\s+echo \"검증을 마친 런타임 설정으로 원자적 전환\"\n"
            r"\s+if ! mv -f \.env\.next \.env; then",
        )
        self.assertIn("런타임 설정 전환 실패", health_step)
        self.assertIn("기존 .env는 교체하지 않았습니다", restart_step)
        marker_step = indented_block(
            self.deploy_job,
            "- name: 미완료 dev DB reset 사전 차단",
            6,
        )
        self.assertIn("test ! -e '$DEPLOY_DIR/.dev-db-reset-incomplete'", marker_step)
        self.assertGreaterEqual(self.deploy_job.count(".dev-db-reset-incomplete"), 2)
        self.assertIn("--env-file .env.next", self.deploy_job)
        self.assertIn("SSING_RUNTIME_ENV_FILE=.env.next", self.deploy_job)
        self.assertIn("${SSING_RUNTIME_ENV_FILE:-.env}", self.dev_compose)
        self.assertIn("SSING_JPA_DDL_AUTO: validate", self.deploy_job)

    def test_disposable_db_check_does_not_reference_shared_dev_credentials(self):
        forbidden = (
            "SSING_DEV_DATASOURCE_URL",
            "SSING_DEV_DB_RESET_PASSWORD",
            "SSING_DEV_DB_MIGRATION_PASSWORD",
            "environment: dev",
            "environment: dev-reset",
        )
        for value in forbidden:
            self.assertNotIn(value, self.db_check)
        self.assertNotIn("./gradlew", self.db_check)
        self.assertEqual(
            1,
            self.db_check.count("./scripts/db/reset-all-local.sh --confirm-local-reset\n"),
        )


if __name__ == "__main__":
    unittest.main()
