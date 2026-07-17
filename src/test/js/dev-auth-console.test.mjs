import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import test from "node:test";
import vm from "node:vm";

const HTML_PATH = new URL("../../main/resources/dev-auth-console.html", import.meta.url);
const SCRIPT = readFileSync(HTML_PATH, "utf8").match(/<script>([\s\S]*?)<\/script>/)?.[1];

test("승인과 설정 저장은 stateToken과 마우스 선택값만 한 번 전송한다", async () => {
    const harness = await createHarness();
    const member = pendingMember("token-pending");
    harness.control.state.kakaoMembers = [member];
    harness.control.state.resorts = [resort()];
    harness.control.openInstructorAction(member, "APPROVE_WITH_CONFIGURATION");

    const execution = harness.control.executeSelectedInstructorAction();
    await harness.control.executeSelectedInstructorAction();
    const postRequest = harness.takePending();

    assert.equal(postRequest.path, "/dev/auth/kakao-members/41/instructor-actions");
    assert.equal(postRequest.options.method, "POST");
    assert.deepEqual(JSON.parse(postRequest.options.body), {
        actionKey: "APPROVE_WITH_CONFIGURATION",
        stateToken: "token-pending",
        configuration: {
            resortCode: "VIVALDI_PARK",
            sport: "SKI",
            lessonLevels: ["FIRST_TIME", "BEGINNER"],
            availableDurationMinutes: [120],
            maxHeadcount: 3,
            basePriceAmount: 100000,
            additionalPersonPriceAmount: 20000
        }
    });
    assert.equal(harness.pending.length, 0, "중복 클릭은 두 번째 POST를 만들지 않아야 합니다.");

    postRequest.resolve(success({}));
    await waitForPending(harness.pending);
    harness.takePending().resolve(success(listPayload([approvedMember("token-approved")])));
    await execution;

    assert.equal(harness.control.state.kakaoActionInFlight, false);
    assert.equal(harness.elements.actionDialog.open, false);
    assert.equal(harness.control.state.kakaoMembers[0].stateToken, "token-approved");
});

test("409 상태 충돌은 modal을 닫고 최신 Kakao 회원 상태를 다시 읽는다", async () => {
    const harness = await createHarness();
    const member = approvedMember("stale-token");
    harness.control.state.kakaoMembers = [member];
    harness.control.state.resorts = [resort()];
    harness.control.openInstructorAction(member, "START_MATCHING");

    const execution = harness.control.executeSelectedInstructorAction();
    harness.takePending().resolve(failure(
        "조회한 뒤 회원 또는 강사 설정이 바뀌었습니다.",
        409,
        "DEV_INSTRUCTOR_STATE_CHANGED"
    ));
    await waitForPending(harness.pending);
    harness.takePending().resolve(success(listPayload([exposedMember("latest-token")])));
    await execution;

    assert.equal(harness.elements.actionDialog.open, false);
    assert.equal(harness.control.state.kakaoMembers[0].stateToken, "latest-token");
    assert.match(harness.elements.kakaoStatus.textContent, /최신 상태/);
    assert.deepEqual(harness.fetchCalls, [
        "/dev/auth/kakao-members/41/instructor-actions",
        "/dev/auth/kakao-members?page=0&size=100"
    ]);
});

test("가격 +/- 동작은 서버 허용 범위를 넘지 않고 5000원 단위를 유지한다", async () => {
    const harness = await createHarness();
    const member = approvedMember("token");
    harness.control.state.resorts = [resort()];
    harness.control.openInstructorAction(member, "SAVE_CONFIGURATION");

    harness.control.adjustPrice("base", -500000);
    harness.control.adjustPrice("additional", 500000);
    const configuration = harness.control.buildInstructorConfigurationPayload();

    assert.equal(configuration.basePriceAmount, 50000);
    assert.equal(configuration.additionalPersonPriceAmount, 50000);
    assert.equal(configuration.basePriceAmount % 5000, 0);
    assert.equal(configuration.additionalPersonPriceAmount % 5000, 0);
});

async function createHarness() {
    assert.ok(SCRIPT, "inline script를 찾을 수 없습니다.");
    const elements = new Map();
    const pending = [];
    const fetchCalls = [];
    let control;

    const element = selector => {
        if (!elements.has(selector)) elements.set(selector, fakeElement());
        return elements.get(selector);
    };
    const document = {
        querySelector: selector => element(selector),
        querySelectorAll: () => [],
        createElement: () => fakeElement()
    };
    Object.assign(element("#basePriceRange"), {min: "50000", max: "200000", step: "5000"});
    Object.assign(element("#additionalPriceRange"), {min: "0", max: "50000", step: "5000"});
    const fetch = (path, options = {}) => {
        fetchCalls.push(path);
        return new Promise((resolve, reject) => pending.push({path, options, resolve, reject}));
    };
    const context = vm.createContext({
        document,
        fetch,
        console,
        Date,
        Intl,
        Number,
        Map,
        Set,
        String,
        Promise,
        navigator: {},
        requestAnimationFrame: callback => callback(),
        setTimeout: () => 1,
        clearTimeout: () => {},
        __SSING_DEV_AUTH_TEST_HOOK__: value => {
            control = value;
        }
    });

    vm.runInContext(SCRIPT, context, {filename: "dev-auth-console.html"});
    assert.equal(pending.length, 2, "초기 persona와 Kakao 목록 요청이 있어야 합니다.");
    pending.shift().resolve(success({personas: []}));
    pending.shift().resolve(success(listPayload([])));
    await flushPromises();
    fetchCalls.length = 0;

    return {
        control,
        pending,
        fetchCalls,
        elements: {
            actionDialog: element("#instructorActionDialog"),
            kakaoStatus: element("#kakaoStatus")
        },
        takePending() {
            assert.ok(pending.length > 0, "대기 중인 fetch가 없습니다.");
            return pending.shift();
        }
    };
}

