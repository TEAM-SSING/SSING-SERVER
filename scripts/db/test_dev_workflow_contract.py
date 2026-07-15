from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
RESET_WORKFLOW = ROOT / ".github/workflows/reset-dev-db.yml"
DEPLOY_WORKFLOW = ROOT / ".github/workflows/deploy-dev.yml"
DB_CHECK_WORKFLOW = ROOT / ".github/workflows/db-seed-check.yml"


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
        cls.deploy = DEPLOY_WORKFLOW.read_text(encoding="utf-8")
        cls.db_check = DB_CHECK_WORKFLOW.read_text(encoding="utf-8")
        cls.reset_job = indented_block(cls.reset, "reset:", 2)
        cls.preflight_job = indented_block(cls.reset, "preflight:", 2)
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
        self.assertIn('GIT_REF: ${{ github.ref }}', self.reset)
        self.assertIn('"refs/heads/main"', self.reset)
        for scenario in (
            "matching-price-vivaldi",
            "matching-no-candidate-alpensia",
            "matching-multi-request-oak",
            "pm-full-requested-catalog",
        ):
            self.assertIn(scenario, self.reset)

        self.assertRegex(self.reset_job, r"(?m)^    environment: dev-reset$")
        self.assertNotIn("environment:", self.preflight_job)
        self.assertIn("./scripts/db/reset-dev.sh --confirm-dev-reset", self.reset_job)
        self.assertIn("SSING_DEV_RESET_ENABLED", self.reset_job)
        self.assertIn('RESET_ENABLED" != "true"', self.reset_job)

    def test_reset_and_deploy_share_one_max_queue(self):
        for workflow in (self.reset, self.deploy):
            self.assertIn("group: ssing-dev-mutation", workflow)
            self.assertIn("queue: max", workflow)
            self.assertNotIn("cancel-in-progress: true", workflow)

    def test_credentials_and_ssh_trust_are_separated(self):
        self.assertIn("SSING_DEV_DB_MIGRATION_PASSWORD", self.reset)
        self.assertIn("SSING_DEV_DB_RESET_PASSWORD", self.reset)
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

    def test_incomplete_reset_blocks_deploy_and_migration_precedes_restart(self):
        marker_position = self.deploy_job.index("미완료 dev DB reset 사전 차단")
        upload_position = self.deploy_job.index("EC2 배포 파일 업로드")
        runtime_env_position = self.deploy_job.index("EC2 런타임 환경변수 생성")
        migration_position = self.deploy_job.index("./scripts/db/migrate-dev.sh")
        activate_env_position = self.deploy_job.index("mv -f .env.next .env")
        restart_position = self.deploy_job.index("up -d --remove-orphans")

        self.assertLess(marker_position, upload_position)
        self.assertLess(marker_position, runtime_env_position)
        self.assertLess(upload_position, runtime_env_position)
        self.assertLess(runtime_env_position, migration_position)
        self.assertLess(migration_position, restart_position)
        self.assertLess(migration_position, activate_env_position)
        self.assertLess(activate_env_position, restart_position)
        self.assertLess(marker_position, restart_position)
        marker_step = indented_block(
            self.deploy_job,
            "- name: 미완료 dev DB reset 사전 차단",
            6,
        )
        self.assertIn("test ! -e '$DEPLOY_DIR/.dev-db-reset-incomplete'", marker_step)
        self.assertGreaterEqual(self.deploy_job.count(".dev-db-reset-incomplete"), 2)
        self.assertIn("--env-file .env.next", self.deploy_job)
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


if __name__ == "__main__":
    unittest.main()
