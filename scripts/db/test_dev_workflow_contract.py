from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
RESET_WORKFLOW = ROOT / ".github/workflows/reset-dev-db.yml"
CI_WORKFLOW = ROOT / ".github/workflows/ci.yml"
DEPLOY_WORKFLOW = ROOT / ".github/workflows/deploy-dev.yml"
DB_CHECK_WORKFLOW = ROOT / ".github/workflows/db-seed-check.yml"
LEGACY_MIGRATION_WORKFLOW = ROOT / ".github/workflows/legacy-migration-test.yml"
DEV_COMPOSE = ROOT / "deploy/docker-compose.dev.yml"
BUILD_GRADLE = ROOT / "build.gradle"
DEV_ENV_EXAMPLE = ROOT / "deploy/env.dev.example"
INTEGRATION_SOURCE_ROOT = ROOT / "src/integrationTest/java"
INTEGRATION_PROFILE = ROOT / "src/integrationTest/resources/application-integration-test.properties"
DEV_RUNNERS = (
    ROOT / "scripts/db/dev-common.sh",
    ROOT / "scripts/db/reset-dev.sh",
    ROOT / "scripts/db/migrate-dev.sh",
    ROOT / "scripts/db/prepare-dev-deploy-db.sh",
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
        cls.legacy_migration = LEGACY_MIGRATION_WORKFLOW.read_text(encoding="utf-8")
        cls.dev_compose = DEV_COMPOSE.read_text(encoding="utf-8")
        cls.build_gradle = BUILD_GRADLE.read_text(encoding="utf-8")
        cls.dev_env_example = DEV_ENV_EXAMPLE.read_text(encoding="utf-8")
        cls.integration_profile = INTEGRATION_PROFILE.read_text(encoding="utf-8")
        cls.reset_job = indented_block(cls.reset, "reset:", 2)
        cls.ci_verify_job = indented_block(cls.ci, "verify:", 2)
        cls.ci_build_job = indented_block(cls.ci, "build-dev-image:", 2)
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
        required_seed_classes = (
            "DatabaseBootstrapContractTest",
            "DatabaseSeedContractTest",
            "MatchingRequestV4ConstraintIntegrationTest",
        )
        migration_contract = "includeTestsMatching '*MigrationTest'"
        migration_exclusion = migration_contract.replace(
            "includeTestsMatching",
            "excludeTestsMatching",
        )

        for class_name in required_seed_classes:
            contract = (
                "includeTestsMatching "
                f"'org.sopt.ssingserver.database.{class_name}'"
            )
            exclusion = contract.replace("includeTestsMatching", "excludeTestsMatching")
            self.assertIn(contract, seed_shard)
            self.assertNotIn(contract, application_shard)
            self.assertIn(exclusion, application_shard)
            self.assertNotIn(exclusion, seed_shard)
        self.assertIn(migration_contract, seed_shard)
        self.assertNotIn(migration_contract, application_shard)
        self.assertIn(migration_exclusion, application_shard)
        self.assertNotIn(migration_exclusion, seed_shard)

        self.assertIn("setFailOnNoMatchingTests(true)", self.build_gradle)
        self.assertIn("maxParallelForks = 1", self.build_gradle)
        self.assertIn("forkEvery = 0", self.build_gradle)
        self.assertEqual(
            2,
            self.build_gradle.count(
                "systemProperty 'junit.jupiter.execution.parallel.enabled', 'false'"
            ),
        )
        self.assertIn("excludeTags 'legacy-migration'", self.build_gradle)
        self.assertIn("tasks.register('legacyMigrationTest', Test)", self.build_gradle)
        self.assertIn("includeTags 'legacy-migration'", self.build_gradle)
        self.assertRegex(self.build_gradle, r"(?m)^\s+case null:$")
        self.assertIn(
            'throw new GradleException("Unsupported integrationTestShard: ${shard}")',
            self.build_gradle,
        )

    def test_integration_tests_share_one_sequential_mysql_container(self):
        sources = sorted(INTEGRATION_SOURCE_ROOT.rglob("*.java"))
        source_text = {
            source: source.read_text(encoding="utf-8")
            for source in sources
        }
        combined = "\n".join(source_text.values())

        self.assertEqual(1, combined.count("new MySQLContainer"))
        self.assertEqual(1, combined.count("MYSQL.start()"))
        self.assertNotIn("MYSQL.stop()", combined)
        self.assertNotIn("withReuse(true)", combined)

        container_owners = [
            source.relative_to(ROOT).as_posix()
            for source, text in source_text.items()
            if "new MySQLContainer" in text
        ]
        self.assertEqual(
            [
                "src/integrationTest/java/org/sopt/ssingserver/database/"
                "support/SharedMySqlDatabase.java"
            ],
            container_owners,
        )

        flyway_clean_owners = [
            source.relative_to(ROOT).as_posix()
            for source, text in source_text.items()
            if re.search(r"\.\s*clean\s*\(\s*\)", text)
        ]
        expected_legacy_owners = {
            "src/integrationTest/java/org/sopt/ssingserver/database/"
            "MatchingRequestV4MigrationTest.java",
            "src/integrationTest/java/org/sopt/ssingserver/database/"
            "NotificationV5MigrationTest.java",
        }
        legacy_tag_owners = {
            source.relative_to(ROOT).as_posix()
            for source, text in source_text.items()
            if '@Tag("legacy-migration")' in text
        }
        self.assertEqual(expected_legacy_owners, set(flyway_clean_owners))
        self.assertEqual(expected_legacy_owners, legacy_tag_owners)

    def test_ci_uses_two_complementary_runners_before_exact_sha_deploy(self):
        self.assertNotIn("publish-unit-test-result-action", self.ci)
        self.assertNotIn("checks: write", self.ci)
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
            "bash scripts/db/test-install-dev-release.sh",
            "bash scripts/db/test-dev-runner-contract.sh",
            "python3 scripts/db/test_dev_workflow_contract.py",
        )
        self.assertIn("if: matrix.shard == 'application'", self.ci_verify_job)
        for command in fast_contract_commands:
            self.assertIn(command, self.ci_verify_job)
            self.assertNotIn(command, self.db_check)

        self.assertIn(
            "if: github.event_name == 'push' && github.ref == 'refs/heads/main'",
            self.ci_build_job,
        )
        self.assertRegex(
            self.ci_build_job,
            r"(?m)^    environment:\n      name: dev\n      deployment: false$",
        )
        self.assertNotRegex(self.ci_build_job, r"(?m)^    needs:")
        self.assertIn("image_digest: ${{ steps.build.outputs.digest }}", self.ci_build_job)
        self.assertIn("id: build", self.ci_build_job)
        self.assertIn("push: true", self.ci_build_job)
        self.assertIn(
            "tags: ${{ vars.DOCKERHUB_IMAGE }}:dev-${{ github.sha }}",
            self.ci_build_job,
        )
        self.assertIn(
            "cache-to: type=gha,mode=max,ignore-error=true",
            self.ci_build_job,
        )
        self.assertIn(
            "cache-to: type=gha,mode=max,ignore-error=true",
            self.deploy_job,
        )

        self.assertIn(
            "if: github.event_name == 'push' && github.ref == 'refs/heads/main'",
            self.ci_deploy_job,
        )
        for bypass in ("always()", "failure()", "cancelled()"):
            self.assertNotIn(bypass, self.ci_deploy_job)
        self.assertRegex(
            self.ci_deploy_job,
            r"(?m)^    needs:\n      - verify\n      - build-dev-image$",
        )
        self.assertIn("uses: ./.github/workflows/deploy-dev.yml", self.ci_deploy_job)
        self.assertIn("deploy_sha: ${{ github.sha }}", self.ci_deploy_job)
        self.assertIn(
            "image_digest: ${{ needs.build-dev-image.outputs.image_digest }}",
            self.ci_deploy_job,
        )
        self.assertIn("workflow_call:", self.deploy)
        self.assertIn("workflow_dispatch:", self.deploy)
        self.assertNotRegex(self.deploy, r"(?m)^  push:$")

        self.assertIn("DEPLOY_SHA: ${{ inputs.deploy_sha || github.sha }}", self.deploy)
        self.assertIn("image_digest:", self.deploy)
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
        self.assertIn("IMAGE_REF=%s:dev-%s@%s", self.deploy_job)
        self.assertIn("PREBUILT_IMAGE_DIGEST: ${{ inputs.image_digest }}", self.deploy_job)
        self.assertIn("ssing-server@dev-${{ env.DEPLOY_SHA }}", self.deploy_job)
        self.assertNotIn("dev-latest", self.deploy_job)
        self.assertNotIn("dev-latest", self.dev_env_example)

        self.assertIn(
            "github.event.pull_request.number || github.run_id",
            self.ci,
        )
        self.assertNotIn("github.head_ref", self.ci)

    def test_integration_profile_disables_all_business_schedulers(self):
        properties = {}
        for raw_line in self.integration_profile.splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#"):
                continue
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip()

        self.assertEqual("false", properties.get("ssing.scheduled-jobs.enabled"))
        self.assertEqual(
            "false",
            properties.get("ssing.matching.search-scheduler.enabled"),
        )

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
            (self.deploy_job, "./scripts/db/prepare-dev-deploy-db.sh"),
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

    def test_latest_deploy_recovers_incomplete_db_before_restart(self):
        marker_position = self.deploy_job.index("미완료 dev DB 작업 확인")
        upload_position = self.deploy_job.index("EC2 배포 파일 업로드")
        runtime_env_position = self.deploy_job.index("EC2 런타임 환경변수 생성")
        preflight_position = self.deploy_job.index("EC2 배포 사전 검증")
        db_guard_position = self.deploy_job.index("dev DB 초기화 직전 현재 main SHA 재확인")
        migration_position = self.deploy_job.index(
            "./scripts/db/prepare-dev-deploy-db.sh --confirm-dev-deploy-reset main"
        )
        restart_guard_position = self.deploy_job.index("Compose 재시작 직전 현재 main SHA 재확인")
        activate_env_position = self.deploy_job.index("mv -f .env.next .env")
        restart_position = self.deploy_job.index("up --pull never -d --remove-orphans")
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
        self.assertLess(runtime_env_position, preflight_position)
        self.assertLess(preflight_position, db_guard_position)
        self.assertLess(runtime_env_position, db_guard_position)
        self.assertLess(db_guard_position, migration_position)
        self.assertLess(runtime_env_position, migration_position)
        self.assertLess(migration_position, restart_position)
        self.assertLess(migration_position, restart_guard_position)
        self.assertLess(restart_guard_position, restart_position)
        self.assertLess(migration_position, activate_env_position)
        self.assertLess(restart_position, activate_env_position)
        self.assertLess(health_position, activate_env_position)
        self.assertLess(marker_position, restart_position)
        self.assertNotIn("mv -f .env.next .env", restart_step)
        self.assertRegex(
            restart_step,
            r"sudo env SSING_RUNTIME_ENV_FILE=\.env\.next \\\n"
            r"\s+docker compose --env-file \.env\.next -f \"\$COMPOSE_FILE\" "
            r"up --pull never -d --remove-orphans",
        )
        self.assertNotIn("docker compose --env-file .env.next -f \"$COMPOSE_FILE\" pull", restart_step)
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
            "- name: 미완료 dev DB 작업 확인",
            6,
        )
        self.assertIn("test -e '$DEPLOY_DIR/.dev-db-reset-incomplete'", marker_step)
        self.assertIn("최신 main의 clean·migration·base seed", marker_step)
        self.assertNotIn("exit 1", marker_step)
        self.assertGreaterEqual(self.deploy_job.count(".dev-db-reset-incomplete"), 2)
        self.assertIn("--env-file .env.next", self.deploy_job)
        self.assertIn("SSING_RUNTIME_ENV_FILE=.env.next", self.deploy_job)
        self.assertIn("${SSING_RUNTIME_ENV_FILE:-.env}", self.dev_compose)
        self.assertIn("SSING_JPA_DDL_AUTO: validate", self.deploy_job)

        prepare_runner = (ROOT / "scripts/db/prepare-dev-deploy-db.sh").read_text(
            encoding="utf-8"
        )
        self.assertNotIn("assert_no_incomplete_dev_reset", prepare_runner)
        self.assertIn("MARKER_PREEXISTED", prepare_runner)
        self.assertLess(prepare_runner.index("run_dev_flyway -cleanDisabled=false clean"),
                        prepare_runner.index("run_dev_flyway migrate"))
        self.assertLess(prepare_runner.index("run_dev_flyway migrate"),
                        prepare_runner.index("run_dev_flyway validate"))
        self.assertLess(prepare_runner.index("run_dev_flyway validate"),
                        prepare_runner.index("apply_dev_sql_directory"))

    def test_main_sha_is_rechecked_before_db_mutation_and_restart(self):
        sha_check = (
            'current_main_sha="$(gh api "repos/${GITHUB_REPOSITORY}/commits/main" '
            "--jq '.sha')\""
        )
        self.assertGreaterEqual(self.deploy_job.count(sha_check), 3)
        first_check = self.deploy_job.index("- name: 현재 main SHA 확인")
        db_guard = self.deploy_job.index("- name: dev DB 초기화 직전 현재 main SHA 재확인")
        preflight = self.deploy_job.index(
            "./scripts/db/prepare-dev-deploy-db.sh --preflight-dev-deploy main"
        )
        db_reset = self.deploy_job.index(
            "./scripts/db/prepare-dev-deploy-db.sh --confirm-dev-deploy-reset main"
        )
        restart_guard = self.deploy_job.index("- name: Compose 재시작 직전 현재 main SHA 재확인")
        restart = self.deploy_job.index("up --pull never -d --remove-orphans")
        self.assertLess(first_check, preflight)
        self.assertLess(preflight, db_guard)
        self.assertLess(db_guard, db_reset)
        self.assertLess(db_reset, restart_guard)
        self.assertLess(restart_guard, restart)

    def test_deploy_uses_one_exact_sha_tooling_snapshot_and_preflights_before_clean(self):
        upload_step = indented_block(
            self.deploy_job,
            "- name: EC2 배포 파일 업로드",
            6,
        )
        preflight_step = indented_block(
            self.deploy_job,
            "- name: EC2 배포 사전 검증",
            6,
        )
        db_step = indented_block(
            self.deploy_job,
            "- name: dev DB clean·migration·base seed",
            6,
        )
        prepare_runner = (
            ROOT / "scripts/db/prepare-dev-deploy-db.sh"
        ).read_text(encoding="utf-8")

        self.assertIn("scripts/db/install-dev-release.sh", upload_step)
        self.assertIn("'$DEPLOY_DIR/releases' '$DEPLOY_SHA'", upload_step)
        self.assertNotIn("-C '$DEPLOY_DIR'", upload_step)
        for step in (preflight_step, db_step):
            self.assertIn('release_dir="$deploy_dir/releases/$deploy_sha"', step)
            self.assertIn('cd "$release_dir"', step)
            self.assertIn('source "$deploy_dir/.migration.env"', step)
        self.assertIn(
            "./scripts/db/prepare-dev-deploy-db.sh --preflight-dev-deploy main",
            preflight_step,
        )
        self.assertNotIn("continue-on-error:", preflight_step)
        self.assertIn("docker compose", prepare_runner)
        self.assertIn("config --quiet", prepare_runner)
        self.assertIn(" pull", prepare_runner)
        self.assertIn("run_dev_flyway info", prepare_runner)
        self.assertIn(".dev-deploy-preflight", prepare_runner)
        self.assertIn(
            "./scripts/db/prepare-dev-deploy-db.sh --confirm-dev-deploy-reset main",
            db_step,
        )

    def test_legacy_migrations_are_manual_nightly_and_retained(self):
        self.assertIn("workflow_dispatch:", self.legacy_migration)
        self.assertIn("schedule:", self.legacy_migration)
        self.assertIn("./gradlew legacyMigrationTest --no-daemon", self.legacy_migration)
        self.assertIn("운영 데이터가 생기기 전", self.legacy_migration)
        for source_name in (
            "MatchingRequestV4MigrationTest.java",
            "NotificationV5MigrationTest.java",
        ):
            source = (
                ROOT / "src/integrationTest/java/org/sopt/ssingserver/database" / source_name
            ).read_text(encoding="utf-8")
            self.assertIn('@Tag("legacy-migration")', source)

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

        triggers = indented_block(self.db_check, "on:", 0)
        trigger_names = re.findall(r"(?m)^  ([a-z_]+):", triggers)
        self.assertEqual(["workflow_dispatch", "schedule"], trigger_names)
        self.assertIn('cron: "40 18 * * *"', triggers)
        concurrency = indented_block(self.db_check, "concurrency:", 0)
        self.assertIn("group: db-seed-check", concurrency)
        self.assertIn("cancel-in-progress: false", concurrency)


if __name__ == "__main__":
    unittest.main()