function fakeElement() {
    const classes = new Set();
    const attributes = new Map();
    return {
        value: "",
        disabled: false,
        hidden: false,
        textContent: "",
        innerHTML: "",
        className: "",
        open: false,
        dataset: {},
        children: [],
        classList: {
            add: value => classes.add(value),
            remove: value => classes.delete(value),
            contains: value => classes.has(value),
            toggle(value, force) {
                if (force === undefined ? !classes.has(value) : force) classes.add(value);
                else classes.delete(value);
            }
        },
        addEventListener() {},
        setAttribute(name, value) {
            attributes.set(name, String(value));
        },
        getAttribute(name) {
            return attributes.get(name);
        },
        append(...nodes) {
            this.children.push(...nodes);
        },
        replaceChildren(...nodes) {
            this.children = [...nodes];
        },
        querySelector() {
            return fakeElement();
        },
        querySelectorAll() {
            return [];
        },
        closest() {
            return null;
        },
        focus() {},
        reset() {},
        close() {
            this.open = false;
        },
        showModal() {
            this.open = true;
        }
    };
}

function listPayload(members) {
    return {
        observedAt: "2026-07-17T00:00:00Z",
        page: 0,
        size: 100,
        totalElements: members.length,
        totalPages: members.length ? 1 : 0,
        hasPrevious: false,
        hasNext: false,
        resorts: [resort()],
        applicationDefaults: {
            phone: "010-0000-0000",
            birthDate: "2000-01-01",
            careerStartDate: "2020-01-01"
        },
        members
    };
}

function resort() {
    return {
        resortId: 1,
        code: "VIVALDI_PARK",
        displayName: "비발디파크",
        passFeeAmount: 25000
    };
}

function pendingMember(stateToken) {
    return member({
        stateToken,
        role: "CONSUMER",
        approval: "PENDING",
        settingId: null,
        complete: false,
        exposed: false,
        actions: ["APPROVE_WITH_CONFIGURATION"]
    });
}

function approvedMember(stateToken) {
    return member({
        stateToken,
        role: "INSTRUCTOR",
        approval: "APPROVED",
        settingId: 61,
        complete: true,
        exposed: false,
        actions: ["SAVE_CONFIGURATION", "START_MATCHING"]
    });
}

function exposedMember(stateToken) {
    return member({
        stateToken,
        role: "INSTRUCTOR",
        approval: "APPROVED",
        settingId: 61,
        complete: true,
        exposed: true,
        actions: ["STOP_MATCHING"]
    });
}

function member({stateToken, role, approval, settingId, complete, exposed, actions}) {
    return {
        oauthAccountId: 31,
        provider: "KAKAO",
        memberId: 41,
        nickname: "카카오강사",
        memberRole: role,
        memberStatus: "ACTIVE",
        memberCreatedAt: "2026-07-17T00:00:00Z",
        instructorProfileId: approval ? 51 : null,
        instructorApprovalStatus: approval,
        approvedAt: approval === "APPROVED" ? "2026-07-17T01:00:00Z" : null,
        certificateTypes: approval === "APPROVED" ? ["KSIA_SKI_LEVEL_1"] : [],
        configuration: {
            matchingSettingId: settingId,
            resortId: settingId ? 1 : null,
            resortCode: settingId ? "VIVALDI_PARK" : null,
            resortDisplayName: settingId ? "비발디파크" : null,
            sport: settingId ? "SKI" : null,
            lessonLevels: settingId ? ["FIRST_TIME", "BEGINNER"] : [],
            availableDurationMinutes: settingId ? [120] : [],
            maxHeadcount: settingId ? 3 : null,
            equipmentReady: Boolean(settingId),
            exposed,
            basePriceAmount: settingId ? 100000 : null,
            additionalPersonPriceAmount: settingId ? 20000 : null,
            activePricePolicyIds: settingId ? [71] : [],
            complete
        },
        availableActions: actions,
        stateToken,
        diagnostics: []
    };
}

function success(data) {
    return {
        ok: true,
        status: 200,
        json: async () => ({success: true, data})
    };
}

function failure(message, status, code) {
    return {
        ok: false,
        status,
        json: async () => ({success: false, code, message})
    };
}

async function waitForPending(pending) {
    for (let index = 0; index < 20 && pending.length === 0; index += 1) {
        await flushPromises();
    }
    assert.ok(pending.length > 0, "후속 fetch가 생성되지 않았습니다.");
}

async function flushPromises() {
    await new Promise(resolve => setImmediate(resolve));
}
